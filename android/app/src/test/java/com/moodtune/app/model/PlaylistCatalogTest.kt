package com.moodtune.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCatalogTest {
    @Test
    fun searchLinksPercentEncodeSpaces() {
        val link = PlaylistCatalog.searchLink(Mood.CALM)
        assertEquals("spotify:search:peaceful%20piano", link.uri)
        assertEquals("https://open.spotify.com/search/peaceful%20piano", link.webUrl)
        assertEquals("spotify:search:Peaceful%20Piano", link.spotifyAppUrl)
    }

    @Test
    fun everyMoodHasAStartupPlaylist() {
        Mood.entries.forEach { mood ->
            val items = PlaylistCatalog.fallback(mood)
            assertTrue(items.isNotEmpty())
            assertTrue(items.any { it.isSearchLink && !it.uri.contains("+") })
        }
    }
}
