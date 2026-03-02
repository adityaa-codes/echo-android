package io.github.adityaacodes.echo.error

public sealed class EchoError(message: String?, cause: Throwable? = null) : Exception(message, cause) {
    public data class Network(public override val cause: Throwable) : EchoError("Network error", cause)
    public data class Auth(public val status: Int, public val body: String) : EchoError("Auth failed with status $status")
    public data class Protocol(public val code: Int, public override val message: String) : EchoError("Protocol error $code: $message")
    public data class Serialization(public override val cause: Throwable) : EchoError("Serialization error", cause)
}
