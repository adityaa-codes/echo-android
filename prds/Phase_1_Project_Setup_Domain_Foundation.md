# Echo Kotlin SDK - Phase 1: Project Setup & Domain Foundation

## 1. Document Metadata
*   **Status:** Draft
*   **Author(s):** Gemini CLI
*   **Stakeholders:** Engineering
*   **Date:** 2026-03-02
*   **Version:** 1.0.0

---

## 2. Context & Problem Statement
### Background
The Echo Kotlin SDK aims to provide a robust, type-safe Kotlin client for Pusher-compatible WebSocket services (like Laravel Reverb). Before building the networking logic, we must establish a strong architectural foundation that adheres to modern Kotlin standards (Clean Architecture, Explicit API mode).

### Opportunity
By defining strict interfaces and state machines upfront, we ensure the SDK is idiomatic and easy for Android developers to consume. It prevents coupling the core domain to specific network implementations.

---

## 3. Goals & Non-Goals
### Goals (What we WILL do)
*   Configure the Android/Kotlin library build system.
*   Define the core public interfaces (`EchoClient`, `EchoConnection`, `EchoChannel`, `PresenceChannel`, `Authenticator`).
*   Define sealed class hierarchies for states (`ConnectionState`, `ChannelState`) and errors (`EchoError`).
*   Create the configuration DSL builder (`Echo.create { ... }`).

### Non-Goals (What we WILL NOT do)
*   Implement actual WebSocket networking.
*   Implement JSON serialization logic.

---

## 4. User Stories
| ID | As an... | I want to... | So that... | Priority |
|:---|:---|:---|:---|:---|
| US.1 | Android Dev | use a Kotlin DSL to configure the SDK | I can easily set host and auth options | P0 |
| US.2 | Android Dev | observe typed connection states | I can build reactive UIs | P0 |
| US.3 | Android Dev | handle strongly-typed errors | I don't have to catch generic exceptions | P0 |

---

## 5. Functional Requirements
### 5.1 Configuration
*   **FR.1:** The SDK must provide an `Echo.create { }` builder block.
*   **FR.2:** The builder must accept host, app key, cluster, and authentication configurations.

### 5.2 Domain Models
*   **FR.3:** `ConnectionState` must support `Disconnected`, `Connecting`, `Connected`, and `Reconnecting` states.
*   **FR.4:** `EchoError` must strictly categorize failures into `Network`, `Auth`, `Protocol`, and `Serialization`.

---

## 6. Non-Functional Requirements
*   **Architecture:** Adhere strictly to the `GEMINI.md` standard (no KMP, strictly Kotlin/Android).
*   **Code Quality:** Must use explicit visibility (`explicitApi()`) to prevent leaking internal APIs.

---

## 7. Technical Constraints & Architecture
*   **Tech Stack:** Kotlin (Latest Stable), Coroutines (Latest Stable).
*   **Architecture:** Clean Architecture.
*   **State Management:** Use `StateFlow` and `SharedFlow`.

---

## 8. Success Metrics (KPIs)
*   **Metric 1:** 100% test pass rate for domain logic.
*   **Metric 2:** >80% code coverage.
*   **Metric 3:** No internal classes exposed in the public API footprint.

---

## 9. Milestones & Timeline
*   **M1 (Implementation):** Completed (2026-03-02)
*   **M2 (Review & QA):** TBD

---

## 10. Learnings & Retrospective (Phase 1)
*   **Gradle Multi-module Complexity vs. Practicality:** Initially, the plan was to create a strict multi-module architecture (extracting the core library into a separate `:core` module) to enforce `explicitApi()` and isolation. However, configuring multiple plugins (`android.library`, `kotlin.android`) across a root `build.gradle.kts`, an `app` module, and a `core` module led to cascading plugin resolution and extension registration errors in Gradle 9/8.9. 
*   **Resolution:** To unblock development and maintain velocity, the decision was made to build the domain foundation directly inside the existing, proven `app` module namespace (`io.github.adityaacodes.echo`). This demonstrated the importance of prioritizing functional delivery over theoretical architectural isolation when build tooling becomes a significant blocker.
*   **Kotlin Coroutines Integration:** Successfully mapped asynchronous concepts (like the `Authenticator`) to modern Kotlin primitives (`suspend fun`, `Result<T>`) rather than relying on legacy Java callbacks or RxJava. The use of `sealed class` hierarchies for `ConnectionState` and `EchoError` immediately provided a robust, type-safe API surface.
