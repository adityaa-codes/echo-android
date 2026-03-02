# Commit Skills

This file defines the skills available for AI agents working with the Echo Android SDK to ensure high-quality, atomic, and conventional commits.

## Skill: commit-atomic

**Description:** Analyzes the current workspace changes and creates one or more atomic, conventional commits.
**Instructions:**
1.  **Analyze Status:** Run `git status` to see all modified, staged, and untracked files.
2.  **Analyze Diffs:** Run `git diff` (for unstaged) and `git diff --staged` (for staged) to understand the *precise* nature of the changes.
3.  **Identify Logical Units:** Group the changes into "atomic" units.
    *   *Atomic:* A commit should do one thing (e.g., "Fix bug A", "Add feature B"). It should not mix a bug fix with a refactor or a formatting change.
    *   *Splitting:* If a file contains changes for two different logical units, use `git add -p` (patch mode) to stage only the relevant hunks for the current commit.
4.  **Formulate Message:** For each atomic unit, write a commit message following the **Conventional Commits** standard:
    *   **Format:** `<type>(<scope>): <description>`
    *   **Types:**
        *   `feat`: A new feature (corresponds to MINOR in SemVer).
        *   `fix`: A bug fix (corresponds to PATCH in SemVer).
        *   `docs`: Documentation only changes.
        *   `style`: Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc).
        *   `refactor`: A code change that neither fixes a bug nor adds a feature.
        *   `perf`: A code change that improves performance.
        *   `test`: Adding missing tests or correcting existing tests.
        *   `chore`: Changes to the build process or auxiliary tools and libraries such as documentation generation.
    *   **Scope:** The specific part of the codebase (e.g., `connection`, `auth`, `channel`, `protocol`).
    *   **Description:** Concise, imperative mood (e.g., "add retry logic" not "added retry logic").
    *   **Body (Optional):** detailed explanation of *why* the change was made, if necessary.
    *   **Footer (Optional):** "BREAKING CHANGE: <description>" or "Closes #123".
5.  **Execute:**
    *   Stage the files/hunks for the first unit: `git add <file>` or `git add -p`.
    *   Commit: `git commit -m "type(scope): description"`
    *   Repeat for remaining units until `git status` is clean.
6.  **Verify:** Run `git log -n <count>` to verify the history looks correct and linear.

**Example Scenario:**
*   *Changes:* Modified `EchoClient.kt` (logic fix) and `README.md` (typo fix).
*   *Action:*
    1.  `git add README.md`
    2.  `git commit -m "docs(readme): fix typo in usage example"`
    3.  `git add app/echo/src/main/java/.../EchoClient.kt`
    4.  `git commit -m "fix(client): resolve connection race condition"`

**Anti-Patterns:**
*   Committing `docs` and `fix` in the same commit.
*   Using vague messages like "updates" or "fixed stuff".
*   Committing broken code (atomic commits must compile).
