package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for {@code POST /api/samples}, {@code GET /api/samples} and
 * {@code GET /api/samples/{id}} (functional-spec.md §2.4, §2.8). Every scenario
 * runs over MockMvc against the real application context; each test registers
 * its own samples and only ever looks for those by ID.
 *
 * <p>The application context is cached and shared with the other API test
 * classes, so samples from other classes may be present in the list. The list
 * checks therefore look for the sample's own ID and never assert an exact list
 * or its size; the "fresh application lists nothing" case lives in
 * {@link SampleListEmptyApiAcceptanceTest}.
 */
class SampleApiAcceptanceTest extends AbstractApiAcceptanceTest {

    private static final String SEEDED_USERNAME = "LabTech";
    private static final String SEEDED_PASSWORD = "I_Like_This_Lab";

    // ---- register and read back ---------------------------------------------------------------

    // @regression
    @Test
    void registeringABloodSampleReturns201WithLocationAndTheNewSampleAndItsOrder() throws Exception {
        MvcResult created = mockMvc.perform(withKey(post("/api/samples"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("PAT-1001", "BLOOD")))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("SMP-")))
                .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                .andExpect(jsonPath("$.sampleType").value("BLOOD"))
                .andExpect(jsonPath("$.status").value("ACCESSIONED"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.orderId").value(org.hamcrest.Matchers.startsWith("ORD-")))
                .andExpect(jsonPath("$.order.id").value(org.hamcrest.Matchers.startsWith("ORD-")))
                .andExpect(jsonPath("$.order.patientId").value("PAT-1001"))
                .andExpect(jsonPath("$.order.createdAt").isString())
                .andReturn();

        String body = created.getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/samples/" + id);
        assertThat((String) JsonPath.read(body, "$.order.id")).isEqualTo(JsonPath.read(body, "$.orderId"));
        assertThat(Instant.parse(JsonPath.read(body, "$.createdAt"))).isNotNull();
        assertThat(Instant.parse(JsonPath.read(body, "$.order.createdAt"))).isNotNull();
    }

    // @regression
    @Test
    void aSampleTypeIsAcceptedInAnyLetterCaseAndReturnedAsTheEnumName() throws Exception {
        mockMvc.perform(withKey(post("/api/samples"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("PAT-1001", "blood")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sampleType").value("BLOOD"))
                .andExpect(jsonPath("$.status").value("ACCESSIONED"));
    }

    // @regression
    @Test
    void aRegisteredSampleCanBeLookedUpByItsId() throws Exception {
        MvcResult created = register("PAT-1001", "BLOOD");
        String body = created.getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mockMvc.perform(withKey(get("/api/samples/" + id)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                .andExpect(jsonPath("$.orderId").value((String) JsonPath.read(body, "$.orderId")))
                .andExpect(jsonPath("$.sampleType").value("BLOOD"))
                .andExpect(jsonPath("$.status").value("ACCESSIONED"))
                .andExpect(jsonPath("$.createdAt").value((String) JsonPath.read(body, "$.createdAt")))
                .andExpect(jsonPath("$.order").doesNotExist());
    }

    // @regression
    @Test
    void aRegisteredSampleAppearsInTheSampleList() throws Exception {
        String id = idOf(register("PAT-1001", "BLOOD"));

        MvcResult list = mockMvc.perform(withKey(get("/api/samples")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        JSONArray mine = JsonPath.read(list.getResponse().getContentAsString(), "$[?(@.id == '" + id + "')]");
        assertThat(mine).hasSize(1);
        mockMvc.perform(withKey(get("/api/samples")))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].patientId").value("PAT-1001"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].sampleType").value("BLOOD"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].status").value("ACCESSIONED"));
    }

    // @regression
    @Test
    void twoSamplesRegisteredForTheSamePatientGetDifferentIdsAndOrders() throws Exception {
        MvcResult first = register("PAT-1001", "BLOOD");
        MvcResult second = register("PAT-1001", "BLOOD");

        assertThat(idOf(first)).isNotEqualTo(idOf(second));
        assertThat((String) JsonPath.read(first.getResponse().getContentAsString(), "$.orderId"))
                .isNotEqualTo(JsonPath.read(second.getResponse().getContentAsString(), "$.orderId"));
    }

    // @regression
    @Test
    void aClientSuppliedIdAndStatusAreIgnored() throws Exception {
        String clientId = "SMP-" + UUID.randomUUID();

        MvcResult created = mockMvc.perform(withKey(post("/api/samples"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"" + clientId + "\", \"status\": \"REPORT_GENERATED\","
                                + " \"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\", \"extra\": true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCESSIONED"))
                .andReturn();

        String id = idOf(created);
        assertThat(id).startsWith("SMP-").isNotEqualTo(clientId);
        mockMvc.perform(withKey(get("/api/samples/" + clientId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(withKey(get("/api/samples/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCESSIONED"));
    }

    // ---- validation (400) ---------------------------------------------------------------------

    // @regression
    @Test
    void aMissingPatientIsRejected() throws Exception {
        expectError(keyedPost("{\"sampleType\": \"BLOOD\"}"), 400, "Bad Request", "Patient is required.");
    }

    // @regression
    @Test
    void aNullPatientIsRejected() throws Exception {
        expectError(keyedPost("{\"patientId\": null, \"sampleType\": \"BLOOD\"}"),
                400, "Bad Request", "Patient is required.");
    }

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   \t  "})
    void aBlankPatientIsRejected(String blank) throws Exception {
        expectError(keyedPost(json(blank, "BLOOD")), 400, "Bad Request", "Patient is required.");
    }

    // @regression
    @Test
    void aMissingSampleTypeIsRejected() throws Exception {
        expectError(keyedPost("{\"patientId\": \"PAT-1001\"}"), 400, "Bad Request", "Sample type is required.");
    }

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   \t  "})
    void aBlankSampleTypeIsRejected(String blank) throws Exception {
        expectError(keyedPost(json("PAT-1001", blank)), 400, "Bad Request", "Sample type is required.");
    }

    // @regression
    @Test
    void anInvalidSampleTypeIsRejected() throws Exception {
        expectError(keyedPost(json("PAT-1001", "PLASMA")), 400, "Bad Request", "Invalid sample type.");
    }

    // @regression
    @Test
    void whenSeveralThingsAreWrongOnlyThePatientProblemIsReported() throws Exception {
        expectError(keyedPost(json("", "")), 400, "Bad Request", "Patient is required.");
    }

    // ---- not found (404) ----------------------------------------------------------------------

    // @regression
    @Test
    void anUnknownPatientIsNotFound() throws Exception {
        expectError(keyedPost(json("PAT-" + UUID.randomUUID(), "BLOOD")),
                404, "Not Found", "Patient not found.");
    }

    // @regression
    @Test
    void anUnknownPatientAndAnInvalidTypeReportOnlyTheUnknownPatient() throws Exception {
        expectError(keyedPost(json("PAT-" + UUID.randomUUID(), "PLASMA")),
                404, "Not Found", "Patient not found.");
    }

    // @regression
    @Test
    void anUnknownSampleIdIsNotFound() throws Exception {
        String path = "/api/samples/SMP-" + UUID.randomUUID();

        expectError(mockMvc.perform(withKey(get(path))), 404, "Not Found", "Sample not found.", path);
    }

    // ---- malformed bodies ---------------------------------------------------------------------

    // @regression
    @Test
    void brokenJsonIsMalformedAndNeverEchoesThePayload() throws Exception {
        String secret = "Secret-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(withKey(post("/api/samples"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"" + secret + "\", "))
                .andReturn();

        expectError(result, 400, "Bad Request", "Malformed request body.", "/api/samples");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
    }

    // @regression
    @Test
    void aJsonArrayInsteadOfAnObjectIsMalformed() throws Exception {
        String secret = "Secret-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(withKey(post("/api/samples"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + secret + "\"]"))
                .andReturn();

        expectError(result, 400, "Bad Request", "Malformed request body.", "/api/samples");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
    }

    // @regression
    @Test
    void aMissingBodyIsMalformed() throws Exception {
        expectError(mockMvc.perform(withKey(post("/api/samples")).contentType(MediaType.APPLICATION_JSON)),
                400, "Bad Request", "Malformed request body.", "/api/samples");
    }

    // ---- verbs and access key -----------------------------------------------------------------

    // @regression
    @Test
    void deleteIsNotAllowedAndTheAllowHeaderListsGet() throws Exception {
        String id = idOf(register("PAT-1001", "BLOOD"));
        String path = "/api/samples/" + id;

        MvcResult result = mockMvc.perform(withKey(delete(path))).andReturn();

        expectError(result, 405, "Method Not Allowed", "Method not allowed.", path);
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("GET");
        mockMvc.perform(withKey(get(path)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    // @regression
    @Test
    void postWithoutAKeyIsUnauthorized() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/samples")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("PAT-1001", "BLOOD")));

        expectError(result, 401, "Unauthorized", "Unauthorized.", "/api/samples");
    }

    // @regression
    @Test
    void listWithoutAKeyIsUnauthorized() throws Exception {
        expectError(mockMvc.perform(get("/api/samples")), 401, "Unauthorized", "Unauthorized.", "/api/samples");
    }

    // @regression
    @Test
    void lookupWithoutAKeyIsUnauthorized() throws Exception {
        String path = "/api/samples/SMP-" + UUID.randomUUID();

        expectError(mockMvc.perform(get(path)), 401, "Unauthorized", "Unauthorized.", path);
    }

    // ---- the API and the screens show the same samples (functional-spec.md §2.8) --------------

    // @regression
    @Test
    void aSampleRegisteredThroughTheApiShowsUpOnTheSampleListScreen() throws Exception {
        String id = idOf(register("PAT-1001", "BLOOD"));
        MockHttpSession session = signIn();

        MvcResult page = mockMvc.perform(get("/samples").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String html = page.getResponse().getContentAsString();
        assertThat(html).contains(id).contains("ACCESSIONED");
        assertThat(rowFor(html, id)).contains("ACCESSIONED");
    }

    // @regression
    @Test
    void aSampleCreatedOnTheCreateSampleScreenIsReadableThroughTheApi() throws Exception {
        MockHttpSession session = signIn();

        MvcResult submitted = mockMvc.perform(post("/samples")
                        .session(session)
                        .param("patientId", "PAT-1001")
                        .param("sampleType", "BLOOD"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = submitted.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/samples/SMP-");
        String id = location.substring("/samples/".length());

        mockMvc.perform(withKey(get("/api/samples/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                .andExpect(jsonPath("$.orderId").value(org.hamcrest.Matchers.startsWith("ORD-")))
                .andExpect(jsonPath("$.sampleType").value("BLOOD"))
                .andExpect(jsonPath("$.status").value("ACCESSIONED"));

        MvcResult list = mockMvc.perform(withKey(get("/api/samples"))).andExpect(status().isOk()).andReturn();
        JSONArray mine = JsonPath.read(list.getResponse().getContentAsString(), "$[?(@.id == '" + id + "')]");
        assertThat(mine).hasSize(1);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private MvcResult register(String patientId, String sampleType) throws Exception {
        return mockMvc.perform(withKey(post("/api/samples"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(patientId, sampleType)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** A keyed {@code POST /api/samples} with the given raw JSON body, ready to perform. */
    private static MockHttpServletRequestBuilder keyedPost(String body) {
        return withKey(post("/api/samples"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void expectError(MockHttpServletRequestBuilder request,
                             int status, String error, String message) throws Exception {
        expectError(mockMvc.perform(request), status, error, message, "/api/samples");
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

    /** The table row of the Sample List page that mentions the given sample ID. */
    private static String rowFor(String html, String sampleId) {
        int idAt = html.indexOf(sampleId);
        int rowStart = html.lastIndexOf("<tr", idAt);
        int rowEnd = html.indexOf("</tr>", idAt);
        return html.substring(rowStart, rowEnd);
    }

    private static String json(String patientId, String sampleType) {
        return "{\"patientId\": \"" + patientId.replace("\t", "\\t")
                + "\", \"sampleType\": \"" + sampleType.replace("\t", "\\t") + "\"}";
    }

    private static String idOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
