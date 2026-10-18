package com.labflow.ui.tests.selfcheck.guards.support;

import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.StepDefinedEvent;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStepFinished;

/**
 * A Cucumber plugin that only listens: it tells {@link DryRun} which step definitions exist, which
 * of them a Gherkin line matched, which lines matched none, and which scenario entries the engine
 * made (an outline row is one entry). Cucumber instantiates a plugin itself, from its class name
 * in {@code cucumber.plugin}, so it cannot be handed its collector; it finds the one the running
 * {@link DryRun} installed. Public with a public constructor because the engine needs both.
 */
public class DryRunRecorder implements EventListener {

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        DryRun.Collector collector = DryRun.currentCollector();
        publisher.registerHandlerFor(StepDefinedEvent.class,
                event -> collector.defined(event.getStepDefinition().getPattern() + " @ " + event.getStepDefinition().getLocation()));
        publisher.registerHandlerFor(TestCaseStarted.class,
                event -> collector.scenario(event.getTestCase().getUri().toString(), event.getTestCase().getLocation().getLine(),
                        event.getTestCase().getName()));
        publisher.registerHandlerFor(TestStepFinished.class, event -> {
            if (!(event.getTestStep() instanceof PickleStepTestStep step)) {
                return;
            }
            Status status = event.getResult().getStatus();
            if (status == Status.UNDEFINED || status == Status.AMBIGUOUS) {
                collector.problem(status, step.getUri().toString(), step.getStep().getLine(),
                        step.getStep().getKeyword() + step.getStep().getText());
            } else {
                collector.used(step.getPattern() + " @ " + step.getCodeLocation());
            }
        });
    }
}
