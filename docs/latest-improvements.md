# Echo Android SDK - Architectural Improvements & Roadmap

Based on an analysis of older legacy implementations (like `suuz-android`), current Kotlin coroutine best practices, and the latest architectural guidelines for modern Android networking, the following implementation strategy outlines how the `echo-android` library will become deeply robust, thread-safe, and production-ready.

## The Implementation Strategy

### Phase 1: Concurrency and Thread Safety (Immediate Priority)
Currently, if a developer calls `connect()` and `disconnect()` rapidly or from different threads (e.g., rapid UI clicks), the connection engine could spawn overlapping connection jobs or leak WebSocket sessions due to race conditions.
*   **Action:** Introduce `kotlinx.coroutines.sync.Mutex` to guard the `connect()` and `disconnect()` lifecycles.
*   **Action:** Use an `AtomicReference` or `Mutex` lock for the `WebSocketSession`. When an external caller triggers a message send (like `channel.whisper()`), the send function must safely verify the session is active without causing a `ConcurrentModificationException`.

### Phase 2: Decouple the Reconnection Engine (Clean Architecture)
Currently, the core connection engine recursively calls `connectInternal(attempt + 1)`. This violates the Single Responsibility Principle, entangling network I/O with backoff mathematics.
*   **Action:** Extract a `ReconnectionManager` interface.
*   **Action:** Implement an `ExponentialBackoffReconnectionManager`. It will observe the connection's `StateFlow<ConnectionState>`, and if it detects a `Disconnected(reason, isUserInitiated = false)`, it will take over.
*   **Action:** Utilize Coroutine `delay()` with an exponential backoff formula (with randomized jitter to prevent "thundering herd" DDoS effects on the Reverb server when a network drops).

### Phase 3: Deterministic Ping/Pong Protocol
The current activity timeout works, but it is loose. If a consumer wants to manually verify the connection is alive before sending critical data, they cannot.
*   **Action:** Implement a `CompletableDeferred<Boolean>` tied to a `pingMutex`. 
*   **Action:** When `ping()` is called, it fires the `pusher:ping` JSON frame and awaits the deferred. When the `handleRawText` function sees a `pusher:pong`, it completes the deferred.
*   **Action:** Expose `suspend fun ping(timeoutMillis: Long): Boolean` on the public `EchoClient` interface. This allows developers to manually probe the Reverb server.

### Phase 4: Global Error Bus (Observability)
Currently, errors are primarily surfaced through the `ConnectionState.Disconnected` data payload. However, if a user fails to authenticate to a specific `private-` channel, that shouldn't disconnect the entire socket, but the developer still needs to know about the error.
*   **Action:** Add `val errors: SharedFlow<EchoError>` to the `EchoClient` interface.
*   **Action:** Use `MutableSharedFlow(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)`.
*   **Action:** Emit all non-fatal protocol errors, parsing errors, and authentication failures here, allowing the app developer to have a single central place to log errors to Crashlytics or show UI Toasts.

### Phase 5: Modern Flow Backpressure Handling
WebSocket servers like Laravel Reverb can fire hundreds of events per second (e.g., live cursor tracking on presence channels).
*   **Action:** Ensure the internal `_incomingFrames` SharedFlow uses `BufferOverflow.DROP_OLDEST` to prevent Coroutine memory from bloating if the UI cannot process the frames fast enough.

---

## Alignment with Industry Best Practices

By adhering to this strategy, the library aligns perfectly with absolute latest Android development standards:

1.  **Cold vs. Hot Streams:** The SDK correctly uses `StateFlow` (Hot) for connection states (meaning the UI always gets the latest state upon recomposition) and `SharedFlow` for global event buses, matching Google's recommended patterns for WebSockets in Kotlin.
2.  **Lifecycle Awareness:** Consumers of the SDK can safely use `Lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)` in their ViewModels/UI. This ensures they stop collecting WebSocket flows when the app goes into the background, saving battery and CPU without severing the persistent connection underneath.
3.  **WSS by Default:** Encouraging `wss://` (via `useTls = true` in `ClientConfig`) is strictly mandated by Android's Network Security Configuration, which bans cleartext HTTP/WS traffic on modern Android APIs.
4.  **Coroutines > Callbacks:** The industry has entirely moved away from traditional callback interfaces (`WebSocketListener`) in favor of Coroutine `Channel` and `Flow` wrappers, making asynchronous streaming safe, cancellation-aware, and highly declarative.