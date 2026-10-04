package com.labflow.common.junit;

/**
 * The tag names behind the label annotations, as typed in {@code -Dgroups=...}.
 *
 * <p>The annotations use these constants, so code that needs a tag name (for example a
 * listener counting {@code api} and {@code ui} checks) refers to the same string.
 */
public final class Labels {

    /** A workflow check through the connected interface. See {@link ApiCheck}. */
    public static final String API = "api";

    /** A screen check in a browser. See {@link UiCheck}. */
    public static final String UI = "ui";

    /** One of the few key paths run for a quick confidence check. See {@link Smoke}. */
    public static final String SMOKE = "smoke";

    /** Part of the full regression run. See {@link Regression}. */
    public static final String REGRESSION = "regression";

    private Labels() {
    }
}
