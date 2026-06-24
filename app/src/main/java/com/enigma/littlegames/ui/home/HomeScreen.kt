package com.enigma.littlegames.ui.home

// ─────────────────────────────────────────────────────────────────────────────
// Home Screen — Phase 4a
// 8 game cards organised into three sections:
//   • Logic Puzzles  (Lights Out, Pipe Flow, Killer Sudoku, Sudoku, Kakuro)
//   • Card & Memory  (Exploding Kittens, Simon Says)
//   • Arcade         (2048)
// Stats bar expanded to show EK wins, Simon best, 2048 best.
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
import com.enigma.littlegames.common.*

@Composable
fun HomeScreen(hub: HubViewModel) {
    val t  = LocalGameTheme.current
    val ui by hub.ui.collectAsStateWithLifecycle()

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

            // Logo
            Text("ENIGMA", fontSize = 36.sp, fontWeight = FontWeight.Black,
                letterSpacing = 8.sp, color = t.primary.copy(alpha = glowAlpha))
            Text("GAME HUB", fontSize = 13.sp, letterSpacing = 6.sp,
                color = t.textSecondary, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(22.dp))

            // ── Stats grid ────────────────────────────────────────────────────
            Surface(
                color = t.surface, shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, t.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MiniStat("LO BEST",  ui.loBestMoves?.let { "$it mv" } ?: "—")
                        MiniStat("PIPE LVL", "${ui.pipeLevel}/24")
                        MiniStat("PUZZLES",  "${ui.sudokuWins} ✓")
                    }
                    HorizontalDivider(color = t.border, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MiniStat("EK WINS",  "${ui.ekWins}")
                        MiniStat("SIMON",    if (ui.simonBest > 0) "Seq ${ui.simonBest}" else "—")
                        MiniStat("2048 BEST",if (ui.tfeBest > 0) "${ui.tfeBest}" else "—")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Section: Logic Puzzles ────────────────────────────────────────
            SectionHeader("🧩  LOGIC PUZZLES", t)

            GameCard(
                emoji = "💡", title = "LIGHTS OUT", subtitle = "Toggle all 25 lights off",
                description = "Tap a cell to flip it and its 4 neighbours. GF(2) matrix solver guarantees solvability. Four difficulties, hint system, best-score tracking.",
                accentColor = Color(0xFFFFD060),
                stat = ui.loBestMoves?.let { "Best: $it moves" } ?: "Not played yet",
                onClick = { hub.navigate(HubScreen.LightsOut) }
            )
            Spacer(Modifier.height(10.dp))

            GameCard(
                emoji = "🔧", title = "PIPE FLOW", subtitle = "Connect inlet to outlet",
                description = "Rotate pipes to seal the circuit. 24-level campaign with rocks, locked pipes, 4-way X junctions, par scoring, and auto-solve hint.",
                accentColor = t.primary,
                stat = "Level ${ui.pipeLevel}/24  ·  ⭐ ${ui.pipeStars}",
                onClick = { hub.navigate(HubScreen.PipeFlow) }
            )
            Spacer(Modifier.height(10.dp))

            GameCard(
                emoji = "🔢", title = "KILLER SUDOKU", subtitle = "Sums inside cages",
                description = "Procedurally generated 9×9 with cage constraints. Min cage size 2. Easy → Expert. Note mode, error highlighting, hint solver.",
                accentColor = Color(0xFF4ECCA3),
                stat = "${ui.sudokuWins} puzzles solved",
                onClick = { hub.navigate(HubScreen.KillerSudoku) }
            )
            Spacer(Modifier.height(10.dp))

            GameCard(
                emoji = "9️⃣", title = "SUDOKU", subtitle = "Classic 9×9 logic puzzle",
                description = "The timeless number placement puzzle. Four difficulty levels from Easy (42 givens) to Expert (20 givens). Note mode, hint, error detection.",
                accentColor = Color(0xFF60A5FA),
                stat = "Classic mode  ·  ${ui.sudokuWins} puzzles solved",
                onClick = { hub.navigate(HubScreen.Sudoku) }
            )
            Spacer(Modifier.height(10.dp))

            GameCard(
                emoji = "➕", title = "KAKURO", subtitle = "Number crossword",
                description = "Fill white cells with 1–9 so every run sums to its clue. No digit repeats in a run. Four grid sizes from Easy 5×7 to Expert 11×11.",
                accentColor = Color(0xFFA78BFA),
                stat = "Number crossword  ·  ${ui.sudokuWins} puzzles solved",
                onClick = { hub.navigate(HubScreen.Kakuro) }
            )

            Spacer(Modifier.height(22.dp))

            // ── Section: Card & Memory ────────────────────────────────────────
            SectionHeader("🃏  CARD & MEMORY", t)

            GameCard(
                emoji = "💣", title = "EXPLODING KITTENS", subtitle = "Don't draw the kitten!",
                description = "Play action cards, avoid Exploding Kittens, and be the last player standing. Single player vs AI (Easy/Medium/Hard), pass-and-play, and WiFi multiplayer.",
                accentColor = Color(0xFFFF4500),
                stat = if (ui.ekWins > 0) "${ui.ekWins} wins" else "Not played yet",
                onClick = { hub.navigate(HubScreen.ExplodingKittens) }
            )
            Spacer(Modifier.height(10.dp))

            GameCard(
                emoji = "🧠", title = "SIMON SAYS", subtitle = "Remember the sequence",
                description = "Watch the grid light up in sequence, then reproduce it perfectly. Sequence grows every round. Three modes: Mini (3×3), Classic (5×5), Speed.",
                accentColor = Color(0xFF34D399),
                stat = if (ui.simonBest > 0) "Best sequence: ${ui.simonBest}" else "Not played yet",
                onClick = { hub.navigate(HubScreen.Simon) }
            )

            Spacer(Modifier.height(22.dp))

            // ── Section: Arcade ───────────────────────────────────────────────
            SectionHeader("🕹️  ARCADE", t)

            GameCard(
                emoji = "2️⃣", title = "2048", subtitle = "Merge tiles to reach 2048",
                description = "Swipe to slide all tiles. Matching tiles merge into their sum. Reach the 2048 tile to win — or keep going for a higher score!",
                accentColor = Color(0xFFFFD700),
                stat = if (ui.tfeBest > 0) "Best score: ${ui.tfeBest}" else "Not played yet",
                onClick = { hub.navigate(HubScreen.TwentyFortyEight) }
            )

            Spacer(Modifier.height(20.dp))

            // ── Bottom nav ────────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { hub.navigate(HubScreen.AchievementsScreen) },
                    border  = BorderStroke(1.dp, t.warning.copy(.4f)),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = t.warning),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🏆", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("${ui.unlockedIds.size} / ${com.enigma.littlegames.domain.Achievements.total}",
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

        val newAch by hub.ui.collectAsStateWithLifecycle()
        AchievementToast(
            achievement = newAch.newAchievement,
            onDismiss   = hub::dismissAchievement,
            modifier    = Modifier.align(Alignment.TopCenter).statusBarsPadding()
        )
    }
}

@Composable
private fun SectionHeader(title: String, t: GameTheme) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(Modifier.weight(1f), color = t.border, thickness = 0.5.dp)
        Text(title, color = t.textSecondary, fontSize = 10.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.weight(1f), color = t.border, thickness = 0.5.dp)
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
            .background(t.surface)
            .border(1.dp, t.border, RoundedCornerShape(16.dp))
            .clickable(interactionSource, null, onClick = onClick)
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Accent badge
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(.15f))
                        .border(1.dp, accentColor.copy(.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 26.sp) }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(title, color = accentColor, fontSize = 15.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(subtitle, color = t.textSecondary, fontSize = 11.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = t.textSecondary)
            }

            Spacer(Modifier.height(10.dp))
            Text(description, color = t.textSecondary, fontSize = 11.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(10.dp))

            Surface(color = accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                Text(stat, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}
