package io.github.adityaacodes.echo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoBuilderTest {

    @Test
    fun `auth config stores endpoint token provider and failure callback`() = runTest {
        var tokenCalls = 0
        var authFailureCalls = 0
        val builder = EchoBuilder()

        builder.auth {
            authEndpoint = "https://example.com/auth"
            tokenProvider = {
                tokenCalls += 1
                "token-123"
            }
            onAuthFailure = {
                authFailureCalls += 1
            }
        }

        assertEquals("https://example.com/auth", builder.authConfig.authEndpoint)
        assertEquals("token-123", builder.authConfig.tokenProvider?.invoke())
        builder.authConfig.onAuthFailure?.invoke()
        assertEquals(1, tokenCalls)
        assertEquals(1, authFailureCalls)
    }

    @Test
    fun `logging config stores enabled flag and custom logger`() {
        val builder = EchoBuilder()
        val logs = mutableListOf<String>()

        builder.logging {
            enabled = true
            logger = { logs += it }
        }

        assertTrue(builder.loggingConfig.enabled)
        assertNotNull(builder.loggingConfig.logger)
        builder.loggingConfig.logger?.invoke("ping")
        assertEquals(listOf("ping"), logs)
    }
}
