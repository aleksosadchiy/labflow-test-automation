package com.labflow.common.junit;

// @layer: unit
// @spec: 005-reliable-isolated-execution

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfiguration;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Unit tests for {@link ChecksAtOnceStrategy}, per spec 005 technical-considerations.md §2.4: unset
 * means one check per processor core, {@code checksAtOnce=N} gives a pool of exactly N, and anything
 * that is not a positive whole number stops the run with an exact message.
 *
 * <p>The last tests run a throwaway probe class through the real Jupiter engine with
 * {@link EngineTestKit}: the probe is a static nested class, so Surefire's default excludes never
 * run it as a real test. They set the documented
 * {@code -DchecksAtOnce} system property, so they prove the path an engineer actually uses.
 *
 * @spec: 005-reliable-isolated-execution
 */
class ChecksAtOnceStrategyTest {

    private static final int CORES = Runtime.getRuntime().availableProcessors();

    private final ChecksAtOnceStrategy strategy = new ChecksAtOnceStrategy();

    @BeforeEach
    @AfterEach
    void noChecksAtOnceSystemProperty() {
        System.clearProperty(ChecksAtOnceStrategy.CHECKS_AT_ONCE);
    }

    private ParallelExecutionConfiguration configure(Map<String, String> parameters) {
        return strategy.createConfiguration(new MapParameters(parameters));
    }

    private static void assertPoolOf(int n, ParallelExecutionConfiguration configuration) {
        assertEquals(n, configuration.getParallelism(), "parallelism");
        assertEquals(n, configuration.getCorePoolSize(), "core pool size");
        assertEquals(n, configuration.getMaxPoolSize(), "max pool size");
        assertEquals(n, configuration.getMinimumRunnable(), "minimum runnable");
        assertEquals(30, configuration.getKeepAliveSeconds(), "keep-alive, same as JUnit's built-in strategies");
        assertTrue(configuration.getSaturatePredicate().test(null),
                "the pool must carry on at its maximum instead of adding threads");
    }

    @Test
    @DisplayName("When checksAtOnce is not set, as many checks run at once as the machine has processor cores")
    void defaultsToTheNumberOfCores() {
        assertPoolOf(CORES, configure(Map.of()));
    }

    @Test
    @DisplayName("A blank checksAtOnce counts as not set")
    void blankMeansTheDefault() {
        System.setProperty(ChecksAtOnceStrategy.CHECKS_AT_ONCE, "  ");

        assertPoolOf(CORES, configure(Map.of()));
    }

    @Test
    @DisplayName("-DchecksAtOnce=3 gives a pool of exactly 3")
    void systemPropertySetsTheNumber() {
        System.setProperty(ChecksAtOnceStrategy.CHECKS_AT_ONCE, "3");

        assertPoolOf(3, configure(Map.of()));
    }

    @Test
    @DisplayName("-DchecksAtOnce=1 gives a pool of one, so checks run one after another")
    void oneMeansOneAtATime() {
        System.setProperty(ChecksAtOnceStrategy.CHECKS_AT_ONCE, "1");

        assertPoolOf(1, configure(Map.of()));
    }

    @Test
    @DisplayName("A JUnit configuration parameter named checksAtOnce is read too, and wins over the system property")
    void configurationParameterIsReadFirst() {
        System.setProperty(ChecksAtOnceStrategy.CHECKS_AT_ONCE, "5");

        assertPoolOf(4, configure(Map.of(ChecksAtOnceStrategy.CHECKS_AT_ONCE, "4")));
    }

    @ParameterizedTest(name = "checksAtOnce=''{0}'' is rejected")
    @ValueSource(strings = {"abc", "0", "-2", "1.5"})
    @DisplayName("Anything other than a positive whole number fails with the exact message")
    void badValuesFailWithTheExactMessage(String value) {
        System.setProperty(ChecksAtOnceStrategy.CHECKS_AT_ONCE, value);

        JUnitException failure = assertThrows(JUnitException.class, () -> configure(Map.of()));
        assertEquals("checksAtOnce must be a positive whole number, got '" + value + "'", failure.getMessage());
    }

