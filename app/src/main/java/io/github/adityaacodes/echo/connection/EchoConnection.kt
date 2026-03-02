package io.github.adityaacodes.echo.connection

import io.github.adityaacodes.echo.state.ConnectionState
import kotlinx.coroutines.flow.StateFlow

public interface EchoConnection {
    public val state: StateFlow<ConnectionState>
    
    public suspend fun connect()
    public suspend fun disconnect()
}
