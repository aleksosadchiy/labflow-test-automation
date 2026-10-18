package com.labflow.tests.steps;

import com.labflow.client.http.LabResponse;
import com.labflow.common.model.Order;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.testdata.SampleDraft;
import com.labflow.testdata.Samples;
import com.labflow.testdata.UniqueData;
import com.labflow.tests.world.LabWorld;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Steps of topic "04. Registering a sample". They register a sample, look it up,
 * and check the starting stage, the input rules and duplicate sample types. The refusal wording is checked by the step in
 * {@link PatientsSteps}; "the lab does not report anything as created" and "afterwards the patient
 * still has no samples" come from {@link GettingAccessSteps}.
 */
public class RegisteringASampleSteps {

    private static final String TYPES = "blood|tissue|swab|urine";

    private final LabWorld world;

    private Order order;
    private Sample first;
    private Sample registered;
    private Sample found;

    public RegisteringASampleSteps(LabWorld world) {
        this.world = world;
    }

    @Given("the patient has an order")
    public void thePatientHasAnOrder() {
        order = world.lab().orders().place(world.patient().id());
        world.orderIs(order);
    }

    @Given("^the patient has an order that already holds a (" + TYPES + ") sample$")
    public void thePatientHasAnOrderHoldingASample(String type) {
        thePatientHasAnOrder();
        first = world.lab().samples().register(world.patient().id(), typeOf(type), order.id());
    }

    @When("^a caller registers a (" + TYPES + ") sample for the patient( under the order)?$")
    public void aCallerRegistersASample(String type, String underTheOrder) {
        String orderId = underTheOrder == null ? null : order.id();
        registered = world.lab().samples().register(world.patient().id(), typeOf(type), orderId);
        world.sampleIs(registered);
    }

    @When("the caller looks the sample up by the id the lab gave it")
    public void theCallerLooksTheSampleUp() {
        found = world.lab().samples().find(registered.id());
    }

    @Then("^the lab returns the same sample, for the patient, as a (" + TYPES + ") sample under the order$")
    public void theLabReturnsTheSameSample(String type) {
        assertThat(found.id()).isNotBlank().isEqualTo(registered.id());
        assertThat(found.patientId()).isEqualTo(world.patient().id());
        assertThat(found.sampleType()).isEqualTo(typeOf(type));
        assertThat(found.orderId()).isEqualTo(order.id());
    }

    @Then("the order lists only that sample, and the lab lists only that sample for the patient")
    public void theOrderAndThePatientListOnlyThatSample() {
        assertThat(world.lab().orders().find(order.id()).samples())
                .extracting("id", "sampleType")
                .containsExactly(tuple(registered.id(), registered.sampleType()));
        assertThat(world.lab().samples().listFor(world.patient().id()))
                .extracting(Sample::id)
                .containsExactly(registered.id());
    }

    @Then("the sample is at Accessioned, both as registered and as looked up")
    public void theSampleIsAtAccessioned() {
        assertThat(registered.status()).isEqualTo(WorkflowStage.ACCESSIONED);
        assertThat(found.status()).isEqualTo(WorkflowStage.ACCESSIONED);
    }

    @Then("^the lab accepts it as a new (" + TYPES + ") sample under the same order, and lists both samples for the patient$")
    public void theLabAcceptsItAsANewSample(String type) {
        assertThat(registered.id()).isNotBlank().isNotEqualTo(first.id());
        assertThat(registered.sampleType()).isEqualTo(typeOf(type));
        assertThat(registered.orderId()).isEqualTo(order.id());
        assertThat(world.lab().samples().listFor(world.patient().id()))
                .as("samples the lab holds for the patient")
                .extracting(Sample::id)
                .containsExactlyInAnyOrder(first.id(), registered.id());
    }

    @When("^a caller registers a sample (without a patient|for a patient the lab does not know|without a sample type|of a type the lab does not support \\(PLASMA\\))$")
    public void aCallerRegistersABrokenSample(String kind) {
        boolean knownPatient = kind.contains("type");
        String patientId = knownPatient ? world.patient().id() : UniqueData.name("unknown-patient");
        SampleDraft draft = Samples.validFor(patientId);
        SampleDraft sent = switch (kind) {
            case "without a patient" -> draft.withoutPatient();
            case "for a patient the lab does not know" -> draft;
            case "without a sample type" -> draft.withoutType();
            case "of a type the lab does not support (PLASMA)" -> draft.withRawType("PLASMA");
            default -> throw new IllegalArgumentException("No such sample request in this topic: " + kind);
        };
        world.answerWas(world.lab().samples().attemptRegister(sent.patientId(), sent.rawType()));
        world.nothingChangedCanBeShownBy(() -> assertThat(world.lab().samples().listFor(patientId))
                .as("samples the lab holds for the patient after the refusal")
                .isEmpty());
    }

    @When("^a caller registers a second (" + TYPES + ") sample under the same order$")
    public void aCallerRegistersASecondSample(String type) {
        SampleDraft duplicate = Samples.validFor(world.patient().id()).ofType(typeOf(type)).inOrder(order.id());
        LabResponse<Sample> answer =
                world.lab().samples().attemptRegister(duplicate.patientId(), duplicate.rawType(), duplicate.orderId());
        world.answerWas(answer);
    }

    @Then("the order and the patient still hold only the first sample")
    public void theOrderAndThePatientStillHoldOnlyTheFirstSample() {
        assertThat(world.lab().samples().listFor(world.patient().id()))
                .as("samples the lab holds for the patient after the refusal")
                .extracting(Sample::id)
                .containsExactly(first.id());
        assertThat(world.lab().orders().find(order.id()).samples())
                .as("samples the order holds after the refusal")
                .extracting("id")
                .containsExactly(first.id());
    }

    private static SampleType typeOf(String label) {
        return SampleType.valueOf(label.toUpperCase(Locale.ROOT));
    }
}
