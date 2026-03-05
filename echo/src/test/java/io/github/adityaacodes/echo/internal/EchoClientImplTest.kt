package io.github.adityaacodes.echo.internal

import app.cash.turbine.test
import io.github.adityaacodes.echo.Echo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runCurrent

class EchoClientImplTest {

    @Test
    fun `client exposes global events correctly`() = runTest {
        val client = Echo.create {
            client {
                host = "ws.example.com"
                apiKey = "test-key"
            }
        }
        
        assertTrue(client is EchoClientImpl)
    }

    @Test
    fun `client handles disconnect and channel methods`() = runTest {
        val client = Echo.create {
            client {
                host = "ws.example.com"
                apiKey = "test-key"
            }
        }
        
        val channel1 = client.channel("pub-chan")
        val channel2 = client.channel("pub-chan")
        
        // Deduplication test
        assertEquals(channel1, channel2)
        
        val privChannel = client.private("my-priv")
        assertTrue(privChannel.name.startsWith("private-"))

        val presChannel = client.presence("my-pres")
        assertTrue(presChannel.name.startsWith("presence-"))        
        client.leave("pub-chan")
        
        // Ensure new instance is created after leave
        val channel3 = client.channel("pub-chan")
        assertTrue(channel1 !== channel3)

        client.disconnect()
        runCurrent()
        assertTrue(client.state.value is io.github.adityaacodes.echo.state.ConnectionState.Disconnected)
    }
}
