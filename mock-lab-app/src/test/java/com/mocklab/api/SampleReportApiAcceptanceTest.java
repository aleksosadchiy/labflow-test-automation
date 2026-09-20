package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for {@code GET /api/samples/{id}/progress}, {@code /result} and
 * {@code /report} (functional-spec.md §2.6 and §2.8): what each returns at each stage,
 * the "not ready yet" rejections, an unknown sample, the wrong verbs and the missing
 * key, the report matching the View Report screen, and samples not seeing each other.
 *
 * <p>Every test registers its own fresh samples and only ever looks at those, so the
 * shared application context never interferes. The stages are listed by their wire
 * names, in workflow order, so the walk does not depend on the production enum.
 */
class SampleReportApiAcceptanceTest extends AbstractApiAcceptanceTest {

    private static final List<String> STAGES = List.of(
            "ACCESSIONED", "READY_FOR_PROCESSING", "PROCESSING", "SEQUENCING",
            "BIOINFORMATICS", "QUALITY_CHECK", "RESULT_READY", "REPORT_GENERATED");

    private static final String SEEDED_USERNAME = "LabTech";
    private static final String SEEDED_PASSWORD = "I_Like_This_Lab";

    private static final String RESULT_NOT_READY = "Test result is not ready yet.";
    private static final String REPORT_NOT_READY = "Report is not ready yet.";

    // ---- a new sample --------------------------------------------------------------------------

    // @regression
    @Test
    void aNewSampleShowsOnlyAccessionedInItsProgress() throws Exception {
        String id = registerSample();

        String body = getOk(progressPath(id));

        assertThat((String) JsonPath.read(body, "$.sampleId")).isEqualTo(id);
        assertThat((String) JsonPath.read(body, "$.currentStatus")).isEqualTo("ACCESSIONED");
        assertThat(stageStatuses(body)).containsExactly("ACCESSIONED");
        assertThat(stageTimes(body)).hasSize(1);
    }

    // @regression
    @Test
    void aNewSampleHasNoResultYet() throws Exception {
        String id = registerSample();

        expectError(mockMvc.perform(withKey(get(resultPath(id)))), 409, "Conflict", RESULT_NOT_READY,
                resultPath(id));
    }

    // @regression
    @Test
    void aNewSampleHasNoReportYet() throws Exception {
        String id = registerSample();

        expectError(mockMvc.perform(withKey(get(reportPath(id)))), 409, "Conflict", REPORT_NOT_READY,
                reportPath(id));
    }

    // ---- progress grows with each accepted step ------------------------------------------------

    // @regression
    @Test
    void eachAcceptedStepAddsOneStageToTheProgressInWorkflowOrder() throws Exception {
        String id = registerSample();

        for (int i = 1; i < STAGES.size(); i++) {
            mockMvc.perform(putStatus(id, STAGES.get(i))).andExpect(status().isOk());

            String body = getOk(progressPath(id));

            assertThat(stageStatuses(body)).containsExactlyElementsOf(STAGES.subList(0, i + 1));
            assertThat((String) JsonPath.read(body, "$.currentStatus")).isEqualTo(STAGES.get(i));
            assertThat(stageStatuses(body).get(i)).isEqualTo((String) JsonPath.read(body, "$.currentStatus"));
            assertTimesAreNonDecreasing(stageTimes(body));
        }
    }

    // ---- result and report by stage ------------------------------------------------------------

    // @regression
    @Test
    void theResultIsStillNotReadyAtQualityCheck() throws Exception {
        String id = registerSample();
        walkTo(id, "QUALITY_CHECK");

        expectError(mockMvc.perform(withKey(get(resultPath(id)))), 409, "Conflict", RESULT_NOT_READY,
                resultPath(id));
        expectError(mockMvc.perform(withKey(get(reportPath(id)))), 409, "Conflict", REPORT_NOT_READY,
                reportPath(id));
    }

