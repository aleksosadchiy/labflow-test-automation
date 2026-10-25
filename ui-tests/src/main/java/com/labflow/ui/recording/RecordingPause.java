package com.labflow.ui.recording;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Tracing;

/**
 * Runs one action with the Playwright trace recording switched off, so a secret used during that
 * action (the real password) cannot end up in the saved trace (tech spec §2.5, FS 2.9).
 *
 * <p>Playwright has no built-in masking, and a trace can carry the password in three places:
 * <ol>
 *   <li>the <b>action log</b>, which records every {@code fill()} argument verbatim;</li>
 *   <li>the <b>DOM snapshots</b> taken around each action, which record input values, including
 *       {@code type=password} fields ({@code __playwright_value_});</li>
 *   <li>the <b>network log</b> ({@code trace.network} plus resource files), which records the
 *       sign-in form's POST body.</li>
 * </ol>
 * Pausing with {@code stopChunk()}/{@code startChunk()} only covers the first two: the network
 * log belongs to the whole trace, not to a chunk, so the POST body survived. This class therefore
 * ends the trace and starts a new one:
 * <ol>
 *   <li>{@code tracing.stop()} with no path ends tracing and <b>discards</b> everything recorded
 *       so far in this test, network log included;</li>
 *   <li>the action runs unrecorded;</li>
 *   <li>{@code tracing.start(TraceSettings.startOptions())} starts a new trace with the same
 *       options, also when the action throws (the action's exception wins; a failure to resume
 *       is attached to it as suppressed).</li>
 * </ol>
 * Trade-off: whatever was recorded before the pause is lost, so for a check that signs in, the
 * saved trace starts after the sign-in. The caller keeps the pause open until the screen holding
 * the secret is gone (see {@code LoginPage.signIn}).
 *
 * <p>No-op when tracing is not running (just runs the action). Playwright has no "is tracing on"
 * query, and {@code tracing.stop()} without a path silently does nothing when tracing was never
 * started, so it cannot tell the two cases apart. {@code tracing.startChunk()} can: in
 * Playwright 1.63 (verified by experiment) and 1.64 (pinned by {@code RecordingPauseTest}) it
 * throws a {@link PlaywrightException} containing {@value #NOT_TRACING} when tracing is off, and
 * when tracing is on it discards the current chunk and begins a new one, which the following
 * {@code stop()} discards anyway. Only that exception is swallowed.
 */
public final class RecordingPause {

    static final String NOT_TRACING = "Must start tracing before starting a new chunk";

    private RecordingPause() {
    }

    /** Runs {@code action} once, with the trace recording of {@code page}'s context off around it. */
    public static void around(Page page, Runnable action) {
        Tracing tracing = page.context().tracing();
        if (!isTracing(tracing)) {
            action.run();
            return;
        }
        tracing.stop();
        try {
            action.run();
        } catch (RuntimeException | Error actionFailure) {
            try {
                tracing.start(TraceSettings.startOptions());
            } catch (RuntimeException resumeFailure) {
                actionFailure.addSuppressed(resumeFailure);
            }
            throw actionFailure;
        }
        tracing.start(TraceSettings.startOptions());
    }

    /** Probes whether tracing is running; see the class comment for why this uses {@code startChunk()}. */
    private static boolean isTracing(Tracing tracing) {
        try {
            tracing.startChunk();
            return true;
        } catch (PlaywrightException e) {
            if (e.getMessage() != null && e.getMessage().contains(NOT_TRACING)) {
                return false;
            }
            throw e;
        }
    }
}
