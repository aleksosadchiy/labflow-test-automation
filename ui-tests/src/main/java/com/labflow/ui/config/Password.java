package com.labflow.ui.config;

import java.util.Objects;

/**
 * The lab sign-in password, wrapped so it cannot be printed by accident.
 *
 * <p>{@link #toString()} always returns {@code ***}, so the password stays hidden even if a
 * {@code Password} (or a {@link UiCredentials} holding one) is logged, concatenated into a
 * message or shown in a failed assertion. The raw value is reachable only through the
 * explicit, greppable {@link #reveal()}, which is meant to be called in exactly one place:
 * where the Sign In screen's password field is filled.
 *
 * <p>A blank password is rejected on construction, so credentials with an empty password
 * cannot exist.
 */
public final class Password {

    private final String value;

    /**
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public Password(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The password must not be blank");
        }
        this.value = value;
    }

    /** The raw secret. Use only to fill the password field; never log it. */
    public String reveal() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Password that && value.equals(that.value));
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
