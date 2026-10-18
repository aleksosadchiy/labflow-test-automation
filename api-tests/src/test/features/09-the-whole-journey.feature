@api @regression
Feature: 09. The whole journey
  A patient, an order and a sample go through the lab from registration to the finished report. Every
  answer the lab gives on the way belongs to the sample that was registered.

  @accepted @smoke
  Scenario: A sample goes from registration to its report
    The whole journey works: patient, order, sample, every stage, result and report, all belonging to
    the sample registered.

    Given the lab holds a registered patient
    And the patient has an order
    When a caller registers a blood sample for the patient under the order
    Then the sample is under that order and at Accessioned
    When the caller takes the sample through every stage to Report Generated
    And the caller asks the lab for the sample's result, report and progress, and for the order
    Then the sample is the one registered, now at Report Generated
    And the lab's answers belong to the sample registered:
      | answer   | holds                                                |
      | result   | the sample's id                                      |
      | result   | a test name                                          |
      | result   | a result value                                       |
      | report   | the sample's id                                      |
      | report   | the test name of the result                          |
      | report   | the result value of the result                       |
      | report   | the time it was generated                            |
      | progress | the sample's id                                      |
      | progress | Report Generated as the current stage                |
      | order    | the patient's id                                     |
      | order    | that sample only, a blood sample at Report Generated |
    And the sample's progress lists these stages in order:
      | stage                |
      | Accessioned          |
      | Ready for Processing |
      | Processing           |
      | Sequencing           |
      | Bioinformatics       |
      | Quality Check        |
      | Result Ready         |
      | Report Generated     |
