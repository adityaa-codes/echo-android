package io.github.adityaacodes.echo.serialization

import io.github.adityaacodes.echo.data.protocol.PusherFrame

/**
 * Pluggable JSON serializer for the Echo SDK.
 */
public interface EchoSerializer {
    /**
     * Encodes a [PusherFrame] into a JSON string.
     */
    public fun encode(frame: PusherFrame): String

    /**
     * Decodes a JSON string into a [PusherFrame].
     */
    public fun decode(data: String): PusherFrame
}
