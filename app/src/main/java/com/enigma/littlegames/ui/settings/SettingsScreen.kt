package com.enigma.littlegames.ui.settings

// ─────────────────────────────────────────────────────────────────────────────
// Settings screen — Phase 4a: about section updated for 8 games / 30 achievements
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enigma.littlegames.common.GameThemes
import com.enigma.littlegames.common.HubScreen
import com.enigma.littlegames.common.HubViewModel
import com.enigma.littlegames.common.LocalGameTheme

@Composable
fun SettingsScreen(hub: HubViewModel) {
    val t  = LocalGameTheme.current
    val ui by hub.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { hub.navigate(HubScreen.Home) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = t.textPrimary)
            }
            Text("SETTINGS", color = t.primary, fontSize = 18.sp,
                fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        }
        Spacer(Modifier.height(24.dp))

        Column(Modifier.verticalScroll(rememberScrollState())) {

            // ── Sound toggle ──────────────────────────────────────────────────
            SectionLabel("SOUND")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(t.surface).border(1.dp, t.border, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (ui.soundEnabled) "🔊" else "🔇", fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sound Effects", color = t.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("SFX and ambient themes", color = t.textSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = ui.soundEnabled,
                    onCheckedChange = { hub.setSoundEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = t.background,
                        checkedTrackColor   = t.primary,
                        uncheckedTrackColor = t.border,
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Theme picker ──────────────────────────────────────────────────
            SectionLabel("CHOOSE THEME")
            Spacer(Modifier.height(12.dp))

            GameThemes.all.forEach { theme ->
                val selected = theme.id == ui.theme.id
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) theme.primary.copy(.15f) else t.surface)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) theme.primary else t.border,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { hub.setTheme(theme) }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(theme.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(theme.name,
                                color = if (selected) theme.primary else t.textPrimary,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 6.dp)) {
                                listOf(theme.primary, theme.secondary, theme.accent, theme.background)
                                    .forEach { c ->
                                        Box(Modifier.size(16.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(c).border(0.5.dp, Color.White.copy(.2f),
                                                androidx.compose.foundation.shape.CircleShape))
                                    }
                            }
                        }
                        if (selected) Icon(Icons.Default.Check, null, tint = theme.primary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── About ─────────────────────────────────────────────────────────
            SectionLabel("ABOUT")
            Spacer(Modifier.height(12.dp))
            Surface(color = t.surface, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Enigma Game Hub  v4.0 — Phase 4a",
                        color = t.textPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        "🎮  Eight complete games",
                        "🧩  Logic: Lights Out · Pipe Flow · Killer Sudoku · Sudoku · Kakuro",
                        "🃏  Card & Memory: Exploding Kittens · Simon Says",
                        "🕹️  Arcade: 2048",
                        "🎨  Five customisable themes",
                        "💾  Progress saved via DataStore",
                        "🔊  Sound effects + ambient audio",
                        "🏆  30 unlockable achievements",
                        "✨  Canvas particle effects on solve",
                    ).forEach { line ->
                        Text(line, color = t.textSecondary, fontSize = 12.sp,
                            lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val t = LocalGameTheme.current
    Text(text, color = t.textSecondary, fontSize = 11.sp,
        letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
}
