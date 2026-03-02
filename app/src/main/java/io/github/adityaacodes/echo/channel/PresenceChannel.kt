package io.github.adityaacodes.echo.channel

import kotlinx.coroutines.flow.StateFlow

public interface PresenceChannel : EchoChannel {
    public val members: StateFlow<List<Member>>

    // Simple default Member representation, could be generic or mapped later
    public interface Member {
        public val id: String
        public val info: Map<String, String> // or proper JSON representation
    }
}
