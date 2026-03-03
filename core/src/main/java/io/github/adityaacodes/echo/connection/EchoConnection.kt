package io.github.adityaacodes.echo.connection

import io.github.adityaacodes.echo.error.EchoError
import io.github.adityaacodes.echo.state.ConnectionState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the underlying WebSocket connection lifecycle.
 */
public interface EchoConnection {
    /** A hot Flow representing the current state of the WebSocket. */
    public val state: StateFlow<ConnectionState>

    /** A global stream of all non-fatal protocol and network errors. */
    public val errors: SharedFlow<EchoError>

    /**
     * Initiates the connection to the server. If already connected or connecting, does nothing.
     */
    public suspend fun connect()

    /**
     * Disconnects from the server gracefully.
     */
    public suspend fun disconnect()

    /**
     * Manually triggers a Ping to the server. Completes when a Pong is received.
     * @param timeoutMillis The maximum time to wait for a Pong.
     * @return True if the Pong was received within the timeout, false otherwise.
     */
    public suspend fun ping(timeoutMillis: Long = 5000L): Boolean
}
