// @layer: integration
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.rejection;

import com.labflow.client.LabClient;
import com.labflow.client.http.LabResponse;
import com.labflow.common.LabMessages;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.tests.support.LabTest;
import com.labflow.tests.support.UniqueData;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static com.labflow.client.assertions.LabAssertions.assertThat;
import static com.labflow.client.http.Rejection.UNAUTHORIZED;

/**
 * The lab answers "Unauthorized." to any request that does not carry the right access key, and
 * says how to sign in with a {@code WWW-Authenticate: Bearer} challenge. The matrix below is
 * two ways of not having the key ({@link Caller}) times three requests that would create or
 * change something ({@link Change}); each pair is its own result. Every check proves the kind
 * of refusal, the exact message, the challenge, that the lab reported nothing as created (no
 * {@code Location} header, nothing returned) and that the state the lab lets a check observe
 * is unchanged, read back with the <em>valid</em> client {@code lab}.
 *
 * <p>The wrong key is a plainly fake text. The real key is never written, shown or asserted here.
 */
class AuthenticationRejectionTest extends LabTest {

    /** The two ways of arriving at the lab without the right key. */
    enum Caller {
        WITHOUT_KEY("Without an access key", LabClient::withoutApiKey),
        WITH_WRONG_KEY("With a wrong access key", valid -> valid.withApiKey("wrong-key"));

        private final String scenario;
        private final Function<LabClient, LabClient> asSeenByTheLab;

        Caller(String scenario, Function<LabClient, LabClient> asSeenByTheLab) {
            this.scenario = scenario;
            this.asSeenByTheLab = asSeenByTheLab;
        }

        /** A client for the same lab, but one that cannot sign in. */
        LabClient client(LabClient valid) {
            return asSeenByTheLab.apply(valid);
        }

        @Override
        public String toString() {
            return scenario;
        }
    }

    /** The answer to the refused request, and how to see that nothing changed. */
    record Attempt(LabResponse<?> response, Runnable nothingChangedCheck) {

        void assertNothingChanged() {
            nothingChangedCheck.run();
        }
    }

    /**
     * A request that would create or change something. Each one first sets up what it needs
     * with the valid client, then sends the request as the given caller, and hands back the
     * answer with the check that proves the lab is untouched.
     */
    enum Change {
        REGISTER_PATIENT("registering a patient is refused, and no patient is issued") {
            @Override
            Attempt attempt(LabClient valid, LabClient caller) {
                LabResponse<Patient> response = caller.patients().attemptRegister(UniqueData.name("patient"));
                // The lab lists no patients, so a check cannot look for the refused one. What it
                // can observe is that no Location header and no patient came back; that is
                // asserted for every case below.
                return new Attempt(response, () -> { });
            }
        },
        REGISTER_SAMPLE("registering a sample is refused, and the patient still has none") {
            @Override
            Attempt attempt(LabClient valid, LabClient caller) {
                Patient patient = valid.patients().register(UniqueData.name("patient"));

                LabResponse<Sample> response =
                        caller.samples().attemptRegister(patient.id(), SampleType.BLOOD.name());

                return new Attempt(response, () -> Assertions.assertThat(valid.samples().listFor(patient.id()))
                        .as("samples the lab holds for the patient after the refusal")
                        .isEmpty());
            }
        },
        // The stage asked for is the valid next one, so the only thing wrong with the move is the key.
        MOVE_SAMPLE("moving a sample to its next stage is refused, and it stays at Accessioned") {
            @Override
            Attempt attempt(LabClient valid, LabClient caller) {
                Patient patient = valid.patients().register(UniqueData.name("patient"));
                Sample sample = valid.samples().register(patient.id(), SampleType.BLOOD);

                LabResponse<Sample> response =
                        caller.samples().attemptMoveTo(sample.id(), WorkflowStage.READY_FOR_PROCESSING);

                return new Attempt(response, () -> Assertions.assertThat(valid.samples().find(sample.id()).status())
                        .as("the sample's stage after the refused move")
                        .isEqualTo(WorkflowStage.ACCESSIONED));
            }
        };

        private final String scenario;

        Change(String scenario) {
            this.scenario = scenario;
        }

        abstract Attempt attempt(LabClient valid, LabClient caller);

        @Override
        public String toString() {
            return scenario;
        }
    }

    /** Every pair of (way of not having the key, request), so 2 x 3 = 6 separate results. */
    static Stream<Arguments> everyCallerAndChange() {
        return Stream.of(Caller.values())
                .flatMap(caller -> Stream.of(Change.values()).map(change -> Arguments.of(caller, change)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("everyCallerAndChange")
    @DisplayName("A request that would create or change something is refused without the right key, and nothing changes")
    void requestWithoutTheRightKeyIsRefused(Caller caller, Change change) {
        Attempt attempt = change.attempt(lab, caller.client(lab));

        assertThat(attempt.response())
                .isRejectedAs(UNAUTHORIZED, LabMessages.UNAUTHORIZED)
                .hasHeader("WWW-Authenticate", "Bearer")
                .hasNoLocationHeader();
        Assertions.assertThat(attempt.response().body())
                .as("anything the lab created or returned for the refused request")
                .isEmpty();
        attempt.assertNothingChanged();
    }

    @Test
    @DisplayName("Looking a sample up without an access key is refused too, and shows nothing of the sample")
    void readWithoutKeyIsRefused() {
        Patient patient = lab.patients().register(UniqueData.name("patient"));
        Sample sample = lab.samples().register(patient.id(), SampleType.BLOOD);

        LabResponse<Sample> response = lab.withoutApiKey().samples().attemptFind(sample.id());

        assertThat(response)
                .isRejectedAs(UNAUTHORIZED, LabMessages.UNAUTHORIZED)
                .hasHeader("WWW-Authenticate", "Bearer");
        Assertions.assertThat(response.body())
                .as("the sample the lab returned to a caller without a key")
                .isEmpty();
    }
}
