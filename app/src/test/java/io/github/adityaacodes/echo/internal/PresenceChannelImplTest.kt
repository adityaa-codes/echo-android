package io.github.adityaacodes.echo.internal

import app.cash.turbine.test
import io.github.adityaacodes.echo.connection.KtorEchoConnection
import io.github.adityaacodes.echo.data.protocol.MemberAdded
import io.github.adityaacodes.echo.data.protocol.MemberRemoved
import io.github.adityaacodes.echo.data.protocol.PusherFrame
import io.github.adityaacodes.echo.data.protocol.SubscriptionSucceeded
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceChannelImplTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `initial members are parsed from subscription succeeded`() = runTest {
        val incomingFrames = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(incomingFrames)
        
        val delegate = mockk<EchoChannelImpl>(relaxed = true)
        io.mockk.every { delegate.name } returns "presence-test"

        val channel = PresenceChannelImpl(
            delegate = delegate,
            eventRouter = router,
            scope = backgroundScope,
            json = json
        )

        runCurrent()

        channel.members.test {
            assertTrue(awaitItem().isEmpty()) // Initial empty state
            
            val initialData = """
                {
                  "presence": {
                    "count": 2,
                    "ids": ["user1", "user2"],
                    "hash": {
                      "user1": {"name": "Alice"},
                      "user2": {"name": "Bob"}
                    }
                  }
                }
            """.trimIndent()
            
            incomingFrames.emit(SubscriptionSucceeded("presence-test", initialData))
            runCurrent()
            
            val members = awaitItem()
            assertEquals(2, members.size)
            assertTrue(members.any { it.id == "user1" && it.info?.jsonObject?.get("name")?.jsonPrimitive?.content == "Alice" })
            assertTrue(members.any { it.id == "user2" && it.info?.jsonObject?.get("name")?.jsonPrimitive?.content == "Bob" })
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `members are added and removed`() = runTest {
        val incomingFrames = MutableSharedFlow<PusherFrame>()
        val router = EventRouter(incomingFrames)
        
        val delegate = mockk<EchoChannelImpl>(relaxed = true)
        io.mockk.every { delegate.name } returns "presence-test"

        val channel = PresenceChannelImpl(
            delegate = delegate,
            eventRouter = router,
            scope = backgroundScope,
            json = json
        )

        runCurrent()

        channel.members.test {
            assertTrue(awaitItem().isEmpty()) // Initial empty state
            
            // Add member 1
            incomingFrames.emit(MemberAdded("presence-test", """{"user_id":"user1","user_info":{"name":"Alice"}}"""))
            runCurrent()
            
            var members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("user1", members[0].id)
            
            // Add member 2
            incomingFrames.emit(MemberAdded("presence-test", """{"user_id":"user2","user_info":{"name":"Bob"}}"""))
            runCurrent()
            
            members = awaitItem()
            assertEquals(2, members.size)
            
            // Remove member 1
            incomingFrames.emit(MemberRemoved("presence-test", """{"user_id":"user1"}"""))
            runCurrent()
            
            members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("user2", members[0].id)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
