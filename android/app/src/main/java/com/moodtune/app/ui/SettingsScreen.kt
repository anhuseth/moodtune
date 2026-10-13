package com.moodtune.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodtune.app.data.AppViewModel
import com.moodtune.app.data.SpotifyClient
import com.moodtune.app.model.HealthAccessStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(model: AppViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text("Done", color = MoodPalette.accent(model.assessment.mood), fontWeight = FontWeight.SemiBold)
                }
            }

            SectionLabel("Spotify")
            OutlinedTextField(
                value = model.clientId,
                onValueChange = model::updateClientId,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Client ID") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
            )
            SelectionContainer {
                Text(
                    text = "In the Spotify dashboard, add redirect URI ${SpotifyClient.REDIRECT_URI}.",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                )
            }
            if (model.isSpotifyConnected) {
                Text(
                    text = "Account  ${model.spotifyName ?: "Connected"}",
                    color = Color.White,
                    fontSize = 16.sp,
                )
                TextButton(onClick = model::disconnectSpotify) {
                    Text("Disconnect", color = Color(0xFFFF6B6B))
                }
            } else {
                Button(
                    onClick = model::connectSpotify,
                    enabled = model.clientId.isNotBlank(),
                ) {
                    Text("Connect Spotify")
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            SectionLabel("Health")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Use sample data when Health is empty",
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                )
                Switch(
                    checked = model.useSampleHealthData,
                    onCheckedChange = model::updateSampleHealthData,
                )
            }
            TextButton(onClick = model::refreshHealthAccess) {
                Text("Refresh Health access", color = MoodPalette.accent(model.assessment.mood))
            }
            Text(
                text = healthDetail(model.healthStatus),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 13.sp,
            )
            model.statusMessage?.let { message ->
                Spacer(Modifier.height(4.dp))
                Text(message, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}

private fun healthDetail(status: HealthAccessStatus): String = when (status) {
    HealthAccessStatus.Unknown -> "Health has not been read yet."
    HealthAccessStatus.Unavailable -> "Health Connect is not available on this device."
    HealthAccessStatus.Ready -> "Reading your Health data."
    HealthAccessStatus.Empty -> "Health allowed a read, but there were no recent samples."
    is HealthAccessStatus.Failed -> status.message
}
