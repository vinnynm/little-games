package com.enigma.littlegames.ui.games.kakuro

// ─────────────────────────────────────────────────────────────────────────────
// Kakuro Screen
//
// Clue-cell rendering — standard Kakuro convention:
//
//   The diagonal runs TOP-LEFT → BOTTOM-RIGHT, splitting the black cell into:
//
//     ┌──────────────┐
//     │▓▓▓▓▓▓▓/ rr  │   TOP-RIGHT triangle
//     │▓▓▓▓▓▓/       │   → RIGHT (across) clue
//     │▓▓▓▓▓/        │   → applies to the horizontal run to the RIGHT
//     │▓▓▓▓/         │
//     │ dd /▓▓▓▓▓▓▓  │   BOTTOM-LEFT triangle
//     │   /▓▓▓▓▓▓▓▓  │   → DOWN clue
//     │  /▓▓▓▓▓▓▓▓▓  │   → applies to the vertical run BELOW
//     └──────────────┘
//
//   This matches every commercially published Kakuro puzzle.
//
// Grid sizing by difficulty (drives aspect-ratio and cell count):
//   Easy    7×7  →  comfortable for beginners
//   Medium  9×9  →  standard Kakuro size
//   Hard   11×11 →  challenging, more constraints
//   Expert 13×13 →  dense, expert-level
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
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .background(t.background)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val timer = remember(state.elapsedSecs) {
                "%02d:%02d".format(state.elapsedSecs / 60, state.elapsedSecs % 60)
            }

            GameTopBar(
                title    = "KAKURO",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}  ·  ${state.size}×${state.size}",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text(
                        timer,
                        color      = t.primary,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(end = 8.dp),
                    )
                }
            )

            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
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
                    color  = t.success.copy(.15f),
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.success.copy(.5f))
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("🎉  KAKURO SOLVED!", color = t.success, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("  ·  ${state.errorCount} errors  ·  $timer", color = t.textSecondary, fontSize = 11.sp)
                    }
                }
            }

            // ── Grid ─────────────────────────────────────────────────────────
            if (state.generating) {
                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = t.primary) }
            } else {
                // The grid is always square; we constrain to the minimum of
                // available width and a reasonable fraction of screen height
                // so larger grids don't overflow on small screens.
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
                                        Modifier.weight(1f).fillMaxHeight(),
                                    ) { vm.select(row, col) }
                                }
                            }
                        }
                    }

                    // Outer border drawn on top of all cells
                    Canvas(Modifier.fillMaxSize()) {
                        drawKakuroOuterBorder(size)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Controls ─────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    "Fill each run with unique digits\nthat sum to the clue shown.",
                    color      = t.textSecondary,
                    fontSize   = 10.sp,
                    lineHeight = 14.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(t.surface)
                            .border(1.dp, t.border, RoundedCornerShape(8.dp))
                            .clickable { hub.sound.play(Sfx.TAP); vm.erase() },
                        contentAlignment = Alignment.Center,
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
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(t.surface)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                KakuroDifficulty.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (sel) t.primary else Color.Transparent)
                            .clickable { hub.sound.play(Sfx.TAP); vm.newGame(d) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${d.emoji} ${d.label.take(3)}",
                            color      = if (sel) t.background else t.textSecondary,
                            fontSize   = 10.sp,
                            fontWeight = if (sel) FontWeight.Black else FontWeight.Normal,
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
            // Black clue cell — diagonal + clue numbers drawn entirely in Canvas
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
                },
                tween(100),
                label = "kk_bg",
            )
            Box(
                modifier
                    .background(bg)
                    .border(0.5.dp, Color.White.copy(CELL_LINE_A))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
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
// Kakuro clue cell drawing — STANDARD CONVENTION
//
//  Diagonal: top-left → bottom-right
//
//  TOP-RIGHT triangle  →  RIGHT (across) clue  →  field: `right`
//    • This cell is to the LEFT of a horizontal run.
//    • Text: right-aligned, upper half of cell.
//
//  BOTTOM-LEFT triangle  →  DOWN clue  →  field: `down`
//    • This cell is ABOVE a vertical run.
//    • Text: left-aligned, lower half of cell.
//
//  Example (cell has both clues, down=14, right=7):
//
//    ┌──────────┐
//    │        7 │   ← right/across clue, top-right
//    │       /  │
//    │      /   │
//    │     /    │
//    │ 14 /     │   ← down clue, bottom-left
//    └──────────┘
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawKakuroClueCell(down: Int?, right: Int?, primary: Color) {
    val w = size.width
    val h = size.height

    // Diagonal: top-left → bottom-right
    drawLine(
        color       = Color.White.copy(alpha = 0.30f),
        start       = Offset(2f, 2f),
        end         = Offset(w - 2f, h - 2f),
        strokeWidth = 1.0f,
    )

    val canvas   = drawContext.canvas.nativeCanvas
    val textSize = h * 0.29f
    val margin   = h * 0.07f

    // ── RIGHT (across) clue — TOP-RIGHT triangle ──────────────────────────────
    // Text sits in the upper-right half.
    // Right-aligned near the right edge, baseline at ~38% of cell height.
    if (right != null) {
        val paint = Paint().apply {
            color       = primary.copy(alpha = 0.95f).toArgb()
            this.textSize = textSize
            textAlign   = Paint.Align.RIGHT
            isAntiAlias = true
            typeface    = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("$right", w - margin, h * 0.38f, paint)
    }

    // ── DOWN clue — BOTTOM-LEFT triangle ─────────────────────────────────────
    // Text sits in the lower-left half.
    // Left-aligned near the left edge, baseline at ~88% of cell height.
    if (down != null) {
        val paint = Paint().apply {
            color       = primary.copy(alpha = 0.95f).toArgb()
            this.textSize = textSize
            textAlign   = Paint.Align.LEFT
            isAntiAlias = true
            typeface    = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("$down", margin, h * 0.88f, paint)
    }
}

/** Thick outer board border drawn on top of all cells. */
private fun DrawScope.drawKakuroOuterBorder(canvasSize: Size) {
    val half  = OUTER_BORDER_W / 2f
    val color = Color.White.copy(alpha = 0.75f)
    val rect  = Rect(half, half, canvasSize.width - half, canvasSize.height - half)
    drawRect(
        color,
        topLeft = rect.topLeft,
        size    = rect.size,
        style   = Stroke(width = OUTER_BORDER_W),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Number pad button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KakuroNumBtn(n: Int, t: GameTheme, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(t.surfaceVariant)
            .border(1.dp, t.primary.copy(.25f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", color = t.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
