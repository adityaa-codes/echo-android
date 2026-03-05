package io.github.adityaacodes.echo.connection

import io.github.adityaacodes.echo.data.protocol.ConnectionEstablished
import io.github.adityaacodes.echo.data.protocol.ErrorFrame
import io.github.adityaacodes.echo.data.protocol.Ping
import io.github.adityaacodes.echo.data.protocol.Pong
import io.github.adityaacodes.echo.data.protocol.PusherFrame
import io.github.adityaacodes.echo.engine.EchoEngine
import io.github.adityaacodes.echo.error.EchoError
import io.github.adityaacodes.echo.serialization.EchoSerializer
import io.github.adityaacodes.echo.state.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicBoolean

internal interface ReconnectionAwareConnection {
    fun markReconnecting(attempt: Int)
    fun markSuspended()
}

internal class KtorEchoConnection(
    private val engine: EchoEngine,
    private val url: String,
    private val serializer: EchoSerializer,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
) : EchoConnection, ReconnectionAwareConnection {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<EchoError>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val errors: SharedFlow<EchoError> = _errors.asSharedFlow()

    internal suspend fun emitError(error: EchoError) {
        _errors.emit(error)
    }

    private val _incomingFrames = MutableSharedFlow<PusherFrame>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incomingFrames = _incomingFrames.asSharedFlow()

    private var connectionJob: Job? = null
    private var activityTimeoutJob: Job? = null
    private var pingJob: Job? = null
    private var pongJob: Job? = null

    private val isUserDisconnected = AtomicBoolean(false)
    private val connectMutex = Mutex()
    private val pingMutex = Mutex()
    private var manualPingDeferred: CompletableDeferred<Boolean>? = null

    private var currentActivityTimeout: Long = DEFAULT_ACTIVITY_TIMEOUT_MS

    override suspend fun connect() {
        connectMutex.withLock {
            isUserDisconnected.set(false)
            if (_state.value is ConnectionState.Connected || _state.value is ConnectionState.Connecting) {
                return@withLock
            }

            connectionJob?.cancel()
            activityTimeoutJob?.cancel()
            pingJob?.cancel()
            pongJob?.cancel()

            connectionJob = scope.launch {
                _state.value = ConnectionState.Connecting

                try {
                    engine.connect(url)
                    engine.incoming.collect { frame ->
                        handleRawText(frame)
                    }

                    if (!isUserDisconnected.get() && _state.value !is ConnectionState.Disconnected) {
                        val error = EchoError.Network(IllegalStateException("WebSocket disconnected unexpectedly"))
                        _state.value = ConnectionState.Disconnected(error)
                        _errors.emit(error)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        throw e
                    }
                    if (!isUserDisconnected.get()) {
                        val error = EchoError.Network(e)
                        _state.value = ConnectionState.Disconnected(error)
                        _errors.emit(error)
                    }
                } finally {
                    if (isUserDisconnected.get() && _state.value !is ConnectionState.Disconnected) {
                        _state.value = ConnectionState.Disconnected()
                    }
                    activityTimeoutJob?.cancel()
                    pingJob?.cancel()
                    pongJob?.cancel()
                }
            }
        }
    }

    override fun markReconnecting(attempt: Int) {
        if (_state.value !is ConnectionState.Connected && _state.value !is ConnectionState.Connecting) {
            _state.value = ConnectionState.Reconnecting(attempt)
        }
    }

    override fun markSuspended() {
        if (_state.value !is ConnectionState.Connected && _state.value !is ConnectionState.Connecting) {
            _state.value = ConnectionState.Suspended
        }
    }

    internal suspend fun handleRawText(text: String) {
        try {
            val pusherFrame = serializer.decode(text)

            when (pusherFrame) {
                is ConnectionEstablished -> {
                    val connectionData = parseConnectionData(pusherFrame.data)
                    _state.value = ConnectionState.Connected(connectionData.socketId)
                    startActivityTimeout(connectionData.activityTimeout)
                }
                is Ping -> {
                    sendRaw(serializer.encode(Pong()))
                }
                is Pong -> {
                    pongJob?.cancel()
                    manualPingDeferred?.complete(true)
                }
                is ErrorFrame -> {
                    val msg = pusherFrame.data?.message ?: "Unknown protocol error"
                    val code = pusherFrame.data?.code ?: -1
                    val error = EchoError.Protocol(code, msg)
                    if (code in 4000..4099) {
                        _state.value = ConnectionState.Failed(error)
                    } else {
                        _state.value = ConnectionState.Disconnected(error)
                    }
                    _errors.emit(error)
                    disconnect(isError = true)
                }
                else -> {
                    _incomingFrames.emit(pusherFrame)
                }
            }

            if (pusherFrame !is Pong) {
                resetActivityTimeout()
            }
        } catch (e: Exception) {
            _errors.emit(EchoError.Serialization(e))
        }
    }

    private fun startActivityTimeout(timeoutSeconds: Int?) {
        val serverActivityTimeout = (timeoutSeconds?.toLong() ?: DEFAULT_ACTIVITY_TIMEOUT_SECONDS) * 1000L
        currentActivityTimeout = minOf(DEFAULT_ACTIVITY_TIMEOUT_MS, serverActivityTimeout)
        resetActivityTimeout()
    }

    private fun resetActivityTimeout() {
        activityTimeoutJob?.cancel()
        pingJob?.cancel()
        pongJob?.cancel()

        if (_state.value !is ConnectionState.Connected) {
            return
        }

        activityTimeoutJob = scope.launch {
            delay(currentActivityTimeout)
            val result = sendRaw(serializer.encode(Ping()))
            if (result.isSuccess) {
                pongJob = scope.launch {
                    delay(PONG_TIMEOUT_MS)
                    disconnect(isError = true)
                }
            }
        }
    }

    private data class ConnectionData(
        val socketId: String,
        val activityTimeout: Int?,
    )

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
        return engine.send(data)
    }

    override suspend fun disconnect() {
        disconnect(isError = false)
    }

    override suspend fun ping(timeoutMillis: Long): Boolean {
        return pingMutex.withLock {
            if (state.value !is ConnectionState.Connected) {
                return false
            }

            val deferred = CompletableDeferred<Boolean>()
            manualPingDeferred = deferred

            try {
                val pingResult = sendRaw(serializer.encode(Ping()))
                if (pingResult.isFailure) {
                    return false
                }

                withTimeoutOrNull(timeoutMillis) {
                    deferred.await()
                } ?: false
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                false
            } finally {
                manualPingDeferred = null
            }
        }
    }

    private suspend fun disconnect(isError: Boolean) {
        connectMutex.withLock {
            if (!isError) {
                isUserDisconnected.set(true)
                _state.value = ConnectionState.Disconnected()
            }
            manualPingDeferred?.complete(false)
            engine.disconnect()
            connectionJob?.cancel()
            activityTimeoutJob?.cancel()
            pingJob?.cancel()
            pongJob?.cancel()
        }
    }

    private companion object {
        const val DEFAULT_ACTIVITY_TIMEOUT_SECONDS: Long = 120
        const val DEFAULT_ACTIVITY_TIMEOUT_MS: Long = 120_000L
        const val PONG_TIMEOUT_MS: Long = 30_000L
    }
}
