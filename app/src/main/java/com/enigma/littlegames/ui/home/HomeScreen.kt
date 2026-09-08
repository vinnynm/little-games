package com.enigma.littlegames.ui.home

// ─────────────────────────────────────────────────────────────────────────────
// Home Screen — 11 games in 4 sections
// Logic Puzzles · Arcade · Word & Memory · Spatial
// Bug fixes applied:
//   #1 (critical) — Added the Exploding Kittens GameCard. It was fully built
//       but had no card anywhere in the UI, so it was unreachable.
//   #5 (high)     — Sokoban card said "30 levels to solve" while the pack
//       actually ships 50 levels (SOKOBAN_LEVELS.size). Fixed the copy.
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
    val t  = LocalGameTheme.current
    val ui by hub.ui.collectAsStateWithLifecycle()

    val inf = rememberInfiniteTransition(label = "logo_glow")
    val glowAlpha by inf.animateFloat(
        0.4f, 0.9f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            Text("ENIGMA", fontSize = 36.sp, fontWeight = FontWeight.Black,
                letterSpacing = 8.sp, color = t.primary.copy(alpha = glowAlpha))
            Text("GAME HUB", fontSize = 13.sp, letterSpacing = 6.sp,
                color = t.textSecondary, fontWeight = FontWeight.Bold)
            Text("11 GAMES", fontSize = 10.sp, letterSpacing = 4.sp,
                color = t.textSecondary.copy(.55f))

            Spacer(Modifier.height(24.dp))

            // Stats grid
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("LO BEST",  ui.loBestMoves?.let { "$it mv" } ?: "—")
                MiniStat("PIPE LVL", "${ui.pipeLevel}")
                MiniStat("PUZZLES",  "${ui.sudokuWins} ✓")
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("SIMON",   if (ui.simonBest > 0) "Lv ${ui.simonBest}" else "—")
                MiniStat("WORDLE",  if (ui.wordleWins > 0) "${ui.wordleWins}W" else "—")
                MiniStat("SOKOBAN", "Lv ${ui.sokobanLevel}")
            }

            Spacer(Modifier.height(24.dp))

            // ── LOGIC PUZZLES ─────────────────────────────────────────────────
            SectionDivider("LOGIC PUZZLES", t.primary)
            Spacer(Modifier.height(12.dp))

            GameCard("💡", "LIGHTS OUT", "Toggle all 25 lights off",
                "Tap a cell to flip it and its 4 neighbours. GF(2) solver. Four difficulties, hint system.",
                Color(0xFFFFD060),
                ui.loBestMoves?.let { "Best: $it moves" } ?: "Not played yet",
            ) { hub.navigate(HubScreen.LightsOut) }
            Spacer(Modifier.height(12.dp))
            GameCard("🔢", "KILLER SUDOKU", "Sums inside cages",
                "Procedural 9×9 with cage constraints. Easy → Expert. Note mode, error highlighting.",
                Color(0xFF4ECCA3), "${ui.sudokuWins} puzzles solved",
            ) { hub.navigate(HubScreen.KillerSudoku) }
            Spacer(Modifier.height(12.dp))
            GameCard("9️⃣", "SUDOKU", "Classic 9×9 logic puzzle",
                "The timeless number placement puzzle. Four difficulties. Notes, hint, error detection.",
                Color(0xFF60A5FA), "${ui.sudokuWins} puzzles solved",
            ) { hub.navigate(HubScreen.Sudoku) }
            Spacer(Modifier.height(12.dp))
            GameCard("➕", "KAKURO", "Number crossword",
                "Fill runs with 1–9 summing to their clue. No repeats. Easy 5×5 to Expert 11×11.",
                Color(0xFFA78BFA), "${ui.sudokuWins} puzzles solved",
            ) { hub.navigate(HubScreen.Kakuro) }
            Spacer(Modifier.height(12.dp))
            GameCard("🖼️", "NONOGRAM", "Reveal the hidden picture",
                "Shade cells using row and column clues to uncover a pixel image. 5×5 to 20×20.",
                Color(0xFF34D399),
                if (ui.nonogramWins > 0) "${ui.nonogramWins} pictures revealed" else "Reveal hidden images",
            ) { hub.navigate(HubScreen.Nonogram) }

            Spacer(Modifier.height(20.dp))

            // ── ARCADE ────────────────────────────────────────────────────────
            SectionDivider("ARCADE", t.secondary)
            Spacer(Modifier.height(12.dp))

            GameCard("🔧", "PIPE FLOW", "Connect inlet to outlet",
                "Rotate pipes to seal the circuit. 24-level campaign with locks, rocks, and X junctions.",
                t.primary, "Level ${ui.pipeLevel} / 24  ·  ⭐ ${ui.pipeStars}",
            ) { hub.navigate(HubScreen.PipeFlow) }
            Spacer(Modifier.height(12.dp))
            GameCard("🌈", "FLOW FREE", "Connect the colour dots",
                "Draw paths to connect each colour pair. Every cell must be filled. Drag to draw.",
                t.accent,
                if (ui.flowWins > 0) "${ui.flowWins} puzzles solved" else "Connect all the dots",
            ) { hub.navigate(HubScreen.FlowFree) }
            Spacer(Modifier.height(12.dp))
            GameCard("🀄", "2048", "Swipe to merge tiles",
                "Slide the board to combine matching tiles. Reach 2048 to win. Simple rules, deep strategy.",
                Color(0xFFFFB830),
                if (ui.tfeScore > 0) "Best score: ${ui.tfeScore}" else "Swipe to play",
            ) { hub.navigate(HubScreen.TwentyFortyEight) }
            Spacer(Modifier.height(12.dp))
            // Bug fix (audit #1) — Exploding Kittens was fully implemented
            // (AI opponent, pass-and-play, LAN multiplayer) but had no card
            // anywhere in the UI, making it completely unreachable.
            GameCard("💣", "EXPLODING KITTENS", "Don't draw the kitten",
                "Play action cards, then draw. Avoid the Exploding Kitten or defuse it in time. AI, pass-and-play, and WiFi multiplayer.",
                Color(0xFFE94560),
                if (ui.ekWins > 0) "${ui.ekWins} wins" else "Last player standing wins",
            ) { hub.navigate(HubScreen.ExplodingKittens) }

            Spacer(Modifier.height(20.dp))

            // ── WORD & MEMORY ─────────────────────────────────────────────────
            SectionDivider("WORD & MEMORY", Color(0xFF60A5FA))
            Spacer(Modifier.height(12.dp))

            GameCard("📝", "WORDLE", "Guess the 5-letter word",
                "6 tries to guess the hidden word. Green = right spot, yellow = wrong spot. Daily + free play.",
                Color(0xFF81C784),
                if (ui.wordleWins > 0) "${ui.wordleWins} words guessed" else "Can you guess it?",
            ) { hub.navigate(HubScreen.Wordle) }
            Spacer(Modifier.height(12.dp))
            GameCard("👁", "SIMON SAYS", "Memory flash sequence",
                "Watch the growing sequence of flashing cells, then repeat it. Gets faster every 5 levels.",
                Color(0xFFFFD060),
                if (ui.simonBest > 0) "Best: level ${ui.simonBest}" else "Test your memory",
            ) { hub.navigate(HubScreen.Simon) }

            Spacer(Modifier.height(20.dp))

            // ── SPATIAL ───────────────────────────────────────────────────────
            SectionDivider("SPATIAL", t.accent)
            Spacer(Modifier.height(12.dp))

            GameCard("📦", "SOKOBAN", "Push boxes onto targets",
                "Plan ahead to push every box onto its goal. Undo any mistake. 50 handcrafted levels.",
                Color(0xFFFFB830),
                if (ui.sokobanLevel > 1) "Reached level ${ui.sokobanLevel}" else "50 levels to solve",
            ) { hub.navigate(HubScreen.Sokoban) }
            Spacer(Modifier.height(12.dp))
            GameCard("🧩", "SLIDING PUZZLE", "Slide tiles into order",
                "Arrange numbered tiles by sliding into the empty gap. 3×3 to 5×5. Spring-animated.",
                Color(0xFF34D399),
                if (ui.slidingWins > 0) "${ui.slidingWins} puzzles solved" else "Classic 15-puzzle",
            ) { hub.navigate(HubScreen.SlidingPuzzle) }
            Spacer(Modifier.height(12.dp))
            GameCard("💣", "MINESWEEPER", "Clear the hex minefield",
                "Tap to reveal, long-press to flag. Safe-first-tap guarantee. Hexagonal grid. 4 difficulties.",
                Color(0xFFE94560),
                if (ui.mineWins > 0) "${ui.mineWins} boards cleared" else "Don't hit a mine!",
            ) { hub.navigate(HubScreen.Minesweeper) }

            Spacer(Modifier.height(20.dp))

            // Bottom nav
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick  = { hub.navigate(HubScreen.AchievementsScreen) },
                    border   = BorderStroke(1.dp, t.warning.copy(.4f)),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = t.warning),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("🏆", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("${ui.unlockedIds.size} / 44",
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick  = { hub.navigate(HubScreen.Settings) },
                    border   = BorderStroke(1.dp, t.border),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = t.textSecondary),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        val newAch by hub.ui.collectAsStateWithLifecycle()
        AchievementToast(
            achievement = newAch.newAchievement,
            onDismiss   = hub::dismissAchievement,
            modifier    = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        )
    }
}

@Composable
private fun SectionDivider(label: String, color: Color) {
    val t = LocalGameTheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(color.copy(.3f)))
        Text("  $label  ", color = color.copy(.7f), fontSize = 9.sp,
            letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f).height(1.dp).background(color.copy(.3f)))
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
            Surface(color = accentColor.copy(.12f), shape = RoundedCornerShape(6.dp)) {
                Text(stat, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}
