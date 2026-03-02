package io.github.adityaacodes.echo

public data class EchoEvent(
    public val event: String,
    public val channel: String?,
    public val data: String?
)
