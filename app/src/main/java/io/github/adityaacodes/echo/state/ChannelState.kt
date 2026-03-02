package io.github.adityaacodes.echo.state

import io.github.adityaacodes.echo.error.EchoError

public sealed class ChannelState {
    public data object Unsubscribed : ChannelState()
    public data object Subscribing : ChannelState()
    public data object Subscribed : ChannelState()
    public data class Failed(public val reason: EchoError) : ChannelState()
}
