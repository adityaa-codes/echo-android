package io.github.adityaacodes.echo

/**
 * A data class representing a generic event received over the connection.
 * @property event The name of the event.
 * @property channel The channel the event was broadcast on, or null if it's a global event.
 * @property data The raw JSON payload of the event.
 */
public data class EchoEvent(
    public val event: String,
    public val channel: String?,
    public val data: String?
)
