// @layer: integration
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.selfcheck.guards;

import com.labflow.tests.selfcheck.guards.support.CheckSources;
import com.labflow.tests.WorkflowScenariosTest;
import com.labflow.tests.framework.preflight.LabPreflightListener;
import com.labflow.tests.world.LabWorld;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.cucumber.core.backend.ObjectFactory;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    @DisplayName("The hooks put the scenario's name into the log context before its first step and take it out again after the last, so every run-log line names its scenario")
    void hooksNameTheScenarioInTheLog() {
        List<Path> hooks = CheckSources.hookFiles();

        assertThat(CheckSources.linesContaining(hooks, "MDC.put("))
                .as("a @Before hook must put the scenario's name into the log context (the log pattern prints it as %X{check})")
                .hasSize(1);
        assertThat(CheckSources.linesContaining(hooks, "MDC.remove("))
                .as("an @After hook must clear the name again, because worker threads are reused by the next scenario")
                .hasSize(1);
    }

    @Test
    @DisplayName("The workflow checks run as Gherkin scenarios: one suite class selects the features package through the Cucumber engine, so Surefire reports every scenario")
    void suiteClassSelectsTheFeaturesThroughTheCucumberEngine() {
        Suite suite = WorkflowScenariosTest.class.getAnnotation(Suite.class);
        IncludeEngines engines = WorkflowScenariosTest.class.getAnnotation(IncludeEngines.class);
        SelectPackages packages = WorkflowScenariosTest.class.getAnnotation(SelectPackages.class);

        assertThat(suite).as("WorkflowScenariosTest is a JUnit Platform suite").isNotNull();
        assertThat(engines).as("WorkflowScenariosTest limits the suite to an engine").isNotNull();
        assertThat(engines.value()).as("the engine that runs the scenarios").containsExactly("cucumber");
        assertThat(packages).as("WorkflowScenariosTest selects the package of the feature files").isNotNull();
        assertThat(packages.value()).as("the package the suite selects").containsExactly("features");
        assertThat(WorkflowScenariosTest.class.getResource("/features"))
                .as("the features folder the suite selects, on the test classpath")
                .isNotNull();
    }

    @Test
    @DisplayName("The Cucumber engine is told where the steps and hooks are, which report plugin records the scenarios and how they are named, so none runs without them")
    void cucumberSettingsPointAtTheStepsHooksPluginAndNaming() throws IOException {
        Properties junit = properties("/junit-platform.properties");

        assertThat(junit.getProperty("cucumber.glue"))
                .as("the packages searched for step definitions and hooks (and nothing else)")
                .isEqualTo("com.labflow.tests.steps,com.labflow.tests.hooks");
        assertThat(junit.getProperty("cucumber.plugin"))
                .as("the plugin that records each scenario for the report")
                .isEqualTo("io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm");
        assertThat(junit.getProperty("cucumber.junit-platform.naming-strategy"))
                .as("a scenario is reported under exactly its short name")
                .isEqualTo("short");
        assertThat(junit.getProperty("cucumber.junit-platform.naming-strategy.short.example-name"))
                .as("an outline row is reported under its own name")
                .isEqualTo("pickle");
    }

    @Test
    @DisplayName("Scenarios share state only through the world object that PicoContainer hands to every step and hook class, never through static fields")
    void stepsAndHooksTakeTheirStateFromTheWorld() throws ClassNotFoundException {
        assertThat(ServiceLoader.load(ObjectFactory.class).stream().map(provider -> provider.type().getName()).toList())
                .as("the object factory that shares one world between the step classes of a scenario")
                .contains("io.cucumber.picocontainer.PicoFactory");

        List<Class<?>> stepClasses = classesOf(CheckSources.stepFiles(), "com.labflow.tests.steps");
        List<Class<?>> hookClasses = classesOf(CheckSources.hookFiles(), "com.labflow.tests.hooks");
        List<Class<?>> all = new ArrayList<>(stepClasses);
        all.addAll(hookClasses);
        all.add(LabWorld.class);

        for (Class<?> type : stepClasses) {
            assertThat(type.getDeclaredConstructors())
                    .as("%s has one constructor, so the container knows what to build it from", type.getSimpleName())
                    .hasSize(1);
            assertThat(type.getDeclaredConstructors()[0].getParameterTypes())
                    .as("%s takes only the world as constructor parameter (state is injected, not static)", type.getSimpleName())
                    .isSubsetOf(LabWorld.class);
        }
        for (Class<?> type : hookClasses) {
            assertThat(type.getDeclaredConstructors()[0].getParameterTypes())
                    .as("%s takes at most the world as constructor parameter", type.getSimpleName())
                    .isSubsetOf(LabWorld.class);
        }

        List<String> offences = new ArrayList<>();
        for (Class<?> type : all) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                boolean mutableHolder = Map.class.isAssignableFrom(field.getType()) || Collection.class.isAssignableFrom(field.getType())
                        || field.getType().getName().startsWith("java.util.concurrent.atomic.");
                if (!Modifier.isFinal(field.getModifiers())) {
                    offences.add(type.getSimpleName() + "." + field.getName() + " is a static field that is not final");
                } else if (mutableHolder && !hookClasses.contains(type)) {
                    offences.add(type.getSimpleName() + "." + field.getName() + " is a static collection that scenarios running side by side could share");
                }
            }
        }
        assertThat(offences)
                .as("scenario state must live in the world, not in static fields; found: %s", String.join(" | ", offences))
                .isEmpty();
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
    @DisplayName("The check counter is registered for every run, so the log says how many lab checks ran and under which labels")
    void checkCounterIsRegistered() throws IOException {
        assertThat(serviceEntries("/META-INF/services/org.junit.platform.launcher.TestExecutionListener"))
                .as("restore 'com.labflow.common.junit.CheckCountListener' in META-INF/services/"
                        + "org.junit.platform.launcher.TestExecutionListener; without it the run never prints 'Lab checks run: N'")
                .contains("com.labflow.common.junit.CheckCountListener");
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

    /** {@code .../tests/steps/FooSteps.java} in the package {@code com.labflow.tests.steps} names the class {@code com.labflow.tests.steps.FooSteps}. */
    private static List<Class<?>> classesOf(List<Path> files, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        for (Path file : files) {
            classes.add(Class.forName(packageName + "." + file.getFileName().toString().replaceFirst("\\.java$", "")));
        }
        return classes;
    }
}
