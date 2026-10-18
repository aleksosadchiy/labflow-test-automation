// @layer: integration
// @spec: 008-report-navigation
// @regression
package com.labflow.tests.selfcheck.guards;

import com.labflow.client.LabClient;
import com.labflow.common.config.LabConfig;
import com.labflow.common.model.Order;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.testdata.Patients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A self-check of the framework (spec 008): the run log is useful and safe to share. It was a
 * lab check before; its subject is the framework's own log, not anything the lab does, so it is
 * not a scenario and carries no {@code api} or {@code ui} label. It therefore stays out of the
 * report and out of the {@code Lab checks run} count, and is still selected by the CI expression
 * {@code regression | !(api | ui)}. It still drives the lab, to have something to find in the log.
 *
 * <p>The lines this check caused must be in the log file under the check's name with the
 * identifiers involved, and neither the patient's name, the access key nor an
 * {@code Authorization} scheme may appear anywhere in it.
 *
 * <p>The log file is written by every check of the run, possibly at the same time, so this check
 * only asserts about its own data: its own identifiers and its own patient name, plus the two
 * secrets that must never appear in any line.
 *
 * <p><b>Nothing here may print a secret.</b> A failed text comparison prints both texts, which
 * here would be the whole log and the key. So the secrecy assertions compare booleans and counts,
 * and a failure names what leaked (the key or the patient's name) and how many lines, never the
 * value or the line.
 */
class RunLogSecrecyGuardTest {

    /** Written by logback-test.xml; Maven runs the checks with the module directory as working directory. */
    private static final Path RUN_LOG = Path.of("target", "logs", "api-tests.log");

    /** The key the log pattern ({@code %X{check}}) reads the name of the running check from. */
    private static final String CHECK_NAME_KEY = "check";

    private final LabClient lab = LabClient.create(LabConfig.fromEnvironment());

    @BeforeEach
    void putTheCheckNameIntoTheLog(TestInfo testInfo) {
        MDC.put(CHECK_NAME_KEY, checkName(testInfo));
    }

    @AfterEach
    void clearTheCheckName() {
        MDC.remove(CHECK_NAME_KEY);
    }

    @Test
    @DisplayName("The run log records this check's requests under its name with their identifiers, and never the patient's name or the access key")
    void runLogHoldsTheCheckAndNeverASecretOrAName(TestInfo testInfo) throws IOException {
        String patientName = Patients.valid().name();
        Patient patient = lab.patients().register(patientName);
        Order order = lab.orders().place(patient.id());
        Sample sample = lab.samples().register(patient.id(), SampleType.BLOOD, order.id());

        String checkName = checkName(testInfo);
        String accessKey = LabConfig.fromEnvironment().apiKey().reveal();
        // A partly written line from another check must not stop the read, so decode leniently.
        String log = new String(Files.readAllBytes(RUN_LOG), StandardCharsets.UTF_8);
        List<String> lines = log.lines().toList();

        // The lines were written (the file is flushed after every line) and belong to this check.
        List<String> sampleLines = lines.stream()
                .filter(line -> line.contains(sample.id()))
                .toList();
        long linesUnderAnotherName = sampleLines.stream().filter(line -> !line.contains(checkName)).count();
        assertThat(sampleLines).as("log lines naming this check's sample %s", sample.id()).isNotEmpty();
        assertThat(linesUnderAnotherName)
                .as("log lines naming this check's sample that do not carry the check's name")
                .isZero();
        boolean registrationLineHasTheIdentifiers = sampleLines.stream().anyMatch(line ->
                line.contains("POST /api/samples -> 201")
                        && line.contains("patient=" + patient.id())
                        && line.contains("order=" + order.id())
                        && line.contains("sample=" + sample.id()));
        assertThat(registrationLineHasTheIdentifiers)
                .as("the line of the sample registration also names the patient, order and sample identifiers")
                .isTrue();

        // Nothing secret or identifying, anywhere in the file. Only counts are compared, so a
        // failure names what leaked and how often, and never prints the value or the log.
        assertThat(lines.stream().filter(line -> line.contains(patientName)).count())
                .as("log lines that contain this check's patient name")
                .isZero();
        assertThat(lines.stream().filter(line -> line.contains(accessKey)).count())
                .as("log lines that contain the access key")
                .isZero();
        assertThat(lines.stream().filter(line -> line.contains("Bearer")).count())
                .as("log lines that contain an Authorization scheme")
                .isZero();
    }

    private static String checkName(TestInfo testInfo) {
        return "RunLogSecrecyGuardTest › " + testInfo.getDisplayName();
    }
}
