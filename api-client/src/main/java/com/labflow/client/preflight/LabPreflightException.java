package com.labflow.client.preflight;

/**
 * Thrown by {@link LabPreflight} when the lab is not ready to be tested. The message always
 * starts with the greppable banner {@code LAB PRE-FLIGHT FAILED:} and never contains the
 * access key, headers or response bodies.
 *
 * <p>Unchecked on purpose: it is raised from a test-run start-up hook, where there is
 * nothing sensible to recover to, and it should simply stop the run.
 */
public class LabPreflightException extends RuntimeException {

    public LabPreflightException(String message) {
        super(message);
    }

    public LabPreflightException(String message, Throwable cause) {
        super(message, cause);
    }
}
