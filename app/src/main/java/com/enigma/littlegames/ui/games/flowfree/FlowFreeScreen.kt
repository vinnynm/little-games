package com.enigma.littlegames.ui.games.flowfree

// ─────────────────────────────────────────────────────────────────────────────
// Flow Free Screen
// Canvas renders committed paths as thick rounded polylines.
// Dots drawn on top. Drag gesture converts pixel coords → grid cells.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.common.*
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem

// ── Colour palette — 10 distinct colours that look great on dark themes ───────
val FLOW_COLOURS = listOf(
    Color(0xFFE94560),   // 0 Red
    Color(0xFF4FC3F7),   // 1 Cyan
    Color(0xFF81C784),   // 2 Green
    Color(0xFFFFD060),   // 3 Yellow
    Color(0xFFCE93D8),   // 4 Purple
    Color(0xFFFF8A65),   // 5 Orange
    Color(0xFF80DEEA),   // 6 Teal
    Color(0xFFF48FB1),   // 7 Pink
    Color(0xFFB0BEC5),   // 8 Silver
    Color(0xFFA5D6A7),   // 9 Mint
)

@Composable
fun FlowFreeScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: FlowFreeViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()

    var boardPxSize  by remember { mutableStateOf(1f) }
    var boardCenter  by remember { mutableStateOf(Offset(400f, 500f)) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            hub.recordFlowFreeWin(state.difficulty.label, state.moves)
            hub.sound.play(Sfx.FLOW_SUCCESS)
            particles.burst(
                center = boardCenter,
                colors = FLOW_COLOURS.take(5) + listOf(Color.White),
                count = 80, speed = 0.4f,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().background(t.background)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameTopBar(
                title    = "FLOW FREE",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}  ·  ${state.difficulty.size}×${state.difficulty.size}",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text("${state.moves} moves", color = t.primary, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                }
            )

            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                val puzzle = state.puzzle
                val connectedPairs = if (puzzle != null) {
                    puzzle.dots.values.toSet().count { colourId ->
                        state.paths[colourId]?.let { path ->
                            val dots = puzzle.dots.entries.filter { it.value == colourId }.map { it.key }
                            dots.size == 2 && path.first() in dots && path.last() in dots
                        } ?: false
                    }
                } else 0
                val totalPairs = state.puzzle?.dots?.values?.toSet()?.size ?: 0
                MiniStat("PAIRS",  "$connectedPairs/$totalPairs")
                val filled = state.grid.sumOf { row -> row.count { it.colorId != null } }
                val total  = (state.difficulty.size * state.difficulty.size)
                MiniStat("FILLED", "$filled/$total")
                MiniStat("MOVES",  "${state.moves}")
            }

            AnimatedVisibility(visible = state.isComplete) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color  = t.success.copy(alpha = 0.15f),
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.success.copy(alpha = 0.5f)),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("🎉  FLOW COMPLETE!", color = t.success,
                            fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("  ·  ${state.moves} moves", color = t.textSecondary, fontSize = 11.sp)
                    }
                }
            }

            // ── Board ─────────────────────────────────────────────────────────
            if (state.generating) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = t.primary)
                }
            } else {
                val puzzle = state.puzzle
                if (puzzle != null) {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                boardPxSize = coords.size.width.toFloat()
                                boardCenter = Offset(
                                    pos.x + coords.size.width / 2f,
                                    pos.y + coords.size.height / 2f,
                                )
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(t.surface)
                            .border(1.dp, t.border, RoundedCornerShape(10.dp))
                            // Single pointerInput — detectDragGestures handles everything
                            .pointerInput(puzzle) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val (r, c) = pixelToCell(offset, boardPxSize, puzzle.size)
                                        vm.onDragStart(r, c)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val (r, c) = pixelToCell(change.position, boardPxSize, puzzle.size)
                                        vm.onDragMove(r, c)
                                    },
                                    onDragEnd = { vm.onDragEnd() },
                                    onDragCancel = { vm.onDragEnd() },
                                )
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawFlowBoard(
                                state     = state,
                                puzzle    = puzzle,
                                cellPx    = size.width / puzzle.size,
                                gridColor = t.border.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text("Drag to draw paths · connect all colour pairs · fill every cell",
                color = t.textSecondary, fontSize = 11.sp)

            Spacer(Modifier.weight(1f))

            // Difficulty tabs
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(t.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                FlowDifficulty.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                            .background(if (sel) t.primary else Color.Transparent)
                            .clickable { hub.sound.play(Sfx.TAP); vm.newGame(d) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.emoji} ${d.label.take(3)}",
                            color      = if (sel) t.background else t.textSecondary,
                            fontSize   = 10.sp,
                            fontWeight = if (sel) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            ThemedButton("↺  New Puzzle", {
                hub.sound.play(Sfx.TAP); vm.newGame(state.difficulty)
            }, Modifier.fillMaxWidth(), outlined = true)

            Spacer(Modifier.height(16.dp))
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas drawing
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawFlowBoard(
    state: FlowState,
    puzzle: FlowPuzzle,
    cellPx: Float,
    gridColor: Color,
) {
    val n = puzzle.size

    // Grid lines
    for (i in 0..n) {
        val pos = i * cellPx
        drawLine(gridColor, Offset(pos, 0f), Offset(pos, size.height), 0.8f)
        drawLine(gridColor, Offset(0f, pos), Offset(size.width, pos), 0.8f)
    }

    // Paths — thick rounded polylines
    state.paths.forEach { (colourId, path) ->
        if (path.size < 2) return@forEach
        val colour = FLOW_COLOURS.getOrElse(colourId) { Color.White }
        val strokeW = cellPx * 0.45f
        val pathObj = Path()
        path.forEachIndexed { idx, (r, c) ->
            val cx = c * cellPx + cellPx / 2f
            val cy = r * cellPx + cellPx / 2f
            if (idx == 0) pathObj.moveTo(cx, cy) else pathObj.lineTo(cx, cy)
        }
        drawPath(pathObj, colour.copy(alpha = 0.85f), style = Stroke(
            width = strokeW,
            cap   = StrokeCap.Round,
            join  = StrokeJoin.Round,
        ))
    }

    // Dots — filled circles with white centre ring
    puzzle.dots.forEach { (pos, colourId) ->
        val (r, c) = pos
        val cx = c * cellPx + cellPx / 2f
        val cy = r * cellPx + cellPx / 2f
        val colour = FLOW_COLOURS.getOrElse(colourId) { Color.White }
        val radius = cellPx * 0.36f
        drawCircle(colour, radius, Offset(cx, cy))
        drawCircle(colour.copy(alpha = 0.4f), radius, Offset(cx, cy),
            style = Stroke(width = cellPx * 0.06f))
        drawCircle(Color.White.copy(alpha = 0.25f), radius * 0.4f, Offset(cx, cy))
    }
}

private fun pixelToCell(offset: Offset, boardPxSize: Float, gridSize: Int): Pair<Int, Int> {
    val cellPx = boardPxSize / gridSize
    val c = (offset.x / cellPx).toInt().coerceIn(0, gridSize - 1)
    val r = (offset.y / cellPx).toInt().coerceIn(0, gridSize - 1)
    return r to c
}