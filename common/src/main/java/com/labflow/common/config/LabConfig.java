package com.labflow.common.config;

import java.util.function.Function;

/**
 * Where the lab is, how to sign in to it and which environment the run targets. All
 * values come from outside the project (nothing is edited to change them) and fall back
 * to the lab's standard local values.
 *
 * <p><b>Environment</b>: the {@code -Denvironment=...} system property, then the
 * {@code ENVIRONMENT} environment variable, then {@code local}. A blank value counts as
 * unset and surrounding whitespace is trimmed. The name is matched ignoring case against
 * {@link LabEnvironment}; an unknown name stops the run with
 * {@code Unknown environment 'x'. Known: local, ci}. The environment supplies only the
 * default lab address and its name in the run output.
 *
 * <p><b>Lab address</b>: the {@code -DbaseUrl=...} system property, then the
 * {@code BASE_URL} environment variable, then the environment's default address (for
 * both {@code local} and {@code ci} that is {@value #DEFAULT_BASE_URL}). An explicit
 * address therefore always wins over the environment. A blank value counts as unset.
 * Surrounding whitespace is trimmed and trailing {@code /} characters are stripped, so
 * callers can always append {@code "/api/..."}.
 *
 * <p><b>Access key</b>: the {@code API_TOKEN} environment variable <em>only</em>, then
 * {@value #DEFAULT_API_KEY} (the lab's documented local default, not a secret). A blank
 * value counts as unset. There is deliberately no system property for the key: a
 * {@code -DapiToken=...} value is written into Surefire's XML report and is visible in
 * the process list, while an environment variable is neither.
 *
 * @param baseUrl     the lab's address, without a trailing slash
 * @param apiKey      the access key, wrapped so it cannot be printed
 * @param environment the environment the run targets
 */
public record LabConfig(String baseUrl, ApiKey apiKey, LabEnvironment environment) {

    /** System property that sets the lab address ({@code -DbaseUrl=...}). */
    public static final String BASE_URL_PROPERTY = "baseUrl";

    /** Environment variable that sets the lab address when the system property is absent. */
    public static final String BASE_URL_ENV = "BASE_URL";

    /** System property that sets the environment ({@code -Denvironment=...}). */
    public static final String ENVIRONMENT_PROPERTY = "environment";

    /** Environment variable that sets the environment when the system property is absent. */
    public static final String ENVIRONMENT_ENV = "ENVIRONMENT";

    /** Environment variable that sets the access key. The only way to supply it. */
    public static final String API_TOKEN_ENV = "API_TOKEN";

    /** The lab's standard local address, and the default address of every {@link LabEnvironment}. */
    public static final String DEFAULT_BASE_URL = "http://localhost:8080";

    /** The lab's documented local access key. It is a published default, not a secret. */
    public static final String DEFAULT_API_KEY = "mock-lab-local-token";

    /**
     * Resolves the configuration. The two lookups are passed in (each returns null when a
     * name is unset) so precedence can be unit-tested without touching the real process
     * environment.
     *
     * @param systemProperties looks up a system property by name
     * @param environment      looks up an environment variable by name
     * @throws IllegalArgumentException if the environment name is not a known {@link LabEnvironment}
     */
    public static LabConfig load(Function<String, String> systemProperties, Function<String, String> environment) {
        String environmentName = firstNonBlank(
                systemProperties.apply(ENVIRONMENT_PROPERTY), environment.apply(ENVIRONMENT_ENV));
        LabEnvironment labEnvironment =
                environmentName == null ? LabEnvironment.LOCAL : LabEnvironment.fromName(environmentName);
        String address = firstNonBlank(systemProperties.apply(BASE_URL_PROPERTY), environment.apply(BASE_URL_ENV));
        String key = firstNonBlank(environment.apply(API_TOKEN_ENV));
        return new LabConfig(
                stripTrailingSlashes(address == null ? labEnvironment.defaultBaseUrl() : address.strip()),
                new ApiKey(key == null ? DEFAULT_API_KEY : key),
                labEnvironment);
    }

    /** {@link #load} against the real system properties and environment variables. */
    public static LabConfig fromEnvironment() {
        return load(System::getProperty, System::getenv);
    }

    /** Never shows the key, whatever {@link ApiKey#toString()} does. */
    @Override
    public String toString() {
        return "LabConfig[baseUrl=" + baseUrl + ", apiKey=***, environment=" + environment + "]";
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}
