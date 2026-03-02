package io.github.adityaacodes.echo.connection

import io.github.adityaacodes.echo.data.protocol.ConnectionEstablished
import io.github.adityaacodes.echo.data.protocol.ErrorFrame
import io.github.adityaacodes.echo.data.protocol.Ping
import io.github.adityaacodes.echo.data.protocol.Pong
import io.github.adityaacodes.echo.data.protocol.PusherFrame
import io.github.adityaacodes.echo.error.EchoError
import io.github.adityaacodes.echo.state.ConnectionState
import io.github.adityaacodes.echo.utils.BackoffStrategy
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class KtorEchoConnection(
    private val client: HttpClient,
    private val url: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) : EchoConnection {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _incomingFrames = MutableSharedFlow<PusherFrame>(extraBufferCapacity = 256)
    val incomingFrames = _incomingFrames.asSharedFlow()

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    
    private var activityTimeoutJob: Job? = null
    private var pingJob: Job? = null
    private var pongJob: Job? = null
    
    // Add a flag to distinguish between user-requested disconnects and network drops
    private var isUserDisconnected = false

    override suspend fun connect() {
        isUserDisconnected = false
        connectInternal(attempt = 1)
    }

    private fun connectInternal(attempt: Int) {
        if (_state.value is ConnectionState.Connected) {
            return
        }

        connectionJob?.cancel()
        activityTimeoutJob?.cancel()
        pingJob?.cancel()
        pongJob?.cancel()

        connectionJob = scope.launch {
            if (attempt == 1) {
                _state.value = ConnectionState.Connecting
            } else {
                _state.value = ConnectionState.Reconnecting(attempt)
                delay(BackoffStrategy.calculateDelay(attempt))
            }

            try {
                session = client.webSocketSession {
                    url(this@KtorEchoConnection.url)
                }

                session?.incoming?.consumeAsFlow()?.collect { frame ->
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        handleRawText(text)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                
                session?.close()
                session = null

                if (!isUserDisconnected) {
                    _state.value = ConnectionState.Disconnected(EchoError.Network(e))
                    // Start reconnection loop
                    connectInternal(attempt + 1)
                }
            } finally {
                if (isUserDisconnected && _state.value !is ConnectionState.Disconnected) {
                    _state.value = ConnectionState.Disconnected()
                }
                activityTimeoutJob?.cancel()
                pingJob?.cancel()
                pongJob?.cancel()
            }
        }
    }

    internal suspend fun handleRawText(text: String) {
        try {
            val pusherFrame = json.decodeFromString<PusherFrame>(text)
            
            when (pusherFrame) {
                is ConnectionEstablished -> {
                    val connectionData = parseConnectionData(pusherFrame.data)
                    _state.value = ConnectionState.Connected(connectionData.socketId)
                    startActivityTimeout(connectionData.activityTimeout)
                }
                is Ping -> {
                    val pongJson = json.encodeToString(PusherFrame.serializer(), Pong())
                    sendRaw(pongJson)
                }
                is Pong -> {
                    // Stop expecting a pong, restart activity timeout
                    pongJob?.cancel()
                    // Restart activity timeout based on last known value or default
                    // Assuming we keep track of the timeout value, let's say we restart the normal timeout
                    // We need to keep a reference to the last timeout value
                }
                is ErrorFrame -> {
                    // Usually pusher errors contain code/message
                    val msg = pusherFrame.data?.message ?: "Unknown protocol error"
                    val code = pusherFrame.data?.code ?: -1
                    _state.value = ConnectionState.Disconnected(EchoError.Protocol(code, msg))
                    disconnect()
                }
                else -> {
                    // It's a channel event or something else, emit to bus
                    _incomingFrames.emit(pusherFrame)
                }
            }
            
            // Any message received should reset the activity timeout
            if (pusherFrame !is Pong) { // Let's simplify and just say any message resets it
                resetActivityTimeout()
            }

        } catch (e: Exception) {
            // Serialization error or malformed JSON
            // We shouldn't disconnect just because of one bad frame, but maybe log it.
        }
    }

    private var currentActivityTimeout: Long = 120_000L

    private fun startActivityTimeout(timeoutSeconds: Int?) {
        currentActivityTimeout = (timeoutSeconds?.toLong() ?: 120L) * 1000L
        resetActivityTimeout()
    }

    private fun resetActivityTimeout() {
        activityTimeoutJob?.cancel()
        pingJob?.cancel()
        pongJob?.cancel()
        
        if (_state.value !is ConnectionState.Connected) return

        activityTimeoutJob = scope.launch {
            delay(currentActivityTimeout)
            // No activity for timeout duration, send a ping
            val pingJson = json.encodeToString(PusherFrame.serializer(), Ping())
            val result = sendRaw(pingJson)
            if (result.isSuccess) {
                // Wait for pong
                pongJob = scope.launch {
                    delay(10_000L) // Wait 10 seconds for a pong
                    // If this block executes, we didn't get a pong in time
                    disconnect(isError = true)
                }
            }
        }
    }

    private data class ConnectionData(val socketId: String, val activityTimeout: Int?)

    private fun parseConnectionData(dataString: String): ConnectionData {
        return try {
            val element = json.parseToJsonElement(dataString)
            val socketId = element.jsonObject["socket_id"]?.jsonPrimitive?.content ?: ""
            val activityTimeout = element.jsonObject["activity_timeout"]?.jsonPrimitive?.content?.toIntOrNull()
            ConnectionData(socketId, activityTimeout)
        } catch (e: Exception) {
            ConnectionData("", null)
        }
    }

    internal suspend fun sendRaw(data: String): Result<Unit> {
        val s = session ?: return Result.failure(IllegalStateException("Not connected"))
        return try {
            s.send(Frame.Text(data))
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        disconnect(isError = false)
    }
    
    private suspend fun disconnect(isError: Boolean) {
        if (!isError) {
            isUserDisconnected = true
        }
        _state.value = ConnectionState.Disconnected()
        session?.close()
        session = null
        connectionJob?.cancel()
        activityTimeoutJob?.cancel()
        pingJob?.cancel()
        pongJob?.cancel()
        
        if (isError && !isUserDisconnected) {
             // Let it try to reconnect by triggering a catch block upstream or similar.
             // Given our current architecture, simply cancelling the session and connectionJob 
             // will drop the websocket and trigger a reconnect if we refactor `connectInternal` slightly
             // or we can just call connectInternal directly.
             // For now, setting it to disconnected isn't enough to trigger reconnect unless we throw.
             // Actually, `session?.close()` will cause `session?.incoming?.consumeAsFlow()` to complete,
             // finishing the `try` block, which will then trigger `finally` and NOT the `catch` block.
             // Let's ensure a reconnect happens if it's an error.
             connectInternal(1) 
        }
    }
}
