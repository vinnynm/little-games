package com.enigma.littlegames.ui.games.pipeflow

// ─────────────────────────────────────────────────────────────────────────────
// Pipe Flow Screen — Phase 3
//   • Grid expands to fill available width; gap reduced from 4dp to 3dp
//   • X (4-way) pipe renders with a distinctive cross glow when in flow
//   • Board container has a subtle border glow matching the theme primary
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
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import com.enigma.littlegames.common.*

@Composable
fun PipeFlowScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: PipeFlowViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val haptic    = LocalHapticFeedback.current
    val particles = rememberParticleSystem()
    var boardCenter by remember { mutableStateOf(Offset(400f, 500f)) }

    LaunchedEffect(state.solved) {
        if (state.solved) {
            hub.recordPipeWin(
                level         = state.level,
                stars         = state.lastStars,
                totalStars    = state.totalStars,
                usedAutoSolve = state.autoSolveUsed,
            )
            hub.sound.play(Sfx.FLOW_SUCCESS)
            particles.burst(
                center = boardCenter,
                colors = listOf(t.primary, t.primary.copy(.6f), Color.White, t.accent),
                count  = 60, speed = 0.35f,
            )
        }
    }

    LaunchedEffect(state.flowResult) {
        val res = state.flowResult
        if (res != null && !res.solved) hub.sound.play(Sfx.FLOW_FAIL)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().background(t.background).padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                title    = "PIPE FLOW",
                subtitle = state.puzzle?.let { "Lvl ${state.level}  ·  \"${it.title}\"  ·  Par ${it.par}" },
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text("${state.moves} mv", color = t.primary, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                }
            )

            // Campaign progress
            val pct = (state.level.toFloat() / PIPE_CAMPAIGN.size).coerceIn(0f, 1f)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LVL", color = t.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp))
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.weight(1f).height(3.dp),
                    color = t.primary, trackColor = t.border,
                )
                Text("⭐ ${state.totalStars}", color = t.warning, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(Modifier.height(4.dp))

            // Board — fills available width
            if (state.generating) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).weight(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = t.primary) }
            } else {
                val puzzle = state.puzzle
                if (puzzle != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onGloballyPositioned { c ->
                                boardCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                            }
                    ) {
                        BoxWithConstraints(
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .align(Alignment.Center)
                                // Subtle glowing border around the entire board
                                .shadow(
                                    elevation   = 12.dp,
                                    shape       = RoundedCornerShape(6.dp),
                                    ambientColor = t.primary.copy(.25f),
                                    spotColor   = t.primary.copy(.35f),
                                )
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.5.dp, t.primary.copy(.5f), RoundedCornerShape(6.dp))
                        ) {
                            // Reduced gap so pipes fill more of the board
                            val gap      = 3.dp
                            val cellSize = (maxWidth - (puzzle.size + 1) * gap) / puzzle.size

                            Column(
                                Modifier.fillMaxSize().background(t.background),
                                verticalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                Spacer(Modifier.height(gap))
                                for (row in 0 until puzzle.size) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(gap)
                                    ) {
                                        Spacer(Modifier.width(gap))
                                        for (col in 0 until puzzle.size) {
                                            val idx    = row * puzzle.size + col
                                            val cell   = state.cells[idx]
                                            val inFlow = state.flowResult?.visited?.contains(idx) == true
                                            PipeCellView(cell, idx, cellSize, t, inFlow,
                                                onRotate = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    hub.sound.play(Sfx.ROTATE)
                                                    vm.rotateCell(it)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Flow result message
            val result = state.flowResult
            AnimatedVisibility(result != null || state.solved) {
                val (msg, color) = when {
                    state.solved           -> "✓ PERFECT SEAL — Water flows freely!" to t.success
                    result?.leaked == true -> "💧 LEAK — an open port has no match."  to t.warning
                    else                   -> "⚠ PATH BROKEN — outlet not reached."   to t.error
                }
                Surface(
                    Modifier.fillMaxWidth(), color = color.copy(.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, color.copy(.4f))
                ) {
                    Text(msg, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(10.dp))
                }
            }

            // Solved panel
            AnimatedVisibility(state.solved) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("⭐".repeat(state.lastStars) + "☆".repeat(3 - state.lastStars), fontSize = 26.sp)
                    Spacer(Modifier.height(8.dp))
                    ThemedButton("Next Level ▶", {
                        hub.sound.play(Sfx.TAP); vm.nextLevel()
                    }, Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.weight(1f))

            // Controls
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemedButton("↺ Reset",  { hub.sound.play(Sfx.TAP); vm.reset()     }, Modifier.weight(1f), outlined = true)
                ThemedButton("💡 Solve", { hub.sound.play(Sfx.TAP); vm.autoSolve() }, Modifier.weight(1f), outlined = true)
                ThemedButton("▶ FLOW",  {
                    hub.sound.play(Sfx.FLOW_CHECK); vm.checkFlow()
                }, Modifier.weight(1.5f))
            }
        }

        ParticleOverlay(particles)
    }
}

// ── Cell composable ───────────────────────────────────────────────────────────

@Composable
private fun PipeCellView(
    cell: PipeCell, idx: Int, size: Dp, t: GameTheme,
    inFlow: Boolean, onRotate: (Int) -> Unit,
) {
    val rotDeg      by animateFloatAsState(cell.rot * 90f,
        spring(Spring.DampingRatioMediumBouncy, stiffness = 400f), label = "pipe_r")
    val borderColor by animateColorAsState(
        if (inFlow) t.primary else t.border, tween(200), label = "pipe_b"
    )

    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(5.dp))
            .background(t.surface)
            .border(1.dp, borderColor, RoundedCornerShape(5.dp))
            .then(
                if (!cell.fixed && !cell.locked)
                    Modifier.clickable { onRotate(idx) }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when (cell.type) {
            '#'  -> Text("🪨", fontSize = (size.value * 0.4f).sp)
            'N'  -> Text("⛲", fontSize = (size.value * 0.45f).sp)
            'O'  -> Text("🏺", fontSize = (size.value * 0.45f).sp)
            else -> Canvas(Modifier.size(size * 0.9f).rotate(rotDeg)) {
                drawPipe(cell.type, inFlow, t)
            }
        }
        if (cell.locked) Text("🔒", fontSize = (size.value * 0.2f).sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(1.dp))
    }
}