    private static Map<String, String> parallelRunWithThisStrategy() {
        return Map.of(
                "junit.jupiter.execution.parallel.enabled", "true",
                "junit.jupiter.execution.parallel.mode.default", "concurrent",
                "junit.jupiter.execution.parallel.config.strategy", "custom",
                "junit.jupiter.execution.parallel.config.custom.class", ChecksAtOnceStrategy.class.getName());
    }

    @Test
    @DisplayName("With -DchecksAtOnce=2, the engine runs checks side by side but never more than 2 at the same moment")
    void theEngineRunsNoMoreThanTheChosenNumberAtOnce() {
        System.setProperty(ChecksAtOnceStrategy.CHECKS_AT_ONCE, "2");
        ConcurrencyProbe.reset();

        EngineTestKit.engine("junit-jupiter")
                .configurationParameters(parallelRunWithThisStrategy())
                .selectors(selectClass(ConcurrencyProbe.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(6).succeeded(6));

        int most = ConcurrencyProbe.MOST_AT_ONCE.get();
        assertTrue(most <= 2, "no more than 2 checks may run at once, but " + most + " did");
        assertTrue(most > 1, "the probes should have run side by side, otherwise this proves nothing; most at once: " + most);
    }

    @Test
    @DisplayName("With -DchecksAtOnce=abc, the run stops before any check runs, and the reason carries the exact message")
    void aBadValueStopsTheWholeRun() {
        System.setProperty(ChecksAtOnceStrategy.CHECKS_AT_ONCE, "abc");

        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .configurationParameters(parallelRunWithThisStrategy())
                .selectors(selectClass(ConcurrencyProbe.class))
                .execute();

        assertEquals(0, results.testEvents().started().count(), "no check may start");
        Throwable reason = results.allEvents().failed().stream()
                .map(event -> event.getRequiredPayload(TestExecutionResult.class))
                .map(result -> result.getThrowable().orElseThrow())
                .findFirst()
                .orElseThrow(() -> new AssertionError("the run should have failed"));
        while (reason.getCause() != null) {
            reason = reason.getCause(); // JUnit wraps it in "Could not create configuration for strategy class: ..."
        }
        assertEquals("checksAtOnce must be a positive whole number, got 'abc'", reason.getMessage());
    }

    /**
     * Six throwaway checks, run only through {@link EngineTestKit}. Each counts how many probes are
     * running at the same moment. The first two wait for each other (so a pool of 2 is seen to run
     * them side by side), then every probe stays busy for 20 ms (120 ms in total), long enough for a
     * pool larger than 2 to be caught running more at once.
     */
    static class ConcurrencyProbe {
        static final AtomicInteger RUNNING = new AtomicInteger();
        static final AtomicInteger MOST_AT_ONCE = new AtomicInteger();
        static volatile CountDownLatch firstTwo = new CountDownLatch(2);

        static void reset() {
            RUNNING.set(0);
            MOST_AT_ONCE.set(0);
            firstTwo = new CountDownLatch(2);
        }

        private static void busy() throws InterruptedException {
            MOST_AT_ONCE.accumulateAndGet(RUNNING.incrementAndGet(), Math::max);
            try {
                firstTwo.countDown();
                firstTwo.await(2, TimeUnit.SECONDS);
                Thread.sleep(20);
            } finally {
                RUNNING.decrementAndGet();
            }
        }

        @Test
        void one() throws InterruptedException {
            busy();
        }

        @Test
        void two() throws InterruptedException {
            busy();
        }

        @Test
        void three() throws InterruptedException {
            busy();
        }

        @Test
        void four() throws InterruptedException {
            busy();
        }

        @Test
        void five() throws InterruptedException {
            busy();
        }

        @Test
        void six() throws InterruptedException {
            busy();
        }
    }

    /** Configuration parameters backed by a plain map, as the strategy would see them. */
    private record MapParameters(Map<String, String> values) implements ConfigurationParameters {

        MapParameters {
            values = new HashMap<>(values);
        }

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public Optional<Boolean> getBoolean(String key) {
            return get(key).map(Boolean::parseBoolean);
        }

        @Override
        @Deprecated
        public int size() {
            return values.size();
        }

        @Override
        public Set<String> keySet() {
            return values.keySet();
        }
    }
}
