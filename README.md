<div align="center">

# 📡 Echo Kotlin SDK

**A robust, type-safe, and idiomatic Kotlin client for Pusher-compatible WebSocket services.**

Built for [Laravel Reverb](https://reverb.laravel.com) and any Pusher Channels–compatible backend.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.md)
[![API 30+](https://img.shields.io/badge/API-30%2B-brightgreen.svg)](https://developer.android.com/about/versions/11)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple.svg)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/Ktor-3.4-orange.svg)](https://ktor.io)

</div>

---

## ✨ Features

- **Kotlin-first** — Coroutines, Flow, StateFlow, and structured concurrency throughout
- **Pusher Protocol v7** — Full compatibility with public, private, and presence channels
- **Laravel Reverb ready** — First-class support for self-hosted Reverb servers
- **Pluggable architecture** — Swap WebSocket engines and serializers via the DSL
- **Robust connection lifecycle** — Mutex-guarded state machine with automatic reconnection and exponential backoff
- **Protocol-level ping/pong** — Configurable keep-alive with 30 s pong timeout
- **Global error stream** — Typed `EchoError` sealed hierarchy exposed as `SharedFlow`
- **Backpressure handling** — Configurable buffer overflow strategies on all internal flows
- **Minimal public API** — `internal` by default; only consumer-facing types are `public`

---

## 📋 Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Channels](#channels)
- [Architecture](#architecture)
- [API Reference](#api-reference)
- [Sample App](#sample-app)
- [Testing](#testing)
- [Contributing](#contributing)
- [License](#license)

---

## Requirements

| Requirement     | Version     |
|-----------------|-------------|
| Android minSdk  | **30** (Android 11) |
| compileSdk      | **36** (Android 16) |
| Kotlin          | **2.3.10**  |
| Java            | **11+**     |
| Gradle          | **9.1+**    |

---

## Installation

### Maven Central

Add the dependency in your app's `build.gradle.kts`:

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("io.github.adityaa-codes:echo:0.1.0")
}
```

To check the latest published version on Maven Central:

```bash
curl -s https://repo1.maven.org/maven2/io/github/adityaa-codes/echo/maven-metadata.xml
```

Look at `<latest>` / `<release>` in the response and use that version.

### Local Module

1. Clone the repository:

```bash
git clone https://github.com/adityaacodes/echo-android.git
```

2. Include the `:core` module in your project's `settings.gradle.kts`:

```kotlin
include(":core")
project(":core").projectDir = file("../echo-android/core")
```

3. Add the dependency in your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":core"))
}
```

For local publishing credentials/signing, use user-level `~/.gradle/gradle.properties` (never commit secrets in project `gradle.properties`):

```properties
mavenCentralUsername=YOUR_CENTRAL_TOKEN_USERNAME
mavenCentralPassword=YOUR_CENTRAL_TOKEN_PASSWORD
signing.keyId=YOUR_GPG_KEY_ID
signing.password=YOUR_GPG_KEY_PASSPHRASE
signing.secretKey=-----BEGIN PGP PRIVATE KEY BLOCK-----...
```

> If your release deployment in Sonatype shows `PUBLISHING`, wait for Maven Central indexing (typically several minutes, sometimes longer).

For release automation (`.github/workflows/publish-release.yml`), configure these repository secrets:
`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`, `SIGNING_SECRET_KEY`.

---

## Quick Start

```kotlin
import io.github.adityaacodes.echo.Echo

// 1. Create a client
val echo = Echo.create {
    client {
        host = "your-reverb-server.com"
        apiKey = "your-app-key"
        port = 8080
        useTls = false
    }
    auth {
        authenticator = { channelName, socketId ->
            // Return auth signature from your backend
            Result.success("""{"auth":"$socketId:signature"}""")
        }
    }
    logging {
        enabled = true
    }
}

// 2. Connect
echo.connect()

// 3. Subscribe to a public channel
val channel = echo.channel("chat-room")
channel.listen("MessageSent") { event ->
    println("New message: ${event.data}")
}

// 4. Subscribe to a private channel
val privateChannel = echo.private("orders")
privateChannel.listen("OrderUpdated") { event ->
    println("Order update: ${event.data}")
}

// 5. Subscribe to a presence channel
val presenceChannel = echo.presence("online-users")
presenceChannel.here { members -> println("Online: $members") }
presenceChannel.joining { member -> println("Joined: $member") }
presenceChannel.leaving { member -> println("Left: $member") }

// 6. Observe connection state
echo.state.collect { state ->
    println("Connection: $state")
}

// 7. Observe errors globally
echo.errors.collect { error ->
    println("Error: $error")
}
```

---

## Configuration

The SDK is configured entirely through a Kotlin DSL:

```kotlin
val echo = Echo.create {
    client {
        host = "ws.example.com"       // WebSocket host
        apiKey = "app-key"            // Pusher/Reverb app key
        cluster = "mt1"               // Optional: Pusher cluster (overrides host)
        port = 443                    // Optional: custom port
        useTls = true                 // Default: true (wss://)

        // Pluggable engine (optional — defaults to KtorEchoEngine)
        engineFactory = { CustomEchoEngine() }

        // Pluggable serializer (optional — defaults to DefaultEchoSerializer)
        serializer = CustomEchoSerializer()
    }

    auth {
        authenticator = myAuthenticator    // Authenticator for private/presence channels
        authEndpoint = "/broadcasting/auth" // Optional: HTTP auth endpoint
        tokenProvider = { "Bearer ..." }   // Optional: token for HTTP auth
        onAuthFailure = {                  // Optional: retry callback on auth failure
            refreshToken()
        }
    }

    logging {
        enabled = true                     // Enable SDK logging (default: false)
        logger = { msg -> Log.d("Echo", msg) } // Optional: custom logger
    }

    reconnection {
        maxAttempts = 10                   // Default: 10 attempts
        baseDelayMs = 1_000                // Default: 1 second
        maxDelayMs = 30_000                // Default: 30 seconds
    }
}
```

---

## Channels

### Public Channels

```kotlin
val channel = echo.channel("news")
channel.listen("ArticlePublished") { event ->
    // handle event
}
```

### Private Channels

Private channels require authentication. The `private-` prefix is added automatically.

```kotlin
val channel = echo.private("user.123")
channel.listen("NotificationSent") { event ->
    // handle event
}
```

### Presence Channels

Presence channels track online members. The `presence-` prefix is added automatically.

```kotlin
val presence = echo.presence("chat-room")
presence.here { members -> /* initial member list */ }
presence.joining { member -> /* a member joined */ }
presence.leaving { member -> /* a member left */ }
```

### Leaving a Channel

```kotlin
echo.leave("chat-room")
```

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                      EchoClient (API)                    │
├──────────────────────────────────────────────────────────┤
│  Echo.create { }  →  EchoBuilder  →  EchoClientImpl     │
├──────────────┬───────────────────┬───────────────────────┤
│  EventRouter │  ReconnectionMgr  │  ChannelImpl(s)       │
├──────────────┴───────────────────┴───────────────────────┤
│              KtorEchoConnection                          │
│     ┌─────────────┐    ┌──────────────┐                  │
│     │  EchoEngine  │    │ EchoSerializer│                 │
│     │  (pluggable) │    │  (pluggable)  │                 │
│     └──────┬──────┘    └──────────────┘                  │
│            │                                             │
│     KtorEchoEngine (default)                             │
│     ┌──────────────────┐                                 │
│     │  Ktor HttpClient  │                                │
│     │  + OkHttp Engine  │                                │
│     │  + WebSockets     │                                │
│     └──────────────────┘                                 │
└──────────────────────────────────────────────────────────┘
```

### Connection State Machine

```
Disconnected ──► Connecting ──► Connected
     ▲                              │
     │                              ▼
     └──── Disconnected ◄── Reconnecting
```

The SDK exposes `StateFlow<ConnectionState>` with four states:
- **`Disconnected`** — not connected (optionally includes disconnect reason)
- **`Connecting`** — WebSocket handshake in progress
- **`Connected`** — handshake complete, `socketId` available
- **`Reconnecting`** — lost connection, attempting automatic reconnect with exponential backoff

### Error Hierarchy

```kotlin
sealed class EchoError {
    data class Network(...)        // Network/IO failures
    data class Auth(...)           // Authentication failures
    data class Protocol(...)       // Pusher protocol errors (4000–4299)
    data class Serialization(...)  // JSON parsing failures
}
```

---

## API Reference

### `Echo`

| Member | Description |
|--------|-------------|
| `Echo.create { }` | Create a configured `EchoClient` instance |

### `EchoClient`

| Member | Type | Description |
|--------|------|-------------|
| `state` | `StateFlow<ConnectionState>` | Current connection state |
| `errors` | `SharedFlow<EchoError>` | Global error stream |
| `globalEvents` | `Flow<EchoEvent>` | All incoming events |
| `socketId` | `String?` | Current socket ID (when connected) |
| `activeChannels` | `List<EchoChannel>` | Currently subscribed channels |
| `connect()` | `suspend` | Initiate WebSocket connection |
| `disconnect()` | `suspend` | Gracefully close the connection |
| `ping(timeoutMillis)` | `suspend → Boolean` | Send a manual ping; returns `true` if pong received |
| `channel(name)` | `EchoChannel` | Subscribe to a public channel |
| `private(name)` | `EchoChannel` | Subscribe to a private channel |
| `presence(name)` | `PresenceChannel` | Subscribe to a presence channel |
| `leave(name)` | `Unit` | Unsubscribe from a channel |

### `EchoEngine` (Pluggable)

| Member | Description |
|--------|-------------|
| `incoming: Flow<String>` | Stream of raw incoming text frames |
| `connect(url)` | Open WebSocket connection |
| `send(data): Result<Unit>` | Send a text frame |
| `disconnect()` | Close the connection |

### `EchoSerializer` (Pluggable)

| Member | Description |
|--------|-------------|
| `deserialize(text): PusherFrame` | Parse raw text into a protocol frame |
| `serialize(frame): String` | Encode a protocol frame to text |

---

## Sample App

The `sample` module provides a fully functional reference app demonstrating:

- Connection lifecycle management
- Public/private/presence channel subscription
- Global error stream collection and toast display
- Manual ping with result feedback
- UDF architecture with `StateFlow<ViewState>` and `ViewIntent`

Run it:

```bash
./gradlew :sample:installDebug
```

For local, non-committed sample credentials, set Gradle properties in `~/.gradle/gradle.properties`:

```properties
ECHO_SAMPLE_HOST=your-host
ECHO_SAMPLE_PORT=8080
ECHO_SAMPLE_USE_TLS=false
ECHO_SAMPLE_APP_KEY=your-app-key
ECHO_SAMPLE_AUTH_ENDPOINT=http://your-host/broadcasting/auth
```

> Tip: In the sample UI, provide host without scheme (for example `staging.example.com`), then toggle TLS based on your endpoint.

---

## Testing

```bash
# Run unit tests
./gradlew :core:testDebugUnitTest

# Run with coverage report
./gradlew :core:createDebugUnitTestCoverageReport
# Report: core/build/reports/coverage/test/debug/index.html

# Lint with ktlint
./gradlew :core:ktlintCheck :sample:ktlintCheck

# Auto-format
./gradlew :core:ktlintFormat :sample:ktlintFormat

# Build the library
./gradlew :core:assemble
```

Current coverage: **≥ 80% line coverage** (40 tests across 8 test classes).

---

## Project Structure

```
echo-android/
├── core/                          # Library module
│   └── src/main/java/.../echo/
│       ├── Echo.kt                # Entry point & DSL builder
│       ├── EchoClient.kt          # Public client interface
│       ├── auth/                   # Authenticator interface
│       ├── channel/                # Channel & PresenceChannel interfaces
│       ├── connection/             # Connection, reconnection manager
│       ├── data/protocol/          # Pusher protocol frame models
│       ├── engine/                 # Pluggable WebSocket engine
│       ├── error/                  # EchoError sealed hierarchy
│       ├── internal/               # Client/channel/router implementations
│       ├── serialization/          # Pluggable serializer
│       ├── state/                  # ConnectionState & ChannelState
│       └── utils/                  # Logger
├── sample/                        # Sample Android app
├── gradle/libs.versions.toml      # Centralized dependency versions
├── docs/                          # PRDs and specifications
└── skills/                        # AI agent skill definitions
```

---

## Contributing

Contributions are welcome! Please read the [Contributing Guide](CONTRIBUTING.md) before submitting a PR.

---

## License

Distributed under the **MIT License**. See [LICENSE.md](LICENSE.md) for details.

---

<div align="center">

Made with ❤️ for the Android & Laravel communities

</div>
