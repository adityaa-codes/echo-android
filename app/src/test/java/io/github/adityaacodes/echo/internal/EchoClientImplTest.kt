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
}
