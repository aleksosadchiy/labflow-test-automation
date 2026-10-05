// @layer: unit
// @spec: 005-reliable-isolated-execution
// @regression
package com.labflow.ui.tests.guard;

import com.labflow.ui.tests.guard.support.CheckSources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance check for functional-spec.md section 2.2 on the screen family: every group of
 * checks that differ only by input is one parameterized check, and each of its rows reaches the
 * run output under a plain-language name (not an index, not a raw constant such as
 * {@code MISSING_NAME}), so a failure names the exact input. It reads the rows the way JUnit
 * would build them (no lab is contacted: only the enum constants and the row factories are read).
 */
class PlainRowNamesAcceptanceTest {

    /** The groups the spec names as examples (each missing required field, each invalid sample, each blank sign-in field), as {@code Class#method}. */
    private static final Set<String> GROUPS_THAT_MUST_BE_PARAMETERIZED = Set.of(
            "SampleRegistrationRejectionTest#missingFieldIsRequired",
            "SampleRegistrationRejectionTest#unacceptableSampleIsRefused",
            "SignInRejectionTest#blankCredentialsAreRefused");

    @Test
    @DisplayName("The input groups named in the spec are each one parameterized check, not copies of the same steps")
    void inputGroupsAreParameterizedChecks() {
        Set<String> parameterized = new TreeSet<>();
        for (Method method : parameterizedMethods()) {
            parameterized.add(method.getDeclaringClass().getSimpleName() + "#" + method.getName());
        }

        assertThat(parameterized).as("parameterized screen checks").containsAll(GROUPS_THAT_MUST_BE_PARAMETERIZED);
    }

    @Test
    @DisplayName("Every row of every parameterized check has a plain-language, distinct name, never an index or a raw constant")
    void everyRowHasAPlainLanguageName() {
        List<String> offences = new ArrayList<>();
        int rows = 0;
        for (Method method : parameterizedMethods()) {
            String where = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
            String pattern = method.getAnnotation(ParameterizedTest.class).name();
            if (pattern.equals(ParameterizedTest.DEFAULT_DISPLAY_NAME) || pattern.replace("{index}", "").isBlank()) {
                offences.add(where + ": row name pattern '" + pattern + "' shows only the default or an index");
                continue;
            }
            Map<String, Integer> seen = new TreeMap<>();
            for (Row row : rowsOf(method)) {
                rows++;
                String name = rowName(pattern, row);
                seen.merge(name, 1, Integer::sum);
                if (!name.matches(".*\\s.*") || name.matches("\\W*\\d+\\W*")) {
                    offences.add(where + ": row named '" + name + "' is not plain language");
                }
                for (int i = 0; i < row.args().length; i++) {
                    // Only an argument the row name shows has to read as plain language.
                    if (pattern.contains("{" + i + "}") && row.args()[i] instanceof Enum<?> constant
                            && constant.toString().equals(constant.name())) {
                        offences.add(where + ": row '" + name + "' shows the raw constant " + constant.name());
                    }
                }
            }
            seen.forEach((name, count) -> {
                if (count > 1) {
                    offences.add(where + ": " + count + " rows share the name '" + name + "'");
                }
            });
        }

        assertThat(rows).as("rows examined").isGreaterThanOrEqualTo(5);
        assertThat(offences).as("rows without a plain-language name: %s", offences).isEmpty();
    }

    /** One row of a parameterized check: its arguments and, for a named argument set, the set's name. */
    private record Row(String setName, Object[] args) {
    }

    /** Builds the row's name from the pattern the way JUnit does for the placeholders the checks use. */
    private static String rowName(String pattern, Row row) {
        String name = pattern;
        if (row.setName() != null) {
            name = name.replace("{argumentSetName}", row.setName());
        }
        for (int i = 0; i < row.args().length; i++) {
            name = name.replace("{" + i + "}", String.valueOf(row.args()[i]));
        }
        return name;
    }

    private static List<Row> rowsOf(Method method) {
        List<Row> rows = new ArrayList<>();
        EnumSource enums = method.getAnnotation(EnumSource.class);
        MethodSource factory = method.getAnnotation(MethodSource.class);
        if (enums != null) {
            for (Object constant : enums.value().getEnumConstants()) {
                rows.add(new Row(null, new Object[]{constant}));
            }
        } else if (factory != null) {
            String factoryName = factory.value().length == 0 ? method.getName() : factory.value()[0];
            try {
                Method source = method.getDeclaringClass().getDeclaredMethod(factoryName);
                source.setAccessible(true);
                try (Stream<?> stream = (Stream<?>) source.invoke(null)) {
                    stream.forEach(item -> {
                        Arguments arguments = (Arguments) item;
                        String setName = arguments instanceof Arguments.ArgumentSet set ? set.getName() : null;
                        rows.add(new Row(setName, arguments.get()));
                    });
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read the rows of " + method, e);
            }
        } else {
            throw new IllegalStateException(method + " is parameterized with a row source this guard does not read");
        }
        return rows;
    }

    private static List<Method> parameterizedMethods() {
        List<Method> methods = new ArrayList<>();
        for (Path file : CheckSources.checkFiles()) {
            Class<?> check = checkClassOf(file);
            Arrays.stream(check.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(ParameterizedTest.class))
                    .forEach(methods::add);
        }
        return methods;
    }

    /** {@code .../tests/rejection/FooTest.java} names the class {@code com.labflow.tests.rejection.FooTest}. */
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
