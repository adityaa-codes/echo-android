package io.github.adityaacodes.echo.channel

import io.github.adityaacodes.echo.state.ChannelState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a single subscription to a Pusher/Reverb channel.
 */
public interface EchoChannel {
    /** The name of the channel. */
    public val name: String
    
    /** The current connection/subscription state of the channel. */
    public val state: StateFlow<ChannelState>

    /**
     * Listens for a specific event broadcasted on this channel.
     * @param event The exact name of the event.
     * @return A hot Flow emitting the JSON string payloads of the event.
     */
    public fun listen(event: String): Flow<String>

    /**
     * Sends a client event (whisper) to other users on this channel.
     * Only valid for private and presence channels.
     * @param event The name of the event (the `client-` prefix is added automatically).
     * @param data The JSON string payload to send.
     */
    public suspend fun whisper(event: String, data: String): Result<Unit>
    
    /**
     * Listens for client events (whispers) from other users.
     * @param event The name of the event (the `client-` prefix is added automatically).
     * @return A hot Flow emitting the JSON string payloads.
     */
    public fun listenForWhisper(event: String): Flow<String>
    
    /**
     * Unsubscribes from this channel immediately.
     */
    public fun leave()
}
