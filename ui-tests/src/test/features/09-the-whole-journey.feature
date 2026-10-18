@ui @regression
Feature: 09. The whole journey
  A sample is registered on screen, taken through every stage with Advance and ends in its finished
  report. Every screen on the way shows the sample that was registered.

  @accepted @smoke
  Scenario: A sample is followed to its finished report
    Following a sample through every screen from registration to Report Generated shows its finished report.

    Given the visitor is signed in
    And the lab holds a registered patient
    When the visitor registers a blood sample for the patient on the Create Sample screen
    And the visitor advances the sample on the Details screen until Advance is no longer offered
    Then the sample needed 7 advances to reach the final stage
    When the visitor opens the report from the Details screen
    Then the Report screen shows the finished report of the sample registered:
      | shown on the Report screen | holds                                    |
      | sample id                  | the id of the sample registered on screen |
      | test name                  | a test name                              |
      | result value               | a result value                           |
      | generation date            | the date the report was generated        |
