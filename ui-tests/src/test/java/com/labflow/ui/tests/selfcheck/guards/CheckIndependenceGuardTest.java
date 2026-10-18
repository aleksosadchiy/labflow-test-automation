// @layer: unit
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.selfcheck.guards;

import com.labflow.ui.tests.selfcheck.guards.support.CheckSources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static final String BROWSER_CLOSING_HOOKS = "UiHooks.java";
    private static final String CLOSING_HOOK = "After" + "All";

    // Written in pieces for the same reason. Test data comes only from the shared recipes in
    // test-data, so a check never makes up a random value or a patient-name prefix of its own.
    // Only the start of a literal ("patient-...") is forbidden, so expected messages stay free.
    private static final List<String> HAND_MADE_DATA = List.of(
            "UUID." + "randomUUID",
            "\"patient" + "-",
            "\"ui-" + "patient");

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
        assertThat(junit.getProperty("cucumber.execution.order"))
                .as("the order in which the scenarios run (the screen checks are Gherkin scenarios)")
                .isEqualTo("random");
    }

    @Test
    @DisplayName("The run lets screen checks run side by side, with the number at once decided by the shared strategy, so a check that shared a browser or data would show up")
    void checksRunSideBySide() throws IOException {
        Properties junit = new Properties();
        try (InputStream in = CheckIndependenceGuardTest.class.getResourceAsStream("/junit-platform.properties")) {
            assertThat(in).as("the run's JUnit settings file, junit-platform.properties").isNotNull();
            junit.load(in);
        }

        Map<String, String> required = Map.of(
                "junit.jupiter.execution.parallel.enabled", "true",
                "junit.jupiter.execution.parallel.mode.default", "concurrent",
                "junit.jupiter.execution.parallel.mode.classes.default", "concurrent",
                "junit.jupiter.execution.parallel.config.strategy", "custom",
                "junit.jupiter.execution.parallel.config.custom.class", "com.labflow.common.junit.ChecksAtOnceStrategy");

        required.forEach((key, value) -> assertThat(junit.getProperty(key))
                .as("the parallel-execution setting %s", key)
                .isEqualTo(value));

        Map<String, String> requiredForScenarios = Map.of(
                "cucumber.execution.parallel.enabled", "true",
                "cucumber.execution.parallel.config.strategy", "custom",
                "cucumber.execution.parallel.config.custom.class", "com.labflow.common.junit.ChecksAtOnceStrategy");

        requiredForScenarios.forEach((key, value) -> assertThat(junit.getProperty(key))
                .as("the parallel-execution setting %s for the scenarios", key)
                .isEqualTo(value));
    }

    @Test
    @DisplayName("No step, hook or world class fixes its own position or shares set-up between scenarios, so each scenario builds what it needs")
    void checksDoNotFixTheirOrderOrShareSetUp() {
        List<Path> checkFiles = CheckSources.checkFiles();

        List<String> offences = new ArrayList<>();
        for (String forbidden : ORDER_OR_SHARED_SETUP) {
            offences.addAll(CheckSources.linesContaining(checkFiles, forbidden));
        }
        // The one shared set-up allowed: Cucumber's global after-all hook in UiHooks closes the
        // per-worker browsers once, at the end of the run (technical-considerations.md section 2.4).
        // It shares no state between scenarios. Exempt by exact file and token, nothing wider.
        offences.removeIf(offence -> offence.startsWith(BROWSER_CLOSING_HOOKS + ":") && offence.contains(CLOSING_HOOK));

        assertThat(offences)
                .as("checks must not fix their order or share set-up; found: %s", String.join(" | ", offences))
                .isEmpty();
    }

    @Test
    @DisplayName("No check makes up its own random values or patient names, so all test data comes from the shared recipes")
    void checksTakeTheirDataFromTheRecipes() {
        List<Path> checkFiles = CheckSources.checkFiles();

        List<String> offences = new ArrayList<>();
        for (String forbidden : HAND_MADE_DATA) {
            offences.addAll(CheckSources.linesContaining(checkFiles, forbidden));
        }

        assertThat(offences)
                .as("checks must take their data from the recipes in test-data; found: %s", String.join(" | ", offences))
                .isEmpty();
    }
}
