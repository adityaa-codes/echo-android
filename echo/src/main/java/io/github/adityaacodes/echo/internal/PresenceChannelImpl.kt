package io.github.adityaacodes.echo.internal

import io.github.adityaacodes.echo.channel.PresenceChannel
import io.github.adityaacodes.echo.data.protocol.MemberAdded
import io.github.adityaacodes.echo.data.protocol.MemberRemoved
import io.github.adityaacodes.echo.data.protocol.MemberUpdated
import io.github.adityaacodes.echo.data.protocol.SubscriptionSucceeded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class PresenceChannelImpl(
    private val delegate: EchoChannelImpl,
    private val eventRouter: EventRouter,
    private val scope: CoroutineScope,
    private val json: Json
) : PresenceChannel, io.github.adityaacodes.echo.channel.EchoChannel by delegate {

    private data class MemberImpl(
        override val id: String,
        override val info: JsonElement?
    ) : PresenceChannel.Member

    private val _members = MutableStateFlow<List<PresenceChannel.Member>>(emptyList())
    override val members: StateFlow<List<PresenceChannel.Member>> = _members.asStateFlow()

    private var updatingCallback: ((PresenceChannel.Member) -> Unit)? = null

    override fun updating(callback: (PresenceChannel.Member) -> Unit): PresenceChannel {
        updatingCallback = callback
        return this
    }

    init {
        scope.launch {
            eventRouter.channelEvents(name)
                .filterIsInstance<SubscriptionSucceeded>()
                .collect { frame ->
                    val dataString = frame.data
                    if (dataString != null) {
                        try {
                            val dataJson = json.parseToJsonElement(dataString).jsonObject
                            val presence = dataJson["presence"]?.jsonObject
                            if (presence != null) {
                                val hash = presence["hash"]?.jsonObject
                                val initialMembers = hash?.map { (key, value) ->
                                    MemberImpl(key, value)
                                } ?: emptyList()
                                _members.value = initialMembers
                            }
                        } catch (e: Exception) {
                            // Ignore parsing errors for now
                        }
                    }
                }
        }

        scope.launch {
            eventRouter.channelEvents(name)
                .filterIsInstance<MemberAdded>()
                .collect { frame ->
                    val dataString = frame.data
                    if (dataString != null) {
                        try {
                            val dataJson = json.parseToJsonElement(dataString).jsonObject
                            val userId = dataJson["user_id"]?.jsonPrimitive?.content
                            val userInfo = dataJson["user_info"]
                            
                            if (userId != null) {
                                val newMember = MemberImpl(userId, userInfo)
                                val currentMembers = _members.value.toMutableList()
                                // Remove if already exists to prevent duplicates, then add
                                currentMembers.removeAll { it.id == userId }
                                currentMembers.add(newMember)
                                _members.value = currentMembers.toList()
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
        }

        scope.launch {
            eventRouter.channelEvents(name)
                .filterIsInstance<MemberRemoved>()
                .collect { frame ->
                    val dataString = frame.data
                    if (dataString != null) {
                        try {
                            val dataJson = json.parseToJsonElement(dataString).jsonObject
                            val userId = dataJson["user_id"]?.jsonPrimitive?.content
                            if (userId != null) {
                                val currentMembers = _members.value.toMutableList()
                                currentMembers.removeAll { it.id == userId }
                                _members.value = currentMembers.toList()
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
        }
        scope.launch {
            eventRouter.channelEvents(name)
                .filterIsInstance<MemberUpdated>()
                .collect { frame ->
                    val dataString = frame.data
                    if (dataString != null) {
                        try {
                            val dataJson = json.parseToJsonElement(dataString).jsonObject
                            val userId = dataJson["user_id"]?.jsonPrimitive?.content
                            val userInfo = dataJson["user_info"]
                            
                            if (userId != null) {
                                val updatedMember = MemberImpl(userId, userInfo)
                                val currentMembers = _members.value.toMutableList()
                                val index = currentMembers.indexOfFirst { it.id == userId }
                                if (index != -1) {
                                    currentMembers[index] = updatedMember
                                } else {
                                    currentMembers.add(updatedMember)
                                }
                                _members.value = currentMembers.toList()
                                updatingCallback?.invoke(updatedMember)
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
        }
    }
}
