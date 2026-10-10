#!/usr/bin/env python3
"""Prints sections of the QA test plan by heading, so a tester reads only what it needs.

    scripts/qa/plan_sections.py ONB HOME "The seed" "Known issues" REQ-4 REQ-9

Each name matches a heading that starts with it, ignoring case, such as "ONB" for
"### ONB: Onboarding and profile". A section runs to the next heading of the same or a
higher level. A case's ID, such as REQ-4, prints that case, after its suite's heading and
what the suite says before its first case, such as where to start.
"""

import os
import re
import sys

PLAN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "qa-test-plan.md")
CASE_ID = re.compile(r"[A-Za-z0-9]+-\d+")


def main():
    if not sys.argv[1:]:
        sys.exit(__doc__)
    case_ids = [arg.casefold() for arg in sys.argv[1:] if CASE_ID.fullmatch(arg)]
    names = [arg.casefold() for arg in sys.argv[1:] if not CASE_ID.fullmatch(arg)]
    lines = open(PLAN).read().splitlines()
    found = set()
    printing_level = None
    in_code = False
    # The heading and the lines before the first case of the section being read.
    suite, intro, in_intro, suite_printed = None, [], False, False
    printing_case = False
    for line in lines:
        if line.lstrip().startswith("```"):
            in_code = not in_code
        heading = None if in_code else re.match(r"^(#+) (.*)", line)
        case = None if in_code else re.match(r"^- \*\*(\S+)", line)
        if heading:
            level, title = len(heading.group(1)), heading.group(2).casefold()
            if printing_level is not None and level <= printing_level:
                printing_level = None
            matches = [name for name in names if title.startswith(name)]
            # A match inside a section already printing is found too, though it starts nothing new.
            found.update(matches)
            if matches and printing_level is None:
                printing_level = level
            suite, intro, in_intro, suite_printed = line, [], True, False
        elif case:
            in_intro = False
            case_id = case.group(1).casefold()
            if case_id in case_ids:
                found.add(case_id)
            printing_case = case_id in case_ids and printing_level is None
            if printing_case and not suite_printed:
                print("\n".join([suite] + intro))
                suite_printed = True
        elif not line.startswith(" "):
            printing_case = False
            if in_intro:
                intro.append(line)
        if printing_level is not None or printing_case:
            print(line)
    missing = [arg for arg in names + case_ids if arg not in found]
    if missing:
        sys.exit("no section starts with, and no case is: " + ", ".join(missing))


if __name__ == "__main__":
    main()
