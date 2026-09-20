package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.jayway.jsonpath.JsonPath;
import com.mocklab.domain.Order;
import com.mocklab.repository.OrderRepository;
import com.mocklab.service.ProcessingJobService;
import com.mocklab.service.SampleCreationResult;
import com.mocklab.service.SampleService;
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

import java.time.Instant;
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
 * Acceptance tests for {@code POST /api/orders} and {@code GET /api/orders/{id}}
 * (functional-spec.md §2.3). Every scenario runs over MockMvc against the real
 * application context; each test uses its own UUID-based data.
 *
 * <p>The order repository is swapped for a subclass that also remembers what
 * was saved, only so the "nothing created" checks can see whether an order was
 * ever stored (the repository has no list or count operation). It still stores
 * and finds orders exactly like the real one.
 *
 * <p>There is no sample API yet, so the "an order lists its samples" checks
 * register the sample through {@link SampleService} directly.
 */
class OrderApiAcceptanceTest extends AbstractApiAcceptanceTest {

    @Autowired
    RecordingOrderRepository orderRepository;

    @Autowired
    SampleService sampleService;

    @Autowired
    ProcessingJobService processingJobService;

    @TestConfiguration
    static class RecordingRepositoryConfig {
        @Bean
        @Primary
        RecordingOrderRepository recordingOrderRepository() {
            return new RecordingOrderRepository();
        }
    }

    static class RecordingOrderRepository extends OrderRepository {
        private final List<Order> saved = new CopyOnWriteArrayList<>();

        @Override
        public Order save(Order order) {
            saved.add(order);
            return super.save(order);
        }

        List<Order> saved() {
            return saved;
        }
    }

    // ---- place and read back ------------------------------------------------------------------

