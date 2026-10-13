package com.moodtune.app.model

object MoodEngine {
    fun assess(health: HealthSnapshot?, messages: List<ChatMessage>): MoodAssessment {
        val healthResult = scoreHealth(health)
        val userMessages = messages.filter { it.role == ChatRole.USER }
        val chatResult = scoreChat(userMessages)
        val lastSignal = userMessages.lastOrNull()?.let { scoreText(it.text).maxScore > 0.0 } ?: false

        val healthMax = healthResult.scores.values.maxOrNull() ?: 0.0
        val chatMax = chatResult.scores.values.maxOrNull() ?: 0.0
        val hasHealth = health?.hasAnySignal == true && healthMax > 0.0
        val hasChat = chatMax > 0.0

        var healthWeight = 0.0
        var chatWeight = 0.0
        if (hasHealth && hasChat) {
            if (chatMax >= 0.8) {
                healthWeight = 0.28
                chatWeight = 0.72
            } else {
                healthWeight = 0.55
                chatWeight = 0.45
            }
            if (health?.isSampleData == true) {
                healthWeight *= 0.45
                val total = healthWeight + chatWeight
                healthWeight /= total
                chatWeight /= total
            }
        } else if (hasChat) {
            chatWeight = 1.0
        } else if (hasHealth) {
            healthWeight = 1.0
        }

        val combined = zeroScores()
        for (mood in Mood.entries) {
            combined[mood] = (healthResult.scores[mood] ?: 0.0) * healthWeight +
                (chatResult.scores[mood] ?: 0.0) * chatWeight
        }

        val ranked = Mood.entries.sortedWith(
            compareByDescending<Mood> { combined[it] ?: 0.0 }.thenBy { it.ordinal },
        )
        val topValue = ranked.firstOrNull()?.let { combined[it] ?: 0.0 } ?: 0.0
        if (topValue <= 0.0 || ranked.isEmpty()) {
            return MoodAssessment.waiting
        }
        val winner = ranked.first()
        val second = ranked.getOrNull(1)?.let { combined[it] ?: 0.0 } ?: 0.0
        val margin = topValue - second
        var confidence = 0.28 + minOf(0.45, margin * 0.35) + minOf(0.22, topValue * 0.12)
        if (hasChat) confidence += 0.08
        if (hasHealth && health?.isSampleData == false) confidence += 0.08
        confidence = minOf(0.94, confidence)

        val reasons = healthResult.reasons
            .filter { it.mood == winner }
            .map { it.text }
            .toMutableList()
        if (hasChat && (chatResult.scores[winner] ?: 0.0) > 0.0) {
            reasons.add(0, "You described feeling ${winner.title.lowercase()}.")
        }

        val summary = when {
            hasHealth && hasChat -> "Health data and what you said both lean ${winner.title.lowercase()}."
            hasChat -> "Mostly from what you just said."
            health?.isSampleData == true ->
                "A sample day reads as ${winner.title.lowercase()}. Your own Health data will replace this."
            else -> "Mostly from your health data."
        }

        return MoodAssessment(
            mood = winner,
            confidence = confidence,
            reasons = reasons.take(3),
            summary = summary,
            usedHealth = hasHealth,
            usedChat = hasChat,
            latestMessageHadSignal = lastSignal,
        )
    }

    fun assistantReply(assessment: MoodAssessment): String {
        val pitch = "I lined up ${assessment.mood.playlistPitch}."
        if (!assessment.latestMessageHadSignal) {
            if (assessment.usedHealth) {
                return "I couldn't read a clear mood in that. Your health data still points to ${assessment.mood.title.lowercase()}. $pitch"
            }
            return "Say it a bit more plainly — happy, calm, anxious, tired, or focused. I'll match the music."
        }

        val lead = when (assessment.mood) {
            Mood.JOYFUL -> "That sounds bright."
            Mood.CALM -> "That sounds settled."
            Mood.FOCUSED -> "You're in a heads-down mood."
            Mood.ENERGETIC -> "You've got some charge."
            Mood.MELANCHOLIC -> "That sounds heavy."
            Mood.STRESSED -> "That sounds tense."
            Mood.TIRED -> "That sounds worn out."
        }
        val reason = assessment.reasons.firstOrNull {
            !it.startsWith("You described") && !it.startsWith("Health figures")
        }
        return if (reason != null) "$lead $reason $pitch" else "$lead $pitch"
    }

