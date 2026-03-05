package io.github.adityaacodes.echo

import io.github.adityaacodes.echo.auth.Authenticator
import io.github.adityaacodes.echo.engine.EchoEngine
import io.github.adityaacodes.echo.serialization.DefaultEchoSerializer
import io.github.adityaacodes.echo.serialization.EchoSerializer

/**
 * The main entry point for creating an [EchoClient] instance.
 */
public object Echo {
    /**
     * Creates a new [EchoClient] configured by the provided [block].
     */
    public fun create(block: EchoBuilder.() -> Unit): EchoClient {
        val builder = EchoBuilder().apply(block)
        return io.github.adityaacodes.echo.internal.EchoClientImpl(builder)
    }
}

/**
 * DSL builder for configuring the [EchoClient].
 */
public class EchoBuilder internal constructor() {
    internal val clientConfig = ClientConfig()
    internal val authConfig = AuthConfig()
    internal val loggingConfig = LoggingConfig()
    internal val reconnectionConfig = ReconnectionConfig()

    /**
     * Configures base client properties like host and API key.
     */
    public fun client(block: ClientConfig.() -> Unit) {
        clientConfig.apply(block)
    }

    /**
     * Configures authentication for private and presence channels.
     */
    public fun auth(block: AuthConfig.() -> Unit) {
        authConfig.apply(block)
    }

    /**
     * Configures logging preferences.
     */
    public fun logging(block: LoggingConfig.() -> Unit) {
        loggingConfig.apply(block)
    }

    /**
     * Configures automatic reconnection behavior.
     *
     * @sample
     * ```
     * Echo.create {
     *     reconnection {
     *         maxAttempts = 5
     *         baseDelayMs = 2000
     *         maxDelayMs = 60_000
     *     }
     * }
     * ```
     */
    public fun reconnection(block: ReconnectionConfig.() -> Unit) {
        reconnectionConfig.apply(block)
    }

    /**
     * Configuration properties for the WebSocket connection.
     */
    public class ClientConfig internal constructor() {
        /** The WebSocket host URL, e.g., "ws.example.com" or "10.0.2.2" */
        public var host: String = ""
        /** The Pusher App Key or Reverb API Key */
        public var apiKey: String = ""
        /** An optional cluster, e.g., "mt1". If provided, it overrides [host] with Pusher's default cluster URLs. */
        public var cluster: String? = null
        /** Whether to use a secure wss:// connection. Defaults to true. */
        public var useTls: Boolean = true
        /** The port to connect to. Defaults to null, which relies on standard ws (80) or wss (443) ports. */
        public var port: Int? = null
        /**
         * Optional custom WebSocket engine factory. If omitted, the SDK uses [io.github.adityaacodes.echo.engine.KtorEchoEngine].
         */
        public var engineFactory: (() -> EchoEngine)? = null
        /**
         * Serializer used for protocol frame encoding/decoding. Defaults to [DefaultEchoSerializer].
         */
        public var serializer: EchoSerializer = DefaultEchoSerializer()
    }

    /**
     * Configuration for channel authentication.
     */
    public class AuthConfig internal constructor() {
        /** The [Authenticator] responsible for fetching authorization signatures. */
        public var authenticator: Authenticator? = null
        /** The endpoint URL for authentication (if used by an internal HTTP authenticator). */
        public var authEndpoint: String? = null
        /** An optional callback providing bearer tokens for HTTP auth requests. */
        public var tokenProvider: (() -> String)? = null
        /**
         * An optional callback invoked when channel authentication fails.
         * Useful for the application to refresh its JWT/Session and then let the SDK automatically retry.
         */
        public var onAuthFailure: (suspend () -> Unit)? = null
        
        /**
         * An optional hint (in milliseconds) for token expiry. If set, the SDK will proactively 
         * attempt to refresh the token and re-subscribe to private/presence channels before it expires.
         */
        public var tokenExpiryMs: Long? = null
    }

    /**
     * Configuration for SDK logging.
     */
    public class LoggingConfig internal constructor() {
        /** Enable internal Timber logging. Defaults to false. */
        public var enabled: Boolean = false
        /** An optional custom logger function. Overrides Timber if provided. */
        public var logger: ((String) -> Unit)? = null
    }

    /**
     * Configuration for automatic reconnection with exponential backoff.
     */
    public class ReconnectionConfig internal constructor() {
        /** Maximum number of reconnection attempts before giving up. Defaults to 10. */
        public var maxAttempts: Int = 10
        /** Initial backoff delay in milliseconds. Defaults to 1000 (1 second). */
        public var baseDelayMs: Long = 1000
        /** Maximum backoff delay in milliseconds. Defaults to 30000 (30 seconds). */
        public var maxDelayMs: Long = 30_000
        /** Time after which reconnection attempts transition the state to Suspended. Defaults to 120000 (2 minutes). */
        public var suspendAfterMs: Long = 120_000
    }
}
