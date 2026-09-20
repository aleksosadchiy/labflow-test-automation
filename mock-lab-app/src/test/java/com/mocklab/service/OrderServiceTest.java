// @layer: unit
// @spec: 002-mock-lab-rest-api

package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.Order;
import com.mocklab.domain.Patient;
import com.mocklab.domain.ProcessingJob;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.SampleType;
import com.mocklab.exception.FailureKind;
import com.mocklab.exception.LabRuleException;
import com.mocklab.repository.OrderRepository;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OrderService}: placing and looking up orders.
 * Plain objects, no Spring context.
 */
class OrderServiceTest {

    private static final String PATIENT_ID = "PAT-1001";
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private PatientRepository patientRepository;
    private OrderRepository orderRepository;
    private SampleRepository sampleRepository;
    private ProcessingJobRepository processingJobRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        patientRepository = new PatientRepository();
        orderRepository = new OrderRepository();
        sampleRepository = new SampleRepository();
        processingJobRepository = new ProcessingJobRepository();
        orderService = new OrderService(patientRepository, orderRepository, sampleRepository, processingJobRepository);
        patientRepository.save(new Patient(PATIENT_ID, "Alice Johnson"));
    }

    private Sample saveSample(String id, String orderId, SampleType type, SampleStatus storedStatus, Instant createdAt) {
        return sampleRepository.save(new Sample(id, PATIENT_ID, orderId, type, storedStatus, createdAt));
    }

    private void saveJob(String sampleId, SampleStatus currentStatus) {
        processingJobRepository.save(new ProcessingJob(
                "JOB-" + sampleId, sampleId, currentStatus,
                List.of(new ProcessingJob.StatusChange(currentStatus, T0))));
    }

    // ---- place ----

    @Test
    void placeSavesAndReturnsAnOrderForTheExistingPatient() {
        Instant before = Instant.now();

        Order order = orderService.place(PATIENT_ID);

        assertThat(order.id()).startsWith("ORD-").hasSizeGreaterThan("ORD-".length());
        assertThat(order.patientId()).isEqualTo(PATIENT_ID);
        assertThat(order.createdAt()).isNotNull().isBetween(before, Instant.now());
        assertThat(orderRepository.findById(order.id())).contains(order);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t", " \n "})
    void placeRejectsNullBlankAndWhitespaceOnlyPatientIds(String patientId) {
        assertThatThrownBy(() -> orderService.place(patientId))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.VALIDATION);
                    assertThat(e.getMessage()).isEqualTo(LabMessages.PATIENT_REQUIRED);
                });
        assertThat(LabMessages.PATIENT_REQUIRED).isEqualTo("Patient is required.");
    }

    @Test
    void placeRejectsAnUnknownPatientAsNotFound() {
        assertThatThrownBy(() -> orderService.place("PAT-does-not-exist"))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(LabMessages.PATIENT_NOT_FOUND);
                });
        assertThat(LabMessages.PATIENT_NOT_FOUND).isEqualTo("Patient not found.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "PAT-does-not-exist"})
    void failedPlaceSavesNothing(String patientId) {
        AtomicInteger saves = new AtomicInteger();
        OrderService service = new OrderService(patientRepository, new OrderRepository() {
            @Override
            public Order save(Order order) {
                saves.incrementAndGet();
                return super.save(order);
            }
        }, sampleRepository, processingJobRepository);

        assertThatThrownBy(() -> service.place(patientId)).isInstanceOf(LabRuleException.class);

        assertThat(saves).hasValue(0);
    }

    @Test
    void placeChecksForABlankPatientBeforeLookingItUp() {
        // blank is not a known patient either; the validation error must win over "not found"
        assertThatThrownBy(() -> orderService.place("  "))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.VALIDATION);
                    assertThat(e.getMessage()).isEqualTo(LabMessages.PATIENT_REQUIRED);
                });
    }

    @Test
    void twoOrdersForTheSamePatientGetDifferentIds() {
        Order first = orderService.place(PATIENT_ID);
        Order second = orderService.place(PATIENT_ID);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(orderRepository.findById(first.id())).contains(first);
        assertThat(orderRepository.findById(second.id())).contains(second);
    }

    // ---- find ----

    @Test
    void findReturnsThePlacedOrderWithNoSamples() {
        Order order = orderService.place(PATIENT_ID);

        OrderDetails details = orderService.find(order.id());

        assertThat(details.order()).isEqualTo(order);
        assertThat(details.samples()).isEmpty();
    }

    @Test
    void findReturnsOnlyThatOrdersSamplesInCreatedAtThenIdOrder() {
        Order order = orderService.place(PATIENT_ID);
        Order other = orderService.place(PATIENT_ID);
        saveSample("SMP-late", order.id(), SampleType.URINE, SampleStatus.ACCESSIONED, T0.plusSeconds(60));
        saveSample("SMP-b", order.id(), SampleType.BLOOD, SampleStatus.ACCESSIONED, T0);
        saveSample("SMP-a", order.id(), SampleType.SWAB, SampleStatus.ACCESSIONED, T0);
        saveSample("SMP-elsewhere", other.id(), SampleType.TISSUE, SampleStatus.ACCESSIONED, T0);

        OrderDetails details = orderService.find(order.id());

        assertThat(details.samples()).containsExactly(
                new OrderDetails.SampleSummary("SMP-a", SampleType.SWAB, SampleStatus.ACCESSIONED),
                new OrderDetails.SampleSummary("SMP-b", SampleType.BLOOD, SampleStatus.ACCESSIONED),
                new OrderDetails.SampleSummary("SMP-late", SampleType.URINE, SampleStatus.ACCESSIONED));
    }

    @Test
    void findTakesEachSamplesStatusFromItsProcessingJobNotTheStoredMirror() {
        Order order = orderService.place(PATIENT_ID);
        // the sample mirror lags behind the job (the job is saved first, the mirror after)
        saveSample("SMP-a", order.id(), SampleType.BLOOD, SampleStatus.ACCESSIONED, T0);
        saveJob("SMP-a", SampleStatus.SEQUENCING);
        saveSample("SMP-b", order.id(), SampleType.SWAB, SampleStatus.PROCESSING, T0.plusSeconds(1));
        saveJob("SMP-b", SampleStatus.RESULT_READY);

        OrderDetails details = orderService.find(order.id());

        assertThat(details.samples()).extracting(OrderDetails.SampleSummary::status)
                .containsExactly(SampleStatus.SEQUENCING, SampleStatus.RESULT_READY);
    }

    @Test
    void findFallsBackToTheStoredStatusWhenASampleHasNoJob() {
        Order order = orderService.place(PATIENT_ID);
        saveSample("SMP-a", order.id(), SampleType.BLOOD, SampleStatus.PROCESSING, T0);

        OrderDetails details = orderService.find(order.id());

        assertThat(details.samples()).containsExactly(
                new OrderDetails.SampleSummary("SMP-a", SampleType.BLOOD, SampleStatus.PROCESSING));
    }

    @Test
    void findRejectsAnUnknownIdAsNotFound() {
        assertThatThrownBy(() -> orderService.find("ORD-does-not-exist"))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(LabMessages.ORDER_NOT_FOUND);
                });
        assertThat(LabMessages.ORDER_NOT_FOUND).isEqualTo("Order not found.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   "})
    void findRejectsNullAndBlankIdsAsNotFound(String id) {
        assertThatThrownBy(() -> orderService.find(id))
                .isInstanceOfSatisfying(LabRuleException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(LabMessages.ORDER_NOT_FOUND);
                });
    }
}
