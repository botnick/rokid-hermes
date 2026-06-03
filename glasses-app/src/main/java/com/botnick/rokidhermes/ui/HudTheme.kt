package com.botnick.rokidhermes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Shared monochrome green-on-black palette for the 480x640 Rokid micro-LED HUD.
 */
object Hud {
    val Green = Color(0xFF00FF41)
    val DimGreen = Color(0xFF00AA2A)
    val Black = Color.Black

    /** Monospace for fixed ASCII chrome (titles, button labels). */
    val Font = FontFamily.Monospace

    /**
     * Proportional family for content that can be Thai (or any non-Latin script):
     * assistant/user messages, hints, field text. Shares the system Noto fallback
     * so Thai renders with consistent metrics instead of a mixed-monospace look.
     */
    val Body = FontFamily.Default
}
