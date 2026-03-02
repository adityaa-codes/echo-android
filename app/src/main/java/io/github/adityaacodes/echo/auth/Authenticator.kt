package io.github.adityaacodes.echo.auth

public fun interface Authenticator {
    public suspend fun authenticate(socketId: String, channel: String): Result<String>
}
