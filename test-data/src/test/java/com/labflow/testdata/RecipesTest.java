package com.labflow.testdata;

// @layer: unit
// @spec: 005-reliable-isolated-execution

import com.labflow.common.model.SampleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link Patients}, {@link Orders} and {@link Samples} recipes, per spec 005
 * functional-spec.md §2.4: each recipe gives valid defaults, each variation changes only the
 * detail it names, and the original draft is never changed.
 *
 * @spec: 005-reliable-isolated-execution
 */
class RecipesTest {

    private static final String PATIENT_ID = "P-1";
    private static final String ORDER_ID = "O-1";

    // ---- patients ----

    @Test
    // @regression
    @DisplayName("A valid patient has a unique test-prefixed patient name")
    void validPatientHasUniqueTestName() {
        PatientDraft first = Patients.valid();
        PatientDraft second = Patients.valid();

        assertTrue(first.name().startsWith("TEST-patient-"), first.name());
        assertNotEquals(first.name(), second.name());
    }

    @Test
    // @regression
    @DisplayName("Patient variations set the name, leave it out or make it blank, and leave the original unchanged")
    void patientVariationsReturnChangedCopies() {
        PatientDraft original = Patients.valid();
        String originalName = original.name();

        assertEquals("Ann", original.withName("Ann").name());
        assertNull(original.withoutName().name());
        assertEquals("   ", original.blankName().name());
        assertEquals(originalName, original.name());
    }

    // ---- orders ----

    @Test
    // @regression
    @DisplayName("A valid order is for the given patient")
    void validOrderIsForGivenPatient() {
        assertEquals(PATIENT_ID, Orders.validFor(PATIENT_ID).patientId());
    }

    @Test
    // @regression
    @DisplayName("An order without a patient leaves the patient out, and the original keeps it")
    void orderWithoutPatientReturnsChangedCopy() {
        OrderDraft original = Orders.validFor(PATIENT_ID);

        assertNull(original.withoutPatient().patientId());
        assertEquals(PATIENT_ID, original.patientId());
    }

    // ---- samples ----

    @Test
    // @regression
    @DisplayName("A valid sample is a blood sample for the given patient with no order")
    void validSampleIsBloodWithoutOrder() {
        SampleDraft sample = Samples.validFor(PATIENT_ID);

        assertEquals(new SampleDraft(PATIENT_ID, "BLOOD", null), sample);
        assertEquals(SampleType.BLOOD, sample.type());
    }

    @Test
    // @regression
    @DisplayName("Each sample variation changes only the detail it names, and the original is unchanged")
    void sampleVariationsChangeOnlyTheirDetail() {
        SampleDraft original = Samples.validFor(PATIENT_ID);

        assertEquals(new SampleDraft(PATIENT_ID, "TISSUE", null), original.ofType(SampleType.TISSUE));
        assertEquals(new SampleDraft(PATIENT_ID, "PLASMA", null), original.withRawType("PLASMA"));
        assertEquals(new SampleDraft(null, "BLOOD", null), original.withoutPatient());
        assertEquals(new SampleDraft(PATIENT_ID, null, null), original.withoutType());
        assertEquals(new SampleDraft(PATIENT_ID, "BLOOD", ORDER_ID), original.inOrder(ORDER_ID));
        assertEquals(new SampleDraft(PATIENT_ID, "BLOOD", null), original);
    }

    @Test
    // @regression
    @DisplayName("A sample of an unsupported or missing type has no supported type, only its raw value")
    void unsupportedOrMissingTypeHasNoSupportedType() {
        SampleDraft unsupported = Samples.validFor(PATIENT_ID).withRawType("PLASMA");

        assertEquals("PLASMA", unsupported.rawType());
        assertThrows(IllegalStateException.class, unsupported::type);
        assertThrows(IllegalStateException.class, Samples.validFor(PATIENT_ID).withoutType()::type);
    }
}
