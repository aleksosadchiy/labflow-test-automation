package com.labflow.common.config;

import java.util.Objects;

/**
 * The lab's access key, wrapped so it cannot be printed by accident.
 *
 * <p>{@link #toString()} always returns {@code ***}, so the key stays hidden even if an
 * {@code ApiKey} (or a {@link LabConfig} holding one) is logged, concatenated into a
 * message or shown in a failed assertion. The raw value is reachable only through the
 * explicit, greppable {@link #reveal()}, which is meant to be called in exactly one
 * place: where the {@code Authorization} header is built.
 *
 * <p>A blank key is rejected on construction, so a configuration with an empty key
 * cannot exist.
 */
public final class ApiKey {

    private final String value;

    /**
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public ApiKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The access key must not be blank");
        }
        this.value = value;
    }

    /** The raw secret. Use only to build the {@code Authorization} header; never log it. */
    public String reveal() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof ApiKey that && value.equals(that.value));
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "***";
    }
}
