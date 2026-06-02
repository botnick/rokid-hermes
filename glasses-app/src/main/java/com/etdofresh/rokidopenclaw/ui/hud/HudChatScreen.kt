package com.etdofresh.rokidopenclaw.ui.hud

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etdofresh.rokidopenclaw.network.ChatMessage
import com.etdofresh.rokidopenclaw.network.Roles
import com.etdofresh.rokidopenclaw.ui.ChatController
import com.etdofresh.rokidopenclaw.ui.ChatStatus
import com.etdofresh.rokidopenclaw.ui.Hud

/**
 * Voice-first HUD chat for the 480x640 monochrome green micro-LED.
 * Minimal chrome: a title, a scrolling transcript, a status line, and a mic button.
 */
@Composable
fun HudChatScreen(
    controller: ChatController,
    onMic: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(controller.messages.size) {
        if (controller.messages.isNotEmpty()) {
            listState.animateScrollToItem(controller.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hud.Black)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◉ HERMES",
                color = Hud.Green,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Hud.Font
            )
            Text(
                text = "[ CFG ]",
                color = Hud.DimGreen,
                fontSize = 14.sp,
                fontFamily = Hud.Font,
                modifier = Modifier.clickable { onOpenSettings() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transcript
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (controller.messages.isEmpty()) {
                Text(
                    text = "Say something to Hermes…",
                    color = Hud.DimGreen,
                    fontSize = 14.sp,
                    fontFamily = Hud.Font,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(controller.messages) { _, msg ->
                        MessageRow(msg)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status line
        Text(
            text = controller.statusText,
            color = if (controller.status == ChatStatus.ERROR) Hud.Green else Hud.DimGreen,
            fontSize = 13.sp,
            fontFamily = Hud.Font,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Mic button
        val busy = controller.status == ChatStatus.THINKING
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Hud.Green, RoundedCornerShape(6.dp))
                .background(
                    if (controller.status == ChatStatus.LISTENING) Hud.Green else Hud.Black,
                    RoundedCornerShape(6.dp)
                )
                .clickable(enabled = !busy) { onMic() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (controller.status) {
                    ChatStatus.LISTENING -> "● LISTENING"
                    ChatStatus.THINKING -> "… THINKING"
                    else -> "🎤 TAP TO TALK"
                },
                color = if (controller.status == ChatStatus.LISTENING) Hud.Black else Hud.Green,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Hud.Font
            )
        }
    }
}

@Composable
private fun MessageRow(msg: ChatMessage) {
    val isUser = msg.role == Roles.USER
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (isUser) "> YOU" else "◉ HERMES",
            color = Hud.DimGreen,
            fontSize = 11.sp,
            fontFamily = Hud.Font
        )
        Text(
            text = msg.content,
            color = Hud.Green,
            fontSize = if (isUser) 15.sp else 16.sp,
            fontFamily = Hud.Font
        )
    }
}
