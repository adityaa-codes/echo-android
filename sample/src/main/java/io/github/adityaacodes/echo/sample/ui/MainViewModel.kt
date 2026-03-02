package io.github.adityaacodes.echo.sample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.adityaacodes.echo.Echo
import io.github.adityaacodes.echo.EchoClient
import io.github.adityaacodes.echo.state.ConnectionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _viewState = MutableStateFlow(MainViewState())
    val viewState: StateFlow<MainViewState> = _viewState.asStateFlow()

    private val _viewEffect = MutableSharedFlow<MainViewEffect>()
    val viewEffect: SharedFlow<MainViewEffect> = _viewEffect.asSharedFlow()

    private var echoClient: EchoClient? = null

    fun processIntent(intent: MainViewIntent) {
        when (intent) {
            is MainViewIntent.Connect -> connect(intent.url)
            MainViewIntent.Disconnect -> disconnect()
        }
    }

    private fun connect(url: String) {
        if (url.isBlank()) {
            viewModelScope.launch {
                _viewEffect.emit(MainViewEffect.ShowToast("URL cannot be empty"))
            }
            return
        }

        viewModelScope.launch {
            try {
                // Initialize the client if not already done
                if (echoClient == null) {
                    echoClient = Echo.create {
                        // Normally you'd configure the client here based on the URL or other settings
                        // For this basic sample, we just create the instance. 
                        // In a real app, Echo.create might require a host/key.
                    }
                }

                // Observe connection state
                launch {
                    echoClient?.state?.collect { state ->
                        _viewState.update { it.copy(connectionState = state) }
                    }
                }

                // Actually initiate connection
                echoClient?.connect()
                _viewState.update { it.copy(connectedUrl = url, errorMessage = null) }

            } catch (e: Exception) {
                _viewState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            echoClient?.disconnect()
            _viewState.update { it.copy(connectedUrl = "") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            echoClient?.disconnect()
        }
    }
}