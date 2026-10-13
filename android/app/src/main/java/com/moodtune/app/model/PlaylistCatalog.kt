package com.moodtune.app.model

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object PlaylistCatalog {
    fun fallback(mood: Mood): List<PlaylistSuggestion> {
        val items = mutableListOf<PlaylistSuggestion>()
        editorial[mood]?.let { items.add(it) }
        items.add(searchLink(mood))
        return items
    }

    fun searchLink(mood: Mood): PlaylistSuggestion {
        val encoded = URLEncoder.encode(mood.searchQuery, StandardCharsets.UTF_8).replace("+", "%20")
        return PlaylistSuggestion(
            id = "search-${mood.name.lowercase()}",
            name = mood.searchQuery.capitalizedWords(),
            detail = "Search in Spotify",
            uri = "spotify:search:$encoded",
            webUrl = "https://open.spotify.com/search/$encoded",
            imageUrl = null,
            trackCount = null,
            isSearchLink = true,
        )
    }

    private val editorial: Map<Mood, PlaylistSuggestion> = mapOf(
        Mood.JOYFUL to playlist("37i9dQZF1DX3rxVfibe1L0", "Mood Booster", "Spotify editorial"),
        Mood.CALM to playlist("37i9dQZF1DX4sWSpwq3LiO", "Peaceful Piano", "Spotify editorial"),
        Mood.FOCUSED to playlist("37i9dQZF1DWZeKCadgRdKQ", "Deep Focus", "Spotify editorial"),
        Mood.ENERGETIC to playlist("37i9dQZF1DX76Wlfdnj7AP", "Beast Mode", "Spotify editorial"),
        Mood.STRESSED to playlist("37i9dQZF1DX4WYpdgoIcn6", "Chill Hits", "Spotify editorial"),
        Mood.TIRED to playlist("37i9dQZF1DWZd79rJ6a7lp", "Sleep", "Spotify editorial"),
    )

    private fun playlist(id: String, name: String, detail: String) = PlaylistSuggestion(
        id = id,
        name = name,
        detail = detail,
        uri = "spotify:playlist:$id",
        webUrl = "https://open.spotify.com/playlist/$id",
        imageUrl = null,
        trackCount = null,
        isSearchLink = false,
    )
}

private fun String.capitalizedWords(): String =
    split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
        }
    }
