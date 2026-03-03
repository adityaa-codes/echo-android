package io.github.adityaacodes.echo.sample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.adityaacodes.echo.Echo
import io.github.adityaacodes.echo.EchoClient
import io.github.adityaacodes.echo.auth.Authenticator
import io.github.adityaacodes.echo.channel.EchoChannel
import io.github.adityaacodes.echo.channel.PresenceChannel
import io.github.adityaacodes.echo.state.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

class MainViewModel : ViewModel() {

    private val _viewState = MutableStateFlow(MainViewState())
    val viewState: StateFlow<MainViewState> = _viewState.asStateFlow()

    private val _viewEffect = MutableSharedFlow<MainViewEffect>()
    val viewEffect: SharedFlow<MainViewEffect> = _viewEffect.asSharedFlow()

    private var echoClient: EchoClient? = null
    private val channels = mutableMapOf<String, EchoChannel>()
    private val presenceChannels = mutableMapOf<String, PresenceChannel>()
    private val channelJobs = mutableMapOf<String, MutableList<Job>>()
    private var connectionStateJob: Job? = null
    private var errorStreamJob: Job? = null
    private var globalEventsJob: Job? = null
    private val eventIdCounter = AtomicInteger(0)
    private val errorIdCounter = AtomicInteger(0)

    fun processIntent(intent: MainViewIntent) {
        when (intent) {
            is MainViewIntent.Connect -> connect(intent)
            is MainViewIntent.SubscribeChannel -> subscribeChannel(intent.channelName, intent.type)
            is MainViewIntent.LeaveChannel -> leaveChannel(intent.channelName)
            is MainViewIntent.ListenEvent -> listenEvent(intent.channelName, intent.eventName)
            is MainViewIntent.SendWhisper -> sendWhisper(intent.channelName, intent.event, intent.data)
            MainViewIntent.Ping -> pingServer()
            MainViewIntent.Disconnect -> disconnect()
            MainViewIntent.ClearEventLog -> _viewState.update { it.copy(eventLog = emptyList()) }
            MainViewIntent.ClearErrorLog -> _viewState.update { it.copy(errorLog = emptyList()) }
        }
    }

    private fun subscribeChannel(name: String, type: ChannelType) {
        if (name.isBlank()) return
        val client = echoClient ?: run {
            toast("Connect first")
            return
        }

        val channel: EchoChannel = when (type) {
            ChannelType.PUBLIC -> client.channel(name)
            ChannelType.PRIVATE -> client.private(name)
            ChannelType.PRESENCE -> client.presence(name)
        }

        channels[channel.name] = channel
        if (channel is PresenceChannel) {
            presenceChannels[channel.name] = channel
            observePresenceMembers(channel)
        }
        observeChannelState(channel)
        refreshChannelList()
    }

    private fun observeChannelState(channel: EchoChannel) {
        val job = viewModelScope.launch {
            channel.state.collect { refreshChannelList() }
        }
        channelJobs.getOrPut(channel.name) { mutableListOf() }.add(job)
    }

    private fun observePresenceMembers(channel: PresenceChannel) {
        val job = viewModelScope.launch {
            channel.members.collect { members ->
                _viewState.update { state ->
                    state.copy(
                        presenceMembers = state.presenceMembers +
                            (channel.name to members.map { it.id }),
                    )
                }
            }
        }
        channelJobs.getOrPut(channel.name) { mutableListOf() }.add(job)
    }

    private fun leaveChannel(name: String) {
        channels.remove(name)?.leave()
        presenceChannels.remove(name)
        channelJobs.remove(name)?.forEach { it.cancel() }
        _viewState.update { it.copy(presenceMembers = it.presenceMembers - name) }
        refreshChannelList()
        toast("Left $name")
    }

    private fun listenEvent(channelName: String, eventName: String) {
        val channel = channels[channelName] ?: run {
            toast("Channel '$channelName' not found")
            return
        }
        val job = viewModelScope.launch {
            channel.listen(eventName).collect { data ->
                appendEvent(channelName, eventName, data)
            }
        }
        channelJobs.getOrPut(channelName) { mutableListOf() }.add(job)
        toast("Listening for '$eventName' on $channelName")
    }

    private fun sendWhisper(channelName: String, event: String, data: String) {
        val channel = channels[channelName] ?: run {
            toast("Channel '$channelName' not found")
            return
        }
        viewModelScope.launch {
            channel.whisper(event, data).fold(
                onSuccess = { toast("Whisper sent: client-$event") },
                onFailure = { toast("Whisper failed: ${it.message}") },
            )
        }
    }

