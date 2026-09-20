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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for {@code PUT /api/samples/{id}/status} (functional-spec.md
 * §2.5 and §2.8): the accepted move to the next stage, every kind of rejected
 * move (with the sample left exactly where it was), the order in which input
 * problems, an unknown sample and a rejected move are reported, the wrong verbs
 * and the missing key, the screens seeing the same state, and simultaneous
 * requests.
 *
 * <p>Every test registers its own fresh sample and only ever looks at that one,
 * so the shared application context (and the other classes' samples in it)
 * never interferes. The stages are listed here by their wire names, in the
 * order the lab moves through them, so the walk does not depend on the
 * production enum.
 */
class SampleStatusApiAcceptanceTest extends AbstractApiAcceptanceTest {

    private static final List<String> STAGES = List.of(
            "ACCESSIONED", "READY_FOR_PROCESSING", "PROCESSING", "SEQUENCING",
            "BIOINFORMATICS", "QUALITY_CHECK", "RESULT_READY", "REPORT_GENERATED");

    private static final String SEEDED_USERNAME = "LabTech";
    private static final String SEEDED_PASSWORD = "I_Like_This_Lab";

    // ---- accepted moves ------------------------------------------------------------------------

    // @regression
    @Test
    void aSampleCanBeWalkedThroughEveryStageOneAcceptedPutAtATime() throws Exception {
        String id = registerSample();

        for (int i = 1; i < STAGES.size(); i++) {
            String stage = STAGES.get(i);

            mockMvc.perform(putStatus(id, stage))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                    .andExpect(jsonPath("$.sampleType").value("BLOOD"))
                    .andExpect(jsonPath("$.orderId").value(org.hamcrest.Matchers.startsWith("ORD-")))
                    .andExpect(jsonPath("$.createdAt").isString())
                    .andExpect(jsonPath("$.status").value(stage));

            assertSampleAt(id, stage);
        }
    }

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"Ready for Processing", "ready_for_processing", "READY FOR PROCESSING",
            "  ready_for_processing  ", "Ready_For_Processing"})
    void theNextStageIsAcceptedUnderItsDisplayNameOrInAnyLetterCaseAndReturnedAsTheEnumName(String spelling)
            throws Exception {
        String id = registerSample();

        mockMvc.perform(putStatus(id, spelling))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_PROCESSING"));

        assertSampleAt(id, "READY_FOR_PROCESSING");
    }

    // @regression
    @Test
    void aClientSuppliedIdOrOtherFieldInTheBodyIsIgnored() throws Exception {
        String id = registerSample();
        String otherId = "SMP-" + UUID.randomUUID();

        mockMvc.perform(withKey(put("/api/samples/" + id + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"READY_FOR_PROCESSING\", \"id\": \"" + otherId
                                + "\", \"sampleType\": \"SWAB\", \"patientId\": \"PAT-1002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sampleType").value("BLOOD"))
                .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                .andExpect(jsonPath("$.status").value("READY_FOR_PROCESSING"));

        mockMvc.perform(withKey(get("/api/samples/" + otherId))).andExpect(status().isNotFound());
    }

    // ---- rejected moves (409) ------------------------------------------------------------------

    // @regression
    @Test
    void skippingAheadFromAccessionedToProcessingIsRejectedAndTheSampleStaysPut() throws Exception {
        String id = registerSample();

        expectError(mockMvc.perform(putStatus(id, "PROCESSING")), 409, "Conflict",
                "Invalid status transition: cannot move from Accessioned to Processing.", statusPath(id));

        assertSampleAt(id, "ACCESSIONED");
    }

    // @regression
    @Test
    void skippingAheadFromProcessingToResultReadyIsRejectedAndTheSampleStaysPut() throws Exception {
        String id = registerSample();
        walkTo(id, "PROCESSING");

        expectError(mockMvc.perform(putStatus(id, "RESULT_READY")), 409, "Conflict",
                "Invalid status transition: cannot move from Processing to Result Ready.", statusPath(id));

        assertSampleAt(id, "PROCESSING");
    }

    // @regression
    @Test
    void movingBackwardIsRejectedAndTheSampleStaysPut() throws Exception {
        String id = registerSample();
        walkTo(id, "RESULT_READY");

        expectError(mockMvc.perform(putStatus(id, "ACCESSIONED")), 409, "Conflict",
                "Invalid status transition: cannot move from Result Ready to Accessioned.", statusPath(id));

        assertSampleAt(id, "RESULT_READY");
    }

    // @regression
    @Test
    void repeatingThePutOfAStageTheSampleAlreadyReachedIsRejectedAndTheSampleStaysPut() throws Exception {
        String id = registerSample();
        mockMvc.perform(putStatus(id, "READY_FOR_PROCESSING")).andExpect(status().isOk());

        expectError(mockMvc.perform(putStatus(id, "READY_FOR_PROCESSING")), 409, "Conflict",
                "Invalid status transition: cannot move from Ready for Processing to Ready for Processing.",
                statusPath(id));

        assertSampleAt(id, "READY_FOR_PROCESSING");
    }

    // @regression
    @ParameterizedTest
    @CsvSource({
            "ACCESSIONED,Accessioned",
            "READY_FOR_PROCESSING,Ready for Processing",
            "PROCESSING,Processing",
            "SEQUENCING,Sequencing",
            "BIOINFORMATICS,Bioinformatics",
            "QUALITY_CHECK,Quality Check",
            "RESULT_READY,Result Ready",
            "REPORT_GENERATED,Report Generated"})
    void noTargetIsAcceptedOnceTheSampleHasReachedReportGenerated(String target, String targetDisplayName)
            throws Exception {
        String id = registerSample();
        walkTo(id, "REPORT_GENERATED");

        expectError(mockMvc.perform(putStatus(id, target)), 409, "Conflict",
                "Invalid status transition: cannot move from Report Generated to " + targetDisplayName + ".",
                statusPath(id));

        assertSampleAt(id, "REPORT_GENERATED");
    }

    // ---- missing or unknown status (400) -------------------------------------------------------

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\": null}",
            "{}",
            "{\"status\": \"\"}",
            "{\"status\": \"   \"}"})
    void aNullMissingEmptyOrBlankStatusIsRejectedAndTheSampleStaysPut(String body) throws Exception {
        String id = registerSample();

        expectError(mockMvc.perform(putBody(id, body)), 400, "Bad Request", "Status is required.", statusPath(id));

        assertSampleAt(id, "ACCESSIONED");
    }

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"NOPE", "READY_FOR", "Ready for", "PROCESSING!", "COMPLETED"})
    void anUnknownStatusIsRejectedAndTheSampleStaysPut(String status) throws Exception {
        String id = registerSample();

        expectError(mockMvc.perform(putStatus(id, status)), 400, "Bad Request", "Invalid status.", statusPath(id));

        assertSampleAt(id, "ACCESSIONED");
    }

    // ---- unknown sample (404) and which failure wins -------------------------------------------

    // @regression
    @Test
    void anUnknownSampleWithAValidStatusIsNotFound() throws Exception {
        String unknownId = "SMP-" + UUID.randomUUID();

        expectError(mockMvc.perform(putStatus(unknownId, "READY_FOR_PROCESSING")), 404, "Not Found",
                "Sample not found.", statusPath(unknownId));

        mockMvc.perform(withKey(get("/api/samples/" + unknownId))).andExpect(status().isNotFound());
    }

    // @regression
    @Test
    void aMissingStatusIsReportedBeforeAnUnknownSample() throws Exception {
        String unknownId = "SMP-" + UUID.randomUUID();

        expectError(mockMvc.perform(putBody(unknownId, "{}")), 400, "Bad Request", "Status is required.",
                statusPath(unknownId));
    }

    // @regression
    @Test
    void anUnknownStatusIsReportedBeforeAnUnknownSample() throws Exception {
        String unknownId = "SMP-" + UUID.randomUUID();

        expectError(mockMvc.perform(putStatus(unknownId, "NOPE")), 400, "Bad Request", "Invalid status.",
                statusPath(unknownId));
    }

    // @regression
    @Test
    void anUnknownSampleIsReportedBeforeARejectedMove() throws Exception {
        String unknownId = "SMP-" + UUID.randomUUID();

        expectError(mockMvc.perform(putStatus(unknownId, "REPORT_GENERATED")), 404, "Not Found",
                "Sample not found.", statusPath(unknownId));
    }

    // ---- malformed body (400) ------------------------------------------------------------------

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"{\"status\": ", "not json at all", "[\"READY_FOR_PROCESSING\"]", "\"READY_FOR_PROCESSING\""})
    void aBodyThatIsNotAStatusObjectIsMalformedAndTheSampleStaysPut(String body) throws Exception {
        String id = registerSample();

        expectError(mockMvc.perform(putBody(id, body)), 400, "Bad Request", "Malformed request body.",
                statusPath(id));

        assertSampleAt(id, "ACCESSIONED");
    }

    // @regression
    @Test
    void aRequestWithNoBodyAtAllIsMalformedAndTheSampleStaysPut() throws Exception {
        String id = registerSample();

        expectError(mockMvc.perform(withKey(put("/api/samples/" + id + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)),
                400, "Bad Request", "Malformed request body.", statusPath(id));

        assertSampleAt(id, "ACCESSIONED");
    }

    // ---- wrong verbs (405) ---------------------------------------------------------------------

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PATCH", "DELETE"})
    void anyVerbOtherThanPutOnTheStatusPathIsNotAllowedAndTheAllowHeaderListsPut(String verb) throws Exception {
        String id = registerSample();

        MvcResult result = mockMvc.perform(withKey(request(HttpMethod.valueOf(verb), statusPath(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"READY_FOR_PROCESSING\"}")))
                .andReturn();

        expectError(result, 405, "Method Not Allowed", "Method not allowed.", statusPath(id));
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("PUT");
        assertSampleAt(id, "ACCESSIONED");
    }

    // ---- access key ----------------------------------------------------------------------------

    // @regression
    @Test
    void aPutWithoutAKeyIsUnauthorizedAndTheSampleStaysPut() throws Exception {
        String id = registerSample();

        expectError(mockMvc.perform(put(statusPath(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"READY_FOR_PROCESSING\"}")),
                401, "Unauthorized", "Unauthorized.", statusPath(id));

        assertSampleAt(id, "ACCESSIONED");
    }

    // ---- the screens see the same state (functional-spec.md §2.8) ------------------------------

    // @regression
    @Test
    void aStageSetThroughTheApiIsShownOnTheSampleDetailsScreen() throws Exception {
        String id = registerSample();
        mockMvc.perform(putStatus(id, "READY_FOR_PROCESSING")).andExpect(status().isOk());
        MockHttpSession session = signIn();

        String page = mockMvc.perform(get("/samples/" + id).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains(id);
        assertThat(page).containsPattern("id=\"current-status\"[^>]*>READY_FOR_PROCESSING<");
        assertThat(page).containsPattern("class=\"stage-current\"[^>]*>READY_FOR_PROCESSING<");
    }

    // @regression
    @Test
    void aStageReachedWithTheScreensAdvanceButtonIsSeenByTheApiAndTheNextPutBuildsOnIt() throws Exception {
        String id = registerSample();
        mockMvc.perform(putStatus(id, "READY_FOR_PROCESSING")).andExpect(status().isOk());
        MockHttpSession session = signIn();

        mockMvc.perform(post("/samples/" + id + "/advance").session(session))
                .andExpect(status().is3xxRedirection());

        assertSampleAt(id, "PROCESSING");
        expectError(mockMvc.perform(putStatus(id, "PROCESSING")), 409, "Conflict",
                "Invalid status transition: cannot move from Processing to Processing.", statusPath(id));
        mockMvc.perform(putStatus(id, "SEQUENCING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEQUENCING"));
        assertSampleAt(id, "SEQUENCING");
    }

    // @regression
    @Test
    void afterTheFullWalkTheReportScreenShowsTheReportForTheSample() throws Exception {
        String id = registerSample();
        walkTo(id, "REPORT_GENERATED");
        MockHttpSession session = signIn();

        String page = mockMvc.perform(get("/samples/" + id + "/report").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains(id);
        assertThat(page).doesNotContain("not ready");
    }

    // ---- simultaneous requests (functional-spec.md §2.8) ---------------------------------------

    // @regression
    @Test
    void tenSimultaneousPutsOfTheNextStageAcceptExactlyOneAndTheSampleMovesExactlyOneStage() throws Exception {
        int requests = 10;
        String id = registerSample();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        List<MvcResult> results = new ArrayList<>();
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(putStatus(id, "READY_FOR_PROCESSING")).andReturn();
                }));
            }
            start.countDown();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        List<MvcResult> accepted = results.stream().filter(r -> r.getResponse().getStatus() == 200).toList();
        List<MvcResult> rejected = results.stream().filter(r -> r.getResponse().getStatus() == 409).toList();
        assertThat(accepted).hasSize(1);
        assertThat(rejected).hasSize(requests - 1);
        assertThat((String) JsonPath.read(accepted.get(0).getResponse().getContentAsString(), "$.status"))
                .isEqualTo("READY_FOR_PROCESSING");
        for (MvcResult result : rejected) {
            expectError(result, 409, "Conflict",
                    "Invalid status transition: cannot move from Ready for Processing to Ready for Processing.",
                    statusPath(id));
        }
        assertSampleAt(id, "READY_FOR_PROCESSING");
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static String statusPath(String sampleId) {
        return "/api/samples/" + sampleId + "/status";
    }

    /** A keyed {@code PUT} of {@code {"status": <status>}} to the sample's status path. */
    private static MockHttpServletRequestBuilder putStatus(String sampleId, String status) {
        return putBody(sampleId, "{\"status\": \"" + status + "\"}");
    }

    /** A keyed {@code PUT} with the given raw JSON body to the sample's status path. */
    private static MockHttpServletRequestBuilder putBody(String sampleId, String body) {
        return withKey(put(statusPath(sampleId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
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

    /** Asserts {@code GET /api/samples/{id}} reports exactly the given stage. */
    private void assertSampleAt(String sampleId, String stage) throws Exception {
        mockMvc.perform(withKey(get("/api/samples/" + sampleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleId))
                .andExpect(jsonPath("$.status").value(stage));
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
