package com.mocklab.web;

// @layer: integration
// @spec: 001-mock-lab-application

import com.mocklab.web.AuthController;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared acceptance-test scaffolding for the Mock Lab Application's Thymeleaf
 * MVC surface. Boots the <em>real</em> Spring context (not a
 * {@code @WebMvcTest} slice) so the actual {@code SessionAuthInterceptor},
 * {@code DataSeeder}-seeded patients/user, and every collaborating service
 * bean are exercised exactly as they run in production — only the HTTP
 * transport is mocked (MockMvc), per technical-considerations.md §8
 * ("Controller slice tests ... full @SpringBootTest + MockMvc").
 *
 * <p>{@code @DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)} forces a
 * brand-new Spring context (and therefore brand-new, empty in-memory
 * {@code SampleRepository}/{@code OrderRepository}/{@code ProcessingJobRepository}
 * singletons) before every single test method. The repositories expose no
 * {@code deleteAll()}/reset method (by design — see technical-considerations
 * §7's "never expose a reset endpoint" risk mitigation), and touching
 * production code to add one is out of scope for a test-only task, so a
 * fresh context per test is the only test-side way to guarantee each test
 * starts from a clean, deterministic slate (matching how the real app only
 * ever resets on a fresh boot). {@code DataSeeder} reseeds the same fixed
 * Users/Patients on every one of those fresh boots, so seeded-data
 * assertions (e.g. {@code PAT-1001} = "Alice Johnson") stay valid across all
 * tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@ExtendWith(SpringExtension.class)
abstract class AbstractWebAcceptanceTest {

    static final String SEEDED_USERNAME = "LabTech";
    static final String SEEDED_PASSWORD = "I_Like_This_Lab";
    static final String KNOWN_PATIENT_ID = "PAT-1001";
    static final String KNOWN_PATIENT_NAME = "Alice Johnson";

    @Autowired
    MockMvc mockMvc;

    /**
     * Signs in with the seeded account ({@link #SEEDED_USERNAME}/
     * {@link #SEEDED_PASSWORD}, configured via {@code application.yml})
     * via the real {@code POST /login} route and returns the resulting
     * {@link MockHttpSession} so callers can carry authentication across
     * subsequent {@code mockMvc.perform(...).session(session)} calls — the
     * standard way to preserve a session across separate MockMvc requests
     * for a hand-rolled (non-Spring-Security) session auth scheme.
     */
    MockHttpSession loginAsSeededUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", SEEDED_USERNAME)
                        .param("password", SEEDED_PASSWORD))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
