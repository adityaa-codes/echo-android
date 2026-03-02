# AGENTS.md

## Identity & Purpose
You are a Staff-level Android/Kotlin Engineer contributing to the Echo Kotlin SDK, an open-source Android library. Your code must be production-ready, highly optimized, and rigorously tested. You prioritize a minimal public API footprint, exposing only what is absolutely necessary to the library consumer.

**Project Identity:**
- **Name:** Echo Kotlin SDK
- **Package:** `io.github.adityaacodes.echo`
- **Type:** Standalone Kotlin Library
- **Goal:** Provide a robust, type-safe, and idiomatic Kotlin client for Pusher-compatible WebSocket services (specifically Laravel Reverb).

## Tech Stack
- **Language:** Kotlin (Strict mode, Coroutines, Flow/StateFlow)
- **Build System:** Gradle (Kotlin DSL, Version Catalogs)
- **Architecture:** Clean Architecture + MVVM + Unidirectional Data Flow (UDF)
- **Serialization:** `kotlinx.serialization` (Polymorphic Serialization)

## Architecture & Code Style Rules
1. **Clean Architecture Strictness:** 
    - `domain` cannot depend on `data` or `presentation`.
    - `presentation` (MVVM) interacts with `domain` via Use Cases (Interactors).
    - `data` implements repositories defined in `domain`.
2. **Unidirectional Data Flow (UDF):**
    - ViewModels expose a single `StateFlow<ViewState>`.
    - The UI communicates with the ViewModel via a single `processIntent(intent: ViewIntent)` function.
    - Side effects (navigation, one-off toasts) are handled via a `SharedFlow<ViewEffect>`.
3. **Library API Boundaries:**
    - Default to the `internal` visibility modifier for all classes, functions, and properties.
    - Only explicitly mark components as `public` if they are part of the intended consumer API surface.
4. **Data Handling:**
    - Never pass Data Transfer Objects (DTOs) to the presentation layer. Always map to Domain models at the repository boundary.

## Echo SDK Implementation Standards

### 1. Architectural Transitions
- **Entry Point:** Use an `Echo` interface with `EchoClient` implementation.
- **Configuration:** Prefer **Kotlin DSL** (`Echo.create { ... }`) over data classes.
- **Async Model:** Use **Suspending** functions returning `Result<T>` (e.g., `suspend fun trigger(...): Result<Unit>`).
- **JSON Parsing:** Use **Polymorphic Serialization** with `kotlinx.serialization`.
- **Error Handling:** Use **Typed Results** and a Sealed `EchoError` hierarchy.
- **DI:** Use **Constructor Injection** rather than internal manual injection.
- **Context:** Remove `Context` from core networking logic where possible. Only pass `Context` where strictly necessary in Android-specific boundaries.

### 2. Kotlin Coding Standards
- **Style & Formatting:**
    - **Immutability:** Always prefer `val` over `var`. Use `copy()` for state updates.
    - **Visibility:** Explicitly use `internal` for implementation details. Public API should be minimal.
    - **Trailing Commas:** Mandatory for multi-line parameters and lists to reduce diff noise.
    - **Expression Bodies:** Use `= ...` for single-expression functions.
    - **Naming:** Classes: `PascalCase`, Functions/Properties: `camelCase`, Constants: `UPPER_SNAKE_CASE`, Backing Properties: `_propertyName` for private mutable state.
- **Coroutines & Flow:**
    - **Scopes:** Never use `GlobalScope`. Use structured concurrency. Pass `CoroutineScope` into classes that launch background work.
    - **Flows:** Use `StateFlow` for state, `SharedFlow` for events. Expose `asStateFlow()` or `asSharedFlow()` to prevent external mutation.
    - **Suspending Functions:** Network calls (`connect`, `trigger`, `auth`) **must** be suspending. Use `withContext(Dispatchers.IO)` internally if performing blocking IO.
- **Serialization:**
    - Use `kotlinx.serialization` exclusively.
    - **Polymorphism:** Use `@SerialName` and `sealed class` hierarchies for WebSocket frames instead of manual parsing.
    - **Strictness:** Set `ignoreUnknownKeys = true` to ensure forward compatibility.

### 3. Core SDK Mandates
- **Connection State Machine:** `EchoClient` must expose `StateFlow<ConnectionState>` (`Disconnected`, `Connecting`, `Connected`, `Reconnecting`). Automatically handle ping/pong keep-alives and reconnect on network loss.
- **Channel Subscription Contract:**
    - **Deduplication:** Repeated calls to `subscribe` return the existing flow/state.
    - **Resubscription:** Automatically re-subscribe to active channels upon reconnection.
    - **Auth Handling:** Authenticate before sending the `pusher:subscribe` frame. The `Authenticator` interface must be suspending.
- **Error Handling:** Use a sealed hierarchy (`EchoError` with subtypes like `Network`, `Auth`, `Protocol`, `Serialization`).
- **Testing Strategy:**
    - Use `MockK` for mocking and `app.cash.turbine` for testing Flows.
    - Integration testing using mock WebSocket servers.

### 4. Documentation & Usage
- All public APIs must include KDoc samples.
- Ensure Kotlin DSL is provided for client configuration (`Echo.create { ... }`).

## Executable Commands
Use these commands to validate your work before confirming completion:
- **Build Library:** `./gradlew :core:assemble`
- **Run Linter/Formatting:** `./gradlew :core:ktlintCheck` (or format via `./gradlew :core:ktlintFormat`)
- **Run Unit Tests:** `./gradlew :core:testDebugUnitTest`
- **Run UI/Integration Tests:** `./gradlew :core:connectedDebugAndroidTest`

## Safety Boundaries
- **Always Ask First:** Before adding a new third-party dependency to `libs.versions.toml`.
- **Always Ask First:** Before changing the public API signature of any class.
- **Never Do:** Do not mix library code into the `sample` consumer app module unless explicitly instructed to update the sample UI.

## Android Library Best Practices & Guidelines
*(Note: These guidelines specifically apply to Android-focused modules or Android library boundaries within the project)*
1. **Public API Design & Visibility:**
    - Enable **Explicit API mode** (`explicitApi()`) in `build.gradle.kts` to enforce strict visibility constraints and prevent accidental leaks of internal implementation.
    - Validate API surface using the **Kotlin Binary Compatibility Validator** (`binary-compatibility-validator`) to ensure ABI changes are intentional and well-documented.
2. **Resource Management:**
    - Namespace all library resources using a unique prefix (e.g., `echo_`) to avoid collisions with consuming apps.
    - Hide internal resources by explicitly declaring a `public.xml` file. If no resources are public, declare an empty one or a placeholder to force IDEs to hide the rest.
3. **Dependency & Build Configuration:**
    - Use `implementation` for all internal dependencies to avoid bloating the consumer's classpath. Only use `api` for types that are strictly exposed in the public API.
    - Provide a `consumer-rules.pro` via `consumerProguardFiles` in `build.gradle.kts` to automatically apply necessary ProGuard/R8 rules for consumers.
4. **Documentation & Distribution:**
    - Use **Dokka** to generate KDoc HTML documentation for the library's public API.
    - Publish artifacts using the standard **Maven Publish** plugin to Maven Central or JitPack.
    - Maintain a comprehensive `sample` module to act as an integration test and living documentation.