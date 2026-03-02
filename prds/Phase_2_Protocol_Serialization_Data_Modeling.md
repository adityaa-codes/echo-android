# Echo Kotlin SDK - Phase 2: Protocol Serialization & Data Modeling

## 1. Document Metadata
*   **Status:** Draft
*   **Author(s):** Gemini CLI
*   **Stakeholders:** Engineering
*   **Date:** 2026-03-02
*   **Version:** 1.0.0

---

## 2. Context & Problem Statement
### Background
The Pusher/Reverb protocol relies on specific JSON structures for its WebSocket frames. The SDK must be able to serialize and deserialize these payloads accurately and efficiently.

### Opportunity
Using `kotlinx.serialization` with polymorphic parsing allows us to map raw JSON strings directly into strongly-typed Kotlin data classes, eliminating manual JSON parsing overhead and reducing runtime crashes.

---

## 3. Goals & Non-Goals
### Goals (What we WILL do)
*   Define a `PusherFrame` sealed class hierarchy.
*   Implement polymorphic parsing for incoming events (`pusher:connection_established`, `pusher:ping`, etc.).
*   Implement serialization for outgoing commands (`pusher:subscribe`, `pusher:unsubscribe`, whispers).
*   Implement event name formatting (namespaces and `client-` prefixes).

### Non-Goals (What we WILL NOT do)
*   Connect to a real WebSocket.
*   Route these events to specific channel listeners.

---

## 4. User Stories
| ID | As an... | I want to... | So that... | Priority |
|:---|:---|:---|:---|:---|
| US.1 | Android Dev | trigger client whispers without manual formatting | the SDK automatically prepends `client-` | P1 |
| US.2 | Android Dev | use namespaced events | the SDK formats them correctly for the server | P1 |

---

## 5. Functional Requirements
### 5.1 Parsing
*   **FR.1:** The SDK must parse valid JSON strings into the appropriate `PusherFrame` subclass based on the "event" key.
*   **FR.2:** Unknown keys in the JSON payload must be ignored to prevent crashes on forward-compatible server changes.

### 5.2 Formatting
*   **FR.3:** The system must ensure that outgoing whisper events strictly begin with `client-`.

---

## 6. Non-Functional Requirements
*   **Performance:** Serialization/Deserialization must be non-blocking and fast.
*   **Safety:** Malformed JSON should result in a typed `EchoError.Serialization` rather than a fatal exception.

---

## 7. Technical Constraints & Architecture
*   **Dependencies:** `kotlinx.serialization` (Latest Stable).
*   **Configuration:** `@Serializable` and `@SerialName` annotations must be used for polymorphic mapping.

---

## 8. Success Metrics (KPIs)
*   **Metric 1:** 100% test pass rate for all serialization/deserialization unit tests.
*   **Metric 2:** >80% code coverage.

---

## 9. Milestones & Timeline
*   **M1 (Implementation):** TBD
*   **M2 (Review & QA):** TBD