# PRD: Echo Android SDK - Robust Architecture & Stability Enhancements

**Status:** Draft
**Date:** 2026-03-02
**Collaborators:** Engineering

---

## 1. Executive Summary
The Echo Android SDK provides a Kotlin-idiomatic client for Laravel Reverb and Pusher WebSockets. While the current API surface is clean and utilizes modern Kotlin Coroutines (`StateFlow`), the underlying connection management lacks robustness under stress. This project will harden the SDK by introducing strict concurrency controls, a decoupled reconnection engine, a deterministic ping/pong protocol, and improved backpressure handling. These changes will elevate the library to production-ready status.

## 2. Goals & Non-Goals
### Goals
*   **Primary Goal:** Eliminate all potential race conditions in the connection/disconnection lifecycle.
*   **Secondary Goal:** Provide a deterministic Ping/Pong keep-alive mechanism that allows manual probing.
*   **Secondary Goal:** Decouple the exponential backoff reconnection logic from the core WebSocket engine.
*   **Secondary Goal:** Improve global error visibility via a dedicated `SharedFlow`.

### Non-Goals
*   This PRD does not cover adding new features like Presence Channels or Whisper events (these are assumed to be handled in separate workflows).
*   We are not replacing Ktor as the underlying WebSocket engine.

## 3. Target Audience & User Stories
### Personas
*   **SDK Consumer:** Android Developers integrating the Echo Android SDK into their applications.

### User Stories
| ID | As a... | I want to... | So that... |
|:---|:---|:---|:---|
| US.1 | Developer | rapidly call `connect()` and `disconnect()` based on UI events | my application does not leak WebSocket sessions or crash due to race conditions. |
| US.2 | Developer | manually invoke `client.ping()` before sending critical data | I can guarantee the Reverb server is reachable. |
| US.3 | Developer | rely on automatic exponential backoff | my app doesn't DDoS the server immediately after a widespread network drop. |
| US.4 | Developer | observe a global stream of all non-fatal protocol errors | I can log these to Crashlytics or show warning toasts to my users. |

## 4. Functional Requirements
*   **FR.1: Concurrency Control**
    *   The `connect()` and `disconnect()` lifecycle methods must be guarded by a `kotlinx.coroutines.sync.Mutex`.
    *   The active `WebSocketSession` must be safely accessed (e.g., via `AtomicReference` or `Mutex` lock) to prevent `ConcurrentModificationException` during outbound message sending.
*   **FR.2: Reconnection Manager & Protocol Error Handling**
    *   Extract reconnection logic into a dedicated `ExponentialBackoffReconnectionManager`.
    *   The manager must observe `ConnectionState` and trigger a `delay()` with an exponential backoff formula (incorporating randomized jitter) when detecting unexpected disconnections.
    *   **Pusher Protocol Compliance:** The manager must parse Pusher WebSocket error codes to dictate the retry behavior:
        *   `4000-4099`: **Fatal Errors** (e.g., App does not exist). The SDK must *not* attempt to reconnect. It must remain disconnected and emit the error.
        *   `4100-4199`: **Transient Errors**. The SDK must reconnect after a backoff delay.
        *   `4200-4299`: **Immediate Reconnect Errors**. The SDK must attempt to reconnect immediately, bypassing the backoff delay.
*   **FR.3: Deterministic Ping/Pong**
    *   Implement a `CompletableDeferred<Boolean>` tied to a `pingMutex`.
    *   Expose `suspend fun ping(timeoutMillis: Long): Boolean` on `EchoClient`.
    *   The internal message router must complete the deferred object when it intercepts a `pusher:pong` frame.
