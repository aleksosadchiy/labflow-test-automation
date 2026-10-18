// @layer: unit
// @spec: 008-report-navigation
// @regression
package com.labflow.ui.tests.selfcheck.framework;

import com.labflow.ui.tests.framework.watchdog.ScenarioWatchdog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A self-check of the framework (no lab, no family label, so it is not counted as a lab check):
 * the watchdog that stands in for the screen checks' 2-minute limit. The limit is a few
 * milliseconds here, so no test waits for 2 minutes.
 */
class ScenarioWatchdogTest {

    private static final Duration SHORT_LIMIT = Duration.ofMillis(50);
    private static final Duration LONG_LIMIT = Duration.ofSeconds(30);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final List<String> reported = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopScheduler() {
        scheduler.shutdownNow();
    }

    /** Runs a "scenario" on its own thread: starts the watch, then blocks as a hung call would. */
    private Thread runScenario(ScenarioWatchdog watchdog, String name, AtomicBoolean wasInterrupted,
                               CountDownLatch finished) {
        Thread scenario = new Thread(() -> {
            ScenarioWatchdog.Watch watch = watchdog.start(name);
            try {
                new CountDownLatch(1).await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                wasInterrupted.set(true);
            } finally {
                watch.cancel();
                finished.countDown();
            }
        }, "test-scenario");
        scenario.setDaemon(true);
        scenario.start();
        return scenario;
    }

    @Test
    @DisplayName("A scenario that runs past the limit is reported and its thread is interrupted")
    void firesWhenTheLimitPasses() throws InterruptedException {
        ScenarioWatchdog watchdog = new ScenarioWatchdog(SHORT_LIMIT, scheduler, reported::add);
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);

        runScenario(watchdog, "Topic › A hung scenario", interrupted, finished);

        assertThat(finished.await(5, TimeUnit.SECONDS)).as("the hung scenario was freed").isTrue();
        assertThat(interrupted).as("the scenario's thread was interrupted").isTrue();
        assertThat(reported).as("a message and a thread dump were reported").hasSize(2);
        assertThat(reported.get(1)).startsWith("Thread dump:").contains("test-scenario");
    }

    @Test
    @DisplayName("A scenario that ends before the limit is left alone and nothing is reported")
    void doesNotFireWhenTheScenarioEndsInTime() throws InterruptedException {
        ScenarioWatchdog watchdog = new ScenarioWatchdog(SHORT_LIMIT, scheduler, reported::add);
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);

        Thread scenario = new Thread(() -> {
            ScenarioWatchdog.Watch watch = watchdog.start("Topic › A quick scenario");
            watch.cancel();
            try {
                new CountDownLatch(1).await(300, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            finished.countDown();
        });
        scenario.setDaemon(true);
        scenario.start();

        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted).as("a cancelled watch must not interrupt").isFalse();
        assertThat(reported).as("a cancelled watch must not report").isEmpty();
    }

    @Test
    @DisplayName("The report names the scenario that ran too long")
    void namesTheScenario() throws InterruptedException {
        ScenarioWatchdog watchdog = new ScenarioWatchdog(SHORT_LIMIT, scheduler, reported::add);
        CountDownLatch finished = new CountDownLatch(1);

        runScenario(watchdog, "01. Getting access › A wrong password is refused", new AtomicBoolean(), finished);

        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reported).isNotEmpty();
        assertThat(reported.get(0))
                .contains("01. Getting access › A wrong password is refused")
                .contains(SHORT_LIMIT.toString());
    }

    @Test
    @DisplayName("A watch cancelled long before its limit never reaches the limit")
    void aLongLimitIsNotReachedByACancelledWatch() {
        ScenarioWatchdog watchdog = new ScenarioWatchdog(LONG_LIMIT, scheduler, reported::add);
        watchdog.start("Topic › Any scenario").cancel();
        assertThat(reported).isEmpty();
    }
}
