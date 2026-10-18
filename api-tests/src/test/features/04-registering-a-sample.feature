@api @regression
Feature: 04. Registering a sample
  The lab registers a sample for a patient, optionally under an order, and every new sample starts at
  Accessioned. A request that is missing something, names something unknown, or repeats a sample type
  within one order is refused.

  @accepted
  Scenario: A sample can be looked up and is listed
    A sample registered under an order can be looked up and is listed by its order and its patient.

    Given the lab holds a registered patient
    And the patient has an order
    When a caller registers a blood sample for the patient under the order
    And the caller looks the sample up by the id the lab gave it
    Then the lab returns the same sample, for the patient, as a blood sample under the order
    And the order lists only that sample, and the lab lists only that sample for the patient

  @accepted @smoke
  Scenario: A new sample starts at Accessioned
    A newly registered sample starts at the Accessioned stage, both in the answer and when looked up.

    Given the lab holds a registered patient
    When a caller registers a tissue sample for the patient
    And the caller looks the sample up by the id the lab gave it
    Then the sample is at Accessioned, both as registered and as looked up

  @accepted
  Scenario: A second sample type in an order is accepted
    A tissue sample is accepted under an order that already holds a blood sample, so the duplicate rule is
    about the type.

    Given the lab holds a registered patient
    And the patient has an order that already holds a blood sample
    When a caller registers a tissue sample for the patient under the order
    Then the lab accepts it as a new tissue sample under the same order, and lists both samples for the patient

  @refused
  Scenario Outline: <name>
    A sample request that is missing something or names something unknown (a patient the lab does not
    know, or the unsupported sample type PLASMA) is refused, and no sample is created.

    Given the lab holds <holds>
    When a caller registers a sample <case>
    Then the lab refuses the request as <refusal>, saying <message>
    And the lab does not report anything as created
    And afterwards the patient still has no samples

    Examples:
      | name                                       | case                                        | holds                | refusal       | message                          |
      | A sample without a patient is refused      | without a patient                           | nothing yet          | invalid input | a patient is required            |
      | A sample for an unknown patient is refused | for a patient the lab does not know         | nothing yet          | not found     | the patient was not found        |
      | A sample without a sample type is refused  | without a sample type                       | a registered patient | invalid input | a sample type is required        |
      | A sample of an unsupported type is refused | of a type the lab does not support (PLASMA) | a registered patient | invalid input | the sample type is not supported |

  @refused
  Scenario Outline: A second <type> sample in an order is refused
    A second sample of a type the order already holds is refused, and the order still holds only the first
    one.

    Given the lab holds a registered patient
    And the patient has an order that already holds a <type> sample
    When a caller registers a second <type> sample under the same order
    Then the lab refuses the request as a conflict, saying the order already holds a sample of that type
    And the lab does not report anything as created
    And the order and the patient still hold only the first sample

    Examples:
      | type   |
      | blood  |
      | tissue |
      | swab   |
      | urine  |
