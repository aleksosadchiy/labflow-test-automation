@ui @regression
Feature: 07. Unknown samples
  Whatever is asked about a sample the lab has never heard of, the lab answers that the sample was not
  found. On screen, the Details screen says so instead of showing a sample.

  @refused
  Scenario: An unknown sample shows it was not found
    Opening the details of a sample id the lab does not know shows "Sample not found."

    Given the visitor is signed in
    When the visitor opens the Details screen of a sample the lab does not know
    Then the Details screen says "Sample not found."
