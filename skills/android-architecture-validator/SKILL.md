---
name: android-architecture-validator
description: Validates Clean Architecture, MVVM, and UDF implementation in Android libraries. Use when reviewing code structure, domain logic, or Kotlin Coroutine/Flow usage.
---
# Android Architecture Validator

## Overview
This skill enforces strict adherence to Clean Architecture, Model-View-ViewModel (MVVM), and Unidirectional Data Flow (UDF) patterns within Android library projects.

## Best Practices & Guidelines
1. **Clean Architecture Strictness:** 
   - `domain` layer must not depend on `data` or `presentation`.
   - `presentation` (MVVM) must interact with `domain` strictly via Use Cases (Interactors).
   - `data` implements repositories defined in the `domain` layer.
2. **Unidirectional Data Flow (UDF):**
   - ViewModels must expose a single `StateFlow<ViewState>`.
   - The UI communicates with the ViewModel via a single `processIntent(intent: ViewIntent)` function.
   - Side effects (navigation, one-off alerts) are handled via a `SharedFlow<ViewEffect>`.
3. **Context Handling:** Avoid requiring `Context` in constructors. If necessary, use `context.applicationContext` to prevent memory leaks, unless specifically requiring an `Activity` context.
4. **Data Handling:** DTOs must be mapped to Domain models at the repository boundary before crossing into the domain or presentation layers.

## Workflow
1. Review package structures to ensure `domain` has no external framework dependencies.
2. Verify ViewModels use `StateFlow` and single `processIntent` methods.
3. Check repository implementations for proper DTO-to-Domain mapping.