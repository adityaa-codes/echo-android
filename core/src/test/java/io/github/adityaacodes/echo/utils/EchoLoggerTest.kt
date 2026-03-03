package io.github.adityaacodes.echo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class EchoLoggerTest {

    @After
    fun tearDown() {
        EchoLogger.enabled = false
        EchoLogger.customLogger = null
    }

    @Test
    fun `debug logging is skipped when disabled`() {
        val received = mutableListOf<String>()
        EchoLogger.enabled = false
        EchoLogger.customLogger = { received += it }

        EchoLogger.d("hello %s", "world")

        assertTrue(received.isEmpty())
    }

    @Test
    fun `debug logging formats and forwards when enabled`() {
        val received = mutableListOf<String>()
        EchoLogger.enabled = true
        EchoLogger.customLogger = { received += it }

        EchoLogger.d("hello %s", "world")

        assertEquals(listOf("hello world"), received)
    }

    @Test
    fun `error logging includes throwable message`() {
        val received = mutableListOf<String>()
        EchoLogger.enabled = true
        EchoLogger.customLogger = { received += it }

        EchoLogger.e(IllegalStateException("boom"), "failure %s", "path")

        assertEquals(listOf("ERROR: failure path - boom"), received)
    }
}
