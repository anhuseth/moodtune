package com.moodtune.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodEngineTest {
    private fun user(text: String) = ChatMessage(role = ChatRole.USER, text = text)

    @Test
    fun sampleHealthReadsAsStressed() {
        val sample = MoodEngine.assess(HealthSnapshot.sample, emptyList())
        assertEquals(Mood.STRESSED, sample.mood)
        assertTrue(sample.usedHealth)
        assertTrue(sample.summary.lowercase().contains("sample"))
    }

    @Test
    fun soHappyReadsAsJoyful() {
        val happy = MoodEngine.assess(null, listOf(user("I am so happy")))
        assertEquals(Mood.JOYFUL, happy.mood)
    }

    @Test
    fun notHappyReadsAsMelancholic() {
        val negated = MoodEngine.assess(null, listOf(user("I am not happy")))
        assertEquals(Mood.MELANCHOLIC, negated.mood)
    }

    @Test
    fun cantSleepAndAnxiousReadsAsStressed() {
        val anxious = MoodEngine.assess(null, listOf(user("can't sleep and anxious")))
        assertEquals(Mood.STRESSED, anxious.mood)
    }

    @Test
    fun chatOverridesSampleStress() {
        val override = MoodEngine.assess(
            HealthSnapshot.sample,
            listOf(user("I feel peaceful and calm")),
        )
        assertEquals(Mood.CALM, override.mood)
    }

    @Test
    fun needToFocusReadsAsFocused() {
        val focus = MoodEngine.assess(null, listOf(user("I need to focus")))
        assertEquals(Mood.FOCUSED, focus.mood)
    }

    @Test
    fun noSignalStaysUndecided() {
        val empty = MoodEngine.assess(null, emptyList())
        assertEquals(Mood.CALM, empty.mood)
        assertTrue(empty.confidence < 0.3)
    }

    @Test
    fun vagueChatKeepsTheHealthMood() {
        val vague = MoodEngine.assess(HealthSnapshot.sample, listOf(user("hello there")))
        assertFalse(vague.latestMessageHadSignal)
        assertEquals(Mood.STRESSED, vague.mood)
    }

    @Test
    fun editorialPlaylistsMatchTheCatalog() {
        val calm = PlaylistCatalog.fallback(Mood.CALM)
        assertEquals("37i9dQZF1DX4sWSpwq3LiO", calm.first().id)
        assertEquals("Peaceful Piano", calm.first().name)
        val melancholic = PlaylistCatalog.fallback(Mood.MELANCHOLIC)
        assertEquals(1, melancholic.size)
        assertTrue(melancholic.single().isSearchLink)
    }
}
