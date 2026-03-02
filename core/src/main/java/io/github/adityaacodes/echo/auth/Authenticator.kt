package io.github.adityaacodes.echo.auth

/**
 * An interface for providing authentication signatures for private and presence channels.
 */
public fun interface Authenticator {
    /**
     * Authenticates the user for a specific channel.
     * @param socketId The unique socket connection identifier.
     * @param channel The name of the channel attempting to be subscribed.
     * @return A Result containing the raw JSON response from the auth endpoint, or an Exception.
     */
    public suspend fun authenticate(socketId: String, channel: String): Result<String>
}
