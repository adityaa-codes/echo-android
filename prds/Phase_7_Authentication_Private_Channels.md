# Echo Kotlin SDK - Phase 7: Authentication & Private Channels

## 1. Document Metadata
*   **Status:** Draft
*   **Author(s):** Gemini CLI
*   **Stakeholders:** Engineering
*   **Date:** 2026-03-02
*   **Version:** 1.0.0

---

## 2. Context & Problem Statement
### Background
Private channels (`private-*`) require the client to prove they have permission to listen. This is done by requesting an authentication signature from the user's backend server before subscribing.

### Opportunity
Implementing a robust, suspending `Authenticator` interface allows developers to easily plug in their existing HTTP clients (like Retrofit or Ktor) to fetch the required signature asynchronously.

---

## 3. Goals & Non-Goals
### Goals (What we WILL do)
*   Implement `EchoClient.private(name)`.
*   Implement the suspending `Authenticator` flow.
*   Ensure authentication occurs *before* sending `pusher:subscribe`.
*   Handle authentication failures gracefully (e.g., HTTP 401/403).
*   Implement client whispers (`whisper()`) on private channels.

### Non-Goals (What we WILL NOT do)
*   Handle presence channel logic (member tracking).

---

## 4. User Stories
| ID | As an... | I want to... | So that... | Priority |
|:---|:---|:---|:---|:---|
| US.1 | Android Dev | join a private channel | I can receive user-specific sensitive data | P0 |
| US.2 | Android Dev | handle auth failures gracefully | I can show an error to the user without crashing the socket | P0 |
| US.3 | Android Dev | send client whispers | I can send UI typing indicators to other users | P1 |

---

## 5. Functional Requirements
### 5.1 Pre-flight Authentication
*   **FR.1:** When joining a private channel, the SDK must pause the subscription process and invoke the `Authenticator.authenticate(socketId, channelName)` suspending function.
*   **FR.2:** If authentication succeeds, the SDK must attach the `auth` signature to the `pusher:subscribe` payload.

### 5.2 Failure Handling
*   **FR.3:** If authentication fails, the `ChannelState` must transition to `SubscriptionFailed(EchoError.Auth)`. The main WebSocket connection MUST NOT close.

---

## 6. Non-Functional Requirements
*   **Security:** Auth tokens/signatures must never be logged in clear text.

---

## 7. Technical Constraints & Architecture
*   **Concurrency:** The auth process must be non-blocking and suspend until the HTTP request completes.

---

## 8. Success Metrics (KPIs)
*   **Metric 1:** 100% test pass rate for the auth flow (success and failure scenarios).
*   **Metric 2:** >80% code coverage.

---

## 9. Milestones & Timeline
*   **M1 (Implementation):** TBD
*   **M2 (Review & QA):** TBD