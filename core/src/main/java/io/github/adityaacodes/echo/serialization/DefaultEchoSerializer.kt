package io.github.adityaacodes.echo.serialization

import io.github.adityaacodes.echo.data.protocol.PusherFrame
import kotlinx.serialization.json.Json

public class DefaultEchoSerializer(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : EchoSerializer {
    override fun encode(frame: PusherFrame): String {
        return json.encodeToString(PusherFrame.serializer(), frame)
    }

    override fun decode(data: String): PusherFrame {
        return json.decodeFromString(PusherFrame.serializer(), data)
    }
}
