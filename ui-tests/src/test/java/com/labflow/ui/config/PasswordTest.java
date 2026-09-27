package com.labflow.ui.config;

// @layer: unit
// @spec: 004-ui-automation-page-objects

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Password} (tech spec §2.2): it never prints its value, the raw value
 * is reachable only through {@code reveal()}, and a blank password cannot exist.
 *
 * @spec: 004-ui-automation-page-objects
 */
class PasswordTest {

    private static final String SECRET = "s3cret-ui-pass-5531";

    @Test
    // @regression
    @DisplayName("Password.toString() is exactly three asterisks")
    void toStringIsMasked() {
        assertThat(new Password(SECRET).toString()).isEqualTo("***");
    }

    @Test
    // @regression
    @DisplayName("String.valueOf, concatenation and String.format never contain the password")
    void printedFormsNeverContainThePassword() {
        Password password = new Password(SECRET);

        assertThat(String.valueOf(password)).doesNotContain(SECRET);
        assertThat("password=" + password).doesNotContain(SECRET);
        assertThat(String.format("%s", password)).doesNotContain(SECRET);
    }

    @Test
    // @regression
    @DisplayName("reveal() returns the raw value")
    void revealReturnsTheRawValue() {
        assertThat(new Password(SECRET).reveal()).isEqualTo(SECRET);
    }

    @Test
    // @regression
    @DisplayName("Passwords with the same value are equal and have the same hash code; different values are not")
    void equalityFollowsTheValue() {
        assertThat(new Password(SECRET)).isEqualTo(new Password(SECRET))
                .hasSameHashCodeAs(new Password(SECRET))
                .isNotEqualTo(new Password("another-pass"))
                .isNotEqualTo(SECRET);
    }

    @Test
    // @regression
    @DisplayName("A null, empty or blank password is rejected")
    void blankPasswordIsRejected() {
        for (String blank : new String[] {null, "", "   ", "\t\n"}) {
            assertThatThrownBy(() -> new Password(blank)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
