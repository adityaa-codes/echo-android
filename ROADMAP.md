# Echo SDK — Roadmap

This document outlines what's planned for upcoming releases. Items are ordered by developer impact and implementation complexity within each milestone.

---

## ✅ v1.0.0 — Initial Stable Release _(current)_

- Kotlin DSL configuration (`Echo.create { ... }`)
- Public, private, and presence channels (Pusher Protocol v7)
- `StateFlow<ConnectionState>` — `Disconnected`, `Connecting`, `Connected`, `Reconnecting`
- `SharedFlow<EchoError>` — typed, sealed error hierarchy
- Automatic reconnection with exponential backoff and jitter
- Protocol-level ping/pong keep-alive
- Suspending `Authenticator` interface with pre-flight auth
- Client whispers on private channels
- Auto-resubscribe on reconnect
- Pluggable `EchoEngine` and `EchoSerializer` interfaces
- KDoc on all public API members

---

## 🔜 v1.1.0 — Protocol Correctness

> Target: works with **Laravel Reverb**, **Soketi**, and **Sockudo**.

### 1. `ChannelState.Failed` + Per-Channel Error Handling

Today, when a private or presence channel auth fails, Echo emits a global `EchoError.Auth` but leaves the channel stuck in `Subscribing`. The Pusher protocol sends `pusher_internal:subscription_error` for this case.

**Plan:**
- Add `ChannelState.Failed(error: EchoError)` to the `ChannelState` sealed class.
- Handle `pusher_internal:subscription_error` in `EventRouter` — transition the affected channel to `Failed` and emit a per-channel error.
- Expose a `channel.error: StateFlow<EchoError?>` property on `EchoChannel`.

**Impact:** Apps can show per-channel error UI instead of relying solely on the global error stream.

---

### 2. `ConnectionState.Suspended` + `ConnectionState.Failed`

Today, Echo has a single `Reconnecting` state that retries indefinitely. Two distinct terminal-ish states are missing:

- **`Suspended`** — entered after prolonged failure (configurable `suspendAfterMs`, default 2 min). Retries slow down (e.g., every 30s). Apps show a "degraded connection" UI.
- **`Failed`** — entered on unrecoverable protocol error codes (Pusher codes 4001–4009: invalid key, app disabled, etc.). No further retries. Requires explicit `echo.connect()` to restart.

**Plan:**
- Extend `ConnectionState` sealed class with `Suspended` and `Failed(error: EchoError)`.
- Update `ExponentialBackoffReconnectionManager` to escalate state after threshold.
- Parse Pusher close codes in `KtorEchoConnection` and emit `Failed` for fatal codes.
- Add `suspendAfterMs` config to the reconnection DSL block.

**Impact:** Stops infinite retry loops on bad credentials. Lets apps surface meaningful network-degraded states.

---

### 3. Presence `update` Event

When a presence member changes their payload (e.g., updates their display name or status) without leaving and re-joining, Reverb ≥1.4 / Soketi / Sockudo fires `pusher_internal:member_updated`. Echo currently ignores this frame.

**Plan:**
- Add `pusher_internal:member_updated` to `PusherFrame` sealed class.
- Update `PresenceChannelImpl` to emit an `updating {}` callback and update the member in the internal members map.
- Expose `channel.updating { member -> }` on `PresenceChannel`.

**Impact:** Presence channels reflect live member metadata without members needing to leave/join.

---

### 4. Non-Disruptive Token Refresh

Today, auth token renewal requires the channel to be re-subscribed. For long-lived connections, tokens (JWTs) may expire while the channel is active.

**Plan:**
- Accept a `tokenExpiryMs` hint in the `auth { }` DSL block.
- Launch a proactive refresh coroutine that calls the `Authenticator` before expiry and silently re-sends `pusher:subscribe` for affected channels without tearing down the WebSocket.
- Emit `EchoError.Auth` on failure without disconnecting.

**Impact:** Zero-downtime token rotation for long-lived sessions (chat, live dashboards).

---

