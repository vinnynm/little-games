package com.enigma.littlegames.ui.games.sliding

// ─────────────────────────────────────────────────────────────────────────────
// Sliding Puzzle Screen
// Animated tile positions via animateOffsetAsState (80ms spring).
// Tap any tile adjacent to blank to slide it.
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.common.*
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import kotlin.text.format

@Composable
fun SlidingPuzzleScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: SlidingPuzzleViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    var boardCenter by remember { mutableStateOf(Offset(400f, 500f)) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            hub.recordSlidingWin(state.size, state.moves)
            hub.sound.play(Sfx.VICTORY)
            particles.burst(
                center = boardCenter,
                colors = listOf(t.primary, t.secondary, Color.White, t.warning),
                count = 65, speed = 0.4f,
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
            val timer = remember(state.elapsedSecs) {
                "%02d:%02d".format(state.elapsedSecs / 60, state.elapsedSecs % 60)
            }

            GameTopBar(
                title    = "SLIDING PUZZLE",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}  ·  ${state.size}×${state.size}",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text(timer, color = t.primary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp))
                }
            )

            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("MOVES", "${state.moves}")
                MiniStat("TIME",  timer)
                MiniStat("SIZE",  "${state.size}×${state.size}")
            }

            AnimatedVisibility(state.isComplete) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color  = t.success.copy(.15f),
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.success.copy(.5f)),
                ) {
                    Column(Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉  SOLVED!", color = t.success,
                            fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("${state.moves} moves  ·  $timer",
                            color = t.textSecondary, fontSize = 12.sp)
                    }
                }
            }

            // ── Board ─────────────────────────────────────────────────────────
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
                            boardCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(t.surface)
                        .border(1.dp, t.border, RoundedCornerShape(12.dp))
                        .padding(6.dp)
                ) {
                    val n        = state.size
                    val gap      = 5.dp
                    val cellSize = (maxWidth - gap * (n + 1)) / n
                    val cellPx   = with(LocalDensity.current) { cellSize.toPx() }
                    val gapPx    = with(LocalDensity.current) { gap.toPx() }

                    // Background empty slots
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        Spacer(Modifier.height(gap))
                        repeat(n) {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gap)) {
                                Spacer(Modifier.width(gap))
                                repeat(n) {
                                    Box(
                                        Modifier.size(cellSize)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(t.border.copy(.3f))
                                    )
                                }
                            }
                        }
                    }

                    // Tile layer — positioned absolutely so they can animate
                    state.tiles.forEachIndexed { currentIdx, value ->
                        if (value == 0) return@forEachIndexed  // skip blank

                        val targetRow = currentIdx / n
                        val targetCol = currentIdx % n
                        val targetX   = gapPx + targetCol * (cellPx + gapPx)
                        val targetY   = gapPx + targetRow * (cellPx + gapPx)

                        val animX by animateFloatAsState(
                            targetX,
                            spring(Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "sx_$value",
                        )
                        val animY by animateFloatAsState(
                            targetY,
                            spring(Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "sy_$value",
                        )

                        val isInPlace = value == currentIdx + 1 ||
                            (currentIdx == n * n - 1 && value == 0)
                        val isHint    = state.hintBlank == currentIdx

                        SlideTile(
                            value    = value,
                            size     = cellSize,
                            isInPlace = isInPlace,
                            isHint   = isHint,
                            theme    = t,
                            modifier = Modifier.absoluteOffset {
                                IntOffset(animX.toInt(), animY.toInt())
                            },
                        ) {
                            hub.sound.play(Sfx.TAP)
                            vm.slideTile(currentIdx)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Tap a tile next to the blank space to slide it into place.",
                color = t.textSecondary, fontSize = 11.sp, textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            // Controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemedButton("💡 Hint",  { hub.sound.play(Sfx.TAP); vm.hint() },
                    Modifier.weight(1f), outlined = true)
                ThemedButton("↺ New",   { hub.sound.play(Sfx.TAP); vm.newGame(state.difficulty) },
                    Modifier.weight(1f), outlined = true)
            }

            Spacer(Modifier.height(8.dp))

            // Difficulty tabs
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(t.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                SlideGrid.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
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

            Spacer(Modifier.height(16.dp))
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tile composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlideTile(
    value: Int,
    size: Dp,
    isInPlace: Boolean,
    isHint: Boolean,
    theme: GameTheme,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        when {
            isInPlace -> theme.primary.copy(.25f)
            isHint    -> theme.warning.copy(.25f)
            else      -> theme.surfaceVariant
        },
        tween(200), label = "tile_bg",
    )
    val borderColor by animateColorAsState(
        when {
            isInPlace -> theme.primary.copy(.6f)
            isHint    -> theme.warning.copy(.6f)
            else      -> theme.border
        },
        tween(200), label = "tile_border",
    )
    val scale by animateFloatAsState(
        if (isHint) 0.95f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "tile_scale",
    )

    Box(
        modifier
            .size(size)
            .scale(scale)
            .shadow(if (isHint) 8.dp else 3.dp, RoundedCornerShape(8.dp),
                spotColor = theme.primary.copy(if (isHint) .4f else .15f))
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$value",
            color = when {
                isInPlace -> theme.primary
                isHint    -> theme.warning
                else      -> theme.textPrimary
            },
            fontSize   = (size.value * 0.35f).sp,
            fontWeight = FontWeight.Black,
        )
    }
}
