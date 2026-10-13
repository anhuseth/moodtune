package com.moodtune.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moodtune.app.model.Mood
import java.text.NumberFormat
import kotlin.math.roundToInt

object MoodPalette {
    fun ink(mood: Mood): Color = when (mood) {
        Mood.JOYFUL -> Color(0.11f, 0.08f, 0.05f)
        Mood.CALM -> Color(0.05f, 0.09f, 0.08f)
        Mood.FOCUSED -> Color(0.05f, 0.07f, 0.11f)
        Mood.ENERGETIC -> Color(0.12f, 0.06f, 0.04f)
        Mood.MELANCHOLIC -> Color(0.07f, 0.08f, 0.12f)
        Mood.STRESSED -> Color(0.12f, 0.07f, 0.06f)
        Mood.TIRED -> Color(0.07f, 0.07f, 0.11f)
    }

    fun accent(mood: Mood): Color = when (mood) {
        Mood.JOYFUL -> Color(0.96f, 0.73f, 0.32f)
        Mood.CALM -> Color(0.56f, 0.78f, 0.68f)
        Mood.FOCUSED -> Color(0.56f, 0.73f, 0.88f)
        Mood.ENERGETIC -> Color(1.0f, 0.45f, 0.31f)
        Mood.MELANCHOLIC -> Color(0.64f, 0.72f, 0.84f)
        Mood.STRESSED -> Color(0.89f, 0.49f, 0.36f)
        Mood.TIRED -> Color(0.70f, 0.72f, 0.88f)
    }
}

@Composable
fun MoodBackground(mood: Mood, content: @Composable () -> Unit) {
    val ink = MoodPalette.ink(mood)
    val accent = MoodPalette.accent(mood)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(ink)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.38f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.18f),
                    radius = 420.dp.toPx(),
                ),
            )
        }
        content()
    }
}

object MetricText {
    fun steps(value: Double?): String {
        value ?: return "—"
        return NumberFormat.getIntegerInstance().format(value)
    }

    fun hours(value: Double?): String {
        value ?: return "—"
        val format = NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        return "${format.format(value)} h"
    }

    fun bpm(value: Double?): String {
        value ?: return "—"
        return "${value.roundToInt()} bpm"
    }

    fun milliseconds(value: Double?): String {
        value ?: return "—"
        return "${value.roundToInt()} ms"
    }

    fun energy(value: Double?): String {
        value ?: return "—"
        return "${value.roundToInt()} kcal"
    }

    fun minutes(value: Double?): String {
        value ?: return "—"
        return "${value.roundToInt()} min"
    }
}
