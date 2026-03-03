package io.github.adityaacodes.echo

import io.github.adityaacodes.echo.channel.EchoChannel
import io.github.adityaacodes.echo.channel.PresenceChannel
import io.github.adityaacodes.echo.connection.EchoConnection
import io.github.adityaacodes.echo.error.EchoError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The main client for interacting with a Pusher/Reverb WebSocket server.
 * Provides methods for joining public, private, and presence channels.
 */
public interface EchoClient : EchoConnection {

    /**
     * A global stream of all events received across all channels.
     */
    public val globalEvents: Flow<EchoEvent>

    /**
     * A global stream of all non-fatal protocol and network errors.
     */
    public override val errors: SharedFlow<EchoError>

    /** The current connection socket ID, if connected. Null otherwise. */
    public val socketId: String?

    /** A list of all currently active (subscribed or connecting) channels. */
    public val activeChannels: List<EchoChannel>

    /**
     * Manually triggers a Ping to the server. Completes when a Pong is received.
     * @param timeoutMillis The maximum time to wait for a Pong.
     * @return True if the Pong was received within the timeout, false otherwise.
     */
    public override suspend fun ping(timeoutMillis: Long): Boolean

    /**
     * Subscribes to a public channel.
     * @param name The name of the channel.
     * @return The [EchoChannel] instance for listening to events.
     */
    public fun channel(name: String): EchoChannel

    /**
     * Subscribes to a private channel. The client will authenticate before subscribing.
     * @param name The name of the private channel (the `private-` prefix is optional and will be added automatically).
     * @return The [EchoChannel] instance for listening to events.
     */
    public fun private(name: String): EchoChannel

    /**
     * Subscribes to a presence channel to track active members.
     * @param name The name of the presence channel (the `presence-` prefix is optional and will be added automatically).
     * @return The [PresenceChannel] instance.
     */
    public fun presence(name: String): PresenceChannel

    /**
     * Unsubscribes from the given channel and cleans up internal state.
     * @param name The exact name of the channel to leave.
     */
    public fun leave(name: String)
}
