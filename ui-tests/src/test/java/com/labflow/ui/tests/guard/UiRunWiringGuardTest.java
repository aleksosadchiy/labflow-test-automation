// @layer: unit
// @spec: 004-ui-automation-page-objects
// @regression
package com.labflow.ui.tests.guard;

import com.labflow.ui.tests.guard.support.CheckSources;
import com.labflow.ui.tests.support.BrowserExtension;
import com.labflow.ui.tests.support.ScenarioNameExtension;
import com.labflow.ui.tests.support.UiPreflightListener;
import com.labflow.ui.tests.support.UiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.launcher.LauncherSessionListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
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
    @DisplayName("The base class of the screen checks carries both the scenario-naming extension and the browser lifecycle extension")
    void checkBaseClassCarriesBothExtensions() {
        ExtendWith extension = UiTest.class.getAnnotation(ExtendWith.class);

        assertThat(extension)
                .as("the base class of the screen checks must register its extensions")
                .isNotNull();
        assertThat(extension.value())
                .as("the extensions registered on the base class of the screen checks")
                .contains(ScenarioNameExtension.class, BrowserExtension.class);
    }

    @Test
    @DisplayName("Every screen check builds on that base class, so none can run without a browser or fail without naming its scenario")
    void everyCheckBuildsOnTheSharedBaseClass() {
        for (Path file : CheckSources.checkFiles()) {
            Class<?> check = checkClassOf(file);

            assertThat(UiTest.class.isAssignableFrom(check))
                    .as("%s extends the shared base class of the screen checks", check.getSimpleName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Every screen check has a per-test time limit, so a stuck check fails instead of hanging the run (spec 004, Slice 8)")
    void everyCheckHasAPerTestTimeLimit() throws IOException {
        Properties junit = new Properties();
        try (InputStream in = UiRunWiringGuardTest.class.getResourceAsStream("/junit-platform.properties")) {
            assertThat(in).as("the run's JUnit settings file, junit-platform.properties").isNotNull();
            junit.load(in);
        }

        assertThat(junit.getProperty("junit.jupiter.execution.timeout.default"))
                .as("the per-test time limit that keeps a stuck check from hanging the run")
                .isNotBlank();
        assertThat(junit.getProperty("junit.jupiter.execution.timeout.thread.mode.default"))
                .as("the thread mode for that time limit, deliberately SEPARATE_THREAD so the run always continues past the limit")
                .isEqualTo("SEPARATE_THREAD");
    }

    /** {@code .../tests/rejection/FooTest.java} names the class {@code com.labflow.ui.tests.rejection.FooTest}. */
    private static Class<?> checkClassOf(Path file) {
        String checkPackage = file.getParent().getFileName().toString();
        String simpleName = file.getFileName().toString().replaceFirst("\\.java$", "");
        try {
            return Class.forName("com.labflow.ui.tests." + checkPackage + "." + simpleName);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("A check source has no compiled class: " + file, e);
        }
    }
}
