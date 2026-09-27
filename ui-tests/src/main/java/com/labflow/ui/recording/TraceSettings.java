package com.labflow.ui.recording;

import com.microsoft.playwright.Tracing;

/**
 * The one place that defines how a screen check's Playwright trace is recorded (tech spec §2.5):
 * screenshots and DOM snapshots on, test sources off.
 *
 * <p>Shared by {@code BrowserExtension}, which starts tracing for every check, and by
 * {@link RecordingPause}, which restarts tracing after the sign-in. Both must use the same
 * options, or a trace resumed after the pause would silently record less (or more) than one
 * started by the extension.
 */
public final class TraceSettings {

    private TraceSettings() {
    }

    /**
     * Returns a fresh set of start options on every call: {@link Tracing.StartOptions} is a
     * mutable builder, so a shared instance could be changed by one caller for everybody.
     */
    public static Tracing.StartOptions startOptions() {
        return new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(false);
    }
}
