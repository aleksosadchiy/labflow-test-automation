package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.mocklab.repository.OrderRepository;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.SampleRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature-level proof of functional-spec.md section 2.1 (Access Control), including the
 * "auth isolation both ways" rule of technical-considerations.md section 4. Everything runs
 * through the real Spring context (real filter registration, real interceptor), not a
 * hand-built filter chain.
 *
 * <h2>Criteria to tests</h2>
 * <ol>
 *   <li><b>Correct key: any read or change is accepted and processed.</b>
 *       Every API acceptance test sends the documented default key and gets its 2xx, e.g.
 *       {@code PatientApiAcceptanceTest#registeringAPatientReturns201WithLocationAndTheGeneratedId},
 *       {@code OrderApiAcceptanceTest#placingAnOrderReturns201WithLocationAndTheGeneratedId},
 *       {@code SampleApiAcceptanceTest#registeringABloodSampleReturns201WithLocationAndTheNewSampleAndItsOrder},
 *       {@code SampleApiAcceptanceTest#aRegisteredSampleAppearsInTheSampleList},
 *       {@code SampleStatusApiAcceptanceTest#aSampleCanBeWalkedThroughEveryStageOneAcceptedPutAtATime},
 *       {@code SampleReportApiAcceptanceTest#atReportGeneratedTheReportIsAvailableAndTheResultAndFullProgressAreToo};
 *       unit level {@code ApiKeyFilterTest#correctKeyContinuesTheChain}.</li>
 *   <li><b>No key: 401 "Unauthorized."</b>
 *       {@code PatientApiAcceptanceTest#postWithoutAKeyIsUnauthorizedAndNothingIsCreated},
 *       {@code PatientApiAcceptanceTest#getWithoutAKeyIsUnauthorized},
 *       {@code OrderApiAcceptanceTest#postWithoutAKeyIsUnauthorizedAndNothingIsCreated},
 *       {@code OrderApiAcceptanceTest#getWithoutAKeyIsUnauthorized},
 *       {@code SampleApiAcceptanceTest#postWithoutAKeyIsUnauthorized},
 *       {@code SampleApiAcceptanceTest#listWithoutAKeyIsUnauthorized},
 *       {@code SampleOrderApiAcceptanceTest#postingWithAnOrderIdButNoKeyIsUnauthorizedAndNothingIsAttached},
 *       {@code SampleStatusApiAcceptanceTest#aPutWithoutAKeyIsUnauthorizedAndTheSampleStaysPut},
 *       {@code SampleReportApiAcceptanceTest#aRequestWithoutAKeyIsUnauthorized},
 *       {@code ApiKeyRegistrationScopeIntegrationTest#apiRouteWithoutKeyIsUnauthorizedJson}.</li>
 *   <li><b>Incorrect key: 401 "Unauthorized."</b> Only proven with a hand-built filter chain
 *       before ({@code ApiKeyFilterTest#wrongKeyIsUnauthorized}); through the real context it is
 *       proven here by {@link #anIncorrectKeyIsUnauthorizedOnEveryKindOfRoute},
 *       {@link #aKeyThatIsNearlyRightOrInTheWrongFormIsUnauthorized} and
 *       {@link #aRequestWithAnIncorrectKeyChangesNothing}.</li>
 *   <li><b>No key and the record does not exist: still 401, not "not found".</b>
 *       {@code SampleApiAcceptanceTest#lookupWithoutAKeyIsUnauthorized},
 *       {@code OrderApiAcceptanceTest#getWithoutAKeyIsUnauthorized},
 *       {@code ApiErrorMappingIntegrationTest#withoutAKeyEveryRejectionIsUnauthorized}; with an
 *       incorrect key the same ordering is proven here (the unknown-record rows of
 *       {@link #anIncorrectKeyIsUnauthorizedOnEveryKindOfRoute}).</li>
 * </ol>
 * The two auth mechanisms must not bleed into each other (technical-considerations.md section 3):
 * {@link #aSignedInScreenSessionDoesNotOpenTheApi} and {@link #theApiKeyDoesNotOpenTheScreens}.
 * The screen-side counterparts are {@code ApiErrorMappingIntegrationTest#screensStillRedirectUnauthenticatedUsersToLogin}
 * and {@code ApiErrorMappingIntegrationTest#validKeyOnAnApiRouteIsNeverRedirectedToLogin}.
 */
class Spec2_1AccessControlAcceptanceTest extends AbstractSpecAcceptanceTest {

    private static final String WRONG_KEY = "not-the-key-" + UUID.randomUUID();

    @Autowired
    Environment environment;

    @Autowired
    PatientRepository patientRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    SampleRepository sampleRepository;

    // ---- the documented default key -----------------------------------------------------------

    // @regression
    @Test
    void theDocumentedLocalDefaultKeyIsAcceptedWhenNoKeyIsChosen() throws Exception {
        // The default only applies while API_TOKEN is not set for this JVM (functional-spec.md 2.9).
        Assumptions.assumeTrue(System.getenv("API_TOKEN") == null, "API_TOKEN is set, so a key has been chosen");

        assertThat(environment.getProperty("mock-lab.api.token")).isEqualTo("mock-lab-local-token");
        mockMvc.perform(withKey(get("/api/patients/PAT-1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("PAT-1001"));
    }

    // ---- an incorrect key --------------------------------------------------------------------

    // @regression
    @ParameterizedTest(name = "{0} {1} with an incorrect key")
    @MethodSource("everyKindOfRoute")
    void anIncorrectKeyIsUnauthorizedOnEveryKindOfRoute(String method, String path, String body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + WRONG_KEY);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        MvcResult result = mockMvc.perform(request).andReturn();

        expectError(result, 401, "Unauthorized", "Unauthorized.", path);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(WRONG_KEY);
    }

    /**
     * Every route of the API, plus the cases where a wrong key must still win over "not found"
     * (records that do not exist), "resource not found" (unknown route) and "method not allowed".
     */
    static Stream<Arguments> everyKindOfRoute() {
        String unknown = UUID.randomUUID().toString();
        return Stream.of(
                Arguments.of("POST", "/api/patients", "{\"name\": \"Wrong key\"}"),
                Arguments.of("GET", "/api/patients/PAT-1001", null),
                Arguments.of("GET", "/api/patients/PAT-" + unknown, null),
                Arguments.of("POST", "/api/orders", "{\"patientId\": \"PAT-1001\"}"),
                Arguments.of("GET", "/api/orders/ORD-" + unknown, null),
                Arguments.of("POST", "/api/samples", "{\"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\"}"),
                Arguments.of("GET", "/api/samples", null),
                Arguments.of("GET", "/api/samples/SMP-" + unknown, null),
                Arguments.of("PUT", "/api/samples/SMP-" + unknown + "/status", "{\"status\": \"READY_FOR_PROCESSING\"}"),
                Arguments.of("GET", "/api/samples/SMP-" + unknown + "/progress", null),
                Arguments.of("GET", "/api/samples/SMP-" + unknown + "/result", null),
                Arguments.of("GET", "/api/samples/SMP-" + unknown + "/report", null),
                Arguments.of("GET", "/api/does-not-exist", null),
                Arguments.of("DELETE", "/api/samples/SMP-" + unknown, null));
    }

    // @regression
    @ParameterizedTest(name = "Authorization: {0}")
    @ValueSource(strings = {
            "Bearer mock-lab-local-toke",
            "Bearer mock-lab-local-token-extra",
            "Bearer MOCK-LAB-LOCAL-TOKEN",
            "Bearer ",
            "Basic bW9jay1sYWItbG9jYWwtdG9rZW4=",
            "mock-lab-local-token"})
    void aKeyThatIsNearlyRightOrInTheWrongFormIsUnauthorized(String authorizationHeader) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/patients/PAT-1001")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andReturn();

        expectError(result, 401, "Unauthorized", "Unauthorized.", "/api/patients/PAT-1001");
    }

    // @regression
    @Test
    void aRequestWithAnIncorrectKeyChangesNothing() throws Exception {
        String sampleId = read(registerSample("PAT-1001", "BLOOD"), "$.id");
        int patients = storedRecords(patientRepository);
        int orders = storedRecords(orderRepository);
        int samples = sampleRepository.findAll().size();
        String wrong = "Bearer " + WRONG_KEY;

        mockMvc.perform(post("/api/patients").header(HttpHeaders.AUTHORIZATION, wrong)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Wrong key\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, wrong)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"patientId\": \"PAT-1001\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/samples").header(HttpHeaders.AUTHORIZATION, wrong)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"PAT-1001\", \"sampleType\": \"TISSUE\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.put("/api/samples/" + sampleId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, wrong)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"READY_FOR_PROCESSING\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(storedRecords(patientRepository)).isEqualTo(patients);
        assertThat(storedRecords(orderRepository)).isEqualTo(orders);
        assertThat(sampleRepository.findAll()).hasSize(samples);
        mockMvc.perform(withKey(get("/api/samples/" + sampleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCESSIONED"));
    }

    // ---- the two ways of signing in stay apart ------------------------------------------------

    // @regression
    @Test
    void aSignedInScreenSessionDoesNotOpenTheApi() throws Exception {
        MockHttpSession session = signIn();

        MvcResult result = mockMvc.perform(get("/api/samples").session(session)).andReturn();

        expectError(result, 401, "Unauthorized", "Unauthorized.", "/api/samples");
        // ... and the same session still works on the screens, so it really was a valid login.
        mockMvc.perform(get("/samples").session(session)).andExpect(status().isOk());
    }

    // @regression
    @Test
    void theApiKeyDoesNotOpenTheScreens() throws Exception {
        String sampleId = read(registerSample("PAT-1001", "BLOOD"), "$.id");

        mockMvc.perform(withKey(get("/samples")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(withKey(get("/samples/" + sampleId)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(withKey(post("/samples/" + sampleId + "/advance")))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/login"));
        // The rejected screen call did not move the sample.
        mockMvc.perform(withKey(get("/api/samples/" + sampleId)))
                .andExpect(jsonPath("$.status").value("ACCESSIONED"));
    }
}
