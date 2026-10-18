package com.labflow.ui.tests.framework.watchdog;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The time limit of a screen scenario. The Jupiter checks had a 2-minute limit
 * ({@code junit.jupiter.execution.timeout.default}); Cucumber has none, so this watchdog stands in
 * for it: {@link #start} is called when a scenario starts and {@link Watch#cancel()} when it ends.
 * If the limit passes first, the watchdog logs the scenario's name and a dump of every thread,
 * then interrupts the scenario's thread.
 *
 * <p>An interrupt only frees a call that checks for it. A call that ignores it (some Playwright
 * driver calls) stays stuck, and only Surefire's fork limit ends the run. That is the accepted
 * trade-off of the design (spec 008, technical considerations 2.9).
 *
 * <p>The limit, the scheduler and the place the report goes to are passed in, so the unit tests
 * run in milliseconds. {@link #forScenarios()} gives the one shared instance the hooks use: a
 * single daemon scheduler thread for the whole JVM, so nothing needs shutting down.
 */
public final class ScenarioWatchdog {

    /** The limit the Jupiter checks had, and the one the scenarios keep. */
    public static final Duration SCENARIO_LIMIT = Duration.ofMinutes(2);

    private static final Logger LOG = LoggerFactory.getLogger(ScenarioWatchdog.class);

    private static final ScenarioWatchdog SHARED =
            new ScenarioWatchdog(SCENARIO_LIMIT, daemonScheduler(), LOG::error);

    private final Duration limit;
    private final ScheduledExecutorService scheduler;
    private final Consumer<String> report;

    public ScenarioWatchdog(Duration limit, ScheduledExecutorService scheduler, Consumer<String> report) {
        this.limit = limit;
        this.scheduler = scheduler;
        this.report = report;
    }

    /** The watchdog of the run: 2 minutes, reporting to the run log. */
    public static ScenarioWatchdog forScenarios() {
        return SHARED;
    }

    /**
     * Starts watching the calling thread, which is the thread the scenario runs on.
     *
     * @param scenarioName the name the run log uses for the scenario, repeated in the report
     */
    public Watch start(String scenarioName) {
        Thread scenarioThread = Thread.currentThread();
        Watch watch = new Watch(scenarioThread);
        ScheduledFuture<?> timer = scheduler.schedule(
                () -> watch.expire(scenarioName), limit.toMillis(), TimeUnit.MILLISECONDS);
        watch.timer = timer;
        return watch;
    }

    private String describe(String scenarioName) {
        return "Scenario '" + scenarioName + "' ran longer than " + limit + "; interrupting its thread";
    }

    private static String threadDump() {
        StringBuilder dump = new StringBuilder("Thread dump:");
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            dump.append("\n\"").append(entry.getKey().getName()).append("\" ").append(entry.getKey().getState());
            for (StackTraceElement frame : entry.getValue()) {
                dump.append("\n    at ").append(frame);
            }
        }
        return dump.toString();
    }

    private static ScheduledExecutorService daemonScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "scenario-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /** One scenario under watch. */
    public final class Watch {

        private enum State { WATCHING, EXPIRED, CANCELLED }

        private final Thread scenarioThread;
        private final AtomicReference<State> state = new AtomicReference<>(State.WATCHING);
        private volatile ScheduledFuture<?> timer;

        private Watch(Thread scenarioThread) {
            this.scenarioThread = scenarioThread;
        }

        /**
         * The scenario ended in time: the timer is dropped. If the limit had already passed, the
         * interrupt it sent is cleared, so it cannot leak into the next scenario on this thread.
         */
        public void cancel() {
            if (state.compareAndSet(State.WATCHING, State.CANCELLED)) {
                ScheduledFuture<?> pending = timer;
                if (pending != null) {
                    pending.cancel(false);
                }
            } else if (state.get() == State.EXPIRED && Thread.currentThread() == scenarioThread) {
                Thread.interrupted();
            }
        }

        private void expire(String scenarioName) {
            if (!state.compareAndSet(State.WATCHING, State.EXPIRED)) {
                return;
            }
            report.accept(describe(scenarioName));
            report.accept(threadDump());
            scenarioThread.interrupt();
        }
    }
}
