# Echo Kotlin SDK - Phase 5: Event Dispatching & Routing

## 1. Document Metadata
*   **Status:** Draft
*   **Author(s):** Gemini CLI
*   **Stakeholders:** Engineering
*   **Date:** 2026-03-02
*   **Version:** 1.0.0

---

## 2. Context & Problem Statement
### Background
The WebSocket receives a single, global stream of text frames. However, developers want to listen to specific events on specific channels.

### Opportunity
We need an internal routing mechanism that acts as a pub/sub bus, reading incoming `PusherFrame` objects and dispatching them to the correct `EchoChannel` instance.

---

## 3. Goals & Non-Goals
### Goals (What we WILL do)
*   Create an internal `SharedFlow<PusherFrame>` for global broadcasting.
*   Implement an event router that filters events by their `channel` property.
*   Allow developers to attach global event listeners to `EchoClient`.

### Non-Goals (What we WILL NOT do)
*   Implement the channel subscription logic itself.

---

## 4. User Stories
| ID | As an... | I want to... | So that... | Priority |
|:---|:---|:---|:---|:---|
| US.1 | Android Dev | listen to a specific event on a channel | I only receive data relevant to my UI component | P0 |
| US.2 | Android Dev | attach a global listener | I can log all incoming socket traffic for debugging | P2 |

---

## 5. Functional Requirements
### 5.1 Routing
*   **FR.1:** The router must inspect the `channel` field of incoming events and emit them only to the Flow belonging to that channel.
*   **FR.2:** Global listeners must receive all events, regardless of the target channel.

---

## 6. Non-Functional Requirements
*   **Performance:** The routing mechanism must be highly optimized and not introduce significant latency between message receipt and listener execution.

---

## 7. Technical Constraints & Architecture
*   **State Management:** Use `SharedFlow` with appropriate `extraBufferCapacity` and `onBufferOverflow` strategies to ensure events are not dropped under heavy load.

---

## 8. Success Metrics (KPIs)
*   **Metric 1:** 100% test pass rate for routing logic.
*   **Metric 2:** Verify events are isolated and not bleeding into incorrect channels.

---

## 9. Milestones & Timeline
*   **M1 (Implementation):** TBD
*   **M2 (Review & QA):** TBD