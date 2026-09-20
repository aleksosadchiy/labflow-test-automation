package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for the whole-feature ("Spec2_x") acceptance tests. They sit on top of
 * {@link AbstractApiAcceptanceTest} (real Spring context per class, MockMvc, {@code withKey},
 * {@code expectError}) and only add what several of these classes need: signing in on the
 * screens, registering and moving samples, and counting what a repository holds.
 *
 * <p>Same rules as the base class: one shared context per class, UUID-based data, and no
 * test may assume an empty lab or a particular test order.
 */
abstract class AbstractSpecAcceptanceTest extends AbstractApiAcceptanceTest {

    /** The stages in the order the lab moves through them, by wire name (independent of the production enum). */
    static final List<String> STAGES = List.of(
            "ACCESSIONED", "READY_FOR_PROCESSING", "PROCESSING", "SEQUENCING",
            "BIOINFORMATICS", "QUALITY_CHECK", "RESULT_READY", "REPORT_GENERATED");

    static final String SEEDED_USERNAME = "LabTech";
    static final String SEEDED_PASSWORD = "I_Like_This_Lab";

    /** Signs in through the real {@code POST /login} route and returns the session that carries the login. */
    MockHttpSession signIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", SEEDED_USERNAME)
                        .param("password", SEEDED_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** A keyed {@code POST} with a JSON body. */
    static MockHttpServletRequestBuilder keyedPost(String path, String jsonBody) {
        return withKey(post(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody);
    }

    /** A keyed {@code PUT /api/samples/{id}/status} asking for {@code stage}. */
    static MockHttpServletRequestBuilder putStatus(String sampleId, String stage) {
        return withKey(put("/api/samples/" + sampleId + "/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + stage + "\"}");
    }

    /** Registers a sample through the API (expects 201) and returns the response body. */
    String registerSample(String patientId, String sampleType) throws Exception {
        return registerSample(patientId, sampleType, null);
    }

    /** As above, attached to {@code orderId} when it is not {@code null}. */
    String registerSample(String patientId, String sampleType, String orderId) throws Exception {
        String orderPart = orderId == null ? "" : ", \"orderId\": \"" + orderId + "\"";
        return mockMvc.perform(keyedPost("/api/samples",
                        "{\"patientId\": \"" + patientId + "\", \"sampleType\": \"" + sampleType + "\"" + orderPart + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Moves the sample forward, one accepted PUT at a time, until it reaches {@code stage}. */
    void walkTo(String sampleId, String stage) throws Exception {
        for (int i = 1; i <= STAGES.indexOf(stage); i++) {
            mockMvc.perform(putStatus(sampleId, STAGES.get(i))).andExpect(status().isOk());
        }
    }

    /** A keyed {@code GET} that must answer 200; returns the JSON body. */
    String getOk(String path) throws Exception {
        return mockMvc.perform(withKey(get(path)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    static String read(String json, String path) {
        return JsonPath.read(json, path);
    }

    /** The table row of the Sample List page that mentions the given sample ID. */
    static String rowFor(String html, String sampleId) {
        int idAt = html.indexOf(sampleId);
        assertThat(idAt).as("sample %s on the Sample List screen", sampleId).isNotNegative();
        int rowStart = html.lastIndexOf("<tr", idAt);
        int rowEnd = html.indexOf("</tr>", idAt);
        return html.substring(rowStart, rowEnd);
    }

    /**
     * How many records an in-memory repository holds, counted over every {@code Map} field it
     * has. The repositories have no count operation (the lab has no list-all for patients,
     * orders, jobs, ...), and adding one to production code only for the tests is not worth it.
     */
    static int storedRecords(Object repository) {
        int total = 0;
        for (Field field : repository.getClass().getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                total += ((Map<?, ?>) ReflectionTestUtils.getField(repository, field.getName())).size();
            }
        }
        return total;
    }
}
