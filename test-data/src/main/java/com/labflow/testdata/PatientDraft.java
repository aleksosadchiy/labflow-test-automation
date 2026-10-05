package com.labflow.testdata;

/**
 * The input for registering a patient, e.g. {@code lab.patients().register(draft.name())}.
 * Immutable: every variation returns a copy.
 *
 * @param name the patient's name; {@code null} means it is left out of the request
 */
public record PatientDraft(String name) {

    /** A copy with the given name. */
    public PatientDraft withName(String newName) {
        return new PatientDraft(newName);
    }

    /** A copy with the name left out of the request. */
    public PatientDraft withoutName() {
        return new PatientDraft(null);
    }

    /** A copy whose name is present but only whitespace. */
    public PatientDraft blankName() {
        return new PatientDraft("   ");
    }
}
