package com.enigma.littlegames.ui.games.sudoku

// ─────────────────────────────────────────────────────────────────────────────
// Classic Sudoku Screen — UI polish pass (matching KillerSudoku improvements):
//   • Inner cell grid lines: lower alpha, very subtle
//   • 3×3 box dividers: medium weight, clearly visible
//   • Outer board border: very thick — about 2× box-divider weight
//   • Alternating 3×3 box shading retained for orientation aid
// ─────────────────────────────────────────────────────────────────────────────

import android.annotation.SuppressLint
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import com.enigma.littlegames.common.*
import com.enigma.littlegames.ui.games.killerSudoku.NotesGrid

// ─────────────────────────────────────────────────────────────────────────────
// Line weight constants — mirrors KillerSudokuScreen for visual consistency
// ─────────────────────────────────────────────────────────────────────────────
private const val INNER_LINE_W   = 0.6f
private const val BOX_DIVIDER_W  = 1.8f
private const val OUTER_BORDER_W = 4.0f

private const val INNER_ALPHA    = 0.18f
private const val BOX_ALPHA      = 0.50f

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun SudokuScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: SudokuViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    var gridCenter by remember { mutableStateOf(Offset(400f, 500f)) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            hub.recordSudokuWin(
                difficulty  = "Classic ${state.difficulty.label}",
                errorCount  = state.errorCount,
                elapsedSecs = state.elapsedSecs,
            )
            hub.sound.play(Sfx.VICTORY)
            particles.burst(
                center = gridCenter,
                colors = listOf(t.primary, t.secondary, Color.White, t.accent),
                count  = 60, speed = 0.4f,
            )
        }
    }

    LaunchedEffect(state.errorCount) {
        if (state.errorCount > 0 && !state.isComplete) hub.sound.play(Sfx.SUDOKU_ERROR)
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
                title    = "SUDOKU",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text(timer, color = t.primary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                }
            )

            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("ERRORS",  "${state.errorCount}")
                MiniStat("FILLED",  "${state.board.sumOf { r -> r.count { it.value != 0 } }}/81")
                MiniStat("NOTES",   if (state.noteMode) "ON" else "OFF")
            }

            AnimatedVisibility(state.isComplete) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color  = t.success.copy(.15f), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.success.copy(.5f))
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("🎉  SOLVED!", color = t.success, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("  ·  ${state.errorCount} errors  ·  $timer", color = t.textSecondary, fontSize = 11.sp)
                    }
                }
            }

            // ── Grid ─────────────────────────────────────────────────────────
            if (state.generating) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = t.primary)
                }
            } else {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .onGloballyPositioned { c ->
                            gridCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                        }
                ) {
                    // ── PASS 1: cell backgrounds ──────────────────────────────
                    Column(Modifier.fillMaxSize()) {
                        for (row in 0..8) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (col in 0..8) {
                                    val cell      = state.board[row][col]
                                    val isSel     = state.selected == row to col
                                    val isHi      = state.selected?.let { (sr, sc) ->
                                        sr == row || sc == col || (sr/3 == row/3 && sc/3 == col/3)
                                    } ?: false
                                    val isSameVal = state.selected?.let { (sr, sc) ->
                                        state.board[sr][sc].value != 0 && state.board[sr][sc].value == cell.value
                                    } ?: false
                                    val isErr     = (row to col) in state.errors
                                    // Alternate 3×3 box shade for orientation
                                    val boxShade  = ((row / 3) + (col / 3)) % 2 == 0

                                    ClassicSudokuCell(
                                        row, col, cell, boxShade, isSel, isHi, isSameVal, isErr, t,
                                        Modifier.weight(1f).fillMaxHeight()
                                    ) { hub.sound.play(Sfx.TAP); vm.select(row, col) }
                                }
                            }
                        }
                    }

                    // ── PASS 2: Canvas overlay — all grid structure on top ────
                    Canvas(Modifier.fillMaxSize()) {
                        drawSudokuInnerLines(size)
                        drawSudokuBoxDividers(size)
                        drawSudokuOuterBorder(size)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Controls
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (state.noteMode) t.primary.copy(.2f) else t.surface)
                            .border(1.dp, if (state.noteMode) t.primary else t.border, RoundedCornerShape(8.dp))
                            .clickable { hub.sound.play(Sfx.TAP); vm.toggleNotes() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text("✏️ Notes", color = if (state.noteMode) t.primary else t.textSecondary, fontSize = 13.sp) }

                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(t.surface)
                            .border(1.dp, t.border, RoundedCornerShape(8.dp))
                            .clickable { hub.sound.play(Sfx.TAP); vm.hint() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text("💡 Hint", color = t.textSecondary, fontSize = 13.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(t.surface)
                            .border(1.dp, t.border, RoundedCornerShape(8.dp))
                            .clickable { hub.sound.play(Sfx.TAP); vm.place(0) },
                        contentAlignment = Alignment.Center
                    ) { Text("⌫", fontSize = 18.sp) }
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
                    val active = state.selected?.let { (r, c) -> state.board[r][c].value == n } ?: false
                    SudokuNumBtn(n, rem, active, t) {
                        hub.sound.play(if (rem > 0) Sfx.SUDOKU_PLACE else Sfx.SUDOKU_ERROR)
                        vm.place(n)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Difficulty tabs
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SudokuDifficulty.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                            .background(if (sel) t.primary else Color.Transparent)
                            .clickable { hub.sound.play(Sfx.TAP); vm.newGame(d) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${d.emoji} ${d.label.take(3)}",
                            color     = if (sel) t.background else t.textSecondary,
                            fontSize  = 10.sp,
                            fontWeight = if (sel) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas drawing helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Thin lines between individual cells (skips box-divider positions). */
private fun DrawScope.drawSudokuInnerLines(canvasSize: Size) {
    val cellPx = canvasSize.width / 9f
    val color  = Color.White.copy(alpha = INNER_ALPHA)
    for (i in 1..8) {
        if (i % 3 == 0) continue
        val x = i * cellPx
        drawLine(color, Offset(x, 0f), Offset(x, canvasSize.height), INNER_LINE_W)
        val y = i * cellPx
        drawLine(color, Offset(0f, y), Offset(canvasSize.width, y), INNER_LINE_W)
    }
}

/** Medium-weight 3×3 box dividers — drawn after inner lines. */
private fun DrawScope.drawSudokuBoxDividers(canvasSize: Size) {
    val cellPx = canvasSize.width / 9f
    val color  = Color.White.copy(alpha = BOX_ALPHA)
    for (i in listOf(3, 6)) {
        val x = i * cellPx
        drawLine(color, Offset(x, 0f), Offset(x, canvasSize.height), BOX_DIVIDER_W)
        val y = i * cellPx
        drawLine(color, Offset(0f, y), Offset(canvasSize.width, y), BOX_DIVIDER_W)
    }
}

/** Very thick outer border — drawn last, fully on top. */
private fun DrawScope.drawSudokuOuterBorder(canvasSize: Size) {
    val half  = OUTER_BORDER_W / 2f
    val color = Color.White.copy(alpha = 0.80f)
    val rect  = androidx.compose.ui.geometry.Rect(half, half, canvasSize.width - half, canvasSize.height - half)
    drawRect(color, topLeft = rect.topLeft, size = rect.size,
        style = Stroke(width = OUTER_BORDER_W))
}

// ─────────────────────────────────────────────────────────────────────────────
// Cell composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClassicSudokuCell(
    row: Int, col: Int, cell: SudokuCell,
    boxShade: Boolean,
    isSelected: Boolean, isHighlighted: Boolean, isSameValue: Boolean,
    isError: Boolean, t: GameTheme,
    modifier: Modifier, onClick: () -> Unit,
) {
    // Subtle alternating box shade — slightly lighter than background so boxes
    // are distinguishable without interfering with selection highlights
    val baseBg = if (boxShade) t.surfaceVariant else t.surface
    val bg by animateColorAsState(
        when {
            isSelected    -> t.primary.copy(.35f)
            isError       -> Color(0xFFE94560).copy(.22f)
            isSameValue   -> t.primary.copy(.12f)
            isHighlighted -> t.primary.copy(.08f)
            else          -> baseBg
        }, tween(100), label = "s_bg"
    )

    // No grid lines drawn in the cell — all handled by Canvas overlay
    Box(
        modifier
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            cell.value != 0 -> Text(
                "${cell.value}",
                color = when {
                    isError      -> Color(0xFFE94560)
                    cell.isGiven -> t.primary
                    else         -> t.textPrimary
                },
                fontSize   = 18.sp,
                fontWeight = if (cell.isGiven) FontWeight.Black else FontWeight.Medium
            )
            cell.notes.isNotEmpty() -> Column(
                Modifier.fillMaxSize().padding(1.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                NotesGrid(
                   notes = cell.notes.toList(),
                    textColor =  t.primary.copy(.85f)
                )

            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Number pad button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SudokuNumBtn(n: Int, remaining: Int, isActive: Boolean, t: GameTheme, onClick: () -> Unit) {
    val bg  by animateColorAsState(if (isActive) t.primary else t.surfaceVariant, tween(150), label = "sn_bg")
    val txt by animateColorAsState(
        if (isActive) t.background else if (remaining == 0) t.textSecondary else t.textPrimary,
        tween(150), label = "sn_txt"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                .border(1.dp, if (remaining == 0) t.border else t.primary.copy(.25f), RoundedCornerShape(8.dp))
                .clickable(enabled = remaining > 0, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Text("$n", color = txt, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        Row(Modifier.padding(top = 2.dp).height(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (remaining == 0) Text("✓", color = t.primary, fontSize = 7.sp)
            else repeat(remaining.coerceAtMost(5)) {
                Box(
                    Modifier.size(2.5.dp).background(
                        if (remaining <= 2) Color(0xFFE94560).copy(.7f) else t.primary.copy(.5f),
                        CircleShape
                    )
                )
            }
        }
    }
}
