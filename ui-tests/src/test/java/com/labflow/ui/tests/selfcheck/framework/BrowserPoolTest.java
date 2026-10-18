// @layer: integration
// @spec: 008-report-navigation
// @regression
package com.labflow.ui.tests.selfcheck.framework;

import com.labflow.ui.tests.framework.browser.BrowserPool;
import com.labflow.ui.tests.world.UiWorld;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.labflow.ui.tests.hooks.UiHooks;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * A self-check of the framework (plain Jupiter, no family label, so it is not counted as a lab
 * check): {@link BrowserPool} as the scenario hooks use it. Each worker thread has its own browser, reuses it for every later
 * scenario, and every browser is closed by the run's final hook.
 *
 * <p>The scenarios are the hooks' own {@code openAFreshPage} calls on threads of this test, with no
 * lab and no Cucumber run. A browser is identified from the outside, through the page the hook
 * opened, and "closed" is asked of the browser ({@link Browser#isConnected()}). Pool threads are
 * created here and ended here, so no thread of the test run keeps a browser handle behind.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("browser-pool")
class BrowserPoolTest {

    private static final int WORKERS = 3;
    private static final int SCENARIOS_PER_WORKER = 2;

    private ExecutorService workers;

    @AfterEach
    void endWorkers() {
        if (workers != null) {
            workers.shutdownNow();
        }
        BrowserPool.closeAll();
    }

    @Test
    @DisplayName("Three workers at once get three browsers, one each, reused by their later scenarios, and all are closed at the end")
    void oneBrowserPerWorkerThread() throws Exception {
        workers = Executors.newFixedThreadPool(WORKERS);
        CyclicBarrier together = new CyclicBarrier(WORKERS);
        Set<Browser> browsers = ConcurrentHashMap.newKeySet();

        List<Future<Set<Browser>>> perWorker = new java.util.ArrayList<>();
        for (int i = 0; i < WORKERS; i++) {
            perWorker.add(workers.submit(scenarioRun(SCENARIOS_PER_WORKER, together)));
        }
        for (Future<Set<Browser>> future : perWorker) {
            Set<Browser> usedByThisWorker = future.get(60, TimeUnit.SECONDS);
            assertThat(usedByThisWorker).as("a worker reuses its own browser for all its scenarios").hasSize(1);
            browsers.addAll(usedByThisWorker);
        }

        assertThat(browsers).as("one browser per worker thread: three workers, three browsers").hasSize(WORKERS);

        UiHooks.closeTheBrowsers();

        assertThat(browsers).as("the final hook closes every browser").noneMatch(Browser::isConnected);
    }

    @Test
    @DisplayName("One worker running several scenarios one after another uses a single browser")
    void oneWorkerSharesOneBrowser() throws Exception {
        workers = Executors.newSingleThreadExecutor();

        Set<Browser> browsers = workers.submit(scenarioRun(3, null)).get(60, TimeUnit.SECONDS);

        assertThat(browsers).as("a single worker thread uses a single browser for all its scenarios").hasSize(1);
        UiHooks.closeTheBrowsers();
        assertThat(browsers).as("the browser is closed by the final hook").noneMatch(Browser::isConnected);
    }

    /**
     * A worker: runs {@code count} scenarios through the hooks and returns the browsers they used.
     * The first scenario waits at the barrier (when there is one) so that all workers are alive,
     * and therefore on different threads, at the same moment.
     */
    private static Callable<Set<Browser>> scenarioRun(int count, CyclicBarrier together) {
        return () -> {
            Set<Browser> used = ConcurrentHashMap.newKeySet();
            for (int i = 0; i < count; i++) {
                UiWorld world = new UiWorld();
                UiHooks hooks = new UiHooks(world);
                hooks.openAFreshPage();
                try {
                    used.add(world.page().context().browser());
                    if (i == 0 && together != null) {
                        together.await(60, TimeUnit.SECONDS);
                    }
                } finally {
                    world.browserContext().close();
                }
            }
            return used;
        };
    }
}
