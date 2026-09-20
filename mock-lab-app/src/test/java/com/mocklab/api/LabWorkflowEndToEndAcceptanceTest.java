package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The regression anchor for the whole feature: one client working the lab from an empty
 * patient to a generated report purely through the API, then a signed-in user looking at the
 * same lab on the screens. If this test passes, the sections of functional-spec.md
 * (2.1 access, 2.2 patients, 2.3 orders, 2.4 samples, 2.5 stages, 2.6 progress/result/report,
 * 2.8 shared lab) work together, not just one by one.
 *
 * <h2>Sections 2.2, 2.3 and 2.6: criteria to tests</h2>
 * These sections had no gap after the slice tests, so they have no class of their own; their
 * whole-feature evidence is this flow plus:
 * <ul>
 *   <li><b>2.2 Register patient: created with lab-assigned ID, ID and name shown.</b>
 *       {@code PatientApiAcceptanceTest#registeringAPatientReturns201WithLocationAndTheGeneratedId},
 *       {@code #aClientSuppliedIdIsIgnoredBecauseTheLabAssignsIds}.</li>
 *   <li><b>2.2 Name missing or blank: "Patient name is required."</b>
 *       {@code PatientApiAcceptanceTest#aMissingNameIsRejectedAndNothingIsCreated},
 *       {@code #aBlankNameIsRejectedAndNothingIsCreated}, {@code #aNullNameIsRejectedAndNothingIsCreated}.</li>
 *   <li><b>2.2 Look up an existing (also a pre-loaded) patient: ID and name.</b>
 *       {@code PatientApiAcceptanceTest#theSeededPatientCanBeLookedUp}; all seven pre-loaded patients:
 *       {@code Spec2_9StartupStateAcceptanceTest#theSevenKnownPatientsAreAvailableWithTheirNames}.</li>
 *   <li><b>2.2 Look up an unknown patient: "Patient not found."</b>
 *       {@code PatientApiAcceptanceTest#anUnknownIdIsNotFound}.</li>
 *   <li><b>2.3 Place an order for an existing patient: order ID, patient and creation time shown.</b>
 *       {@code OrderApiAcceptanceTest#placingAnOrderReturns201WithLocationAndTheGeneratedId},
 *       {@code #anOrderCanBePlacedForAPatientRegisteredThroughTheApi}.</li>
 *   <li><b>2.3 No patient named: "Patient is required."</b>
 *       {@code OrderApiAcceptanceTest#aMissingPatientIdIsRejectedAndNothingIsCreated},
 *       {@code #aNullPatientIdIsRejectedAndNothingIsCreated}, {@code #aBlankPatientIdIsRejectedAndNothingIsCreated}.</li>
 *   <li><b>2.3 Unknown patient: "Patient not found."</b>
 *       {@code OrderApiAcceptanceTest#anUnknownPatientIsNotFoundAndNoOrderIsCreated}.</li>
 *   <li><b>2.3 Look up an existing order: its details.</b>
 *       {@code OrderApiAcceptanceTest#placingAnOrderReturns201WithLocationAndTheGeneratedId} (read back),
 *       {@code #anOrderListsItsSamplesWithTheStatusFromTheProcessingJob}.</li>
 *   <li><b>2.3 Look up an unknown order: "Order not found."</b>
 *       {@code OrderApiAcceptanceTest#anUnknownOrderIdIsNotFound}.</li>
 *   <li><b>2.6 Progress: current stage plus the ordered stages passed, each with the time reached.</b>
 *       {@code SampleReportApiAcceptanceTest#aNewSampleShowsOnlyAccessionedInItsProgress},
 *       {@code #eachAcceptedStepAddsOneStageToTheProgressInWorkflowOrder}.</li>
 *   <li><b>2.6 Progress / result / report of an unknown sample: "Sample not found."</b>
 *       {@code SampleReportApiAcceptanceTest#anUnknownSampleIsNotFound} (all three routes).</li>
 *   <li><b>2.6 Result at "Result Ready": sample ID, test name, result value.</b>
 *       {@code SampleReportApiAcceptanceTest#atResultReadyTheResultIsAvailableButTheReportStillIsNot}.</li>
 *   <li><b>2.6 Result before "Result Ready": "Test result is not ready yet."</b>
 *       {@code SampleReportApiAcceptanceTest#aNewSampleHasNoResultYet}, {@code #theResultIsStillNotReadyAtQualityCheck}.</li>
 *   <li><b>2.6 Report at "Report Generated": sample ID, test name, result value, generation date, same as the
 *       View Report screen.</b>
 *       {@code SampleReportApiAcceptanceTest#atReportGeneratedTheReportIsAvailableAndTheResultAndFullProgressAreToo},
 *       {@code #theApiReportShowsTheSameContentAsTheViewReportScreen}.</li>
 *   <li><b>2.6 Report before "Report Generated": "Report is not ready yet."</b>
 *       {@code SampleReportApiAcceptanceTest#aNewSampleHasNoReportYet}, {@code #atResultReadyTheResultIsAvailableButTheReportStillIsNot}.</li>
 * </ul>
 */
class LabWorkflowEndToEndAcceptanceTest extends AbstractSpecAcceptanceTest {

    // @regression
    @Test
    void aLabWorkflowRunsFromPatientToReportThroughTheApiAndIsSeenOnTheScreens() throws Exception {
        // 1. Register a patient (2.2)
        String patientName = "Workflow Patient " + UUID.randomUUID();
        String patient = mockMvc.perform(keyedPost("/api/patients", "{\"name\": \"" + patientName + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(patientName))
                .andReturn().getResponse().getContentAsString();
        String patientId = read(patient, "$.id");
        assertThat(patientId).startsWith("PAT-");

        // 2. Place an order for that patient (2.3); it starts with no samples
        String order = mockMvc.perform(keyedPost("/api/orders", "{\"patientId\": \"" + patientId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.samples").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String orderId = read(order, "$.id");

        // 3. Register a sample on that order (2.4): it starts at Accessioned
        String sample = registerSample(patientId, "BLOOD", orderId);
        String sampleId = read(sample, "$.id");
        assertThat(read(sample, "$.status")).isEqualTo("ACCESSIONED");
        assertThat(read(sample, "$.orderId")).isEqualTo(orderId);
        assertThat(read(sample, "$.order.id")).isEqualTo(orderId);

        //    ... a second BLOOD sample on the same order is refused, a SWAB one joins it
        expectError(mockMvc.perform(keyedPost("/api/samples",
                        "{\"patientId\": \"" + patientId + "\", \"sampleType\": \"BLOOD\", \"orderId\": \"" + orderId + "\"}")),
                409, "Conflict", "A sample of this type already exists for this order.", "/api/samples");
        String swabId = read(registerSample(patientId, "SWAB", orderId), "$.id");

        // 4. Nothing to read yet, and a skip ahead is refused (2.5, 2.6)
        String resultPath = "/api/samples/" + sampleId + "/result";
        String reportPath = "/api/samples/" + sampleId + "/report";
        expectError(mockMvc.perform(withKey(get(resultPath))), 409, "Conflict", "Test result is not ready yet.", resultPath);
        expectError(mockMvc.perform(withKey(get(reportPath))), 409, "Conflict", "Report is not ready yet.", reportPath);
        expectError(mockMvc.perform(putStatus(sampleId, "PROCESSING")), 409, "Conflict",
                "Invalid status transition: cannot move from Accessioned to Processing.",
                "/api/samples/" + sampleId + "/status");

        // 5. Move the sample through all seven remaining stages, one accepted request per stage (2.5),
        //    checking progress, result and report at every step (2.6)
        for (int i = 1; i < STAGES.size(); i++) {
            String stage = STAGES.get(i);
            mockMvc.perform(putStatus(sampleId, stage))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(stage));

            String progress = getOk("/api/samples/" + sampleId + "/progress");
            assertThat(read(progress, "$.currentStatus")).isEqualTo(stage);
            List<String> reached = JsonPath.read(progress, "$.stages[*].status");
            assertThat(reached).containsExactlyElementsOf(STAGES.subList(0, i + 1));

            boolean resultReady = i >= STAGES.indexOf("RESULT_READY");
            boolean reportReady = i >= STAGES.indexOf("REPORT_GENERATED");
            mockMvc.perform(withKey(get(resultPath))).andExpect(status().is(resultReady ? 200 : 409));
            mockMvc.perform(withKey(get(reportPath))).andExpect(status().is(reportReady ? 200 : 409));
        }
        String result = getOk(resultPath);
        String report = getOk(reportPath);
        assertThat(read(result, "$.sampleId")).isEqualTo(sampleId);
        assertThat(read(report, "$.sampleId")).isEqualTo(sampleId);
        assertThat(read(report, "$.testName")).isNotBlank().isEqualTo(read(result, "$.testName"));
        assertThat(read(report, "$.resultValue")).isNotBlank().isEqualTo(read(result, "$.resultValue"));

        //    ... and the finished sample accepts no further move
        expectError(mockMvc.perform(putStatus(sampleId, "ACCESSIONED")), 409, "Conflict",
                "Invalid status transition: cannot move from Report Generated to Accessioned.",
                "/api/samples/" + sampleId + "/status");

        // 6. The API tells the same story from every angle
        mockMvc.perform(withKey(get("/api/samples/" + sampleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPORT_GENERATED"))
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.orderId").value(orderId));
        mockMvc.perform(withKey(get("/api/orders/" + orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples.length()").value(2))
                .andExpect(jsonPath("$.samples[?(@.id == '" + sampleId + "')].status").value("REPORT_GENERATED"))
                .andExpect(jsonPath("$.samples[?(@.id == '" + swabId + "')].status").value("ACCESSIONED"));
        mockMvc.perform(withKey(get("/api/samples")))
                .andExpect(jsonPath("$[?(@.id == '" + sampleId + "')].status").value("REPORT_GENERATED"));

        // 7. A signed-in user sees the same lab on the screens (2.8)
        MockHttpSession session = signIn();
        String list = mockMvc.perform(get("/samples").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(rowFor(list, sampleId)).contains(patientName).contains(">REPORT_GENERATED<");
        assertThat(rowFor(list, swabId)).contains(patientName).contains(">ACCESSIONED<");

        String details = mockMvc.perform(get("/samples/" + sampleId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(screenValue(details, "sample-id")).isEqualTo(sampleId);
        assertThat(screenValue(details, "patient-name")).isEqualTo(patientName);
        assertThat(screenValue(details, "current-status")).isEqualTo("REPORT_GENERATED");

        String reportScreen = mockMvc.perform(get("/samples/" + sampleId + "/report").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(screenValue(reportScreen, "report-sample-id")).isEqualTo(sampleId);
        assertThat(screenValue(reportScreen, "report-test-name")).isEqualTo(read(report, "$.testName"));
        assertThat(screenValue(reportScreen, "report-result-value")).isEqualTo(read(report, "$.resultValue"));
        assertThat(screenValue(reportScreen, "report-generated-at")).isEqualTo(read(report, "$.generatedAt"));

        // 8. None of it was readable without the key (2.1)
        expectError(mockMvc.perform(get("/api/samples/" + sampleId)),
                401, "Unauthorized", "Unauthorized.", "/api/samples/" + sampleId);
    }

    /** The text of the element with the given id on a rendered screen. */
    private static String screenValue(String page, String elementId) {
        Matcher matcher = Pattern.compile("id=\"" + elementId + "\"[^>]*>([^<]*)<").matcher(page);
        assertThat(matcher.find()).as("element %s on the screen", elementId).isTrue();
        return matcher.group(1);
    }
}
