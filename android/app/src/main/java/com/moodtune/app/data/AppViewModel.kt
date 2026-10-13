package com.moodtune.app.data

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moodtune.app.model.ChatMessage
import com.moodtune.app.model.ChatRole
import com.moodtune.app.model.HealthAccessStatus
import com.moodtune.app.model.HealthSnapshot
import com.moodtune.app.model.Mood
import com.moodtune.app.model.MoodAssessment
import com.moodtune.app.model.MoodEngine
import com.moodtune.app.model.PlaybackRoute
import com.moodtune.app.model.PlaylistCatalog
import com.moodtune.app.model.PlaylistSuggestion
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class PermissionPrompt(val id: Int, val permissions: Set<String>)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var assessment by mutableStateOf(MoodAssessment.waiting)
        private set
    var snapshot by mutableStateOf<HealthSnapshot?>(null)
        private set
    var healthStatus by mutableStateOf<HealthAccessStatus>(HealthAccessStatus.Unknown)
        private set
    var playlists by mutableStateOf<List<PlaylistSuggestion>>(emptyList())
        private set
    var spotifyName by mutableStateOf<String?>(null)
        private set
    var isSpotifyConnected by mutableStateOf(false)
        private set
    var isLoadingPlaylists by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var didFinishOnboarding by mutableStateOf(false)
        private set
    var useSampleHealthData by mutableStateOf(true)
        private set
    var clientId by mutableStateOf("")
        private set
    var permissionPrompt by mutableStateOf<PermissionPrompt?>(null)
        private set

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val health = HealthService(application)
    private val spotify = SpotifyClient(application)
    private val gate = Mutex()
    private var didBootstrap = false
    private var playlistMood: Mood? = null
    private var permissionId = 0
    private var permissionContinuation: kotlinx.coroutines.CancellableContinuation<Unit>? = null

    private val authListener = object : SpotifyAuthRelay.Listener {
        override fun onRedirect(uri: Uri) = spotify.onRedirect(uri)
        override fun onCancel() = spotify.onCancel()
    }

    init {
        SpotifyAuthRelay.listener = authListener
        clientId = prefs.getString(Keys.CLIENT_ID, "").orEmpty()
        useSampleHealthData = if (prefs.contains(Keys.SAMPLE)) prefs.getBoolean(Keys.SAMPLE, true) else true
        didFinishOnboarding = prefs.getBoolean(Keys.ONBOARDING, false)
        messages = loadMessages()
        isSpotifyConnected = spotify.hasStoredSession()
        if (useSampleHealthData) {
            snapshot = HealthSnapshot.sample
        }
        recompute(resetPlaylists = true)
    }

    override fun onCleared() {
        if (SpotifyAuthRelay.listener === authListener) {
            SpotifyAuthRelay.listener = null
        }
        super.onCleared()
    }

    fun updateClientId(value: String) {
        clientId = value
        prefs.edit().putString(Keys.CLIENT_ID, value).apply()
    }

    fun bootstrap() = enqueue {
        if (didBootstrap) return@enqueue
        didBootstrap = true
        completePendingLogin()
        restoreSpotifyProfile()
        refreshHealth(requestAuthorization = false)
        refreshMusic()
    }

    fun updateSampleHealthData(enabled: Boolean) {
        useSampleHealthData = enabled
        prefs.edit().putBoolean(Keys.SAMPLE, enabled).apply()
        enqueue {
            if (!enabled && snapshot?.isSampleData == true) {
                snapshot = null
            }
            refreshHealth(requestAuthorization = false)
            refreshMusic()
        }
    }

    fun finishOnboarding() {
        didFinishOnboarding = true
        prefs.edit().putBoolean(Keys.ONBOARDING, true).apply()
        enqueue {
            refreshHealth(requestAuthorization = true)
            refreshMusic()
        }
    }

    fun skipOnboarding() {
        didFinishOnboarding = true
        prefs.edit().putBoolean(Keys.ONBOARDING, true).apply()
        enqueue {
            refreshHealth(requestAuthorization = false)
            refreshMusic()
        }
    }

    fun send(text: String) = enqueue {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@enqueue
        messages = messages + ChatMessage(role = ChatRole.USER, text = trimmed)
        recompute(resetPlaylists = false)
        messages = messages + ChatMessage(
            role = ChatRole.ASSISTANT,
            text = MoodEngine.assistantReply(assessment),
        )
        persistMessages()
        refreshMusic()
    }

    fun connectSpotify() = enqueue {
        statusMessage = null
        try {
            spotify.connect(clientId)
            isSpotifyConnected = true
            val token = spotify.validAccessToken(clientId)
            spotifyName = spotify.profileName(token)
            refreshMusic()
        } catch (_: SpotifyException.Cancelled) {
            return@enqueue
        } catch (error: Exception) {
            statusMessage = error.message
        }
    }

    fun disconnectSpotify() = enqueue {
        spotify.disconnect()
        isSpotifyConnected = false
        spotifyName = null
        playlists = PlaylistCatalog.fallback(assessment.mood)
        playlistMood = assessment.mood
        statusMessage = "Disconnected from Spotify."
    }

    fun play(playlist: PlaylistSuggestion) = enqueue {
        if (isPlaying) return@enqueue
        isPlaying = true
        try {
            if (!playlist.isSearchLink && isSpotifyConnected) {
                try {
                    val token = spotify.validAccessToken(clientId)
                    spotify.startPlayback(playlist.uri, token)
                    statusMessage = "Playing ${playlist.name} in Spotify."
                    return@enqueue
                } catch (_: SpotifyException.PremiumRequired) {
                    val route = spotify.openExternally(playlist)
                    statusMessage = "Premium is required to start playback from MoodTune. ${openedPhrase(route)}"
                    return@enqueue
                } catch (_: SpotifyException.NoPlaybackDevice) {
                    val route = spotify.openExternally(playlist)
                    statusMessage = "No active Spotify device. ${openedPhrase(route)}"
                    return@enqueue
                } catch (_: Exception) {
                    val route = spotify.openExternally(playlist)
                    statusMessage = openedPhrase(route)
                    return@enqueue
                }
            }
            val route = spotify.openExternally(playlist)
            statusMessage = openedPhrase(route)
        } finally {
            isPlaying = false
        }
    }

    fun refreshHealthAccess() = enqueue {
        refreshHealth(requestAuthorization = true)
        refreshMusic()
    }

    fun onHealthPermissionsResult(@Suppress("UNUSED_PARAMETER") granted: Set<String>) {
        permissionPrompt = null
        val continuation = permissionContinuation
        permissionContinuation = null
        continuation?.resume(Unit)
    }

    private fun enqueue(block: suspend () -> Unit) {
        viewModelScope.launch {
            gate.withLock { block() }
        }
    }

    private suspend fun refreshHealth(requestAuthorization: Boolean) {
        if (requestAuthorization && health.availability() == HealthAccessStatus.Ready) {
            requestHealthPermissions()
        }
        val result = health.loadSnapshot()
        healthStatus = result.status
        val loaded = result.snapshot
        if (loaded != null && loaded.hasAnySignal) {
            snapshot = loaded
        } else if (useSampleHealthData) {
            snapshot = HealthSnapshot.sample
            if (healthStatus == HealthAccessStatus.Unknown) {
                healthStatus = HealthAccessStatus.Empty
            }
        } else {
            snapshot = loaded
        }
        recompute(resetPlaylists = true)
    }

    private suspend fun requestHealthPermissions() {
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            permissionContinuation = continuation
            permissionPrompt = PermissionPrompt(id = ++permissionId, permissions = health.readPermissions)
            continuation.invokeOnCancellation {
                permissionContinuation = null
                permissionPrompt = null
            }
        }
    }

    private suspend fun refreshMusic() {
        val mood = assessment.mood
        if (playlistMood != mood || playlists.isEmpty()) {
            playlists = PlaylistCatalog.fallback(mood)
            playlistMood = mood
        }
        if (!isSpotifyConnected) {
            isLoadingPlaylists = false
            enrichArtwork(mood)
            return
        }
        isLoadingPlaylists = true
        try {
            val token = spotify.validAccessToken(clientId)
            val found = spotify.searchPlaylists(mood.searchQuery, token)
            if (assessment.mood != mood) return
            if (found.isNotEmpty()) {
                playlists = found
                playlistMood = mood
            }
        } catch (error: Exception) {
            if (assessment.mood != mood) return
            statusMessage = error.message
        } finally {
            if (assessment.mood == mood) {
                isLoadingPlaylists = false
            }
        }
        enrichArtwork(mood)
    }

    private suspend fun enrichArtwork(mood: Mood) {
        playlists.forEachIndexed { index, playlist ->
            if (assessment.mood != mood) return
            if (playlist.imageUrl != null || playlist.isSearchLink) return@forEachIndexed
            val imageUrl = spotify.thumbnail(playlist.webUrl) ?: return@forEachIndexed
            if (assessment.mood != mood) return
            playlists = playlists.mapIndexed { itemIndex, item ->
                if (itemIndex == index && item.id == playlist.id) item.copy(imageUrl = imageUrl) else item
            }
        }
    }

    private suspend fun completePendingLogin() {
        val pending = spotify.takePendingRedirect() ?: return
        try {
            spotify.completeLogin(pending)
            isSpotifyConnected = true
            val token = spotify.validAccessToken(clientId)
            spotifyName = spotify.profileName(token)
        } catch (_: SpotifyException.Cancelled) {
            return
        } catch (error: Exception) {
            isSpotifyConnected = spotify.hasStoredSession()
            statusMessage = error.message
        }
    }

    private suspend fun restoreSpotifyProfile() {
        if (!isSpotifyConnected) return
        try {
            val token = spotify.validAccessToken(clientId)
            spotifyName = spotify.profileName(token)
        } catch (_: Exception) {
            isSpotifyConnected = spotify.hasStoredSession()
        }
    }

    private fun recompute(resetPlaylists: Boolean) {
        val previous = assessment.mood
        assessment = MoodEngine.assess(snapshot, messages)
        if (resetPlaylists || previous != assessment.mood) {
            playlists = PlaylistCatalog.fallback(assessment.mood)
            playlistMood = assessment.mood
        }
    }

    private fun openedPhrase(route: PlaybackRoute): String = when (route) {
        PlaybackRoute.REMOTE -> "Playing in Spotify."
        PlaybackRoute.SPOTIFY_APP -> "Opened in the Spotify app."
        PlaybackRoute.WEB -> "Opened Spotify on the web."
    }

    private fun loadMessages(): List<ChatMessage> {
        val raw = prefs.getString(Keys.MESSAGES, null) ?: return defaultMessages()
        return try {
            val array = JSONArray(raw)
            val stored = buildList {
                for (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    val role = if (obj.optString("role") == "user") ChatRole.USER else ChatRole.ASSISTANT
                    add(
                        ChatMessage(
                            id = obj.getString("id"),
                            role = role,
                            text = obj.getString("text"),
                            dateMillis = obj.getLong("date"),
                        ),
                    )
                }
            }
            stored.ifEmpty { defaultMessages() }
        } catch (_: Exception) {
            defaultMessages()
        }
    }

    private fun persistMessages() {
        val recent = messages.takeLast(40)
        messages = recent
        val array = JSONArray()
        recent.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("role", message.role.name.lowercase())
                    .put("text", message.text)
                    .put("date", message.dateMillis),
            )
        }
        prefs.edit().putString(Keys.MESSAGES, array.toString()).apply()
    }

    private fun defaultMessages() = listOf(
        ChatMessage(
            role = ChatRole.ASSISTANT,
            text = "Tell me how you feel. A sentence is enough — tired, wired, calm, stuck — and I’ll pick the music.",
        ),
    )

    private object Keys {
        const val CLIENT_ID = "spotifyClientID"
        const val SAMPLE = "useSampleHealthData"
        const val ONBOARDING = "didFinishOnboarding"
        const val MESSAGES = "chatMessages"
    }

    companion object {
        private const val PREFS = "moodtune"
    }
}
