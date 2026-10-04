package com.labflow.testdata;

/** Recipes for orders with valid defaults. */
public final class Orders {

    private Orders() {
    }

    /** A valid order for the given patient. */
    public static OrderDraft validFor(String patientId) {
        return new OrderDraft(patientId);
    }
}
