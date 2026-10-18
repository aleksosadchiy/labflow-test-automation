package com.labflow.tests.steps;

import com.labflow.common.model.Sample;
import com.labflow.tests.world.LabWorld;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.assertj.core.api.Assertions;

import static com.labflow.client.assertions.LabAssertions.assertThat;

/**
 * Steps of topic "08. Malformed and unsupported requests". They send the malformed and
 * unsupported requests and expect the lab's refusal. The set-up, the refusal step ("the lab
 * refuses the request as ..., saying ...") and "the lab does not report anything as created" are
 * shared: see {@link GettingAccessSteps} and {@link PatientsSteps}.
 *
 * <p>The feature file holds a label for the kind of broken request ("that stops halfway"); the text
 * sent is chosen here and never written in the feature file or shown in a report.
 */
public class MalformedAndUnsupportedRequestsSteps {

    private final LabWorld world;

    public MalformedAndUnsupportedRequestsSteps(LabWorld world) {
        this.world = world;
    }

    @When("^a caller sends the lab a (sample|patient) request (that stops halfway|in plain text|with nothing in it)$")
    public void aCallerSendsAnUnreadableRequest(String target, String kind) {
        String content = switch (kind) {
            case "that stops halfway" -> "{\"patientId\": ";
            case "in plain text" -> "not json at all";
            case "with nothing in it" -> "";
            default -> throw new IllegalArgumentException("No such broken request in this topic: " + kind);
        };
        switch (target) {
            case "sample" -> {
                String patientId = world.patient().id();
                world.answerWas(world.lab().samples().attemptRegisterRaw(content));
                world.nothingChangedCanBeShownBy(() -> Assertions.assertThat(world.lab().samples().listFor(patientId))
                        .as("samples the lab holds for the patient after the refusal")
                        .isEmpty());
            }
            case "patient" -> {
                if (!kind.equals("in plain text")) {
                    throw new IllegalArgumentException("Only a plain-text patient request is part of this topic");
                }
                world.answerWas(world.lab().patients().attemptRegisterRaw(content));
            }
            default -> throw new IllegalArgumentException("No such request in this topic: " + target);
        }
    }

    @When("a caller asks the lab to delete the sample")
    public void aCallerAsksTheLabToDeleteTheSample() {
        world.answerWas(world.lab().samples().attemptDelete(world.sample().id()));
    }

    @Then("the lab lists reading the sample as an allowed action")
    public void theLabListsReadingAsAllowed() {
        // Only that GET is allowed is the contract; the exact list of allowed actions is not.
        assertThat(world.lastResponse()).hasHeaderContaining("Allow", "GET");
    }

    @Then("^the lab still holds the sample, at (.+)$")
    public void theLabStillHoldsTheSample(String stageName) {
        Sample registered = world.sample();
        Sample afterwards = world.lab().samples().find(registered.id());
        Assertions.assertThat(afterwards.id())
                .as("the sample the lab still holds after the refused delete")
                .isEqualTo(registered.id());
        Assertions.assertThat(afterwards.status())
                .as("the sample's stage after the refused delete")
                .isEqualTo(LabWorld.stageNamed(stageName));
    }
}
