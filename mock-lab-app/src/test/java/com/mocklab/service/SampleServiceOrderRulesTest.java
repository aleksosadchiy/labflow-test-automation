// @layer: unit
// @spec: 002-mock-lab-rest-api

package com.mocklab.service;

import com.mocklab.api.error.LabMessages;
import com.mocklab.domain.Order;
import com.mocklab.domain.Patient;
import com.mocklab.domain.Sample;
import com.mocklab.domain.SampleStatus;
import com.mocklab.domain.SampleType;
import com.mocklab.exception.FailureKind;
import com.mocklab.repository.OrderRepository;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.SampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the order-related rules of
 * {@link SampleService#createSample(String, String, String)} (spec 002): order not
 * found, order belongs to another patient, one sample per type per order, the rule
 * precedence, attaching to an existing order, and the concurrency guarantee on the
 * duplicate-type rule. Every failure is asserted on both its exact message and its kind.
 */
class SampleServiceOrderRulesTest {

    private static final String PATIENT_ID = "PAT-1001";
    private static final String OTHER_PATIENT_ID = "PAT-1002";
    private static final String UNKNOWN_ORDER_ID = "ORD-does-not-exist";

    private PatientRepository patientRepository;
    private CountingOrderRepository orderRepository;
    private SampleRepository sampleRepository;
    private ProcessingJobRepository processingJobRepository;
    private SampleService sampleService;

    @BeforeEach
    void setUp() {
        patientRepository = new PatientRepository();
        patientRepository.save(new Patient(PATIENT_ID, "Alice Johnson"));
        patientRepository.save(new Patient(OTHER_PATIENT_ID, "Bob Smith"));
        orderRepository = new CountingOrderRepository();
        sampleRepository = new SampleRepository();
        processingJobRepository = new ProcessingJobRepository();
        sampleService = new SampleService(
                patientRepository, orderRepository, sampleRepository, processingJobRepository);
    }

    // ---- the three new rules, message and kind ----------------------------------

    @Test
    void unknownOrderIsANotFoundFailure() {
        assertFailure(sampleService.createSample(PATIENT_ID, "BLOOD", UNKNOWN_ORDER_ID),
                FailureKind.NOT_FOUND, "Order not found.");
        assertThat(LabMessages.ORDER_NOT_FOUND).isEqualTo("Order not found.");
    }

    @Test
    void orderOfAnotherPatientIsAConflictFailure() {
        Order othersOrder = registerNewOrderFor(OTHER_PATIENT_ID);

        assertFailure(sampleService.createSample(PATIENT_ID, "BLOOD", othersOrder.id()),
                FailureKind.CONFLICT, "Order does not belong to this patient.");
        assertThat(sampleRepository.findByOrderId(othersOrder.id())).hasSize(1);
    }

    @Test
    void sameTypeTwiceOnOneOrderIsAConflictFailureAndSavesNothing() {
        Order order = registerNewOrderFor(PATIENT_ID); // holds one BLOOD sample
        int samplesBefore = sampleRepository.findAll().size();

        assertFailure(sampleService.createSample(PATIENT_ID, "BLOOD", order.id()),
                FailureKind.CONFLICT, "A sample of this type already exists for this order.");
        // the type is matched case-insensitively, like everywhere else
        assertFailure(sampleService.createSample(PATIENT_ID, "blood", order.id()),
                FailureKind.CONFLICT, "A sample of this type already exists for this order.");

        assertThat(sampleRepository.findByOrderId(order.id())).hasSize(1);
        assertThat(sampleRepository.findAll()).hasSize(samplesBefore);
    }

    // ---- attaching to an existing order ------------------------------------------

    @Test
    void namedOrderIsReusedAndNoNewOrderIsCreated() {
        Order order = registerNewOrderFor(PATIENT_ID);
        int ordersBefore = orderCount();

        SampleCreationResult result = sampleService.createSample(PATIENT_ID, "TISSUE", order.id());

        SampleCreationResult.Success success = assertSuccess(result);
        assertThat(success.order().id()).isEqualTo(order.id());
        assertThat(success.sample().orderId()).isEqualTo(order.id());
        assertThat(success.sample().patientId()).isEqualTo(PATIENT_ID);
        assertThat(success.processingJob().sampleId()).isEqualTo(success.sample().id());
        assertThat(orderCount()).isEqualTo(ordersBefore);
    }

    @Test
    void twoDifferentTypesUnderOneOrderBothSucceedAndBothAreListedUnderIt() {
        Order order = registerNewOrderFor(PATIENT_ID); // BLOOD already there

        SampleCreationResult.Success second =
                assertSuccess(sampleService.createSample(PATIENT_ID, "SWAB", order.id()));
        SampleCreationResult.Success third =
                assertSuccess(sampleService.createSample(PATIENT_ID, "tissue", order.id()));

        assertThat(sampleRepository.findByOrderId(order.id()))
                .extracting(Sample::sampleType)
                .containsExactlyInAnyOrder(SampleType.BLOOD, SampleType.SWAB, SampleType.TISSUE);
        assertThat(sampleRepository.findByOrderId(order.id()))
                .extracting(Sample::id)
                .contains(second.sample().id(), third.sample().id());
    }

    @Test
    void sameTypeUnderADifferentOrderIsAllowed() {
        Order first = registerNewOrderFor(PATIENT_ID);
        Order second = registerNewOrderFor(PATIENT_ID);

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(sampleRepository.findByOrderId(first.id())).hasSize(1);
        assertThat(sampleRepository.findByOrderId(second.id())).hasSize(1);
    }

    // ---- no order named -----------------------------------------------------------

    @Test
    void nullEmptyOrWhitespaceOrderIdBehavesAsNoOrderNamed() {
        int ordersBefore = orderCount();

        SampleCreationResult.Success fromNull =
                assertSuccess(sampleService.createSample(PATIENT_ID, "BLOOD", null));
        SampleCreationResult.Success fromEmpty =
                assertSuccess(sampleService.createSample(PATIENT_ID, "BLOOD", ""));
        SampleCreationResult.Success fromWhitespace =
                assertSuccess(sampleService.createSample(PATIENT_ID, "BLOOD", "   "));

        assertThat(orderCount()).isEqualTo(ordersBefore + 3);
        assertThat(List.of(fromNull.order().id(), fromEmpty.order().id(), fromWhitespace.order().id()))
                .doesNotHaveDuplicates();
        assertThat(fromWhitespace.sample().orderId()).isEqualTo(fromWhitespace.order().id());
        assertThat(orderRepository.findById(fromWhitespace.order().id())).isPresent();
    }

    @Test
    void twoArgumentMethodStillCreatesANewOrderPerCall() {
        int ordersBefore = orderCount();

        SampleCreationResult.Success first = assertSuccess(sampleService.createSample(PATIENT_ID, "BLOOD"));
        SampleCreationResult.Success second = assertSuccess(sampleService.createSample(PATIENT_ID, "BLOOD"));

        assertThat(first.order().id()).isNotEqualTo(second.order().id());
        assertThat(first.order().patientId()).isEqualTo(PATIENT_ID);
        assertThat(orderCount()).isEqualTo(ordersBefore + 2);
    }

    // ---- precedence: first failure wins -------------------------------------------

    @Test
    void blankPatientBeatsEverythingIncludingOrderProblems() {
        Order othersOrder = registerNewOrderFor(OTHER_PATIENT_ID);

        assertFailure(sampleService.createSample("", "", UNKNOWN_ORDER_ID),
                FailureKind.VALIDATION, "Patient is required.");
        assertFailure(sampleService.createSample(null, "PLASMA", othersOrder.id()),
                FailureKind.VALIDATION, "Patient is required.");
    }

    @Test
    void blankTypeBeatsUnknownPatientAndOrderProblems() {
        assertFailure(sampleService.createSample("PAT-9999", " ", UNKNOWN_ORDER_ID),
                FailureKind.VALIDATION, "Sample type is required.");
    }

    @Test
    void unknownPatientBeatsUnknownOrder() {
        assertFailure(sampleService.createSample("PAT-9999", "BLOOD", UNKNOWN_ORDER_ID),
                FailureKind.NOT_FOUND, "Patient not found.");
    }

    @Test
    void invalidTypeBeatsUnknownOrder() {
        assertFailure(sampleService.createSample(PATIENT_ID, "PLASMA", UNKNOWN_ORDER_ID),
                FailureKind.VALIDATION, "Invalid sample type.");
    }

    @Test
    void invalidTypeBeatsOrderOwnershipAndDuplicate() {
        Order othersOrder = registerNewOrderFor(OTHER_PATIENT_ID);

        assertFailure(sampleService.createSample(PATIENT_ID, "PLASMA", othersOrder.id()),
                FailureKind.VALIDATION, "Invalid sample type.");
    }

    @Test
    void unknownOrderBeatsMismatch() {
        // there is no owner to mismatch against when the order does not exist
        assertFailure(sampleService.createSample(OTHER_PATIENT_ID, "BLOOD", UNKNOWN_ORDER_ID),
                FailureKind.NOT_FOUND, "Order not found.");
    }

    @Test
    void mismatchBeatsDuplicateType() {
        // the other patient's order already holds BLOOD, and PAT-1001 asks for BLOOD:
        // both "wrong owner" and "duplicate type" apply; ownership is checked first
        Order othersOrder = registerNewOrderFor(OTHER_PATIENT_ID);

        assertFailure(sampleService.createSample(PATIENT_ID, "BLOOD", othersOrder.id()),
                FailureKind.CONFLICT, "Order does not belong to this patient.");
    }

    @Test
    void duplicateTypeBeatsGeneratedIdCollision() {
        Order order = registerNewOrderFor(PATIENT_ID);
        SampleRepository everyNewIdTaken = new SampleRepository() {
            @Override
            public Optional<Sample> findById(String id) {
                return Optional.of(new Sample(id, PATIENT_ID, "ORD-existing",
                        SampleType.BLOOD, SampleStatus.ACCESSIONED, Instant.EPOCH));
            }
        };
        everyNewIdTaken.save(new Sample("SMP-seed", PATIENT_ID, order.id(),
                SampleType.BLOOD, SampleStatus.ACCESSIONED, Instant.EPOCH));
        SampleService colliding = new SampleService(
                patientRepository, orderRepository, everyNewIdTaken, processingJobRepository);

        assertFailure(colliding.createSample(PATIENT_ID, "BLOOD", order.id()),
                FailureKind.CONFLICT, "A sample of this type already exists for this order.");
        // while a non-duplicate type reaches the defensive ID check
        assertFailure(colliding.createSample(PATIENT_ID, "SWAB", order.id()),
                FailureKind.CONFLICT, "A sample with this ID already exists.");
    }

    // ---- concurrency ---------------------------------------------------------------

    @Test
    void manyThreadsRegisteringTheSameTypeOnOneOrderYieldExactlyOneSuccess() throws Exception {
        assertExactlyOneWinner(sampleService, sampleRepository);
    }

    @Test
    void sameTypeRaceStillYieldsOneSuccessWhenTheCheckToSaveGapIsWidened() throws Exception {
        // Same scenario, but the duplicate check is made slow (a hand-written subclass, no
        // mocking) so that WITHOUT the lock every thread would see "no sample yet" first.
        SampleRepository slowLookup = new SampleRepository() {
            @Override
            public List<Sample> findByOrderId(String orderId) {
                List<Sample> snapshot = super.findByOrderId(orderId);
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return snapshot;
            }
        };
        SampleService slowService = new SampleService(
                patientRepository, orderRepository, slowLookup, processingJobRepository);

        assertExactlyOneWinner(slowService, slowLookup);
    }

    @Test
    void threadsRegisteringDifferentTypesOnOneOrderAllSucceedWithDistinctSampleIds() throws Exception {
        Order order = orderRepository.save(new Order("ORD-shared", PATIENT_ID, Instant.now()));
        SampleType[] types = SampleType.values();

        List<Callable<SampleCreationResult>> tasks = new ArrayList<>();
        for (SampleType type : types) {
            tasks.add(() -> sampleService.createSample(PATIENT_ID, type.name(), order.id()));
        }
        List<SampleCreationResult> results = runConcurrently(tasks);

        List<String> sampleIds = new ArrayList<>();
        for (SampleCreationResult result : results) {
            sampleIds.add(assertSuccess(result).sample().id());
        }
        assertThat(sampleIds).hasSize(types.length).doesNotHaveDuplicates();
        assertThat(sampleRepository.findByOrderId(order.id()))
                .extracting(Sample::sampleType)
                .containsExactlyInAnyOrder(types);
        assertThat(orderCount()).as("only the hand-made shared order exists").isEqualTo(1);
    }

    // ---- helpers -------------------------------------------------------------------

    private void assertExactlyOneWinner(SampleService service, SampleRepository samples) throws Exception {
        final int threads = 20;
        Order order = orderRepository.save(new Order("ORD-race", PATIENT_ID, Instant.now()));

        List<Callable<SampleCreationResult>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> service.createSample(PATIENT_ID, "BLOOD", order.id()));
        }
        List<SampleCreationResult> results = runConcurrently(tasks);

        long successes = results.stream().filter(r -> r instanceof SampleCreationResult.Success).count();
        List<SampleCreationResult> failures = results.stream()
                .filter(r -> r instanceof SampleCreationResult.Failure).toList();
        assertThat(successes).as("exactly one registration wins").isEqualTo(1);
        assertThat(failures).hasSize(threads - 1);
        assertThat(failures).allSatisfy(failure ->
                assertFailure(failure, FailureKind.CONFLICT, "A sample of this type already exists for this order."));
        assertThat(samples.findByOrderId(order.id())).hasSize(1);
    }

    /**
     * Runs all tasks at the same moment (a start latch releases them together) and
     * returns their results. Bounded by timeouts so a bug can never hang the build.
     */
    private static List<SampleCreationResult> runConcurrently(List<Callable<SampleCreationResult>> tasks)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<SampleCreationResult>> futures = new ArrayList<>();
            for (Callable<SampleCreationResult> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start latch never released");
                    }
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("all threads started").isTrue();
            start.countDown();

            List<SampleCreationResult> results = new ArrayList<>();
            for (Future<SampleCreationResult> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Registers a BLOOD sample with no order named, which creates a fresh order; returns that order. */
    private Order registerNewOrderFor(String patientId) {
        return assertSuccess(sampleService.createSample(patientId, "BLOOD")).order();
    }

    /** How many orders have been saved so far (OrderRepository has no findAll). */
    private int orderCount() {
        return orderRepository.saves.get();
    }

    /** Hand-written subclass (no mocking) that counts how many orders were saved. */
    private static class CountingOrderRepository extends OrderRepository {
        final AtomicInteger saves = new AtomicInteger();

        @Override
        public Order save(Order order) {
            saves.incrementAndGet();
            return super.save(order);
        }
    }

    private static SampleCreationResult.Success assertSuccess(SampleCreationResult result) {
        assertThat(result).isInstanceOf(SampleCreationResult.Success.class);
        return (SampleCreationResult.Success) result;
    }

    private static void assertFailure(SampleCreationResult result, FailureKind kind, String message) {
        assertThat(result).isInstanceOf(SampleCreationResult.Failure.class);
        SampleCreationResult.Failure failure = (SampleCreationResult.Failure) result;
        assertThat(failure.message()).isEqualTo(message);
        assertThat(failure.kind()).isEqualTo(kind);
    }
}
