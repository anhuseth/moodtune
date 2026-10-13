package com.moodtune.app.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.moodtune.app.model.PlaybackRoute
import com.moodtune.app.model.PlaylistSuggestion
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class SpotifyException(message: String) : Exception(message) {
    class MissingClientId : SpotifyException("Add your Spotify Client ID in settings.")
    class Cancelled : SpotifyException("Spotify sign-in was cancelled.")
    class AuthFailed(message: String) : SpotifyException(message)
    class Http(val code: Int, message: String) : SpotifyException(message)
    class PremiumRequired : SpotifyException("Spotify Premium is required to start playback remotely.")
    class NoPlaybackDevice : SpotifyException("No active Spotify device.")
}

object SpotifyAuthRelay {
    interface Listener {
        fun onRedirect(uri: Uri)
        fun onCancel()
    }

    @Volatile
    var listener: Listener? = null

    fun onRedirect(uri: Uri) {
        listener?.onRedirect(uri)
    }

    fun onCancel() {
        listener?.onCancel()
    }
}

class SpotifyClient(context: Context) {
    private val appContext = context.applicationContext
    private val securePrefs = createSecurePrefs(appContext)
    private var authContinuation: CancellableContinuation<Uri>? = null

    @Volatile
    var isAwaitingAuth: Boolean = false

    @Volatile
    private var pendingRedirect: Uri? = null

    fun hasStoredSession(): Boolean = loadTokens() != null

    fun onRedirect(uri: Uri) {
        val continuation = authContinuation
        if (continuation != null && continuation.isActive) {
            authContinuation = null
            isAwaitingAuth = false
            continuation.resume(uri)
        } else {
            pendingRedirect = uri
        }
    }

    fun onCancel() {
        if (!isAwaitingAuth) return
        isAwaitingAuth = false
        val continuation = authContinuation
        authContinuation = null
        clearVerifier()
        continuation?.resumeWithException(SpotifyException.Cancelled())
    }

    fun takePendingRedirect(): Uri? {
        val uri = pendingRedirect
        pendingRedirect = null
        return uri
    }

    fun disconnect() {
        securePrefs.edit().remove(KEY_TOKENS).apply()
        clearVerifier()
    }

    suspend fun connect(clientId: String) {
        val trimmed = clientId.trim()
        if (trimmed.isEmpty()) throw SpotifyException.MissingClientId()
        val verifier = randomVerifier()
        securePrefs.edit()
            .putString(KEY_VERIFIER, verifier)
            .putString(KEY_CLIENT, trimmed)
            .apply()
        val url = authorizeUrl(trimmed, challenge(verifier))
        isAwaitingAuth = true
        val callback = try {
            awaitRedirect(url)
        } finally {
            isAwaitingAuth = false
        }
        completeLogin(callback)
    }

