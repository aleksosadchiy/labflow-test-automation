@api @regression
Feature: 03. Orders
  The lab places an order for a patient it knows. An order can be looked up again, and a request that
  names no patient, or one the lab has never seen, is refused.

  @accepted
  Scenario: A placed order can be looked up
    A placed order can be looked up for its patient, with a creation time and no samples yet.

    Given the lab holds a registered patient
    When a caller places an order for the patient
    And the caller looks the order up by the id the lab gave it
    Then the lab returns the order for that patient, with a creation time and no samples

  @refused
  Scenario Outline: <name>
    An order request that is missing the patient or names an unknown one is refused.

    Given the lab holds nothing yet
    When a caller places an order for <case>
    Then the lab refuses the request as <refusal>, saying <message>
    And the lab does not report anything as created

    Examples:
      | name                                | case                            | refusal       | message                   |
      | An order for no patient is refused  | no patient                      | invalid input | a patient is required     |
      | An order for an unknown patient is refused | a patient the lab does not know | not found     | the patient was not found |
