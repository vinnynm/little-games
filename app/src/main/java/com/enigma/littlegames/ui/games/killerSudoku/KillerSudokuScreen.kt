package com.enigma.littlegames.ui.games.killerSudoku

// ─────────────────────────────────────────────────────────────────────────────
// Killer Sudoku Screen — Phase 2: particles on complete, sound effects,
// achievement reporting via HubViewModel
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.domain.rememberParticleSystem
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.common.GameTheme
import com.enigma.littlegames.common.GameTopBar
import com.enigma.littlegames.common.HubScreen
import com.enigma.littlegames.common.HubViewModel
import com.enigma.littlegames.common.LocalGameTheme
import com.enigma.littlegames.common.MiniStat
import com.enigma.littlegames.common.ParticleOverlay
import com.enigma.littlegames.common.ThemedButton
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.text.format

// 18 subtle cage background colours
val CAGE_COLORS = listOf(
    Color(0xFF1E3A2E), Color(0xFF1A2E40), Color(0xFF2E2A1A), Color(0xFF2A1A2E),
    Color(0xFF1A2A2E), Color(0xFF2A2E1A), Color(0xFF2E1A1A), Color(0xFF1A2E2A),
    Color(0xFF2A1A1A), Color(0xFF1A1A2E), Color(0xFF2E2E1A), Color(0xFF1A2E1A),
    Color(0xFF2E1A2E), Color(0xFF1A1A1A), Color(0xFF2A2A2A), Color(0xFF1E2A1E),
    Color(0xFF2E1E2A), Color(0xFF1E2E2A),
)