## 🔜 v1.2.0 — REST Interface

> Target: **Soketi** and **Sockudo** (both expose the Pusher HTTP Events API).  
> Note: Laravel Reverb does **not** expose the Pusher HTTP Events API directly — use Laravel's Broadcasting backend instead.

### 5. HTTP Publish Interface

Add a lightweight REST client that wraps the [Pusher HTTP Events API](https://pusher.com/docs/channels/library_auth_reference/rest-api/) for fire-and-forget event publishing without an active WebSocket connection.

**Plan:**
- Expose `echo.rest()` returning an `EchoRestClient` interface.
- `suspend fun EchoRestClient.trigger(channel: String, event: String, data: Any): Result<Unit>`
- `suspend fun EchoRestClient.triggerBatch(events: List<EchoEvent>): Result<Unit>`
- Authentication via HMAC-SHA256 request signing (same as Pusher REST spec).
- Useful from background services, WorkManager tasks, or one-shot publishes.

**Impact:** Publish events without holding an open WebSocket — essential for background workers and server-adjacent Android code.

---

## 🔜 v2.0.0 — Sockudo Extensions

> These features are **Sockudo-exclusive** protocol extensions and are off by default. Reverb and Soketi silently ignore the extra subscribe frame fields, so enabling them on a non-Sockudo server is harmless.

### 6. Server-Side Tag Filtering

Sockudo supports subscribing with filter expressions evaluated server-side. Only messages whose tags match the filter are delivered — reducing bandwidth without any client-side filtering logic.

**Plan:**
- Add a `Filter` sealed class DSL:
  ```kotlin
  Filter.eq("symbol", "BTC")
  Filter.and(Filter.eq("exchange", "NYSE"), Filter.gt("volume", "1000"))
  Filter.or(Filter.eq("symbol", "BTC"), Filter.eq("symbol", "ETH"))
  ```
- Serialize the filter to JSON and attach it to the `pusher:subscribe` frame.
- Extend `echo.channel("name") { filter = Filter.eq("priority", "high") }` in the channel builder.
- Guard behind a feature flag in `EchoConfig`: `features { tagFiltering = true }`.

**Impact:** Subscribe to one shared broadcast channel and receive only messages relevant to the client — ideal for market data feeds, IoT dashboards, and notification streams.

---

### 7. Delta Compression

Sockudo can transmit only the diff between consecutive messages (Fossil or XDelta3 algorithms), saving 30–95% bandwidth on high-frequency update channels.

**Plan:**
- Extend `PusherFrame` with `DeltaCacheSync` and `Delta` frame types.
- Add delta negotiation to the subscribe frame (`"delta": {"enabled": true, "algorithms": ["fossil"]}`).
- Integrate a Fossil/XDelta3 decompression library (evaluation needed — JNI wrapper vs. pure-Kotlin port).
- Maintain a per-channel, per-conflation-key delta state cache.
- Expose `channel.enableDelta(algorithm = DeltaAlgorithm.FOSSIL)` opt-in in the channel DSL.
- Guard behind `features { deltaCompression = true }`.

**Impact:** Dramatically reduces data usage for apps streaming high-frequency updates (live prices, sensor readings, game state). Only activates on Sockudo; ignored on Reverb/Soketi.

---

## 💡 Under Consideration (No Milestone Yet)

| Feature | Notes |
|---|---|
| **Channel Occupancy** | Would require server-side support. Sockudo exposes Prometheus metrics but no occupancy WebSocket events. |
| **E2E Channel Encryption** | AES-256 client-side encrypt/decrypt before publish. Server-agnostic. Requires key distribution strategy. |
| **Presence History** | Requires server-side storage. Not supported by any Pusher-compatible server today. |
| **MQTT / SSE transports** | Pusher protocol is WebSocket-only. Out of scope. |
| **Push Notifications** | Platform-level feature (FCM/APNs). Out of scope for this SDK. |

---

## Contributing

If you'd like to work on a roadmap item, open an issue to discuss the approach before submitting a PR. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.
