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
            is MainViewIntent.Connect -> connect(intent.host, intent.port, intent.useTls, intent.appKey)
            MainViewIntent.Disconnect -> disconnect()
        }
    }

    private fun connect(host: String, port: Int?, useTls: Boolean, appKey: String) {
        if (host.isBlank() || appKey.isBlank()) {
            viewModelScope.launch {
                _viewEffect.emit(MainViewEffect.ShowToast("Host and App Key cannot be empty"))
            }
            return
        }

        viewModelScope.launch {
            try {
                // We disconnect and recreate client to allow testing different configs
                echoClient?.disconnect()
                echoClient = Echo.create {
                    client {
                        this.host = host
                        this.port = port
                        this.useTls = useTls
                        this.apiKey = appKey
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
                val url = "${if(useTls) "wss" else "ws"}://$host${if(port!=null) ":$port" else ""}"
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