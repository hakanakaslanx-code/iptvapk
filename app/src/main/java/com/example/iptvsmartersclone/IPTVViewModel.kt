package com.example.iptvsmartersclone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class Channel(
    val name: String,
    val url: String,
    val group: String? = null,
    val logo: String? = null
)

data class IPTVUiState(
    val playlistUrl: String = "",
    val epgUrl: String = "",
    val channels: List<Channel> = emptyList(),
    val selectedChannel: Channel? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class IPTVViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(IPTVUiState())
    val uiState: StateFlow<IPTVUiState> = _uiState.asStateFlow()

    companion object {
        private val GROUP_REGEX = Regex("group-title=\\"(.*?)\\"")
        private val LOGO_REGEX = Regex("tvg-logo=\\"(.*?)\\"")
    }

    fun onPlaylistUrlChange(value: String) {
        _uiState.update { it.copy(playlistUrl = value) }
    }

    fun onEpgUrlChange(value: String) {
        _uiState.update { it.copy(epgUrl = value) }
    }

    fun selectChannel(channel: Channel) {
        _uiState.update { state ->
            if (state.channels.any { it.url == channel.url }) {
                state.copy(selectedChannel = channel)
            } else {
                state
            }
        }
    }

    fun loadPlaylist() {
        val playlistUrl = _uiState.value.playlistUrl.trim()
        if (playlistUrl.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = fetchPlaylist(playlistUrl)
            result.onSuccess { channels ->
                _uiState.update { state ->
                    val previousChannel = state.selectedChannel
                    val nextChannel = when {
                        channels.isEmpty() -> null
                        previousChannel == null -> channels.first()
                        else -> channels.find { it.url == previousChannel.url } ?: channels.first()
                    }
                    state.copy(
                        channels = channels,
                        selectedChannel = nextChannel,
                        isLoading = false
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: throwable.toString(),
                        channels = emptyList(),
                        selectedChannel = null
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun fetchPlaylist(url: String): Result<List<Channel>> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Failed to load playlist: ${'$'}{connection.responseCode}")
                }

                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    parseM3U(reader)
                }
            } finally {
                connection.disconnect()
            }
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
                    currentGroup = GROUP_REGEX.find(line)?.groupValues?.getOrNull(1)
                    currentLogo = LOGO_REGEX.find(line)?.groupValues?.getOrNull(1)
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
}
