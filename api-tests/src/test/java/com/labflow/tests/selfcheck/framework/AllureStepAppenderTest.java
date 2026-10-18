// @layer: unit
// @spec: 007-allure-report
// @regression
package com.labflow.tests.selfcheck.framework;

import com.labflow.tests.framework.log.AllureStepAppender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit checks for the appender that turns request lines into Allure steps. Allure itself is
 * replaced by a recording subclass, so nothing here touches the report of the running suite.
 * Needs no lab.
 */
class AllureStepAppenderTest {

    private static final Logger LOGGER = new LoggerContext().getLogger("lab.http");

    @Test
    @DisplayName("A request line logged while a check is running becomes one report step with the same text")
    void requestLineBecomesAStep() {
        RecordingAppender appender = new RecordingAppender(true);

        appender.doAppend(event(Level.INFO, "POST /api/samples -> 201 in 34 ms [sample=SMP-1]"));

        assertThat(appender.steps).containsExactly("POST /api/samples -> 201 in 34 ms [sample=SMP-1]");
    }

    @Test
    @DisplayName("A request body, which is logged at DEBUG, never becomes a report step")
    void bodiesStayOutOfTheReport() {
        RecordingAppender appender = new RecordingAppender(true);

        appender.doAppend(event(Level.DEBUG, "request body: {\"name\":\"***\"}"));

        assertThat(appender.steps).isEmpty();
    }

    @Test
    @DisplayName("A line logged while no check is running, such as the pre-flight, is not turned into a step")
    void linesOutsideACheckAreSkipped() {
        RecordingAppender appender = new RecordingAppender(false);

        appender.doAppend(event(Level.INFO, "GET /health -> 200 in 3 ms"));

        assertThat(appender.steps).isEmpty();
    }

    private static LoggingEvent event(Level level, String message) {
        return new LoggingEvent(AllureStepAppenderTest.class.getName(), LOGGER, level, message, null, null);
    }

    /** The appender with Allure swapped for a list. */
    private static final class RecordingAppender extends AllureStepAppender {
        private final boolean checkRunning;
        private final List<String> steps = new ArrayList<>();

        private RecordingAppender(boolean checkRunning) {
            this.checkRunning = checkRunning;
            start();
        }

        @Override
        protected boolean aCheckIsRunning() {
            return checkRunning;
        }

        @Override
        protected void addStep(String text) {
            steps.add(text);
        }
    }
}
