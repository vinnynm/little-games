package com.enigma.littlegames.ui.games.kakuro

// ─────────────────────────────────────────────────────────────────────────────
// Kakuro Screen — clue-cell rendering fix:
//
//   Kakuro convention (universally used in published puzzles):
//     • The clue cell is always the BLACK cell immediately to the LEFT of a
//       horizontal run, or immediately ABOVE a vertical run.
//     • The diagonal divides the black cell top-left → bottom-right.
//     • The DOWN clue sits in the TOP-RIGHT triangle  → it applies to the
//       run that starts on the row BELOW this cell (going downward).
//     • The RIGHT (across) clue sits in the BOTTOM-LEFT triangle → it applies
//       to the run that starts in the column to the RIGHT of this cell.
//
//   Previous bug: both triangles were drawn but text was mis-positioned so
//   the down clue appeared where the across clue should be, and vice-versa.
//   The fix below positions text precisely within each triangle half.
//
//   Additionally the grid line hierarchy now matches the Sudoku screens:
//     • Thin inner lines, medium outer-cell borders, thick board outline.
// ─────────────────────────────────────────────────────────────────────────────

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import com.enigma.littlegames.common.*

private const val OUTER_BORDER_W = 3.5f
private const val CELL_LINE_W    = 0.5f
private const val CELL_LINE_A    = 0.20f

