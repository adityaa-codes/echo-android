# Echo Kotlin SDK Implementation Phases

This document outlines a detailed, step-by-step implementation plan for the Echo Kotlin SDK. It is designed to ensure a robust, testable, and idiomatic Kotlin library that mirrors the functionality of Laravel Echo while strictly adhering to modern Android and Kotlin best practices (Clean Architecture, MVVM, UDF, Coroutines, Flow).

## Phase 1: Project Setup & Domain Foundation
**Goal:** Establish the strict Kotlin library environment and define the core domain contracts without implementing network logic.
*   **1.1 Library Configuration:** Configure `build.gradle.kts` for a standalone Kotlin/Android library. Enable `explicitApi()` mode and set up the `libs.versions.toml` catalog for dependencies (Coroutines, Serialization, Ktor, MockK, Turbine).
*   **1.2 Core Domain Interfaces:** Define the public-facing API contracts: `EchoClient`, `EchoConnection`, `EchoChannel`, `PresenceChannel`, and `Authenticator`.
*   **1.3 State Machine Models:** Define the sealed hierarchies for state management, specifically the `ConnectionState` (Disconnected, Connecting, Connected, Reconnecting) and `ChannelState` (Unsubscribed, Subscribing, Subscribed, Failed).
*   **1.4 Error Hierarchy:** Create the `EchoError` sealed class to strongly type all potential failures (Network, Auth, Protocol, Serialization).
*   **1.5 Initialization DSL:** Design and implement the Kotlin DSL builder (`Echo.create { ... }`) to configure the client's host, authentication endpoints, and logging preferences cleanly.

## Phase 2: Protocol Serialization & Data Modeling
**Goal:** Define how the SDK parses and formats the raw JSON WebSocket frames required by the Pusher/Reverb protocol.
*   **2.1 Base Frame Definition:** Define the `PusherFrame` sealed class using `kotlinx.serialization` to represent all incoming and outgoing WebSocket payloads.
*   **2.2 Incoming Events:** Implement polymorphic parsing for key incoming frames: `pusher:connection_established`, `pusher:ping`, `pusher:error`, and `pusher_internal:subscription_succeeded`.
*   **2.3 Outgoing Commands:** Create data classes for outgoing commands: `pusher:subscribe`, `pusher:unsubscribe`, and client whispers.
*   **2.4 Event Formatting & Namespacing:** Implement the logic to handle event name formatting (e.g., auto-prefixing namespaces or enforcing the `client-` prefix for whisper events).

## Phase 3: Core WebSocket Engine (The Connector)
**Goal:** Implement the low-level WebSocket connection management, ensuring stability and adherence to the Pusher connection protocol.
*   **3.1 WebSocket Client Integration:** Implement `EchoConnection` using Ktor's WebSocket client (`HttpClient` with `WebSockets` plugin) to handle the physical socket lifecycle.
*   **3.2 Connection Lifecycle Mapping:** Bridge the raw Ktor WebSocket events (open, close, incoming text frame, exception) into the SDK's `ConnectionState` Flow.
*   **3.3 Keep-Alive Mechanism:** Implement a background coroutine to automatically respond to incoming `pusher:ping` frames with `pusher:pong` frames to prevent the server from terminating idle connections.
*   **3.4 Protocol Handshake:** Handle the initial `pusher:connection_established` frame to extract the `socket_id` and transition the client state to `Connected`.

## Phase 4: Connection Resilience & Reconnection Strategy
**Goal:** Make the connection engine robust against network instability.
*   **4.1 Disconnect Handling:** Gracefully handle abnormal socket closures and network drops, updating the `ConnectionState` appropriately.
*   **4.2 Exponential Backoff:** Implement a reconnection strategy using Coroutines (e.g., `delay()`) with exponential backoff and jitter to prevent server flooding during outages.
*   **4.3 Reconnection State Flow:** Ensure the `StateFlow<ConnectionState>` accurately emits `Reconnecting(attempt)` during the backoff process.
*   **4.4 Connection Timeout:** Implement a timeout mechanism for the initial connection attempt to fail fast if the server is unreachable.

