package io.github.adityaacodes.echo.utils

import timber.log.Timber

internal object EchoLogger {
    var enabled: Boolean = false
    var customLogger: ((String) -> Unit)? = null

    fun d(message: String, vararg args: Any?) {
        if (!enabled) return
        val formatted = if (args.isEmpty()) message else message.format(*args)
        customLogger?.invoke(formatted) ?: Timber.tag("EchoSDK").d(formatted)
    }

    fun e(t: Throwable?, message: String, vararg args: Any?) {
        if (!enabled) return
        val formatted = if (args.isEmpty()) message else message.format(*args)
        customLogger?.invoke("ERROR: $formatted - ${t?.message}") ?: Timber.tag("EchoSDK").e(t, formatted)
    }
}
