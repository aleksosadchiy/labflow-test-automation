package com.labflow.testdata;

/** Recipes for patients with valid defaults. */
public final class Patients {

    private Patients() {
    }

    /** A valid patient with a unique {@code TEST-patient-...} name. */
    public static PatientDraft valid() {
        return new PatientDraft(UniqueData.name("patient"));
    }
}
