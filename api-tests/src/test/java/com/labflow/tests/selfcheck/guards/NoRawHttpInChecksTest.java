// @layer: unit
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.selfcheck.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the low-level web-request details out of the checks: every talk to the lab goes through
 * the lab layer (the api-client module), so no source file in this module may mention the
 * web-request library's package. The rule is deliberately plain text: any line that contains the
 * package name counts, whether it is an import, a static import or a fully qualified use.
 */
class NoRawHttpInChecksTest {

    // Written in two pieces so that this file does not contain the text it forbids (and so needs no exemption).
    private static final String WEB_REQUEST_PACKAGE = "io." + "restassured";

    // Maven runs the checks with the module directory (api-tests) as the working directory.
    private static final Path CHECKS_ROOT = Path.of("src", "test", "java");

    @Test
    @DisplayName("Checks do not use the web-request library directly")
    void checksDoNotUseTheWebRequestLibraryDirectly() throws IOException {
        assertThat(CHECKS_ROOT.toAbsolutePath())
                .as("The guard must look at the checks' source folder, otherwise it would pass without scanning anything")
                .isDirectory();

        List<Path> sources = javaFilesUnder(CHECKS_ROOT);
        assertThat(sources)
                .as("The guard must find at least one check source file, otherwise it would pass without scanning anything")
                .isNotEmpty();

        List<String> offences = new ArrayList<>();
        for (Path source : sources) {
            String shownName = CHECKS_ROOT.relativize(source).toString().replace('\\', '/');
            offences.addAll(offencesIn(shownName, Files.readAllLines(source)));
        }

        assertThat(offences)
                .as("Checks must not use the web-request library directly; found it in: %s", String.join(" | ", offences))
                .isEmpty();
    }

    @Test
    @DisplayName("The guard spots an import, a static import and a fully qualified use, and leaves clean lines alone")
    void guardSpotsEveryKindOfUseAndIgnoresCleanLines() {
        List<String> lines = List.of(
                "package com.labflow.tests.rejection;",                                        // 1: clean
                "import " + WEB_REQUEST_PACKAGE + ".RestAssured;",                             // 2: import
                "import com.labflow.client.Lab;",                                              // 3: clean
                "import static " + WEB_REQUEST_PACKAGE + ".RestAssured.given;",                // 4: static import
                "    var spec = " + WEB_REQUEST_PACKAGE + ".RestAssured.given();"              // 5: fully qualified use
        );

        assertThat(offencesIn("rejection/Foo.java", lines)).containsExactly(
                "rejection/Foo.java:2: import " + WEB_REQUEST_PACKAGE + ".RestAssured;",
                "rejection/Foo.java:4: import static " + WEB_REQUEST_PACKAGE + ".RestAssured.given;",
                "rejection/Foo.java:5: var spec = " + WEB_REQUEST_PACKAGE + ".RestAssured.given();");

        assertThat(offencesIn("rejection/Clean.java", lines.subList(0, 1))).isEmpty();
    }

    /** Every line of one file that mentions the web-request package, as {@code file:line: text}. */
    private static List<String> offencesIn(String fileName, List<String> lines) {
        List<String> offences = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(WEB_REQUEST_PACKAGE)) {
                offences.add(fileName + ":" + (i + 1) + ": " + lines.get(i).trim());
            }
        }
        return offences;
    }

    /** All {@code .java} files below the folder, including those in sub-folders. */
    private static List<Path> javaFilesUnder(Path folder) {
        try (Stream<Path> paths = Files.walk(folder)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the checks' source folder " + folder, e);
        }
    }
}
