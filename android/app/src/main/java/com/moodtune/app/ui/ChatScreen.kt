package com.moodtune.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodtune.app.data.AppViewModel
import com.moodtune.app.model.ChatRole

private val suggestions = listOf("I'm anxious", "Feeling great", "Need to focus", "Can't sleep")

@Composable
fun ChatScreen(model: AppViewModel, padding: PaddingValues, onHome: () -> Unit) {
    val accent = MoodPalette.accent(model.assessment.mood)
    val ink = MoodPalette.ink(model.assessment.mood)
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(model.messages.size) {
        val last = model.messages.lastIndex
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.clickable(onClick = onHome),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back to home", tint = accent)
                Text("Home", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(model.messages, key = { it.id }) { message ->
                val isUser = message.role == ChatRole.USER
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message.text,
                        color = if (isUser) ink else Color.White,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier
                            .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                            .widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isUser) accent else Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                Text(
                    text = suggestion,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { model.send(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        val canSend = draft.isNotBlank()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text("How do you feel?", color = Color.White.copy(alpha = 0.45f))
                },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = accent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(42.dp)
                    .alpha(if (canSend) 1f else 0.4f)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(enabled = canSend) {
                        val text = draft
                        draft = ""
                        model.send(text)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = ink)
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}
