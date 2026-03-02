# Echo Kotlin SDK Implementation Specification

## 1. Overview & Laravel Echo Comparison
This document outlines the technical specification for the Echo Kotlin SDK, comparing the proposed architecture against the reference JavaScript Laravel Echo library (https://github.com/laravel/echo).

### 1.1 Core Architecture Mapping

| Component | Laravel Echo (TS) | Android SDK (Kotlin) | Notes |
| :--- | :--- | :--- | :--- |
| **Entry Point** | `Echo` class | `EchoClient` interface | Kotlin entry point should mirror methods like `channel()`, `private()`, `leave()`. |
| **Connector** | `PusherConnector` | `EchoConnection` | Responsible for holding the underlying socket client (e.g., Ktor WebSockets). |
| **Channel** | `PusherChannel` | `EchoChannel` | Wraps the subscription object. Handles event binding. |
| **Auth** | Interceptors / `authEndpoint` | `Authenticator` interface | Kotlin SDK uses a robust suspending authenticator. |

### 1.2 Key Methods to Mirror

**`EchoClient` Interface:**
- `connect()`: Initializes the connector.
- `disconnect()`: Closes connection.
- `channel(name)`: Returns a public channel instance.
- `private(name)`: Returns a private channel instance.
- `join(name)`: Returns a presence channel instance.
- `leave(name)`: Unsubscribes from a channel.
- `listen(channel, event, callback)`: Shortcut to listen to an event on a channel.

**`EchoChannel` Interface:**
- `listen(event, callback)`: Binds a listener to an event.
- `whisper(event, data)`: Sends a client event (`client-event`).
- `listenForWhisper(event, callback)`: Binds to a client event.

**`PresenceChannel` Interface (extends `EchoChannel`):**
- `here(callback)`: Called with initial member list.
- `joining(callback)`: Called when a member joins.
- `leaving(callback)`: Called when a member leaves.

### 1.3 Implementation Details & Gaps

- **Event Formatting:** Laravel Echo uses an `EventFormatter` to handle namespaced events. The Kotlin SDK must support a namespace option to auto-prefix events.
- **Authentication:** Laravel Echo expects an HTTP endpoint returning `{ auth: "..." }`. The Kotlin `Authenticator` handles the POST request, passing `socket_id` and `channel_name`.
- **Client Events (Whispers):** Echo prefixes client events with `client-` automatically. The Kotlin SDK enforces this prefix.
- **Interceptors:** Echo JS allows registering interceptors for headers. The Kotlin SDK provides similar hooks during connection/auth.
- **Socket.io:** Echo supports Socket.io. The Kotlin SDK is Pusher-focused (Reverb), so Socket.io is currently out of scope.

---

## 2. Kotlin SDK Technical Standards

The Kotlin SDK follows the strict architectural guidelines defined in `GEMINI.md` (Clean Architecture, MVVM, UDF). Below are the implementation-specific standards.

### 2.1 Code Construction
- **Configuration:** Use **Kotlin DSL** (`Echo.create { ... }`) for initialization.
- **Async Model:** Use **Suspending** functions returning `Result<T>` (e.g., `suspend fun trigger(...): Result<Unit>`).
- **Context:** Remove Android `Context` from core networking logic where possible.
- **Serialization:** Use `kotlinx.serialization` exclusively. Use `@SerialName` and sealed class hierarchies for polymorphic parsing of WebSocket frames.
- **Error Handling:** Use Typed Results and a Sealed `EchoError` hierarchy instead of hot `SharedFlow<SdkError>` or generic exceptions.
- **DI:** Rely on Constructor Injection.

### 2.2 Core Mandates

#### Connection State Machine
The `EchoClient` must expose a robust state machine via `StateFlow<ConnectionState>`:
- `Disconnected(reason: Throwable? = null)`: Initial state or after errors.
- `Connecting`: Connection attempt in progress.
- `Connected(socketId: String)`: WebSocket open and handshake complete.
- `Reconnecting(attempt: Int)`: Active backoff strategy in play.

**Requirement:** The client must automatically handle `pusher:ping` / `pusher:pong` keep-alives and reconnect on network loss.

#### Channel Subscription Contract
- **Deduplication:** Calling `subscribe` on an already subscribed channel returns the existing flow/state.
- **Resubscription:** Re-subscribe to all active channels automatically upon reconnection.
- **Auth Handling:** Authentication happens *before* sending `pusher:subscribe` for private/presence channels. The `Authenticator` interface must be suspending.

#### Error Hierarchy
```kotlin
sealed class EchoError : Exception() {
    data class Network(override val cause: Throwable) : EchoError()
    data class Auth(val status: Int, val body: String) : EchoError()
    data class Protocol(val code: Int, val message: String) : EchoError() // From pusher:error
    data class Serialization(override val cause: Throwable) : EchoError()
}
```

### 2.3 Testing & Usage
- **Testing:** Use `MockK` for mocking, `app.cash.turbine` for testing Flows, and mock WebSocket servers (`ktor-server-test-host`) for integration.
- **Documentation:** All public APIs must include KDoc samples.

**DSL Usage Example:**
```kotlin
val echo = Echo.create {
    client {
        host = "ws.suuz.app"
        apiKey = "app-key"
        cluster = "mt1" // Optional
    }
    
    auth {
        authenticator = MyAuthenticator() // Custom
        // OR default HTTP
        authEndpoint = "https://api.suuz.app/broadcasting/auth"
        tokenProvider = { "Bearer ${session.token}" }
    }
    
    logging {
        level = EchoLogLevel.BASIC
        logger = { msg -> Timber.d(msg) }
    }
}
```