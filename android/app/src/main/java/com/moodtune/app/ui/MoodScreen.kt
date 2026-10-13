package com.moodtune.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodtune.app.data.AppViewModel
import com.moodtune.app.model.Mood
import kotlin.math.roundToInt

@Composable
fun MoodScreen(model: AppViewModel, padding: PaddingValues, onSettings: () -> Unit) {
    val mood = model.assessment.mood
    val accent = MoodPalette.accent(mood)
    val ink = MoodPalette.ink(mood)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MOODTUNE",
                color = accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = 2.2.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Tune, contentDescription = "Settings", tint = Color.White.copy(alpha = 0.85f))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Right now", color = Color.White.copy(alpha = 0.6f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    moodIcon(mood),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = mood.title,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Light,
                    fontSize = if (mood.title.length > 9) 42.sp else 56.sp,
                    maxLines = 1,
                )
            }
            Text(
                text = model.assessment.summary,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 18.sp,
                lineHeight = 24.sp,
            )
            Text(
                text = "${(model.assessment.confidence * 100).roundToInt()}% match",
                color = accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }

        val canPlay = model.playlists.isNotEmpty() && !model.isPlaying
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (model.playlists.isEmpty()) 0.5f else 1f)
                .clip(RoundedCornerShape(50))
                .background(accent)
                .clickable(enabled = canPlay) { model.playlists.firstOrNull()?.let(model::play) }
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ink)
            Spacer(Modifier.width(8.dp))
            Text(mood.playButtonTitle, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
        }

        if (model.snapshot?.isSampleData == true) {
            Text(
                text = "These health figures are sample data. Allow Health Connect access to use your own sleep, heart, and activity.",
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .padding(14.dp),
            )
        }

        if (model.assessment.reasons.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.assessment.reasons.forEach { reason ->
                    Text(
                        text = reason,
                        color = Color.White.copy(alpha = 0.88f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        val snapshot = model.snapshot
        val tiles = listOf(
            "Sleep" to MetricText.hours(snapshot?.sleepHours),
            "Heart" to MetricText.bpm(snapshot?.latestHeartRate),
            "HRV" to MetricText.milliseconds(snapshot?.heartRateVariabilityMS),
            "Steps" to MetricText.steps(snapshot?.stepsToday),
            "Move" to MetricText.energy(snapshot?.activeCalories),
            "Mindful" to MetricText.minutes(snapshot?.mindfulMinutes),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (title, value) ->
                        MetricTile(title, value, accent, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        model.statusMessage?.let { message ->
            Text(message, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MetricTile(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 1.1.sp,
        )
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 26.sp,
            maxLines = 1,
        )
    }
}

private fun moodIcon(mood: Mood): ImageVector = when (mood) {
    Mood.JOYFUL -> Icons.Filled.WbSunny
    Mood.CALM -> Icons.Filled.Spa
    Mood.FOCUSED -> Icons.Filled.CenterFocusStrong
    Mood.ENERGETIC -> Icons.Filled.Bolt
    Mood.MELANCHOLIC -> Icons.Filled.Cloud
    Mood.STRESSED -> Icons.Filled.MonitorHeart
    Mood.TIRED -> Icons.Filled.NightsStay
}
