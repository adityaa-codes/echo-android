package io.github.adityaacodes.echo.internal

import io.github.adityaacodes.echo.auth.Authenticator
import io.github.adityaacodes.echo.channel.EchoChannel
import io.github.adityaacodes.echo.connection.KtorEchoConnection
import io.github.adityaacodes.echo.data.protocol.GenericEvent
import io.github.adityaacodes.echo.data.protocol.PusherFrame
import io.github.adityaacodes.echo.data.protocol.SubscribeCommand
import io.github.adityaacodes.echo.data.protocol.SubscriptionSucceeded
import io.github.adityaacodes.echo.data.protocol.UnsubscribeCommand
import io.github.adityaacodes.echo.data.protocol.WhisperCommand
import io.github.adityaacodes.echo.error.EchoError
import io.github.adityaacodes.echo.state.ChannelState
import io.github.adityaacodes.echo.state.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class EchoChannelImpl(
    override val name: String,
    private val connection: KtorEchoConnection,
    private val eventRouter: EventRouter,
    private val scope: CoroutineScope,
    private val json: Json,
    private val authenticator: Authenticator?,
    private val onAuthFailure: (suspend () -> Unit)?,
    private val onLeave: (String) -> Unit
) : EchoChannel {

    private val _state = MutableStateFlow<ChannelState>(ChannelState.Unsubscribed)
    override val state: StateFlow<ChannelState> = _state.asStateFlow()

    private var subscriptionJob: Job? = null
    private var connectionStateJob: Job? = null
    private var hasAttemptedAutoRefresh = false

    init {
        // Listen for subscription succeeded
        scope.launch {
            eventRouter.channelEvents(name)
                .filterIsInstance<SubscriptionSucceeded>()
                .collect {
                    _state.value = ChannelState.Subscribed
                }
        }

        connectionStateJob = scope.launch {
            connection.state.collect { connState ->
                when (connState) {
                    is ConnectionState.Connected -> {
                        doSubscribe(connState.socketId)
                    }
                    is ConnectionState.Disconnected, is ConnectionState.Reconnecting -> {
                        if (_state.value is ChannelState.Subscribed) {
                            _state.value = ChannelState.Subscribing
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    internal fun subscribe() {
        // We trigger a forced resubscribe only if the user somehow manually requested it, 
        // but typically the connectionStateJob handles it.
        // To be safe, if we are already connected, we can try to subscribe immediately.
        val currentState = connection.state.value
        if (currentState is ConnectionState.Connected) {
            doSubscribe(currentState.socketId)
        } else {
            _state.value = ChannelState.Subscribing
        }
    }

    override fun retry() {
        val currentState = state.value
        if (currentState is ChannelState.Failed) {
            val connState = connection.state.value
            if (connState is ConnectionState.Connected) {
                doSubscribe(connState.socketId)
            } else {
                _state.value = ChannelState.Subscribing
            }
        }
    }

    private fun doSubscribe(socketId: String) {
        // If we are already subscribed and the socket hasn't changed (though it usually does on reconnect),
        // we might not want to resubscribe. But if this is called, it means we connected/reconnected.
        _state.value = ChannelState.Subscribing
        
        subscriptionJob?.cancel()
        subscriptionJob = scope.launch {
            var authSignature: String? = null
            var channelData: String? = null

            if (name.startsWith("private-") || name.startsWith("presence-")) {
                if (authenticator == null) {
                    val error = EchoError.Auth(400, "No authenticator provided")
                    _state.value = ChannelState.Failed(error)
                    connection.emitError(error)
                    return@launch
                }
                
                var result = authenticator.authenticate(socketId, name)
                
                // Automated token refresh retry logic
                if (result.isFailure && !hasAttemptedAutoRefresh && onAuthFailure != null) {
                    onAuthFailure.invoke()
                    hasAttemptedAutoRefresh = true
                    result = authenticator.authenticate(socketId, name)
                }

                if (result.isFailure) {
                    val ex = result.exceptionOrNull()
                    val authError = if (ex is EchoError.Auth) ex else EchoError.Auth(-1, ex?.message ?: "Unknown auth error")
                    _state.value = ChannelState.Failed(authError)
                    connection.emitError(authError)
                    return@launch
                } else {
                    hasAttemptedAutoRefresh = false // Reset on success
                    val jsonResponse = result.getOrNull() ?: ""
                    try {
                        val element = json.parseToJsonElement(jsonResponse).jsonObject
                        authSignature = element["auth"]?.jsonPrimitive?.content
                        channelData = element["channel_data"]?.jsonPrimitive?.content
                    } catch (e: Exception) {
                        val error = EchoError.Auth(-1, "Invalid auth response format")
                        _state.value = ChannelState.Failed(error)
                        connection.emitError(error)
                        return@launch
                    }
                }
            }

            // Send subscribe command
            val command = SubscribeCommand(data = SubscribeCommand.SubscribeData(
                channel = name,
                auth = authSignature,
                channel_data = channelData
            ))
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
        if (!name.startsWith("private-") && !name.startsWith("presence-")) {
            return Result.failure(IllegalStateException("Whisper not allowed for public channels"))
        }
        val eventName = if (event.startsWith("client-")) event else "client-$event"
        val element = try {
            json.parseToJsonElement(data)
        } catch (e: Exception) {
            JsonPrimitive(data)
        }
        val command = WhisperCommand(
            event = eventName,
            channel = name,
            data = element
        )
        val jsonString = json.encodeToString(PusherFrame.serializer(), command)
        return connection.sendRaw(jsonString)
    }

    override fun listenForWhisper(event: String): Flow<String> {
        val eventName = if (event.startsWith("client-")) event else "client-$event"
        return listen(eventName)
    }

    override fun leave() {
        subscriptionJob?.cancel()
        connectionStateJob?.cancel()
        _state.value = ChannelState.Unsubscribed
        
        scope.launch {
            // Only send unsubscribe if we are connected
            if (connection.state.value is ConnectionState.Connected) {
                val command = UnsubscribeCommand(data = UnsubscribeCommand.UnsubscribeData(channel = name))
                val jsonString = json.encodeToString(PusherFrame.serializer(), command)
                connection.sendRaw(jsonString)
            }
        }
        
        onLeave(name)
    }
}
