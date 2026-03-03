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
public sealed class PusherFrame

@Serializable
public data class ConnectionEstablished(
    public val data: String,
) : PusherFrame()

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class Ping(
    @EncodeDefault public val event: String = "pusher:ping",
    public val data: JsonElement? = null,
) : PusherFrame()

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class Pong(
    @EncodeDefault public val event: String = "pusher:pong",
    public val data: JsonElement? = null,
) : PusherFrame()

@Serializable
public data class ErrorFrame(
    public val data: ErrorData? = null,
) : PusherFrame() {
    @Serializable
    public data class ErrorData(
        public val message: String,
        public val code: Int? = null,
    )
}

@Serializable
public data class SubscriptionSucceeded(
    public val channel: String,
    public val data: String? = null,
) : PusherFrame()

@Serializable
public data class MemberAdded(
    public val channel: String,
    public val data: String? = null,
) : PusherFrame()

@Serializable
public data class MemberRemoved(
    public val channel: String,
    public val data: String? = null,
) : PusherFrame()

@Serializable
public data class GenericEvent(
    public val event: String,
    public val channel: String? = null,
    public val data: JsonElement? = null,
) : PusherFrame()

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class SubscribeCommand(
    @EncodeDefault public val event: String = "pusher:subscribe",
    public val data: SubscribeData,
) : PusherFrame() {
    @Serializable
    public data class SubscribeData(
        public val channel: String,
        public val auth: String? = null,
        public val channel_data: String? = null,
    )
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class UnsubscribeCommand(
    @EncodeDefault public val event: String = "pusher:unsubscribe",
    public val data: UnsubscribeData,
) : PusherFrame() {
    @Serializable
    public data class UnsubscribeData(
        public val channel: String,
    )
}

@Serializable
public data class WhisperCommand(
    public val event: String,
    public val channel: String,
    public val data: JsonElement? = null,
) : PusherFrame()

public object PusherFrameSerializer : JsonContentPolymorphicSerializer<PusherFrame>(PusherFrame::class) {
    public override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PusherFrame> {
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
