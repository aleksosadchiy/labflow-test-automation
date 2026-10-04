// @layer: integration
// @spec: 005-reliable-isolated-execution
// @regression
package com.labflow.tests.happy;

import com.labflow.common.config.LabConfig;
import com.labflow.common.model.Order;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.testdata.Patients;
import com.labflow.tests.support.LabTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the run log is useful and safe to share (functional-spec.md section 2.6): the lines this
 * check caused are in the log file under the check's name with the identifiers involved, and
 * neither the patient's name, the access key nor an {@code Authorization} scheme appears anywhere
 * in it.
 *
 * <p>The log file is written by every check of the run, possibly at the same time, so this check
 * only asserts about its own data: its own identifiers and its own patient name, plus the two
 * secrets that must never appear in any line.
 */
class RunLogSecrecyTest extends LabTest {

    /** Written by logback-test.xml; Maven runs the checks with the module directory as working directory. */
    private static final Path RUN_LOG = Path.of("target", "logs", "api-tests.log");

    @Test
    @DisplayName("The run log records this check's requests under its name with their identifiers, and never the patient's name or the access key")
    void runLogHoldsTheCheckAndNeverASecretOrAName(TestInfo testInfo) throws IOException {
        String patientName = Patients.valid().name();
        Patient patient = lab.patients().register(patientName);
        Order order = lab.orders().place(patient.id());
        Sample sample = lab.samples().register(patient.id(), SampleType.BLOOD, order.id());

        String checkName = "RunLogSecrecyTest › " + testInfo.getDisplayName();
        String accessKey = LabConfig.fromEnvironment().apiKey().reveal();
        // A partly written line from another check must not stop the read, so decode leniently.
        String log = new String(Files.readAllBytes(RUN_LOG), StandardCharsets.UTF_8);
        List<String> lines = log.lines().toList();

        // The lines were written (the file is flushed after every line) and belong to this check.
        List<String> sampleLines = lines.stream()
                .filter(line -> line.contains(sample.id()))
                .toList();
        assertThat(sampleLines)
                .as("log lines naming this check's sample %s", sample.id())
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line).contains(checkName));
        assertThat(sampleLines)
                .as("the line of the sample registration also names the patient and order identifiers")
                .anySatisfy(line -> assertThat(line)
                        .contains("POST /api/samples -> 201")
                        .contains("patient=" + patient.id())
                        .contains("order=" + order.id())
                        .contains("sample=" + sample.id()));

        // Nothing secret or identifying, anywhere in the file.
        assertThat(log).as("the run log never contains this check's patient name").doesNotContain(patientName);
        assertThat(log).as("the run log never contains the access key").doesNotContain(accessKey);
        assertThat(log).as("the run log never contains an Authorization scheme").doesNotContain("Bearer");
    }
}
