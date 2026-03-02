package io.github.adityaacodes.echo.data.protocol
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable(with = PusherFrameSerializer::class)
internal sealed class PusherFrame

@Serializable
internal data class ConnectionEstablished(
    val data: String
) : PusherFrame()

@Serializable
internal data class Ping(
    val data: JsonElement? = null
) : PusherFrame()

@Serializable
internal data class Pong(
    val data: JsonElement? = null
) : PusherFrame()

@Serializable
internal data class ErrorFrame(
    val data: ErrorData? = null
) : PusherFrame() {
    @Serializable
    internal data class ErrorData(
        val message: String,
        val code: Int? = null
    )
}

@Serializable
internal data class SubscriptionSucceeded(
    val channel: String,
    val data: String? = null
) : PusherFrame()

@Serializable
internal data class MemberAdded(
    val channel: String,
    val data: String? = null
) : PusherFrame()

@Serializable
internal data class MemberRemoved(
    val channel: String,
    val data: String? = null
) : PusherFrame()

@Serializable
internal data class GenericEvent(
    val event: String,
    val channel: String? = null,
    val data: JsonElement? = null
) : PusherFrame()

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SubscribeCommand(
    @EncodeDefault val event: String = "pusher:subscribe",
    val data: SubscribeData
) : PusherFrame() {
    @Serializable
    internal data class SubscribeData(
        val channel: String,
        val auth: String? = null,
        val channel_data: String? = null
    )
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class UnsubscribeCommand(
    @EncodeDefault val event: String = "pusher:unsubscribe",
    val data: UnsubscribeData
) : PusherFrame() {
    @Serializable
    internal data class UnsubscribeData(
        val channel: String
    )
}

@Serializable
internal data class WhisperCommand(
    val event: String,
    val channel: String,
    val data: JsonElement? = null
) : PusherFrame()

internal object PusherFrameSerializer : JsonContentPolymorphicSerializer<PusherFrame>(PusherFrame::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PusherFrame> {
        val event = element.jsonObject["event"]?.jsonPrimitive?.content ?: return GenericEvent.serializer()
        
        return when (event) {
            "pusher:connection_established" -> ConnectionEstablished.serializer()
            "pusher:ping" -> Ping.serializer()
            "pusher:pong" -> Pong.serializer()
            "pusher:error" -> ErrorFrame.serializer()
            "pusher_internal:subscription_succeeded" -> SubscriptionSucceeded.serializer()
            "pusher_internal:member_added" -> MemberAdded.serializer()
            "pusher_internal:member_removed" -> MemberRemoved.serializer()
            "pusher:subscribe" -> SubscribeCommand.serializer()
            "pusher:unsubscribe" -> UnsubscribeCommand.serializer()
            else -> GenericEvent.serializer()
        }
    }
}
