@api @regression
Feature: 02. Patients
  The lab registers patients under a name and gives each one an id of its own. A patient can be looked up
  again by that id, and a request without a usable name is refused.

  @accepted
  Scenario: A registered patient can be looked up
    A registered patient can be looked up with its lab-assigned id and name.

    Given the lab holds nothing yet
    When a caller registers a patient with a name of its own
    And the caller looks the patient up by the id the lab gave it
    Then the lab returns the same id and the name the patient was registered with

  @refused
  Scenario Outline: <name>
    A patient cannot be registered without a usable name (none given, an empty one, or one of only blank spaces), and none is issued.

    Given the lab holds nothing yet
    When a caller registers a patient with <case>
    Then the lab refuses the request as invalid input, saying a patient name is required
    And the lab does not report anything as created

    Examples:
      | name                                       | case                        |
      | A patient with no name at all is refused   | no name at all              |
      | A patient with an empty name is refused    | an empty name               |
      | A patient with a spaces-only name is refused | a name of only blank spaces |
