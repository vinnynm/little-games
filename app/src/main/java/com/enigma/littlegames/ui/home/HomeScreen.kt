package com.enigma.littlegames.ui.home

// ─────────────────────────────────────────────────────────────────────────────
// Home / hub screen — game cards, stats, nav to each game
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enigma.littlegames.common.AchievementToast
import com.enigma.littlegames.common.HubScreen
import com.enigma.littlegames.common.HubViewModel
import com.enigma.littlegames.common.LocalGameTheme
import com.enigma.littlegames.common.MiniStat

@Composable
fun HomeScreen(hub: HubViewModel) {
    val t      = LocalGameTheme.current
    val ui     by hub.ui.collectAsStateWithLifecycle()

    // Pulsing logo glow
    val inf = rememberInfiniteTransition(label = "logo_glow")
    val glowAlpha by inf.animateFloat(0.4f, 0.9f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow")

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // ── Logo ──────────────────────────────────────────────────────────
            Text("ENIGMA", fontSize = 36.sp, fontWeight = FontWeight.Black,
                letterSpacing = 8.sp, color = t.primary.copy(alpha = glowAlpha))
            Text("GAME HUB", fontSize = 13.sp, letterSpacing = 6.sp,
                color = t.textSecondary, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(28.dp))

            // ── Cross-game stats ──────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("LO BEST",  ui.loBestMoves?.let { "$it mv" } ?: "—")
                MiniStat("PIPE LVL", "${ui.pipeLevel}")
                MiniStat("SUDOKU",   "${ui.sudokuWins} ✓")
            }

            Spacer(Modifier.height(28.dp))

            // ── Game cards ────────────────────────────────────────────────────
            GameCard(
                emoji = "💡", title = "LIGHTS OUT", subtitle = "Toggle all 25 lights off",
                description = "Tap a cell to flip it and its 4 neighbours. GF(2) matrix solver guarantees solvability. Four difficulties, hint system, and best-score tracking.",
                accentColor = Color(0xFFFFD060),
                stat = ui.loBestMoves?.let { "Best: $it moves" } ?: "Not played yet",
                onClick = { hub.navigate(HubScreen.LightsOut) }
            )
            Spacer(Modifier.height(14.dp))
            GameCard(
                emoji = "🔧", title = "PIPE FLOW", subtitle = "Connect inlet to outlet",
                description = "Rotate pipes to seal the circuit without leaks. 24-level campaign with rocks, locked pipes, par scoring, and auto-solve hint.",
                accentColor = t.primary,
                stat = "Level ${ui.pipeLevel} / 24  ·  ⭐ ${ui.pipeStars}",
                onClick = { hub.navigate(HubScreen.PipeFlow) }
            )
            Spacer(Modifier.height(14.dp))
            GameCard(
                emoji = "🔢", title = "KILLER SUDOKU", subtitle = "Sums inside cages",
                description = "Procedurally generated 9×9 Sudoku with cage constraints. Easy → Expert. Note mode, error highlighting, elapsed timer, and instant hint solver.",
                accentColor = Color(0xFF4ECCA3),
                stat = "${ui.sudokuWins} puzzles solved",
                onClick = { hub.navigate(HubScreen.KillerSudoku) }
            )

            Spacer(Modifier.height(20.dp))

            // ── Bottom nav row ────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { hub.navigate(HubScreen.AchievementsScreen) },
                    border  = BorderStroke(1.dp, t.warning.copy(.4f)),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = t.warning),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🏆", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("${ui.unlockedIds.size} / 17", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { hub.navigate(HubScreen.Settings) },
                    border  = BorderStroke(1.dp, t.border),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = t.textSecondary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // Achievement toast floats above everything
        val newAch by hub.ui.collectAsStateWithLifecycle()
        AchievementToast(
            achievement = newAch.newAchievement,
            onDismiss   = hub::dismissAchievement,
            modifier    = Modifier.align(Alignment.TopCenter).statusBarsPadding()
        )
    }
}

@Composable
private fun GameCard(
    emoji: String, title: String, subtitle: String,
    description: String, accentColor: Color, stat: String, onClick: () -> Unit,
) {
    val t = LocalGameTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "card_s")

    Box(
        Modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(16.dp))
            .background(t.surface).border(1.dp, t.border, RoundedCornerShape(16.dp))
            .clickable(interactionSource, null, onClick = onClick).padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 32.sp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, color = accentColor, fontSize = 16.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text(subtitle, color = t.textSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = t.textSecondary)
            }
            Spacer(Modifier.height(12.dp))
            Text(description, color = t.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            Surface(color = accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                Text(stat, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}
