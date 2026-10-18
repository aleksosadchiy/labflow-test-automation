@ui @regression
Feature: 01. Getting access
  The lab's screens are only for people who have signed in. The Sign In screen lets the lab's valid
  details through, turns anything else away, and any other screen sends a signed-out visitor back to
  Sign In.

  @accepted @smoke
  Scenario: Valid details open the Sample List screen
    Signing in with the lab's valid details opens the Sample List screen.

    Given the visitor opens the Sign In screen
    When the visitor signs in with the lab's valid details
    Then the Sample List screen is shown

  @refused
  Scenario: A wrong password is refused
    Signing in with a wrong password is refused, and the Sign In screen stays.

    Given the visitor opens the Sign In screen
    When the visitor signs in with the right username and a wrong password
    Then the Sign In screen says "Invalid username or password."
    And the Sign In screen is still shown

  @refused
  Scenario Outline: <case> is refused
    Submitting the Sign In screen with the username or the password left blank is refused.

    Given the visitor opens the Sign In screen
    When the visitor submits the Sign In screen with the <field> left blank
    Then the Sign In screen says "Username and password are required."

    Examples:
      | case                | field    |
      | A blank username    | username |
      | A blank password    | password |

  @refused
  Scenario: Sample List without signing in shows Sign In
    Opening the Sample List screen without signing in shows the Sign In screen instead.

    Given the visitor has not signed in
    When the visitor opens the Sample List screen
    Then the Sign In screen is shown instead
