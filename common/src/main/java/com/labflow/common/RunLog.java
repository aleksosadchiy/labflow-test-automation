package com.labflow.common;

/**
 * The one name the run log and the scenario hooks share: the SLF4J MDC key that holds the running
 * check's name.
 *
 * <p>The hooks of {@code api-tests} and {@code ui-tests} put {@code <Feature name> › <scenario name>}
 * under this key before a scenario's first step and remove it after its last; the log pattern in
 * each module's {@code logback-test.xml} prints it as {@code %X{check}}, so every line of a scenario
 * can be found by searching for its name. Kept here, beside the other shared names, so both
 * families use the same key.
 */
public final class RunLog {

    /** The MDC key holding the check name; a log pattern reads it as {@code %X{check}}. */
    public static final String CHECK_NAME_KEY = "check";

    private RunLog() {
    }
}