## Phase 5: Event Dispatching & Routing
**Goal:** Bridge the gap between the raw, global socket data and the specific channel listeners.
*   **5.1 Global Event Bus:** Implement an internal `SharedFlow<PusherFrame>` that broadcasts all incoming, successfully parsed frames from the WebSocket.
*   **5.2 Channel Router:** Create a mechanism to route incoming events to the correct `EchoChannel` instance based on the `channel` property in the JSON payload.
*   **5.3 Global Listeners:** Implement the ability to attach global listeners to the `EchoClient` that receive all events, regardless of the channel (useful for logging or debugging).

## Phase 6: Public Channel Subscriptions
**Goal:** Implement the logic for joining and listening to unauthenticated, public channels.
*   **6.1 Subscription Command:** Implement the logic to send the `pusher:subscribe` frame for a public channel.
*   **6.2 Channel State Management:** Map the `pusher_internal:subscription_succeeded` frame to update the `ChannelState` to `Subscribed`.
*   **6.3 Event Listening:** Implement the `EchoChannel.listen(event)` method, returning a `Flow` or allowing callback registration for specific event names on that channel.
*   **6.4 Unsubscribe Logic:** Implement the `leave()` method to send the `pusher:unsubscribe` frame and clean up the channel resources.

## Phase 7: Authentication & Private Channels
**Goal:** Implement the strict authentication requirements for secured topics.
*   **7.1 Authenticator Interface:** Implement the suspending `Authenticator` interface to handle the HTTP POST request to the server's broadcast auth endpoint.
*   **7.2 Pre-flight Auth:** Ensure the SDK successfully fetches the auth signature *before* sending the `pusher:subscribe` request for private channels (`private-*`).
*   **7.3 Auth Failure Handling:** Handle HTTP errors or invalid tokens by transitioning the channel state to `SubscriptionFailed(reason: EchoError.Auth)` without crashing the main connection.
*   **7.4 Whispers (Client Events):** Implement the `whisper()` method on private channels, ensuring the `client-` prefix is enforced and the payload is sent via the WebSocket.

## Phase 8: Presence Channels & Member Tracking
**Goal:** Extend private channel logic to handle the specific presence frames and track the active member list.
*   **8.1 Presence Auth Payload:** Update the `Authenticator` to handle the additional `channel_data` returned by the server for presence channels (`presence-*`).
*   **8.2 Initial Roster:** Handle the `pusher_internal:subscription_succeeded` frame payload to populate the initial list of channel members (`here` callback).
*   **8.3 Member Events:** Parse and route the `pusher_internal:member_added` (`joining`) and `pusher_internal:member_removed` (`leaving`) frames to update the local member list and trigger listeners.

## Phase 9: State Synchronization & Auto-Resubscribe
**Goal:** Ensure the client state remains consistent across connection drops.
*   **9.1 Channel Registry:** Maintain an internal registry of active channels the user has requested to join.
*   **9.2 Subscription Deduplication:** If `echo.channel("xyz")` is called multiple times, return the existing channel instance rather than sending duplicate subscribe commands.
*   **9.3 Auto-Resubscribe Logic:** Implement an observer on the `ConnectionState`. Upon transitioning back to `Connected` after a drop, automatically re-authenticate (if necessary) and re-send `pusher:subscribe` for all channels in the registry.

## Phase 10: Validation, Testing, & Polish
**Goal:** Ensure the SDK is production-ready, heavily tested, and adheres to Android/Kotlin library standards.
*   **10.1 Unit Testing Flows:** Use `MockK` and `app.cash.turbine` to exhaustively test the state machines, ensuring the correct sequence of states (e.g., `Connecting` -> `Connected`, `Disconnected` -> `Reconnecting`).
*   **10.2 Integration Testing:** Mock the WebSocket server (using `ktor-server-test-host` or Ktor's `MockEngine`) to verify the end-to-end flow of connecting, authenticating, subscribing, and receiving events.
*   **10.3 API Visibility Audit:** Review all classes and functions. Ensure explicit `public` modifiers are used only for the intended consumer API, and everything else is `internal` or `private`.
*   **10.4 Documentation:** Add comprehensive KDoc examples to all public-facing interfaces, classes, and DSL builders.
*   **10.5 Sample App Integration:** (Optional) Update the `app` module to use the newly built SDK, verifying it works correctly in a real Android UI context following UDF principles.