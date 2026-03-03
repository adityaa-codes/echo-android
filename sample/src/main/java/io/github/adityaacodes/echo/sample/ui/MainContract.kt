package io.github.adityaacodes.echo.sample.ui

import io.github.adityaacodes.echo.state.ConnectionState

data class MainViewState(
    val connectionState: ConnectionState = ConnectionState.Disconnected(),
    val connectedUrl: String = "",
    val errorMessage: String? = null,
    val socketId: String? = null,
    val activeChannels: List<String> = emptyList(),
    val lastPingSuccessful: Boolean? = null,
)

sealed interface MainViewIntent {
    data class Connect(val host: String, val port: Int?, val useTls: Boolean, val appKey: String) : MainViewIntent
    data class Subscribe(val channelName: String) : MainViewIntent
    object Ping : MainViewIntent
    object Disconnect : MainViewIntent
}

sealed interface MainViewEffect {
    data class ShowToast(val message: String) : MainViewEffect
}
