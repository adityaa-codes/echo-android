package io.github.adityaacodes.echo

import io.github.adityaacodes.echo.auth.Authenticator

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
    }

    /**
     * Configuration for SDK logging.
     */
    public class LoggingConfig internal constructor() {
        /** The severity level to log at. */
        public var level: Int = 0 // Will replace with enum later if needed
        /** An optional custom logger function. */
        public var logger: ((String) -> Unit)? = null
    }
}
