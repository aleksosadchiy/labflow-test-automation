package com.labflow.tests.steps;

import com.labflow.common.model.Order;
import com.labflow.testdata.OrderDraft;
import com.labflow.testdata.Orders;
import com.labflow.testdata.UniqueData;
import com.labflow.tests.world.LabWorld;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of topic "03. Orders". They place an order, look it up, and
 * check the order input rules. The refusal wording is checked by the step in {@link PatientsSteps}.
 */
public class OrdersSteps {

    private final LabWorld world;

    private Order placed;
    private Order found;

    public OrdersSteps(LabWorld world) {
        this.world = world;
    }

    @When("a caller places an order for the patient")
    public void aCallerPlacesAnOrderForThePatient() {
        placed = world.lab().orders().place(world.patient().id());
    }

    @When("the caller looks the order up by the id the lab gave it")
    public void theCallerLooksTheOrderUp() {
        found = world.lab().orders().find(placed.id());
    }

    @Then("the lab returns the order for that patient, with a creation time and no samples")
    public void theLabReturnsTheOrder() {
        assertThat(found.id()).isNotBlank().isEqualTo(placed.id());
        assertThat(found.patientId()).isEqualTo(world.patient().id());
        assertThat(found.createdAt()).isNotNull();
        assertThat(found.samples()).isEmpty();
    }

    @When("^a caller places an order for (no patient|a patient the lab does not know)$")
    public void aCallerPlacesAnOrderForNoOrUnknownPatient(String patient) {
        OrderDraft draft = Orders.validFor(UniqueData.name("unknown-patient"));
        OrderDraft sent = switch (patient) {
            case "no patient" -> draft.withoutPatient();
            case "a patient the lab does not know" -> draft;
            default -> throw new IllegalArgumentException("No such patient in this topic: " + patient);
        };
        world.answerWas(world.lab().orders().attemptPlace(sent.patientId()));
    }
}
