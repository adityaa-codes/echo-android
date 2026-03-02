---
name: android-dependency-manager
description: Validates dependencies for Android libraries, emphasizing implementation vs api usage and Version Catalogs. Use when adding, removing, or reviewing Gradle dependencies in Android library projects.
---
# Android Dependency Manager

## Overview
This skill ensures that Android library dependencies are managed securely and optimally without bloating the consumer's classpath.

## Best Practices & Guidelines
1. **`api` vs `implementation`:** Always prefer `implementation` to keep dependencies internal to the library. Only use `api` if the dependency's types are explicitly exposed in the library's public API signature.
2. **Version Catalogs:** Manage all dependencies centrally using `libs.versions.toml`. Do not hardcode versions in `build.gradle.kts`.
3. **AndroidX Migration:** Ensure all dependencies are AndroidX compatible. Never mix legacy support libraries.
4. **Consumer Rules:** Provide a `consumer-rules.pro` file via `consumerProguardFiles` to automatically supply required ProGuard/R8 rules to the consuming app.
5. **Safety Boundary:** Never add a new third-party dependency without explicitly asking the user or checking architectural guidelines first.

## Workflow
1. Review `build.gradle.kts` dependency blocks.
2. Convert unnecessary `api` declarations to `implementation`.
3. Verify that all versions are managed in `libs.versions.toml`.
4. Ensure `consumerProguardFiles` is configured if the library requires specific minification rules.