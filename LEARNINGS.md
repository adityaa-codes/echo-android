# Echo Kotlin SDK - Phase Learnings

This document captures the key technical decisions, architectural patterns, and lessons learned during the implementation of each phase. It serves as a continuous knowledge base for future phases.

## Phase 1: Project Setup & Domain Foundation
- **Architecture Strictness:** Established strict adherence to Clean Architecture. Core interfaces (`EchoClient`, `EchoChannel`, `EchoConnection`) are kept clean, hiding implementation details behind `internal` modifiers.
- **State Management:** Adopted Unidirectional Data Flow (UDF) using Kotlin `StateFlow` to expose reactive state (e.g., `ConnectionState`, `ChannelState`) rather than imperative callbacks.

## Phase 2: Protocol Serialization & Data Modeling
- **Polymorphic Serialization:** Using `kotlinx.serialization`, we implemented a `JsonContentPolymorphicSerializer` (`PusherFrameSerializer`) to automatically map raw JSON frames into strong domain objects (`ConnectionEstablished`, `Ping`, `GenericEvent`, etc.) based on the `"event"` key.
- **Forward Compatibility:** Strictness was loosened intentionally using `ignoreUnknownKeys = true` on the `Json` instance. This prevents the SDK from crashing if the server adds new fields to the protocol in the future.

## Phase 3: Core WebSocket Engine
- **Ktor WebSockets:** Leveraged Ktor for the WebSocket client. We learned that the `HttpClient` requires an explicit engine dependency on Android to avoid `IllegalStateException` at runtime.
- **Manual Ping/Pong:** Disabled Ktor's default ping mechanism (`pingInterval = -1L`) because the Pusher protocol requires application-level `pusher:ping` and `pusher:pong` JSON frames rather than standard WebSocket control frames.

## Phase 4: Connection Resilience
- **State Machine Integration:** Implemented a robust connection state machine transitioning through `Connecting`, `Connected`, and `Reconnecting`.
- **Coroutine Scope Lifecycle:** Managed the connection loop within an explicit `CoroutineScope`. Handled `CancellationException` properly to avoid breaking structured concurrency during deliberate user disconnects vs. network drops.

## Phase 5: Event Dispatching & Routing
- **SharedFlow Backpressure:** Utilized a central `MutableSharedFlow` for distributing incoming messages. Increased `extraBufferCapacity` to `256` to ensure events are not dropped under heavy payload bursts before subscribers can process them.
- **Ktor Engine Resolution:** Resolved runtime engine failures by explicitly adding `ktor-client-okhttp` to our dependency catalog (`libs.versions.toml`) and wiring it into `EchoClientImpl`.

## Phase 6: Public Channel Subscriptions
- **Thread-Safe Caching:** Used `ConcurrentHashMap` within `EchoClientImpl` to safely cache and retrieve active `EchoChannelImpl` instances across multiple concurrent subscription requests.
- **Testing Coroutine Flows:** Learned that when testing state transitions and mock verifications with Turbine and MockK, explicit calls to `runCurrent()` (from `kotlinx.coroutines.test`) are required to advance the virtual time and allow background `launch` blocks to execute before asserting.

## Phase 7: Authentication & Private Channels
- **Pre-flight Suspending Authentication:** Modified the `EchoChannelImpl.subscribe()` flow to wait for the `ConnectionState.Connected` state using `.first()`, allowing us to extract the `socketId`. This `socketId` and channel name are then passed to the user-provided `Authenticator` interface to fetch the signature asynchronously before sending the `pusher:subscribe` JSON payload.
- **Graceful Failure Handling:** Designed the subscription process to transition the channel state to `ChannelState.Failed(EchoError.Auth)` if authentication fails, ensuring the main WebSocket connection remains uninterrupted.
- **Client Events (Whispering):** Added `whisper` and `listenForWhisper` implementations that are guarded to only allow outgoing client events on `private-*` and `presence-*` channels.

## Phase 8: Presence Channels & Member Tracking
- **Composition over Inheritance:** Used Kotlin's interface delegation (`by delegate`) in `PresenceChannelImpl` to wrap the `EchoChannelImpl` logic. This avoids polluting standard channels with presence logic and avoids making classes `open` needlessly.
- **JSON Flexibility:** Updated the `Member` interface to expose `info` as `JsonElement` instead of a flat `Map`, better handling nested user details returned from backend authenticators.
- **Reactive Member Lists:** Maintained a private `MutableStateFlow<List<Member>>` that immediately updates upon `SubscriptionSucceeded` (initial roster), `MemberAdded`, and `MemberRemoved` internal Pusher frames.
