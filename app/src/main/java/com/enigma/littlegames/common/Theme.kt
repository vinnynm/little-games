package com.enigma.littlegames.common

// ─────────────────────────────────────────────────────────────────────────────
// Theme system — 5 themes, CompositionLocal, no Material3 color scheme required
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class GameTheme(
    val id: String,
    val name: String,
    val emoji: String,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val success: Color,
    val error: Color,
    val warning: Color,
    // Ambient resource ID for SoundEngine (0 = none until wired)
    val ambientResId: Int = 0,
)

object GameThemes {
    val CYBER = GameTheme(
        id = "cyber",      name = "Cyber",  emoji = "🌐",
        background     = Color(0xFF0D0F14), surface        = Color(0xFF141720),
        surfaceVariant = Color(0xFF1A1E2C), primary        = Color(0xFF00E5FF),
        primaryVariant = Color(0xFF00B8D9), secondary      = Color(0xFF7C3AED),
        accent         = Color(0xFF10B981), textPrimary    = Color(0xFFE2E8F0),
        textSecondary  = Color(0xFF64748B), border         = Color(0xFF2A2F3F),
        success        = Color(0xFF10B981), error          = Color(0xFFEF4444),
        warning        = Color(0xFFF59E0B),
    )
    val LAVA = GameTheme(
        id = "lava",       name = "Lava",   emoji = "🔥",
        background     = Color(0xFF100808), surface        = Color(0xFF1A0A0A),
        surfaceVariant = Color(0xFF231010), primary        = Color(0xFFFF4500),
        primaryVariant = Color(0xFFD93800), secondary      = Color(0xFFFF8C00),
        accent         = Color(0xFFFFCC00), textPrimary    = Color(0xFFF5E0D0),
        textSecondary  = Color(0xFF8B5045), border         = Color(0xFF3A1A1A),
        success        = Color(0xFFFFCC00), error          = Color(0xFFFF1744),
        warning        = Color(0xFFFF6D00),
    )
    val BIO = GameTheme(
        id = "bio",        name = "Bio",    emoji = "🧬",
        background     = Color(0xFF040D0A), surface        = Color(0xFF061410),
        surfaceVariant = Color(0xFF0A1E16), primary        = Color(0xFF00FF88),
        primaryVariant = Color(0xFF00CC66), secondary      = Color(0xFF88FF44),
        accent         = Color(0xFF44FFEE), textPrimary    = Color(0xFFD0F5E8),
        textSecondary  = Color(0xFF3A8060), border         = Color(0xFF0D3020),
        success        = Color(0xFF00FF88), error          = Color(0xFFFF4466),
        warning        = Color(0xFFFFDD00),
    )
    val ICE = GameTheme(
        id = "ice",        name = "Ice",    emoji = "❄️",
        background     = Color(0xFF040810), surface        = Color(0xFF080F1E),
        surfaceVariant = Color(0xFF0C1428), primary        = Color(0xFF60A5FA),
        primaryVariant = Color(0xFF3B82F6), secondary      = Color(0xFFA78BFA),
        accent         = Color(0xFF34D399), textPrimary    = Color(0xFFDDE8FF),
        textSecondary  = Color(0xFF4A6080), border         = Color(0xFF1A2545),
        success        = Color(0xFF34D399), error          = Color(0xFFF87171),
        warning        = Color(0xFFFBBF24),
    )
    val GOLD = GameTheme(
        id = "gold",       name = "Gold",   emoji = "🏆",
        background     = Color(0xFF0F0D06), surface        = Color(0xFF1A1608),
        surfaceVariant = Color(0xFF231E0A), primary        = Color(0xFFFFD700),
        primaryVariant = Color(0xFFDAA520), secondary      = Color(0xFFFF8C42),
        accent         = Color(0xFFE040FB), textPrimary    = Color(0xFFFFF8DC),
        textSecondary  = Color(0xFF8B7830), border         = Color(0xFF3D3010),
        success        = Color(0xFF66BB6A), error          = Color(0xFFEF5350),
        warning        = Color(0xFFFF8C42),
    )

    val all = listOf(CYBER, LAVA, BIO, ICE, GOLD)
    fun byId(id: String): GameTheme = all.firstOrNull { it.id == id } ?: CYBER
}

val LocalGameTheme = compositionLocalOf { GameThemes.CYBER }
