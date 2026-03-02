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

    private val _incomingFrames = MutableSharedFlow<PusherFrame>(extraBufferCapacity = 64)
    val incomingFrames = _incomingFrames.asSharedFlow()

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    
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
            }
        }
    }

    internal suspend fun handleRawText(text: String) {
        try {
            val pusherFrame = json.decodeFromString<PusherFrame>(text)
            
            when (pusherFrame) {
                is ConnectionEstablished -> {
                    val socketId = parseSocketId(pusherFrame.data)
                    _state.value = ConnectionState.Connected(socketId)
                }
                is Ping -> {
                    val pongJson = json.encodeToString(PusherFrame.serializer(), Pong())
                    sendRaw(pongJson)
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
        } catch (e: Exception) {
            // Serialization error or malformed JSON
            // We shouldn't disconnect just because of one bad frame, but maybe log it.
        }
    }

    private fun parseSocketId(dataString: String): String {
        return try {
            val element = json.parseToJsonElement(dataString)
            element.jsonObject["socket_id"]?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            ""
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
        isUserDisconnected = true
        _state.value = ConnectionState.Disconnected()
        session?.close()
        session = null
        connectionJob?.cancel()
    }
}
