#!/usr/bin/env python3
"""Prints sections of the QA test plan by heading, so a tester reads only what it needs.

    scripts/qa/plan_sections.py ONB HOME "The seed" "Known issues"

Each name matches a heading that starts with it, ignoring case, such as "ONB" for
"### ONB: Onboarding and profile". A section runs to the next heading of the same or a
higher level.
"""

import os
import re
import sys

PLAN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "qa-test-plan.md")


def main():
    names = [name.casefold() for name in sys.argv[1:]]
    if not names:
        sys.exit(__doc__)
    lines = open(PLAN).read().splitlines()
    found = set()
    printing_level = None
    in_code = False
    for line in lines:
        if line.lstrip().startswith("```"):
            in_code = not in_code
        heading = None if in_code else re.match(r"^(#+) (.*)", line)
        if heading:
            level, title = len(heading.group(1)), heading.group(2).casefold()
            if printing_level is not None and level <= printing_level:
                printing_level = None
            matches = [name for name in names if title.startswith(name)]
            # A match inside a section already printing is found too, though it starts nothing new.
            found.update(matches)
            if matches and printing_level is None:
                printing_level = level
        if printing_level is not None:
            print(line)
    missing = [name for name in names if name not in found]
    if missing:
        sys.exit("no section starts with: " + ", ".join(missing))


if __name__ == "__main__":
    main()
