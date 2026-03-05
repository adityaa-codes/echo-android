package io.github.adityaacodes.echo.channel

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * A specialized channel that tracks users currently subscribed.
 */
public interface PresenceChannel : EchoChannel {
    /** A hot Flow representing the current list of active members. */
    public val members: StateFlow<List<Member>>

    /**
     * Listens for updates to an existing member's information.
     */
    public fun updating(callback: (Member) -> Unit): PresenceChannel

    /**
     * Represents a single authenticated user in a presence channel.
     */
    public interface Member {
        /** The unique identifier of the member. */
        public val id: String
        /** Arbitrary user information provided by the authentication server. */
        public val info: JsonElement?
    }
}
