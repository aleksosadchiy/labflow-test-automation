package com.labflow.common.junit;

// @layer: unit
// @spec: 005-reliable-isolated-execution

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Unit tests for {@link CheckNameInterceptor}, per spec 005 technical-considerations.md §2.8: the
 * check name is in the MDC on the thread that actually runs the check body - including under the
 * {@code SEPARATE_THREAD} timeout mode {@code ui-tests} uses - and is cleared afterwards.
 *
 * <p>Runs throwaway probe checks through the real Jupiter engine with {@link EngineTestKit}, the
 * same pattern as {@code BrowserExtensionArtifactsTest}. The probes are static nested classes, so
 * Surefire's default excludes ({@code **}{@code /*$*}) never run them as real tests. Each probe
 * writes what it saw into {@link #SEEN}; this test reads it back.
 *
 * @spec: 005-reliable-isolated-execution
 */
class CheckNameInterceptorTest {

    /** What a probe saw: which method kind ran, the MDC check name, and the thread it ran on. */
    record Seen(String phase, String check, Thread thread) {
    }

    private static final List<Seen> SEEN = new CopyOnWriteArrayList<>();

    private static void record(String phase) {
        SEEN.add(new Seen(phase, MDC.get(CheckNameInterceptor.MDC_KEY), Thread.currentThread()));
    }

    private static final String TIMED_CHECK = "TimedProbe › probe: records the check name it runs under";

    @BeforeEach
    @AfterEach
    void startAndEndWithACleanSlate() {
        SEEN.clear();
        MDC.remove(CheckNameInterceptor.MDC_KEY);
    }

    private static void run(Class<?> probe, Map<String, String> configuration) {
        EngineTestKit.engine("junit-jupiter")
                .configurationParameters(configuration)
                .selectors(selectClass(probe))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.failed(0));
    }

    private static Seen seenIn(String phase) {
        List<Seen> matching = SEEN.stream().filter(seen -> seen.phase().equals(phase)).toList();
        assertEquals(1, matching.size(), "expected the probe to record its " + phase + " exactly once: " + SEEN);
        return matching.get(0);
    }

    @Test
    @DisplayName("With default settings, the check body, its before-each and its after-each all run under the check's name")
    void checkNameIsSetWithDefaultSettings() {
        run(TimedProbe.class, Map.of());

        assertEquals(TIMED_CHECK, seenIn("body").check());
        assertEquals(TIMED_CHECK, seenIn("beforeEach").check());
        assertEquals(TIMED_CHECK, seenIn("afterEach").check());
    }

    @Test
    @DisplayName("Under a SEPARATE_THREAD time limit, the check body runs on another thread and still carries the check's name")
    void checkNameReachesTheBodyThreadUnderSeparateThreadTimeout() {
        run(TimedProbe.class, Map.of(
                "junit.jupiter.execution.timeout.default", "5s",
                "junit.jupiter.execution.timeout.thread.mode.default", "SEPARATE_THREAD"));

        Seen body = seenIn("body");
        assertNotSame(Thread.currentThread(), body.thread(),
                "SEPARATE_THREAD should run the body on a helper thread, otherwise this test proves nothing");
        assertEquals(TIMED_CHECK, body.check(), "the check name did not reach the thread that ran the body");
        assertEquals(TIMED_CHECK, seenIn("beforeEach").check());
        assertEquals(TIMED_CHECK, seenIn("afterEach").check());
    }

    @Test
    @DisplayName("After a check finishes, its name is removed from the thread that ran it")
    void checkNameIsClearedAfterTheCheck() {
        run(TimedProbe.class, Map.of());

        assertSame(Thread.currentThread(), seenIn("body").thread(),
                "with default settings the engine runs the body on this thread, so this thread's MDC is the one to check");
        assertNull(MDC.get(CheckNameInterceptor.MDC_KEY), "the check name was left behind in the MDC");
    }

    @Test
    @DisplayName("A value already in the MDC before a check is put back after it")
    void previousValueIsRestoredAfterTheCheck() {
        MDC.put(CheckNameInterceptor.MDC_KEY, "outer value");

        run(TimedProbe.class, Map.of());

        assertEquals(TIMED_CHECK, seenIn("body").check());
        assertEquals("outer value", MDC.get(CheckNameInterceptor.MDC_KEY));
    }

    @Test
    @DisplayName("Each row of a check with many inputs runs under its own row name")
    void eachParameterizedRowGetsItsOwnName() {
        run(RowsProbe.class, Map.of());

        List<String> rowNames = SEEN.stream().map(Seen::check).sorted().collect(Collectors.toList());
        assertEquals(List.of("RowsProbe › first row", "RowsProbe › second row"), rowNames);
    }

    /** A throwaway check, run only through {@link EngineTestKit}, never by Surefire's own discovery. */
    @ExtendWith(CheckNameInterceptor.class)
    static class TimedProbe {
        @BeforeEach
        void before() {
            record("beforeEach");
        }

        @Test
        @DisplayName("probe: records the check name it runs under")
        void body() {
            record("body");
        }

        @AfterEach
        void after() {
            record("afterEach");
        }
    }

    /** A throwaway check with many inputs, run only through {@link EngineTestKit}. */
    @ExtendWith(CheckNameInterceptor.class)
    static class RowsProbe {
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"first row", "second row"})
        @DisplayName("probe: records the check name each row runs under")
        void row(String input) {
            record("row " + input);
        }
    }
}
