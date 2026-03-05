package io.github.adityaacodes.echo.state

import io.github.adityaacodes.echo.error.EchoError

public sealed class ConnectionState {
    public data class Disconnected(public val reason: Throwable? = null) : ConnectionState()
    public data object Connecting : ConnectionState()
    public data class Connected(public val socketId: String) : ConnectionState()
    public data class Reconnecting(public val attempt: Int) : ConnectionState()
    public data object Suspended : ConnectionState()
    public data class Failed(public val error: EchoError) : ConnectionState()
}
