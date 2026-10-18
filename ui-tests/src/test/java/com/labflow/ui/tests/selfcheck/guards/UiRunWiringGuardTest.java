// @layer: unit
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.selfcheck.guards;

import com.labflow.ui.tests.selfcheck.guards.support.CheckSources;
import com.labflow.ui.tests.hooks.UiHooks;
import com.labflow.ui.tests.framework.watchdog.ScenarioWatchdog;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import com.labflow.ui.tests.ScreenScenariosTest;
import com.labflow.ui.tests.framework.preflight.UiPreflightListener;
import com.labflow.ui.tests.world.UiWorld;
import io.cucumber.core.backend.ObjectFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
 * Proves the run is wired the way the technical considerations describe (section 4, "Wiring"),
 * mirroring api-tests' {@code LabRunWiringTest}: the unit tests already prove what the
 * pre-flight, the scenario naming and the browser lifecycle each do on their own; nothing else
 * fails if the wiring that makes them run is removed, because the pieces would still pass in
 * isolation and the run would simply carry on without them. These checks need no lab or browser.
 */
class UiRunWiringGuardTest {

    @Test
    @DisplayName("The lab pre-flight is registered to run when a run starts, so an unreachable lab stops the run before any screen check opens a browser")
    void labPreflightIsRegisteredForEveryRun() {
        List<Class<?>> registered = ServiceLoader.load(LauncherSessionListener.class).stream()
                .<Class<?>>map(ServiceLoader.Provider::type)
                .toList();

        assertThat(registered)
                .as("session listeners registered for the run; without the pre-flight a dead lab shows as dozens of navigation errors")
                .contains(UiPreflightListener.class);
    }

    @Test
    @DisplayName("The hooks give every scenario its own page, close it afterwards, close the browsers once at the end, and name the scenario in the log")
    void hooksCarryTheBrowserLifecycleAndTheLogName() {
        List<Method> methods = Arrays.asList(UiHooks.class.getDeclaredMethods());

        assertThat(methods.stream().filter(method -> method.isAnnotationPresent(Before.class)).count())
                .as("@Before hooks: the log name, the watchdog, the report labels and the fresh page (at least four)")
                .isGreaterThanOrEqualTo(4);
        assertThat(methods.stream().filter(method -> method.isAnnotationPresent(After.class)).count())
                .as("@After hooks: closing the page and its evidence, stopping the watchdog, clearing the log name (at least three)")
                .isGreaterThanOrEqualTo(3);
        assertThat(methods.stream().filter(method -> method.isAnnotationPresent(AfterAll.class)).toList())
                .as("exactly one @AfterAll hook closes the browsers once, after the last scenario")
                .hasSize(1)
                .allMatch(method -> Modifier.isStatic(method.getModifiers()), "and it is static, as Cucumber requires");
        List<Path> hooks = CheckSources.hookFiles();
        assertThat(CheckSources.linesContaining(hooks, "MDC.put(")).as("the scenario's name goes into the log context once").hasSize(1);
        assertThat(CheckSources.linesContaining(hooks, "MDC.remove(")).as("and comes out again once").hasSize(1);
    }

    @Test
    @DisplayName("The screen checks run as Gherkin scenarios: one suite class selects the features package through the Cucumber engine, so Surefire reports every scenario")
    void suiteClassSelectsTheFeaturesThroughTheCucumberEngine() {
        Suite suite = ScreenScenariosTest.class.getAnnotation(Suite.class);
        IncludeEngines engines = ScreenScenariosTest.class.getAnnotation(IncludeEngines.class);
        SelectPackages packages = ScreenScenariosTest.class.getAnnotation(SelectPackages.class);

        assertThat(suite).as("ScreenScenariosTest is a JUnit Platform suite").isNotNull();
        assertThat(engines).as("ScreenScenariosTest limits the suite to an engine").isNotNull();
        assertThat(engines.value()).as("the engine that runs the scenarios").containsExactly("cucumber");
        assertThat(packages).as("ScreenScenariosTest selects the package of the feature files").isNotNull();
        assertThat(packages.value()).as("the package the suite selects").containsExactly("features");
        assertThat(ScreenScenariosTest.class.getResource("/features"))
                .as("the features folder the suite selects, on the test classpath")
                .isNotNull();
    }

