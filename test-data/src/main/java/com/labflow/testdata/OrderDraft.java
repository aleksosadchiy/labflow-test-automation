package com.labflow.testdata;

/**
 * The input for placing an order, e.g. {@code lab.orders().place(draft.patientId())}.
 * Immutable: every variation returns a copy.
 *
 * @param patientId the patient the order is for; {@code null} means it is left out
 */
public record OrderDraft(String patientId) {

    /** A copy with the patient left out of the request. */
    public OrderDraft withoutPatient() {
        return new OrderDraft(null);
    }
}
