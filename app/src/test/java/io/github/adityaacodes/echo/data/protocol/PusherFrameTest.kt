package io.github.adityaacodes.echo.data.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PusherFrameTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse connection_established event`() {
        val rawJson = """
            {
                "event": "pusher:connection_established",
                "data": "{\"socket_id\":\"123.456\",\"activity_timeout\":120}"
            }
        """.trimIndent()

        val frame = json.decodeFromString<PusherFrame>(rawJson)
        assertTrue(frame is ConnectionEstablished)
        assertEquals("{\"socket_id\":\"123.456\",\"activity_timeout\":120}", (frame as ConnectionEstablished).data)
    }

    @Test
    fun `parse generic custom event`() {
        val rawJson = """
            {
                "event": "App\\Events\\OrderCreated",
                "channel": "private-user.1",
                "data": {"order_id": 999}
            }
        """.trimIndent()

        val frame = json.decodeFromString<PusherFrame>(rawJson)
        assertTrue(frame is GenericEvent)
        val genericFrame = frame as GenericEvent
        assertEquals("App\\Events\\OrderCreated", genericFrame.event)
        assertEquals("private-user.1", genericFrame.channel)
    }

    @Test
    fun `serialize subscribe command`() {
        val command = SubscribeCommand(
            data = SubscribeCommand.SubscribeData(
                channel = "private-chat",
                auth = "some_auth_token"
            )
        )

        val jsonString = json.encodeToString(PusherFrame.serializer(), command)
        assertTrue(jsonString.contains("pusher:subscribe"))
        assertTrue(jsonString.contains("private-chat"))
        assertTrue(jsonString.contains("some_auth_token"))
    }

    @Test
    fun `parse and serialize ping pong`() {
        val pingStr = """{"event":"pusher:ping"}"""
        val pingFrame = json.decodeFromString<PusherFrame>(pingStr)
        assertTrue(pingFrame is Ping)
        
        val pongStr = """{"event":"pusher:pong"}"""
        val pongFrame = json.decodeFromString<PusherFrame>(pongStr)
        assertTrue(pongFrame is Pong)

        val pongEncoded = json.encodeToString(PusherFrame.serializer(), Pong())
        assertTrue(pongEncoded.contains("pusher:pong"))
        
        val pingEncoded = json.encodeToString(PusherFrame.serializer(), Ping())
        assertTrue(pingEncoded.contains("pusher:ping"))
    }

    @Test
    fun `parse error frame`() {
        val errorStr = """{"event":"pusher:error","data":{"message":"Forbidden","code":4000}}"""
        val frame = json.decodeFromString<PusherFrame>(errorStr)
        assertTrue(frame is ErrorFrame)
        assertEquals("Forbidden", (frame as ErrorFrame).data?.message)
        assertEquals(4000, frame.data?.code)
    }

    @Test
    fun `parse internal pusher events`() {
        val subStr = """{"event":"pusher_internal:subscription_succeeded","channel":"my-channel","data":"{}"}"""
        val subFrame = json.decodeFromString<PusherFrame>(subStr)
        assertTrue(subFrame is SubscriptionSucceeded)
        assertEquals("my-channel", (subFrame as SubscriptionSucceeded).channel)

        val addStr = """{"event":"pusher_internal:member_added","channel":"presence-ch","data":"user1"}"""
        val addFrame = json.decodeFromString<PusherFrame>(addStr)
        assertTrue(addFrame is MemberAdded)

        val rmStr = """{"event":"pusher_internal:member_removed","channel":"presence-ch","data":"user1"}"""
        val rmFrame = json.decodeFromString<PusherFrame>(rmStr)
        assertTrue(rmFrame is MemberRemoved)
    }

    @Test
    fun `serialize unsubscribe command`() {
        val command = UnsubscribeCommand(data = UnsubscribeCommand.UnsubscribeData("my-channel"))
        val jsonString = json.encodeToString(PusherFrame.serializer(), command)
        assertTrue(jsonString.contains("pusher:unsubscribe"))
        assertTrue(jsonString.contains("my-channel"))
    }
}
