---
name: land-pr
description: Make one PR for a GitHub issue, from a new branch to merged. Implement it, open the PR, run two xhigh /code-review rounds that post their findings to the PR, then squash-merge once CI passes, asking the developer every question that needs their call.
argument-hint: <issue-number>
disable-model-invocation: true
---

# One PR for issue #$ARGUMENTS

Make one PR for issue #$ARGUMENTS, from a new branch to merged, following CLAUDE.md's Git Development Workflow and Claude Code Behaviors. An issue can take more than one PR; this covers one. The merge at the end is automatic, so the questions you ask along the way are the developer's say in the change.

## When to ask

Ask with AskUserQuestion, recommended option first, whenever the answer is the developer's to give:

- The issue is unclear, or what it expects conflicts with PRD.md or ARCHITECTURE.md.
- The issue needs more than one PR: which part this one covers.
- Two or more reasonable approaches differ in a way the developer would care about.
- A change would add or change a row in PRD.md's Design decisions, or a decision in ARCHITECTURE.md.
- You'd decline a review finding about correctness, or fixing one would change behavior the issue didn't ask about.
- A test or CI failure isn't clearly caused by this change.

Don't ask what the code, the docs or a convention already answers. Ask when the question comes up, not in a batch at the end, and keep each question and its answer: they go in the PR description.

## 1. Read the issue

1. `gh issue view $ARGUMENTS --comments`, and `gh pr list --state all --search "#$ARGUMENTS"` for PRs it already has. If one is still open, or a local branch for it exists, ask whether to continue it.
2. Read the tables of contents of ARCHITECTURE.md and PRD.md, then only the sections the issue touches.
3. Find the cause. If what's left of the issue is too much for one PR, propose a split and ask which part this PR covers.
4. Ask the questions it raises before writing code.

## 2. Implement

1. `git switch main && git pull --ff-only`, then `git switch -c fix/$ARGUMENTS-<short-slug>`.
2. Write a test that fails for the issue's reason, then make it pass. Update PRD.md and ARCHITECTURE.md in the same change when a decision changes.
3. `./gradlew build` passes. CI's Build job runs the same command: unit tests, the coverage check, lint and Spotless.
4. For a change people can see or hear, check it on the emulator through a subagent that reports back in text.

## 3. Open the PR

1. Commit, `git push -u origin HEAD`, then `gh pr create`.
2. Title: what now works, in plain words, ending in `(#$ARGUMENTS)`. For example, "Keep Badges' search field above the keyboard in landscape (#308)".
3. Body, as in recent PRs (`gh pr view 314` is a model): `Closes #$ARGUMENTS.` if this PR finishes the issue, or `Part of #$ARGUMENTS.` and what's left if it doesn't. Then what was wrong, and `## Cause`, `## What`, `## Tests` and, if you checked it, `## Checked on the emulator`. The squash commit takes the PR's title and body, so write them for `main`'s history.

## 4. Two review rounds

Do this twice. Round 2 reviews the PR with round 1's fixes pushed.

1. Run the code-review skill with `xhigh --comment <PR number>`. It posts its findings to the PR.
2. Judge each finding by the state it describes, not only by its example: disproving the example doesn't make the state unreachable. Fix it, or decline it with a reason. Ask before declining a correctness finding.
3. `./gradlew build` passes, and the emulator is rechecked if behavior changed. Commit and push.
4. Edit the PR description in place. Add `## After the /code-review` (round 1) or `## After the second /code-review` (round 2), with lists headed "Fixed:", "Not changed, by agreement:" and "Not changed:", each item with its reason.

## 5. Merge

Merge only when all of these hold:

- Both review rounds are done, and every finding is fixed or listed with its reason.
- Every question has an answer, and nothing is left that the developer should decide. If something is, ask now.
- `gh pr checks <PR number> --watch` shows Build and Instrumented tests passing. If a check fails, fix it and push. If Instrumented tests hangs or fails with no cause in this change, rerun it once with `gh run rerun --failed <run id>`, and ask if it fails again.
- If `main` has moved and the PR conflicts with it, merge `origin/main` into the branch, run `./gradlew build`, push, and wait for the checks again.

Then:

1. `gh pr merge <PR number> --squash`. GitHub deletes the remote branch.
2. `git switch main && git pull --ff-only && git branch -D <branch> && git fetch --prune`. Use `-D`, since a squash merge isn't an ancestor of `main`.
3. Report the merged PR's URL, the questions asked with their answers, and what's left of the issue, if anything.
