@api @regression
Feature: 01. Getting access
  The lab only serves callers who present its access key. A caller with no key, or with a wrong one,
  is turned away, and the lab is left exactly as it was.

  @refused
  Scenario Outline: <case>
    A request that would create or change something (registering a patient, registering a sample, moving a sample on) is refused without the right key, and nothing changes.
    The lab says it is unauthorized, asks the caller to sign in with a bearer key, reports nothing as
    created, and the record the request was about is unchanged.

    Given the lab holds <holds>
    When a caller <caller> asks the lab to <request>
    Then the lab refuses the request as unauthorized, with its standard message and a request to sign in with a bearer key
    And the lab does not report anything as created
    And the lab returns nothing for the request
    And afterwards <afterwards>

    Examples:
      | case                                        | holds                   | caller                  | request                             | afterwards                         |
      | No key: registering a patient is refused    | nothing yet             | without an access key   | register a patient                  | no patient was issued              |
      | No key: registering a sample is refused     | a registered patient    | without an access key   | register a sample for the patient   | the patient still has no samples   |
      | No key: moving a sample on is refused       | a sample at Accessioned | without an access key   | move the sample to its next stage   | the sample is still at Accessioned |
      | Wrong key: registering a patient is refused | nothing yet             | with a wrong access key | register a patient                  | no patient was issued              |
      | Wrong key: registering a sample is refused  | a registered patient    | with a wrong access key | register a sample for the patient   | the patient still has no samples   |
      | Wrong key: moving a sample on is refused    | a sample at Accessioned | with a wrong access key | move the sample to its next stage   | the sample is still at Accessioned |

  @refused @smoke
  Scenario: No key: looking a sample up is refused
    Looking a sample up without an access key is refused too, and shows nothing of the sample.

    Given the lab holds a sample at Accessioned
    When a caller without an access key asks the lab to look the sample up
    Then the lab refuses the request as unauthorized, with its standard message and a request to sign in with a bearer key
    And the lab returns nothing for the request
