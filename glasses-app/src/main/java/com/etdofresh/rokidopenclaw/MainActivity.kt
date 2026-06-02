package com.etdofresh.rokidopenclaw

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.etdofresh.rokidopenclaw.audio.VoiceInput
import com.etdofresh.rokidopenclaw.data.SettingsStore
import com.etdofresh.rokidopenclaw.tts.TtsPlayback
import com.etdofresh.rokidopenclaw.ui.ChatController
import com.etdofresh.rokidopenclaw.ui.hud.HudChatScreen
import com.etdofresh.rokidopenclaw.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Main entry point for the Rokid Hermes glasses app. Voice-first: tap to talk,
 * the utterance goes to a Hermes Agent gateway, and the reply is shown on the
 * HUD and spoken back. Runs directly on the glasses (no phone bridge).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HermesApp() }
    }
}

private const val SCREEN_CHAT = "chat"
private const val SCREEN_SETTINGS = "settings"

@Composable
private fun HermesApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    val controller = remember { ChatController(settings) }
    val voice = remember { VoiceInput(context) }
    val tts = remember { TtsPlayback(context) }
    var screen by remember { mutableStateOf(SCREEN_CHAT) }

    DisposableEffect(Unit) {
        onDispose {
            voice.destroy()
            tts.shutdown()
        }
    }

    fun startListening() {
        controller.setListening()
        voice.start(
            onPartial = { controller.setPartial(it) },
            onResult = { text ->
                scope.launch {
                    controller.send(text) { reply -> tts.speak(reply) }
                }
            },
            onError = { controller.onError(it) }
        )
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else controller.onError("Mic permission denied")
    }

    fun onMic() {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    when (screen) {
        SCREEN_SETTINGS -> SettingsScreen(
            initial = settings,
            onSave = { updated ->
                settings = updated
                store.save(updated)
                controller.updateSettings(updated)
                screen = SCREEN_CHAT
            },
            onBack = { screen = SCREEN_CHAT }
        )

        else -> HudChatScreen(
            controller = controller,
            onMic = { onMic() },
            onOpenSettings = { screen = SCREEN_SETTINGS }
        )
    }
}
