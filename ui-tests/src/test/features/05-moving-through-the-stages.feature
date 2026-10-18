@ui @regression
Feature: 05. Moving through the stages
  The Details screen shows where a sample stands among the eight stages and moves it on with Advance.
  At the final stage it offers the report instead.

  @accepted
  Scenario: A new sample shows Accessioned as current
    The Details screen of a newly registered sample shows Accessioned as the current stage, with all
    eight stages in order: Accessioned current and the other seven not yet reached.

    Given the lab holds a sample at Accessioned
    And the visitor is signed in
    When the visitor opens the Details screen of the sample
    Then the Details screen shows Accessioned as the current stage
    And the Details screen lists the stages in this order, each in this state:
      | stage                | state     |
      | Accessioned          | current   |
      | Ready for Processing | remaining |
      | Processing           | remaining |
      | Sequencing           | remaining |
      | Bioinformatics       | remaining |
      | Quality Check        | remaining |
      | Result Ready         | remaining |
      | Report Generated     | remaining |

  @accepted
  Scenario: Advance moves a new sample to the next stage
    Advancing a newly registered sample once moves it to Ready for Processing: Accessioned is
    completed, Ready for Processing is current and the remaining six are not yet reached.

    Given the lab holds a sample at Accessioned
    And the visitor is signed in
    When the visitor opens the Details screen of the sample
    And the visitor advances the sample on the Details screen
    Then the Details screen shows Ready for Processing as the current stage
    And the Details screen lists the stages in this order, each in this state:
      | stage                | state     |
      | Accessioned          | completed |
      | Ready for Processing | current   |
      | Processing           | remaining |
      | Sequencing           | remaining |
      | Bioinformatics       | remaining |
      | Quality Check        | remaining |
      | Result Ready         | remaining |
      | Report Generated     | remaining |

  @accepted
  Scenario: The final stage offers the report
    Once a sample reaches its final stage, the Details screen no longer offers Advance and offers
    View Report instead.

    Given the lab holds a sample at Result Ready
    And the visitor is signed in
    When the visitor opens the Details screen of the sample
    And the visitor advances the sample on the Details screen
    Then the Details screen shows Report Generated as the current stage
    And the Details screen does not offer Advance
    And the Details screen offers View Report
