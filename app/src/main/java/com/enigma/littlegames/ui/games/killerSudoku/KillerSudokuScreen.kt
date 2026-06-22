package com.enigma.littlegames.ui.games.killerSudoku

// ─────────────────────────────────────────────────────────────────────────────
// Killer Sudoku Screen — UI polish pass:
//   • Cage borders: dashed, lower alpha (0.55) — regions readable, not harsh
//   • Inner grid lines: higher alpha (0.20 thin / 0.45 box), lighter tint
//   • 3×3 box dividers: ~1.5× inner-line width, clearly distinct
//   • Outer board border: ~2× box-divider width, very prominent
//   • Cage colours retained; cage min-size ≥ 2 enforced in ViewModel
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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

// 18 subtle cage background colours — kept from original for visual region identity
val CAGE_COLORS = listOf(
    Color(0xFF1A3828), Color(0xFF162A3A), Color(0xFF2A2618), Color(0xFF261828),
    Color(0xFF18262A), Color(0xFF262A18), Color(0xFF2A1818), Color(0xFF182A26),
    Color(0xFF261818), Color(0xFF18182A), Color(0xFF2A2A18), Color(0xFF182A18),
    Color(0xFF2A182A), Color(0xFF1E1E1E), Color(0xFF262626), Color(0xFF1A261A),
    Color(0xFF2A1E26), Color(0xFF1E2A26),
)

// ─────────────────────────────────────────────────────────────────────────────
// Line weight constants — tweak here to adjust the whole visual hierarchy
// ─────────────────────────────────────────────────────────────────────────────
private const val INNER_LINE_W   = 0.6f   // thin grid lines between individual cells
private const val BOX_DIVIDER_W  = 1.8f   // 3×3 box dividers
private const val OUTER_BORDER_W = 4.0f   // outer board border (drawn as Canvas overlay)
private const val CAGE_STROKE_W  = 2.0f   // cage outline stroke

private const val INNER_ALPHA    = 0.18f  // thin cell lines
private const val BOX_ALPHA      = 0.50f  // box divider lines
private const val CAGE_ALPHA     = 0.55f  // cage outlines (dashed)

