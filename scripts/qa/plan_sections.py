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
CASE = re.compile(r"- \*\*([A-Za-z0-9]+-\d+)\b")


def main():
    if not sys.argv[1:]:
        sys.exit(__doc__)
    lines = open(PLAN).read().splitlines()
    headings = []  # (line number, level, title), leaving out lines in code blocks
    in_code = False
    for number, line in enumerate(lines):
        if line.lstrip().startswith("```"):
            in_code = not in_code
        heading = None if in_code else re.match(r"(#+) (.*)", line)
        if heading:
            headings.append((number, len(heading.group(1)), heading.group(2).casefold()))
    # The numbers of the lines to print, which come out in the plan's order, each once.
    shown = set()
    missing = []
    for arg in sys.argv[1:]:
        wanted = arg.casefold()
        if CASE_ID.fullmatch(arg):
            starts = [n for n, line in enumerate(lines) if CASE.match(line)
                      and CASE.match(line).group(1).casefold() == wanted]
            if not starts:
                missing.append(arg)
                continue
            # A case runs to the next line that isn't indented under it.
            start = starts[0]
            end = next((n for n in range(start + 1, len(lines)) if not lines[n].startswith(" ")), len(lines))
            suite = max(n for n, _, _ in headings if n < start)
            first_case = next(n for n in range(suite, len(lines)) if CASE.match(lines[n]))
            shown.update(range(suite, first_case))
            shown.update(range(start, end))
        else:
            sections = [i for i, (_, _, title) in enumerate(headings) if title.startswith(wanted)]
            if not sections:
                missing.append(arg)
            for i in sections:
                start, level, _ = headings[i]
                end = next((n for n, deeper, _ in headings[i + 1:] if deeper <= level), len(lines))
                shown.update(range(start, end))
    if shown:
        print("\n".join(lines[n] for n in sorted(shown)))
    if missing:
        sys.exit("no section starts with, and no case is: " + ", ".join(missing))


if __name__ == "__main__":
    main()
