package com.labflow.ui.tests.steps;

import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.testdata.Samples;
import com.labflow.testdata.UniqueData;
import com.labflow.ui.pages.SampleListPage;
import com.labflow.ui.pages.SampleRow;
import com.labflow.ui.tests.world.UiWorld;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of topic "04. Registering a sample" on the screens. They drive the
 * registration Page Objects.
 *
 * <p>The feature file holds labels ("a patient the lab does not know", "the unsupported sample
 * type PLASMA"): the step resolves the label to a plainly fake, uniquely generated value.
 */
public class RegisteringASampleSteps {

    private static final String UNSUPPORTED_TYPE = "PLASMA";

    private final UiWorld world;

    public RegisteringASampleSteps(UiWorld world) {
        this.world = world;
    }

    @Given("the visitor is signed in")
    public void theVisitorIsSignedIn() {
        world.sampleListIs(world.signedIn());
    }

    @Given("the lab holds a registered patient")
    public void theLabHoldsARegisteredPatient() {
        world.patientIs(world.newPatient());
    }

    @Given("the visitor is on the Create Sample screen")
    public void theVisitorIsOnTheCreateSampleScreen() {
        world.createSampleScreenIs(world.sampleList().startNewSample());
    }

    @When("the visitor registers a blood sample for the patient on the Create Sample screen")
    public void theVisitorRegistersABloodSample() {
        world.sampleDetailsIs(world.sampleList().startNewSample()
                .createSample(world.patient().id(), SampleType.BLOOD.name()));
    }

    @When("^the visitor submits the Create Sample screen with the (patient|sample type) left blank$")
    public void theVisitorLeavesAFieldBlank(String field) {
        String valid = Samples.validFor(world.patient().id()).rawType();
        String patientId = "patient".equals(field) ? "" : world.patient().id();
        String sampleType = "sample type".equals(field) ? "" : valid;
        world.createSampleScreenIs(world.createSampleScreen().createSampleExpectingError(patientId, sampleType));
    }

    @When("^the visitor submits the Create Sample screen with "
            + "(a patient the lab does not know|a patient the lab knows) and "
            + "(a supported sample type|the unsupported sample type PLASMA)$")
    public void theVisitorSubmitsAnUnacceptableSample(String patientLabel, String typeLabel) {
        String patientId = patientLabel.contains("does not")
                ? UniqueData.name("unknown-patient")
                : world.newPatient().id();
        String sampleType = typeLabel.contains("PLASMA")
                ? UNSUPPORTED_TYPE
                : Samples.validFor(patientId).rawType();
        world.createSampleScreenIs(world.createSampleScreen().createSampleExpectingError(patientId, sampleType));
    }

    @Then("the Sample List shows the new sample for the patient as Accessioned")
    public void theSampleListShowsTheNewSampleAsAccessioned() {
        String sampleId = world.sampleDetails().sampleId();
        Optional<SampleRow> row = SampleListPage.open(world.page(), world.config()).rowFor(sampleId);

        assertThat(row)
                .as("whether the Sample List shows the newly registered sample")
                .isPresent();
        assertThat(row.get().patientName())
                .as("the patient name shown in the new sample's row")
                .isEqualTo(world.patient().name());
        assertThat(row.get().status())
                .as("the status shown in the new sample's row")
                .isEqualTo(WorkflowStage.ACCESSIONED);
    }

    @Then("the Create Sample screen says {string}")
    public void theCreateSampleScreenSays(String message) {
        assertThat(world.createSampleScreen().errorMessage())
                .as("the Create Sample screen's error message")
                .isEqualTo(message);
    }
}