@Composable
fun KillerSudokuScreen(hub: HubViewModel) {
    val t      = LocalGameTheme.current
    val vm: KillerSudokuViewModel = viewModel()
    val state  by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    var gridCenter by remember { mutableStateOf(Offset(400f, 500f)) }

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
                title    = "KILLER SUDOKU",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text(timer, color = t.primary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                }
            )

            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniStat("ERRORS",  "${state.errorCount}")
                MiniStat("FILLED",  "${state.board.sumOf { r -> r.count { it.value != 0 } }}/81")
                MiniStat("NOTES",   if (state.noteMode) "ON" else "OFF")
            }

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

            // ── Grid ─────────────────────────────────────────────────────────
            if (state.generating) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = t.primary)
                }
            } else if (state.cages.isNotEmpty()) {
                val cageById   = remember(state.cages) { state.cages.associateBy { it.id } }
                val cellToCage = remember(state.cages) {
                    buildMap<Pair<Int,Int>, Int> {
                        state.cages.forEach { cage -> cage.cells.forEach { put(it, cage.id) } }
                    }
                }

                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .onGloballyPositioned { c ->
                            gridCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                        }
                ) {
                    val cellPx = with(LocalDensity.current) { (maxWidth / 9).toPx() }

                    // ── PASS 1: cell backgrounds ──────────────────────────────
                    Column(Modifier.fillMaxSize()) {
                        for (row in 0..8) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (col in 0..8) {
                                    val cell      = state.board[row][col]
                                    val cage      = cageById[cellToCage[row to col]]
                                    val isSel     = state.selected == row to col
                                    val isHi      = state.selected?.let { (sr, sc) ->
                                        sr == row || sc == col || (sr/3 == row/3 && sc/3 == col/3)
                                    } ?: false
                                    val isSameVal = state.selected?.let { (sr, sc) ->
                                        state.board[sr][sc].value != 0 && state.board[sr][sc].value == cell.value
                                    } ?: false
                                    val isErr     = (row to col) in state.errors
                                    val showSum   = cage != null &&
                                            cage.cells.minWithOrNull(compareBy({ it.first }, { it.second })) == row to col

                                    SKCellView(
                                        row, col, cell, cage, isSel, isHi, isSameVal, isErr,
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

                    // ── PASS 2: Canvas overlay — grid lines + cage borders ────
                    Canvas(Modifier.fillMaxSize()) {
                        // Draw inner cell grid lines first (lowest layer)
                        drawInnerGridLines(size, t.primary)
                        // Then cage dashed outlines
                        drawCageOutlines(state.cageBorders, cellPx, t.primary)
                        // Then box dividers on top of cages
                        drawBoxDividers(size, t.primary)
                        // Outer border on top of everything
                        drawOuterBorder(size, t.primary)
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
                    SKNumBtn(n, rem, active, t) {
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
                SKDifficulty.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                            .background(if (sel) t.primary else Color.Transparent)
                            .clickable { hub.sound.play(Sfx.TAP); vm.newGame(d) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${d.emoji} ${d.label.take(3)}",
                            color = if (sel) t.background else t.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (sel) FontWeight.Black else FontWeight.Normal
                        )
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

/**
 * Thin inner cell-to-cell lines — very subtle, just enough to delineate cells.
 * Drawn BEFORE cage outlines so cages always read on top.
 */
private fun DrawScope.drawInnerGridLines(canvasSize: Size, primary: Color) {
    val cellPx = canvasSize.width / 9f
    val color  = Color.White.copy(alpha = INNER_ALPHA)

    for (i in 1..8) {
        if (i % 3 == 0) continue   // box dividers handled separately
        val x = i * cellPx
        drawLine(color, Offset(x, 0f), Offset(x, canvasSize.height), INNER_LINE_W)
        val y = i * cellPx
        drawLine(color, Offset(0f, y), Offset(canvasSize.width, y), INNER_LINE_W)
    }
}

/**
 * Dashed cage outlines — drawn after inner grid lines, before box dividers.
 * Lower alpha keeps them readable without overwhelming the number content.
 */
private fun DrawScope.drawCageOutlines(borders: CageBorders, cellPx: Float, primary: Color) {
    val color  = Color.White.copy(alpha = CAGE_ALPHA)
    // Dash pattern: 4px on, 3px off — classic KS dashed look
    val dash   = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
    val stroke = Stroke(width = CAGE_STROKE_W, pathEffect = dash)
    val inset  = cellPx * 0.05f

    borders.forEach { (cell, sides) ->
        val (row, col) = cell
        val left   = col * cellPx + inset
        val top    = row * cellPx + inset
        val right  = left  + cellPx - inset * 2
        val bottom = top   + cellPx - inset * 2

        val path = Path()
        if (CageSide.TOP    in sides) { path.moveTo(left, top);    path.lineTo(right, top) }
        if (CageSide.BOTTOM in sides) { path.moveTo(left, bottom); path.lineTo(right, bottom) }
        if (CageSide.LEFT   in sides) { path.moveTo(left, top);    path.lineTo(left, bottom) }
        if (CageSide.RIGHT  in sides) { path.moveTo(right, top);   path.lineTo(right, bottom) }
        drawPath(path, color, style = stroke)
    }
}

/**
 * 3×3 box dividers — medium weight, clearly separating the nine boxes.
 * Drawn after cage outlines so they're always visible.
 */
private fun DrawScope.drawBoxDividers(canvasSize: Size, primary: Color) {
    val cellPx = canvasSize.width / 9f
    val color  = Color.White.copy(alpha = BOX_ALPHA)

    for (i in listOf(3, 6)) {
        val x = i * cellPx
        drawLine(color, Offset(x, 0f), Offset(x, canvasSize.height), BOX_DIVIDER_W)
        val y = i * cellPx
        drawLine(color, Offset(0f, y), Offset(canvasSize.width, y), BOX_DIVIDER_W)
    }
}

/**
 * Very thick outer border — drawn last so nothing obscures it.
 * Inset by half its own stroke width so it stays inside the composable bounds.
 */
private fun DrawScope.drawOuterBorder(canvasSize: Size, primary: Color) {
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

    // No grid lines drawn here — all lines are handled in the Canvas overlay
    Box(
        modifier
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Cage sum label in top-left corner of the first cell of each cage
        if (showCageSum && cage != null) {
            Box(
                Modifier.align(Alignment.TopStart)
                    .background(accent.copy(.18f), RoundedCornerShape(bottomEnd = 3.dp))
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                Text("${cage.sum}", color = accent.copy(.95f), fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        when {
            cell.value != 0 -> Text(
                "${cell.value}",
                color = when {
                    isError      -> Color(0xFFE94560)
                    cell.isGiven -> accent
                    else         -> Color(0xFFCCD6F6)
                },
                fontSize = 17.sp,
                fontWeight = if (cell.isGiven) FontWeight.Black else FontWeight.Medium
            )
            cell.notes.isNotEmpty() -> Column(
                Modifier.fillMaxSize().padding(1.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                for (nr in 0..2) Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (nc in 0..2) {
                        val n = nr * 3 + nc + 1
                        if (n in cell.notes)
                            Text("$n", color = accent.copy(.85f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        else
                            Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Number pad button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SKNumBtn(n: Int, remaining: Int, isActive: Boolean, t: GameTheme, onClick: () -> Unit) {
    val bg  by animateColorAsState(if (isActive) t.primary else t.surfaceVariant, tween(150), label = "nb_bg")
    val txt by animateColorAsState(
        if (isActive) t.background else if (remaining == 0) t.textSecondary else t.textPrimary,
        tween(150), label = "nb_txt"
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
