package io.github.adityaacodes.echo.sample

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.adityaacodes.echo.sample.ui.ChannelInfo
import io.github.adityaacodes.echo.sample.ui.ChannelType
import io.github.adityaacodes.echo.sample.ui.ErrorLogEntry
import io.github.adityaacodes.echo.sample.ui.EventLogEntry
import io.github.adityaacodes.echo.sample.ui.MainViewEffect
import io.github.adityaacodes.echo.sample.ui.MainViewIntent
import io.github.adityaacodes.echo.sample.ui.MainViewModel
import io.github.adityaacodes.echo.sample.ui.MainViewState
import io.github.adityaacodes.echo.sample.ui.theme.EchoTheme
import io.github.adityaacodes.echo.state.ChannelState
import io.github.adityaacodes.echo.state.ConnectionState
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        setContent {
            EchoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val viewState by viewModel.viewState.collectAsState()
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        viewModel.viewEffect.collect { effect ->
                            when (effect) {
                                is MainViewEffect.ShowToast ->
                                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    MainScreen(state = viewState, onIntent = viewModel::processIntent)
                }
            }
        }
    }
}

// region Main Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: MainViewState, onIntent: (MainViewIntent) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Echo SDK Sample")
                        ConnectionBadge(state.connectionState)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ConnectionStatusSection(state) }
            item { ConnectionConfigSection(onIntent) }
            item { ChannelSubscriptionSection(state, onIntent) }
            item { ActiveChannelsSection(state, onIntent) }

            if (state.presenceMembers.isNotEmpty()) {
                item { PresenceMembersSection(state) }
            }

            item { EventListenerSection(state, onIntent) }
            item { EventLogSection(state, onIntent) }
            item { ErrorLogSection(state, onIntent) }

            // Bottom spacing
            item { Box(modifier = Modifier.padding(8.dp)) }
        }
    }
}

// endregion

// region Connection Status

