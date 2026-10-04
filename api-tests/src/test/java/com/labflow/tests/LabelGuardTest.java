// @layer: unit
// @spec: 005-reliable-isolated-execution
// @regression
package com.labflow.tests;

import com.labflow.common.junit.ApiCheck;
import com.labflow.common.junit.Regression;
import com.labflow.common.junit.Smoke;
import com.labflow.tests.support.CheckSources;
import com.labflow.tests.support.LabTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the labels (functional-spec.md section 2.1). Labels come from the base class and from
 * a short, fixed list of {@code @Smoke} methods, so a run such as {@code -Dgroups=smoke} cannot
 * silently grow, shrink or lose a label. These guards need no lab.
 */
class LabelGuardTest {

    /** The smoke checks of the workflow family, as {@code Class#method}. Changing this list is a deliberate decision. */
    private static final Set<String> SMOKE_ALLOWLIST = Set.of(
            "FullJourneyTest#wholeJourneyFromPatientToReport",
            "PatientOrderSampleTest#newSampleIsAccessioned",
            "AuthenticationRejectionTest#readWithoutKeyIsRefused");

    // Written in pieces so this file does not contain the text it forbids in the checks. Both the
    // annotation and the fully qualified or imported name are forbidden, so "@org.junit...Tag(...)" is caught too.
    private static final List<String> RAW_TAG = List.of("@" + "Tag", "jupiter.api." + "Tag");

    @Test
    @DisplayName("The base class of the workflow checks carries the api and regression labels, so every check inherits them")
    void baseClassCarriesTheApiAndRegressionLabels() {
        assertThat(LabTest.class.isAnnotationPresent(ApiCheck.class))
                .as("LabTest is labelled @ApiCheck (inherited by every check)").isTrue();
        assertThat(LabTest.class.isAnnotationPresent(Regression.class))
                .as("LabTest is labelled @Regression (inherited by every check)").isTrue();
    }

    @Test
    @DisplayName("Exactly the agreed workflow checks are labelled smoke, so a change to the smoke set is deliberate")
    void smokeChecksAreExactlyTheAgreedOnes() {
        Set<String> smoke = new TreeSet<>();
        for (Path file : CheckSources.checkFiles()) {
            Class<?> check = checkClassOf(file);
            Arrays.stream(check.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Smoke.class))
                    .map((Method method) -> check.getSimpleName() + "#" + method.getName())
                    .forEach(smoke::add);
        }

        assertThat(smoke).as("methods labelled @Smoke in the workflow checks").isEqualTo(SMOKE_ALLOWLIST);
    }

    @Test
    @DisplayName("No check writes a raw tag of its own, so every label goes through the label annotations and a typo cannot hide a check")
    void checksUseTheLabelAnnotationsOnly() {
        List<Path> checkFiles = CheckSources.checkFiles();

        List<String> offences = new ArrayList<>();
        for (String forbidden : RAW_TAG) {
            offences.addAll(CheckSources.linesContaining(checkFiles, forbidden));
        }

        assertThat(offences)
                .as("checks must use the label annotations, not raw tags; found: %s", String.join(" | ", offences))
                .isEmpty();
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
