package com.botnick.rokidhermes.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botnick.rokidhermes.data.HermesSettings
import com.botnick.rokidhermes.ui.Hud
import kotlinx.coroutines.launch

/**
 * Friendly setup for the Hermes connection: where it lives (URL), the key, and a
 * one-tap "Test & Save" that only drops you into chat once the gateway actually
 * answers — so you never save a silently-broken config.
 */
@Composable
fun SettingsScreen(
    initial: HermesSettings,
    onSave: (HermesSettings) -> Unit,
    onBack: () -> Unit,
    tester: suspend (HermesSettings) -> Result<List<String>>
) {
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var language by remember { mutableStateOf(initial.language) }

    var testing by remember { mutableStateOf(false) }
    var testMsg by remember { mutableStateOf("") }
    var testOk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun current() = HermesSettings(baseUrl.trim(), apiKey.trim(), initial.memoryKey, language)

    fun testAndSave() {
        testing = true
        testMsg = ""
        scope.launch {
            val candidate = current()
            val result = tester(candidate)
            testing = false
            result
                .onSuccess { ids ->
                    testOk = true
                    testMsg = "Connected (${ids.joinToString().ifBlank { "hermes-agent" }})"
                    onSave(candidate) // verified — save and leave to chat
                }
                .onFailure { e ->
                    testOk = false
                    testMsg = (e.message ?: "unknown error").take(90)
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hud.Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "⚙  CONNECT TO HERMES",
            color = Hud.Green,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Hud.Font
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Point this at your Hermes Agent's\napi_server (usually port 8642).",
            color = Hud.DimGreen,
            fontSize = 12.sp,
            fontFamily = Hud.Font
        )
        Spacer(modifier = Modifier.height(16.dp))

        Field(
            label = "Hermes URL",
            helper = "Include /v1 — e.g. http://192.168.1.50:8642/v1",
            value = baseUrl,
            onChange = { baseUrl = it; testMsg = "" },
            keyboardType = KeyboardType.Uri
        )
        Spacer(modifier = Modifier.height(14.dp))
        Field(
            label = "API key",
            helper = "Your API_SERVER_KEY (required)",
            value = apiKey,
            onChange = { apiKey = it; testMsg = "" },
            keyboardType = KeyboardType.Password
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Language", color = Hud.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Hud.Font)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Voice in, voice out, and reply language",
            color = Hud.DimGreen, fontSize = 11.sp, fontFamily = Hud.Font
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LangChip("Auto", language == HermesSettings.LANG_AUTO, Modifier.weight(1f)) {
                language = HermesSettings.LANG_AUTO; testMsg = ""
            }
            LangChip("ไทย", language == HermesSettings.LANG_TH, Modifier.weight(1f)) {
                language = HermesSettings.LANG_TH; testMsg = ""
            }
            LangChip("English", language == HermesSettings.LANG_EN, Modifier.weight(1f)) {
                language = HermesSettings.LANG_EN; testMsg = ""
            }
        }

        if (testMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = (if (testOk) "✓  " else "✗  ") + testMsg,
                color = Hud.Green,
                fontSize = 13.sp,
                fontFamily = Hud.Body
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            text = if (testing) "TESTING…" else "TEST & SAVE",
            filled = true,
            enabled = !testing && baseUrl.isNotBlank() && apiKey.isNotBlank(),
            onClick = { testAndSave() }
        )

        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                Button(text = "BACK", filled = false, enabled = true) { onBack() }
            }
            Box(modifier = Modifier.weight(1f)) {
                Button(text = "SAVE ANYWAY", filled = false, enabled = true) { onSave(current()) }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Memory ID: ${initial.memoryKey.take(8)}…  (kept so Hermes remembers you)",
            color = Hud.DimGreen,
            fontSize = 11.sp,
            fontFamily = Hud.Font
        )
    }
}

@Composable
private fun Field(
    label: String,
    helper: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Hud.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Hud.Font)
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
                textStyle = TextStyle(color = Hud.Green, fontSize = 15.sp, fontFamily = Hud.Font),
                cursorBrush = SolidColor(Hud.Green),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = helper, color = Hud.DimGreen, fontSize = 11.sp, fontFamily = Hud.Font)
    }
}

@Composable
private fun LangChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .border(2.dp, if (selected) Hud.Green else Hud.DimGreen, RoundedCornerShape(6.dp))
            .background(if (selected) Hud.Green else Hud.Black, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Hud.Black else Hud.Green,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Hud.Body // "ไทย" is Thai text
        )
    }
}

@Composable
private fun Button(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (enabled) Hud.Green else Hud.DimGreen, RoundedCornerShape(6.dp))
            .background(if (filled && enabled) Hud.Green else Hud.Black, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled && enabled) Hud.Black else if (enabled) Hud.Green else Hud.DimGreen,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Hud.Font
        )
    }
}
