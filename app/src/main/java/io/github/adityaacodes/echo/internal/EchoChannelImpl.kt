package io.github.adityaacodes.echo.internal

import io.github.adityaacodes.echo.channel.EchoChannel
import io.github.adityaacodes.echo.connection.KtorEchoConnection
import io.github.adityaacodes.echo.data.protocol.GenericEvent
import io.github.adityaacodes.echo.data.protocol.PusherFrame
import io.github.adityaacodes.echo.data.protocol.SubscribeCommand
import io.github.adityaacodes.echo.data.protocol.SubscriptionSucceeded
import io.github.adityaacodes.echo.data.protocol.UnsubscribeCommand
import io.github.adityaacodes.echo.state.ChannelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal class EchoChannelImpl(
    override val name: String,
    private val connection: KtorEchoConnection,
    private val eventRouter: EventRouter,
    private val scope: CoroutineScope,
    private val json: Json,
    private val onLeave: (String) -> Unit
) : EchoChannel {

    private val _state = MutableStateFlow<ChannelState>(ChannelState.Unsubscribed)
    override val state: StateFlow<ChannelState> = _state.asStateFlow()

    private var subscriptionJob: Job? = null

    init {
        subscribe()
    }

    internal fun subscribe() {
        if (_state.value is ChannelState.Subscribed || _state.value is ChannelState.Subscribing) return
        
        _state.value = ChannelState.Subscribing
        
        subscriptionJob = scope.launch {
            // Listen for subscription succeeded
            launch {
                eventRouter.channelEvents(name)
                    .filterIsInstance<SubscriptionSucceeded>()
                    .collect {
                        _state.value = ChannelState.Subscribed
                    }
            }
            
            // Send subscribe command
            val command = SubscribeCommand(data = SubscribeCommand.SubscribeData(channel = name))
            val jsonString = json.encodeToString(PusherFrame.serializer(), command)
            connection.sendRaw(jsonString)
        }
    }

    override fun listen(event: String): Flow<String> {
        return eventRouter.channelEvents(name)
            .filterIsInstance<GenericEvent>()
            .mapNotNull { frame ->
                if (frame.event == event) {
                    frame.data?.toString() ?: ""
                } else {
                    null
                }
            }
    }

    override suspend fun whisper(event: String, data: String): Result<Unit> {
        return Result.failure(NotImplementedError("Whisper not implemented for public channels"))
    }

    override fun listenForWhisper(event: String): Flow<String> {
        throw NotImplementedError("Whisper listening not implemented")
    }

    override fun leave() {
        subscriptionJob?.cancel()
        _state.value = ChannelState.Unsubscribed
        
        scope.launch {
            val command = UnsubscribeCommand(data = UnsubscribeCommand.UnsubscribeData(channel = name))
            val jsonString = json.encodeToString(PusherFrame.serializer(), command)
            connection.sendRaw(jsonString)
        }
        
        onLeave(name)
    }
}
