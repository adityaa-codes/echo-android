package io.github.adityaacodes.echo.internal

import io.github.adityaacodes.echo.EchoBuilder
import io.github.adityaacodes.echo.EchoClient
import io.github.adityaacodes.echo.EchoEvent
import io.github.adityaacodes.echo.channel.EchoChannel
import io.github.adityaacodes.echo.channel.PresenceChannel
import io.github.adityaacodes.echo.connection.EchoConnection
import io.github.adityaacodes.echo.connection.KtorEchoConnection
import io.github.adityaacodes.echo.state.ConnectionState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

internal class EchoClientImpl(
    private val builder: EchoBuilder
) : EchoClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val channels = ConcurrentHashMap<String, EchoChannelImpl>()

    private val httpClient = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = -1L // We handle ping/pong manually
        }
    }

    private val url = buildString {
        append(if (builder.clientConfig.cluster != null) "wss" else "ws")
        append("://")
        if (builder.clientConfig.cluster != null) {
            append("ws-${builder.clientConfig.cluster}.pusher.com:443/app/${builder.clientConfig.apiKey}?protocol=7&client=echo-kotlin&version=1.0.0")
        } else {
            append("${builder.clientConfig.host}/app/${builder.clientConfig.apiKey}?protocol=7&client=echo-kotlin&version=1.0.0")
        }
    }

    private val connection: KtorEchoConnection = KtorEchoConnection(httpClient, url, json = json, scope = scope)
    private val eventRouter = EventRouter(connection.incomingFrames)

    override val globalEvents: Flow<EchoEvent> = eventRouter.globalEvents
    override val state: StateFlow<ConnectionState> = connection.state

    override suspend fun connect() {
        connection.connect()
    }

    override suspend fun disconnect() {
        connection.disconnect()
    }

    override fun channel(name: String): EchoChannel {
        return channels.getOrPut(name) {
            EchoChannelImpl(
                name = name,
                connection = connection,
                eventRouter = eventRouter,
                scope = scope,
                json = json,
                onLeave = { leftChannelName ->
                    channels.remove(leftChannelName)
                }
            )
        }
    }

    override fun private(name: String): EchoChannel {
        throw NotImplementedError("Private channel subscriptions not implemented yet (Phase 7)")
    }

    override fun join(name: String): PresenceChannel {
        throw NotImplementedError("Presence channel subscriptions not implemented yet (Phase 8)")
    }

    override fun leave(name: String) {
        channels[name]?.leave()
    }
}