@Composable
fun KillerSudokuScreen(hub: HubViewModel) {
    val t      = LocalGameTheme.current
    val vm: KillerSudokuViewModel = viewModel()
    val state  by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    var gridCenter by remember { mutableStateOf(Offset(400f, 500f)) }

    // Report win + particles
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            hub.recordSudokuWin(
                difficulty  = state.difficulty.label,
                errorCount  = state.errorCount,
                elapsedSecs = state.elapsedSecs,
            )
            hub.sound.play(Sfx.VICTORY)
            particles.burst(
                center = gridCenter,
                colors = listOf(Color(0xFF4ECCA3), Color(0xFFFFB830), t.primary, Color.White),
                count  = 65, speed = 0.4f,
            )
        }
    }

    // Play error sound when error count increases
    LaunchedEffect(state.errorCount) {
        if (state.errorCount > 0 && !state.isComplete) {
            hub.sound.play(Sfx.SUDOKU_ERROR)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().background(t.background).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val timer = remember(state.elapsedSecs) {
                "%02d:%02d".format(state.elapsedSecs / 60, state.elapsedSecs % 60)
            }

            GameTopBar(
                title    = "KILLER SUDOKU",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text(timer, color = t.primary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                }
            )

            // Stats
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("ERRORS",  "${state.errorCount}")
                MiniStat("FILLED",  "${state.board.sumOf { r -> r.count { it.value != 0 } }}/81")
                MiniStat("NOTES",   if (state.noteMode) "ON" else "OFF")
            }

            // Completion banner
            AnimatedVisibility(state.isComplete) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = t.success.copy(.15f), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.success.copy(.5f))
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("🎉  PUZZLE COMPLETE!", color = t.success,
                            fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("  ·  ${state.errorCount} errors  ·  $timer",
                            color = t.textSecondary, fontSize = 11.sp)
                    }
                }
            }

            // Grid
            if (state.generating) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = t.primary)
                }
            } else if (state.cages.isNotEmpty()) {
                val cellToCage = remember(state.cages) {
                    buildMap<Pair<Int,Int>, Int> {
                        state.cages.forEach { cage -> cage.cells.forEach { put(it, cage.id) } }
                    }
                }
                val cageById = remember(state.cages) { state.cages.associateBy { it.id } }

                BoxWithConstraints(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .border(2.dp, t.primary.copy(.7f), RoundedCornerShape(4.dp))
                        .onGloballyPositioned { c ->
                            gridCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                        }
                ) {
                    Column(Modifier.fillMaxSize()) {
                        for (row in 0..8) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (col in 0..8) {
                                    val cell        = state.board[row][col]
                                    val cage        = cageById[cellToCage[row to col]]
                                    val isSel       = state.selected == row to col
                                    val isHi        = state.selected?.let { (sr,sc) ->
                                        sr == row || sc == col || (sr/3 == row/3 && sc/3 == col/3)
                                    } ?: false
                                    val isSameVal   = state.selected?.let { (sr,sc) ->
                                        state.board[sr][sc].value != 0 && state.board[sr][sc].value == cell.value
                                    } ?: false
                                    val isErr       = (row to col) in state.errors
                                    val showSum     = cage != null &&
                                        cage.cells.minWithOrNull(compareBy({ it.first }, { it.second })) == row to col

                                    SKCellView(row, col, cell, cage, isSel, isHi, isSameVal, isErr,
                                        showSum, state.difficulty, t,
                                        Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        hub.sound.play(Sfx.TAP)
                                        vm.select(row, col)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                // Notes toggle
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (state.noteMode) t.primary.copy(.2f) else t.surface)
                        .border(1.dp, if (state.noteMode) t.primary else t.border, RoundedCornerShape(8.dp))
                        .clickable { hub.sound.play(Sfx.TAP); vm.toggleNotes() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("✏️  Notes", color = if (state.noteMode) t.primary else t.textSecondary, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(t.surface)
                        .border(1.dp, t.border, RoundedCornerShape(8.dp))
                        .clickable { hub.sound.play(Sfx.TAP); vm.place(0) },
                        contentAlignment = Alignment.Center) { Text("⌫", fontSize = 16.sp) }
                    ThemedButton("Solve", { hub.sound.play(Sfx.TAP); vm.solve() }, outlined = true)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Number pad
            val counts = IntArray(10).also { arr ->
                state.board.forEach { row -> row.forEach { if (it.value != 0) arr[it.value]++ } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (n in 1..9) {
                    val rem    = 9 - counts[n]
                    val active = state.selected?.let { (r,c) -> state.board[r][c].value == n } ?: false
                    SKNumBtn(n, rem, active, t) {
                        hub.sound.play(if (rem > 0) Sfx.SUDOKU_PLACE else Sfx.SUDOKU_ERROR)
                        vm.place(n)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Difficulty tabs
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SKDifficulty.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                        .background(if (sel) t.primary else Color.Transparent)
                        .clickable { hub.sound.play(Sfx.TAP); vm.newGame(d) }
                        .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center) {
                        Text("${d.emoji} ${d.label.take(3)}",
                            color = if (sel) t.background else t.textSecondary,
                            fontSize = 10.sp, fontWeight = if (sel) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        ParticleOverlay(particles)
    }
}

// ── Cell ─────────────────────────────────────────────────────────────────────

@Composable
fun SKCellView(
    row: Int, col: Int, cell: SKCell, cage: SKCage?,
    isSelected: Boolean, isHighlighted: Boolean, isSameValue: Boolean,
    isError: Boolean, showCageSum: Boolean, d: SKDifficulty, t: GameTheme,
    modifier: Modifier, onClick: () -> Unit,
) {
    val accent = when (d) {
        SKDifficulty.EASY   -> Color(0xFF4ECCA3)
        SKDifficulty.MEDIUM -> Color(0xFFFFB830)
        SKDifficulty.HARD   -> Color(0xFFE94560)
        SKDifficulty.EXPERT -> Color(0xFFB44FE8)
    }
    val cageBg = cage?.let { CAGE_COLORS[it.colorIdx % CAGE_COLORS.size] } ?: Color(0xFF0D1020)
    val bg by animateColorAsState(
        when {
            isSelected    -> accent.copy(.35f)
            isError       -> Color(0xFFE94560).copy(.22f)
            isSameValue   -> accent.copy(.12f)
            isHighlighted -> Color(0xFF0F3460)
            else          -> cageBg
        }, tween(120), label = "sk_bg"
    )
    Box(
        modifier.background(bg).drawBehind {
            val thick = Color.White.copy(.55f); val thin = Color.White.copy(.07f)
            if (col < 8) drawLine(if ((col+1)%3==0) thick else thin, Offset(size.width,0f), Offset(size.width,size.height), if ((col+1)%3==0) 2.5f else 0.6f)
            if (row < 8) drawLine(if ((row+1)%3==0) thick else thin, Offset(0f,size.height), Offset(size.width,size.height), if ((row+1)%3==0) 2.5f else 0.6f)
        }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (showCageSum && cage != null) {
            Box(Modifier.align(Alignment.TopStart)
                .background(accent.copy(.18f), RoundedCornerShape(bottomEnd = 3.dp))
                .padding(horizontal = 2.dp, vertical = 1.dp)) {
                Text("${cage.sum}", color = accent.copy(.95f), fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        when {
            cell.value != 0 -> Text("${cell.value}",
                color = when { isError -> Color(0xFFE94560); cell.isGiven -> accent; else -> Color(0xFFCCD6F6) },
                fontSize = 17.sp, fontWeight = if (cell.isGiven) FontWeight.Black else FontWeight.Medium)
            cell.notes.isNotEmpty() -> Column(Modifier.fillMaxSize().padding(1.dp),
                verticalArrangement = Arrangement.SpaceEvenly) {
                for (nr in 0..2) Row(Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    for (nc in 0..2) {
                        val n = nr*3+nc+1
                        if (n in cell.notes) Text("$n", color = accent.copy(.85f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        else Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ── Number button ─────────────────────────────────────────────────────────────

@Composable
fun SKNumBtn(n: Int, remaining: Int, isActive: Boolean, t: GameTheme, onClick: () -> Unit) {
    val bg  by animateColorAsState(if (isActive) t.primary else t.surfaceVariant, tween(150), label = "nb_bg")
    val txt by animateColorAsState(
        if (isActive) t.background else if (remaining == 0) t.textSecondary else t.textPrimary,
        tween(150), label = "nb_txt"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(34.dp)) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                .border(1.dp, if (remaining == 0) t.border else t.primary.copy(.25f), RoundedCornerShape(8.dp))
                .clickable(enabled = remaining > 0, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Text("$n", color = txt, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        Row(Modifier.padding(top = 2.dp).height(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (remaining == 0) Text("✓", color = t.primary, fontSize = 7.sp)
            else repeat(remaining.coerceAtMost(5)) {
                Box(Modifier.size(2.5.dp).background(
                    if (remaining <= 2) Color(0xFFE94560).copy(.7f) else t.primary.copy(.5f),
                    CircleShape))
            }
        }
    }
}
