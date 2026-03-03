package io.github.adityaacodes.echo.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorEchoEngineTest {

    @Test
    fun `send fails when engine is disconnected`() = runTest {
        val engine = KtorEchoEngine(scope = backgroundScope)

        val result = engine.send("""{"event":"test"}""")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `disconnect is safe when no session exists`() = runTest {
        val engine = KtorEchoEngine(scope = backgroundScope)

        engine.disconnect()

        assertTrue(engine.send("after-disconnect").isFailure)
    }

    @Test
    fun `connect with non websocket mock client fails cleanly`() = runTest {
        val mockClient = HttpClient(MockEngine) {
            install(WebSockets) {
                pingIntervalMillis = 0L
            }
            engine {
                addHandler { respondOk() }
            }
        }
        val engine = KtorEchoEngine(client = mockClient, scope = backgroundScope)

        val result = runCatching { engine.connect("ws://localhost/app/test") }

        assertTrue(result.isFailure)
        assertTrue(engine.send("after-failed-connect").isFailure)
    }
}
