package com.enigma.littlegames.ui.games.minesweeper

// ─────────────────────────────────────────────────────────────────────────────
// Minesweeper Screen
// Pure Canvas hex grid. Tap = reveal. Long-press = flag.
// Hex cells drawn with flat-top orientation.
// ─────────────────────────────────────────────────────────────────────────────

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.common.*
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import kotlin.math.*

// Adjacency number colours (classic Minesweeper palette)
private val ADJ_COLOURS = listOf(
    Color.Transparent,          // 0 — no label
    Color(0xFF4FC3F7),          // 1 — blue
    Color(0xFF81C784),          // 2 — green
    Color(0xFFE94560),          // 3 — red
    Color(0xFF9C27B0),          // 4 — purple
    Color(0xFFFF7043),          // 5 — deep orange
    Color(0xFF26C6DA),          // 6 — cyan
    Color(0xFFFFEE58),          // 7 — yellow
    Color(0xFFBDBDBD),          // 8 — grey
)

@Composable
fun MinesweeperScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: MinesweeperViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()

    var canvasWidth  by remember { mutableStateOf(1f) }
    var canvasHeight by remember { mutableStateOf(1f) }

    LaunchedEffect(state.phase) {
        when (state.phase) {
            MinesweeperPhase.WON -> {
                hub.recordMinesweeperWin(state.difficulty.label, state.elapsedSecs)
                hub.sound.play(Sfx.VICTORY)
                particles.burst(
                    center = Offset(canvasWidth / 2f, canvasHeight / 2f),
                    colors = listOf(t.primary, t.accent, Color.White, t.warning),
                    count  = 65, speed = 0.4f,
                )
            }
            MinesweeperPhase.LOST -> hub.sound.play(Sfx.FLOW_FAIL)
            else -> {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().background(t.background)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val timer = remember(state.elapsedSecs) {
                "%02d:%02d".format(state.elapsedSecs / 60, state.elapsedSecs % 60)
            }

            GameTopBar(
                title    = "MINESWEEPER",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}  ·  hex grid",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text(timer, color = t.primary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                }
            )

            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("MINES",   "${state.mineCount - state.flagCount}")
                MiniStat("FLAGS",   "${state.flagCount}")
                MiniStat("SAFE",    "${state.revealCount}/${state.safeCount}")
            }

            // Phase banners
            AnimatedVisibility(state.phase == MinesweeperPhase.WON) {
                Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = t.success.copy(.15f), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.success.copy(.5f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("🎉  CLEARED!", color = t.success,
                            fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("  ·  $timer", color = t.textSecondary, fontSize = 11.sp)
                    }
                }
            }
            AnimatedVisibility(state.phase == MinesweeperPhase.LOST) {
                Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = Color(0xFFE94560).copy(.12f), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE94560).copy(.4f))) {
                    Text("💥  BOOM!  Try again.",
                        color = Color(0xFFE94560), fontWeight = FontWeight.Black,
                        fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp))
                }
            }
            AnimatedVisibility(state.phase == MinesweeperPhase.IDLE) {
                Text("Tap any cell to start · long-press to flag",
                    color = t.textSecondary, fontSize = 12.sp)
            }

            // ── Hex board canvas ──────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        canvasWidth  = coords.size.width.toFloat()
                        canvasHeight = coords.size.height.toFloat()
                    }
            ) {
                if (!state.generating && state.board.isNotEmpty()) {
                    val hexRadius = computeHexRadius(
                        state.difficulty.radius, canvasWidth, canvasHeight
                    )
                    val originX = canvasWidth  / 2f
                    val originY = canvasHeight / 2f

                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(state.board, hexRadius) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val coord = pixelToHex(offset, hexRadius, originX, originY)
                                        if (coord != null && state.board.containsKey(coord)) {
                                            hub.sound.play(Sfx.TAP)
                                            vm.reveal(coord)
                                        }
                                    },
                                    onLongPress = { offset ->
                                        val coord = pixelToHex(offset, hexRadius, originX, originY)
                                        if (coord != null && state.board.containsKey(coord)) {
                                            hub.sound.play(Sfx.ROTATE)
                                            vm.toggleFlag(coord)
                                        }
                                    },
                                )
                            }
                    ) {
                        state.board.forEach { (coord, cell) ->
                            val (cx, cy) = hexToPixel(coord, hexRadius, originX, originY)
                            drawHexCell(cell, cx, cy, hexRadius, t)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Difficulty tabs
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(t.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                MineDifficulty.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                            .background(if (sel) t.primary else Color.Transparent)
                            .clickable { hub.sound.play(Sfx.TAP); vm.newGame(d) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.emoji} ${d.label.take(3)}",
                            color = if (sel) t.background else t.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (sel) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            ThemedButton("↺  New Game", {
                hub.sound.play(Sfx.TAP); vm.newGame(state.difficulty)
            }, Modifier.fillMaxWidth(), outlined = true)

            Spacer(Modifier.height(16.dp))
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas hex drawing
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawHexCell(
    cell: HexCell,
    cx: Float, cy: Float,
    radius: Float,
    t: GameTheme,
) {
    val path = hexPath(cx, cy, radius * 0.94f)   // slight gap between cells

    val bgColor = when {
        cell.isMine && cell.isRevealed -> Color(0xFFE94560).copy(.6f)
        cell.isRevealed                -> t.surfaceVariant
        cell.isFlagged                 -> t.warning.copy(.25f)
        else                           -> t.surface
    }

    drawPath(path, bgColor)
    drawPath(path, t.border.copy(.4f), style = Stroke(1.5f))

    if (cell.isFlagged && !cell.isRevealed) {
        // Flag emoji-style dot
        drawCircle(t.warning, radius * 0.22f, Offset(cx, cy))
        return
    }

    if (cell.isRevealed) {
        if (cell.isMine) {
            // Mine — filled red circle
            drawCircle(Color(0xFFE94560), radius * 0.30f, Offset(cx, cy))
            drawCircle(Color.Black.copy(.4f), radius * 0.15f, Offset(cx, cy))
        } else if (cell.adjMines > 0) {
            // Draw number via native canvas text
            val paint = Paint().apply {
                color     = ADJ_COLOURS.getOrElse(cell.adjMines) { Color.White }.toArgb()
                textSize  = radius * 0.72f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
            }
            drawContext.canvas.nativeCanvas.drawText(
                "${cell.adjMines}", cx, cy + radius * 0.26f, paint
            )
        }
    }
}

private fun hexPath(cx: Float, cy: Float, radius: Float): Path {
    val path = Path()
    for (i in 0..5) {
        // Flat-top: angle offset = 0°
        val angle = Math.PI / 180.0 * (60.0 * i)
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

// ─────────────────────────────────────────────────────────────────────────────
// Coordinate helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun computeHexRadius(gridRadius: Int, w: Float, h: Float): Float {
    // Fit the hex grid in the available space with a small margin
    val margin = 16f
    val hexW = (w - margin * 2) / (gridRadius * 3f + 1.5f)
    val hexH = (h - margin * 2) / ((gridRadius * 2 + 1) * sqrt(3f))
    return minOf(hexW, hexH).coerceAtLeast(8f)
}

private fun pixelToHex(
    offset: Offset,
    size: Float,
    originX: Float,
    originY: Float,
): HexCoord? {
    val dx = offset.x - originX
    val dy = offset.y - originY
    val q  = ((2f / 3f * dx) / size).roundToInt()
    val r  = ((-1f / 3f * dx + sqrt(3f) / 3f * dy) / size).roundToInt()
    return HexCoord(q, r)
}

private fun Float.roundToInt() = roundToInt(this.toDouble())

private fun roundToInt(d: Double) = Math.round(d).toInt()
