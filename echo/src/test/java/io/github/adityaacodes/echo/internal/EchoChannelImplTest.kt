package io.github.adityaacodes.echo.internal

import app.cash.turbine.test
import io.github.adityaacodes.echo.auth.Authenticator
import io.github.adityaacodes.echo.connection.KtorEchoConnection
import io.github.adityaacodes.echo.data.protocol.GenericEvent
import io.github.adityaacodes.echo.data.protocol.PusherFrame
import io.github.adityaacodes.echo.data.protocol.SubscriptionSucceeded
import io.github.adityaacodes.echo.error.EchoError
import io.github.adityaacodes.echo.state.ChannelState
import io.github.adityaacodes.echo.state.ConnectionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoChannelImplTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `public channel transitions to subscribing and sends subscribe command when connected`() = runTest {
        val connection = mockk<KtorEchoConnection>()
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
        every { connection.state } returns connectionState
        coEvery { connection.sendRaw(any()) } returns Result.success(Unit)
        coEvery { connection.emitError(any()) } returns Unit

        val incomingFrames = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(incomingFrames)

        val channel = EchoChannelImpl(
            name = "my-channel",
            connection = connection,
            eventRouter = router,
            scope = backgroundScope,
            json = json,
            authenticator = null,
            onAuthFailure = null,
            tokenExpiryMs = null,
            onLeave = {}
        )

        runCurrent()
        // Simulate connecting
        connectionState.value = ConnectionState.Connected("123.456")
        runCurrent()

        channel.state.test {
            assertTrue(awaitItem() is ChannelState.Subscribing)
            
            // Should have sent subscribe command
            coVerify(exactly = 1) { 
                connection.sendRaw(match { it.contains("pusher:subscribe") && it.contains("my-channel") })
            }
            
            // Simulate subscription succeeded event
            incomingFrames.emit(SubscriptionSucceeded("my-channel"))
            runCurrent()
            
            assertTrue(awaitItem() is ChannelState.Subscribed)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `private channel authenticates before subscribing`() = runTest {
        val connection = mockk<KtorEchoConnection>()
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected("123.456"))
        every { connection.state } returns connectionState
        coEvery { connection.sendRaw(any()) } returns Result.success(Unit)
        coEvery { connection.emitError(any()) } returns Unit

        val incomingFrames = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(incomingFrames)

        val authenticator = mockk<Authenticator>()
        coEvery { authenticator.authenticate("123.456", "private-test") } returns Result.success("{\"auth\":\"sig123\"}")

        val channel = EchoChannelImpl(
            name = "private-test",
            connection = connection,
            eventRouter = router,
            scope = backgroundScope,
            json = json,
            authenticator = authenticator,
            onAuthFailure = null,
            tokenExpiryMs = null,
            onLeave = {}
        )

        runCurrent()

        channel.state.test {
            assertTrue(awaitItem() is ChannelState.Subscribing)
            
            coVerify(exactly = 1) { authenticator.authenticate("123.456", "private-test") }
            coVerify(exactly = 1) { 
                connection.sendRaw(match { 
                    it.contains("pusher:subscribe") && 
                    it.contains("private-test") && 
                    it.contains("sig123") 
                })
            }
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `private channel transitions to failed when authentication fails`() = runTest {
        val connection = mockk<KtorEchoConnection>()
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected("123.456"))
        every { connection.state } returns connectionState
        coEvery { connection.sendRaw(any()) } returns Result.success(Unit)
        coEvery { connection.emitError(any()) } returns Unit

        val incomingFrames = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(incomingFrames)

        val authenticator = mockk<Authenticator>()
        coEvery { authenticator.authenticate("123.456", "private-test") } returns Result.failure(EchoError.Auth(403, "Forbidden"))

        val channel = EchoChannelImpl(
            name = "private-test",
            connection = connection,
            eventRouter = router,
            scope = backgroundScope,
            json = json,
            authenticator = authenticator,
            onAuthFailure = null,
            tokenExpiryMs = null,
            onLeave = {}
        )

        runCurrent()

        channel.state.test {
            val failedState = awaitItem()
            assertTrue(failedState is ChannelState.Failed)
            val error = (failedState as ChannelState.Failed).reason
            assertTrue(error is EchoError.Auth)
            assertEquals(403, (error as EchoError.Auth).status)
            
            coVerify(exactly = 0) { connection.sendRaw(any()) }
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `whisper command is sent for private channel`() = runTest {
        val connection = mockk<KtorEchoConnection>()
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected("123.456"))
        every { connection.state } returns connectionState
        coEvery { connection.sendRaw(any()) } returns Result.success(Unit)
        coEvery { connection.emitError(any()) } returns Unit

        val router = EventRouter(MutableSharedFlow())
        val authenticator = mockk<Authenticator>()
        coEvery { authenticator.authenticate(any(), any()) } returns Result.success("{\"auth\":\"sig123\"}")

        val channel = EchoChannelImpl(
            name = "private-test",
            connection = connection,
            eventRouter = router,
            scope = backgroundScope,
            json = json,
            authenticator = authenticator,
            onAuthFailure = null,
            tokenExpiryMs = null,
            onLeave = {}
        )
        runCurrent()

        val result = channel.whisper("typing", "{\"user\":1}")
        assertTrue(result.isSuccess)

        coVerify(exactly = 1) {
            connection.sendRaw(match { 
                it.contains("client-typing") && 
                it.contains("private-test") &&
                it.contains("\"user\":1")
            })
        }
    }

    @Test
    fun `channel auto-resubscribes when connection drops and reconnects`() = runTest {
        val connection = mockk<KtorEchoConnection>()
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected("socket-1"))
        every { connection.state } returns connectionState
        coEvery { connection.sendRaw(any()) } returns Result.success(Unit)
        coEvery { connection.emitError(any()) } returns Unit

        val incomingFrames = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(incomingFrames)

        val channel = EchoChannelImpl(
            name = "my-channel",
            connection = connection,
            eventRouter = router,
            scope = backgroundScope,
            json = json,
            authenticator = null,
            onAuthFailure = null,
            tokenExpiryMs = null,
            onLeave = {}
        )

        runCurrent()
        
        channel.state.test {
            assertTrue(awaitItem() is ChannelState.Subscribing)
            
            // First subscribe
            coVerify(exactly = 1) { connection.sendRaw(match { it.contains("pusher:subscribe") }) }
            
            incomingFrames.emit(SubscriptionSucceeded("my-channel"))
            runCurrent()
            
            assertTrue(awaitItem() is ChannelState.Subscribed)
            
            // Connection drops
            connectionState.value = ConnectionState.Disconnected(null)
            runCurrent()
            
            assertTrue(awaitItem() is ChannelState.Subscribing)
            
            // Reconnects with new socketId
            connectionState.value = ConnectionState.Connected("socket-2")
            runCurrent()
            
            // Should send subscribe command again
            coVerify(exactly = 2) { connection.sendRaw(match { it.contains("pusher:subscribe") }) }
            
            incomingFrames.emit(SubscriptionSucceeded("my-channel"))
            runCurrent()
            
            assertTrue(awaitItem() is ChannelState.Subscribed)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
