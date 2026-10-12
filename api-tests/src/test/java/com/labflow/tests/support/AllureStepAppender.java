package com.labflow.tests.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.qameta.allure.Allure;

/**
 * Shows every request a check makes as a step in its Allure report, by forwarding the run log's
 * request lines ({@code POST /api/samples -> 201 in 34 ms [patient=… sample=…]}) to Allure.
 *
 * <p>Why a Logback appender rather than Allure's own RestAssured filter: that filter attaches
 * the raw request and answer, including the {@code Authorization} header and patient names. The
 * run log line is already safe to share (see {@code RequestLogFilter} and {@code
 * RunLogSecrecyTest}), so reusing it keeps one place that decides what may be written down, and
 * keeps {@code api-client} free of any reporting library.
 *
 * <p>Attached to the {@code lab.http} logger only, in {@code logback-test.xml}. Only INFO lines
 * are forwarded: the DEBUG lines are the request bodies ({@code -DlogBodies=true}), which stay
 * in the log file. A line logged while no check is running (the pre-flight) is skipped. Allure
 * keeps the running check per thread, and a workflow check runs its body on the thread that
 * JUnit started it on, so the step lands on the right check under parallel execution too.
 */
public class AllureStepAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel().isGreaterOrEqual(Level.INFO) && aCheckIsRunning()) {
            addStep(event.getFormattedMessage());
        }
    }

    /** Whether Allure has a running check on this thread. Overridden by the unit test. */
    protected boolean aCheckIsRunning() {
        return Allure.getLifecycle().getCurrentTestCase().isPresent();
    }

    /** Adds one finished, passed step with this text to the running check. Overridden by the unit test. */
    protected void addStep(String text) {
        Allure.step(text);
    }
}
