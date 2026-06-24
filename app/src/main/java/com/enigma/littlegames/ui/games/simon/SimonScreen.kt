package com.enigma.littlegames.ui.games.simon

// ─────────────────────────────────────────────────────────────────────────────
// Simon Says Screen
// Reuses the LOCell visual from Lights Out — same grid, same highlight style.
// The grid pulses bright on each sequence step, then dims for player input.
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
    var boardCenter by remember { mutableStateOf(Offset(400f, 500f)) }

    // Wire high-score callback
    LaunchedEffect(Unit) {
        vm.onHighScore = { score, mode ->
            hub.recordSimonHighScore(score, mode.label)
            hub.sound.play(Sfx.VICTORY)
        }
    }

    // Play tap sound and haptic on each sequence flash
    LaunchedEffect(state.currentHighlight) {
        if (state.currentHighlight != null && state.phase == SimonPhase.SHOWING) {
            hub.sound.play(Sfx.TAP)
        }
    }

    // Particle burst on level clear
    LaunchedEffect(state.score) {
        if (state.score > 0 && state.phase == SimonPhase.LEVEL_CLEAR) {
            particles.burst(
                center = boardCenter,
                colors = listOf(t.primary, t.accent, Color.White),
                count  = 20, speed = 0.2f,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().background(t.background).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                title    = "SIMON SAYS",
                subtitle = "${state.mode.emoji} ${state.mode.label}  ·  Score: ${state.score}",
                onBack   = { hub.navigate(HubScreen.Home) },
            )

            // Score / high-score row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("SCORE",      "${state.score}")
                MiniStat("BEST",       "${state.highScore}")
                MiniStat("SEQUENCE",   "${state.sequence.size}")
            }

            Spacer(Modifier.height(16.dp))

            // Phase label
            val phaseLabel = when (state.phase) {
                SimonPhase.IDLE        -> "Press START to begin"
                SimonPhase.SHOWING     -> "👀 Watch the sequence…"
                SimonPhase.PLAYER_TURN -> "👆 Your turn! ${state.sequence.size - state.playerInput.size} left"
                SimonPhase.LEVEL_CLEAR -> "✓ Nice! Next round…"
                SimonPhase.FAILED      -> "💀 Wrong! Score: ${state.score}"
            }
            AnimatedContent(phaseLabel, label = "phase_lbl") { lbl ->
                Text(lbl,
                    color = when (state.phase) {
                        SimonPhase.FAILED      -> t.error
                        SimonPhase.LEVEL_CLEAR -> t.success
                        SimonPhase.PLAYER_TURN -> t.primary
                        else                   -> t.textSecondary
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Grid
            BoxWithConstraints(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .onGloballyPositioned { c ->
                        boardCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                    }
            ) {
                val n        = state.mode.gridSize
                val gap      = 6.dp
                val cellSize = (maxWidth - (n + 1) * gap) / n

                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Spacer(Modifier.height(gap))
                    for (row in 0 until n) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Spacer(Modifier.width(gap))
                            for (col in 0 until n) {
                                val idx      = row * n + col
                                val isLit    = state.currentHighlight == idx
                                val isInput  = idx in state.playerInput
                                val canTap   = state.phase == SimonPhase.PLAYER_TURN
                                SimonCell(
                                    isLit    = isLit,
                                    isInput  = isInput,
                                    canTap   = canTap,
                                    size     = cellSize,
                                    theme    = t,
                                ) {
                                    if (canTap) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        hub.sound.play(Sfx.TAP)
                                        vm.onCellTap(idx)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action buttons
            when (state.phase) {
                SimonPhase.IDLE, SimonPhase.FAILED -> {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.phase == SimonPhase.FAILED && state.sequence.size > 1) {
                            ThemedButton("👁 Replay Sequence", vm::replay,
                                Modifier.fillMaxWidth(), outlined = true)
                        }
                        ThemedButton(
                            if (state.phase == SimonPhase.IDLE) "▶  Start Game" else "▶  Play Again",
                            vm::startGame, Modifier.fillMaxWidth()
                        )
                    }
                }
                else -> {}
            }

            Spacer(Modifier.height(16.dp))

            // Mode selector
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.surface).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SimonMode.entries.forEach { mode ->
                    val sel = state.mode == mode
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (sel) t.primary else Color.Transparent)
                            .clickable { hub.sound.play(Sfx.TAP); vm.setMode(mode) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${mode.emoji} ${mode.label}",
                            color      = if (sel) t.background else t.textSecondary,
                            fontSize   = 11.sp,
                            fontWeight = if (sel) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Simon cell composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SimonCell(
    isLit: Boolean,
    isInput: Boolean,
    canTap: Boolean,
    size: Dp,
    theme: GameTheme,
    onClick: () -> Unit,
) {
    val bgColor   by animateColorAsState(
        when {
            isLit   -> Color(0xFFFFD060)
            isInput -> theme.primary.copy(.35f)
            canTap  -> theme.surface
            else    -> theme.surface.copy(.7f)
        },
        tween(120), label = "sc_bg"
    )
    val elevation by animateDpAsState(if (isLit) 14.dp else 0.dp, tween(100), label = "sc_elev")
    val scale     by animateFloatAsState(if (isLit) 1.04f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "sc_scale")

    Box(
        Modifier.size(size).scale(scale)
            .shadow(elevation, RoundedCornerShape(10.dp),
                ambientColor = if (isLit) Color(0xFFFFD060).copy(.6f) else Color.Transparent,
                spotColor    = if (isLit) Color(0xFFFFD060).copy(.8f) else Color.Transparent)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(
                if (isLit) 2.dp else 1.dp,
                when { isLit -> Color(0xFFFFD060).copy(.5f); isInput -> theme.primary.copy(.5f); else -> theme.border },
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = canTap, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLit) {
            Box(Modifier.size(size * 0.28f).clip(CircleShape)
                .background(Color.White.copy(.55f)))
        }
    }
}
