package io.github.adityaacodes.echo.sample

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.adityaacodes.echo.sample.ui.MainViewEffect
import io.github.adityaacodes.echo.sample.ui.MainViewIntent
import io.github.adityaacodes.echo.sample.ui.MainViewModel
import io.github.adityaacodes.echo.sample.ui.theme.EchoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EchoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewState by viewModel.viewState.collectAsState()
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        viewModel.viewEffect.collect { effect ->
                            when (effect) {
                                is MainViewEffect.ShowToast -> {
                                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    MainScreen(
                        state = viewState,
                        onIntent = viewModel::processIntent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: io.github.adityaacodes.echo.sample.ui.MainViewState,
    onIntent: (MainViewIntent) -> Unit
) {
    var hostInput by remember { mutableStateOf("10.0.2.2") }
    var portInput by remember { mutableStateOf("8080") }
    var keyInput by remember { mutableStateOf("reverb-app-key") }
    var useTls by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Echo SDK Sample") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Connection State: ${state.connectionState}",
                style = MaterialTheme.typography.titleMedium
            )

            if (state.errorMessage != null) {
                Text(
                    text = "Error: ${state.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedTextField(
                value = hostInput,
                onValueChange = { hostInput = it },
                label = { Text("Host (e.g., 10.0.2.2)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("Port (e.g., 8080)") },
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(checked = useTls, onCheckedChange = { useTls = it })
                    Text("Use TLS (wss)")
                }
            }

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("App Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        onIntent(MainViewIntent.Connect(
                            host = hostInput, 
                            port = portInput.toIntOrNull(), 
                            useTls = useTls, 
                            appKey = keyInput
                        )) 
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Connect")
                }

                OutlinedButton(
                    onClick = { onIntent(MainViewIntent.Disconnect) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disconnect")
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text("Subscribed Channels (Coming Soon)", style = MaterialTheme.typography.titleSmall)
            // TODO: Display list of channels
        }
    }
}
