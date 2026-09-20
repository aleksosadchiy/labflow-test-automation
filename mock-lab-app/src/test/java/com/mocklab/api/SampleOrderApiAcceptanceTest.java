package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.jayway.jsonpath.JsonPath;
import com.mocklab.repository.SampleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for registering a sample under an existing order through
 * {@code POST /api/samples} with the optional {@code orderId}
 * (functional-spec.md §2.4 and §2.8): the order rules, the precedence of the
 * failures, the screens staying unaffected, and simultaneous requests.
 *
 * <p>Every test places its own order first and only ever looks at that order,
 * so the shared application context (and the other classes' samples in it)
 * never interferes. Tests are run one after another; the "nothing was created"
 * checks compare the total number of samples before and after a rejected call,
 * which is only sound because no other test runs at the same time.
 */
class SampleOrderApiAcceptanceTest extends AbstractApiAcceptanceTest {

    private static final String DUPLICATE_MESSAGE = "A sample of this type already exists for this order.";
    private static final String MISMATCH_MESSAGE = "Order does not belong to this patient.";
    private static final String SEEDED_USERNAME = "LabTech";
    private static final String SEEDED_PASSWORD = "I_Like_This_Lab";

    @Autowired
    SampleRepository sampleRepository;

    // ---- attaching samples to an existing order -----------------------------------------------

    // @regression
    @Test
    void aSampleNamingAnExistingOrderIsRegisteredUnderThatOrder() throws Exception {
        String orderId = placeOrder("PAT-1001");

        MvcResult created = mockMvc.perform(withKey(post("/api/samples"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("PAT-1001", "BLOOD", orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                .andExpect(jsonPath("$.sampleType").value("BLOOD"))
                .andExpect(jsonPath("$.status").value("ACCESSIONED"))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.order.id").value(orderId))
                .andExpect(jsonPath("$.order.patientId").value("PAT-1001"))
                .andReturn();

        String sampleId = idOf(created);
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith(sampleId);
        mockMvc.perform(withKey(get("/api/orders/" + orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples.length()").value(1))
                .andExpect(jsonPath("$.samples[0].id").value(sampleId))
                .andExpect(jsonPath("$.samples[0].sampleType").value("BLOOD"))
                .andExpect(jsonPath("$.samples[0].status").value("ACCESSIONED"));
    }

    // @regression
    @Test
    void aSecondSampleOfAnotherTypeCanJoinTheSameOrder() throws Exception {
        String orderId = placeOrder("PAT-1001");
        String bloodId = idOf(register("PAT-1001", "BLOOD", orderId));

        MvcResult swab = mockMvc.perform(withKey(post("/api/samples"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("PAT-1001", "SWAB", orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sampleType").value("SWAB"))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.order.id").value(orderId))
                .andReturn();
        String swabId = idOf(swab);

        assertThat(swabId).isNotEqualTo(bloodId);
        mockMvc.perform(withKey(get("/api/orders/" + orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples.length()").value(2))
                .andExpect(jsonPath("$.samples[?(@.id == '" + bloodId + "')].sampleType").value("BLOOD"))
                .andExpect(jsonPath("$.samples[?(@.id == '" + swabId + "')].sampleType").value("SWAB"));
    }

    // ---- duplicate type on the same order (409) -----------------------------------------------

    // @regression
    @Test
    void aSecondSampleOfTheSameTypeOnTheSameOrderIsRejected() throws Exception {
        String orderId = placeOrder("PAT-1001");
        String bloodId = idOf(register("PAT-1001", "BLOOD", orderId));
        String swabId = idOf(register("PAT-1001", "SWAB", orderId));
        int samplesBefore = sampleRepository.findAll().size();

        expectError(keyedPost(json("PAT-1001", "BLOOD", orderId)), 409, "Conflict", DUPLICATE_MESSAGE);

        assertThat(sampleRepository.findAll()).hasSize(samplesBefore);
        assertOrderListsExactly(orderId, bloodId, swabId);
    }

    // @regression
    @Test
    void theDuplicateTypeCheckIgnoresLetterCase() throws Exception {
        String orderId = placeOrder("PAT-1001");
        String bloodId = idOf(register("PAT-1001", "BLOOD", orderId));

        expectError(keyedPost(json("PAT-1001", "blood", orderId)), 409, "Conflict", DUPLICATE_MESSAGE);

        assertOrderListsExactly(orderId, bloodId);
    }

    // ---- order of another patient (409) and unknown order (404) -------------------------------

    // @regression
    @Test
    void anOrderBelongingToAnotherPatientIsRejectedAndNothingIsAdded() throws Exception {
        String orderId = placeOrder("PAT-1001");
        int samplesBefore = sampleRepository.findAll().size();

        expectError(keyedPost(json("PAT-1002", "BLOOD", orderId)), 409, "Conflict", MISMATCH_MESSAGE);

        assertThat(sampleRepository.findAll()).hasSize(samplesBefore);
        assertOrderListsExactly(orderId);
    }

    // @regression
    @Test
    void anUnknownOrderIsNotFoundAndNoSampleIsCreated() throws Exception {
        int samplesBefore = sampleRepository.findAll().size();

        expectError(keyedPost(json("PAT-1001", "BLOOD", "ORD-DOES-NOT-EXIST")),
                404, "Not Found", "Order not found.");

        assertThat(sampleRepository.findAll()).hasSize(samplesBefore);
        mockMvc.perform(withKey(get("/api/orders/ORD-DOES-NOT-EXIST")))
                .andExpect(status().isNotFound());
    }

    // ---- no order named ------------------------------------------------------------------------

    // @regression
    @Test
    void aNullOrEmptyOrBlankOrderIdEachCreateANewOrder() throws Exception {
        String placedOrderId = placeOrder("PAT-1001");
        List<String> bodies = List.of(
                "{\"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\", \"orderId\": null}",
                "{\"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\", \"orderId\": \"\"}",
                "{\"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\", \"orderId\": \"   \"}");

        Set<String> orderIds = new HashSet<>();
        for (String body : bodies) {
            MvcResult created = mockMvc.perform(keyedPost(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").value(org.hamcrest.Matchers.startsWith("ORD-")))
                    .andReturn();
            String newOrderId = JsonPath.read(created.getResponse().getContentAsString(), "$.orderId");
            assertThat((String) JsonPath.read(created.getResponse().getContentAsString(), "$.order.id"))
                    .isEqualTo(newOrderId);
            orderIds.add(newOrderId);
        }

        assertThat(orderIds).hasSize(3).doesNotContain(placedOrderId);
        assertOrderListsExactly(placedOrderId);
    }

    // @regression
    @Test
    void omittingTheOrderIdEntirelyStillCreatesANewOrder() throws Exception {
        MvcResult first = mockMvc.perform(keyedPost("{\"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(org.hamcrest.Matchers.startsWith("ORD-")))
                .andExpect(jsonPath("$.order.id").value(org.hamcrest.Matchers.startsWith("ORD-")))
                .andReturn();
        MvcResult second = mockMvc.perform(keyedPost("{\"patientId\": \"PAT-1001\", \"sampleType\": \"BLOOD\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String firstOrderId = JsonPath.read(first.getResponse().getContentAsString(), "$.orderId");
        String secondOrderId = JsonPath.read(second.getResponse().getContentAsString(), "$.orderId");
        assertThat(firstOrderId).isNotEqualTo(secondOrderId);
        assertOrderListsExactly(firstOrderId, idOf(first));
    }

    // ---- which failure wins when several apply -------------------------------------------------

    // @regression
    @Test
    void anUnknownPatientBeatsAnUnknownOrder() throws Exception {
        expectError(keyedPost(json("PAT-" + UUID.randomUUID(), "BLOOD", "ORD-DOES-NOT-EXIST")),
                404, "Not Found", "Patient not found.");
    }

    // @regression
    @Test
    void anInvalidSampleTypeBeatsAnUnknownOrder() throws Exception {
        expectError(keyedPost(json("PAT-1001", "PLASMA", "ORD-DOES-NOT-EXIST")),
                400, "Bad Request", "Invalid sample type.");
    }

    // @regression
    @Test
    void aBlankPatientBeatsAnUnknownOrder() throws Exception {
        expectError(keyedPost(json("", "BLOOD", "ORD-DOES-NOT-EXIST")),
                400, "Bad Request", "Patient is required.");
    }

    // @regression
    @Test
    void anOrderOfAnotherPatientIsReportedBeforeADuplicateType() throws Exception {
        String orderId = placeOrder("PAT-1001");
        String bloodId = idOf(register("PAT-1001", "BLOOD", orderId));

        expectError(keyedPost(json("PAT-1002", "BLOOD", orderId)), 409, "Conflict", MISMATCH_MESSAGE);

        assertOrderListsExactly(orderId, bloodId);
    }

    // ---- the screens are unaffected (functional-spec.md §2.8) ----------------------------------

    // @regression
    @Test
    void everyCreateSampleScreenSubmissionStillGetsItsOwnNewOrder() throws Exception {
        MockHttpSession session = signIn();

        String firstId = submitScreenForm(session, "PAT-1001", "BLOOD");
        String secondId = submitScreenForm(session, "PAT-1001", "BLOOD");

        assertThat(firstId).isNotEqualTo(secondId);
        String firstOrderId = orderIdOfSample(firstId);
        String secondOrderId = orderIdOfSample(secondId);
        assertThat(firstOrderId).startsWith("ORD-");
        assertThat(secondOrderId).startsWith("ORD-").isNotEqualTo(firstOrderId);
    }

    // ---- simultaneous requests (functional-spec.md §2.8) ---------------------------------------

    // @regression
    @Test
    void tenSimultaneousRequestsForTheSameOrderAndTypeCreateExactlyOneSample() throws Exception {
        int requests = 10;
        String orderId = placeOrder("PAT-1001");
        String body = json("PAT-1001", "BLOOD", orderId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        List<MvcResult> results = new ArrayList<>();
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(keyedPost(body)).andReturn();
                }));
            }
            start.countDown();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        List<MvcResult> created = results.stream().filter(r -> r.getResponse().getStatus() == 201).toList();
        List<MvcResult> rejected = results.stream().filter(r -> r.getResponse().getStatus() == 409).toList();
        assertThat(created).hasSize(1);
        assertThat(rejected).hasSize(requests - 1);
        for (MvcResult result : rejected) {
            expectError(result, 409, "Conflict", DUPLICATE_MESSAGE, "/api/samples");
        }
        assertOrderListsExactly(orderId, idOf(created.get(0)));
    }

    // ---- access key ----------------------------------------------------------------------------

    // @regression
    @Test
    void postingWithAnOrderIdButNoKeyIsUnauthorizedAndNothingIsAttached() throws Exception {
        String orderId = placeOrder("PAT-1001");
        int samplesBefore = sampleRepository.findAll().size();

        expectError(mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("PAT-1001", "BLOOD", orderId))),
                401, "Unauthorized", "Unauthorized.", "/api/samples");

        assertThat(sampleRepository.findAll()).hasSize(samplesBefore);
        assertOrderListsExactly(orderId);
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** Places an order for the patient through the API and returns its ID. */
    private String placeOrder(String patientId) throws Exception {
        MvcResult placed = mockMvc.perform(withKey(post("/api/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"" + patientId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(placed);
    }

    /** Registers a sample naming the order and expects it to succeed. */
    private MvcResult register(String patientId, String sampleType, String orderId) throws Exception {
        return mockMvc.perform(keyedPost(json(patientId, sampleType, orderId)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** Asserts the order lists exactly the given sample IDs, no more and no fewer. */
    private void assertOrderListsExactly(String orderId, String... sampleIds) throws Exception {
        MvcResult order = mockMvc.perform(withKey(get("/api/orders/" + orderId)))
                .andExpect(status().isOk())
                .andReturn();
        List<String> listed = JsonPath.read(order.getResponse().getContentAsString(), "$.samples[*].id");
        assertThat(listed).containsExactlyInAnyOrder(sampleIds);
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

    /** Submits the Create Sample form and returns the new sample's ID taken from the redirect. */
    private String submitScreenForm(MockHttpSession session, String patientId, String sampleType) throws Exception {
        MvcResult submitted = mockMvc.perform(post("/samples")
                        .session(session)
                        .param("patientId", patientId)
                        .param("sampleType", sampleType))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = submitted.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/samples/SMP-");
        return location.substring("/samples/".length());
    }

    private String orderIdOfSample(String sampleId) throws Exception {
        MvcResult sample = mockMvc.perform(withKey(get("/api/samples/" + sampleId)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(sample.getResponse().getContentAsString(), "$.orderId");
    }

    private static String json(String patientId, String sampleType, String orderId) {
        return "{\"patientId\": \"" + patientId + "\", \"sampleType\": \"" + sampleType
                + "\", \"orderId\": \"" + orderId + "\"}";
    }

    private static String idOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
