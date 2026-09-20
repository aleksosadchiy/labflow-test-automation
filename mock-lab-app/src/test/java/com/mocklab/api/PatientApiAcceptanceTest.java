package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.mocklab.domain.Patient;
import com.mocklab.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for {@code POST /api/patients} and {@code GET /api/patients/{id}}
 * (functional-spec.md §2.2). Every scenario runs over MockMvc against the real
 * application context; each test uses its own UUID-based data.
 *
 * <p>The patient repository is swapped for a subclass that also remembers what
 * was saved, only so the "nothing created" checks can see whether an unnamed
 * patient was ever stored (the repository has no list or count operation). It
 * still stores and finds patients exactly like the real one.
 */
class PatientApiAcceptanceTest extends AbstractApiAcceptanceTest {

    @Autowired
    RecordingPatientRepository patientRepository;

    @TestConfiguration
    static class RecordingRepositoryConfig {
        @Bean
        @Primary
        RecordingPatientRepository recordingPatientRepository() {
            return new RecordingPatientRepository();
        }
    }

    static class RecordingPatientRepository extends PatientRepository {
        private final List<Patient> saved = new CopyOnWriteArrayList<>();

        @Override
        public Patient save(Patient patient) {
            saved.add(patient);
            return super.save(patient);
        }

        List<Patient> saved() {
            return saved;
        }
    }

    // ---- create and read back -----------------------------------------------------------------