    private fun connect(config: MainViewIntent.Connect) {
        val normalizedHost = config.host.normalizeHostInput()

        if (normalizedHost.isBlank() || config.appKey.isBlank()) {
            toast("Host and App Key are required")
            return
        }

        viewModelScope.launch {
            try {
                cleanupConnection()

                val authenticator = createAuthenticator(config.authEndpoint, config.bearerToken)

                echoClient = Echo.create {
                    client {
                        host = normalizedHost
                        port = config.port
                        useTls = config.useTls
                        apiKey = config.appKey
                    }
                    auth {
                        this.authenticator = authenticator
                    }
                    logging { enabled = true }
                }

                connectionStateJob = launch {
                    echoClient?.state?.collect { state ->
                        _viewState.update {
                            it.copy(connectionState = state)
                        }
                        refreshChannelList()
                    }
                }

                errorStreamJob = launch {
                    echoClient?.errors?.collect { error ->
                        val entry = ErrorLogEntry(
                            id = errorIdCounter.incrementAndGet(),
                            type = error::class.simpleName ?: "Unknown",
                            message = error.message ?: "Unknown error",
                        )
                        _viewState.update { state ->
                            state.copy(
                                errorLog = (state.errorLog + entry).takeLast(MAX_LOG_SIZE),
                            )
                        }
                        toast("Error: ${error.message}")
                    }
                }

                globalEventsJob = launch {
                    echoClient?.globalEvents?.collect { event ->
                        appendEvent(event.channel, event.event, event.data)
                    }
                }

                echoClient?.connect()

                val scheme = if (config.useTls) "wss" else "ws"
                val portSuffix = config.port?.let { ":$it" } ?: ""
                _viewState.update {
                    it.copy(
                        connectedUrl = "$scheme://$normalizedHost$portSuffix",
                        lastPingSuccessful = null,
                    )
                }
            } catch (e: Exception) {
                toast("Connection failed (${e::class.simpleName}): ${e.message}")
            }
        }
    }

    private fun pingServer() {
        viewModelScope.launch {
            val client = echoClient ?: run {
                toast("Connect first")
                return@launch
            }
            val success = client.ping(timeoutMillis = 5000L)
            _viewState.update { it.copy(lastPingSuccessful = success) }
            toast(if (success) "Ping successful ✓" else "Ping timed out ✗")
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            _viewState.update {
                it.copy(
                    connectionState = ConnectionState.Disconnected(),
                    connectedUrl = "",
                    socketId = null,
                    lastPingSuccessful = null,
                    activeChannels = emptyList(),
                    presenceMembers = emptyMap(),
                )
            }
            cleanupConnection()
        }
    }

    private suspend fun cleanupConnection() {
        val clientToDisconnect = echoClient
        echoClient = null

        connectionStateJob?.cancel()
        errorStreamJob?.cancel()
        globalEventsJob?.cancel()
        channelJobs.values.flatten().forEach { it.cancel() }
        channelJobs.clear()
        channels.clear()
        presenceChannels.clear()
        viewModelScope.launch {
            clientToDisconnect?.disconnect()
        }
    }

    private fun refreshChannelList() {
        val client = echoClient ?: return
        val infos = channels.map { (name, channel) ->
            ChannelInfo(
                name = name,
                type = when {
                    name.startsWith("presence-") -> ChannelType.PRESENCE
                    name.startsWith("private-") -> ChannelType.PRIVATE
                    else -> ChannelType.PUBLIC
                },
                state = channel.state.value,
            )
        }
        _viewState.update {
            it.copy(socketId = client.socketId, activeChannels = infos)
        }
    }

    private fun appendEvent(channel: String?, event: String, data: String?) {
        val entry = EventLogEntry(
            id = eventIdCounter.incrementAndGet(),
            channel = channel,
            event = event,
            data = data,
        )
        _viewState.update { state ->
            state.copy(eventLog = (state.eventLog + entry).takeLast(MAX_LOG_SIZE))
        }
    }

    private fun toast(message: String) {
        viewModelScope.launch { _viewEffect.emit(MainViewEffect.ShowToast(message)) }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { cleanupConnection() }
    }

    companion object {
        private const val MAX_LOG_SIZE = 100

        private fun createAuthenticator(endpoint: String, token: String): Authenticator? {
            if (endpoint.isBlank()) return null
            return Authenticator { socketId, channel ->
                withContext(Dispatchers.IO) {
                    try {
                        val url = URL(endpoint)
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                            setRequestProperty("Accept", "application/json")
                            if (token.isNotBlank()) {
                                setRequestProperty("Authorization", "Bearer $token")
                            }
                            doOutput = true
                        }
                        conn.outputStream.bufferedWriter().use { writer ->
                            val body = "socket_id=${URLEncoder.encode(socketId, "UTF-8")}" +
                                "&channel_name=${URLEncoder.encode(channel, "UTF-8")}"
                            writer.write(body)
                        }
                        val response = conn.inputStream.bufferedReader().readText()
                        conn.disconnect()
                        Result.success(response)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        }
    }
}

private fun String.normalizeHostInput(): String {
    return this
        .trim()
        .removePrefix("ws://")
        .removePrefix("wss://")
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore("/")
}
