package com.mocklab.web;

/**
 * View-friendly projection of a {@link com.mocklab.domain.Sample} for the
 * Sample List screen (functional-spec.md §2.2): the sample's identifier,
 * its patient's display name (resolved via {@code PatientRepository}), and
 * its current status.
 */
public record SampleListRow(String sampleId, String patientName, String status) {
}
