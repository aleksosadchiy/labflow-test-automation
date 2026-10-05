// @layer: integration
// @spec: 005-reliable-isolated-execution
// @regression
package com.labflow.ui.tests.support;

import com.microsoft.playwright.Browser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Proves the browser rule of spec 005 FS 2.3 for screen checks: each worker thread has its own
 * browser, a thread reuses it for every later check, and every browser is closed once the run is
 * over.
 *
 * <p>Runs a throwaway probe class through the real {@code JupiterTestEngine} with {@link
 * EngineTestKit}, so {@link BrowserExtension} behaves exactly as for a real check. The probe
 * extends {@link UiTest} but never touches the lab; each of its six checks only records which
 * {@link Browser} its page belongs to. Nothing is read from the extension's internals: the
 * browsers are identified from the outside, through the page the extension hands to the check, and
 * "closed" is asked of the browser itself ({@link Browser#isConnected()}).
 *
 * <p>Side by side: the run is given {@code checksAtOnce=3}. The first three checks to start wait for each other at a barrier, so
 * the test only passes if three checks really run at the same moment, on three different worker
 * threads; the other three run afterwards on those same threads. Exactly three browsers must
 * result, not six (a browser per check) and not one (a shared browser). If the checks did not
 * overlap, the barrier would time out and the probe would fail.
 *
 * <p>One at a time (the negative counterpart): with parallel execution switched off, all six checks
 * run on one thread and must share exactly one browser.
 *
 * <p>{@link Execution @Execution(SAME_THREAD)}: the two tests share the probe's static state, so
 * when the whole run goes side by side they must not run beside each other (the class itself may
 * still run beside other classes). Both set the nested engine's parallel settings explicitly, so
 * the outer run's settings cannot change what they prove.
 */
@Execution(ExecutionMode.SAME_THREAD)
class BrowserPerWorkerTest {

    private static final int CHECKS = 6;
    private static final int AT_ONCE = 3;

    @BeforeEach
    void resetProbe() {
        CountingProbe.reset();
    }

    @Test
    @DisplayName("When checks run three at a time, then there are three browsers, one per worker thread, and all are closed after the run")
    void oneBrowserPerWorkerThread() {
        CountingProbe.overlap = true;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(CountingProbe.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
                .configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "custom")
                .configurationParameter("junit.jupiter.execution.parallel.config.custom.class",
                        "com.labflow.common.junit.ChecksAtOnceStrategy")
                .configurationParameter("junit.jupiter.execution.parallel.config.checksAtOnce", String.valueOf(AT_ONCE))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(CHECKS).succeeded(CHECKS).failed(0));

        assertThat(CountingProbe.browsers)
                .as("one browser per worker thread: three checks ran at once, the rest reused those threads' browsers")
                .hasSize(AT_ONCE);
        assertThat(CountingProbe.browsers)
                .as("every browser is closed after the last check")
                .noneMatch(Browser::isConnected);
    }

    @Test
    @DisplayName("When checks run one at a time, then they all share one browser, which is closed after the run")
    void oneThreadSharesOneBrowser() {
        CountingProbe.overlap = false;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(CountingProbe.class))
                // Said out loud, so a run that turns parallel execution on for the whole project
                // (junit-platform.properties or -D) cannot turn this one-thread case parallel too.
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(CHECKS).succeeded(CHECKS).failed(0));

        assertThat(CountingProbe.browsers)
                .as("a single worker thread uses a single browser for all its checks")
                .hasSize(1);
        assertThat(CountingProbe.browsers)
                .as("the browser is closed after the last check")
                .noneMatch(Browser::isConnected);
    }

    /**
     * A throwaway check, run only through {@link EngineTestKit}, never by Surefire's own
     * discovery. Extends {@link UiTest} only, like every real screen check (the extension is
     * inherited; declaring it again would open two contexts per check).
     */
    public static final class CountingProbe extends UiTest {

        static final Set<Browser> browsers = ConcurrentHashMap.newKeySet();
        static volatile boolean overlap;
        private static final AtomicInteger started = new AtomicInteger();
        private static final CyclicBarrier together = new CyclicBarrier(AT_ONCE);

        static void reset() {
            browsers.clear();
            started.set(0);
            together.reset();
        }

        @RepeatedTest(value = CHECKS, name = "probe: records its browser, check {currentRepetition}")
        void recordsItsBrowser() throws Exception {
            browsers.add(page.context().browser());
            if (overlap && started.getAndIncrement() < AT_ONCE) {
                together.await(30, TimeUnit.SECONDS);
            }
        }
    }
}
