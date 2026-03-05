package io.github.adityaacodes.echo.engine

import kotlinx.coroutines.flow.Flow

/**
 * Pluggable WebSocket engine interface for the Echo SDK.
 */
public interface EchoEngine {
    /**
     * A stream of incoming text frames from the WebSocket.
     */
    public val incoming: Flow<String>

    /**
     * Initiates a connection to the specified URL.
     */
    public suspend fun connect(url: String)

    /**
     * Sends a raw text frame over the WebSocket.
     */
    public suspend fun send(data: String): Result<Unit>

    /**
     * Gracefully closes the WebSocket connection.
     */
    public suspend fun disconnect()
}
