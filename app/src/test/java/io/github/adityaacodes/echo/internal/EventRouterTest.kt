package io.github.adityaacodes.echo.internal

import app.cash.turbine.test
import io.github.adityaacodes.echo.data.protocol.ConnectionEstablished
import io.github.adityaacodes.echo.data.protocol.GenericEvent
import io.github.adityaacodes.echo.data.protocol.Ping
import io.github.adityaacodes.echo.data.protocol.PusherFrame
import io.github.adityaacodes.echo.data.protocol.SubscriptionSucceeded
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRouterTest {

    @Test
    fun `router filters channel events correctly`() = runTest {
        val sharedFlow = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(sharedFlow)

        val channelName = "private-test"
        val channelFlow = router.channelEvents(channelName)

        channelFlow.test {
            // Should be ignored by channel filter
            sharedFlow.emit(Ping())
            
            // Should be ignored (wrong channel)
            sharedFlow.emit(GenericEvent("App\\Events\\Test", "other-channel", null))

            // Should be received
            val targetEvent = GenericEvent("App\\Events\\Test", channelName, JsonPrimitive("data"))
            sharedFlow.emit(targetEvent)

            val received = awaitItem()
            assertTrue(received is GenericEvent)
            assertEquals(channelName, (received as GenericEvent).channel)
            assertEquals("App\\Events\\Test", received.event)
        }
    }

    @Test
    fun `router maps global events to EchoEvent`() = runTest {
        val sharedFlow = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(sharedFlow)

        router.globalEvents.test {
            // Should be ignored
            sharedFlow.emit(Ping())

            // Connection established should be mapped
            sharedFlow.emit(ConnectionEstablished("{\"socket_id\":\"123\"}"))
            val connEvent = awaitItem()
            assertEquals("pusher:connection_established", connEvent.event)
            assertEquals("{\"socket_id\":\"123\"}", connEvent.data)

            // Generic event should be mapped
            sharedFlow.emit(GenericEvent("my-event", "my-channel", JsonPrimitive("hello")))
            val genEvent = awaitItem()
            assertEquals("my-event", genEvent.event)
            assertEquals("my-channel", genEvent.channel)
            assertEquals("\"hello\"", genEvent.data) // JsonPrimitive toString includes quotes

            // Internal subscription succeeded should be mapped
            sharedFlow.emit(SubscriptionSucceeded("private-1", "{}"))
            val subEvent = awaitItem()
            assertEquals("pusher_internal:subscription_succeeded", subEvent.event)
            assertEquals("private-1", subEvent.channel)
            assertEquals("{}", subEvent.data)
        }
    }
}