    @Test
    @DisplayName("The Cucumber engine is told where the steps and hooks are, which report plugin records the scenarios and how they are named, so none runs without them")
    void cucumberSettingsPointAtTheStepsHooksPluginAndNaming() throws IOException {
        Properties junit = properties("/junit-platform.properties");

        assertThat(junit.getProperty("cucumber.glue"))
                .as("the packages searched for step definitions and hooks (and nothing else)")
                .isEqualTo("com.labflow.ui.tests.steps,com.labflow.ui.tests.hooks");
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

        List<Class<?>> stepClasses = classesOf(CheckSources.stepFiles(), "com.labflow.ui.tests.steps");
        List<Class<?>> hookClasses = classesOf(CheckSources.hookFiles(), "com.labflow.ui.tests.hooks");
        List<Class<?>> all = new ArrayList<>(stepClasses);
        all.addAll(hookClasses);
        all.add(UiWorld.class);

        for (Class<?> type : stepClasses) {
            assertThat(type.getDeclaredConstructors())
                    .as("%s has one constructor, so the container knows what to build it from", type.getSimpleName())
                    .hasSize(1);
            assertThat(type.getDeclaredConstructors()[0].getParameterTypes())
                    .as("%s takes only the world as constructor parameter (state is injected, not static)", type.getSimpleName())
                    .isSubsetOf(UiWorld.class);
        }
        for (Class<?> type : hookClasses) {
            assertThat(type.getDeclaredConstructors()[0].getParameterTypes())
                    .as("%s takes at most the world as constructor parameter", type.getSimpleName())
                    .isSubsetOf(UiWorld.class);
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
    @DisplayName("Every screen scenario has a 2-minute time limit: a watchdog is started in a @Before hook and cancelled in an @After hook, so a stuck scenario is named and interrupted (spec 008)")
    void everyScenarioHasATimeLimit() {
        List<Path> hooks = CheckSources.hookFiles();

        assertThat(ScenarioWatchdog.SCENARIO_LIMIT)
                .as("the limit the Jupiter screen checks had, which the scenarios keep")
                .isEqualTo(Duration.ofMinutes(2));
        assertThat(CheckSources.linesContaining(hooks, "ScenarioWatchdog.forScenarios().start("))
                .as("the hooks start the shared watchdog for each scenario; without it a stuck screen scenario waits for Surefire's 10-minute fork limit")
                .hasSize(1);
        assertThat(CheckSources.linesContaining(hooks, ".cancel()"))
                .as("and cancel it when the scenario ends, or it would fire on a scenario that finished")
                .hasSize(1);
    }

    @Test
    @DisplayName("Allure's own listener is switched off, so the report lists the screen checks only and not the framework's self-checks (spec 007)")
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
        try (InputStream in = UiRunWiringGuardTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("the resource " + resource + " on the test classpath").isNotNull();
            properties.load(in);
        }
        return properties;
    }

    /** The class names a service file lists: one per line, comments and blank lines left out. */
    private static List<String> serviceEntries(String resource) throws IOException {
        try (InputStream in = UiRunWiringGuardTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("the service file " + resource + " on the test classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(line -> line.replaceFirst("#.*", "").trim())
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }

    /** {@code .../tests/steps/FooSteps.java} in the package {@code com.labflow.ui.tests.steps} names the class {@code com.labflow.ui.tests.steps.FooSteps}. */
    private static List<Class<?>> classesOf(List<Path> files, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        for (Path file : files) {
            classes.add(Class.forName(packageName + "." + file.getFileName().toString().replaceFirst("\\.java$", "")));
        }
        return classes;
    }
}
