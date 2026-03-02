---
name: android-library-publisher
description: Guides the artifact publishing process for Android libraries using Maven Publish, managing Dokka, and semantic versioning. Use when publishing artifacts or configuring Maven/JitPack deployments.
---
# Android Library Publisher

## Overview
This skill outlines the process for packaging, documenting, and publishing an Android library artifact to a remote repository like Maven Central or JitPack.

## Best Practices & Guidelines
1. **Documentation Generation:** Use the **Dokka** plugin to generate comprehensive HTML KDoc documentation for the public API.
2. **Maven Publish Configuration:** Use the standard `maven-publish` plugin to configure publications. Ensure the POM file contains the correct `groupId`, `artifactId`, `version`, license, developer info, and SCM coordinates.
3. **Semantic Versioning:** Strictly follow SemVer (Major.Minor.Patch). Breaking changes to the public API must increment the Major version.
4. **Sources & Javadoc:** Always configure the publication to include `-sources.jar` and `-javadoc.jar` (via Dokka) artifacts alongside the `.aar`.
5. **Sample App Verification:** Ensure the `sample` module correctly consumes the library before publishing.

## Workflow
1. Verify semantic versioning is correct based on the CHANGELOG.
2. Check that the `maven-publish` block includes sources and Javadoc.
3. Ensure Dokka task runs successfully.
4. Provide instructions for publishing the artifact (e.g., `./gradlew publishToMavenLocal` for testing).