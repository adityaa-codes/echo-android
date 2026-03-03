package io.github.adityaacodes.echo.connection

import io.github.adityaacodes.echo.error.EchoError
import io.github.adityaacodes.echo.state.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.random.Random

internal class ExponentialBackoffReconnectionManager(
    private val scope: CoroutineScope,
    private val connection: EchoConnection,
    private val maxAttempts: Int = 10,
    private val baseDelayMs: Long = 1000,
    private val maxDelayMs: Long = 30000
) {
    private var reconnectJob: Job? = null
    private var currentAttempt = 0

    fun startObserving() {
        scope.launch {
            connection.state.collect { state ->
                when (state) {
                    is ConnectionState.Disconnected -> handleDisconnected(state)
                    is ConnectionState.Connected -> {
                        currentAttempt = 0
                        reconnectJob?.cancel()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun handleDisconnected(state: ConnectionState.Disconnected) {
        val reason = state.reason
        
        // Don't reconnect if it was a user-initiated disconnect
        // For KtorEchoConnection, we might need a way to distinguish this.
        // Wait, KtorEchoConnection currently emits Disconnected() without reason for user disconnects.
        if (reason == null) {
            currentAttempt = 0
            reconnectJob?.cancel()
            return
        }

        if (reason is EchoError.Protocol) {
            val code = reason.code
            when {
                code in 4000..4099 -> {
                    // Fatal errors, do not reconnect
                    return
                }
                code in 4100..4199 -> {
                    // Transient errors, use backoff
                    scheduleReconnect(immediate = false)
                }
                code in 4200..4299 -> {
                    // Immediate reconnect
                    scheduleReconnect(immediate = true)
                }
                else -> {
                    // Default to backoff
                    scheduleReconnect(immediate = false)
                }
            }
        } else {
            // Network errors or other
            scheduleReconnect(immediate = false)
        }
    }

    private fun scheduleReconnect(immediate: Boolean) {
        if (currentAttempt >= maxAttempts) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            currentAttempt++
            (connection as? ReconnectionAwareConnection)?.markReconnecting(currentAttempt)
            if (!immediate) {
                val delayMs = calculateDelayWithJitter(currentAttempt)
                delay(delayMs)
            }
            connection.connect()
        }
    }

    private fun calculateDelayWithJitter(attempt: Int): Long {
        val exp = 2.0.pow(attempt.toDouble() - 1)
        val delay = (baseDelayMs * exp).toLong().coerceAtMost(maxDelayMs)
        // Add up to 20% jitter
        val jitter = Random.nextDouble(0.8, 1.2)
        return (delay * jitter).toLong()
    }
}
