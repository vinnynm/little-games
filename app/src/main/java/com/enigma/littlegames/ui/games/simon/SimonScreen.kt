package com.enigma.littlegames.ui.games.simon

// ─────────────────────────────────────────────────────────────────────────────
// Simon Says Screen
// Reuses the LOCell visual style from Lights Out for the flash grid.
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.common.*
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem

@Composable
fun SimonScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: SimonViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val haptic    = LocalHapticFeedback.current
    val particles = rememberParticleSystem()
    var gridCenter by remember { mutableStateOf(Offset(400f, 600f)) }

    // Sound on level clear
    LaunchedEffect(state.score) {
        if (state.score > 0 && state.phase == SimonPhase.LEVEL_CLEAR) {
            hub.sound.play(Sfx.FLOW_SUCCESS)
        }
    }
    // Sound + score report on fail
    LaunchedEffect(state.phase) {
        if (state.phase == SimonPhase.FAILED) {
            hub.recordSimonScore(state.score)
            hub.sound.play(Sfx.FLOW_FAIL)
            particles.burst(
                center = gridCenter,
                colors = listOf(Color(0xFFE94560), Color(0xFFFF6B6B), Color.White),
                count = 30, speed = 0.25f,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .background(t.background)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameTopBar(
                title    = "SIMON SAYS",
                subtitle = "${state.mode.label} · ${state.mode.gridSize}×${state.mode.gridSize} grid",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text(
                        "BEST: ${state.highScore}",
                        color = t.warning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            )

            Spacer(Modifier.height(8.dp))

            // Score display
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("LEVEL",  "${state.score}")
                MiniStat("STATUS", when (state.phase) {
                    SimonPhase.IDLE        -> "READY"
                    SimonPhase.SHOWING     -> "WATCH"
                    SimonPhase.PLAYER_TURN -> "YOUR TURN"
                    SimonPhase.LEVEL_CLEAR -> "CORRECT ✓"
                    SimonPhase.FAILED      -> "FAILED ✗"
                })
                MiniStat("BEST",   "${state.highScore}")
            }

            Spacer(Modifier.height(20.dp))

            // Phase message
            AnimatedVisibility(
                visible = state.phase == SimonPhase.SHOWING || state.phase == SimonPhase.PLAYER_TURN,
                enter = fadeIn() + slideInVertically { -it },
                exit  = fadeOut(),
            ) {
                val isWatching = state.phase == SimonPhase.SHOWING
                Surface(
                    Modifier.fillMaxWidth(),
                    color = (if (isWatching) t.warning else t.primary).copy(.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, (if (isWatching) t.warning else t.primary).copy(.4f)),
                ) {
                    Text(
                        if (isWatching) "👁  Watch the sequence…"
                        else            "👆  Repeat the sequence!",
                        color = if (isWatching) t.warning else t.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            AnimatedVisibility(state.phase == SimonPhase.FAILED) {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = Color(0xFFE94560).copy(.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE94560).copy(.4f)),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("💥  GAME OVER", color = Color(0xFFE94560),
                            fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text("You reached level ${state.score}",
                            color = t.textSecondary, fontSize = 12.sp)
                        if (state.score == state.highScore && state.score > 0)
                            Text("🏆 New best score!", color = t.warning,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Grid ─────────────────────────────────────────────────────────
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .onGloballyPositioned { c ->
                        gridCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                    }
            ) {
                val grid = state.mode.gridSize
                val gap  = 6.dp
                val cell = (maxWidth - gap * (grid + 1)) / grid
                val playerProgress = state.playerInput.size
                val seqLen         = state.sequence.size

                Column(
                    verticalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Spacer(Modifier.height(gap))
                    for (row in 0 until grid) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            Spacer(Modifier.width(gap))
                            for (col in 0 until grid) {
                                val idx    = row * grid + col
                                val isLit  = state.currentHighlight == idx
                                val canTap = state.phase == SimonPhase.PLAYER_TURN
                                SimonCell(
                                    isLit  = isLit,
                                    idx    = idx,
                                    size   = cell,
                                    theme  = t,
                                    playerProgress = if (canTap) playerProgress else -1,
                                    seqLen = seqLen,
                                ) {
                                    if (canTap) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        hub.sound.play(Sfx.TAP)
                                        vm.onTap(idx)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Progress indicator during player turn
            AnimatedVisibility(state.phase == SimonPhase.PLAYER_TURN) {
                val progress = if (state.sequence.isEmpty()) 0f
                else state.playerInput.size.toFloat() / state.sequence.size
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${state.playerInput.size} / ${state.sequence.size} taps",
                        color = t.textSecondary, fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = t.primary, trackColor = t.border,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Buttons
            when (state.phase) {
                SimonPhase.IDLE, SimonPhase.FAILED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemedButton(
                            text = if (state.phase == SimonPhase.IDLE) "▶  Start Game" else "↺  Try Again",
                            onClick = { hub.sound.play(Sfx.TAP); vm.startGame() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Mode selector
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(t.surface).padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            SimonMode.entries.forEach { mode ->
                                val sel = state.mode == mode
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                                        .background(if (sel) t.primary else Color.Transparent)
                                        .clickable {
                                            hub.sound.play(Sfx.TAP)
                                            vm.setMode(mode)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        mode.label,
                                        color = if (sel) t.background else t.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (sel) FontWeight.Black else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    ThemedButton(
                        "✕  Give Up",
                        onClick = { hub.sound.play(Sfx.TAP); vm.retry() },
                        modifier = Modifier.fillMaxWidth(),
                        outlined = true,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Simon cell — lit = bright yellow glow, unlit = dark, tap dims briefly
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SimonCell(
    isLit: Boolean,
    idx: Int,
    size: Dp,
    theme: GameTheme,
    playerProgress: Int,  // -1 if not player turn; else count of correct taps so far
    seqLen: Int,
    onClick: () -> Unit,
) {
    val bgColor   by animateColorAsState(
        if (isLit) Color(0xFFFFD060) else theme.surface,
        tween(120), label = "simon_bg"
    )
    val elevation by animateDpAsState(
        if (isLit) 14.dp else 0.dp, tween(150), label = "simon_elev"
    )
    val scale     by animateFloatAsState(
        if (isLit) 1.06f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "simon_scale"
    )

    Box(
        Modifier
            .size(size)
            .scale(scale)
            .shadow(
                elevation,
                RoundedCornerShape(10.dp),
                ambientColor = Color(0xFFFFD060).copy(if (isLit) 0.5f else 0f),
                spotColor    = Color(0xFFFFD060).copy(if (isLit) 0.7f else 0f),
            )
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(
                1.5.dp,
                if (isLit) Color(0xFFFFD060).copy(.5f) else theme.border,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLit) {
            Box(
                Modifier
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(Color.White.copy(.55f))
            )
        }
    }
}
