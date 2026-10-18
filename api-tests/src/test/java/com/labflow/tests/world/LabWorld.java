package com.labflow.tests.world;

import com.labflow.client.LabClient;
import com.labflow.client.http.LabResponse;
import com.labflow.common.config.LabConfig;
import com.labflow.common.model.Order;
import com.labflow.common.model.Patient;
import com.labflow.common.model.Sample;
import com.labflow.common.model.WorkflowStage;
import com.labflow.testdata.Patients;

import java.util.Arrays;

/**
 * Everything one scenario knows: the client for the lab named in the configuration, what the
 * scenario created, the last answer it got, and the check that proves the lab is unchanged.
 *
 * <p>Cucumber's PicoContainer creates one instance per scenario and hands the same instance to
 * every step and hook class of that scenario through its constructor. Two scenarios never share
 * one, so the state stays per scenario even when scenarios run side by side. It holds the client and the data recipes
 * that the former Jupiter base class held.
 */
public class LabWorld {

    private final LabClient lab = LabClient.create(LabConfig.fromEnvironment());

    private Patient patient;
    private Order order;
    private Sample sample;
    private LabResponse<?> lastResponse;
    private Runnable nothingChangedCheck = () -> { };

    /** The client that carries the right access key. */
    public LabClient lab() {
        return lab;
    }

    /** Registers a new patient from the shared recipe, with a unique {@code TEST-} name. */
    public Patient newPatient() {
        patient = lab.patients().register(Patients.valid().name());
        return patient;
    }

    /** The patient this scenario registered; fails with a clear message when none was. */
    public Patient patient() {
        return require(patient, "a patient registered in a Given step");
    }

    public void orderIs(Order placed) {
        this.order = placed;
    }

    /** The order this scenario placed; fails with a clear message when none was. */
    public Order order() {
        return require(order, "an order placed in a Given step");
    }

    public void sampleIs(Sample registered) {
        this.sample = registered;
    }

    /** The sample this scenario registered; fails with a clear message when none was. */
    public Sample sample() {
        return require(sample, "a sample registered in a Given step");
    }

    public void answerWas(LabResponse<?> response) {
        this.lastResponse = response;
    }

    /** The answer to the request the scenario sent last. */
    public LabResponse<?> lastResponse() {
        return require(lastResponse, "a request sent in a When step");
    }

    /** Remembers how to prove that the refused request changed nothing. */
    public void nothingChangedCanBeShownBy(Runnable check) {
        this.nothingChangedCheck = check;
    }

    public void assertNothingChanged() {
        nothingChangedCheck.run();
    }

    /** The stage a feature file names the way users see it, for example "Ready for Processing". */
    public static WorkflowStage stageNamed(String displayName) {
        return Arrays.stream(WorkflowStage.values())
                .filter(stage -> stage.displayName().equals(displayName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such stage: " + displayName));
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalStateException("This step needs " + what + " before it");
        }
        return value;
    }
}
