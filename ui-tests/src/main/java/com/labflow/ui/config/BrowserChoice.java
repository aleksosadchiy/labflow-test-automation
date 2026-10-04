package com.labflow.ui.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The browser the screen checks run in. It comes from outside the project, so the same checks
 * run in another browser without editing anything.
 *
 * <p>The {@code -Dbrowser=...} system property, then the {@code BROWSER} environment variable,
 * then {@code chromium}. A blank value counts as unset and surrounding whitespace is trimmed.
 * The name is matched ignoring case; an unknown name stops the run with
 * {@code Unsupported browser 'x'. Supported: chromium, webkit}. (Some dev containers
 * set {@code BROWSER} to a helper program's path; that error catches it.)
 *
 * <p>Chromium is installed by default; each other browser is installed once with
 * {@link #installCommand()}.
 */
public enum BrowserChoice {

    /** The default. */
    CHROMIUM,

    WEBKIT;

    /** System property that sets the browser ({@code -Dbrowser=...}). */
    public static final String BROWSER_PROPERTY = "browser";

    /** Environment variable that sets the browser when the system property is absent. */
    public static final String BROWSER_ENV = "BROWSER";

    /** The name as the engineer types it and as the run output shows it: {@code chromium}, ... */
    public String displayName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The one-time command, run from the repository root, that downloads this browser. */
    public String installCommand() {
        return "./mvnw -N exec:java -Dexec.args=\"install " + displayName() + "\"";
    }

    /** The {@link #displayName()}, so the browser prints the way it is typed. */
    @Override
    public String toString() {
        return displayName();
    }

    /**
     * Resolves the browser. The lookups are passed in (each returns null when a name is unset)
     * so precedence can be unit-tested without touching the real process environment.
     *
     * @param systemProperties looks up a system property by name
     * @param environment      looks up an environment variable by name
     * @throws IllegalArgumentException if the name is not a supported browser
     */
    public static BrowserChoice load(Function<String, String> systemProperties, Function<String, String> environment) {
        String name = firstNonBlank(systemProperties.apply(BROWSER_PROPERTY), environment.apply(BROWSER_ENV));
        return name == null ? CHROMIUM : fromName(name);
    }

    /** {@link #load} against the real system properties and environment variables. */
    public static BrowserChoice fromEnvironment() {
        return load(System::getProperty, System::getenv);
    }

    /**
     * Finds the browser by name, ignoring case and surrounding whitespace.
     *
     * @param name the name as given, for example {@code "chromium"} or {@code "WebKit"}
     * @throws IllegalArgumentException if no browser has that name; the message names the
     *                                  value as given and lists the supported names
     */
    public static BrowserChoice fromName(String name) {
        String wanted = name.strip();
        for (BrowserChoice browser : values()) {
            if (browser.displayName().equalsIgnoreCase(wanted)) {
                return browser;
            }
        }
        throw new IllegalArgumentException("Unsupported browser '" + name + "'. Supported: " + supportedNames());
    }

    private static String supportedNames() {
        return Arrays.stream(values()).map(BrowserChoice::displayName).collect(Collectors.joining(", "));
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