@Composable
fun KakuroScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: KakuroViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    var gridCenter by remember { mutableStateOf(Offset(400f, 400f)) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            hub.recordSudokuWin(
                difficulty  = "Kakuro ${state.difficulty.label}",
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
                title    = "KAKURO",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text(timer, color = t.primary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                }
            )

            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("ERRORS", "${state.errorCount}")
                val whiteFilled = state.cells.sumOf { row ->
                    row.count { cell -> cell is KakuroCell.White && cell.value != 0 }
                }
                val whiteTotal = state.cells.sumOf { row ->
                    row.count { cell -> cell is KakuroCell.White }
                }
                MiniStat("FILLED", "$whiteFilled/$whiteTotal")
                MiniStat("TIME",   timer)
            }

            AnimatedVisibility(state.isComplete) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color  = t.success.copy(.15f), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.success.copy(.5f))
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("🎉  KAKURO SOLVED!", color = t.success, fontWeight = FontWeight.Black, fontSize = 15.sp)
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
                    val cellSize = maxWidth / state.size

                    Column(Modifier.fillMaxSize()) {
                        for (row in 0 until state.size) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (col in 0 until state.size) {
                                    val cell   = state.cells[row][col]
                                    val isSel  = state.selected == row to col
                                    val isErr  = (row to col) in state.errors
                                    val isHi   = state.selected?.let { (sr, sc) ->
                                        state.runs.any { run ->
                                            (sr to sc) in run.cells && (row to col) in run.cells
                                        }
                                    } ?: false

                                    KakuroCellView(
                                        cell, cellSize, isSel, isHi, isErr, t,
                                        Modifier.weight(1f).fillMaxHeight()
                                    ) { vm.select(row, col) }
                                }
                            }
                        }
                    }

                    // Outer border on top
                    Canvas(Modifier.fillMaxSize()) {
                        drawKakuroOuterBorder(size)
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
                Text(
                    "Fill each run with unique digits\nthat sum to the clue shown.",
                    color    = t.textSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(t.surface)
                            .border(1.dp, t.border, RoundedCornerShape(8.dp))
                            .clickable { hub.sound.play(Sfx.TAP); vm.erase() },
                        contentAlignment = Alignment.Center
                    ) { Text("⌫", fontSize = 18.sp) }
                    ThemedButton("Solve", { hub.sound.play(Sfx.TAP); vm.solve() }, outlined = true)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Number pad
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (n in 1..9) {
                    KakuroNumBtn(n, t) { hub.sound.play(Sfx.SUDOKU_PLACE); vm.place(n) }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Difficulty tabs
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                KakuroDifficulty.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                            .background(if (sel) t.primary else Color.Transparent)
                            .clickable { hub.sound.play(Sfx.TAP); vm.newGame(d) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${d.emoji} ${d.label.take(3)}",
                            color      = if (sel) t.background else t.textSecondary,
                            fontSize   = 10.sp,
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
// Cell composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KakuroCellView(
    cell: KakuroCell,
    size: Dp,
    isSelected: Boolean,
    isHighlighted: Boolean,
    isError: Boolean,
    t: GameTheme,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    when (cell) {
        is KakuroCell.Clue -> {
            // Black clue cell — draw diagonal + clues entirely in Canvas
            Box(
                modifier
                    .background(Color(0xFF080A10))
                    .border(0.5.dp, t.border.copy(.25f))
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawKakuroClueCell(cell.down, cell.right, t.primary)
                }
            }
        }
        is KakuroCell.White -> {
            val bg by animateColorAsState(
                when {
                    isSelected    -> t.primary.copy(.35f)
                    isError       -> Color(0xFFE94560).copy(.22f)
                    isHighlighted -> t.primary.copy(.10f)
                    else          -> t.surface
                }, tween(100), label = "kk_bg"
            )
            Box(
                modifier
                    .background(bg)
                    .border(0.5.dp, Color.White.copy(CELL_LINE_A))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (cell.value != 0) {
                    Text(
                        "${cell.value}",
                        color = when {
                            isError -> Color(0xFFE94560)
                            else    -> if (isSelected) t.primary else t.textPrimary
                        },
                        fontSize   = (size.value * 0.38f).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kakuro clue cell drawing
//
//  Layout of a clue cell:
//
//    ┌──────────┐
//    │       /dd│   dd = down clue  (top-right triangle)
//    │      /   │   The run goes DOWNWARD from the cell below this one.
//    │     /    │
//    │    /     │
//    │rr /      │   rr = right/across clue (bottom-left triangle)
//    │  /       │   The run goes RIGHTWARD from the cell to the right of this one.
//    └──────────┘
//
//  The diagonal line runs from TOP-LEFT to BOTTOM-RIGHT.
//  Down clue text: right-aligned in the upper-right region.
//  Right clue text: left-aligned in the lower-left region.
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawKakuroClueCell(down: Int?, right: Int?, primary: Color) {
    val w = size.width
    val h = size.height

    // Diagonal line: top-left → bottom-right
    drawLine(
        color       = Color.White.copy(alpha = 0.30f),
        start       = Offset(2f, 2f),
        end         = Offset(w - 2f, h - 2f),
        strokeWidth = 1.0f,
    )

    val canvas = drawContext.canvas.nativeCanvas
    val textSize = h * 0.29f
    val margin   = h * 0.06f   // small padding from edges

    // ── DOWN clue — top-right triangle ───────────────────────────────────────
    // Text sits in the upper-right half, right-aligned near the right edge,
    // and vertically centred in the top half of the cell.
    if (down != null) {
        val paint = Paint().apply {
            color       = primary.copy(alpha = 0.95f).toArgb()
            this.textSize = textSize
            textAlign   = Paint.Align.RIGHT
            isAntiAlias = true
            typeface    = Typeface.DEFAULT_BOLD
        }
        // Baseline sits at ~40% of cell height — firmly in the upper half
        canvas.drawText("$down", w - margin, h * 0.40f, paint)
    }

    // ── RIGHT (across) clue — bottom-left triangle ────────────────────────────
    // Text sits in the lower-left half, left-aligned near the left edge,
    // and vertically centred in the bottom half of the cell.
    if (right != null) {
        val paint = Paint().apply {
            color       = primary.copy(alpha = 0.95f).toArgb()
            this.textSize = textSize
            textAlign   = Paint.Align.LEFT
            isAntiAlias = true
            typeface    = Typeface.DEFAULT_BOLD
        }
        // Baseline at ~88% of cell height — firmly in the lower half
        canvas.drawText("$right", margin, h * 0.88f, paint)
    }
}

/** Thick outer board border drawn on top of all cells. */
private fun DrawScope.drawKakuroOuterBorder(canvasSize: Size) {
    val half  = OUTER_BORDER_W / 2f
    val color = Color.White.copy(alpha = 0.75f)
    val rect  = Rect(half, half, canvasSize.width - half, canvasSize.height - half)
    drawRect(color, topLeft = rect.topLeft, size = rect.size,
        style = Stroke(width = OUTER_BORDER_W))
}

// ─────────────────────────────────────────────────────────────────────────────
// Number pad button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KakuroNumBtn(n: Int, t: GameTheme, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(t.surfaceVariant)
            .border(1.dp, t.primary.copy(.25f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text("$n", color = t.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}
