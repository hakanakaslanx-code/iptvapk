package com.example.iptvsmartersclone

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iptvsmartersclone.ui.theme.IPTVSmartersTheme
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVApp()
        }
    }
}

@Composable
fun IPTVApp(viewModel: IPTVViewModel = viewModel()) {
    IPTVSmartersTheme {
        val uiState by viewModel.uiState.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val context = androidx.compose.ui.platform.LocalContext.current
        val player = remember {
            ExoPlayer.Builder(context).build()
        }

        DisposableEffect(player) {
            onDispose { player.release() }
        }

        LaunchedEffect(uiState.errorMessage) {
            uiState.errorMessage?.let { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearError()
            }
        }

        LaunchedEffect(uiState.selectedChannel?.url) {
            uiState.selectedChannel?.let { channel ->
                player.setMediaItem(MediaItem.fromUri(Uri.parse(channel.url)))
                player.prepare()
                player.play()
            } ?: player.stop()
        }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            IPTVHome(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                uiState = uiState,
                onPlaylistUrlChange = viewModel::onPlaylistUrlChange,
                onEpgUrlChange = viewModel::onEpgUrlChange,
                onLoadPlaylist = viewModel::loadPlaylist,
                onChannelSelected = viewModel::selectChannel,
                player = player
            )
        }
    }
}

@Composable
fun IPTVHome(
    modifier: Modifier = Modifier,
    uiState: IPTVUiState,
    onPlaylistUrlChange: (String) -> Unit,
    onEpgUrlChange: (String) -> Unit,
    onLoadPlaylist: () -> Unit,
    onChannelSelected: (Channel) -> Unit,
    player: ExoPlayer
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = uiState.playlistUrl,
            onValueChange = onPlaylistUrlChange,
            label = { Text(text = stringResource(id = R.string.playlist_url_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.epgUrl,
            onValueChange = onEpgUrlChange,
            label = { Text(text = stringResource(id = R.string.epg_url_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onLoadPlaylist,
            enabled = uiState.playlistUrl.isNotBlank() && !uiState.isLoading
        ) {
            Text(text = stringResource(id = R.string.load_playlist))
        }

        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        uiState.selectedChannel?.let { channel ->
            Text(text = channel.name, style = MaterialTheme.typography.titleMedium)
            AndroidViewComposable(player = player)
        } ?: Text(text = stringResource(id = R.string.player_not_ready))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.channels, key = { it.url }) { channel ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (channel == uiState.selectedChannel) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    onClick = { onChannelSelected(channel) }
                ) {
                    ListItem(
                        headlineContent = { Text(channel.name) },
                        supportingContent = {
                            channel.group?.takeIf { it.isNotBlank() }?.let { group ->
                                Text(group)
                            }
                        },
                        overlineContent = {
                            channel.logo?.takeIf { it.isNotBlank() }?.let { logo ->
                                Text(logo)
                            }
                        },
                        trailingContent = {
                            if (channel == uiState.selectedChannel) {
                                Text(
                                    text = stringResource(id = R.string.playing_label),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AndroidViewComposable(player: ExoPlayer) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                useController = true
            }
        },
        update = { view ->
            view.player = player
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}
