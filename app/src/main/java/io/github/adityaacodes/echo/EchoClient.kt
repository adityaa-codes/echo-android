package io.github.adityaacodes.echo

import io.github.adityaacodes.echo.channel.EchoChannel
import io.github.adityaacodes.echo.channel.PresenceChannel
import io.github.adityaacodes.echo.connection.EchoConnection

public interface EchoClient : EchoConnection {
    public fun channel(name: String): EchoChannel
    public fun private(name: String): EchoChannel
    public fun join(name: String): PresenceChannel
    public fun leave(name: String)
}
