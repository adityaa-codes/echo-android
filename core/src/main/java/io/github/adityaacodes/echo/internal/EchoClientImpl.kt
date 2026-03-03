package io.github.adityaacodes.echo.internal

import io.github.adityaacodes.echo.EchoBuilder
import io.github.adityaacodes.echo.EchoClient
import io.github.adityaacodes.echo.EchoEvent
import io.github.adityaacodes.echo.channel.EchoChannel
import io.github.adityaacodes.echo.channel.PresenceChannel
import io.github.adityaacodes.echo.connection.ExponentialBackoffReconnectionManager
import io.github.adityaacodes.echo.connection.KtorEchoConnection
import io.github.adityaacodes.echo.engine.EchoEngine
import io.github.adityaacodes.echo.engine.KtorEchoEngine
import io.github.adityaacodes.echo.error.EchoError
import io.github.adityaacodes.echo.state.ConnectionState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import io.github.adityaacodes.echo.utils.EchoLogger
import java.util.concurrent.ConcurrentHashMap

internal class EchoClientImpl(
    private val builder: EchoBuilder,
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(WebSockets) {
            pingIntervalMillis = 0L // We handle ping/pong manually
        }
    }
) : EchoClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val _channels = ConcurrentHashMap<String, EchoChannel>()
    private val serializer = builder.clientConfig.serializer

    private val url = buildString {
        val scheme = if (builder.clientConfig.useTls) "wss" else "ws"
        if (builder.clientConfig.cluster != null) {
            val defaultPort = if (builder.clientConfig.useTls) 443 else 80
            val port = builder.clientConfig.port ?: defaultPort
            append("$scheme://ws-${builder.clientConfig.cluster}.pusher.com:$port/app/${builder.clientConfig.apiKey}?protocol=7&client=echo-kotlin&version=1.0.0")
        } else {
            append("$scheme://${builder.clientConfig.host}")
            if (builder.clientConfig.port != null) {
                append(":${builder.clientConfig.port}")
            }
            append("/app/${builder.clientConfig.apiKey}?protocol=7&client=echo-kotlin&version=0.3.0")
        }
    }

    private val engine: EchoEngine = builder.clientConfig.engineFactory?.invoke() ?: KtorEchoEngine(
        client = httpClient,
        scope = scope,
    )
    private val connection: KtorEchoConnection = KtorEchoConnection(
        engine = engine,
        url = url,
        serializer = serializer,
        json = json,
        scope = scope,
    )
    private val reconnectionManager = ExponentialBackoffReconnectionManager(scope, connection)
    private val eventRouter = EventRouter(connection.incomingFrames)

    init {
        EchoLogger.enabled = builder.loggingConfig.enabled
        EchoLogger.customLogger = builder.loggingConfig.logger
        reconnectionManager.startObserving()
    }

    override val globalEvents: Flow<EchoEvent> = eventRouter.globalEvents
    override val errors: SharedFlow<EchoError> = connection.errors
    override val state: StateFlow<ConnectionState> = connection.state

    override val socketId: String?
        get() = (connection.state.value as? ConnectionState.Connected)?.socketId

    override val activeChannels: List<EchoChannel>
        get() = _channels.values.toList()

    override suspend fun connect() {
        EchoLogger.d("EchoClientImpl: connect() called. Connecting to $url")
        connection.connect()
    }

    override suspend fun disconnect() {
        EchoLogger.d("EchoClientImpl: disconnect() called.")
        connection.disconnect()
    }

    override suspend fun ping(timeoutMillis: Long): Boolean {
        EchoLogger.d("EchoClientImpl: ping() called.")
        return connection.ping(timeoutMillis)
    }

    override fun channel(name: String): EchoChannel {
        return _channels.getOrPut(name) {
            EchoLogger.d("EchoClientImpl: Joining public channel $name")
            EchoChannelImpl(
                name = name,
                connection = connection,
                eventRouter = eventRouter,
                scope = scope,
                json = json,
                authenticator = builder.authConfig.authenticator,
                onAuthFailure = builder.authConfig.onAuthFailure,
                onLeave = { leftChannelName ->
                    EchoLogger.d("EchoClientImpl: Left channel $leftChannelName")
                    _channels.remove(leftChannelName)
                }
            )
        }
    }

    override fun private(name: String): EchoChannel {
        val channelName = if (name.startsWith("private-")) name else "private-$name"
        return channel(channelName)
    }

    override fun presence(name: String): PresenceChannel {
        val channelName = if (name.startsWith("presence-")) name else "presence-$name"
        return _channels.getOrPut(channelName) {
            EchoLogger.d("EchoClientImpl: Joining presence channel $channelName")
            val delegate = EchoChannelImpl(
                name = channelName,
                connection = connection,
                eventRouter = eventRouter,
                scope = scope,
                json = json,
                authenticator = builder.authConfig.authenticator,
                onAuthFailure = builder.authConfig.onAuthFailure,
                onLeave = { leftChannelName ->
                    EchoLogger.d("EchoClientImpl: Left presence channel $leftChannelName")
                    _channels.remove(leftChannelName)
                }
            )
            PresenceChannelImpl(
                delegate = delegate,
                eventRouter = eventRouter,
                scope = scope,
                json = json
            )
        } as PresenceChannel
    }

    override fun leave(name: String) {
        EchoLogger.d("EchoClientImpl: Leaving channel $name")
        _channels[name]?.leave()
    }
}
