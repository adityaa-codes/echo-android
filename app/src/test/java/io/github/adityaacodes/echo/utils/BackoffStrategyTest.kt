package io.github.adityaacodes.echo.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffStrategyTest {

    @Test
    fun `calculateDelay applies exponential backoff and jitter`() {
        val attempt1 = BackoffStrategy.calculateDelay(1)
        // Base is 2000, jitter is +/- 400 (1600 to 2400)
        assertTrue("Attempt 1 delay should be between 1600 and 2400, was $attempt1", attempt1 in 1600..2400)

        val attempt2 = BackoffStrategy.calculateDelay(2)
        // Base is 4000, jitter is +/- 800 (3200 to 4800)
        assertTrue("Attempt 2 delay should be between 3200 and 4800, was $attempt2", attempt2 in 3200..4800)

        val attempt3 = BackoffStrategy.calculateDelay(3)
        // Base is 8000, jitter is +/- 1600 (6400 to 9600)
        assertTrue("Attempt 3 delay should be between 6400 and 9600, was $attempt3", attempt3 in 6400..9600)
    }

    @Test
    fun `calculateDelay caps at MAX_DELAY_MS with jitter`() {
        // High attempt to force cap (30000ms)
        val attempt10 = BackoffStrategy.calculateDelay(10)
        // Cap is 30000, jitter is +/- 6000 (24000 to 36000)
        assertTrue("Attempt 10 delay should be capped between 24000 and 36000, was $attempt10", attempt10 in 24000..36000)
    }
}