package com.botnick.rokidhermes.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botnick.rokidhermes.network.Roles
import com.botnick.rokidhermes.ui.ChatController
import com.botnick.rokidhermes.ui.ChatStatus
import com.botnick.rokidhermes.ui.Hud
import com.botnick.rokidhermes.ui.Reachability

/**
 * Voice-first HUD chat for the 480x640 monochrome green micro-LED. One big,
 * obvious action that changes with state (talk / send / stop), an honest
 * connection indicator, a forming-speech bubble, and an actionable error card —
 * no dead-ends.
 */
@Composable
fun HudChatScreen(
    controller: ChatController,
    voiceUnavailable: Boolean = false,
    onMic: () -> Unit,
    onStopListening: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hud.Black)
            .padding(16.dp)
    ) {
        Header(
            reachability = controller.reachability,
            onOpenSettings = onOpenSettings,
            onNewChat = onNewChat,
            showNewChat = controller.messages.isNotEmpty()
        )
        if (voiceUnavailable) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "🔇 Voice replies unavailable — text only",
                color = Hud.DimGreen,
                fontSize = 11.sp,
                fontFamily = Hud.Font
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !controller.configured -> Onboarding(onOpenSettings)
                controller.messages.isEmpty() && controller.streamingReply.isEmpty() &&
                    controller.partial.isEmpty() -> EmptyHint()
                else -> Transcript(controller)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (controller.status == ChatStatus.ERROR) {
            ErrorCard(
                message = controller.statusText,
                canRetry = controller.canRetry,
                onRetry = onRetry,
                onOpenSettings = onOpenSettings
            )
        } else {
            StatusLine(controller)
        }

        Spacer(modifier = Modifier.height(8.dp))
        MicBar(controller, onMic, onStopListening, onStop)
    }
}

@Composable
private fun Header(
    reachability: Reachability,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit,
    showNewChat: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dotFor(reachability),
                color = Hud.Green,
                fontSize = 14.sp,
                fontFamily = Hud.Font
            )
            Text(
                text = "  HERMES",
                color = Hud.Green,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Hud.Font
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showNewChat) {
                TapTarget(onClick = onNewChat) {
                    Text(text = "NEW", color = Hud.DimGreen, fontSize = 13.sp, fontFamily = Hud.Font)
                }
                Spacer(modifier = Modifier.height(0.dp))
            }
            TapTarget(onClick = onOpenSettings) {
                Text(text = "SET UP", color = Hud.DimGreen, fontSize = 13.sp, fontFamily = Hud.Font)
            }
        }
    }
}

private fun dotFor(r: Reachability): String = when (r) {
    Reachability.OK -> "●"        // verified reachable
    Reachability.FAILED -> "⚠"    // configured but last attempt failed
    Reachability.UNKNOWN -> "◌"   // configured, not yet checked
    Reachability.NOT_SET -> "○"   // not set up
}

@Composable
private fun TapTarget(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun Onboarding(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "👋", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Connect to your Hermes agent",
            color = Hud.Green,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Hud.Font
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Open setup and enter your Hermes\nURL and API key to start talking.",
            color = Hud.DimGreen,
            fontSize = 13.sp,
            fontFamily = Hud.Font
        )
        Spacer(modifier = Modifier.height(24.dp))
        BigButton(text = "⚙  OPEN SETUP", filled = true, onClick = onOpenSettings)
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🎤", fontSize = 36.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Tap the mic and just talk.",
            color = Hud.Green,
            fontSize = 16.sp,
            fontFamily = Hud.Font
        )
        Spacer(modifier = Modifier.height(8.dp))
        listOf(
            "\"What's on my schedule today?\"",
            "\"Summarize my latest emails.\"",
            "\"Remind me to call mom at 6pm.\""
        ).forEach { example ->
            Text(text = example, color = Hud.DimGreen, fontSize = 12.sp, fontFamily = Hud.Font)
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun Transcript(controller: ChatController) {
    val listState = rememberLazyListState()
    val streaming = controller.streamingReply
    val partial = controller.partial.takeIf { controller.status == ChatStatus.LISTENING } ?: ""
    val count = controller.messages.size

    LaunchedEffect(count, streaming, partial) {
        val extra = (if (streaming.isNotEmpty()) 1 else 0) + (if (partial.isNotEmpty()) 1 else 0)
        val target = (count + extra - 1).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(controller.messages) { msg -> MessageRow(msg.role, msg.content, label = null) }
        if (streaming.isNotEmpty()) {
            item { MessageRow(Roles.ASSISTANT, "$streaming▌", label = null) }
        }
        if (partial.isNotEmpty()) {
            item { MessageRow(Roles.USER, partial, label = "› YOU (listening)") }
        }
    }
}

@Composable
private fun MessageRow(role: String, content: String, label: String?) {
    val isUser = role == Roles.USER
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label ?: if (isUser) "› YOU" else "◉ HERMES",
            color = Hud.DimGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Hud.Font
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = content,
            color = Hud.Green,
            fontSize = if (isUser) 15.sp else 16.sp,
            fontFamily = Hud.Font
        )
    }
}

@Composable
private fun StatusLine(controller: ChatController) {
    val text = when (controller.status) {
        ChatStatus.IDLE -> if (controller.configured) "" else "Not connected"
        else -> controller.statusText
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            color = Hud.DimGreen,
            fontSize = 13.sp,
            fontFamily = Hud.Font,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ErrorCard(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hud.Green, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "⚠  $message",
            color = Hud.Green,
            fontSize = 13.sp,
            fontFamily = Hud.Font
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canRetry) {
                Box(modifier = Modifier.weight(1f)) {
                    SmallButton(text = "RETRY", filled = true, onClick = onRetry)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                SmallButton(text = "SET UP", filled = false, onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun MicBar(
    controller: ChatController,
    onMic: () -> Unit,
    onStopListening: () -> Unit,
    onStop: () -> Unit
) {
    val status = controller.status
    val busy = status == ChatStatus.THINKING || status == ChatStatus.STREAMING
    val listening = status == ChatStatus.LISTENING

    val label: String
    val action: () -> Unit
    val enabled: Boolean
    when {
        !controller.configured -> { label = "⚙  SET UP FIRST"; action = onMic; enabled = false }
        listening -> { label = "●  LISTENING — TAP TO SEND"; action = onStopListening; enabled = true }
        busy -> { label = "✕  STOP"; action = onStop; enabled = true }
        else -> { label = "🎤  TAP TO TALK"; action = onMic; enabled = true }
    }

    val filled = listening
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (enabled) Hud.Green else Hud.DimGreen, RoundedCornerShape(8.dp))
            .background(if (filled) Hud.Green else Hud.Black, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { action() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (filled) Hud.Black else if (enabled) Hud.Green else Hud.DimGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Hud.Font
        )
    }
}

@Composable
private fun BigButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .border(2.dp, Hud.Green, RoundedCornerShape(8.dp))
            .background(if (filled) Hud.Green else Hud.Black, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled) Hud.Black else Hud.Green,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Hud.Font
        )
    }
}

@Composable
private fun SmallButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Hud.Green, RoundedCornerShape(6.dp))
            .background(if (filled) Hud.Green else Hud.Black, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled) Hud.Black else Hud.Green,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Hud.Font
        )
    }
}
