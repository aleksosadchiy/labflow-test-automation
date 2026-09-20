package com.mocklab.api;

// @layer: integration
// @spec: 002-mock-lab-rest-api

import com.mocklab.repository.SampleRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature-level proof of functional-spec.md section 2.4 (Samples): the one criterion no
 * single slice could prove on its own, the full "first problem wins" order across the whole
 * chain. The other 2.4 criteria are proven by the slice acceptance tests listed below.
 *
 * <h2>Criteria to tests</h2>
 * <ol>
 *   <li><b>Register with patient + supported type: created, lab-assigned ID, "Accessioned",
 *       ID/patient/type/status shown.</b>
 *       {@code SampleApiAcceptanceTest#registeringABloodSampleReturns201WithLocationAndTheNewSampleAndItsOrder},
 *       {@code SampleApiAcceptanceTest#aClientSuppliedIdAndStatusAreIgnored}.</li>
 *   <li><b>Patient missing or blank: "Patient is required."</b>
 *       {@code SampleApiAcceptanceTest#aMissingPatientIsRejected}, {@code #aNullPatientIsRejected},
 *       {@code #aBlankPatientIsRejected}.</li>
 *   <li><b>Sample type missing or blank: "Sample type is required."</b>
 *       {@code SampleApiAcceptanceTest#aMissingSampleTypeIsRejected}, {@code #aBlankSampleTypeIsRejected}.</li>
 *   <li><b>Unknown patient: "Patient not found."</b> {@code SampleApiAcceptanceTest#anUnknownPatientIsNotFound}.</li>
 *   <li><b>Unsupported type: "Invalid sample type."</b> {@code SampleApiAcceptanceTest#anInvalidSampleTypeIsRejected}.</li>
 *   <li><b>Several problems: only the first is reported, in the fixed order.</b>
 *       Pairs are proven by {@code SampleApiAcceptanceTest#whenSeveralThingsAreWrongOnlyThePatientProblemIsReported},
 *       {@code #anUnknownPatientAndAnInvalidTypeReportOnlyTheUnknownPatient},
 *       {@code SampleOrderApiAcceptanceTest#anUnknownPatientBeatsAnUnknownOrder},
 *       {@code #anInvalidSampleTypeBeatsAnUnknownOrder}, {@code #aBlankPatientBeatsAnUnknownOrder},
 *       {@code #anOrderOfAnotherPatientIsReportedBeforeADuplicateType}; the whole ladder, including
 *       "type missing before patient not found" and "order not found before order of another patient",
 *       which no pair test covered, is proven here by {@link #whenSeveralThingsAreWrongOnlyTheFirstProblemInTheSpecifiedOrderIsReported}.</li>
 *   <li><b>Look up by ID: ID, patient, type, current status.</b>
 *       {@code SampleApiAcceptanceTest#aRegisteredSampleCanBeLookedUpByItsId}.</li>
 *   <li><b>Look up an unknown ID: "Sample not found."</b> {@code SampleApiAcceptanceTest#anUnknownSampleIdIsNotFound}.</li>
 *   <li><b>List: every registered sample, or an empty list.</b>
 *       {@code SampleApiAcceptanceTest#aRegisteredSampleAppearsInTheSampleList},
 *       {@code SampleListEmptyApiAcceptanceTest#aFreshApplicationListsNoSamples} (fresh context),
 *       {@code Spec2_8SharedLabAcceptanceTest#severalClientsRegisteringAtTheSameTimeEachGetTheirOwnSampleAndNoneIsLost}
 *       (many samples, all listed).</li>
 *   <li><b>Existing order of the named patient: attached, order shown with the sample.</b>
 *       {@code SampleOrderApiAcceptanceTest#aSampleNamingAnExistingOrderIsRegisteredUnderThatOrder}.</li>
 *   <li><b>No order named: a new order is created, as the Create Sample screen does.</b>
 *       {@code SampleOrderApiAcceptanceTest#aNullOrEmptyOrBlankOrderIdEachCreateANewOrder},
 *       {@code #omittingTheOrderIdEntirelyStillCreatesANewOrder},
 *       {@code #everyCreateSampleScreenSubmissionStillGetsItsOwnNewOrder}.</li>
 *   <li><b>Unknown order: "Order not found."</b>
 *       {@code SampleOrderApiAcceptanceTest#anUnknownOrderIsNotFoundAndNoSampleIsCreated}.</li>
 *   <li><b>Order of a different patient: "Order does not belong to this patient."</b>
 *       {@code SampleOrderApiAcceptanceTest#anOrderBelongingToAnotherPatientIsRejectedAndNothingIsAdded}.</li>
 *   <li><b>Second sample of the same type on the order: "A sample of this type already exists for this
 *       order.", nothing created.</b>
 *       {@code SampleOrderApiAcceptanceTest#aSecondSampleOfTheSameTypeOnTheSameOrderIsRejected},
 *       {@code #theDuplicateTypeCheckIgnoresLetterCase},
 *       {@code #tenSimultaneousRequestsForTheSameOrderAndTypeCreateExactlyOneSample}.</li>
 *   <li><b>A different type on an order that already has a sample: accepted, both listed under the order.</b>
 *       {@code SampleOrderApiAcceptanceTest#aSecondSampleOfAnotherTypeCanJoinTheSameOrder}.</li>
 * </ol>
 */
class Spec2_4SampleRegistrationAcceptanceTest extends AbstractSpecAcceptanceTest {

    @Autowired
    SampleRepository sampleRepository;

    /**
     * One request breaks the rules one at a time, from the top of the specified order down:
     * each row fixes the problem the row above was reported for, so the next problem in the
     * list is the one that must be reported. The last row is the control: with nothing wrong,
     * the same shape of request is accepted.
     *
     * <p>Tokens: patient BLANK / UNKNOWN (no such patient) / OTHER (PAT-1002) / OWNER (PAT-1001,
     * who owns the order); order UNKNOWN (no such order) / OWNED (an order of PAT-1001 that already
     * has a BLOOD sample). Rejected rows must leave the sample count unchanged.
     */
    // @regression
    @ParameterizedTest(name = "{0}")
    @CsvSource(useHeadersInDisplayName = false, delimiter = '|', textBlock = """
            1 patient missing (all else wrong too)      | BLANK   | BLANK  | UNKNOWN | 400 | Bad Request | Patient is required.
            2 type missing (patient, order also wrong)  | UNKNOWN | BLANK  | UNKNOWN | 400 | Bad Request | Sample type is required.
            3 patient not found (type, order also wrong)| UNKNOWN | PLASMA | UNKNOWN | 404 | Not Found   | Patient not found.
            4 invalid type (order also wrong)           | OTHER   | PLASMA | UNKNOWN | 400 | Bad Request | Invalid sample type.
            5 order not found                           | OTHER   | BLOOD  | UNKNOWN | 404 | Not Found   | Order not found.
            6 order of another patient (also duplicate) | OTHER   | BLOOD  | OWNED   | 409 | Conflict    | Order does not belong to this patient.
            7 duplicate type for the order              | OWNER   | BLOOD  | OWNED   | 409 | Conflict    | A sample of this type already exists for this order.
            8 control: nothing wrong, accepted          | OWNER   | TISSUE | OWNED   | 201 | Created     | -
            """)
    void whenSeveralThingsAreWrongOnlyTheFirstProblemInTheSpecifiedOrderIsReported(
            String caseName, String patient, String type, String order, int expectedStatus, String expectedError,
            String expectedMessage) throws Exception {
        String orderId = read(mockMvc.perform(keyedPost("/api/orders", "{\"patientId\": \"PAT-1001\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        registerSample("PAT-1001", "BLOOD", orderId);
        int samplesBefore = sampleRepository.findAll().size();

        String body = "{\"patientId\": \"" + switch (patient) {
                    case "BLANK" -> "";
                    case "UNKNOWN" -> "PAT-" + UUID.randomUUID();
                    case "OTHER" -> "PAT-1002";
                    default -> "PAT-1001";
                }
                + "\", \"sampleType\": \"" + (type.equals("BLANK") ? "" : type)
                + "\", \"orderId\": \"" + (order.equals("UNKNOWN") ? "ORD-" + UUID.randomUUID() : orderId) + "\"}";

        var result = mockMvc.perform(keyedPost("/api/samples", body));

        if (expectedStatus == 201) {
            result.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACCESSIONED"));
            assertThat(sampleRepository.findAll()).hasSize(samplesBefore + 1);
            mockMvc.perform(withKey(get("/api/orders/" + orderId)))
                    .andExpect(jsonPath("$.samples.length()").value(2));
        } else {
            expectError(result, expectedStatus, expectedError, expectedMessage, "/api/samples");
            assertThat(sampleRepository.findAll()).hasSize(samplesBefore);
        }
    }
}
