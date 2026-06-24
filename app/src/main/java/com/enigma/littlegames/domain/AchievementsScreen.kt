package com.enigma.littlegames.domain

// ─────────────────────────────────────────────────────────────────────────────
// Achievements screen — Phase 4a: 30 achievements, 7 groups
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enigma.littlegames.common.GameTheme
import com.enigma.littlegames.common.HubScreen
import com.enigma.littlegames.common.HubViewModel
import com.enigma.littlegames.common.LocalGameTheme

@Composable
fun AchievementsScreen(hub: HubViewModel) {
    val t  = LocalGameTheme.current
    val ui by hub.ui.collectAsStateWithLifecycle()

    val groups = listOf(
        "💡 LIGHTS OUT"        to listOf(Achievements.LO_FIRST_WIN, Achievements.LO_PERFECT, Achievements.LO_EXPERT, Achievements.LO_SPEED, Achievements.LO_NO_HINT),
        "🔧 PIPE FLOW"         to listOf(Achievements.PIPE_FIRST_WIN, Achievements.PIPE_3STARS, Achievements.PIPE_LEVEL10, Achievements.PIPE_LEVEL24, Achievements.PIPE_NO_SOLVE),
        "🔢 SUDOKU GAMES"      to listOf(Achievements.SK_FIRST_WIN, Achievements.SK_NO_ERROR, Achievements.SK_EXPERT, Achievements.SK_FIVE_WINS, Achievements.SK_SPEED_RUN),
        "💣 EXPLODING KITTENS" to listOf(Achievements.EK_FIRST_WIN, Achievements.EK_DEFUSE, Achievements.EK_BEAT_HARD, Achievements.EK_FIVE_WINS),
        "🧠 SIMON SAYS"        to listOf(Achievements.SIMON_FIRST, Achievements.SIMON_10, Achievements.SIMON_20, Achievements.SIMON_SPEED),
        "2️⃣ 2048"              to listOf(Achievements.TFE_FIRST, Achievements.TFE_256, Achievements.TFE_1024, Achievements.TFE_2048),
        "🎮 HUB"               to listOf(Achievements.ALL_GAMES, Achievements.THEME_EXPLORER, Achievements.COMPLETIONIST),
    )

    val total    = Achievements.total
    val unlocked = ui.unlockedIds.size

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { hub.navigate(HubScreen.Home) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = t.textPrimary)
            }
            Text("ACHIEVEMENTS", color = t.primary, fontSize = 18.sp,
                fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Spacer(Modifier.weight(1f))
            Text("$unlocked/$total", color = t.warning, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }

        LinearProgressIndicator(
            progress = { unlocked.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 8.dp),
            color = t.warning, trackColor = t.border,
        )

        Spacer(Modifier.height(16.dp))

        Column(Modifier.verticalScroll(rememberScrollState())) {
            groups.forEach { (groupName, achievements) ->
                val groupUnlocked = achievements.count { ui.unlockedIds.contains(it.id) }
                Row(
                    Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(groupName, color = t.textSecondary, fontSize = 10.sp,
                        letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text("$groupUnlocked/${achievements.size}",
                        color = t.textSecondary, fontSize = 10.sp)
                }
                achievements.forEach { ach ->
                    AchievementRow(ach, ui.unlockedIds.contains(ach.id), t)
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AchievementRow(ach: Achievement, unlocked: Boolean, t: GameTheme) {
    val inf = rememberInfiniteTransition(label = "ach_glow")
    val glowAlpha by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ach_glow_a"
    )
    val isHidden = ach.hidden && !unlocked

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (unlocked) t.warning.copy(.08f) else t.surface)
            .border(
                1.dp,
                if (unlocked) t.warning.copy(glowAlpha) else t.border,
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                .background(if (unlocked) t.warning.copy(.15f) else t.border.copy(.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isHidden) "❓" else ach.emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isHidden) "???" else ach.title,
                color = if (unlocked) t.warning else t.textPrimary,
                fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
            Text(
                if (isHidden) "Complete hidden challenge to reveal" else ach.description,
                color = t.textSecondary, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
        if (unlocked) {
            Text("✓", color = t.warning, fontSize = 18.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 8.dp))
        }
    }
}