    // @regression
    @Test
    void placingAnOrderReturns201WithLocationAndTheGeneratedId() throws Exception {
        MvcResult created = mockMvc.perform(withKey(post("/api/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("PAT-1001")))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("ORD-")))
                .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.samples").isArray())
                .andExpect(jsonPath("$.samples").isEmpty())
                .andReturn();

        String id = idOf(created);
        String createdAt = JsonPath.read(created.getResponse().getContentAsString(), "$.createdAt");
        assertThat(Instant.parse(createdAt)).isNotNull();
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/orders/" + id);

        mockMvc.perform(withKey(get("/api/orders/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andExpect(jsonPath("$.samples").isEmpty());
    }

    // @regression
    @Test
    void anOrderCanBePlacedForAPatientRegisteredThroughTheApi() throws Exception {
        MvcResult patient = mockMvc.perform(withKey(post("/api/patients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Order patient " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String patientId = idOf(patient);

        MvcResult created = mockMvc.perform(withKey(post("/api/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(patientId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andReturn();

        mockMvc.perform(withKey(get("/api/orders/" + idOf(created))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientId));
    }

    // @regression
    @Test
    void twoOrdersForTheSamePatientGetDifferentIds() throws Exception {
        MvcResult first = placeOrder("PAT-1001");
        MvcResult second = placeOrder("PAT-1001");

        assertThat(idOf(first)).isNotEqualTo(idOf(second));
        mockMvc.perform(withKey(get("/api/orders/" + idOf(first))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idOf(first)));
        mockMvc.perform(withKey(get("/api/orders/" + idOf(second))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idOf(second)));
    }

    // @regression
    @Test
    void aClientSuppliedIdIsIgnoredBecauseTheLabAssignsIds() throws Exception {
        String clientId = "ORD-" + UUID.randomUUID();

        MvcResult created = mockMvc.perform(withKey(post("/api/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"" + clientId + "\", \"patientId\": \"PAT-1001\", \"extra\": true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                .andReturn();

        assertThat(idOf(created)).startsWith("ORD-").isNotEqualTo(clientId);
    }

    // ---- an order lists its samples -----------------------------------------------------------

    // @regression
    @Test
    void anOrderListsItsSamplesWithTheStatusFromTheProcessingJob() throws Exception {
        SampleCreationResult.Success registered =
                (SampleCreationResult.Success) sampleService.createSample("PAT-1001", "BLOOD");
        String orderPath = "/api/orders/" + registered.order().id();
        String sampleId = registered.sample().id();

        mockMvc.perform(withKey(get(orderPath)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registered.order().id()))
                .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                .andExpect(jsonPath("$.samples.length()").value(1))
                .andExpect(jsonPath("$.samples[0].id").value(sampleId))
                .andExpect(jsonPath("$.samples[0].sampleType").value("BLOOD"))
                .andExpect(jsonPath("$.samples[0].status").value("ACCESSIONED"));

        processingJobService.advance(sampleId);

        mockMvc.perform(withKey(get(orderPath)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples.length()").value(1))
                .andExpect(jsonPath("$.samples[0].id").value(sampleId))
                .andExpect(jsonPath("$.samples[0].status").value("READY_FOR_PROCESSING"));
    }

    // ---- validation ---------------------------------------------------------------------------

    // @regression
    @Test
    void aMissingPatientIdIsRejectedAndNothingIsCreated() throws Exception {
        ResultActions result = mockMvc.perform(withKey(post("/api/orders"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        expectError(result, 400, "Bad Request", "Patient is required.", "/api/orders");
        verifyNoOrderWithoutAPatientWasSaved();
    }

    // @regression
    @Test
    void aNullPatientIdIsRejectedAndNothingIsCreated() throws Exception {
        ResultActions result = mockMvc.perform(withKey(post("/api/orders"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\": null}"));

        expectError(result, 400, "Bad Request", "Patient is required.", "/api/orders");
        verifyNoOrderWithoutAPatientWasSaved();
    }

    // @regression
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   \t  "})
    void aBlankPatientIdIsRejectedAndNothingIsCreated(String blankId) throws Exception {
        ResultActions result = mockMvc.perform(withKey(post("/api/orders"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(blankId)));

        expectError(result, 400, "Bad Request", "Patient is required.", "/api/orders");
        verifyNoOrderWithoutAPatientWasSaved();
    }

    // ---- not found ----------------------------------------------------------------------------

    // @regression
    @Test
    void anUnknownPatientIsNotFoundAndNoOrderIsCreated() throws Exception {
        String unknown = "PAT-" + UUID.randomUUID();

        ResultActions result = mockMvc.perform(withKey(post("/api/orders"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(unknown)));

        expectError(result, 404, "Not Found", "Patient not found.", "/api/orders");
        assertThat(orderRepository.saved()).noneMatch(o -> unknown.equals(o.patientId()));
    }

    // @regression
    @Test
    void anUnknownOrderIdIsNotFound() throws Exception {
        String path = "/api/orders/ORD-" + UUID.randomUUID();

        expectError(mockMvc.perform(withKey(get(path))), 404, "Not Found", "Order not found.", path);
    }

    // ---- malformed bodies ---------------------------------------------------------------------

    // @regression
    @Test
    void brokenJsonIsMalformedAndNeverEchoesThePayload() throws Exception {
        String secret = "Secret-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(withKey(post("/api/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"" + secret + "\", "))
                .andReturn();

        expectError(result, 400, "Bad Request", "Malformed request body.", "/api/orders");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
    }

    // @regression
    @Test
    void aJsonArrayInsteadOfAnObjectIsMalformed() throws Exception {
        String secret = "Secret-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(withKey(post("/api/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + secret + "\"]"))
                .andReturn();

        expectError(result, 400, "Bad Request", "Malformed request body.", "/api/orders");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
    }

    // @regression
    @Test
    void aMissingBodyIsMalformed() throws Exception {
        expectError(mockMvc.perform(withKey(post("/api/orders")).contentType(MediaType.APPLICATION_JSON)),
                400, "Bad Request", "Malformed request body.", "/api/orders");
    }

    // @regression
    @Test
    void aNumericPatientIdIsCoercedToAStringSoItIsAnUnknownPatient() throws Exception {
        // Observed behaviour: Jackson's default scalar coercion turns the JSON number 123 into the
        // string "123", so the request is well-formed (not "Malformed request body.") and simply
        // names a patient that does not exist. The mapper is left lenient on purpose.
        ResultActions result = mockMvc.perform(withKey(post("/api/orders"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\": 123}"));

        expectError(result, 404, "Not Found", "Patient not found.", "/api/orders");
        assertThat(orderRepository.saved()).noneMatch(o -> "123".equals(o.patientId()));
    }

    // ---- verbs and access key -----------------------------------------------------------------

    // @regression
    @Test
    void deleteIsNotAllowedAndTheAllowHeaderListsGet() throws Exception {
        String id = idOf(placeOrder("PAT-1001"));
        String path = "/api/orders/" + id;

        MvcResult result = mockMvc.perform(withKey(delete(path))).andReturn();

        expectError(result, 405, "Method Not Allowed", "Method not allowed.", path);
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("GET");
        mockMvc.perform(withKey(get(path)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    // @regression
    @Test
    void postWithoutAKeyIsUnauthorizedAndNothingIsCreated() throws Exception {
        String patientId = idOf(mockMvc.perform(withKey(post("/api/patients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"No key " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());

        ResultActions result = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(patientId)));

        expectError(result, 401, "Unauthorized", "Unauthorized.", "/api/orders");
        assertThat(orderRepository.saved()).noneMatch(o -> patientId.equals(o.patientId()));
    }

    // @regression
    @Test
    void getWithoutAKeyIsUnauthorized() throws Exception {
        String path = "/api/orders/ORD-" + UUID.randomUUID();

        expectError(mockMvc.perform(get(path)), 401, "Unauthorized", "Unauthorized.", path);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private MvcResult placeOrder(String patientId) throws Exception {
        return mockMvc.perform(withKey(post("/api/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(patientId)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private void verifyNoOrderWithoutAPatientWasSaved() {
        assertThat(orderRepository.saved())
                .noneMatch(o -> o.patientId() == null || o.patientId().isBlank());
    }

    private static String json(String patientId) {
        return "{\"patientId\": \"" + patientId.replace("\t", "\\t") + "\"}";
    }

    private static String idOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
