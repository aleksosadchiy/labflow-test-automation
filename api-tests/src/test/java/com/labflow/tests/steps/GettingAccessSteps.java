package com.labflow.tests.steps;

import com.labflow.client.LabClient;
import com.labflow.client.http.LabResponse;
import com.labflow.common.LabMessages;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.testdata.Patients;
import com.labflow.testdata.SampleDraft;
import com.labflow.testdata.Samples;
import com.labflow.tests.world.LabWorld;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.assertj.core.api.Assertions;

import static com.labflow.client.assertions.LabAssertions.assertThat;
import static com.labflow.client.http.Rejection.UNAUTHORIZED;

/**
 * Steps of topic "01. Getting access". They call the lab-layer services and the shared
 * assertions.
 *
 * <p>The feature file holds labels ("without an access key", "register a sample for the
 * patient"), never a key: the label is resolved to a client or a request here. The wrong key is
 * a plainly fake text; the real key is never written, shown or asserted.
 */
public class GettingAccessSteps {

    private final LabWorld world;

    public GettingAccessSteps(LabWorld world) {
        this.world = world;
    }

    @Given("the lab holds nothing yet")
    public void theLabHoldsNothingYet() {
        // Nothing to set up: the request itself is the first thing the lab sees.
    }

    @Given("the lab holds a registered patient")
    public void theLabHoldsARegisteredPatient() {
        world.newPatient();
    }

    /** The type is optional ("a swab sample at ..."); a sample without one is a blood sample. */
    @Given("^the lab holds a (?:(blood|tissue|swab|urine) )?sample at (.+)$")
    public void theLabHoldsASampleAt(String typeName, String stageName) {
        Patient patient = world.newPatient();
        SampleType type = typeName == null ? SampleType.BLOOD : SampleType.valueOf(typeName.toUpperCase());
        Sample registered = world.lab().samples().register(patient.id(), type);
        WorkflowStage stage = LabWorld.stageNamed(stageName);
        if (stage != WorkflowStage.ACCESSIONED) {
            registered = world.lab().samples().walkTo(registered.id(), stage);
        }
        world.sampleIs(registered);
    }

    @When("^a caller (without an access key|with a wrong access key) asks the lab to (.+)$")
    public void aCallerAsksTheLabTo(String caller, String request) {
        LabClient valid = world.lab();
        LabClient asSeen = clientFor(caller, valid);
        switch (request) {
            case "register a patient" -> {
                world.answerWas(asSeen.patients().attemptRegister(Patients.valid().name()));
                // The lab lists no patients, so a scenario cannot look for the refused one. What it
                // can observe is that no Location header and no patient came back.
            }
            case "register a sample for the patient" -> {
                Patient patient = world.patient();
                SampleDraft draft = Samples.validFor(patient.id());
                world.answerWas(asSeen.samples().attemptRegister(draft.patientId(), draft.rawType()));
                world.nothingChangedCanBeShownBy(() -> Assertions.assertThat(valid.samples().listFor(patient.id()))
                        .as("samples the lab holds for the patient after the refusal")
                        .isEmpty());
            }
            case "move the sample to its next stage" -> {
                Sample sample = world.sample();
                world.answerWas(asSeen.samples().attemptMoveTo(sample.id(), WorkflowStage.READY_FOR_PROCESSING));
                world.nothingChangedCanBeShownBy(() -> Assertions.assertThat(valid.samples().find(sample.id()).status())
                        .as("the sample's stage after the refused move")
                        .isEqualTo(WorkflowStage.ACCESSIONED));
            }
            case "look the sample up" -> world.answerWas(asSeen.samples().attemptFind(world.sample().id()));
            default -> throw new IllegalArgumentException("No such request in this topic: " + request);
        }
    }

    @Then("the lab refuses the request as unauthorized, with its standard message and a request to sign in with a bearer key")
    public void theLabRefusesTheRequestAsUnauthorized() {
        assertThat(world.lastResponse())
                .isRejectedAs(UNAUTHORIZED, LabMessages.UNAUTHORIZED)
                .hasHeader("WWW-Authenticate", "Bearer");
    }

    @Then("the lab does not report anything as created")
    public void theLabDoesNotReportAnythingAsCreated() {
        assertThat(world.lastResponse()).hasNoLocationHeader();
    }

    @Then("the lab returns nothing for the request")
    public void theLabReturnsNothingForTheRequest() {
        LabResponse<?> response = world.lastResponse();
        Assertions.assertThat(response.body())
                .as("anything the lab created or returned for the refused request")
                .isEmpty();
    }

    @Then("^afterwards (.+)$")
    public void afterwards(String whatHolds) {
        String stillAt = "the sample is still at ";
        if (whatHolds.startsWith(stillAt)) {
            WorkflowStage stage = LabWorld.stageNamed(whatHolds.substring(stillAt.length()));
            Assertions.assertThat(world.lab().samples().find(world.sample().id()).status())
                    .as("the sample's stage after the refused move")
                    .isEqualTo(stage);
            return;
        }
        switch (whatHolds) {
            case "no patient was issued", "the patient still has no samples" -> world.assertNothingChanged();
            default -> throw new IllegalArgumentException("No such statement in this topic: " + whatHolds);
        }
    }

    /** The two ways of arriving at the lab without the right key. */
    private static LabClient clientFor(String caller, LabClient valid) {
        return switch (caller) {
            case "without an access key" -> valid.withoutApiKey();
            case "with a wrong access key" -> valid.withApiKey("s3cret-wrong-key");
            default -> throw new IllegalArgumentException("No such way of arriving at the lab: " + caller);
        };
    }
}
