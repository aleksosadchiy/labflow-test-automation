@api @regression
Feature: 08. Malformed and unsupported requests
  The lab refuses a request whose content it cannot read, and an action it does not offer on a sample.
  Nothing is created or removed by such a request.

  @refused
  Scenario Outline: <name>
    A request to register a sample whose content the lab cannot read (it stops halfway, is plain text or
    holds nothing) is refused as unreadable, that is malformed, and no sample is created. The wording of the broken request is never shown, only the kind of break.

    Given the lab holds a registered patient
    When a caller sends the lab a sample request <content>
    Then the lab refuses the request as invalid input, saying the content of the request could not be read
    And the lab does not report anything as created
    And afterwards the patient still has no samples

    Examples:
      | name                                 | content            |
      | A cut-off sample request is refused  | that stops halfway |
      | A plain-text sample request is refused | in plain text    |
      | An empty sample request is refused   | with nothing in it |

  @refused
  Scenario: A plain-text patient request is refused
    A request to register a patient whose content the lab cannot read is refused as malformed, and no
    patient comes back.

    Given the lab holds nothing yet
    When a caller sends the lab a patient request in plain text
    Then the lab refuses the request as invalid input, saying the content of the request could not be read
    And the lab does not report anything as created
    And the lab returns nothing for the request

  @refused
  Scenario: Deleting a sample is not offered
    Deleting a sample is not offered: it is refused as not allowed, the answer names reading as an allowed action, and the
    sample is still held at the stage it was.

    Given the lab holds a sample at Accessioned
    When a caller asks the lab to delete the sample
    Then the lab refuses the request as not allowed, saying that action is not allowed
    And the lab lists reading the sample as an allowed action
    And the lab still holds the sample, at Accessioned
