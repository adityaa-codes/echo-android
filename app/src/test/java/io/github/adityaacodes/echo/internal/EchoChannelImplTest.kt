package io.github.adityaacodes.echo.internal

import app.cash.turbine.test
import io.github.adityaacodes.echo.connection.KtorEchoConnection
import io.github.adityaacodes.echo.data.protocol.GenericEvent
import io.github.adityaacodes.echo.data.protocol.PusherFrame
import io.github.adityaacodes.echo.data.protocol.SubscriptionSucceeded
import io.github.adityaacodes.echo.state.ChannelState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
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
    fun `channel transitions to subscribing and sends subscribe command`() = runTest {
        val connection = mockk<KtorEchoConnection>()
        coEvery { connection.sendRaw(any()) } returns Result.success(Unit)

        val incomingFrames = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(incomingFrames)

        val channel = EchoChannelImpl(
            name = "my-channel",
            connection = connection,
            eventRouter = router,
            scope = backgroundScope,
            json = json,
            onLeave = {}
        )

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
    fun `listen filters events for specific channel and event name`() = runTest {
        val connection = mockk<KtorEchoConnection>()
        coEvery { connection.sendRaw(any()) } returns Result.success(Unit)

        val incomingFrames = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(incomingFrames)

        val channel = EchoChannelImpl(
            name = "my-channel",
            connection = connection,
            eventRouter = router,
            scope = backgroundScope,
            json = json,
            onLeave = {}
        )

        runCurrent()

        channel.listen("my-event").test {
            // Emitting for wrong channel
            incomingFrames.emit(GenericEvent("my-event", "wrong-channel", JsonPrimitive("data")))
            
            // Emitting for wrong event
            incomingFrames.emit(GenericEvent("wrong-event", "my-channel", JsonPrimitive("data2")))
            
            // Emitting correct
            incomingFrames.emit(GenericEvent("my-event", "my-channel", JsonPrimitive("data3")))

            val item = awaitItem()
            assertEquals("\"data3\"", item)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `leave transitions to unsubscribed and sends unsubscribe command`() = runTest {
        val connection = mockk<KtorEchoConnection>()
        coEvery { connection.sendRaw(any()) } returns Result.success(Unit)

        val incomingFrames = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(incomingFrames)

        var leaveCalledWith = ""
        val channel = EchoChannelImpl(
            name = "my-channel",
            connection = connection,
            eventRouter = router,
            scope = backgroundScope,
            json = json,
            onLeave = { leaveCalledWith = it }
        )

        runCurrent()

        channel.state.test {
            assertTrue(awaitItem() is ChannelState.Subscribing)
            
            channel.leave()
            runCurrent()
            
            assertTrue(awaitItem() is ChannelState.Unsubscribed)
            assertEquals("my-channel", leaveCalledWith)
            
            coVerify(exactly = 1) { 
                connection.sendRaw(match { it.contains("pusher:unsubscribe") && it.contains("my-channel") })
            }
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
