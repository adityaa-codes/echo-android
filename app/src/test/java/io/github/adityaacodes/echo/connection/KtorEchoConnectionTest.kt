package io.github.adityaacodes.echo.connection

import app.cash.turbine.test
import io.github.adityaacodes.echo.state.ConnectionState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.mockk

class KtorEchoConnectionTest {

    @Test
    fun `connect transitions to Connecting then Connected when handshake received`() = runTest {
        val mockEngine = MockEngine { request ->
            respondOk()
        }
        
        val client = HttpClient(mockEngine) {
            install(WebSockets)
        }
        
        val connection = KtorEchoConnection(client, "ws://localhost", scope = this)

        connection.state.test {
            val initialState = awaitItem()
            assertTrue(initialState is ConnectionState.Disconnected)
            
            connection.connect()
            
            val connectingState = awaitItem()
            assertTrue(connectingState is ConnectionState.Connecting)
            
            val errorState = awaitItem()
            assertTrue(errorState is ConnectionState.Disconnected)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handleRawText with connection_established updates state with socketId`() = runTest {
        val mockEngine = MockEngine { request -> respondOk() }
        val client = HttpClient(mockEngine) { install(WebSockets) }
        val connection = KtorEchoConnection(client, "ws://localhost", scope = this)

        connection.state.test {
            assertTrue(awaitItem() is ConnectionState.Disconnected)
            
            // Simulate receiving the connection_established frame
            val frameJson = """
                {
                    "event": "pusher:connection_established",
                    "data": "{\"socket_id\":\"123.456\",\"activity_timeout\":120}"
                }
            """.trimIndent()
            
            connection.handleRawText(frameJson)
            
            val connectedState = awaitItem()
            assertTrue(connectedState is ConnectionState.Connected)
            assertEquals("123.456", (connectedState as ConnectionState.Connected).socketId)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
