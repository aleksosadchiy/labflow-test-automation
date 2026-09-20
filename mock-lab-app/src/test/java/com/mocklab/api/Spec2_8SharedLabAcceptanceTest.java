package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature-level proof of functional-spec.md section 2.8 (Same Lab, Same Rules as the Screens):
 * the two "at the same time" criteria that the slice tests only proved for the same order or the
 * same sample (a duplicate race), never for many independent clients. The cross-surface criteria
 * were already proven end to end.
 *
 * <h2>Criteria to tests</h2>
 * <ol>
 *   <li><b>A client registers a sample: a signed-in user sees it on the Sample List with "Accessioned".</b>
 *       {@code SampleApiAcceptanceTest#aSampleRegisteredThroughTheApiShowsUpOnTheSampleListScreen};
 *       whole flow: {@code LabWorkflowEndToEndAcceptanceTest#aLabWorkflowRunsFromPatientToReportThroughTheApiAndIsSeenOnTheScreens}.</li>
 *   <li><b>A user registers a sample on the Create Sample screen: a client retrieves it with the same details.</b>
 *       {@code SampleApiAcceptanceTest#aSampleCreatedOnTheCreateSampleScreenIsReadableThroughTheApi};
 *       also {@code SampleStatusApiAcceptanceTest#aStageReachedWithTheScreensAdvanceButtonIsSeenByTheApiAndTheNextPutBuildsOnIt}
 *       and {@code SampleReportApiAcceptanceTest#aSampleAdvancedPartlyOnTheScreenAndPartlyThroughTheApiHasAConsistentProgress}.</li>
 *   <li><b>Several clients register samples at the same time: each gets a distinct ID, none lost or overwritten.</b>
 *       {@link #severalClientsRegisteringAtTheSameTimeEachGetTheirOwnSampleAndNoneIsLost}. Related race, same order and type:
 *       {@code SampleOrderApiAcceptanceTest#tenSimultaneousRequestsForTheSameOrderAndTypeCreateExactlyOneSample}
 *       (service level: {@code SampleServiceOrderRulesTest#threadsRegisteringDifferentTypesOnOneOrderAllSucceedWithDistinctSampleIds}).</li>
 *   <li><b>Two clients each move a different sample forward at the same time: each ends at the stage that
 *       client asked for, neither affected by the other.</b>
 *       {@link #clientsMovingDifferentSamplesAtTheSameTimeEachEndAtTheirOwnRequestedStage}. Related race, same sample:
 *       {@code SampleStatusApiAcceptanceTest#tenSimultaneousPutsOfTheNextStageAcceptExactlyOneAndTheSampleMovesExactlyOneStage}.</li>
 * </ol>
 */
class Spec2_8SharedLabAcceptanceTest extends AbstractSpecAcceptanceTest {

    private static final int CLIENTS = 12;

    // @regression
    @Test
    void severalClientsRegisteringAtTheSameTimeEachGetTheirOwnSampleAndNoneIsLost() throws Exception {
        // Everybody registers the same type for the same patient: with no order named, each
        // request gets its own new order, so none of them may be turned away as a duplicate.
        List<Callable<String>> clients = new ArrayList<>();
        for (int i = 0; i < CLIENTS; i++) {
            clients.add(() -> registerSample("PAT-1001", "BLOOD"));
        }

        List<String> created = runAtTheSameTime(clients);

        List<String> sampleIds = created.stream().map(body -> read(body, "$.id")).toList();
        List<String> orderIds = created.stream().map(body -> read(body, "$.orderId")).toList();
        assertThat(sampleIds).as("sample IDs").doesNotHaveDuplicates().hasSize(CLIENTS);
        assertThat(orderIds).as("order IDs").doesNotHaveDuplicates().hasSize(CLIENTS);

        String list = getOk("/api/samples");
        for (String sampleId : sampleIds) {
            JSONArray listed = JsonPath.read(list, "$[?(@.id == '" + sampleId + "')]");
            assertThat(listed).as("sample %s in the list", sampleId).hasSize(1);
            mockMvc.perform(withKey(get("/api/samples/" + sampleId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                    .andExpect(jsonPath("$.sampleType").value("BLOOD"))
                    .andExpect(jsonPath("$.status").value("ACCESSIONED"));
        }
    }

    // @regression
    @Test
    void clientsMovingDifferentSamplesAtTheSameTimeEachEndAtTheirOwnRequestedStage() throws Exception {
        // Client k moves its own sample k stages forward (1..7), all clients at once; a bystander is never touched.
        int clients = STAGES.size() - 1;
        List<String> sampleIds = new ArrayList<>();
        for (int k = 1; k <= clients; k++) {
            sampleIds.add(read(registerSample("PAT-1001", "BLOOD"), "$.id"));
        }
        String bystander = read(registerSample("PAT-1002", "SWAB"), "$.id");

        List<Callable<Integer>> movers = new ArrayList<>();
        for (int k = 1; k <= clients; k++) {
            String sampleId = sampleIds.get(k - 1);
            int stages = k;
            movers.add(() -> {
                for (int stage = 1; stage <= stages; stage++) {
                    // Every single request of every client must be accepted.
                    mockMvc.perform(putStatus(sampleId, STAGES.get(stage))).andExpect(status().isOk());
                }
                return stages;
            });
        }

        runAtTheSameTime(movers);

        for (int k = 1; k <= clients; k++) {
            String sampleId = sampleIds.get(k - 1);
            String expected = STAGES.get(k);
            assertThat(read(getOk("/api/samples/" + sampleId), "$.status"))
                    .as("stage of the sample moved %d step(s)", k).isEqualTo(expected);
            String progress = getOk("/api/samples/" + sampleId + "/progress");
            assertThat(read(progress, "$.currentStatus")).isEqualTo(expected);
            List<String> reached = JsonPath.read(progress, "$.stages[*].status");
            assertThat(reached).containsExactlyElementsOf(STAGES.subList(0, k + 1));
        }
        assertThat(read(getOk("/api/samples/" + bystander), "$.status")).isEqualTo("ACCESSIONED");
    }

    /** Runs every task on its own thread, released together, and returns the results in task order. */
    private static <T> List<T> runAtTheSameTime(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