    private data class TextScore(val scores: Map<Mood, Double>) {
        val maxScore: Double get() = scores.values.maxOrNull() ?: 0.0
    }

    private data class Reason(val mood: Mood, val text: String)

    private data class HealthScore(
        val scores: Map<Mood, Double>,
        val reasons: List<Reason>,
    )

    private fun scoreHealth(snapshot: HealthSnapshot?): HealthScore {
        val scores = zeroScores()
        val reasons = mutableListOf<Reason>()
        if (snapshot == null || !snapshot.hasAnySignal) {
            return HealthScore(scores, reasons)
        }

        fun note(mood: Mood, amount: Double, text: String) {
            scores[mood] = (scores[mood] ?: 0.0) + amount
            if (reasons.none { it.mood == mood && it.text == text }) {
                reasons.add(Reason(mood, text))
            }
        }

        snapshot.sleepHours?.let { sleep ->
            when {
                sleep < 5.0 -> {
                    note(Mood.TIRED, 0.9, "Sleep was under 5 hours.")
                    note(Mood.STRESSED, 0.4, "Sleep was under 5 hours.")
                    note(Mood.MELANCHOLIC, 0.3, "Sleep was under 5 hours.")
                }
                sleep < 6.5 -> {
                    note(Mood.TIRED, 0.45, "Sleep was short.")
                    note(Mood.STRESSED, 0.2, "Sleep was short.")
                }
                sleep >= 7.5 && sleep <= 9.0 -> {
                    note(Mood.CALM, 0.55, "Sleep landed in a solid range.")
                    note(Mood.JOYFUL, 0.35, "Sleep landed in a solid range.")
                }
                sleep > 9.0 -> {
                    note(Mood.TIRED, 0.25, "Sleep ran long.")
                    note(Mood.CALM, 0.2, "Sleep ran long.")
                }
                else -> note(Mood.CALM, 0.2, "Sleep was about average.")
            }
        }

        snapshot.heartRateVariabilityMS?.let { hrv ->
            when {
                hrv < 20.0 -> {
                    note(Mood.STRESSED, 0.8, "Heart-rate variability is low.")
                    note(Mood.MELANCHOLIC, 0.2, "Heart-rate variability is low.")
                }
                hrv < 35.0 -> note(Mood.STRESSED, 0.4, "Heart-rate variability is on the low side.")
                hrv >= 60.0 -> {
                    note(Mood.CALM, 0.6, "Heart-rate variability looks high.")
                    note(Mood.JOYFUL, 0.3, "Heart-rate variability looks high.")
                }
                else -> {
                    note(Mood.CALM, 0.35, "Heart-rate variability looks steady.")
                    note(Mood.FOCUSED, 0.2, "Heart-rate variability looks steady.")
                }
            }
        }

        val recentWorkout = (snapshot.hoursSinceWorkout ?: 99.0) < 3.0 &&
            (snapshot.recentWorkoutMinutes ?: 0.0) > 10.0
        snapshot.latestHeartRate?.let { heart ->
            when {
                recentWorkout && heart > 90.0 -> note(Mood.ENERGETIC, 0.7, "Heart rate is up after a workout.")
                heart > 100.0 -> note(Mood.STRESSED, 0.6, "Heart rate is elevated.")
                heart > 85.0 -> {
                    note(Mood.ENERGETIC, 0.25, "Heart rate is a little elevated.")
                    note(Mood.STRESSED, 0.15, "Heart rate is a little elevated.")
                }
                heart < 60.0 -> {
                    note(Mood.CALM, 0.3, "Heart rate is low and steady.")
                    note(Mood.TIRED, 0.2, "Heart rate is low and steady.")
                }
                else -> note(Mood.CALM, 0.15, "Heart rate is in a resting range.")
            }
        }

        snapshot.stepsToday?.let { steps ->
            when {
                steps >= 10_000.0 -> {
                    note(Mood.ENERGETIC, 0.55, "Step count is high today.")
                    note(Mood.JOYFUL, 0.35, "Step count is high today.")
                }
                steps >= 6_000.0 -> {
                    note(Mood.JOYFUL, 0.25, "You've been moving around.")
                    note(Mood.ENERGETIC, 0.2, "You've been moving around.")
                }
                steps < 2_000.0 -> {
                    note(Mood.TIRED, 0.35, "Activity is very low today.")
                    note(Mood.MELANCHOLIC, 0.25, "Activity is very low today.")
                }
            }
        }

        snapshot.activeCalories?.let { calories ->
            if (calories > 400.0) note(Mood.ENERGETIC, 0.3, "Active energy is high.")
        }

        snapshot.mindfulMinutes?.let { mindful ->
            if (mindful >= 5.0) {
                note(Mood.CALM, 0.7, "There's a mindfulness session today.")
                scores[Mood.STRESSED] = maxOf(0.0, (scores[Mood.STRESSED] ?: 0.0) - 0.3)
            }
        }

        val minutes = snapshot.recentWorkoutMinutes
        if (minutes != null && minutes > 20.0 && (snapshot.hoursSinceWorkout ?: 99.0) < 8.0) {
            note(Mood.ENERGETIC, 0.8, "You worked out recently.")
            note(Mood.JOYFUL, 0.2, "You worked out recently.")
        }

        return HealthScore(scores, reasons)
    }

