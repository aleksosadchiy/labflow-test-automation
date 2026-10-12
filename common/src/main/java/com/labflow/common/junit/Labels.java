package com.labflow.common.junit;

import org.junit.platform.engine.TestTag;

import java.util.Set;

/**
 * The tag names behind the label annotations, as typed in {@code -Dgroups=...}.
 *
 * <p>The annotations use these constants, so code that needs a tag name (for example a
 * listener counting {@code api} and {@code ui} checks) refers to the same string. The rule for
 * "this is a lab check" is here too, so every listener that needs it asks the same question.
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

    /**
     * Whether these tags belong to a lab check: one of them is {@value #API} or {@value #UI}.
     *
     * <p>The framework's own self-checks carry neither tag. {@link CheckCountListener} and
     * {@link LabChecksAllureListener} both use this one rule, so the number of checks the run
     * says it executed and the number of checks in the report cannot drift apart.
     *
     * @param tags the tags of one test or container, as given by {@code TestIdentifier.getTags()}
     */
    public static boolean isLabCheck(Set<TestTag> tags) {
        return tags.stream()
                .anyMatch(tag -> tag.getName().equals(API) || tag.getName().equals(UI));
    }
}
