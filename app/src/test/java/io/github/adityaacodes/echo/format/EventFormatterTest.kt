package io.github.adityaacodes.echo.format

import org.junit.Assert.assertEquals
import org.junit.Test

class EventFormatterTest {

    @Test
    fun `format with no namespace returns original`() {
        val formatter = EventFormatter()
        assertEquals("OrderCreated", formatter.format("OrderCreated"))
    }

    @Test
    fun `format with namespace prefixes event`() {
        val formatter = EventFormatter("App.Events")
        assertEquals("App.Events.OrderCreated", formatter.format("OrderCreated"))
    }

    @Test
    fun `format ignores namespace if event starts with dot`() {
        val formatter = EventFormatter("App.Events")
        assertEquals("OrderCreated", formatter.format(".OrderCreated"))
    }

    @Test
    fun `format ignores namespace for internal pusher events`() {
        val formatter = EventFormatter("App.Events")
        assertEquals("pusher:subscribe", formatter.format("pusher:subscribe"))
        assertEquals("client-typing", formatter.format("client-typing"))
    }

    @Test
    fun `formatClientEvent adds client- prefix`() {
        val formatter = EventFormatter()
        assertEquals("client-typing", formatter.formatClientEvent("typing"))
        assertEquals("client-typing", formatter.formatClientEvent("client-typing"))
    }
}