    private fun scoreChat(messages: List<ChatMessage>): TextScore {
        val scores = zeroScores()
        val recent = messages.takeLast(6)
        for ((index, message) in recent.withIndex()) {
            val distanceFromEnd = recent.size - 1 - index
            val weight = when (distanceFromEnd) {
                0 -> 1.0
                1 -> 0.45
                else -> 0.2
            }
            val textScore = scoreText(message.text)
            for (mood in Mood.entries) {
                scores[mood] = (scores[mood] ?: 0.0) + (textScore.scores[mood] ?: 0.0) * weight
            }
        }
        return TextScore(scores)
    }

    private fun scoreText(text: String): TextScore {
        val scores = zeroScores()
        var working = text.lowercase()
        val phrases = phraseLexicon.sortedByDescending { it.phrase.length }
        for (entry in phrases) {
            if (!working.contains(entry.phrase)) continue
            for ((mood, amount) in entry.scores) {
                scores[mood] = (scores[mood] ?: 0.0) + amount
            }
            working = working.replace(entry.phrase, " ")
        }

        working = working.replace("n't", " not ")
        val tokens = working.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }

        var negationWindow = 0
        for (token in tokens) {
            if (token in negationWords) {
                negationWindow = 3
                continue
            }
            val weights = wordLexicon[token]
            if (weights == null) {
                if (negationWindow > 0) negationWindow -= 1
                continue
            }
            if (negationWindow > 0) {
                for ((mood, amount) in weights) {
                    val flipped = negated(mood)
                    scores[flipped] = (scores[flipped] ?: 0.0) + amount
                }
                negationWindow = 0
            } else {
                for ((mood, amount) in weights) {
                    scores[mood] = (scores[mood] ?: 0.0) + amount
                }
            }
        }
        return TextScore(scores)
    }

    private fun negated(mood: Mood): Mood = when (mood) {
        Mood.JOYFUL -> Mood.MELANCHOLIC
        Mood.CALM -> Mood.STRESSED
        Mood.FOCUSED -> Mood.STRESSED
        Mood.ENERGETIC -> Mood.TIRED
        Mood.MELANCHOLIC -> Mood.JOYFUL
        Mood.STRESSED -> Mood.CALM
        Mood.TIRED -> Mood.ENERGETIC
    }

    private val negationWords = setOf("not", "no", "never", "hardly", "without")

    private data class Phrase(val phrase: String, val scores: Map<Mood, Double>)

    private val phraseLexicon = listOf(
        Phrase("burnt out", mapOf(Mood.TIRED to 1.0, Mood.STRESSED to 0.7)),
        Phrase("burned out", mapOf(Mood.TIRED to 1.0, Mood.STRESSED to 0.7)),
        Phrase("can't sleep", mapOf(Mood.TIRED to 1.0, Mood.STRESSED to 0.45)),
        Phrase("cannot sleep", mapOf(Mood.TIRED to 1.0, Mood.STRESSED to 0.45)),
        Phrase("need to focus", mapOf(Mood.FOCUSED to 1.0)),
        Phrase("deep work", mapOf(Mood.FOCUSED to 0.9)),
        Phrase("feeling great", mapOf(Mood.JOYFUL to 1.0)),
        Phrase("feel great", mapOf(Mood.JOYFUL to 1.0)),
        Phrase("so happy", mapOf(Mood.JOYFUL to 1.0)),
        Phrase("feeling good", mapOf(Mood.JOYFUL to 0.8)),
        Phrase("worked out", mapOf(Mood.ENERGETIC to 1.0)),
        Phrase("at the gym", mapOf(Mood.ENERGETIC to 0.9)),
        Phrase("heart broken", mapOf(Mood.MELANCHOLIC to 1.0)),
        Phrase("feel down", mapOf(Mood.MELANCHOLIC to 0.9)),
    )

    private val wordLexicon: Map<String, Map<Mood, Double>> = mapOf(
        "happy" to mapOf(Mood.JOYFUL to 1.0),
        "joy" to mapOf(Mood.JOYFUL to 1.0),
        "joyful" to mapOf(Mood.JOYFUL to 1.0),
        "excited" to mapOf(Mood.JOYFUL to 1.0),
        "great" to mapOf(Mood.JOYFUL to 0.8),
        "amazing" to mapOf(Mood.JOYFUL to 1.0),
        "wonderful" to mapOf(Mood.JOYFUL to 1.0),
        "awesome" to mapOf(Mood.JOYFUL to 0.9),
        "good" to mapOf(Mood.JOYFUL to 0.7),
        "glad" to mapOf(Mood.JOYFUL to 0.8),
        "cheerful" to mapOf(Mood.JOYFUL to 1.0),
        "calm" to mapOf(Mood.CALM to 1.0),
        "relaxed" to mapOf(Mood.CALM to 1.0),
        "peaceful" to mapOf(Mood.CALM to 1.0),
        "chill" to mapOf(Mood.CALM to 0.9),
        "serene" to mapOf(Mood.CALM to 1.0),
        "quiet" to mapOf(Mood.CALM to 0.5),
        "focus" to mapOf(Mood.FOCUSED to 1.0),
        "focused" to mapOf(Mood.FOCUSED to 1.0),
        "concentrate" to mapOf(Mood.FOCUSED to 1.0),
        "study" to mapOf(Mood.FOCUSED to 0.8),
        "studying" to mapOf(Mood.FOCUSED to 0.9),
        "productive" to mapOf(Mood.FOCUSED to 0.8),
        "coding" to mapOf(Mood.FOCUSED to 0.6),
        "energy" to mapOf(Mood.ENERGETIC to 0.9),
        "energetic" to mapOf(Mood.ENERGETIC to 1.0),
        "workout" to mapOf(Mood.ENERGETIC to 1.0),
        "gym" to mapOf(Mood.ENERGETIC to 0.9),
        "hype" to mapOf(Mood.ENERGETIC to 0.8),
        "pumped" to mapOf(Mood.ENERGETIC to 0.9, Mood.JOYFUL to 0.4),
        "wired" to mapOf(Mood.ENERGETIC to 0.7, Mood.STRESSED to 0.3),
        "sad" to mapOf(Mood.MELANCHOLIC to 1.0),
        "lonely" to mapOf(Mood.MELANCHOLIC to 1.0),
        "blue" to mapOf(Mood.MELANCHOLIC to 0.4),
        "cry" to mapOf(Mood.MELANCHOLIC to 0.9),
        "crying" to mapOf(Mood.MELANCHOLIC to 1.0),
        "heartbroken" to mapOf(Mood.MELANCHOLIC to 1.0),
        "empty" to mapOf(Mood.MELANCHOLIC to 0.7),
        "miserable" to mapOf(Mood.MELANCHOLIC to 1.0),
        "depressed" to mapOf(Mood.MELANCHOLIC to 1.0),
        "stressed" to mapOf(Mood.STRESSED to 1.0),
        "stress" to mapOf(Mood.STRESSED to 1.0),
        "anxious" to mapOf(Mood.STRESSED to 1.0),
        "anxiety" to mapOf(Mood.STRESSED to 1.0),
        "overwhelmed" to mapOf(Mood.STRESSED to 1.0),
        "angry" to mapOf(Mood.STRESSED to 0.8),
        "frustrated" to mapOf(Mood.STRESSED to 0.8),
        "tense" to mapOf(Mood.STRESSED to 0.9),
        "worried" to mapOf(Mood.STRESSED to 0.8),
        "tired" to mapOf(Mood.TIRED to 1.0),
        "sleepy" to mapOf(Mood.TIRED to 1.0),
        "exhausted" to mapOf(Mood.TIRED to 1.0),
        "drained" to mapOf(Mood.TIRED to 1.0),
        "fatigue" to mapOf(Mood.TIRED to 1.0),
        "fatigued" to mapOf(Mood.TIRED to 1.0),
    )

    private fun zeroScores(): MutableMap<Mood, Double> =
        Mood.entries.associateWith { 0.0 }.toMutableMap()
}
