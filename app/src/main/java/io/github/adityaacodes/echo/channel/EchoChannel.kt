package io.github.adityaacodes.echo.channel

import io.github.adityaacodes.echo.state.ChannelState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public interface EchoChannel {
    public val name: String
    public val state: StateFlow<ChannelState>

    public fun listen(event: String): Flow<String> // Will be parsed to JsonElement or string later
    public suspend fun whisper(event: String, data: String): Result<Unit>
    public fun listenForWhisper(event: String): Flow<String>
    
    public fun leave()
}
