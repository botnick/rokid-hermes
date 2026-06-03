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
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.botnick.rokidhermes.audio.VoiceInput
import com.botnick.rokidhermes.data.SettingsStore
import com.botnick.rokidhermes.network.HermesClient
import com.botnick.rokidhermes.tts.TtsPlayback
import com.botnick.rokidhermes.ui.ChatController
import com.botnick.rokidhermes.ui.Reachability
import com.botnick.rokidhermes.ui.hud.HudChatScreen
import com.botnick.rokidhermes.ui.settings.SettingsScreen
import com.botnick.rokidhermes.vision.CameraCapture
import kotlinx.coroutines.launch

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
    var ttsNotice by remember { mutableStateOf<String?>(null) }
    val tts = remember { TtsPlayback(context) { notice -> ttsNotice = notice } }
    val camera = remember { CameraCapture(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // A captured frame waiting to be attached to the next spoken question.
    val pendingImage = remember { mutableStateOf<String?>(null) }
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

    fun speak(reply: String) = tts.speak(reply, settings.ttsLocale)

    fun startListening() {
        tts.stop() // don't let a playing reply bleed into the live mic
        controller.setListening()
        voice.start(
            languageTag = settings.sttLanguageTag,
            onPartial = { controller.updatePartial(it) },
            onResult = { text ->
                val image = pendingImage.value
                pendingImage.value = null
                controller.send(text, image) { reply -> speak(reply) }
            },
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

    // Capture a frame, then immediately listen so the next utterance is asked about it.
    fun captureThenListen() {
        tts.stop()
        controller.setCapturing()
        scope.launch {
            val image = camera.captureDataUrl(lifecycleOwner)
            if (image == null) {
                controller.onError("Couldn't capture image")
            } else {
                pendingImage.value = image
                startListening()
            }
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) captureThenListen() else controller.onError("Camera permission needed to look")
    }

    fun onLook() {
        val granted = context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) captureThenListen() else cameraPermission.launch(Manifest.permission.CAMERA)
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
            voiceNotice = ttsNotice,
            onMic = { onMic() },
            onLook = { onLook() },
            onStopListening = { voice.stop() },          // stop listening = submit the utterance
            onStop = { tts.stop(); voice.stop(); controller.cancel() },
            onRetry = { controller.retry { reply -> speak(reply) } },
            onOpenSettings = { screen = SCREEN_SETTINGS },
            onNewChat = { tts.stop(); controller.newConversation() }
        )
    }
}
