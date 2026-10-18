@ui @regression
Feature: 04. Registering a sample
  A sample is registered on the Create Sample screen for a patient the lab knows, and it then appears
  in the Sample List as Accessioned. The screen refuses anything it cannot register and says why.

  @accepted @smoke
  Scenario: A registered sample is listed as Accessioned
    A sample registered on screen for a known patient appears in the list as Accessioned.

    Given the visitor is signed in
    And the lab holds a registered patient
    When the visitor registers a blood sample for the patient on the Create Sample screen
    Then the Sample List shows the new sample for the patient as Accessioned

  @refused
  Scenario Outline: <case> is refused
    Submitting the Create Sample screen with the patient or the sample type left blank is refused.

    Given the visitor is signed in
    And the lab holds a registered patient
    And the visitor is on the Create Sample screen
    When the visitor submits the Create Sample screen with the <field> left blank
    Then the Create Sample screen says "<message>"

    Examples:
      | case                 | field       | message                  |
      | A blank patient      | patient     | Patient is required.     |
      | A blank sample type  | sample type | Sample type is required. |

  @refused
  Scenario Outline: <case> is refused
    Registering a sample for a patient the lab does not know, or of a type the lab does not support, is refused.

    Given the visitor is signed in
    And the visitor is on the Create Sample screen
    When the visitor submits the Create Sample screen with <patient> and <type>
    Then the Create Sample screen says "<message>"

    Examples:
      | case                            | patient                         | type                               | message              |
      | A sample for an unknown patient | a patient the lab does not know | a supported sample type            | Patient not found.   |
      | A sample of an unsupported type | a patient the lab knows         | the unsupported sample type PLASMA | Invalid sample type. |
