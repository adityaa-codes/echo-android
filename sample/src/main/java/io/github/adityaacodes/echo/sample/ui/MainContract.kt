package io.github.adityaacodes.echo.sample.ui

import io.github.adityaacodes.echo.state.ConnectionState

data class MainViewState(
    val connectionState: ConnectionState = ConnectionState.Disconnected(),
    val connectedUrl: String = "",
    val errorMessage: String? = null
)

sealed interface MainViewIntent {
    data class Connect(val url: String) : MainViewIntent
    object Disconnect : MainViewIntent
}

sealed interface MainViewEffect {
    data class ShowToast(val message: String) : MainViewEffect
}