package com.botnick.rokidhermes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.botnick.rokidhermes.audio.VoiceInput
import com.botnick.rokidhermes.data.SettingsStore
import com.botnick.rokidhermes.network.HermesClient
import com.botnick.rokidhermes.tts.TtsPlayback
import com.botnick.rokidhermes.ui.ChatController
import com.botnick.rokidhermes.ui.Reachability
import com.botnick.rokidhermes.ui.hud.HudChatScreen
import com.botnick.rokidhermes.ui.settings.SettingsScreen

/**
 * Main entry point for Rokid Hermes. Voice-first: tap to talk, the utterance is
 * streamed to a Hermes Agent gateway, and the reply shows on the HUD and is
 * spoken back. Runs directly on the glasses (no phone bridge).
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
    val controller = remember { ChatController(settings, scope) }
    val voice = remember { VoiceInput(context) }
    val tts = remember { TtsPlayback(context) }
    // First run with nothing configured → start on setup.
    var screen by remember { mutableStateOf(if (settings.isConfigured) SCREEN_CHAT else SCREEN_SETTINGS) }

    DisposableEffect(Unit) {
        onDispose {
            voice.destroy()
            tts.shutdown()
        }
    }

    // Seed the honest connection indicator with a real probe when entering chat.
    LaunchedEffect(screen, settings) {
        if (screen == SCREEN_CHAT && settings.isConfigured &&
            controller.reachability == Reachability.UNKNOWN
        ) {
            controller.markReachability(HermesClient(settings).testConnection().isSuccess)
        }
    }

    fun speak(reply: String) = tts.speak(reply)

    fun startListening() {
        controller.setListening()
        voice.start(
            onPartial = { controller.updatePartial(it) },
            onResult = { text -> controller.send(text) { reply -> speak(reply) } },
            onError = { controller.onError(it) }
        )
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else controller.onError("Mic permission needed to talk")
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
                store.save(updated)
                settings = store.load() // re-read so memoryKey is materialised
                controller.updateSettings(settings)
                screen = SCREEN_CHAT
            },
            onBack = { screen = SCREEN_CHAT },
            tester = { candidate -> HermesClient(candidate).testConnection() }
        )

        else -> HudChatScreen(
            controller = controller,
            onMic = { onMic() },
            onStopListening = { voice.stop() },          // stop listening = submit the utterance
            onStop = { voice.stop(); controller.cancel() },
            onRetry = { controller.retry { reply -> speak(reply) } },
            onOpenSettings = { screen = SCREEN_SETTINGS },
            onNewChat = { controller.newConversation() }
        )
    }
}