@Composable
fun ConnectionBadge(connectionState: ConnectionState) {
    val (color, label) = when (connectionState) {
        is ConnectionState.Connected -> Color(0xFF4CAF50) to "Connected"
        is ConnectionState.Connecting -> Color(0xFFFFC107) to "Connecting"
        is ConnectionState.Reconnecting -> Color(0xFFFF9800) to "Reconnecting"
        is ConnectionState.Disconnected -> Color(0xFFF44336) to "Disconnected"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun ConnectionStatusSection(state: MainViewState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Connection Info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            StatusRow("State", state.connectionState.toLabel())
            StatusRow("URL", state.connectedUrl.ifBlank { "—" })
            StatusRow("Socket ID", state.socketId ?: "—")
            StatusRow(
                "Last Ping",
                when (state.lastPingSuccessful) {
                    true -> "✓ Success"
                    false -> "✗ Timed out"
                    null -> "Not run"
                },
            )
            if (state.connectionState is ConnectionState.Reconnecting) {
                StatusRow("Attempt", "#${state.connectionState.attempt}")
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// endregion

// region Connection Config

@Composable
fun ConnectionConfigSection(onIntent: (MainViewIntent) -> Unit) {
    var hostInput by remember { mutableStateOf(BuildConfig.ECHO_SAMPLE_HOST) }
    var portInput by remember { mutableStateOf(BuildConfig.ECHO_SAMPLE_PORT.toString()) }
    var keyInput by remember { mutableStateOf(BuildConfig.ECHO_SAMPLE_APP_KEY) }
    var useTls by remember { mutableStateOf(BuildConfig.ECHO_SAMPLE_USE_TLS) }
    var authEndpoint by remember { mutableStateOf(BuildConfig.ECHO_SAMPLE_AUTH_ENDPOINT) }
    var bearerToken by remember { mutableStateOf("") }
    var showAuth by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Server Configuration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = hostInput,
                onValueChange = { hostInput = it },
                label = { Text("Host") },
                placeholder = { Text("10.0.2.2") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                singleLine = true,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("Port") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("App Key") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    singleLine = true,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useTls, onCheckedChange = { useTls = it })
                Text("TLS (wss://)", style = MaterialTheme.typography.bodyMedium)

                Box(modifier = Modifier.weight(1f))

                TextButton(onClick = { showAuth = !showAuth }) {
                    Text(if (showAuth) "Hide Auth" else "Show Auth")
                }
            }

            AnimatedVisibility(visible = showAuth) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Auth Config (required for private/presence channels)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = authEndpoint,
                        onValueChange = { authEndpoint = it },
                        label = { Text("Auth Endpoint URL") },
                        placeholder = { Text("http://10.0.2.2:8000/broadcasting/auth") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = bearerToken,
                        onValueChange = { bearerToken = it },
                        label = { Text("Bearer Token (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        singleLine = true,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                androidx.compose.material3.Button(
                    onClick = {
                        onIntent(
                            MainViewIntent.Connect(
                                host = hostInput,
                                port = portInput.toIntOrNull(),
                                useTls = useTls,
                                appKey = keyInput,
                                authEndpoint = authEndpoint,
                                bearerToken = bearerToken,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Connect") }

                OutlinedButton(
                    onClick = { onIntent(MainViewIntent.Disconnect) },
                    modifier = Modifier.weight(1f),
                ) { Text("Disconnect") }

                OutlinedButton(
                    onClick = { onIntent(MainViewIntent.Ping) },
                    modifier = Modifier.weight(1f),
                ) { Text("Ping") }
            }
        }
    }
}

// endregion

// region Channel Subscription

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelSubscriptionSection(state: MainViewState, onIntent: (MainViewIntent) -> Unit) {
    var channelInput by remember { mutableStateOf("my-channel") }
    var selectedType by remember { mutableStateOf(ChannelType.PUBLIC) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Subscribe to Channel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.name) },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = channelInput,
                    onValueChange = { channelInput = it },
                    label = { Text("Channel Name") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    singleLine = true,
                    supportingText = {
                        val prefix = when (selectedType) {
                            ChannelType.PRIVATE -> "private-"
                            ChannelType.PRESENCE -> "presence-"
                            ChannelType.PUBLIC -> ""
                        }
                        if (prefix.isNotEmpty()) Text("Prefix '$prefix' added automatically")
                    },
                )
                androidx.compose.material3.Button(
                    onClick = { onIntent(MainViewIntent.SubscribeChannel(channelInput, selectedType)) },
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) { Text("Subscribe") }
            }
        }
    }
}

// endregion

// region Active Channels

@Composable
fun ActiveChannelsSection(state: MainViewState, onIntent: (MainViewIntent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Active Channels (${state.activeChannels.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            if (state.activeChannels.isEmpty()) {
                Text(
                    "No channels subscribed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.activeChannels.forEach { channel ->
                    ChannelRow(channel, onLeave = { onIntent(MainViewIntent.LeaveChannel(channel.name)) })
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: ChannelInfo, onLeave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelStateBadge(channel.state)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(channel.name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            Text(
                channel.type.name.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onLeave) {
            Icon(Icons.Default.Close, contentDescription = "Leave", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun ChannelStateBadge(channelState: ChannelState) {
    val (color, label) = when (channelState) {
        is ChannelState.Subscribed -> Color(0xFF4CAF50) to "●"
        is ChannelState.Subscribing -> Color(0xFFFFC107) to "◐"
        is ChannelState.Unsubscribed -> Color(0xFF9E9E9E) to "○"
        is ChannelState.Failed -> Color(0xFFF44336) to "✗"
    }
    Text(label, color = color, style = MaterialTheme.typography.titleMedium)
}

// endregion

// region Presence Members

@Composable
fun PresenceMembersSection(state: MainViewState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Presence Members", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            state.presenceMembers.forEach { (channelName, members) ->
                Text(channelName, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                if (members.isEmpty()) {
                    Text(
                        "  No members yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    members.forEach { memberId ->
                        Text("  • $memberId", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// endregion

// region Event Listener & Whisper

@Composable
fun EventListenerSection(state: MainViewState, onIntent: (MainViewIntent) -> Unit) {
    var selectedChannel by remember { mutableStateOf("") }
    var eventName by remember { mutableStateOf("") }
    var whisperEvent by remember { mutableStateOf("") }
    var whisperData by remember { mutableStateOf("{}") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Event Listener & Whisper", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            // Channel selector
            if (state.activeChannels.isNotEmpty()) {
                Text("Select channel:", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.activeChannels.take(5).forEach { ch ->
                        FilterChip(
                            selected = selectedChannel == ch.name,
                            onClick = { selectedChannel = ch.name },
                            label = {
                                Text(
                                    ch.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Listen for event
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = eventName,
                        onValueChange = { eventName = it },
                        label = { Text("Event Name") },
                        placeholder = { Text("MessageSent") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = {
                            if (selectedChannel.isNotBlank() && eventName.isNotBlank()) {
                                onIntent(MainViewIntent.ListenEvent(selectedChannel, eventName))
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterVertically),
                    ) { Text("Listen") }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Whisper (client events)
                Text(
                    "Send Whisper (private/presence only)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = whisperEvent,
                        onValueChange = { whisperEvent = it },
                        label = { Text("Event") },
                        placeholder = { Text("typing") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = whisperData,
                        onValueChange = { whisperData = it },
                        label = { Text("Data (JSON)") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        singleLine = true,
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (selectedChannel.isNotBlank() && whisperEvent.isNotBlank()) {
                            onIntent(MainViewIntent.SendWhisper(selectedChannel, whisperEvent, whisperData))
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Send Whisper") }
            } else {
                Text(
                    "Subscribe to a channel first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// endregion

// region Event Log

@Composable
fun EventLogSection(state: MainViewState, onIntent: (MainViewIntent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Event Log (${state.eventLog.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (state.eventLog.isNotEmpty()) {
                    IconButton(onClick = { onIntent(MainViewIntent.ClearEventLog) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (state.eventLog.isEmpty()) {
                Text(
                    "No events received yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.eventLog.takeLast(20).reversed().forEach { entry ->
                    EventLogRow(entry)
                }
                if (state.eventLog.size > 20) {
                    Text(
                        "… and ${state.eventLog.size - 20} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventLogRow(entry: EventLogEntry) {
    val time = remember(entry.timestamp) { formatTime(entry.timestamp) }
    val isSystem = entry.event.startsWith("pusher:")

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(time, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            Text(
                entry.channel ?: "global",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                entry.event,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (!isSystem) FontWeight.Bold else FontWeight.Normal,
                color = if (isSystem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!entry.data.isNullOrBlank() && entry.data != "{}") {
            Text(
                entry.data,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// endregion

// region Error Log

@Composable
fun ErrorLogSection(state: MainViewState, onIntent: (MainViewIntent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.errorLog.isNotEmpty()) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Error Log (${state.errorLog.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (state.errorLog.isNotEmpty()) {
                    IconButton(onClick = { onIntent(MainViewIntent.ClearErrorLog) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (state.errorLog.isEmpty()) {
                Text(
                    "No errors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.errorLog.takeLast(10).reversed().forEach { entry ->
                    ErrorLogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ErrorLogRow(entry: ErrorLogEntry) {
    val time = remember(entry.timestamp) { formatTime(entry.timestamp) }
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(time, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        Text(entry.type, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(
            entry.message,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// endregion

// region Helpers

private fun ConnectionState.toLabel(): String = when (this) {
    is ConnectionState.Connected -> "Connected (${socketId.take(12)}…)"
    is ConnectionState.Connecting -> "Connecting…"
    is ConnectionState.Reconnecting -> "Reconnecting (attempt #$attempt)"
    is ConnectionState.Disconnected -> if (reason != null) "Disconnected: ${reason?.message}" else "Disconnected"
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))

// endregion