    // @regression
    @Test
    void atResultReadyTheResultIsAvailableButTheReportStillIsNot() throws Exception {
        String id = registerSample();
        walkTo(id, "RESULT_READY");

        String body = getOk(resultPath(id));

        assertThat((String) JsonPath.read(body, "$.sampleId")).isEqualTo(id);
        assertThat((String) JsonPath.read(body, "$.testName")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.resultValue")).isNotBlank();
        expectError(mockMvc.perform(withKey(get(reportPath(id)))), 409, "Conflict", REPORT_NOT_READY,
                reportPath(id));
    }

    // @regression
    @Test
    void atReportGeneratedTheReportIsAvailableAndTheResultAndFullProgressAreToo() throws Exception {
        String id = registerSample();
        walkTo(id, "RESULT_READY");
        String resultBefore = getOk(resultPath(id));
        mockMvc.perform(putStatus(id, "REPORT_GENERATED")).andExpect(status().isOk());

        String report = getOk(reportPath(id));
        String result = getOk(resultPath(id));
        String progress = getOk(progressPath(id));

        assertThat((String) JsonPath.read(report, "$.sampleId")).isEqualTo(id);
        assertThat((String) JsonPath.read(report, "$.testName")).isNotBlank();
        assertThat((String) JsonPath.read(report, "$.resultValue")).isNotBlank();
        assertThat(Instant.parse(JsonPath.read(report, "$.generatedAt"))).isNotNull();

        assertThat((String) JsonPath.read(result, "$.sampleId")).isEqualTo(id);
        assertThat((String) JsonPath.read(result, "$.testName"))
                .isEqualTo((String) JsonPath.read(resultBefore, "$.testName"))
                .isEqualTo((String) JsonPath.read(report, "$.testName"));
        assertThat((String) JsonPath.read(result, "$.resultValue"))
                .isEqualTo((String) JsonPath.read(resultBefore, "$.resultValue"))
                .isEqualTo((String) JsonPath.read(report, "$.resultValue"));

        assertThat((String) JsonPath.read(progress, "$.currentStatus")).isEqualTo("REPORT_GENERATED");
        assertThat(stageStatuses(progress)).containsExactlyElementsOf(STAGES);
        assertThat(stageTimes(progress)).hasSize(STAGES.size());
        assertTimesAreNonDecreasing(stageTimes(progress));
    }

    // ---- the screens see the same lab (functional-spec.md §2.8) --------------------------------

    // @regression
    @Test
    void theApiReportShowsTheSameContentAsTheViewReportScreen() throws Exception {
        String id = registerSample();
        walkTo(id, "REPORT_GENERATED");
        MockHttpSession session = signIn();

        String report = getOk(reportPath(id));
        String page = mockMvc.perform(get("/samples/" + id + "/report").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(screenValue(page, "report-sample-id")).isEqualTo(id);
        assertThat(screenValue(page, "report-test-name"))
                .isEqualTo((String) JsonPath.read(report, "$.testName"));
        assertThat(screenValue(page, "report-result-value"))
                .isEqualTo((String) JsonPath.read(report, "$.resultValue"));
        assertThat(screenValue(page, "report-generated-at"))
                .isEqualTo((String) JsonPath.read(report, "$.generatedAt"));
    }

    // @regression
    @Test
    void aSampleAdvancedPartlyOnTheScreenAndPartlyThroughTheApiHasAConsistentProgress() throws Exception {
        String id = registerSample();
        MockHttpSession session = signIn();

        mockMvc.perform(putStatus(id, "READY_FOR_PROCESSING")).andExpect(status().isOk());
        advanceOnScreen(id, session);
        mockMvc.perform(putStatus(id, "SEQUENCING")).andExpect(status().isOk());
        advanceOnScreen(id, session);

        String body = getOk(progressPath(id));

        assertThat(stageStatuses(body)).containsExactly(
                "ACCESSIONED", "READY_FOR_PROCESSING", "PROCESSING", "SEQUENCING", "BIOINFORMATICS");
        assertThat((String) JsonPath.read(body, "$.currentStatus")).isEqualTo("BIOINFORMATICS");
        assertTimesAreNonDecreasing(stageTimes(body));
    }

    // ---- unknown sample (404) ------------------------------------------------------------------

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"progress", "result", "report"})
    void anUnknownSampleIsNotFound(String route) throws Exception {
        String unknownId = "SMP-" + UUID.randomUUID();
        String path = "/api/samples/" + unknownId + "/" + route;

        expectError(mockMvc.perform(withKey(get(path))), 404, "Not Found", "Sample not found.", path);
    }

    // ---- wrong verbs (405) ---------------------------------------------------------------------

