package com.moodtune.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.health.connect.client.PermissionController
import com.moodtune.app.data.AppViewModel
import com.moodtune.app.data.SpotifyAuthRelay
import com.moodtune.app.ui.MoodTuneRoot

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private var redirectHandled = false
    private var hasResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Touch the model so the Spotify listener exists before a cold-start redirect.
        model.clientId
        redirectHandled = deliverRedirect(intent)
        setContent {
            val prompt = model.permissionPrompt
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract(),
            ) { granted ->
                model.onHealthPermissionsResult(granted)
            }
            LaunchedEffect(prompt?.id) {
                val permissions = prompt?.permissions ?: return@LaunchedEffect
                launcher.launch(permissions)
            }
            MoodTuneRoot(model)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        redirectHandled = deliverRedirect(intent)
    }

    override fun onResume() {
        super.onResume()
        if (hasResumed && !redirectHandled) {
            SpotifyAuthRelay.onCancel()
        }
        hasResumed = true
        redirectHandled = false
    }

    private fun deliverRedirect(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (data.scheme != "moodtune") return false
        SpotifyAuthRelay.onRedirect(data)
        intent.data = null
        setIntent(intent)
        return true
    }
}
