package io.github.adityaacodes.echo.sample.ui

import io.github.adityaacodes.echo.sample.BuildConfig
import io.github.adityaacodes.echo.state.ChannelState
import io.github.adityaacodes.echo.state.ConnectionState

enum class ChannelType { PUBLIC, PRIVATE, PRESENCE }

data class ChannelInfo(
    val name: String,
    val type: ChannelType,
    val state: ChannelState = ChannelState.Unsubscribed,
)

data class EventLogEntry(
    val id: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val channel: String?,
    val event: String,
    val data: String?,
)

data class ErrorLogEntry(
    val id: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val message: String,
)

data class MainViewState(
    val connectionState: ConnectionState = ConnectionState.Disconnected(),
    val connectedUrl: String = "",
    val socketId: String? = null,
    val lastPingSuccessful: Boolean? = null,
    val activeChannels: List<ChannelInfo> = emptyList(),
    val eventLog: List<EventLogEntry> = emptyList(),
    val errorLog: List<ErrorLogEntry> = emptyList(),
    val presenceMembers: Map<String, List<String>> = emptyMap(),
)

sealed interface MainViewIntent {
    data class Connect(
        val host: String = BuildConfig.ECHO_SAMPLE_HOST,
        val port: Int? = BuildConfig.ECHO_SAMPLE_PORT,
        val useTls: Boolean = BuildConfig.ECHO_SAMPLE_USE_TLS,
        val appKey: String = BuildConfig.ECHO_SAMPLE_APP_KEY,
        val authEndpoint: String = BuildConfig.ECHO_SAMPLE_AUTH_ENDPOINT,
        val bearerToken: String = "",
    ) : MainViewIntent

    data class SubscribeChannel(
        val channelName: String,
        val type: ChannelType,
    ) : MainViewIntent

    data class LeaveChannel(val channelName: String) : MainViewIntent

    data class ListenEvent(
        val channelName: String,
        val eventName: String,
    ) : MainViewIntent

    data class SendWhisper(
        val channelName: String,
        val event: String,
        val data: String,
    ) : MainViewIntent

    data object Ping : MainViewIntent
    data object Disconnect : MainViewIntent
    data object ClearEventLog : MainViewIntent
    data object ClearErrorLog : MainViewIntent
}

sealed interface MainViewEffect {
    data class ShowToast(val message: String) : MainViewEffect
}
