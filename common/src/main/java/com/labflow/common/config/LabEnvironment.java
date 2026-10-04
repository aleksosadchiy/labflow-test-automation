package com.labflow.common.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The named place a run targets. An environment supplies only a default lab address and
 * its name in the run output; nothing else differs between environments.
 *
 * <p>Both environments currently point at {@value LabConfig#DEFAULT_BASE_URL}: in the
 * pipeline the job starts the lab on the runner itself, so only the name differs. An
 * explicit lab address ({@code -DbaseUrl} or {@code BASE_URL}) always wins over the
 * environment's address (see {@link LabConfig}).
 */
public enum LabEnvironment {

    /** An engineer's machine. The default when no environment is given. */
    LOCAL(LabConfig.DEFAULT_BASE_URL),

    /** The automated build pipeline. */
    CI(LabConfig.DEFAULT_BASE_URL);

    private final String defaultBaseUrl;

    LabEnvironment(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    /** The lab address used when no address is given explicitly. */
    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    /** The name as the engineer types it and as the run output shows it: {@code local}, {@code ci}. */
    public String displayName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The {@link #displayName()}, so the environment prints the way it is typed. */
    @Override
    public String toString() {
        return displayName();
    }

    /**
     * Finds the environment by name, ignoring case and surrounding whitespace.
     *
     * @param name the name as given, for example {@code "ci"} or {@code "LOCAL"}
     * @throws IllegalArgumentException if no environment has that name; the message names
     *                                  the value as given and lists the known names
     */
    public static LabEnvironment fromName(String name) {
        String wanted = name.strip();
        for (LabEnvironment environment : values()) {
            if (environment.displayName().equalsIgnoreCase(wanted)) {
                return environment;
            }
        }
        throw new IllegalArgumentException("Unknown environment '" + name + "'. Known: " + knownNames());
    }

    private static String knownNames() {
        return Arrays.stream(values()).map(LabEnvironment::displayName).collect(Collectors.joining(", "));
    }
}
