package com.enigma.littlegames.ui.games.lightsout

// ─────────────────────────────────────────────────────────────────────────────
// Lights Out Screen — Phase 2 improvements:
//   • Particle burst on solve
//   • Sound effects (tap, victory)
//   • Achievement reporting to HubViewModel
//   • Animated cell glow and spring-scale
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
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
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import com.enigma.littlegames.common.*

@Composable
fun LightsOutScreen(hub: HubViewModel) {
    val t      = LocalGameTheme.current
    val vm: LightsOutViewModel = viewModel()
    val state  by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val particles = rememberParticleSystem()

    // Board centre for particle burst origin
    var boardCenter by remember { mutableStateOf(Offset(400f, 600f)) }

    // Report win
    LaunchedEffect(state.isSolved) {
        if (state.isSolved) {
            hub.recordLightsOutWin(
                moves      = state.moveCount,
                difficulty = state.difficulty.label,
                usedHint   = state.hintUsed,
            )
            hub.sound.play(Sfx.VICTORY)
            particles.burst(
                center = boardCenter,
                colors = listOf(Color(0xFFFFD060), Color(0xFFFFF4B0), t.primary, Color.White),
                count  = 55,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().background(t.background)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                title    = "LIGHTS OUT",
                subtitle = "${state.difficulty.label}  ·  Optimal: ${state.solution.sum()} moves",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    IconButton(onClick = { vm.toggleHint(); hub.sound.play(Sfx.TAP) }) {
                        Icon(Icons.Default.Lightbulb, "Hint",
                            tint = if (state.showHint) Color(0xFFFFD060) else t.textSecondary)
                    }
                }
            )

            // Stats row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("MOVES",   "${state.moveCount}")
                MiniStat("LIT",     "${state.cells.sum()}")
                MiniStat("OPTIMAL", "${state.solution.sum()}")
            }

            Spacer(Modifier.height(20.dp))

            // Board
            if (state.isGenerating) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = t.primary)
                }
            } else {
                BoxWithConstraints(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val size = coords.size
                            boardCenter = Offset(size.width / 2f, size.height / 2f)
                        }
                ) {
                    val cellSize = (maxWidth - 24.dp) / 5
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (row in 0..4) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                for (col in 0..4) {
                                    val idx = row * 5 + col
                                    LOCell(
                                        isOn   = state.cells[idx] == 1,
                                        isHint = vm.isHintCell(idx),
                                        size   = cellSize,
                                        theme  = t,
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        hub.sound.play(Sfx.TAP)
                                        vm.press(idx)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Solved banner
            AnimatedVisibility(state.isSolved) {
                SolvedBanner(
                    message  = "✦ LIGHTS OUT ✦",
                    stars    = if (state.moveCount <= state.solution.sum()) 3
                               else if (state.moveCount <= state.solution.sum() * 2) 2 else 1,
                    subLabel = "${state.moveCount} moves  ·  optimal ${state.solution.sum()}",
                    onNext   = { vm.newGame(state.difficulty) },
                    nextLabel = "New Game",
                )
            }

            Spacer(Modifier.height(16.dp))

            // Difficulty tabs
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(t.surface).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LoDifficulty.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (sel) t.primary else Color.Transparent)
                            .clickable { hub.sound.play(Sfx.TAP); vm.newGame(d) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(d.label.take(3).uppercase(),
                            color = if (sel) t.background else t.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (sel) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemedButton("New Game", {
                    hub.sound.play(Sfx.TAP)
                    vm.newGame(state.difficulty)
                }, Modifier.weight(1f))
                ThemedButton("Hint", {
                    hub.sound.play(Sfx.TAP)
                    vm.toggleHint()
                }, Modifier.weight(1f), outlined = true)
            }

            Spacer(Modifier.height(16.dp))
        }

        // Particle overlay on top
        ParticleOverlay(particles)
    }
}

@Composable
private fun LOCell(isOn: Boolean, isHint: Boolean, size: Dp, theme: GameTheme, onClick: () -> Unit) {
    val bgColor   by animateColorAsState(if (isOn) Color(0xFFFFD060) else Color(0xFF1E2235), tween(160), label = "lo_bg")
    val elevation by animateDpAsState(if (isOn) 12.dp else 0.dp, tween(180), label = "lo_elev")
    val scale     by animateFloatAsState(if (isOn) 1f else 0.95f, spring(Spring.DampingRatioMediumBouncy), label = "lo_scale")

    Box(
        Modifier
            .size(size)
            .scale(scale)
            .shadow(
                elevation,
                RoundedCornerShape(8.dp),
                ambientColor = Color(0xFFFFD060).copy(if (isOn) 0.5f else 0f),
                spotColor    = Color(0xFFFFD060).copy(if (isOn) 0.7f else 0f),
            )
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                if (isHint) 2.dp else 1.dp,
                when { isHint -> Color(0xFF60EFFF); isOn -> Color(0xFFFFD060).copy(.4f); else -> Color(0xFF2E3555) },
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Bright inner glow when lit
        if (isOn) {
            Box(Modifier.size(size * 0.28f).clip(CircleShape).background(Color.White.copy(.55f)))
        }
    }
}
