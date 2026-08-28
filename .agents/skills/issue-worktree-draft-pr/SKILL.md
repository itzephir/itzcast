---
name: issue-worktree-draft-pr
description: Turn a described repository problem into a user-reviewed GitHub issue, implement it in an isolated worktree branched from main, and publish a draft pull request. Use when the user requests the complete issue-to-PR workflow; do not use for issue-only, implementation-only, or review-only requests.
---

# Issue to Draft PR

Run one ticket from problem definition through a draft PR while keeping the primary checkout untouched.

## Establish the task

- Read the repository instructions, contribution guide, issue/PR templates, current branch and worktree state, and relevant code before drafting anything.
- Confirm the GitHub repository and that `main` is the intended base. If either cannot be discovered safely, ask the user.
- Use the problem supplied by the user or an already identified current ticket. Do not silently substitute an unrelated improvement. If no concrete problem or current ticket can be discovered, propose a narrowly evidenced problem and make that assumption explicit in the review request.
- Search open and closed issues and pull requests for duplicates before proposing a new issue.

## Mandatory issue review

Draft the exact GitHub issue title and body. The body should state the observed problem, why it matters, the desired outcome, and testable acceptance criteria; add reproduction details or technical context only when evidenced.

Show the complete draft to the user and explicitly ask for approval or edits. This is a hard pause:

- Do not create the issue, branch, worktree, commits, pushes, or PR before the user unambiguously approves the issue draft.
- General authorization to run the workflow is not approval of a draft the user has not seen.
- After requested edits, show the complete revised draft and obtain approval again.

## Create the ticket and isolated worktree

After approval:

1. Recheck for duplicates and create the issue with the approved title and body. Preserve its number and URL.
2. Fetch the remote and verify the base ref. Do not rewrite, clean, or switch the user's primary checkout.
3. Create a dedicated branch from the latest `origin/main`, following repository naming rules and including the issue number where practical. Create a separate worktree for that branch outside the primary checkout.
4. If the intended branch or worktree already exists, inspect and reuse it only when it clearly belongs to the same issue; otherwise stop and report the collision.

Keep all implementation files, generated files, commits, and verification activity inside the dedicated worktree. Never discard unrelated user changes.

## Implement and verify

- Re-read repository instructions from the worktree and implement only the approved issue scope.
- Add or update tests appropriate to the change and run the repository-prescribed checks. Diagnose failures; do not hide or weaken unrelated checks.
- Review the diff for scope, secrets, build artifacts, and accidental edits.
- Commit according to repository policy, including DCO sign-off or cryptographic signing when required. Do not bypass hooks or signing requirements.

## Publish the draft PR

Push the ticket branch and open a draft PR targeting `main`. The PR body must:

- link the issue with the repository's closing syntax, such as `Closes #123`;
- summarize the implemented outcome;
- list the verification performed and any known limitation.

Confirm from GitHub that the PR is a draft and targets `main`. Report the issue URL, worktree path, branch, checks run, commit, and draft PR URL. Leave the worktree in place unless the user explicitly asks to remove it.

## Failure boundaries

- Prefer idempotent inspection before every GitHub mutation so retries do not create duplicate issues or PRs.
- Authentication, permissions, protected-branch rules, signing failures, and unresolved test failures are blockers to report, not safeguards to bypass.
- Creating an issue, pushing a branch, and opening a PR are separate external mutations; stop after a failure and preserve enough state for a safe retry.
