@ui @regression
Feature: 06. Results and reports
  A sample's report is available on the Report screen from the stage Report Generated on. Asked for
  earlier, the screen says the report is not ready yet.

  @refused
  Scenario: An early report shows it is not ready
    Opening the report of a sample that has not reached Report Generated shows "Report is not ready yet."

    Given the lab holds a sample at Processing
    And the visitor is signed in
    When the visitor opens the Report screen of the sample
    Then the Report screen says "Report is not ready yet."
