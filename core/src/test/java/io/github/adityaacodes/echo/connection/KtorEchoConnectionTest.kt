package io.github.adityaacodes.echo.connection

import io.github.adityaacodes.echo.engine.EchoEngine
import io.github.adityaacodes.echo.serialization.DefaultEchoSerializer
import io.github.adityaacodes.echo.state.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorEchoConnectionTest {

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `connect transitions to Connecting then Connected when handshake received`() = runTest {
        val engine = FakeEchoEngine()
        val connection = KtorEchoConnection(
            engine = engine,
            url = "ws://localhost",
            serializer = DefaultEchoSerializer(),
            scope = backgroundScope,
        )

        assertTrue(connection.state.value is ConnectionState.Disconnected)

        connection.connect()
        runCurrent()
        val stateAfterConnect = connection.state.value
        assertTrue("Expected Connecting, got $stateAfterConnect", stateAfterConnect is ConnectionState.Connecting)

        connection.handleRawText(
            """
            {
                "event": "pusher:connection_established",
                "data": "{\"socket_id\":\"123.456\",\"activity_timeout\":120}"
            }
            """.trimIndent(),
        )
        runCurrent()

        val connectedState = connection.state.value
        assertTrue("Expected Connected, got $connectedState", connectedState is ConnectionState.Connected)
        assertEquals("123.456", (connectedState as ConnectionState.Connected).socketId)

        connection.disconnect()
        runCurrent()
        assertTrue(connection.state.value is ConnectionState.Disconnected)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `handleRawText with ping replies with pong`() = runTest {
        val engine = FakeEchoEngine()
        val connection = KtorEchoConnection(
            engine = engine,
            url = "ws://localhost",
            serializer = DefaultEchoSerializer(),
            scope = backgroundScope,
        )

        connection.connect()
        runCurrent()
        connection.handleRawText(
            """
            {
                "event": "pusher:connection_established",
                "data": "{\"socket_id\":\"123.456\",\"activity_timeout\":120}"
            }
            """.trimIndent(),
        )
        runCurrent()

        connection.handleRawText("""{"event":"pusher:ping"}""")
        runCurrent()

        assertTrue(engine.sentMessages.any { it.contains("pusher:pong") })
        connection.disconnect()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `ping returns true when pong is received`() = runTest {
        val engine = FakeEchoEngine()
        val connection = KtorEchoConnection(
            engine = engine,
            url = "ws://localhost",
            serializer = DefaultEchoSerializer(),
            scope = backgroundScope,
        )

        connection.connect()
        runCurrent()
        connection.handleRawText(
            """
            {
                "event": "pusher:connection_established",
                "data": "{\"socket_id\":\"123.456\",\"activity_timeout\":120}"
            }
            """.trimIndent(),
        )
        runCurrent()

        val pingResult = async { connection.ping(timeoutMillis = 1000L) }
        runCurrent()
        assertTrue("Expected ping frame to be sent, got ${engine.sentMessages}", engine.sentMessages.any { it.contains("pusher:ping") })

        connection.handleRawText("""{"event":"pusher:pong"}""")
        runCurrent()

        assertTrue(pingResult.await())
        connection.disconnect()
    }

    @Test
    fun `handleRawText with error disconnects with protocol reason`() = runTest {
        val engine = FakeEchoEngine()
        val connection = KtorEchoConnection(
            engine = engine,
            url = "ws://localhost",
            serializer = DefaultEchoSerializer(),
            scope = backgroundScope,
        )

        connection.handleRawText(
            """{"event":"pusher:error","data":{"message":"Invalid version","code":4000}}""",
        )

        val errorState = connection.state.value
        assertTrue(errorState is ConnectionState.Disconnected)
        val reason = (errorState as ConnectionState.Disconnected).reason
        assertTrue(reason is io.github.adityaacodes.echo.error.EchoError.Protocol)
        assertEquals(4000, (reason as io.github.adityaacodes.echo.error.EchoError.Protocol).code)
    }

    private class FakeEchoEngine : EchoEngine {
        private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 32)
        override val incoming: Flow<String> = _incoming.asSharedFlow()

        var connected: Boolean = false
            private set
        val sentMessages = mutableListOf<String>()

        override suspend fun connect(url: String) {
            connected = true
        }

        override suspend fun send(data: String): Result<Unit> {
            return if (connected) {
                sentMessages += data
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Not connected"))
            }
        }

        override suspend fun disconnect() {
            connected = false
        }
    }
}
