package com.enigma.littlegames.ui.games.kakuro

// ─────────────────────────────────────────────────────────────────────────────
// Kakuro Screen
// Black clue cells split diagonally (Canvas Path) showing down clue in the
// top-right triangle and right/across clue in the bottom-left triangle.
// White cells work like Sudoku number entry with error highlighting.
// ─────────────────────────────────────────────────────────────────────────────

import android.graphics.Paint
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import com.enigma.littlegames.common.*

@Composable
fun KakuroScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: KakuroViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    var gridCenter by remember { mutableStateOf(Offset(400f, 400f)) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
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

            // Grid
            if (state.generating) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = t.primary)
                }
            } else {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .border(2.dp, t.primary.copy(.5f), RoundedCornerShape(4.dp))
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
                                        // Highlight run members
                                        state.runs.any { run ->
                                            (sr to sc) in run.cells && (row to col) in run.cells
                                        }
                                    } ?: false

                                    KakuroCellView(cell, cellSize, isSel, isHi, isErr, t,
                                        Modifier.weight(1f).fillMaxHeight()
                                    ) { vm.select(row, col) }
                                }
                            }
                        }
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
                    "Fill each row/column run with unique\ndigits that sum to the clue.",
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
                    KakuroNumBtn(n, t) {
                        hub.sound.play(Sfx.SUDOKU_PLACE); vm.place(n)
                    }
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

// ── Cell composable ───────────────────────────────────────────────────────────

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
            // Black clue cell with diagonal split
            Box(
                modifier.background(Color(0xFF0A0C12))
                    .border(.5.dp, t.border.copy(.4f))
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawKakuroClueCell(cell.down, cell.right, t.primary, t.textSecondary)
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
                modifier.background(bg)
                    .border(.5.dp, if (isSelected) t.primary.copy(.6f) else t.border.copy(.3f))
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

/**
 * Draw a Kakuro clue cell:
 *  - Diagonal line from top-right to bottom-left
 *  - Down clue in the top-right triangle
 *  - Right (across) clue in the bottom-left triangle
 */
private fun DrawScope.drawKakuroClueCell(down: Int?, right: Int?, primary: Color, secondary: Color) {
    val w = size.width; val h = size.height

    // Background already set by Box; just draw the diagonal line
    drawLine(
        color       = secondary.copy(.5f),
        start       = Offset(0f, 0f),
        end         = Offset(w, h),
        strokeWidth = 1.2f,
    )

    // Down clue — top-right area
    if (down != null) {
        // We can't draw text in DrawScope directly; use Canvas with nativeCanvas
        val paint = Paint().apply {
            color     = primary.copy(.9f).toArgb()
            textSize  = h * 0.28f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText("$down", w - 2f, h * 0.40f, paint)
    }

    // Right (across) clue — bottom-left area
    if (right != null) {
        val paint = Paint().apply {
            color     = primary.copy(.9f).toArgb()
            textSize  = h * 0.28f
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText("$right", 2f, h - 2f, paint)
    }
}

// ── Number button ─────────────────────────────────────────────────────────────

@Composable
private fun KakuroNumBtn(n: Int, t: GameTheme, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(t.surfaceVariant)
            .border(1.dp, t.primary.copy(.25f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text("$n", color = t.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}
