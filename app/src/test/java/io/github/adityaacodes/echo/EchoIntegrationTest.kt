package io.github.adityaacodes.echo

import app.cash.turbine.test
import io.github.adityaacodes.echo.auth.Authenticator
import io.github.adityaacodes.echo.channel.PresenceChannel
import io.github.adityaacodes.echo.internal.EchoClientImpl
import io.github.adityaacodes.echo.state.ChannelState
import io.github.adityaacodes.echo.state.ConnectionState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class EchoIntegrationTest {

    @Test
    fun `full lifecycle integration test`() = runTest {
        // We will mock the Ktor Echo Connection by passing a mock HTTP Client
        val mockEngine = MockEngine { request ->
            respondOk()
        }
        
        val httpClient = HttpClient(mockEngine) {
            install(WebSockets) {
                pingInterval = -1L
            }
        }
        
        // Setup Echo builder
        val builder = EchoBuilder().apply {
            client {
                host = "ws.example.com"
                apiKey = "app-key"
            }
            auth {
                authenticator = Authenticator { _, _ -> Result.success("{\"auth\":\"test-auth\"}") }
            }
        }
        
        val client = EchoClientImpl(builder, httpClient)
        
        // For testing we just check that the client initializes properly
        // without crashing, as MockEngine's WebSocket implementation doesn't 
        // fully support simulating a server sending messages down easily without
        // writing a custom TestEngine.
        
        client.state.test {
            assertTrue(awaitItem() is ConnectionState.Disconnected)
            
            // Connect
            client.connect()
            
            val connecting = awaitItem()
            assertTrue(connecting is ConnectionState.Connecting)
            
            // Reconnecting state will be hit because MockEngine respondOk() doesn't upgrade to WS properly
            // in standard Ktor mock.
            // But this confirms the state machine started.
            
            cancelAndIgnoreRemainingEvents()
        }
        
        // Attempt to create a channel
        val channel = client.private("test")
        assertEquals("private-test", channel.name)
        
        val presence = client.join("room")
        assertEquals("presence-room", presence.name)
    }
}
