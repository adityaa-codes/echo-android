# Echo Kotlin SDK - Phase 6: Public Channel Subscriptions

## 1. Document Metadata
*   **Status:** Draft
*   **Author(s):** Gemini CLI
*   **Stakeholders:** Engineering
*   **Date:** 2026-03-02
*   **Version:** 1.0.0

---

## 2. Context & Problem Statement
### Background
Once connected, the SDK must allow users to subscribe to public channels to receive broadcasted events without any authorization overhead.

### Opportunity
By creating a clean `EchoChannel` interface, developers can simply request a channel and immediately start listening to events using Kotlin Coroutines/Flows.

---

## 3. Goals & Non-Goals
### Goals (What we WILL do)
*   Implement `EchoClient.channel(name)`.
*   Send the `pusher:subscribe` command for unauthenticated channels.
*   Track the `ChannelState` (Unsubscribed, Subscribing, Subscribed).
*   Implement the `EchoChannel.listen(event)` method.
*   Implement the `EchoChannel.leave()` method.

### Non-Goals (What we WILL NOT do)
*   Handle authentication for private/presence channels.
*   Handle auto-resubscription on network reconnect.

---

## 4. User Stories
| ID | As an... | I want to... | So that... | Priority |
|:---|:---|:---|:---|:---|
| US.1 | Android Dev | join a public channel | I can receive real-time updates for public data | P0 |
| US.2 | Android Dev | leave a channel | I can stop receiving updates and save resources | P0 |

---

## 5. Functional Requirements
### 5.1 Subscription
*   **FR.1:** Calling `channel("my-channel")` must trigger the SDK to send a `pusher:subscribe` JSON payload to the server.
*   **FR.2:** The SDK must map the server's `pusher_internal:subscription_succeeded` event to transition the channel state to `Subscribed`.

### 5.2 Listening
*   **FR.3:** The `listen(event)` method must return a `Flow` that only emits events matching both the channel name and the specific event name.

---

## 6. Non-Functional Requirements
*   **Memory Management:** Leaving a channel must clear all active listeners and Coroutine jobs associated with that channel.

---

## 7. Technical Constraints & Architecture
*   **State Management:** Each channel must maintain its own `StateFlow<ChannelState>`.

---

## 8. Success Metrics (KPIs)
*   **Metric 1:** >80% code coverage.
*   **Metric 2:** 100% test pass rate for subscription and unsubscription logic.

---

## 9. Milestones & Timeline
*   **M1 (Implementation):** TBD
*   **M2 (Review & QA):** TBD