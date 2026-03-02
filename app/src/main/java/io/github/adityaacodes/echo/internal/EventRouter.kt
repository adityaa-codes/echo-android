package io.github.adityaacodes.echo.internal

import io.github.adityaacodes.echo.EchoEvent
import io.github.adityaacodes.echo.data.protocol.ConnectionEstablished
import io.github.adityaacodes.echo.data.protocol.ErrorFrame
import io.github.adityaacodes.echo.data.protocol.GenericEvent
import io.github.adityaacodes.echo.data.protocol.MemberAdded
import io.github.adityaacodes.echo.data.protocol.MemberRemoved
import io.github.adityaacodes.echo.data.protocol.PusherFrame
import io.github.adityaacodes.echo.data.protocol.SubscriptionSucceeded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull

internal class EventRouter(private val incomingFrames: SharedFlow<PusherFrame>) {

    val globalEvents: Flow<EchoEvent> = incomingFrames.mapNotNull { it.toEchoEvent() }

    fun channelEvents(channelName: String): Flow<PusherFrame> {
        return incomingFrames.filter { frame ->
            when (frame) {
                is GenericEvent -> frame.channel == channelName
                is SubscriptionSucceeded -> frame.channel == channelName
                is MemberAdded -> frame.channel == channelName
                is MemberRemoved -> frame.channel == channelName
                else -> false
            }
        }
    }

    private fun PusherFrame.toEchoEvent(): EchoEvent? {
        return when (this) {
            is GenericEvent -> EchoEvent(this.event, this.channel, this.data?.toString())
            is SubscriptionSucceeded -> EchoEvent("pusher_internal:subscription_succeeded", this.channel, this.data)
            is MemberAdded -> EchoEvent("pusher_internal:member_added", this.channel, this.data)
            is MemberRemoved -> EchoEvent("pusher_internal:member_removed", this.channel, this.data)
            is ConnectionEstablished -> EchoEvent("pusher:connection_established", null, this.data)
            is ErrorFrame -> EchoEvent(
                "pusher:error", 
                null, 
                this.data?.let { """{"message":"${it.message}","code":${it.code ?: "null"}}""" }
            )
            else -> null // Ping, Pong, Subscribe, Unsubscribe are not broadcast
        }
    }
}