    // @regression
    @Test
    void registeringAPatientReturns201WithLocationAndTheGeneratedId() throws Exception {
        String name = "Patient " + UUID.randomUUID();

        MvcResult created = mockMvc.perform(withKey(post("/api/patients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(name)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("PAT-")))
                .andExpect(jsonPath("$.name").value(name))
                .andReturn();

        String id = idOf(created);
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/patients/" + id);

        mockMvc.perform(withKey(get("/api/patients/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name));
    }

    // @regression
    @Test
    void theNameIsTrimmedBeforeItIsStored() throws Exception {
        String name = "Trim " + UUID.randomUUID();

        MvcResult created = mockMvc.perform(withKey(post("/api/patients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("   " + name + "  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn();

        mockMvc.perform(withKey(get("/api/patients/" + idOf(created))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
    }

    // @regression
    @Test
    void theSeededPatientCanBeLookedUp() throws Exception {
        mockMvc.perform(withKey(get("/api/patients/PAT-1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("PAT-1001"))
                .andExpect(jsonPath("$.name").value("Alice Johnson"));
    }

    // @regression
    @Test
    void aClientSuppliedIdIsIgnoredBecauseTheLabAssignsIds() throws Exception {
        String name = "Ignore id " + UUID.randomUUID();

        MvcResult created = mockMvc.perform(withKey(post("/api/patients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"PAT-1001\", \"name\": \"" + name + "\", \"extra\": true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn();

        assertThat(idOf(created)).startsWith("PAT-").isNotEqualTo("PAT-1001");

        mockMvc.perform(withKey(get("/api/patients/PAT-1001")))
                .andExpect(jsonPath("$.name").value("Alice Johnson"));
    }

    // @regression
    @Test
    void duplicateNamesAreAllowedAndGetDifferentIds() throws Exception {
        String name = "Twin " + UUID.randomUUID();

        MvcResult first = register(name);
        MvcResult second = register(name);

        assertThat(idOf(first)).isNotEqualTo(idOf(second));
        mockMvc.perform(withKey(get("/api/patients/" + idOf(first))))
                .andExpect(jsonPath("$.name").value(name));
        mockMvc.perform(withKey(get("/api/patients/" + idOf(second))))
                .andExpect(jsonPath("$.name").value(name));
    }

    // ---- validation ---------------------------------------------------------------------------

    // @regression
    @Test
    void aMissingNameIsRejectedAndNothingIsCreated() throws Exception {
        ResultActions result = mockMvc.perform(withKey(post("/api/patients"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        expectError(result, 400, "Bad Request", "Patient name is required.", "/api/patients");
        verifyNoUnnamedPatientWasSaved();
    }

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   \t  "})
    void aBlankNameIsRejectedAndNothingIsCreated(String blankName) throws Exception {
        ResultActions result = mockMvc.perform(withKey(post("/api/patients"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(blankName)));

        expectError(result, 400, "Bad Request", "Patient name is required.", "/api/patients");
        verifyNoUnnamedPatientWasSaved();
    }

    // @regression
    @Test
    void aNullNameIsRejectedAndNothingIsCreated() throws Exception {
        ResultActions result = mockMvc.perform(withKey(post("/api/patients"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": null}"));

        expectError(result, 400, "Bad Request", "Patient name is required.", "/api/patients");
        verifyNoUnnamedPatientWasSaved();
    }

    // ---- not found ----------------------------------------------------------------------------

    // @regression
    @Test
    void anUnknownIdIsNotFound() throws Exception {
        String path = "/api/patients/PAT-" + UUID.randomUUID();

        expectError(mockMvc.perform(withKey(get(path))), 404, "Not Found", "Patient not found.", path);
    }

    // ---- malformed bodies ---------------------------------------------------------------------

    // @regression
    @Test
    void brokenJsonIsMalformedAndNeverEchoesThePayload() throws Exception {
        String secret = "Secret-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(withKey(post("/api/patients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + secret + "\", "))
                .andReturn();

        expectError(result, 400, "Bad Request", "Malformed request body.", "/api/patients");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
    }

    // @regression
    @Test
    void aJsonArrayInsteadOfAnObjectIsMalformed() throws Exception {
        String secret = "Secret-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(withKey(post("/api/patients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + secret + "\"]"))
                .andReturn();

        expectError(result, 400, "Bad Request", "Malformed request body.", "/api/patients");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
    }

    // @regression
    @Test
    void aMissingBodyIsMalformed() throws Exception {
        expectError(mockMvc.perform(withKey(post("/api/patients")).contentType(MediaType.APPLICATION_JSON)),
                400, "Bad Request", "Malformed request body.", "/api/patients");
    }

    // ---- verbs and access key -----------------------------------------------------------------

    // @regression
    @Test
    void deleteIsNotAllowedAndTheAllowHeaderListsGet() throws Exception {
        MvcResult result = mockMvc.perform(withKey(delete("/api/patients/PAT-1001"))).andReturn();

        expectError(result, 405, "Method Not Allowed", "Method not allowed.", "/api/patients/PAT-1001");
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("GET");
        mockMvc.perform(withKey(get("/api/patients/PAT-1001")))
                .andExpect(jsonPath("$.name").value("Alice Johnson"));
    }

    // @regression
    @Test
    void postWithoutAKeyIsUnauthorizedAndNothingIsCreated() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("No key " + UUID.randomUUID())));

        expectError(result, 401, "Unauthorized", "Unauthorized.", "/api/patients");
        assertThat(patientRepository.saved()).noneMatch(p -> p.name().startsWith("No key "));
    }

    // @regression
    @Test
    void getWithoutAKeyIsUnauthorized() throws Exception {
        expectError(mockMvc.perform(get("/api/patients/PAT-1001")),
                401, "Unauthorized", "Unauthorized.", "/api/patients/PAT-1001");
    }

    // ---- helpers ------------------------------------------------------------------------------

    private MvcResult register(String name) throws Exception {
        return mockMvc.perform(withKey(post("/api/patients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(name)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private void verifyNoUnnamedPatientWasSaved() {
        assertThat(patientRepository.saved()).noneMatch(this::hasNoName);
    }

    private boolean hasNoName(Patient patient) {
        return patient.name() == null || patient.name().isBlank();
    }

    private static String json(String name) {
        return "{\"name\": \"" + name.replace("\t", "\\t") + "\"}";
    }

    private static String idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }
}
