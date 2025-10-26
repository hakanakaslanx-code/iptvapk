package com.example.iptvsmartersclone

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                IPTVHome(lifecycleScope = lifecycleScope)
            }
        }
    }
}

data class Channel(
    val name: String,
    val url: String,
    val group: String? = null,
    val logo: String? = null
)

@Composable
fun IPTVHome(lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope) {
    var playlistUrl by remember { mutableStateOf(TextFieldValue("")) }
    var epgUrl by remember { mutableStateOf(TextFieldValue("")) }
    var channels by remember { mutableStateOf(listOf<Channel>()) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = playlistUrl,
            onValueChange = { playlistUrl = it },
            label = { Text(text = context.getString(R.string.playlist_url_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = epgUrl,
            onValueChange = { epgUrl = it },
            label = { Text(text = context.getString(R.string.epg_url_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                lifecycleScope.launch {
                    val result = loadPlaylist(playlistUrl.text)
                    isLoading = false
                    result.onSuccess {
                        channels = it
                    }.onFailure {
                        errorMessage = it.localizedMessage
                    }
                }
            },
            enabled = playlistUrl.text.isNotBlank()
        ) {
            Text(text = context.getString(R.string.load_playlist))
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        selectedChannel?.let { channel ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = channel.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            AndroidViewComposable(player = player, channel = channel)
        } ?: run {
            Text(text = context.getString(R.string.player_not_ready))
        }

        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(channels) { channel ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { selectedChannel = channel },
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (channel == selectedChannel) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    ListItem(
                        headlineContent = { Text(channel.name) },
                        supportingContent = {
                            if (!channel.group.isNullOrBlank()) {
                                Text(channel.group!!)
                            }
                        },
                        overlineContent = {
                            channel.logo?.let { Text(it) }
                        },
                        trailingContent = {
                            if (channel == selectedChannel) {
                                Text("Playing", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) {
            selectedChannel = channels.first()
        }
    }

    LaunchedEffect(selectedChannel) {
        selectedChannel?.let { channel ->
            player.setMediaItem(MediaItem.fromUri(Uri.parse(channel.url)))
            player.prepare()
            player.playWhenReady = true
        }
    }
}

@Composable
fun AndroidViewComposable(player: ExoPlayer, channel: Channel) {
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

private suspend fun loadPlaylist(url: String): Result<List<Channel>> = withContext(Dispatchers.IO) {
    return@withContext runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("Failed to load playlist: ${'$'}{connection.responseCode}")
        }

        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        val channels = parseM3U(reader)
        reader.close()
        connection.disconnect()
        channels
    }
}

private fun parseM3U(reader: BufferedReader): List<Channel> {
    val channels = mutableListOf<Channel>()
    var currentName: String? = null
    var currentGroup: String? = null
    var currentLogo: String? = null

    reader.forEachLine { line ->
        when {
            line.startsWith("#EXTINF") -> {
                val attributes = line.substringAfter(":", "")
                currentName = attributes.substringAfter(",", attributes)
                currentGroup = Regex("group-title=\"(.*?)\"").find(line)?.groupValues?.getOrNull(1)
                currentLogo = Regex("tvg-logo=\"(.*?)\"").find(line)?.groupValues?.getOrNull(1)
            }
            line.isNotBlank() && !line.startsWith("#") -> {
                val url = line.trim()
                val channel = Channel(
                    name = currentName ?: url,
                    url = url,
                    group = currentGroup,
                    logo = currentLogo
                )
                channels.add(channel)
                currentName = null
                currentGroup = null
                currentLogo = null
            }
        }
    }

    return channels
}
