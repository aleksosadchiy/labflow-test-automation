package com.labflow.testdata;

import com.labflow.common.model.SampleType;

/** Recipes for samples with valid defaults. */
public final class Samples {

    private Samples() {
    }

    /** A valid sample for the given patient: a supported type ({@link SampleType#BLOOD}) and no order. */
    public static SampleDraft validFor(String patientId) {
        return new SampleDraft(patientId, SampleType.BLOOD.name(), null);
    }
}
