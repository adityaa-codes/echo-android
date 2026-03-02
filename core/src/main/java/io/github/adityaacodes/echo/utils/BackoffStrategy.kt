package io.github.adityaacodes.echo.utils

import kotlin.math.min
import kotlin.random.Random

internal object BackoffStrategy {
    private const val INITIAL_DELAY_MS = 2000L
    private const val MAX_DELAY_MS = 30000L
    private const val JITTER_FACTOR = 0.2 // 20% jitter

    /**
     * Calculates the delay for the next reconnection attempt using exponential backoff with jitter.
     *
     * @param attempt The current attempt number (starts at 1)
     * @return The calculated delay in milliseconds.
     */
    fun calculateDelay(attempt: Int): Long {
        if (attempt <= 0) return 0L

        // Exponential backoff: 2000, 4000, 8000, 16000...
        // Use min(attempt, 20) to prevent 1 shl (attempt - 1) from overflowing integer bounds
        val safeAttempt = min(attempt, 20)
        val exponentialDelay = INITIAL_DELAY_MS * (1 shl (safeAttempt - 1))
        val cappedDelay = min(exponentialDelay, MAX_DELAY_MS).toDouble()

        // Apply Jitter: +/- 20%
        val jitterAmount = cappedDelay * JITTER_FACTOR
        val minJitter = cappedDelay - jitterAmount
        val maxJitter = cappedDelay + jitterAmount
        
        return Random.nextDouble(minJitter, maxJitter).toLong()
    }
}
