package io.github.adityaacodes.echo.channel

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

public interface PresenceChannel : EchoChannel {
    public val members: StateFlow<List<Member>>

    public interface Member {
        public val id: String
        public val info: JsonElement?
    }
}
