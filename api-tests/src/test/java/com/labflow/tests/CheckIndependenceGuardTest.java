// @layer: unit
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests;

import com.labflow.tests.support.CheckSources;
import com.labflow.tests.support.UniqueData;
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
 * Guards the rules that keep every check independent (functional-spec.md section 2.7): each
 * check creates its own records, no check needs another one to have run first, and the run
 * order is shuffled so a hidden dependency would show up. The proof that a single check, or
 * two whole runs in a row, pass is the command run against a lab; these guards keep the
 * design rules that make that true from being removed unnoticed. They need no lab.
 */
class CheckIndependenceGuardTest {

    // Written in pieces so this file does not contain the names it forbids in the checks. Plain
    // names, not "@Name", so an import or a fully qualified use is caught as well as the annotation.
    private static final List<String> ORDER_OR_SHARED_SETUP = List.of(
            "jupiter.api." + "Order",
            "Test" + "MethodOrder",
            "Test" + "ClassOrder",
            "Before" + "All",
            "After" + "All",
            "Test" + "Instance");

    @Test
    @DisplayName("The run shuffles the order of checks and of their classes, so a check that secretly needed another one would fail")
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
    @DisplayName("No check fixes its own position or shares set-up with another check, so each one builds what it needs")
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

    @Test
    @DisplayName("Every record a check names for itself is different from the last, so two runs against the same lab never collide")
    void uniqueDataIsDifferentEachTime() {
        String first = UniqueData.name("patient");
        String second = UniqueData.name("patient");

        assertThat(first).startsWith("patient-");
        assertThat(second).startsWith("patient-");
        assertThat(first).isNotEqualTo(second);
    }
}
