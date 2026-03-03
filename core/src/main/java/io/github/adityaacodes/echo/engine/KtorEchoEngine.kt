package io.github.adityaacodes.echo.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Default [EchoEngine] implementation backed by Ktor WebSockets.
 */
public class KtorEchoEngine(
    private val client: HttpClient = HttpClient(OkHttp) {
        install(WebSockets) {
            pingIntervalMillis = 0L // Echo uses protocol-level ping/pong frames.
        }
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : EchoEngine {

    private val _incoming = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    public override val incoming: Flow<String> = _incoming.asSharedFlow()

    private val connectMutex = Mutex()
    private val sessionRef = AtomicReference<DefaultClientWebSocketSession?>(null)
    private val reportCloseCode = AtomicBoolean(true)
    private var incomingJob: Job? = null

    public override suspend fun connect(url: String) {
        connectMutex.withLock {
            disconnectLocked(reportClose = false)

            val session = client.webSocketSession {
                url(url)
            }
            reportCloseCode.set(true)
            sessionRef.set(session)
            incomingJob = scope.launch {
                try {
                    session.incoming.consumeAsFlow().collect { frame ->
                        if (frame is Frame.Text) {
                            _incoming.emit(frame.readText())
                        }
                    }
                } finally {
                    maybeEmitProtocolClose(session)
                    sessionRef.compareAndSet(session, null)
                }
            }
        }
    }

    public override suspend fun send(data: String): Result<Unit> {
        val session = sessionRef.get() ?: return Result.failure(IllegalStateException("Not connected"))
        return try {
            session.send(Frame.Text(data))
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            Result.failure(e)
        }
    }

    public override suspend fun disconnect() {
        connectMutex.withLock {
            disconnectLocked(reportClose = false)
        }
    }

    private suspend fun disconnectLocked(reportClose: Boolean) {
        reportCloseCode.set(reportClose)
        incomingJob?.cancel()
        incomingJob = null
        sessionRef.getAndSet(null)?.close()
    }

    private suspend fun maybeEmitProtocolClose(session: DefaultClientWebSocketSession) {
        if (!reportCloseCode.get()) {
            return
        }

        val closeReason = withTimeoutOrNull(1000L) { session.closeReason.await() } ?: return
        val code = closeReason.code.toInt()
        if (code !in 4000..4299) {
            return
        }

        val message = closeReason.message.escapeJson()
        _incoming.emit("""{"event":"pusher:error","data":{"message":"$message","code":$code}}""")
    }

    private fun String.escapeJson(): String {
        return this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }
}
