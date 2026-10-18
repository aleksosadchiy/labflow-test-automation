@api @regression
Feature: 06. Results and reports
  A sample's test result is available from the stage Result Ready on, and its report from Report
  Generated on. Asked for earlier, the lab refuses and says the result or report is not ready yet.

  @accepted
  Scenario: The result is available at Result Ready
    A sample's test result becomes available at Result Ready, with its sample id, test name and result value.

    Given the lab holds a swab sample at Result Ready
    When a caller asks the lab for the sample's result
    Then the sample was at Result Ready when it was asked
    And the lab returns the sample's result, with the sample's id, a test name and a result value

  @accepted
  Scenario: The report is available at Report Generated
    A sample's report becomes available at Report Generated, with sample id, test name, result value and generation time, and the earlier result still matches it.

    Given the lab holds a tissue sample at Report Generated
    When a caller asks the lab for the sample's report and then its result
    Then the sample was at Report Generated when it was asked
    And the lab returns the sample's report, with the sample's id, a test name, a result value and the time it was generated
    And the result the lab returns matches the report

  @refused
  Scenario Outline: <case> is refused
    A result or report asked for before its stage is refused with the matching "not ready yet" message.
    The two boundary cases are the result at Quality Check, one stage before Result Ready, and the report at
    Result Ready, one stage before Report Generated.

    Given the lab holds a sample at <stage>
    When a caller asks the lab for the sample's <asked>
    Then the lab refuses the request as a conflict, saying the <asked> is not ready yet

    Examples:
      | case                                  | stage            | asked  |
      | The result at Accessioned             | Accessioned      | result |
      | The result at Quality Check           | Quality Check    | result |
      | The report at Accessioned             | Accessioned      | report |
      | The report at Result Ready            | Result Ready     | report |
