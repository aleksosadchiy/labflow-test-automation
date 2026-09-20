package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.mocklab.repository.OrderRepository;
import com.mocklab.repository.PatientRepository;
import com.mocklab.repository.ProcessingJobRepository;
import com.mocklab.repository.ReportRepository;
import com.mocklab.repository.SampleRepository;
import com.mocklab.repository.TestResultRepository;
import com.mocklab.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature-level proof of the part of functional-spec.md section 2.9 (Starting the Lab
 * Reliably) that can be observed inside a test JVM: what the lab looks like the moment it
 * has started, and when it says it is ready. The context of this class is its own (the
 * recorder below makes it unique), so it is freshly started, exactly like a new lab, and no
 * other test's data is in it. The tests here never create a sample, order or patient.
 *
 * <h2>Criteria to tests</h2>
 * <ol>
 *   <li><b>A developer runs the single documented start command and the lab starts with no further
 *       manual steps.</b> MANUAL / CI-LEVEL, not testable inside the module. Evidence: README
 *       "Running the lab" (one command: {@code cd mock-lab-app && ./mvnw spring-boot:run}), the Maven Wrapper
 *       files in {@code mock-lab-app/}, and the Slice 1 "Verify" task in tasks.md (clean package, {@code java -jar},
 *       and {@code ./mvnw spring-boot:run} both confirmed to start). What is automated: the whole application
 *       boots from its own configuration alone, with no extra properties or files, in every
 *       {@code @SpringBootTest} of this module.</li>
 *   <li><b>When started, a readiness check with no access key reports ready.</b>
 *       {@code HealthEndpointIntegrationTest#healthReturns200UpWithoutCredentials} (real port, real HTTP),
 *       {@code ApiKeyRegistrationScopeIntegrationTest#healthIsNotAffectedByTheKeyFilter}, and here
 *       {@link #theLabIsReadyOnlyOnceStartUpAndSeedingHaveFinished}.</li>
 *   <li><b>Asked before the lab has finished starting, the readiness check does not report ready.</b>
 *       {@code HealthControllerTest#refusingTrafficReturns503Starting} (mocked availability); with the real
 *       controller and the real readiness state, both while the start-up runners execute and when
 *       the state is switched back and forth, here:
 *       {@link #theLabIsReadyOnlyOnceStartUpAndSeedingHaveFinished} and
 *       {@link #theReadinessCheckFollowsTheLabsReadinessStateAndNeedsNoKey}.</li>
 *   <li><b>The same known pre-loaded patients and sign-in user every start, and no samples, orders or other
 *       records.</b>
 *       {@link #theSevenKnownPatientsAreAvailableWithTheirNames},
 *       {@link #theKnownSignInUserCanSignInAndIsTheOnlyUser},
 *       {@link #noSamplesOrOrdersOrOtherRecordsExistAtStart} (and
 *       {@code SampleListEmptyApiAcceptanceTest#aFreshApplicationListsNoSamples}).</li>
 *   <li><b>A chosen access key is the only one accepted; with none chosen, the documented default is.</b>
 *       {@code Spec2_9AccessKeyConfigurationAcceptanceTest} (a lab started with a chosen key) and
 *       {@code Spec2_1AccessControlAcceptanceTest#theDocumentedLocalDefaultKeyIsAcceptedWhenNoKeyIsChosen}
 *       (and every other API test, which uses the default). The {@code API_TOKEN} environment variable
 *       reaches the same property through {@code application.yml}
 *       ({@code ${API_TOKEN:mock-lab-local-token}}); a blank one stops start-up:
 *       {@code ApiSecurityConfigTest#blankTokenStopsStartupWithAClearMessage}.</li>
 *   <li><b>In the automated build the lab is ready within 30 seconds.</b> MANUAL / CI-LEVEL: a wall-clock
 *       budget of a real {@code java -jar}, measured from launch to the first 200 from {@code /health}.
 *       Evidence: the Slice 1 "Verify" task in tasks.md (launch-to-first-200 timed, under 30 seconds). The CI/CD
 *       spec wires it up as the README's {@code curl --retry-max-time 30} poll, which fails the job if
 *       the budget is exceeded.</li>
 * </ol>
 */
class Spec2_9StartupStateAcceptanceTest extends AbstractSpecAcceptanceTest {

    @Autowired PatientRepository patientRepository;
    @Autowired UserRepository userRepository;
    @Autowired SampleRepository sampleRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired ProcessingJobRepository processingJobRepository;
    @Autowired TestResultRepository testResultRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired ApplicationContext applicationContext;
    @Autowired StartupRecorder recorder;

    /** The seeded fixture, by ID and name (DataSeeder). */
    private static final Map<String, String> KNOWN_PATIENTS = Map.of(
            "PAT-1001", "Alice Johnson",
            "PAT-1002", "Brian Smith",
            "PAT-1003", "Carla Diaz",
            "PAT-1004", "David Chen",
            "PAT-1005", "Emily Turner",
            "PAT-1006", "Farid Haidari",
            "PAT-1007", "Grace Kim");

    // ---- what a freshly started lab holds ------------------------------------------------------

    // @regression
    @Test
    void theSevenKnownPatientsAreAvailableWithTheirNames() throws Exception {
        for (Map.Entry<String, String> patient : KNOWN_PATIENTS.entrySet()) {
            mockMvc.perform(withKey(get("/api/patients/" + patient.getKey())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(patient.getKey()))
                    .andExpect(jsonPath("$.name").value(patient.getValue()));
        }
        // ... and there are exactly these seven: nothing else was pre-loaded.
        assertThat(storedRecords(patientRepository)).isEqualTo(KNOWN_PATIENTS.size());
        expectError(mockMvc.perform(withKey(get("/api/patients/PAT-1008"))),
                404, "Not Found", "Patient not found.", "/api/patients/PAT-1008");
    }

    // @regression
    @Test
    void theKnownSignInUserCanSignInAndIsTheOnlyUser() throws Exception {
        mockMvc.perform(post("/login").param("username", SEEDED_USERNAME).param("password", SEEDED_PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/samples"));
        mockMvc.perform(post("/login").param("username", SEEDED_USERNAME).param("password", "not-the-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid username or password.")));
        assertThat(storedRecords(userRepository)).isEqualTo(1);
    }

    // @regression
    @Test
    void noSamplesOrOrdersOrOtherRecordsExistAtStart() throws Exception {
        mockMvc.perform(withKey(get("/api/samples")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
        String screen = mockMvc.perform(get("/samples").session(signIn()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(screen).contains("No samples registered yet.");

        assertThat(storedRecords(sampleRepository)).as("samples").isZero();
        assertThat(storedRecords(orderRepository)).as("orders").isZero();
        assertThat(storedRecords(processingJobRepository)).as("processing jobs").isZero();
        assertThat(storedRecords(testResultRepository)).as("test results").isZero();
        assertThat(storedRecords(reportRepository)).as("reports").isZero();
    }

    // ---- readiness -----------------------------------------------------------------------------

    /**
     * The lab must not report ready while it is still starting, and must be fully seeded by the
     * time it does. The recorder watched the real start-up: it asked {@code /health} while the
     * start-up runners were executing (the seeder is one of them, and the port is already open),
     * and looked at the data at the instant the framework declared the lab ready.
     */
    // @regression
    @Test
    void theLabIsReadyOnlyOnceStartUpAndSeedingHaveFinished() throws Exception {
        assertThat(recorder.healthStatusWhileStartingUp).as("/health while start-up runners execute").isEqualTo(503);
        assertThat(recorder.healthBodyWhileStartingUp).contains("STARTING").doesNotContain("UP");

        assertThat(recorder.patientsWhenDeclaredReady).as("patients seeded when declared ready")
                .isEqualTo(KNOWN_PATIENTS.size());
        assertThat(recorder.usersWhenDeclaredReady).as("users seeded when declared ready").isEqualTo(1);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    // @regression
    @Test
    void theReadinessCheckFollowsTheLabsReadinessStateAndNeedsNoKey() throws Exception {
        try {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
            mockMvc.perform(get("/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().json("{\"status\":\"STARTING\"}", true));
        } finally {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
        }
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    // ---- observing the real start-up -----------------------------------------------------------

    @TestConfiguration
    static class StartupRecorderConfig {
        @Bean
        StartupRecorder startupRecorder(ObjectProvider<MockMvc> mockMvc, PatientRepository patients,
                                        UserRepository users) {
            return new StartupRecorder(mockMvc, patients, users);
        }
    }

    /**
     * Test-only observer of the real start-up. As a {@link CommandLineRunner} it runs in the same
     * phase as the production {@code DataSeeder}: after the port is open, before the lab is declared
     * ready. It also listens for the framework's "ready" announcement.
     */
    static class StartupRecorder implements CommandLineRunner {
        private final ObjectProvider<MockMvc> mockMvc;
        private final PatientRepository patients;
        private final UserRepository users;

        volatile int healthStatusWhileStartingUp = -1;
        volatile String healthBodyWhileStartingUp = "";
        volatile int patientsWhenDeclaredReady = -1;
        volatile int usersWhenDeclaredReady = -1;

        StartupRecorder(ObjectProvider<MockMvc> mockMvc, PatientRepository patients, UserRepository users) {
            this.mockMvc = mockMvc;
            this.patients = patients;
            this.users = users;
        }

        @Override
        public void run(String... args) throws Exception {
            var response = mockMvc.getObject().perform(get("/health")).andReturn().getResponse();
            healthStatusWhileStartingUp = response.getStatus();
            healthBodyWhileStartingUp = response.getContentAsString();
        }

        @EventListener(AvailabilityChangeEvent.class)
        void onAvailabilityChange(AvailabilityChangeEvent<?> event) {
            if (event.getState() == ReadinessState.ACCEPTING_TRAFFIC && patientsWhenDeclaredReady < 0) {
                patientsWhenDeclaredReady = storedRecords(patients);
                usersWhenDeclaredReady = storedRecords(users);
            }
        }
    }
}
