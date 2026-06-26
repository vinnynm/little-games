package com.enigma.littlegames.ui.games.sokoban

// ─────────────────────────────────────────────────────────────────────────────
// Sokoban Screen
// Emoji-based grid rendering. D-pad + swipe gesture controls.
// Player and box positions animate with animateFloatAsState.
// Undo and Restart always visible.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
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
import kotlin.math.abs

@Composable
fun SokobanScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: SokobanViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    var boardCenter by remember { mutableStateOf(Offset(400f, 500f)) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            hub.recordSokobanWin(state.level, state.board?.moveCount ?: 0)
            hub.sound.play(Sfx.VICTORY)
            particles.burst(
                center = boardCenter,
                colors = listOf(t.primary, t.accent, Color.White, t.warning),
                count  = 60, speed = 0.35f,
            )
        }
    }

    // Swipe state
    var dragAccum by remember { mutableStateOf(Offset.Zero) }
    val swipeThreshold = 30f

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .background(t.background)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameTopBar(
                title    = "SOKOBAN",
                subtitle = "Level ${state.level} / ${state.totalLevels}",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    // Undo button in top bar
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(t.surface)
                            .border(1.dp, t.border, RoundedCornerShape(8.dp))
                            .clickable { hub.sound.play(Sfx.TAP); vm.undo() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("↩ Undo", color = t.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            )

            // Stats
            val board = state.board
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("MOVES",  "${board?.moveCount ?: 0}")
                MiniStat("PUSHES", "${board?.pushCount ?: 0}")
                val best = state.bestMoves[state.level]
                MiniStat("BEST",   if (best != null) "$best" else "—")
            }

            // Level progress bar
            val pct = state.level.toFloat() / state.totalLevels
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = t.primary, trackColor = t.border,
            )
            Spacer(Modifier.height(6.dp))

            // Complete banner
            AnimatedVisibility(state.isComplete) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color  = t.success.copy(.15f),
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.success.copy(.5f)),
                ) {
                    Column(Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉  Level ${state.level} Complete!",
                            color = t.success, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("${board?.moveCount ?: 0} moves  ·  ${board?.pushCount ?: 0} pushes",
                            color = t.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        if (state.level < state.totalLevels) {
                            ThemedButton("Next Level ▶", { hub.sound.play(Sfx.TAP); vm.nextLevel() },
                                Modifier.fillMaxWidth())
                        } else {
                            Text("🏆 All levels complete!", color = t.warning,
                                fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }

            // ── Board ─────────────────────────────────────────────────────────
            if (board != null && !state.isLoading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { c ->
                            boardCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragAccum = Offset.Zero },
                                onDragEnd = {
                                    if (abs(dragAccum.x) > swipeThreshold || abs(dragAccum.y) > swipeThreshold) {
                                        val dir = if (abs(dragAccum.x) > abs(dragAccum.y)) {
                                            if (dragAccum.x > 0) SokoDir.RIGHT else SokoDir.LEFT
                                        } else {
                                            if (dragAccum.y > 0) SokoDir.DOWN else SokoDir.UP
                                        }
                                        hub.sound.play(Sfx.TAP)
                                        vm.move(dir)
                                    }
                                    dragAccum = Offset.Zero
                                },
                                onDrag = { _, delta -> dragAccum += delta },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    SokobanGrid(board = board, theme = t)
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = t.primary)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── D-pad controls ────────────────────────────────────────────────
            Column(
                Modifier.padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DPadButton("↑", t) { hub.sound.play(Sfx.TAP); vm.move(SokoDir.UP) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DPadButton("←", t) { hub.sound.play(Sfx.TAP); vm.move(SokoDir.LEFT) }
                    DPadButton("↓", t) { hub.sound.play(Sfx.TAP); vm.move(SokoDir.DOWN) }
                    DPadButton("→", t) { hub.sound.play(Sfx.TAP); vm.move(SokoDir.RIGHT) }
                }
            }

            // Level nav + restart
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemedButton("◀ Prev", { hub.sound.play(Sfx.TAP); vm.prevLevel() },
                    Modifier.weight(1f), outlined = true)
                ThemedButton("↺ Restart", { hub.sound.play(Sfx.TAP); vm.restart() },
                    Modifier.weight(1f), outlined = true)
                ThemedButton("Next ▶", { hub.sound.play(Sfx.TAP); vm.nextLevel() },
                    Modifier.weight(1f), outlined = true)
            }
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Board renderer
// Each cell type gets an emoji. Player and box positions animate.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SokobanGrid(board: SokobanBoard, theme: GameTheme) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Compute cell size to fit the level in the available space
        val cellW = maxWidth  / board.width
        val cellH = maxHeight / board.height
        val cellSize = minOf(cellW, cellH).coerceAtMost(44.dp)
        val fontSize = (cellSize.value * 0.55f).sp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            for (r in 0 until board.height) {
                Row {
                    for (c in 0 until board.width) {
                        val pos     = r to c
                        val isWall  = pos in board.walls
                        val isTarget = pos in board.targets
                        val isBox   = pos in board.boxes
                        val isPlayer = r == board.playerRow && c == board.playerCol

                        val bg = when {
                            isWall   -> theme.border.copy(.6f)
                            isTarget -> theme.primary.copy(.12f)
                            else     -> Color.Transparent
                        }

                        Box(
                            Modifier
                                .size(cellSize)
                                .background(bg)
                                .then(
                                    if (isTarget && !isWall)
                                        Modifier.border(1.dp, theme.primary.copy(.4f))
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val emoji = when {
                                isWall            -> "🧱"
                                isPlayer && isTarget -> "😊"
                                isPlayer          -> "🧑"
                                isBox && isTarget -> "📦"   // box on goal — different shade
                                isBox             -> "📦"
                                isTarget          -> "🎯"
                                else              -> ""
                            }
                            // Box on target gets a green tint overlay
                            if (isBox && isTarget) {
                                Box(Modifier.fillMaxSize().background(theme.success.copy(.18f)))
                            }
                            if (emoji.isNotEmpty()) {
                                Text(emoji, fontSize = fontSize, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// D-pad button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DPadButton(label: String, t: GameTheme, onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.88f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "dpad_s",
    )
    Box(
        Modifier
            .size(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(t.surface)
            .border(1.5.dp, t.primary.copy(.4f), RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = t.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
