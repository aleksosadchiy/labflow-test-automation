package com.labflow.tests.steps;

import com.labflow.client.http.LabResponse;
import com.labflow.client.service.SampleService;
import com.labflow.common.model.WorkflowStage;
import com.labflow.testdata.UniqueData;
import com.labflow.tests.world.LabWorld;
import io.cucumber.java.en.When;

/**
 * Steps of topic "07. Unknown samples". They ask for samples the lab has never
 * issued and expect a refusal. The set-up ("the lab holds nothing yet") and the refusal step
 * are shared: see {@link GettingAccessSteps} and {@link PatientsSteps}.
 */
public class UnknownSamplesSteps {

    private final LabWorld world;

    public UnknownSamplesSteps(LabWorld world) {
        this.world = world;
    }

    @When("^a caller asks the lab to (look up|advance|show the progress of|give the result of|give the report of) a sample it does not know$")
    public void aCallerAsksAboutAnUnknownSample(String request) {
        String unknownSampleId = UniqueData.name("unknown-sample");
        SampleService samples = world.lab().samples();
        LabResponse<?> answer = switch (request) {
            case "look up" -> samples.attemptFind(unknownSampleId);
            // The stage asked for is the valid next one, so the only thing wrong is the missing sample.
            case "advance" -> samples.attemptMoveTo(unknownSampleId, WorkflowStage.READY_FOR_PROCESSING);
            case "show the progress of" -> samples.attemptProgress(unknownSampleId);
            case "give the result of" -> samples.attemptResult(unknownSampleId);
            case "give the report of" -> samples.attemptReport(unknownSampleId);
            default -> throw new IllegalArgumentException("No such request in this topic: " + request);
        };
        world.answerWas(answer);
    }
}
