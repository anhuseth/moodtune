package com.moodtune.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodtune.app.data.AppViewModel

private enum class AppTab { MOOD, CHAT, LISTEN }

@Composable
fun MoodTuneRoot(model: AppViewModel) {
    val mood = model.assessment.mood
    LaunchedEffect(Unit) { model.bootstrap() }
    MoodBackground(mood) {
        if (!model.didFinishOnboarding) {
            OnboardingScreen(model)
        } else {
            MainScaffold(model)
        }
    }
}

@Composable
private fun OnboardingScreen(model: AppViewModel) {
    val accent = MoodPalette.accent(com.moodtune.app.model.Mood.CALM)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = "MOODTUNE",
            color = accent,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 2.4.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Music that follows the day you’re actually having.",
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            fontSize = 36.sp,
            lineHeight = 42.sp,
        )
        Spacer(Modifier.height(28.dp))
        Feature(
            title = "Heart and sleep",
            detail = "Health Connect supplies heart rate, HRV, sleep, steps, workouts, and mindfulness.",
        )
        Spacer(Modifier.height(16.dp))
        Feature(
            title = "A sentence from you",
            detail = "Chat can confirm the reading or override it when the sensors are wrong.",
        )
        Spacer(Modifier.height(16.dp))
        Feature(
            title = "A playlist",
            detail = "MoodTune opens a Spotify mix that fits the mood it landed on.",
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(accent)
                .clickable(onClick = model::finishOnboarding)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Allow Health access",
                color = Color(0.08f, 0.10f, 0.09f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
        }
        TextButton(
            onClick = model::skipOnboarding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Not now",
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun Feature(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(detail, color = Color.White.copy(alpha = 0.68f), fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun MainScaffold(model: AppViewModel) {
    var tab by remember { mutableStateOf(AppTab.MOOD) }
    var showSettings by remember { mutableStateOf(false) }
    val mood = model.assessment.mood
    val accent = MoodPalette.accent(mood)
    val ink = MoodPalette.ink(mood)
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = ink.copy(alpha = 0.94f)) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    unselectedIconColor = Color.White.copy(alpha = 0.62f),
                    unselectedTextColor = Color.White.copy(alpha = 0.62f),
                    indicatorColor = accent.copy(alpha = 0.18f),
                )
                NavigationBarItem(
                    selected = tab == AppTab.MOOD,
                    onClick = { tab = AppTab.MOOD },
                    icon = { Icon(Icons.Filled.MonitorHeart, contentDescription = "Mood") },
                    label = { Text("Mood") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == AppTab.CHAT,
                    onClick = { tab = AppTab.CHAT },
                    icon = { Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Chat") },
                    label = { Text("Chat") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == AppTab.LISTEN,
                    onClick = { tab = AppTab.LISTEN },
                    icon = { Icon(Icons.Filled.PlayCircle, contentDescription = "Listen") },
                    label = { Text("Listen") },
                    colors = colors,
                )
            }
        },
    ) { padding ->
        when (tab) {
            AppTab.MOOD -> MoodScreen(model, padding, onSettings = { showSettings = true })
            AppTab.CHAT -> ChatScreen(model, padding, onHome = { tab = AppTab.MOOD })
            AppTab.LISTEN -> ListenScreen(model, padding)
        }
    }
    if (showSettings) {
        SettingsSheet(model, onDismiss = { showSettings = false })
    }
}