    suspend fun completeLogin(callback: Uri) {
        val error = callback.getQueryParameter("error")
        if (!error.isNullOrEmpty()) {
            clearVerifier()
            throw SpotifyException.AuthFailed(error)
        }
        val code = callback.getQueryParameter("code")
        if (code.isNullOrEmpty()) {
            clearVerifier()
            throw SpotifyException.AuthFailed("Spotify did not return a login code.")
        }
        val verifier = securePrefs.getString(KEY_VERIFIER, null)
        val clientId = securePrefs.getString(KEY_CLIENT, null)?.trim().orEmpty()
        if (verifier.isNullOrEmpty() || clientId.isEmpty()) {
            throw SpotifyException.AuthFailed("Spotify login expired. Connect again.")
        }
        val tokens = withContext(Dispatchers.IO) {
            exchange(
                form = mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to REDIRECT_URI,
                    "client_id" to clientId,
                    "code_verifier" to verifier,
                ),
            )
        }
        saveTokens(tokens)
        clearVerifier()
    }

    suspend fun validAccessToken(clientId: String): String = withContext(Dispatchers.IO) {
        val existing = loadTokens() ?: throw SpotifyException.AuthFailed("Connect Spotify first.")
        if (existing.expiresAtMillis - System.currentTimeMillis() > 60_000) {
            return@withContext existing.accessToken
        }
        val refresh = existing.refreshToken
            ?: throw SpotifyException.AuthFailed("Spotify session expired. Connect again.")
        val trimmed = clientId.trim()
        if (trimmed.isEmpty()) throw SpotifyException.MissingClientId()
        val refreshed = exchange(
            form = mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refresh,
                "client_id" to trimmed,
            ),
            previousRefreshToken = refresh,
        )
        saveTokens(refreshed)
        refreshed.accessToken
    }

    suspend fun profileName(token: String): String = withContext(Dispatchers.IO) {
        val data = request("https://api.spotify.com/v1/me", "GET", token, null)
        val profile = JSONObject(data.toString(Charsets.UTF_8))
        profile.optString("display_name").ifEmpty { profile.optString("id") }
    }

    suspend fun searchPlaylists(query: String, token: String): List<PlaylistSuggestion> =
        withContext(Dispatchers.IO) {
            val url = "https://api.spotify.com/v1/search?q=" +
                encode(query) + "&type=playlist&limit=8"
            val data = request(url, "GET", token, null)
            val items = JSONObject(data.toString(Charsets.UTF_8))
                .optJSONObject("playlists")
                ?.optJSONArray("items")
                ?: JSONArray()
            buildList {
                for (index in 0 until items.length()) {
                    if (items.isNull(index)) continue
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    val uri = item.optString("uri")
                    if (id.isEmpty() || name.isEmpty() || uri.isEmpty()) continue
                    val images = item.optJSONArray("images")
                    val imageUrl = images?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() }
                    val owner = item.optJSONObject("owner")?.optString("display_name").orEmpty()
                    val tracks = item.optJSONObject("tracks")
                    val trackCount = if (tracks != null && tracks.has("total")) tracks.optInt("total") else null
                    add(
                        PlaylistSuggestion(
                            id = id,
                            name = name,
                            detail = owner.ifEmpty { "Spotify" },
                            uri = uri,
                            webUrl = "https://open.spotify.com/playlist/$id",
                            imageUrl = imageUrl,
                            trackCount = trackCount,
                            isSearchLink = false,
                        ),
                    )
                }
            }
        }

    suspend fun startPlayback(playlistUri: String, token: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("context_uri", playlistUri).toString().toByteArray()
        try {
            request(
                url = "https://api.spotify.com/v1/me/player/play",
                method = "PUT",
                token = token,
                body = body,
                acceptEmpty = true,
            )
        } catch (error: SpotifyException.NoPlaybackDevice) {
            val deviceId = firstDeviceId(token)
            request(
                url = "https://api.spotify.com/v1/me/player/play?device_id=${encode(deviceId)}",
                method = "PUT",
                token = token,
                body = body,
                acceptEmpty = true,
            )
        }
    }

    suspend fun thumbnail(playlistUrl: String): String? = withContext(Dispatchers.IO) {
        val url = "https://open.spotify.com/oembed?url=${encode(playlistUrl)}"
        try {
            val result = http(url, "GET", emptyMap(), null)
            if (result.status != 200) return@withContext null
            JSONObject(result.body.toString(Charsets.UTF_8))
                .optString("thumbnail_url")
                .takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun openExternally(playlist: PlaylistSuggestion): PlaybackRoute {
        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(playlist.spotifyAppUrl)).apply {
            setPackage("com.spotify.music")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val packageManager = appContext.packageManager
        if (spotifyIntent.resolveActivity(packageManager) != null) {
            appContext.startActivity(spotifyIntent)
            return PlaybackRoute.SPOTIFY_APP
        }
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(playlist.webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(webIntent)
        return PlaybackRoute.WEB
    }

    private suspend fun awaitRedirect(url: Uri): Uri = suspendCancellableCoroutine { continuation ->
        authContinuation = continuation
        continuation.invokeOnCancellation {
            isAwaitingAuth = false
            authContinuation = null
            clearVerifier()
        }
        val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            tabs.launchUrl(appContext, url)
        } catch (_: Exception) {
            authContinuation = null
            isAwaitingAuth = false
            if (continuation.isActive) {
                continuation.resumeWithException(SpotifyException.AuthFailed("Could not open Spotify login."))
            }
        }
    }

    private fun firstDeviceId(token: String): String {
        val data = request("https://api.spotify.com/v1/me/player/devices", "GET", token, null)
        val devices = JSONObject(data.toString(Charsets.UTF_8)).optJSONArray("devices") ?: JSONArray()
        var fallback: String? = null
        for (index in 0 until devices.length()) {
            val device = devices.optJSONObject(index) ?: continue
            val id = device.optString("id")
            if (id.isEmpty()) continue
            if (fallback == null) fallback = id
            if (device.optBoolean("is_active")) return id
        }
        return fallback ?: throw SpotifyException.NoPlaybackDevice()
    }

    private fun exchange(form: Map<String, String>, previousRefreshToken: String? = null): SpotifyTokens {
        val body = form.entries.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
            .toByteArray()
        val result = http(
            url = "https://accounts.spotify.com/api/token",
            method = "POST",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = body,
        )
        if (result.status !in 200..299) {
            val message = result.body.toString(Charsets.UTF_8).ifEmpty { "Spotify login failed." }
            throw SpotifyException.AuthFailed(message)
        }
        val json = JSONObject(result.body.toString(Charsets.UTF_8))
        val refresh = json.optString("refresh_token").takeIf { it.isNotEmpty() } ?: previousRefreshToken
        return SpotifyTokens(
            accessToken = json.getString("access_token"),
            refreshToken = refresh,
            expiresAtMillis = System.currentTimeMillis() + json.getLong("expires_in") * 1000,
        )
    }

    private fun request(
        url: String,
        method: String,
        token: String,
        body: ByteArray?,
        acceptEmpty: Boolean = false,
    ): ByteArray {
        val result = http(
            url = url,
            method = method,
            headers = buildMap {
                put("Authorization", "Bearer $token")
                if (body != null) put("Content-Type", "application/json")
            },
            body = body,
        )
        if (result.status == 204 || (acceptEmpty && result.body.isEmpty() && result.status in 200..299)) {
            return result.body
        }
        if (result.status !in 200..299) {
            val raw = result.body.toString(Charsets.UTF_8)
            if (result.status == 404) throw SpotifyException.NoPlaybackDevice()
            if (result.status == 403) throw SpotifyException.PremiumRequired()
            throw SpotifyException.Http(result.status, raw.ifEmpty { "Spotify request failed." })
        }
        return result.body
    }

    private fun http(url: String, method: String, headers: Map<String, String>, body: ByteArray?): HttpResult {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 20_000
            doInput = true
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            return HttpResult(status, bytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun authorizeUrl(clientId: String, codeChallenge: String): Uri =
        Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter(
                "scope",
                "user-read-private user-read-email user-modify-playback-state user-read-playback-state playlist-read-private",
            )
            .build()

    private fun loadTokens(): SpotifyTokens? {
        val raw = securePrefs.getString(KEY_TOKENS, null) ?: return null
        return try {
            val json = JSONObject(raw)
            SpotifyTokens(
                accessToken = json.getString("accessToken"),
                refreshToken = json.optString("refreshToken").ifEmpty { null },
                expiresAtMillis = json.getLong("expiresAt"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun saveTokens(tokens: SpotifyTokens) {
        val json = JSONObject()
            .put("accessToken", tokens.accessToken)
            .put("refreshToken", tokens.refreshToken)
            .put("expiresAt", tokens.expiresAtMillis)
        securePrefs.edit().putString(KEY_TOKENS, json.toString()).apply()
    }

    private fun clearVerifier() {
        securePrefs.edit().remove(KEY_VERIFIER).remove(KEY_CLIENT).apply()
    }

    private data class SpotifyTokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long,
    )

    private data class HttpResult(val status: Int, val body: ByteArray)

    companion object {
        const val REDIRECT_URI = "moodtune://callback"
        private const val KEY_TOKENS = "spotify.tokens"
        private const val KEY_VERIFIER = "spotify.verifier"
        private const val KEY_CLIENT = "spotify.client"

        private fun randomVerifier(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        private fun challenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

        private fun createSecurePrefs(context: Context): SharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "moodtune_spotify",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            context.getSharedPreferences("moodtune_spotify_fallback", Context.MODE_PRIVATE)
        }
    }
}