private fun DrawScope.drawPipe(type: Char, lit: Boolean, t: GameTheme) {
    val w = size.width; val h = size.height; val cx = w / 2f; val cy = h / 2f
    val pW = w * 0.24f; val fW = w * 0.13f
    val pipe  = Color(0xFF2D3548)
    val fluid = if (lit) t.primary else Color.Transparent

    val ports = BASE_PORTS[type] ?: return

    // Draw pipe segments
    if (ports[0]) { drawRect(pipe,  Offset(cx - pW/2, 0f), Size(pW, cy));       drawRect(fluid, Offset(cx - fW/2, 0f), Size(fW, cy)) }
    if (ports[1]) { drawRect(pipe,  Offset(cx, cy - pW/2), Size(w - cx, pW));   drawRect(fluid, Offset(cx, cy - fW/2), Size(w - cx, fW)) }
    if (ports[2]) { drawRect(pipe,  Offset(cx - pW/2, cy), Size(pW, h - cy));   drawRect(fluid, Offset(cx - fW/2, cy), Size(fW, h - cy)) }
    if (ports[3]) { drawRect(pipe,  Offset(0f, cy - pW/2), Size(cx, pW));       drawRect(fluid, Offset(0f, cy - fW/2), Size(cx, fW)) }

    // Center hub — larger and more prominent for X pipes
    val hubRadius = if (type == 'X') pW * 1.0f else pW * 0.8f
    val hubColor  = if (type == 'X') Color(0xFF3D4560) else Color(0xFF3D4560)
    drawCircle(hubColor, hubRadius, Offset(cx, cy))

    // Fluid center glow
    if (lit) {
        val glowRadius = if (type == 'X') fW * 1.2f else fW * 0.9f
        drawCircle(t.primary.copy(.6f), glowRadius, Offset(cx, cy))
        // X pipes get an extra outer glow ring when carrying flow
        if (type == 'X') {
            drawCircle(
                t.primary.copy(.25f),
                pW * 0.95f,
                Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )
        }
    }
}
