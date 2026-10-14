package com.moodtune.app.model

import java.util.UUID

enum class Mood {
    JOYFUL,
    CALM,
    FOCUSED,
    ENERGETIC,
    MELANCHOLIC,
    STRESSED,
    TIRED;

    val title: String
        get() = when (this) {
            JOYFUL -> "Joyful"
            CALM -> "Calm"
            FOCUSED -> "Focused"
            ENERGETIC -> "Energetic"
            MELANCHOLIC -> "Melancholic"
            STRESSED -> "Stressed"
            TIRED -> "Tired"
        }

    val searchQuery: String
        get() = when (this) {
            JOYFUL -> "happy hits"
            CALM -> "peaceful piano"
            FOCUSED -> "deep focus"
            ENERGETIC -> "workout beast mode"
            MELANCHOLIC -> "sad songs"
            STRESSED -> "stress relief"
            TIRED -> "sleep"
        }

    val playButtonTitle: String
        get() = when (this) {
            STRESSED -> "Play soothing music"
            else -> "Play ${title.lowercase()} music"
        }

    val playlistPitch: String
        get() = when (this) {
            JOYFUL -> "something upbeat"
            CALM -> "a quieter playlist"
            FOCUSED -> "music for concentration"
            ENERGETIC -> "a high-energy mix"
            MELANCHOLIC -> "something slower"
            STRESSED -> "a low-pressure playlist"
            TIRED -> "a wind-down playlist"
        }
}

data class HealthSnapshot(
    val stepsToday: Double? = null,
    val activeCalories: Double? = null,
    val latestHeartRate: Double? = null,
    val heartRateVariabilityMS: Double? = null,
    val sleepHours: Double? = null,
    val mindfulMinutes: Double? = null,
    val recentWorkoutMinutes: Double? = null,
    val hoursSinceWorkout: Double? = null,
    val isSampleData: Boolean = false,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
) {
    val hasAnySignal: Boolean
        get() = stepsToday != null ||
            activeCalories != null ||
            latestHeartRate != null ||
            heartRateVariabilityMS != null ||
            sleepHours != null ||
            mindfulMinutes != null ||
            recentWorkoutMinutes != null

    companion object {
        val sample = HealthSnapshot(
            stepsToday = 3_200.0,
            activeCalories = 180.0,
            latestHeartRate = 88.0,
            heartRateVariabilityMS = 28.0,
            sleepHours = 5.2,
            mindfulMinutes = 0.0,
            recentWorkoutMinutes = null,
            hoursSinceWorkout = null,
            isSampleData = true,
        )
    }
}

data class MoodAssessment(
    val mood: Mood,
    val confidence: Double,
    val reasons: List<String>,
    val summary: String,
    val usedHealth: Boolean,
    val usedChat: Boolean,
    val latestMessageHadSignal: Boolean,
) {
    companion object {
        val waiting = MoodAssessment(
            mood = Mood.CALM,
            confidence = 0.2,
            reasons = emptyList(),
            summary = "Tell me how you feel, or allow Health access and I’ll read the day.",
            usedHealth = false,
            usedChat = false,
            latestMessageHadSignal = false,
        )
    }
}

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val dateMillis: Long = System.currentTimeMillis(),
)

data class PlaylistSuggestion(
    val id: String,
    val name: String,
    val detail: String,
    val uri: String,
    val webUrl: String,
    val imageUrl: String?,
    val trackCount: Int?,
    val isSearchLink: Boolean,
) {
    val spotifyAppUrl: String
        get() = if (isSearchLink) {
            val query = urlEncode(name)
            "spotify:search:$query"
        } else {
            uri
        }
}

enum class PlaybackRoute { REMOTE, SPOTIFY_APP, WEB }

sealed class HealthAccessStatus {
    data object Unknown : HealthAccessStatus()
    data object Unavailable : HealthAccessStatus()
    data object Ready : HealthAccessStatus()
    data object Empty : HealthAccessStatus()
    data class Failed(val message: String) : HealthAccessStatus()
}

data class HealthLoadResult(
    val status: HealthAccessStatus,
    val snapshot: HealthSnapshot?,
)
