package io.github.adityaacodes.echo.connection

import io.github.adityaacodes.echo.state.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the underlying WebSocket connection lifecycle.
 */
public interface EchoConnection {
    /** A hot Flow representing the current state of the WebSocket. */
    public val state: StateFlow<ConnectionState>

    /**
     * Initiates the connection to the server. If already connected or connecting, does nothing.
     */
    public suspend fun connect()

    /**
     * Disconnects from the server gracefully.
     */
    public suspend fun disconnect()
}
