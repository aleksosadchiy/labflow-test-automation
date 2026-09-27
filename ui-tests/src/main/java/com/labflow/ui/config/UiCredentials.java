package com.labflow.ui.config;

import java.util.function.Function;

/**
 * The details the screen checks sign in to the lab with. Both come from outside the project
 * (nothing is edited to change them) and fall back to the lab's standard local values.
 *
 * <p><b>Username</b>: the {@code MOCK_LAB_USERNAME} environment variable, then
 * {@value #DEFAULT_USERNAME}. These are the same variable names the lab itself reads.
 *
 * <p><b>Password</b>: the {@code MOCK_LAB_PASSWORD} environment variable <em>only</em>, then
 * {@value #DEFAULT_PASSWORD} (the lab's documented local default, not a secret). There is
 * deliberately no system property for the password, the same rule {@code LabConfig} applies
 * to the access key: a {@code -D...=...} value is written into Surefire's XML report and is
 * visible in the process list, while an environment variable is neither.
 *
 * <p>A blank value counts as unset.
 *
 * @param username the sign-in username
 * @param password the sign-in password, wrapped so it cannot be printed
 */
public record UiCredentials(String username, Password password) {

    /** Environment variable that sets the sign-in username. */
    public static final String USERNAME_ENV = "MOCK_LAB_USERNAME";

    /** Environment variable that sets the sign-in password. The only way to supply it. */
    public static final String PASSWORD_ENV = "MOCK_LAB_PASSWORD";

    /** The lab's standard local username. */
    public static final String DEFAULT_USERNAME = "LabTech";

    /** The lab's documented local password. It is a published default, not a secret. */
    public static final String DEFAULT_PASSWORD = "I_Like_This_Lab";

    /**
     * Resolves the credentials. The lookups are passed in (each returns null when a name is
     * unset) so resolution can be unit-tested without touching the real process environment.
     * The system-property lookup is accepted to keep the same shape as {@code LabConfig.load},
     * but neither value is read from it.
     *
     * @param systemProperties looks up a system property by name (never consulted)
     * @param environment      looks up an environment variable by name
     */
    public static UiCredentials load(Function<String, String> systemProperties, Function<String, String> environment) {
        String username = nonBlankOrNull(environment.apply(USERNAME_ENV));
        String password = nonBlankOrNull(environment.apply(PASSWORD_ENV));
        return new UiCredentials(
                username == null ? DEFAULT_USERNAME : username,
                new Password(password == null ? DEFAULT_PASSWORD : password));
    }

    /** {@link #load} against the real system properties and environment variables. */
    public static UiCredentials fromEnvironment() {
        return load(System::getProperty, System::getenv);
    }

    /** Never shows the password, whatever {@link Password#toString()} does. */
    @Override
    public String toString() {
        return "UiCredentials[username=" + username + ", password=***]";
    }

    private static String nonBlankOrNull(String candidate) {
        return candidate == null || candidate.isBlank() ? null : candidate;
    }
}
