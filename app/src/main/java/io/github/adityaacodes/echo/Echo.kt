package io.github.adityaacodes.echo

import io.github.adityaacodes.echo.auth.Authenticator

public object Echo {
    public fun create(block: EchoBuilder.() -> Unit): EchoClient {
        val builder = EchoBuilder().apply(block)
        return io.github.adityaacodes.echo.internal.EchoClientImpl(builder)
    }
}

public class EchoBuilder internal constructor() {
    internal val clientConfig = ClientConfig()
    internal val authConfig = AuthConfig()
    internal val loggingConfig = LoggingConfig()

    public fun client(block: ClientConfig.() -> Unit) {
        clientConfig.apply(block)
    }

    public fun auth(block: AuthConfig.() -> Unit) {
        authConfig.apply(block)
    }

    public fun logging(block: LoggingConfig.() -> Unit) {
        loggingConfig.apply(block)
    }

    public class ClientConfig internal constructor() {
        public var host: String = ""
        public var apiKey: String = ""
        public var cluster: String? = null
    }

    public class AuthConfig internal constructor() {
        public var authenticator: Authenticator? = null
        public var authEndpoint: String? = null
        public var tokenProvider: (() -> String)? = null
    }

    public class LoggingConfig internal constructor() {
        public var level: Int = 0 // Will replace with enum later if needed
        public var logger: ((String) -> Unit)? = null
    }
}
