// @layer: unit
// @spec: 007-allure-report
// @regression
package com.labflow.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps request content out of the report (spec 007, functional spec 2.5): no Maven module may
 * depend on Allure's web-request filter. Plain text, like the other guards: the artifact name
 * counts wherever it appears outside an XML comment (a comment explaining the ban is fine).
 */
class NoAllureRequestCaptureGuardTest {

    // Maven runs the checks with the module directory (api-tests) as the working directory.
    private static final List<Path> POMS = List.of(
            Path.of("..", "pom.xml"),
            Path.of("..", "common", "pom.xml"),
            Path.of("..", "test-data", "pom.xml"),
            Path.of("..", "api-client", "pom.xml"),
            Path.of("pom.xml"),
            Path.of("..", "ui-tests", "pom.xml"));

    // Written in two pieces so that this file does not contain the text it forbids.
    private static final String FORBIDDEN = "allure-" + "rest-assured";

    @Test
    @DisplayName("No module depends on Allure's web-request filter, because it would put the raw request and answer into the report")
    void noModuleDependsOnAllureRestAssured() {
        List<String> offences = new ArrayList<>();
        for (Path pom : POMS) {
            assertThat(pom.toAbsolutePath().normalize())
                    .as("The guard must find every pom.xml, otherwise it would pass without scanning it")
                    .isRegularFile();
            if (withoutXmlComments(read(pom)).contains(FORBIDDEN)) {
                offences.add(pom.toAbsolutePath().normalize().getParent().getFileName() + "/pom.xml");
            }
        }

        assertThat(offences)
                .as("Remove the %s dependency from: %s. That filter attaches the raw request and answer to the report, "
                        + "including the Authorization header and patient names; the request steps come from the run log line instead.",
                        FORBIDDEN, String.join(", ", offences))
                .isEmpty();
    }

    @Test
    @DisplayName("The guard spots the dependency outside a comment and leaves a mention inside a comment alone")
    void guardIgnoresCommentsButSpotsRealUse() {
        String dependency = "<dependency><artifactId>" + FORBIDDEN + "</artifactId></dependency>";

        assertThat(withoutXmlComments(dependency)).contains(FORBIDDEN);
        assertThat(withoutXmlComments("<!-- never add " + FORBIDDEN + " -->\n<project/>")).doesNotContain(FORBIDDEN);
    }

    private static String withoutXmlComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.toAbsolutePath(), e);
        }
    }
}
