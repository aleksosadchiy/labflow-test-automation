package com.mocklab.web.form;

/**
 * Form-backing object for the Create Sample screen. A plain mutable class
 * (matching {@link LoginForm}'s style) so Spring's {@code @ModelAttribute}
 * binding and Thymeleaf's {@code th:field} can both read and write it via a
 * no-arg constructor and setters. Both fields are {@code String} since
 * {@code SampleService.createSample(String, String)} takes raw strings.
 */
public class CreateSampleForm {

    private String patientId;
    private String sampleType;

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getSampleType() {
        return sampleType;
    }

    public void setSampleType(String sampleType) {
        this.sampleType = sampleType;
    }
}
