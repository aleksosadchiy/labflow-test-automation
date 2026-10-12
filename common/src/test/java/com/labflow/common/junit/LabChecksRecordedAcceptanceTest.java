package com.labflow.common.junit;

// @layer: integration
// @spec: 007-allure-report
// @regression

import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.FileSystemResultsWriter;
import io.qameta.allure.junitplatform.AllureJunitPlatform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Feature-level proof of spec 007 FS 2.2 (paragraphs 2, 3 and 6) through Allure's REAL adapter:
 * two throwaway checks, one tagged like a lab check and one untagged like a self-check, are run
 * through a JUnit {@link Launcher} with {@link LabChecksAllureListener} in front of the adapter.
 * Exactly the tagged check must leave a result file, under its plain-language name, with its tag
 * labels. {@link LabChecksAllureListenerTest} proves the filter against a fake; this proves the
 * filter and the adapter together write what the report is built from.
 *
 * <p>Safe next to the rest of the suite: the adapter is given its own {@link AllureLifecycle}
 * writing into a temporary folder, so Allure's global lifecycle is never touched; the launcher
 * has listener auto-registration switched off, so nothing else records the probes; and the probes
 * are static nested classes, which Surefire's default excludes never run as real tests.
 *
 * <h2>Spec 007 criteria not testable inside a module</h2>
 * See {@code Spec007ReportAcceptanceTest} in {@code api-client} for the full traceability of all
 * 41 criteria.
 */
class LabChecksRecordedAcceptanceTest {

    /** Stands for a lab check: it carries a lab tag, as every {@code @ApiCheck} / {@code @UiCheck} does. */
    @Tag("api")
    @Tag("regression")
    static class LabLikeProbe {
        @Test
        @DisplayName("A registered sample starts as Accessioned")
        void labCheck() {
        }
    }

    /** Stands for a self-check of the framework: no lab tag. */
    static class SelfCheckProbe {
        @Test
        @DisplayName("The framework counts its own rows")
        void selfCheck() {
        }
    }

    private static List<Path> runProbes(Path resultsDir, Class<?>... probes) throws IOException {
        AllureJunitPlatform adapter = new AllureJunitPlatform(new AllureLifecycle(new FileSystemResultsWriter(resultsDir)));
        Launcher launcher = LauncherFactory.create(
                LauncherConfig.builder().enableTestExecutionListenerAutoRegistration(false).build());
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request();
        for (Class<?> probe : probes) {
            request.selectors(selectClass(probe));
        }
        launcher.execute(request.build(), new LabChecksAllureListener(adapter));
        try (Stream<Path> files = Files.list(resultsDir)) {
            return files.filter(file -> file.getFileName().toString().endsWith("-result.json")).toList();
        }
    }

    @Test
    @DisplayName("Of a lab-tagged check and an untagged self-check run together, only the lab check is recorded, under its plain-language name and with its tag labels")
    void onlyTheLabCheckIsRecordedWithItsNameAndLabels(@TempDir Path resultsDir) throws IOException {
        List<Path> results = runProbes(resultsDir, LabLikeProbe.class, SelfCheckProbe.class);

        assertEquals(1, results.size(), "expected one result file, for the lab check only: " + results);
        String result = Files.readString(results.get(0));
        assertTrue(result.contains("\"name\":\"A registered sample starts as Accessioned\""), "display name missing in " + result);
        assertTrue(result.contains("\"status\":\"passed\""), "verdict missing in " + result);
        assertTrue(result.contains("\"name\":\"tag\",\"value\":\"api\""), "api tag label missing in " + result);
        assertTrue(result.contains("\"name\":\"tag\",\"value\":\"regression\""), "regression tag label missing in " + result);
        assertTrue(!result.contains("The framework counts its own rows"), "the self-check leaked into " + result);
    }

    @Test
    @DisplayName("A run of self-checks alone records nothing")
    void aRunOfSelfChecksAloneRecordsNothing(@TempDir Path resultsDir) throws IOException {
        List<Path> results = runProbes(resultsDir, SelfCheckProbe.class);

        assertTrue(results.isEmpty(), "expected no result file for a self-check, found " + results);
    }
}
