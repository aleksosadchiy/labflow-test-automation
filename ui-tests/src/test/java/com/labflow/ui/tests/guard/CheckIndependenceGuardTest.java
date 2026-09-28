// @layer: unit
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.guard;

import com.labflow.ui.tests.guard.support.CheckSources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the rule that keeps every screen check independent, mirroring api-tests'
 * {@code CheckIndependenceGuardTest}: the run shuffles method and class order, and no check fixes
 * its own position or shares set-up with another one. A hidden dependency between checks would
 * show up sooner or later once the order is no longer fixed. Plain-text and reflection scans;
 * need no lab or browser.
 */
class CheckIndependenceGuardTest {

    // Written in pieces so this file does not contain the names it forbids in the checks. Plain
    // names, not "@Name", so an import or a fully qualified use is caught as well as the
    // annotation.
    private static final List<String> ORDER_OR_SHARED_SETUP = List.of(
            "jupiter.api." + "Order",
            "Test" + "MethodOrder",
            "Test" + "ClassOrder",
            "Before" + "All",
            "After" + "All",
            "Test" + "Instance");

    @Test
    @DisplayName("The run shuffles the order of screen checks and of their classes, so a check that secretly needed another one would fail")
    void checksRunInRandomOrder() throws IOException {
        Properties junit = new Properties();
        try (InputStream in = CheckIndependenceGuardTest.class.getResourceAsStream("/junit-platform.properties")) {
            assertThat(in).as("the run's JUnit settings file, junit-platform.properties").isNotNull();
            junit.load(in);
        }

        assertThat(junit.getProperty("junit.jupiter.testmethod.order.default"))
                .as("the order in which the checks of one class run")
                .contains("Random");
        assertThat(junit.getProperty("junit.jupiter.testclass.order.default"))
                .as("the order in which the check classes run")
                .contains("Random");
    }

    @Test
    @DisplayName("No screen check fixes its own position or shares set-up with another check, so each one builds what it needs")
    void checksDoNotFixTheirOrderOrShareSetUp() {
        List<Path> checkFiles = CheckSources.checkFiles();

        List<String> offences = new ArrayList<>();
        for (String forbidden : ORDER_OR_SHARED_SETUP) {
            offences.addAll(CheckSources.linesContaining(checkFiles, forbidden));
        }

        assertThat(offences)
                .as("checks must not fix their order or share set-up; found: %s", String.join(" | ", offences))
                .isEmpty();
    }
}
