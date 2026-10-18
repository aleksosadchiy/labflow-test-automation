package com.labflow.tests.steps;

import com.labflow.common.LabMessages;
import com.labflow.common.model.Patient;
import com.labflow.testdata.PatientDraft;
import com.labflow.testdata.Patients;
import com.labflow.tests.world.LabWorld;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.labflow.client.assertions.LabAssertions.assertThat;
import static com.labflow.client.http.Rejection.CONFLICT;
import static com.labflow.client.http.Rejection.INVALID_INPUT;
import static com.labflow.client.http.Rejection.METHOD_NOT_ALLOWED;
import static com.labflow.client.http.Rejection.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of topic "02. Patients". They register a patient, look it up,
 * and check the patient-name input rules.
 *
 * <p>The refusal step ("the lab refuses the request as ..., saying ...") is shared with the other
 * topics: Cucumber's glue is global, so one definition serves every feature file. It resolves the
 * readable labels of the feature file to the lab's rejection kind and exact message.
 */
public class PatientsSteps {

    private static final Pattern TRANSITION = Pattern.compile("a sample cannot move from (.+) to (.+)");

    private final LabWorld world;

    private Patient registered;
    private String registeredName;
    private Patient found;

    public PatientsSteps(LabWorld world) {
        this.world = world;
    }

    @When("a caller registers a patient with a name of its own")
    public void aCallerRegistersAPatientWithAName() {
        registeredName = Patients.valid().name();
        registered = world.lab().patients().register(registeredName);
    }

    @When("the caller looks the patient up by the id the lab gave it")
    public void theCallerLooksThePatientUp() {
        found = world.lab().patients().find(registered.id());
    }

    @Then("the lab returns the same id and the name the patient was registered with")
    public void theLabReturnsTheSameIdAndName() {
        assertThat(found.id()).isNotBlank().isEqualTo(registered.id());
        assertThat(found.name()).isEqualTo(registeredName);
    }

    @When("^a caller registers a patient with (no name at all|an empty name|a name of only blank spaces)$")
    public void aCallerRegistersAPatientWithAnUnusableName(String kind) {
        PatientDraft draft = Patients.valid();
        PatientDraft broken = switch (kind) {
            case "no name at all" -> draft.withoutName();
            case "an empty name" -> draft.withName("");
            case "a name of only blank spaces" -> draft.blankName();
            default -> throw new IllegalArgumentException("No such name in this topic: " + kind);
        };
        world.answerWas(world.lab().patients().attemptRegister(broken.name()));
    }

    @Then("^the lab refuses the request as (invalid input|not found|not allowed|a conflict), saying (.+)$")
    public void theLabRefusesTheRequestAs(String refusal, String message) {
        var kind = switch (refusal) {
            case "invalid input" -> INVALID_INPUT;
            case "not found" -> NOT_FOUND;
            case "not allowed" -> METHOD_NOT_ALLOWED;
            case "a conflict" -> CONFLICT;
            default -> throw new IllegalArgumentException("No such refusal: " + refusal);
        };
        String text = switch (message) {
            case "a patient name is required" -> LabMessages.PATIENT_NAME_REQUIRED;
            case "a patient is required" -> LabMessages.PATIENT_REQUIRED;
            case "the patient was not found" -> LabMessages.PATIENT_NOT_FOUND;
            case "a sample type is required" -> LabMessages.SAMPLE_TYPE_REQUIRED;
            case "the sample type is not supported" -> LabMessages.INVALID_SAMPLE_TYPE;
            case "the order already holds a sample of that type" -> LabMessages.DUPLICATE_SAMPLE_TYPE;
            case "the sample was not found" -> LabMessages.SAMPLE_NOT_FOUND;
            case "the result is not ready yet" -> LabMessages.RESULT_NOT_READY;
            case "the report is not ready yet" -> LabMessages.REPORT_NOT_READY;
            case "the content of the request could not be read" -> LabMessages.MALFORMED_REQUEST_BODY;
            case "that action is not allowed" -> LabMessages.METHOD_NOT_ALLOWED;
            default -> transitionMessage(message);
        };
        assertThat(world.lastResponse()).isRejectedAs(kind, text);
    }

    /** "a sample cannot move from A to B" is the lab's refusal of a wrong move, with the stage names as users see them. */
    private static String transitionMessage(String message) {
        Matcher move = TRANSITION.matcher(message);
        if (!move.matches()) {
            throw new IllegalArgumentException("No such message in these topics: " + message);
        }
        return LabMessages.invalidTransition(LabWorld.stageNamed(move.group(1)), LabWorld.stageNamed(move.group(2)));
    }
}
