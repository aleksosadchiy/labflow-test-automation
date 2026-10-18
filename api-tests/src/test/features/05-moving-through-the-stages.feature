@api @regression
Feature: 05. Moving through the stages
  The lab moves a sample forward one stage at a time, from Accessioned to Report Generated, and keeps a
  record of when each stage was reached. Any other move, whether skipping ahead, going back, repeating
  the current stage or moving on from the final one, is refused and the sample stays where it is.

  @accepted
  Scenario: A sample moves through all eight stages
    A sample moves through all seven remaining stages one at a time, and its progress lists all eight in
    order.

    Given the lab holds a sample at Accessioned
    When a caller moves the sample one stage at a time through these stages:
      | from                 | to                   |
      | Accessioned          | Ready for Processing |
      | Ready for Processing | Processing           |
      | Processing           | Sequencing           |
      | Sequencing           | Bioinformatics       |
      | Bioinformatics       | Quality Check        |
      | Quality Check        | Result Ready         |
      | Result Ready         | Report Generated     |
    Then every move was accepted for the same sample, which was then at the stage asked for
    And the sample is at Report Generated, and its progress lists all eight stages in order, each with the time it was reached

  @refused
  Scenario Outline: <case>
    A move to any stage other than the next one is refused, and the sample's stage is unchanged: skipping
    ahead (Accessioned straight to Processing), going back (Processing to Accessioned), repeating the
    current stage (Processing to Processing) and moving on from the final stage.

    Given the lab holds a sample at <from>
    When a caller asks the lab to move the sample to <to>
    Then the lab refuses the request as a conflict, saying a sample cannot move from <from> to <to>
    And afterwards the sample is still at <from>

    Examples:
      | case                                       | from             | to          |
      | Skipping a stage is refused                | Accessioned      | Processing  |
      | Going back to an earlier stage is refused  | Processing       | Accessioned |
      | Repeating the current stage is refused     | Processing       | Processing  |
      | Moving on from the final stage is refused  | Report Generated | Accessioned |
