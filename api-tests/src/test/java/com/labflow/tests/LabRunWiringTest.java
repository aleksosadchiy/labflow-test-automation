// @layer: integration
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests;

import com.labflow.tests.support.CheckSources;
import com.labflow.tests.support.LabPreflightListener;
import com.labflow.tests.support.LabTest;
import com.labflow.tests.support.ScenarioNameExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.launcher.LauncherSessionListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the run is wired the way functional-spec.md sections 2.3 and 2.7 describe. The unit
 * tests already prove what the pre-flight and the scenario naming do; nothing else fails if
 * the wiring that makes them run is removed, because the pieces would still pass on their own
 * and the run would simply carry on without them. These checks need no lab.
 */
class LabRunWiringTest {

    @Test
    @DisplayName("The lab pre-flight is registered to run when a run starts, so an unreachable lab or a rejected key stops the run before any check")
    void labPreflightIsRegisteredForEveryRun() {
        List<Class<?>> registered = ServiceLoader.load(LauncherSessionListener.class).stream()
                .<Class<?>>map(ServiceLoader.Provider::type)
                .toList();

        assertThat(registered)
                .as("session listeners registered for the run; without the pre-flight a dead lab shows as dozens of connection errors")
                .contains(LabPreflightListener.class);
    }

    @Test
    @DisplayName("The base class of the lab checks puts the business scenario in front of any failure")
    void checkBaseClassNamesTheScenarioWhenACheckFails() {
        ExtendWith extension = LabTest.class.getAnnotation(ExtendWith.class);

        assertThat(extension)
                .as("the base class of the checks must register the scenario-naming extension")
                .isNotNull();
        assertThat(extension.value()).contains(ScenarioNameExtension.class);
    }

    @Test
    @DisplayName("Every lab check builds on that base class, so none can fail without naming its scenario")
    void everyCheckBuildsOnTheSharedBaseClass() {
        for (Path file : CheckSources.checkFiles()) {
            Class<?> check = checkClassOf(file);

            assertThat(LabTest.class.isAssignableFrom(check))
                    .as("%s extends the shared base class of the checks", check.getSimpleName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Allure's own listener is switched off, so the report lists the lab checks only and not the framework's self-checks (spec 007)")
    void allureOwnListenerIsSwitchedOff() throws IOException {
        Properties junit = properties("/junit-platform.properties");

        assertThat(junit.getProperty("junit.platform.execution.listeners.deactivate"))
                .as("restore 'junit.platform.execution.listeners.deactivate=io.qameta.allure.junitplatform.AllureJunitPlatform' "
                        + "in junit-platform.properties; without it Allure records every test, self-checks included")
                .isNotNull()
                .contains("io.qameta.allure.junitplatform.AllureJunitPlatform");
    }

    @Test
    @DisplayName("The filtering Allure listener is registered for every run, so the lab checks reach the report (spec 007)")
    void filteringAllureListenerIsRegistered() throws IOException {
        assertThat(serviceEntries("/META-INF/services/org.junit.platform.launcher.TestExecutionListener"))
                .as("restore 'com.labflow.common.junit.LabChecksAllureListener' in META-INF/services/"
                        + "org.junit.platform.launcher.TestExecutionListener; without it the report is empty")
                .contains("com.labflow.common.junit.LabChecksAllureListener");
    }

    @Test
    @DisplayName("The Allure result files go into the shared target folder at the repository root, so a clean removes them and both families land in one place (spec 007)")
    void allureResultsGoIntoTheSharedTargetFolder() throws IOException {
        assertThat(properties("/allure.properties").getProperty("allure.results.directory"))
                .as("restore 'allure.results.directory=../target/allure-results' in allure.properties; "
                        + "another place would leave results that clean does not remove, or split the report")
                .isEqualTo("../target/allure-results");
    }

    private static Properties properties(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = LabRunWiringTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("the resource " + resource + " on the test classpath").isNotNull();
            properties.load(in);
        }
        return properties;
    }

    /** The class names a service file lists: one per line, comments and blank lines left out. */
    private static List<String> serviceEntries(String resource) throws IOException {
        try (InputStream in = LabRunWiringTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("the service file " + resource + " on the test classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(line -> line.replaceFirst("#.*", "").trim())
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }

    /** {@code .../tests/rejection/FooTest.java} names the class {@code com.labflow.tests.rejection.FooTest}. */
    private static Class<?> checkClassOf(Path file) {
        String checkPackage = file.getParent().getFileName().toString();
        String simpleName = file.getFileName().toString().replaceFirst("\\.java$", "");
        try {
            return Class.forName("com.labflow.tests." + checkPackage + "." + simpleName);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("A check source has no compiled class: " + file, e);
        }
    }
}
