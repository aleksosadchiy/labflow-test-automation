package com.labflow.ui.recording;

// @layer: unit
// @spec: 004-ui-automation-page-objects

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.Tracing;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RecordingPause} (tech spec §2.5), against a real headless Chromium:
 * Playwright offers no "is tracing on" query, so the no-op detection relies on real driver
 * behaviour that only a real browser can pin down. No lab is needed; pages are inline HTML.
 *
 * @spec: 004-ui-automation-page-objects
 */
class RecordingPauseTest {

    private static final String SECRET = "Rp_Secret_Value_7719";
    private static final String FORM = "<input id=username><input id=password type=password><button>Go</button>";
    private static final String STUB_SITE = "http://recording-pause.test/";
    private static final String LOGIN_FORM = "<form method=post action=/submit>"
            + "<input id=username name=username><input id=password name=password type=password>"
            + "<button>Sign In</button></form>";

    private static Playwright playwright;
    private static Browser browser;

    private BrowserContext context;
    private Page page;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext();
        page = context.newPage();
        page.setContent(FORM);
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    // @regression
    @DisplayName("With tracing off, the action runs and tracing stays off")
    void tracingOffIsANoOp() {
        AtomicInteger runs = new AtomicInteger();

        RecordingPause.around(page, runs::incrementAndGet);

        assertThat(runs).hasValue(1);
        Path zip = tempDir.resolve("trace.zip");
        assertThatThrownBy(() -> context.tracing().stop(new Tracing.StopOptions().setPath(zip)))
                .as("the pause did not start a trace of its own")
                .isInstanceOf(PlaywrightException.class)
                .hasMessageContaining("Must start tracing before stopping");
        assertThat(zip).doesNotExist();
    }

    @Test
    // @regression
    @DisplayName("With tracing on, the action runs once and recording continues afterwards")
    void tracingOnKeepsRecordingAfterThePause() throws IOException {
        startTracing();
        AtomicInteger runs = new AtomicInteger();

        RecordingPause.around(page, runs::incrementAndGet);
        page.locator("#username").fill("after-resume");
        Path zip = stopTracing();

        assertThat(runs).hasValue(1);
        assertThat(zip).isNotEmptyFile();
        assertThat(traceText(zip)).as("the action after the pause is recorded")
                .contains("after-resume");
    }

    @Test
    // @regression
    @DisplayName("If the action throws, the exception propagates and recording resumes")
    void failingActionStillResumesRecording() throws IOException {
        startTracing();
        IllegalStateException boom = new IllegalStateException("boom");

        assertThatThrownBy(() -> RecordingPause.around(page, () -> {
            throw boom;
        })).isSameAs(boom);
        assertThat(boom.getSuppressed()).isEmpty();

        page.locator("#username").fill("after-failure");
        assertThat(traceText(stopTracing())).as("recording resumed after the failing action")
                .contains("after-failure");
    }

    @Test
    // @regression
    @DisplayName("If the action throws with tracing off, the same exception propagates")
    void failingActionWithTracingOffPropagates() {
        IllegalStateException boom = new IllegalStateException("boom");

        assertThatThrownBy(() -> RecordingPause.around(page, () -> {
            throw boom;
        })).isSameAs(boom);
        assertThat(boom.getSuppressed()).isEmpty();
    }

    /**
     * Evidence for FS 2.9: a password typed and <b>submitted</b> inside the pause appears in no
     * file of the saved trace: not in the action log, not in a DOM snapshot, not in the network
     * log ({@code trace.network} and resource files hold request bodies). The "server" is a
     * {@code context.route(...)} stub, so no lab is needed; routed requests are still recorded in
     * the network log, which the old {@code stopChunk()} pause leaked through.
     *
     * <p>Also pins the trade-off: what was recorded before the pause (the username) is discarded.
     */
    @Test
    // @regression
    @DisplayName("A password submitted while the recording is paused appears nowhere in the saved trace")
    void pausedSubmitLeavesNoTraceOfTheValue() throws IOException {
        context.route(STUB_SITE + "**", route -> route.fulfill(new Route.FulfillOptions()
                .setContentType("text/html")
                .setBody("POST".equals(route.request().method()) ? "<h1>Signed in</h1>" : LOGIN_FORM)));
        startTracing();
        page.navigate(STUB_SITE + "login");
        page.locator("#username").fill("someone-before-pause");

        RecordingPause.around(page, () -> {
            page.locator("#password").fill(SECRET);
            page.locator("button").click();
            page.locator("h1").waitFor();
        });
        page.navigate(STUB_SITE + "after-pause");
        Path zip = stopTracing();

        assertThat(entriesContaining(zip, SECRET)).as("trace entries containing the paused value").isEmpty();
        String trace = traceText(zip);
        assertThat(trace).as("the navigation after the pause is recorded").contains(STUB_SITE + "after-pause");
        assertThat(trace).as("what was recorded before the pause is discarded")
                .doesNotContain("someone-before-pause");
    }

    private void startTracing() {
        context.tracing().start(TraceSettings.startOptions());
    }

    private Path stopTracing() {
        Path zip = tempDir.resolve("trace.zip");
        context.tracing().stop(new Tracing.StopOptions().setPath(zip));
        return zip;
    }

    private static String traceText(Path zip) throws IOException {
        StringBuilder all = new StringBuilder();
        forEachEntry(zip, (name, text) -> all.append(text));
        return all.toString();
    }

    private static List<String> entriesContaining(Path zip, String value) throws IOException {
        List<String> names = new ArrayList<>();
        forEachEntry(zip, (name, text) -> {
            if (text.contains(value)) {
                names.add(name);
            }
        });
        return names;
    }

    private static void forEachEntry(Path zip, EntryVisitor visitor) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                // ISO-8859-1 maps every byte to a char, so binary entries (screenshots) are
                // searched too without decoding errors.
                visitor.visit(entry.getName(), new String(in.readAllBytes(), StandardCharsets.ISO_8859_1));
            }
        }
    }

    @FunctionalInterface
    private interface EntryVisitor {
        void visit(String name, String text);
    }
}
