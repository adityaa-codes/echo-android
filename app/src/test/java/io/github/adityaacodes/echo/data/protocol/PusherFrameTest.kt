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
        println("Encoded JSON: $jsonString")
        assertTrue(jsonString.contains("pusher:subscribe"))
        assertTrue(jsonString.contains("private-chat"))
        assertTrue(jsonString.contains("some_auth_token"))
    }
}