*   **FR.4: Global Error Bus & Channel Error Recovery**
    *   Add `val errors: SharedFlow<EchoError>` to `EchoClient`.
    *   **Types of Errors to Stream:**
        *   **Protocol Errors:** Server rejections (e.g., HTTP 401 on connect, Pusher Protocol Error 4004).
        *   **Authentication Errors:** Failures when attempting to sign signatures for `private-` or `presence-` channels via the HTTP `Authenticator`.
        *   **Serialization/Parsing Errors:** Invalid JSON payloads received from the server that cannot be parsed into `EchoEvent` objects.
        *   **Network Errors:** Underlying `SocketException` or `TimeoutCancellationException` instances.
    *   Errors emitted to this bus must not automatically crash the client.
    *   **Channel Recovery:** If a channel fails to authenticate (e.g., expired token), it will enter a `ChannelState.Failed` state. The `EchoChannel` interface must expose a new `fun retry()` method so developers can manually re-trigger the subscription without having to leave and rejoin the channel.
    *   **Automated Token Refresh:** `AuthConfig` should accept an optional `onAuthFailure: suspend () -> Unit` callback, allowing the SDK to automatically pause, let the app refresh its JWT, and then automatically retry the channel subscription once.
*   **FR.5: Flow Backpressure**
    *   Internal `_incomingFrames` SharedFlow must be configured with `extraBufferCapacity = 64` and `onBufferOverflow = BufferOverflow.DROP_OLDEST`.
*   **FR.6: Pluggable Architecture (Extendability)**
    *   **WebSocket Engine Abstraction:** The core SDK must not tightly couple to Ktor. We must expose an `EchoEngine` interface (which handles raw WS connecting, sending, and receiving text frames). Provide `KtorEchoEngine` as the default artifact, but allow developers to implement `OkHttpEchoEngine` or others.
    *   **Serializer Abstraction:** Abstract the JSON parsing behind an `EchoSerializer` interface. While `kotlinx.serialization` will be the default, this allows legacy codebases to inject `Moshi` or `Gson` wrappers if required.

## 5. Non-Functional Requirements
*   **Reliability:** The SDK must correctly resubscribe to active channels upon a successful automated reconnection.
*   **Performance:** The addition of Mutex locks must not introduce observable latency (< 10ms overhead) to the message sending path.
*   **Compatibility:** Must maintain compatibility with Android API 21+ and Kotlin Coroutines 1.8+.

## 6. Technical Constraints & Architecture
*   **State Management:** Must continue to use `StateFlow` (Hot) for connection states and `SharedFlow` (Cold/Hot) for global event buses.
*   **Visibility:** Internal state machines (`ReconnectionManager`, `EchoLogger`) must remain `internal` using Kotlin's `explicitApi()` compiler checks.
*   **WSS Default:** Ensure the `ClientConfig.useTls` defaults to `true` to enforce Android Network Security Configuration standards.

## 7. UI/UX Requirements
*   Update the `sample` application to demonstrate the global `errors` stream (e.g., displaying Toasts for authentication failures).
*   Add a "Ping Server" button to the `sample` app to verify the manual Ping API.

## 8. Success Metrics
*   [ ] 100% passing Unit Tests, specifically testing concurrent `connect()`/`disconnect()` invocations.
*   [ ] Reconnection tests proving that backoff delays compound correctly.
*   [ ] Successful demonstration of manual Ping/Pong in the Sample App.

## 9. Risks & Mitigations
| Risk | Impact | Mitigation |
|:---|:---|:---|
| Deadlocks from incorrect Mutex usage | High | Keep critical sections extremely small. Never execute blocking I/O or long-running suspend functions inside the `connectMutex` lock. |
| Thundering Herd during reconnects | Med | Add randomized jitter to the exponential backoff calculation in `ReconnectionManager`. |

## 10. Milestones & Timeline
*   **Phase 1: Concurrency & Backpressure (FR.1, FR.5)**
*   **Phase 2: Decoupled Reconnection (FR.2)**
*   **Phase 3: Deterministic Ping & Global Errors (FR.3, FR.4)**
*   **Phase 4: Sample App Updates & Validation**