    // @regression
    @ParameterizedTest
    @CsvSource({
            "progress,PUT", "progress,POST", "progress,PATCH", "progress,DELETE",
            "result,PUT", "result,POST", "result,PATCH", "result,DELETE",
            "report,PUT", "report,POST", "report,PATCH", "report,DELETE"})
    void anyVerbOtherThanGetIsNotAllowedAndTheAllowHeaderListsGet(String route, String verb) throws Exception {
        String id = registerSample();
        String path = "/api/samples/" + id + "/" + route;

        MvcResult result = mockMvc.perform(withKey(request(HttpMethod.valueOf(verb), path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andReturn();

        expectError(result, 405, "Method Not Allowed", "Method not allowed.", path);
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("GET");
    }

    // ---- access key ----------------------------------------------------------------------------

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"progress", "result", "report"})
    void aRequestWithoutAKeyIsUnauthorized(String route) throws Exception {
        String id = registerSample();
        walkTo(id, "REPORT_GENERATED");
        String path = "/api/samples/" + id + "/" + route;

        expectError(mockMvc.perform(get(path)), 401, "Unauthorized", "Unauthorized.", path);
    }

    // ---- isolation between samples -------------------------------------------------------------

    // @regression
    @Test
    void twoSamplesDoNotSeeEachOthersProgressResultOrReport() throws Exception {
        String finished = registerSample();
        walkTo(finished, "REPORT_GENERATED");
        String midway = registerSample();
        walkTo(midway, "RESULT_READY");
        String fresh = registerSample();

        String finishedReport = getOk(reportPath(finished));
        String midwayResult = getOk(resultPath(midway));
        String finishedProgress = getOk(progressPath(finished));
        String midwayProgress = getOk(progressPath(midway));
        String freshProgress = getOk(progressPath(fresh));

        assertThat((String) JsonPath.read(finishedReport, "$.sampleId")).isEqualTo(finished);
        assertThat((String) JsonPath.read(getOk(resultPath(finished)), "$.sampleId")).isEqualTo(finished);
        assertThat((String) JsonPath.read(midwayResult, "$.sampleId")).isEqualTo(midway);
        assertThat((String) JsonPath.read(finishedProgress, "$.sampleId")).isEqualTo(finished);
        assertThat((String) JsonPath.read(midwayProgress, "$.sampleId")).isEqualTo(midway);
        assertThat((String) JsonPath.read(freshProgress, "$.sampleId")).isEqualTo(fresh);

        assertThat(stageStatuses(finishedProgress)).hasSize(8);
        assertThat(stageStatuses(midwayProgress)).hasSize(7);
        assertThat(stageStatuses(freshProgress)).containsExactly("ACCESSIONED");

        expectError(mockMvc.perform(withKey(get(reportPath(midway)))), 409, "Conflict", REPORT_NOT_READY,
                reportPath(midway));
        expectError(mockMvc.perform(withKey(get(resultPath(fresh)))), 409, "Conflict", RESULT_NOT_READY,
                resultPath(fresh));
        expectError(mockMvc.perform(withKey(get(reportPath(fresh)))), 409, "Conflict", REPORT_NOT_READY,
                reportPath(fresh));
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static String progressPath(String sampleId) {
        return "/api/samples/" + sampleId + "/progress";
    }

    private static String resultPath(String sampleId) {
        return "/api/samples/" + sampleId + "/result";
    }

    private static String reportPath(String sampleId) {
        return "/api/samples/" + sampleId + "/report";
    }

    /** A keyed {@code GET} that must answer 200; returns the JSON body. */
    private String getOk(String path) throws Exception {
        return mockMvc.perform(withKey(get(path)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static List<String> stageStatuses(String progressBody) {
        return JsonPath.read(progressBody, "$.stages[*].status");
    }

    private static List<String> stageTimes(String progressBody) {
        return JsonPath.read(progressBody, "$.stages[*].reachedAt");
    }

    /** Every time must be an ISO-8601 instant and none may be earlier than the one before it. */
    private static void assertTimesAreNonDecreasing(List<String> times) {
        Instant previous = null;
        for (String time : times) {
            Instant current = Instant.parse(time);
            if (previous != null) {
                assertThat(current).isAfterOrEqualTo(previous);
            }
            previous = current;
        }
    }

    /** The text of the element with the given id on a rendered screen. */
    private static String screenValue(String page, String elementId) {
        Matcher matcher = Pattern.compile("id=\"" + elementId + "\"[^>]*>([^<]*)<").matcher(page);
        assertThat(matcher.find()).as("element %s on the report screen", elementId).isTrue();
        return matcher.group(1);
    }

    /** A keyed {@code PUT} of {@code {"status": <status>}} to the sample's status path. */
    private static MockHttpServletRequestBuilder putStatus(String sampleId, String status) {
        return withKey(put("/api/samples/" + sampleId + "/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\"}");
    }

    /** Registers a fresh BLOOD sample for PAT-1001 (its own new order) and returns its ID. */
    private String registerSample() throws Exception {
        MvcResult created = mockMvc.perform(withKey(post("/api/samples"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    /** Moves the sample forward, one accepted PUT at a time, until it reaches {@code stage}. */
    private void walkTo(String sampleId, String stage) throws Exception {
        for (int i = 1; i <= STAGES.indexOf(stage); i++) {
            mockMvc.perform(putStatus(sampleId, STAGES.get(i))).andExpect(status().isOk());
        }
    }

    /** Presses the Sample Details screen's advance button for the sample. */
    private void advanceOnScreen(String sampleId, MockHttpSession session) throws Exception {
        mockMvc.perform(post("/samples/" + sampleId + "/advance").session(session))
                .andExpect(status().is3xxRedirection());
    }

    /** Signs in through the real {@code POST /login} route and returns the session that carries the login. */
    private MockHttpSession signIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", SEEDED_USERNAME)
                        .param("password", SEEDED_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
