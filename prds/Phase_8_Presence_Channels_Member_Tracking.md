# Echo Kotlin SDK - Phase 8: Presence Channels & Member Tracking

## 1. Document Metadata
*   **Status:** Draft
*   **Author(s):** Gemini CLI
*   **Stakeholders:** Engineering
*   **Date:** 2026-03-02
*   **Version:** 1.0.0

---

## 2. Context & Problem Statement
### Background
Presence channels (`presence-*`) are similar to private channels but offer the additional capability of tracking who is currently subscribed to the channel.

### Opportunity
By tracking the `pusher_internal:member_added` and `pusher_internal:member_removed` events, the SDK can provide developers with a real-time list of active users, perfect for chat rooms or collaborative editing features.

---

## 3. Goals & Non-Goals
### Goals (What we WILL do)
*   Implement `EchoClient.join(name)` to return a `PresenceChannel`.
*   Update `Authenticator` to parse `channel_data` for presence channels.
*   Parse the initial roster from `pusher_internal:subscription_succeeded`.
*   Handle `joining` and `leaving` member events to keep a local cache of users.

### Non-Goals (What we WILL NOT do)
*   Implement specific UI components for displaying users.

---

## 4. User Stories
| ID | As an... | I want to... | So that... | Priority |
|:---|:---|:---|:---|:---|
| US.1 | Android Dev | join a presence channel | I can see who else is in the "room" | P0 |
| US.2 | Android Dev | observe the member list | my UI updates automatically when someone joins or leaves | P0 |

---

## 5. Functional Requirements
### 5.1 Member Tracking
*   **FR.1:** The SDK must expose a `StateFlow<List<Member>>` on the `PresenceChannel` interface.
*   **FR.2:** The initial roster must be populated immediately after a successful subscription.
*   **FR.3:** The member list must be dynamically updated upon receiving `member_added` or `member_removed` frames.

---

## 6. Non-Functional Requirements
*   **Performance:** Member list updates should trigger minimal GC churn; prefer immutable list copies.

---

## 7. Technical Constraints & Architecture
*   **Data Models:** Provide a flexible way for developers to map the raw `channel_data` JSON into their own domain user objects.

---

## 8. Success Metrics (KPIs)
*   **Metric 1:** 100% test pass rate for member tracking logic.
*   **Metric 2:** >80% code coverage.

---

## 9. Milestones & Timeline
*   **M1 (Implementation):** TBD
*   **M2 (Review & QA):** TBD