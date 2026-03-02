---
name: android-api-reviewer
description: Validates and reviews the public API surface of Android/Kotlin libraries. Use when checking Kotlin visibility, explicit API mode, or binary compatibility for Android libraries.
---
# Android API Reviewer

## Overview
This skill provides procedural knowledge for reviewing and validating the public API boundaries of an Android Library project. 

## Best Practices & Guidelines
1. **Explicit API Mode:** Always ensure that `explicitApi()` mode is enabled in the `build.gradle.kts` file. This prevents accidental exposure of internal implementation details.
2. **Minimize Surface Area:** By default, use the `internal` modifier for all Kotlin classes, functions, and properties. Only explicitly mark components as `public` if they are part of the intended consumer API.
3. **Binary Compatibility:** Use the `binary-compatibility-validator` tool. Before merging or committing changes that affect public APIs, ensure you run the validator to capture ABI dumps.
4. **Data Models:** Never expose Data Transfer Objects (DTOs) in the public API. Always map DTOs to Domain models at the repository boundary before exposing them to the presentation layer or consumer.

## Workflow
1. Check `build.gradle.kts` for `explicitApi()`.
2. Review Kotlin files for missing `public` or `internal` modifiers.
3. Validate ABI dumps via `apiCheck` or `apiDump` tasks if the binary compatibility plugin is applied.