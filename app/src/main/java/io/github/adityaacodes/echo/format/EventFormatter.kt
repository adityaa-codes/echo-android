package io.github.adityaacodes.echo.format

internal class EventFormatter(private val namespace: String? = null) {

    fun format(event: String): String {
        if (event.startsWith(".") || event.startsWith("\\")) {
            return event.substring(1)
        }

        if (event.startsWith("pusher:") || event.startsWith("pusher_internal:") || event.startsWith("client-")) {
            return event
        }

        return if (namespace.isNullOrEmpty()) {
            event
        } else {
            val sanitizedNamespace = namespace.removeSuffix(".")
            "$sanitizedNamespace.$event"
        }
    }

    fun formatClientEvent(event: String): String {
        return if (event.startsWith("client-")) {
            event
        } else {
            "client-$event"
        }
    }
}
