package com.labflow.tests.steps;

import com.labflow.common.model.Report;
import com.labflow.common.model.TestResult;
import com.labflow.tests.world.LabWorld;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of topic "06. Results and reports". They read the result and the report
 * when they are available and expect a refusal when they are not ready. The set-up ("the lab holds a sample at ...") and the refusal step
 * are shared: see {@link GettingAccessSteps} and {@link PatientsSteps}.
 */
public class ResultsAndReportsSteps {

    private final LabWorld world;

    private TestResult result;
    private Report report;

    public ResultsAndReportsSteps(LabWorld world) {
        this.world = world;
    }

    @When("^a caller asks the lab for the sample's (result|report)$")
    public void aCallerAsksForTheResultOrReport(String asked) {
        String id = world.sample().id();
        if (asked.equals("result")) {
            var answer = world.lab().samples().attemptResult(id);
            world.answerWas(answer);
            result = answer.body().orElse(null);
        } else {
            var answer = world.lab().samples().attemptReport(id);
            world.answerWas(answer);
            report = answer.body().orElse(null);
        }
    }

    @When("a caller asks the lab for the sample's report and then its result")
    public void aCallerAsksForTheReportAndThenTheResult() {
        String id = world.sample().id();
        report = world.lab().samples().report(id);
        result = world.lab().samples().result(id);
    }

    @Then("^the sample was at (.+) when it was asked$")
    public void theSampleWasAt(String stageName) {
        assertThat(world.sample().status()).isEqualTo(LabWorld.stageNamed(stageName));
    }

    @Then("the lab returns the sample's result, with the sample's id, a test name and a result value")
    public void theLabReturnsTheResult() {
        assertThat(result).as("the result the lab returned").isNotNull();
        assertThat(result.sampleId()).isEqualTo(world.sample().id());
        assertThat(result.testName()).isNotBlank();
        assertThat(result.resultValue()).isNotBlank();
    }

    @Then("the lab returns the sample's report, with the sample's id, a test name, a result value and the time it was generated")
    public void theLabReturnsTheReport() {
        assertThat(report.sampleId()).isEqualTo(world.sample().id());
        assertThat(report.testName()).isNotBlank();
        assertThat(report.resultValue()).isNotBlank();
        assertThat(report.generatedAt()).isNotNull();
    }

    @Then("the result the lab returns matches the report")
    public void theResultMatchesTheReport() {
        assertThat(result.sampleId()).isEqualTo(world.sample().id());
        assertThat(result.testName()).isEqualTo(report.testName());
        assertThat(result.resultValue()).isEqualTo(report.resultValue());
    }
}
