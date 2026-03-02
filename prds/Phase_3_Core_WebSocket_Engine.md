# Echo Kotlin SDK - Phase 3: Core WebSocket Engine (The Connector)

## 1. Document Metadata
*   **Status:** Draft
*   **Author(s):** Gemini CLI
*   **Stakeholders:** Engineering
*   **Date:** 2026-03-02
*   **Version:** 1.0.0

---

## 2. Context & Problem Statement
### Background
The SDK needs a reliable underlying transport mechanism to connect to the Reverb/Pusher WebSocket server. 

### Opportunity
Implementing a robust engine using Ktor WebSockets ensures non-blocking I/O and easy integration with Kotlin Coroutines.

---

## 3. Goals & Non-Goals
### Goals (What we WILL do)
*   Implement `EchoConnection` using the Ktor WebSocket client.
*   Map raw Ktor WebSocket events (open, text frame, close, error) to the SDK's `ConnectionState` Flow.
*   Implement the `pusher:ping` / `pusher:pong` keep-alive mechanism.
*   Extract `socket_id` from the initial handshake.

### Non-Goals (What we WILL NOT do)
*   Implement auto-reconnection logic (covered in Phase 4).
*   Handle channel subscriptions.

---

## 4. User Stories
| ID | As an... | I want to... | So that... | Priority |
|:---|:---|:---|:---|:---|
| US.1 | Android Dev | initiate a connection asynchronously | it doesn't block the main UI thread | P0 |
| US.2 | Android Dev | trust the SDK to stay alive | the server doesn't drop the connection due to inactivity | P0 |

---

## 5. Functional Requirements
### 5.1 Connection Management
*   **FR.1:** `connect()` must be a suspending function that opens the WebSocket.
*   **FR.2:** `disconnect()` must close the WebSocket and update the state to `Disconnected`.

### 5.3 Keep-Alive
*   **FR.3:** The client MUST automatically send a `pusher:pong` whenever a `pusher:ping` is received.

---

## 6. Non-Functional Requirements
*   **Resource Management:** Coroutines and WebSocket sessions must be properly canceled and cleaned up on disconnect to prevent memory leaks.

---

## 7. Technical Constraints & Architecture
*   **Dependencies:** `ktor-client-websockets`, `ktor-client-core` (Latest Stable).
*   **Concurrency:** Heavy reliance on Coroutine Scopes and `Dispatchers.IO` for network operations.

---

## 8. Success Metrics (KPIs)
*   **Metric 1:** 100% test pass rate using Ktor `MockEngine`.
*   **Metric 2:** Successful `socket_id` extraction on mock connection.

---

## 9. Milestones & Timeline
*   **M1 (Implementation):** TBD
*   **M2 (Review & QA):** TBD