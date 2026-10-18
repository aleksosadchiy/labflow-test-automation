package com.labflow.tests.steps;

import com.labflow.common.model.Order;
import com.labflow.common.model.Progress;
import com.labflow.common.model.Report;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.StageEntry;
import com.labflow.common.model.TestResult;
import com.labflow.common.model.WorkflowStage;
import com.labflow.tests.world.LabWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Steps of topic "09. The whole journey". They make the requests of the
 * journey in order and check each answer. The set-up ("the lab holds a registered
 * patient", "the patient has an order", registering the sample) is shared: see
 * {@link GettingAccessSteps} and {@link RegisteringASampleSteps}.
 *
 * <p>The tables of the feature file are the journey's expectations. Each row of the answers table is
 * one fact checked here; a row this class does not know fails the step by name, so the table cannot
 * quietly say more than the step checks.
 */
public class TheWholeJourneySteps {

    private final LabWorld world;

    private Sample finished;
    private TestResult result;
    private Report report;
    private Progress progress;
    private Order orderAfter;

    public TheWholeJourneySteps(LabWorld world) {
        this.world = world;
    }

    @Then("the sample is under that order and at Accessioned")
    public void theSampleIsUnderThatOrderAndAtAccessioned() {
        assertThat(world.sample().orderId()).isEqualTo(world.order().id());
        assertThat(world.sample().status()).isEqualTo(WorkflowStage.ACCESSIONED);
    }

    @When("the caller takes the sample through every stage to Report Generated")
    public void theCallerTakesTheSampleThroughEveryStage() {
        finished = world.lab().samples().walkTo(world.sample().id(), WorkflowStage.REPORT_GENERATED);
    }

    @When("the caller asks the lab for the sample's result, report and progress, and for the order")
    public void theCallerAsksForTheAnswers() {
        String sampleId = world.sample().id();
        result = world.lab().samples().result(sampleId);
        report = world.lab().samples().report(sampleId);
        progress = world.lab().samples().progress(sampleId);
        orderAfter = world.lab().orders().find(world.order().id());
    }

    @Then("the sample is the one registered, now at Report Generated")
    public void theSampleIsTheOneRegistered() {
        assertThat(finished.id()).isEqualTo(world.sample().id());
        assertThat(finished.status()).isEqualTo(WorkflowStage.REPORT_GENERATED);
    }

    @Then("the lab's answers belong to the sample registered:")
    public void theAnswersBelongToTheSample(DataTable facts) {
        String sampleId = world.sample().id();
        for (Map<String, String> row : facts.asMaps()) {
            String fact = row.get("answer") + ": " + row.get("holds");
            switch (fact) {
                case "result: the sample's id" -> assertThat(result.sampleId()).as(fact).isEqualTo(sampleId);
                case "result: a test name" -> assertThat(result.testName()).as(fact).isNotBlank();
                case "result: a result value" -> assertThat(result.resultValue()).as(fact).isNotBlank();
                case "report: the sample's id" -> assertThat(report.sampleId()).as(fact).isEqualTo(sampleId);
                case "report: the test name of the result" ->
                        assertThat(report.testName()).as(fact).isEqualTo(result.testName());
                case "report: the result value of the result" ->
                        assertThat(report.resultValue()).as(fact).isEqualTo(result.resultValue());
                case "report: the time it was generated" -> assertThat(report.generatedAt()).as(fact).isNotNull();
                case "progress: the sample's id" -> assertThat(progress.sampleId()).as(fact).isEqualTo(sampleId);
                case "progress: Report Generated as the current stage" ->
                        assertThat(progress.currentStatus()).as(fact).isEqualTo(WorkflowStage.REPORT_GENERATED);
                case "order: the patient's id" ->
                        assertThat(orderAfter.patientId()).as(fact).isEqualTo(world.patient().id());
                case "order: that sample only, a blood sample at Report Generated" ->
                        assertThat(orderAfter.samples()).as(fact)
                                .extracting("id", "sampleType", "status")
                                .containsExactly(tuple(sampleId, SampleType.BLOOD, WorkflowStage.REPORT_GENERATED));
                default -> throw new IllegalArgumentException("This step does not know how to check: " + fact);
            }
        }
    }

    @Then("the sample's progress lists these stages in order:")
    public void theProgressListsTheStagesInOrder(DataTable stages) {
        List<WorkflowStage> expected = stages.asMaps().stream()
                .map(row -> LabWorld.stageNamed(row.get("stage")))
                .toList();
        assertThat(progress.stages())
                .extracting(StageEntry::status)
                .containsExactlyElementsOf(expected);
        // The old check compared with every stage of the lab; the table must be all of them.
        assertThat(expected).containsExactly(WorkflowStage.values());
    }
}
