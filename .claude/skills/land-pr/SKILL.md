---
name: land-pr
description: Make one PR for a GitHub issue, from a new branch to merged. Implement it, open the PR, run two xhigh /code-review rounds that post their findings to the PR, then squash-merge once CI passes, asking the developer every question that needs their call.
argument-hint: <issue-number>
disable-model-invocation: true
---

# One PR for an issue

The issue number, N below, is `$ARGUMENTS`. If that isn't a single issue number, ask for one. A leading `#` can be dropped without asking.

Make one PR for issue #N, from a new branch to merged, following CLAUDE.md's Git Development Workflow and Claude Code Behaviors. An issue can take more than one PR; this covers one. The merge at the end is automatic, so the questions you ask along the way are the developer's say in the change.

## When to ask

Ask with AskUserQuestion, recommended option first, whenever the answer is the developer's to give:

- The issue is unclear, or what it expects conflicts with PRD.md or ARCHITECTURE.md.
- The issue needs more than one PR: which part this one covers.
- Two or more reasonable approaches differ in a way the developer would care about.
- A change would add or change a row in PRD.md's Design decisions, or a decision in ARCHITECTURE.md.
- You'd decline a review finding about correctness, or fixing one would change behavior the issue didn't ask about.
- A test or CI failure isn't clearly caused by this change.

Don't ask what the code, the docs or a convention already answers. Ask when the question comes up, not in a batch at the end. Record each answer in the PR description, in the section it affects: for example, the approach chosen and why under `## What`.

## 1. Read the issue

1. `gh issue view N --comments`. Look for work already started: `gh pr list --state all --search '"(#N)" in:title'` for its PRs, and `git branch --list '*/N-*'` for local branches. If a PR is still open or a branch exists, ask whether to continue it. To continue, check out its branch and pick up at the first step not yet done: an open PR skips step 3, and its description's review sections show which rounds have run.
2. Read the tables of contents of ARCHITECTURE.md and PRD.md, then only the sections the issue touches.
3. Find the cause. If what's left of the issue is too much for one PR, propose a split and ask which part this PR covers.
4. Ask the questions it raises before writing code.

## 2. Implement

1. `git status --porcelain` prints nothing. If it prints anything, ask what to do with those changes rather than carrying them onto the new branch.
2. `git switch main && git pull --ff-only`, then `git switch -c fix/N-<short-slug>` for a bug, or `feature/N-<short-slug>` otherwise.
3. Write a test that fails for the issue's reason, then make it pass. Update PRD.md and ARCHITECTURE.md in the same change when a decision changes.
4. `./gradlew build` passes. CI's Build job runs the same command: unit tests, the coverage check, lint and Spotless. When the change touches `app/src/androidTest`, `./gradlew pixel6Api37DebugAndroidTest` passes too, as in CI's Instrumented tests job.
5. The coverage check only holds each class to 80%. If the change lowers coverage anyway, say so and why under `## Tests`, with numbers from `./gradlew createDebugUnitTestCoverageReport`.
6. For a change people can see or hear, check it on the emulator through a subagent that reports back in text. Also do the checks docs/toolchain.md asks for by hand: "Checking a release build" after adding or updating AGP, a library, a keep rule or code that uses reflection, and "Checking backup and restore" after changing the backup rules or where data is stored.

## 3. Open the PR

1. Commit, `git push -u origin HEAD`, then `gh pr create`.
2. Title: what now works, in plain words, ending in `(#N)`. For example, "Keep Badges' search field above the keyboard in landscape (#308)".
3. Body, as in recent PRs (`gh pr view 314` is a model): `Closes #N.` if this PR finishes the issue, or `Part of #N.` and what's left if it doesn't. Then what was wrong, and `## Cause`, `## What`, `## Tests` and, for the checks in step 2.6, `## Checked on the emulator`. The squash commit takes the PR's title and body, so write them for `main`'s history.

## 4. Two review rounds

Do this twice. Round 2 reviews the PR with round 1's fixes pushed.

1. Run the code-review skill with `xhigh --comment <PR number>`. It posts its findings to the PR.
2. Judge each finding by the state it describes, not only by its example: disproving the example doesn't make the state unreachable. Fix it, or decline it with a reason. Ask before declining a correctness finding.
3. `./gradlew build` passes, and the checks in step 2.6 are redone for anything the fixes changed. Commit and push.
4. Edit the PR description in place. Add `## After the /code-review` (round 1) or `## After the second /code-review` (round 2), with lists headed "Fixed:", "Not changed, by agreement:" and "Not changed:", each item with its reason.

## 5. Merge

1. Bring in `main`. The "Protect main" ruleset won't merge a PR that's behind it, and CI doesn't run while a PR conflicts with it. `git fetch origin`, and if `git merge-base --is-ancestor origin/main HEAD` fails, merge `origin/main` into the branch, run `./gradlew build` and push.
2. Wait for CI in a background command, since it can take longer than a foreground command may run: `gh pr checks <PR number> --watch --interval 60 > /dev/null`. Then read the result with `gh pr checks <PR number>`. If a check fails, fix it and push. If Instrumented tests hangs or fails with no cause in this change, rerun it once with `gh run rerun --failed <run id>`, and ask if it fails again.
3. Anything changed after round 2, such as a CI fix or a conflict resolved with `main`, goes in the PR description, since it becomes the commit message. If it changes production code beyond the trivial, ask whether to review again before merging.
4. Before merging, check that both review rounds are done and every finding is fixed or listed with its reason. Every question has an answer, and nothing is left that the developer should decide. If something is, ask now.
5. `gh pr merge <PR number> --squash --match-head-commit "$(git rev-parse HEAD)"`. It refuses if the PR's head isn't the local branch's, so no local commit is left out. If it refuses because `main` moved again, go back to step 5.1. GitHub deletes the remote branch.
6. `git switch main && git pull --ff-only && git branch -D <branch> && git fetch --prune`. Use `-D`, since a squash merge isn't an ancestor of `main`. The merge in step 5.5 already checked the branch had nothing unpushed.
7. Report the merged PR's URL, the questions asked with their answers, and what's left of the issue, if anything.
