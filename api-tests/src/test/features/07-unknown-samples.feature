@api @regression
Feature: 07. Unknown samples
  Whatever is asked about a sample the lab has never heard of, the lab answers that the sample was not
  found. Nothing is created or changed by such a request.

  @refused
  Scenario Outline: <case> is refused
    Any request about a sample the lab does not know is answered "Sample not found."
    The move asks for the valid next stage, so the only thing wrong with it is that the sample does not exist.

    Given the lab holds nothing yet
    When a caller asks the lab to <request> a sample it does not know
    Then the lab refuses the request as not found, saying the sample was not found

    Examples:
      | case                                   | request                  |
      | Looking up an unknown sample           | look up                  |
      | Moving an unknown sample on            | advance                  |
      | Progress of an unknown sample          | show the progress of     |
      | Result of an unknown sample            | give the result of       |
      | Report of an unknown sample            | give the report of       |
