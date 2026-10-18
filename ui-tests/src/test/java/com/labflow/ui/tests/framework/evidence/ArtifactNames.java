package com.labflow.ui.tests.framework.evidence;

import java.util.Locale;

/**
 * Turns a test's identifying names into a short, filesystem-safe folder name for its failure
 * artifacts (tech spec §2.5, FS 2.9 ¶2–3). Fed only class and display names by
 * the hooks ({@code UiHooks}): the topic and the scenario's name, which are written in a feature
 * file. The row of an outline comes back separately as the scenario's line number, which the hooks
 * append themselves; this class never sees a value typed on a screen.
 *
 * <p>As a second line of defence, in case a raw display name is ever handed in with a bracketed
 * suffix — the shape a resolved argument list takes, for example {@code "Sign in
 * [I_Like_This_Lab]"} if a {@code name=} pattern ever echoed an argument next to the scenario
 * name — anything from the first {@code '['} onward is dropped before the rest of the
 * sanitising runs. That keeps a password, or any other argument value, out of a folder name even
 * if one slipped past the caller.
 */
public final class ArtifactNames {

    private static final int MAX_LENGTH = 80;
    private static final String FALLBACK = "unnamed";

    private ArtifactNames() {
    }

    /**
     * Sanitises {@code name} into a folder name: lower-cased, with every run of non-alphanumeric
     * characters collapsed to a single {@code -}, leading/trailing {@code -} trimmed, truncated
     * to {@value #MAX_LENGTH} characters, and {@value #FALLBACK} if nothing usable is left.
     */
    public static String sanitise(String name) {
        if (name == null) {
            return FALLBACK;
        }
        String withoutParameterPart = withoutBracketedSuffix(name);
        String lower = withoutParameterPart.toLowerCase(Locale.ROOT);
        String dashed = lower.replaceAll("[^a-z0-9]+", "-");
        String truncated = trimDashes(truncate(trimDashes(dashed)));
        return truncated.isEmpty() ? FALLBACK : truncated;
    }

    private static String withoutBracketedSuffix(String name) {
        int bracket = name.indexOf('[');
        return bracket < 0 ? name : name.substring(0, bracket);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }

    private static String trimDashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}
