package com.moodtune.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moodtune.app.data.AppViewModel
import com.moodtune.app.model.PlaylistSuggestion

@Composable
fun ListenScreen(model: AppViewModel, padding: PaddingValues) {
    val accent = MoodPalette.accent(model.assessment.mood)
    val intro = if (model.isSpotifyConnected) {
        val name = model.spotifyName?.let { ", $it" }.orEmpty()
        "Playlists for this mood$name."
    } else {
        "Connect Spotify in settings for search and remote play. These still open in Spotify."
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 22.dp)
            .padding(top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = model.assessment.mood.title,
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            fontSize = 40.sp,
        )
        Text(
            text = intro,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 17.sp,
            lineHeight = 23.sp,
        )
        if (model.isLoadingPlaylists) {
            CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
        model.playlists.forEach { playlist ->
            PlaylistRow(playlist, accent) { model.play(playlist) }
        }
        model.statusMessage?.let { message ->
            Text(message, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistSuggestion, accent: Color, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            if (playlist.imageUrl != null) {
                AsyncImage(
                    model = playlist.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    if (playlist.isSearchLink) Icons.Filled.Search else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = playlist.name,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playlistSubtitle(playlist),
                color = Color.White.copy(alpha = 0.62f),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${playlist.name}", tint = accent)
    }
}

private fun playlistSubtitle(playlist: PlaylistSuggestion): String {
    val count = playlist.trackCount
    return if (count != null) "${playlist.detail} · $count tracks" else playlist.detail
}
