package com.labflow.testdata;

import com.labflow.common.model.SampleType;

/**
 * The input for registering a sample. The type is held as the raw wire value, so a draft can
 * also carry a type the lab does not support. A check passes the fields to the service, e.g.
 * {@code lab.samples().attemptRegister(s.patientId(), s.rawType(), s.orderId())}, or
 * {@code lab.samples().register(s.patientId(), s.type(), s.orderId())} for a supported type.
 * Immutable: every variation returns a copy.
 *
 * @param patientId the patient the sample belongs to; {@code null} means it is left out
 * @param rawType   the sample type as sent on the wire; {@code null} means it is left out
 * @param orderId   the order the sample belongs to; {@code null} means no order
 */
public record SampleDraft(String patientId, String rawType, String orderId) {

    /**
     * The type as a {@link SampleType}, for the {@code register} methods.
     *
     * @throws IllegalStateException if the type is left out or is not one the lab supports
     */
    public SampleType type() {
        if (rawType == null) {
            throw new IllegalStateException("This sample draft has no type");
        }
        try {
            return SampleType.valueOf(rawType);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("'" + rawType + "' is not a supported sample type; use rawType()", e);
        }
    }

    /** A copy of the given supported type. */
    public SampleDraft ofType(SampleType type) {
        return new SampleDraft(patientId, type.name(), orderId);
    }

    /** A copy with any raw type value, e.g. one the lab does not support such as {@code "PLASMA"}. */
    public SampleDraft withRawType(String newRawType) {
        return new SampleDraft(patientId, newRawType, orderId);
    }

    /** A copy with the patient left out of the request. */
    public SampleDraft withoutPatient() {
        return new SampleDraft(null, rawType, orderId);
    }

    /** A copy with the type left out of the request. */
    public SampleDraft withoutType() {
        return new SampleDraft(patientId, null, orderId);
    }

    /** A copy registered under the given order. */
    public SampleDraft inOrder(String newOrderId) {
        return new SampleDraft(patientId, rawType, newOrderId);
    }
}
