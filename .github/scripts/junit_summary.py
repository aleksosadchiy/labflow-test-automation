#!/usr/bin/env python3
"""Turn Surefire JUnit XML reports into a short Markdown summary for a CI job.

Usage:
    junit_summary.py "<Title>" <reports-dir> [<job-start-epoch>]

Prints Markdown to stdout; the workflow appends it to $GITHUB_STEP_SUMMARY
(and echoes it to the log). It always exits 0: a summary must never be the
reason a job goes red - the Maven step already decides pass/fail.

Why an in-repo script instead of a marketplace action (e.g. dorny/test-reporter):
  * no `checks: write` permission needed - that permission is not granted on
    pull requests from forks, so check-run based reporters silently break there;
  * no third-party dependency to pin, trust and keep up to date;
  * ~80 lines of standard-library Python that can be read and explained in full.
"""
import glob
import os
import sys
import time
import xml.etree.ElementTree as ET

MAX_MESSAGE = 200  # keep table cells readable


def format_duration(seconds):
    """1m12s / 45s - compact, like the GitHub UI."""
    seconds = int(round(seconds))
    minutes, secs = divmod(seconds, 60)
    return f"{minutes}m{secs:02d}s" if minutes else f"{secs}s"


def cell(text):
    """Make text safe for one Markdown table cell: one line, no pipes, truncated."""
    lines = [line.strip() for line in (text or "").splitlines() if line.strip()]
    # AssertJ puts the check's `as("[Scenario: ...]")` description on line 1 and
    # "expected: X / but was: Y" on the next lines. The Check column already
    # shows the name, so drop that prefix and keep the expected-versus-actual.
    if len(lines) > 1 and lines[0].startswith("[") and lines[0].endswith("]"):
        lines = lines[1:]
    one_line = " ".join(lines).replace("|", "\\|")
    if len(one_line) > MAX_MESSAGE:
        one_line = one_line[:MAX_MESSAGE - 1] + "…"
    return one_line


def main(argv):
    title, reports_dir = argv[1], argv[2]
    job_start = int(argv[3]) if len(argv) > 3 and argv[3] else None

    files = sorted(glob.glob(os.path.join(reports_dir, "TEST-*.xml")))
    if not files:
        print(f"⚠️ **{title}**: no results — the lab did not start "
              "or the build failed before tests")
        return

    total = skipped = 0
    suite_time = 0.0
    # (check name, message). No class column: every lab check is a scenario
    # run through one suite class per module, so a class column would repeat
    # that one name on every row. The name is the scenario's own short name,
    # exactly what the reader types into the report's search box.
    failed = []
    for path in files:
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            continue  # a half-written report should not crash the summary
        suite_time += float(suite.get("time") or 0)
        # Count from the <testcase> elements themselves, so one rule covers
        # failures, errors and both spellings of "skipped" (skipped/disabled).
        for case in suite.iter("testcase"):
            total += 1
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                message = problem.get("message") or problem.text or problem.get("type")
                failed.append((cell(case.get("name")), cell(message)))
            elif case.find("skipped") is not None or case.find("disabled") is not None:
                skipped += 1

    passed = total - len(failed) - skipped
    duration = time.time() - job_start if job_start else suite_time
    took = format_duration(duration)

    if not failed:
        print(f"✅ **{title}**: {passed} passed, {skipped} skipped in {took}")
        return

    print(f"❌ **{title}**: {passed} passed, {len(failed)} failed, "
          f"{skipped} skipped in {took}")
    print()
    print("| Check | Message |")
    print("|---|---|")
    for name, message in failed:
        print(f"| {name} | {message} |")


if __name__ == "__main__":
    try:
        main(sys.argv)
    except Exception as exc:  # never fail the job because of the summary itself
        print(f"⚠️ **summary**: could not build the summary ({exc})")
    sys.exit(0)
