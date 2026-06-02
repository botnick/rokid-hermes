package com.etdofresh.rokidopenclaw.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etdofresh.rokidopenclaw.data.HermesSettings
import com.etdofresh.rokidopenclaw.ui.Hud

/**
 * Settings for the Hermes gateway connection: base URL (incl. /v1), API key,
 * and an optional session id for cross-turn continuity.
 */
@Composable
fun SettingsScreen(
    initial: HermesSettings,
    onSave: (HermesSettings) -> Unit,
    onBack: () -> Unit
) {
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var sessionId by remember { mutableStateOf(initial.sessionId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hud.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "⚙ HERMES GATEWAY",
            color = Hud.Green,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Hud.Font
        )
        Spacer(modifier = Modifier.height(16.dp))

        HudField(
            label = "Base URL (include /v1)",
            value = baseUrl,
            onChange = { baseUrl = it },
            keyboardType = KeyboardType.Uri
        )
        Spacer(modifier = Modifier.height(12.dp))
        HudField(
            label = "API key (API_SERVER_KEY)",
            value = apiKey,
            onChange = { apiKey = it },
            keyboardType = KeyboardType.Password
        )
        Spacer(modifier = Modifier.height(12.dp))
        HudField(
            label = "Session id (optional)",
            value = sessionId,
            onChange = { sessionId = it },
            keyboardType = KeyboardType.Text
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudButton(text = "BACK", filled = false, modifier = Modifier.weight(1f)) { onBack() }
            HudButton(text = "SAVE", filled = true, modifier = Modifier.weight(1f)) {
                onSave(
                    HermesSettings(
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim(),
                        sessionId = sessionId.trim()
                    )
                )
            }
        }
    }
}

@Composable
private fun HudField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Hud.DimGreen, fontSize = 12.sp, fontFamily = Hud.Font)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Hud.DimGreen, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Hud.Green,
                    fontSize = 15.sp,
                    fontFamily = Hud.Font
                ),
                cursorBrush = SolidColor(Hud.Green),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HudButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .border(2.dp, Hud.Green, RoundedCornerShape(6.dp))
            .background(if (filled) Hud.Green else Hud.Black, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled) Hud.Black else Hud.Green,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Hud.Font
        )
    }
}
