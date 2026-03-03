# Contributing to Echo Kotlin SDK

Thank you for your interest in contributing! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Code Style](#code-style)
- [Commit Conventions](#commit-conventions)
- [Pull Request Process](#pull-request-process)
- [Reporting Issues](#reporting-issues)
- [Architecture Guidelines](#architecture-guidelines)

---

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). By participating, you are expected to uphold this code. Please report unacceptable behavior by opening an issue.

---

## Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/echo-android.git
   cd echo-android
   ```
3. **Create a branch** for your work:
   ```bash
   git checkout -b feature/your-feature-name
   ```

---

## Development Setup

### Prerequisites

- **Android Studio** Ladybug (2024.2+) or newer
- **JDK 11+**
- **Android SDK** with API 36 installed

### Building

```bash
# Build the library
./gradlew :core:assemble

# Build everything (library + sample app)
./gradlew assemble
```

### Running Tests

```bash
# Unit tests
./gradlew :core:testDebugUnitTest

# Unit tests with coverage report
./gradlew :core:createDebugUnitTestCoverageReport
# View report at: core/build/reports/coverage/test/debug/index.html

# All tests (core + sample)
./gradlew testDebugUnitTest
```

### Linting

The project uses [ktlint](https://pinterest.github.io/ktlint/) for Kotlin code style enforcement.

```bash
# Check for violations
./gradlew :core:ktlintCheck :sample:ktlintCheck

# Auto-format violations
./gradlew :core:ktlintFormat :sample:ktlintFormat
```

---

## Code Style

### General Rules

- **Kotlin only** — no Java source files
- **Immutability** — prefer `val` over `var`; use `copy()` for state updates
- **Visibility** — default to `internal`; only use `public` for intended consumer API
- **Trailing commas** — mandatory for multi-line parameter lists and collections
- **Expression bodies** — use `= ...` for single-expression functions
- **No `GlobalScope`** — always use structured concurrency

### Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Classes/Interfaces | `PascalCase` | `EchoClient`, `EchoEngine` |
| Functions/Properties | `camelCase` | `connect()`, `socketId` |
| Constants | `UPPER_SNAKE_CASE` | `DEFAULT_PORT` |
| Backing properties | `_camelCase` | `_connectionState` |

### Architecture Rules

- **Clean Architecture** — `domain` must not depend on `data` or `presentation`
- **No DTOs in presentation** — always map to domain models at the repository boundary
- **`implementation` only** — never use `api(...)` in Gradle unless a type is part of the public API surface
- **Serialization** — use `kotlinx.serialization` exclusively (no Gson/Moshi/Jackson)

### ktlint

All code must pass `ktlintCheck` before merge. The project enforces the Kotlin coding conventions style. Run `./gradlew ktlintFormat` to auto-fix most issues.

---

## Commit Conventions

This project follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).

### Format

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation changes only |
| `style` | Formatting, missing semicolons, etc. (no code change) |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `perf` | Performance improvement |
| `test` | Adding or correcting tests |
| `build` | Changes to build system or dependencies |
| `ci` | Changes to CI configuration |
| `chore` | Maintenance tasks |

### Scopes

- `sdk` — core library changes
- `sample` — sample app changes
- `deps` — dependency updates
- `docs` — documentation

### Examples

```
feat(sdk): add presence channel member tracking
fix(sdk): resolve reconnection race condition on API 30
docs: update README with presence channel usage
build(deps): upgrade Ktor to 3.4.1
test(sdk): add EchoEngine edge case tests
```

---

## Pull Request Process

1. **Ensure your branch is up-to-date** with `main`/`master`:
   ```bash
   git fetch origin
   git rebase origin/master
   ```

2. **Run all checks locally** before pushing:
   ```bash
   ./gradlew :core:assemble :core:testDebugUnitTest :core:ktlintCheck
   ```

3. **Open a Pull Request** with:
   - A clear title following commit conventions
   - A description of **what** changed and **why**
   - Links to any related issues

4. **Review criteria:**
   - All CI checks pass (build, tests, lint)
   - Code coverage does not decrease below 80%
   - No new `api(...)` dependencies without justification
   - Public API changes are intentional and documented
   - KDoc is present on all new public APIs

5. **After approval**, the maintainer will squash-merge your PR.

---

## Reporting Issues

### Bug Reports

Please include:

- **Device / emulator** and Android API level
- **Echo SDK version** (or commit hash)
- **Steps to reproduce** the issue
- **Expected behavior** vs. **actual behavior**
- **Logs / stack traces** if available

### Feature Requests

Please include:

- **Use case** — what problem are you trying to solve?
- **Proposed solution** — how would you like it to work?
- **Alternatives considered** — any other approaches you've thought of?

---

## Architecture Guidelines

Before contributing new features, please understand the project architecture:

```
core/
├── Echo.kt              # Entry point & DSL builder
├── EchoClient.kt        # Public client interface
├── auth/                 # Authenticator contracts
├── channel/              # Channel interfaces
├── connection/           # Connection state machine & reconnection
├── data/protocol/        # Pusher wire protocol models
├── engine/               # Pluggable WebSocket engine
├── error/                # Typed error hierarchy
├── internal/             # All implementations (internal visibility)
├── serialization/        # Pluggable serializer
├── state/                # ConnectionState & ChannelState
└── utils/                # Logger utilities
```

### Key Principles

1. **Interfaces live in public packages** (`channel/`, `engine/`, `auth/`, etc.)
2. **Implementations live in `internal/`** with `internal` visibility
3. **State is exposed as `StateFlow`** — never mutable state directly
4. **Errors flow through `SharedFlow<EchoError>`** — never thrown
5. **Engine and Serializer are pluggable** — consumers can replace defaults

---

## Questions?

If you have questions about contributing, feel free to open an issue with the `question` label.

Thank you for helping make the Echo Kotlin SDK better! 🎉
