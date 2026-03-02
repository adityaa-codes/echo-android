---
name: android-resource-manager
description: Manages Android library resources, ensuring prefixing, collision avoidance, and proper encapsulation via public.xml. Use when adding or reviewing XML resources (drawables, values) in Android libraries.
---
# Android Resource Manager

## Overview
This skill guides the correct management of Android XML resources within a library project to prevent namespace collisions and hide internal assets from the consuming application.

## Best Practices & Guidelines
1. **Resource Prefixing:** All resources (strings, colors, drawables, layouts) in the library must use a unique prefix (e.g., the library name or acronym like `echo_`) to avoid collisions with the consuming app's resources.
2. **Resource Privacy:** By default, all library resources are public. To hide them, you must define at least one resource in `res/values/public.xml`. 
   - If no resources are public, declare an empty `<resources>` block or a placeholder resource in `public.xml` to force the IDE to treat all other resources as private.
3. **Consumer ProGuard Rules:** If resources are accessed via reflection, ensure the appropriate keep rules are added to a `consumer-rules.pro` file, and that the file is specified in `consumerProguardFiles`.

## Workflow
1. Verify all new resource files or XML tags have the correct unique prefix.
2. Ensure `res/values/public.xml` exists.
3. Check that only intentionally exposed resources are listed in `public.xml`.