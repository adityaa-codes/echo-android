package io.github.adityaacodes.echo.sample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.adityaacodes.echo.Echo
import io.github.adityaacodes.echo.EchoClient
import io.github.adityaacodes.echo.state.ConnectionState
import kotlinx.coroutines.Job
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
    private var connectionStateJob: Job? = null
    private var errorStreamJob: Job? = null

    fun processIntent(intent: MainViewIntent) {
        when (intent) {
            is MainViewIntent.Connect -> connect(intent.host, intent.port, intent.useTls, intent.appKey)
            is MainViewIntent.Subscribe -> subscribe(intent.channelName)
            MainViewIntent.Ping -> pingServer()
            MainViewIntent.Disconnect -> disconnect()
        }
    }

    private fun subscribe(channelName: String) {
        if (channelName.isBlank()) return
        echoClient?.channel(channelName)
        updateClientInfo()
    }

    private fun updateClientInfo() {
        val client = echoClient ?: return
        _viewState.update { 
            it.copy(
                socketId = client.socketId,
                activeChannels = client.activeChannels.map { ch -> ch.name }
            ) 
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
                    logging {
                        this.enabled = true
                    }
                }

                // Observe connection state
                connectionStateJob?.cancel()
                errorStreamJob?.cancel()
                connectionStateJob = launch {
                    echoClient?.state?.collect { state ->
                        val errorMessage = if (state is ConnectionState.Disconnected && state.reason != null) {
                            state.reason?.message ?: "Unknown error"
                        } else null
                        
                        _viewState.update { 
                            it.copy(
                                connectionState = state,
                                errorMessage = errorMessage ?: it.errorMessage
                            ) 
                        }
                        updateClientInfo()
                    }
                }
                errorStreamJob = launch {
                    echoClient?.errors?.collect { error ->
                        _viewEffect.emit(MainViewEffect.ShowToast("Echo error: ${error.message ?: "Unknown"}"))
                        _viewState.update { it.copy(errorMessage = error.message ?: it.errorMessage) }
                    }
                }

                // Actually initiate connection
                echoClient?.connect()
                val url = "${if(useTls) "wss" else "ws"}://$host${if(port!=null) ":$port" else ""}"
                _viewState.update { it.copy(connectedUrl = url, errorMessage = null, lastPingSuccessful = null) }

            } catch (e: Exception) {
                _viewState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    private fun pingServer() {
        viewModelScope.launch {
            val client = echoClient
            if (client == null) {
                _viewEffect.emit(MainViewEffect.ShowToast("Connect first"))
                return@launch
            }

            val success = client.ping(timeoutMillis = 5000L)
            _viewState.update { it.copy(lastPingSuccessful = success) }
            _viewEffect.emit(
                MainViewEffect.ShowToast(
                    if (success) "Ping successful" else "Ping timed out",
                ),
            )
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            echoClient?.disconnect()
            _viewState.update { it.copy(connectedUrl = "", lastPingSuccessful = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectionStateJob?.cancel()
        errorStreamJob?.cancel()
        viewModelScope.launch {
            echoClient?.disconnect()
        }
    }
}
