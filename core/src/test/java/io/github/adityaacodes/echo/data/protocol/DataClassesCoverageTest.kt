package io.github.adityaacodes.echo.data.protocol

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DataClassesCoverageTest {

    @Test
    fun `test data class methods for coverage`() {
        val ce = ConnectionEstablished("data")
        assertEquals(ce, ce.copy())
        assertEquals(ce.hashCode(), ce.copy().hashCode())
        assertNotNull(ce.toString())

        val ping = Ping(data = JsonPrimitive("p"))
        assertEquals(ping, ping.copy())
        assertEquals(ping.hashCode(), ping.copy().hashCode())
        assertNotNull(ping.toString())

        val pong = Pong(data = JsonPrimitive("p"))
        assertEquals(pong, pong.copy())
        assertEquals(pong.hashCode(), pong.copy().hashCode())
        assertNotNull(pong.toString())

        val errData = ErrorFrame.ErrorData("msg", 1)
        val errFrame = ErrorFrame(errData)
        assertEquals(errFrame, errFrame.copy())
        assertEquals(errData, errData.copy())
        assertNotNull(errFrame.toString())
        assertNotNull(errData.toString())

        val subSucceeded = SubscriptionSucceeded("ch", "data")
        assertEquals(subSucceeded, subSucceeded.copy())
        assertNotNull(subSucceeded.toString())

        val memAdded = MemberAdded("ch", "data")
        assertEquals(memAdded, memAdded.copy())
        assertNotNull(memAdded.toString())

        val memRemoved = MemberRemoved("ch", "data")
        assertEquals(memRemoved, memRemoved.copy())
        assertNotNull(memRemoved.toString())

        val genEvent = GenericEvent("ev", "ch", JsonPrimitive("d"))
        assertEquals(genEvent, genEvent.copy())
        assertNotNull(genEvent.toString())

        val subData = SubscribeCommand.SubscribeData("ch", "auth", "d")
        val subCmd = SubscribeCommand("ev", subData)
        assertEquals(subCmd, subCmd.copy())
        assertEquals(subData, subData.copy())
        assertNotNull(subCmd.toString())

        val unsubData = UnsubscribeCommand.UnsubscribeData("ch")
        val unsubCmd = UnsubscribeCommand("ev", unsubData)
        assertEquals(unsubCmd, unsubCmd.copy())
        assertEquals(unsubData, unsubData.copy())
        assertNotNull(unsubCmd.toString())

        val whisper = WhisperCommand("ev", "ch", JsonPrimitive("d"))
        assertEquals(whisper, whisper.copy())
        assertNotNull(whisper.toString())
    }
}
