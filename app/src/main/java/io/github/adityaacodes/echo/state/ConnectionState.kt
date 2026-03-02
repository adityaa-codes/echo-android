package io.github.adityaacodes.echo.state

public sealed class ConnectionState {
    public data class Disconnected(public val reason: Throwable? = null) : ConnectionState()
    public data object Connecting : ConnectionState()
    public data class Connected(public val socketId: String) : ConnectionState()
    public data class Reconnecting(public val attempt: Int) : ConnectionState()
}
