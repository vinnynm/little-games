package com.enigma.littlegames.ui.games.twentyfortyeight

// ─────────────────────────────────────────────────────────────────────────────
// 2048 Screen
// Swipe gestures via detectDragGestures.
// Tiles animate position with animateOffsetAsState.
// Tile colour gradients keyed to value, themed to the active hub theme.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalDensity
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
fun TwentyFortyEightScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: TwentyFortyEightViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    var boardCenter by remember { mutableStateOf(Offset(400f, 600f)) }

    // Win / merge sounds
    LaunchedEffect(state.isWon) {
        if (state.isWon) {
            val maxTile = state.tiles.maxOfOrNull { it.value } ?: 0
            hub.recordTFEScore(state.score, maxTile)
            hub.sound.play(Sfx.VICTORY)
            particles.burst(
                center = boardCenter,
                colors = listOf(t.primary, t.warning, Color.White, t.accent),
                count = 70, speed = 0.4f,
            )
        }
    }
    LaunchedEffect(state.isOver) {
        if (state.isOver) {
            val maxTile = state.tiles.maxOfOrNull { it.value } ?: 0
            hub.recordTFEScore(state.score, maxTile)
        }
    }
    LaunchedEffect(state.lastMergeScore) {
        if (state.lastMergeScore > 0) hub.sound.play(Sfx.SUDOKU_PLACE)
    }

    // Drag state
    var dragAccum by remember { mutableStateOf(Offset.Zero) }
    val swipeThreshold = 40f

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
                title    = "2048",
                subtitle = "Swipe to merge tiles · reach 2048",
                onBack   = { hub.navigate(HubScreen.Home) },
            )

            // Score strip
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScoreBox("SCORE", "${state.score}", t, Modifier.weight(1f))
                ScoreBox("BEST",  "${state.bestScore}", t, Modifier.weight(1f))
            }

            // Win overlay
            AnimatedVisibility(state.isWon) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color  = t.warning.copy(.15f),
                    shape  = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, t.warning.copy(.5f)),
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉  You reached 2048!", color = t.warning,
                            fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemedButton("Keep Going", { vm.keepPlaying() }, Modifier.weight(1f))
                            ThemedButton("New Game",  { hub.sound.play(Sfx.TAP); vm.newGame() },
                                Modifier.weight(1f), outlined = true)
                        }
                    }
                }
            }

            AnimatedVisibility(state.isOver) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color  = Color(0xFFE94560).copy(.12f),
                    shape  = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE94560).copy(.4f)),
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💥  Game Over!", color = Color(0xFFE94560),
                            fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text("Score: ${state.score}", color = t.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        ThemedButton("↺  New Game", { hub.sound.play(Sfx.TAP); vm.newGame() },
                            Modifier.fillMaxWidth())
                    }
                }
            }

            // ── Board ─────────────────────────────────────────────────────────
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
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragAccum = Offset.Zero },
                            onDragEnd = {
                                val dx = dragAccum.x
                                val dy = dragAccum.y
                                if (abs(dx) > swipeThreshold || abs(dy) > swipeThreshold) {
                                    val dir = if (abs(dx) > abs(dy)) {
                                        if (dx > 0) SwipeDir.RIGHT else SwipeDir.LEFT
                                    } else {
                                        if (dy > 0) SwipeDir.DOWN else SwipeDir.UP
                                    }
                                    hub.sound.play(Sfx.ROTATE)
                                    vm.swipe(dir)
                                }
                                dragAccum = Offset.Zero
                            },
                            onDrag = { _, delta -> dragAccum += delta },
                        )
                    }
                    .padding(8.dp)
            ) {
                val gap      = 6.dp
                val cellSize = (maxWidth - gap * 5) / 4

                // Empty cell grid background
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(4) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            repeat(4) {
                                Box(
                                    Modifier.size(cellSize).clip(RoundedCornerShape(8.dp))
                                        .background(t.border.copy(.5f))
                                )
                            }
                        }
                    }
                }

                // Tiles overlay (positioned absolutely)
                state.tiles.forEach { tile ->
                    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
                    val gapPx  = with(LocalDensity.current) { gap.toPx() }
                    val targetX = tile.col * (cellPx + gapPx)
                    val targetY = tile.row * (cellPx + gapPx)

                    val animX by animateFloatAsState(targetX, spring(stiffness = Spring.StiffnessMedium), label = "tx_${tile.id}")
                    val animY by animateFloatAsState(targetY, spring(stiffness = Spring.StiffnessMedium), label = "ty_${tile.id}")

                    val scale by animateFloatAsState(
                        1f, spring(Spring.DampingRatioMediumBouncy), label = "ts_${tile.id}"
                    )

                    Box(
                        Modifier
                            .absoluteOffset { IntOffset(animX.toInt(), animY.toInt()) }
                            .size(cellSize)
                            .scale(scale)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tileColor(tile.value, t)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${tile.value}",
                            color = if (tile.value <= 4) t.textSecondary else t.textPrimary,
                            fontSize = when {
                                tile.value < 100  -> (cellSize.value * 0.35f).sp
                                tile.value < 1000 -> (cellSize.value * 0.28f).sp
                                else              -> (cellSize.value * 0.22f).sp
                            },
                            fontWeight = FontWeight.Black,
                            textAlign  = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Swipe hint / controls
            Text(
                "↑ ↓ ← →  Swipe to slide tiles",
                color = t.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            ThemedButton(
                "↺  New Game",
                onClick = { hub.sound.play(Sfx.TAP); vm.newGame() },
                modifier = Modifier.fillMaxWidth(),
                outlined = true,
            )

            Spacer(Modifier.height(16.dp))
        }

        ParticleOverlay(particles)
    }
}

// ── Tile colours ──────────────────────────────────────────────────────────────
// Progressive brightness keyed to tile value, blended with the active theme.

private fun tileColor(value: Int, t: GameTheme): Color = when (value) {
    2     -> t.surface
    4     -> t.surfaceVariant
    8     -> t.primary.copy(.25f)
    16    -> t.primary.copy(.40f)
    32    -> t.primary.copy(.55f)
    64    -> t.primary.copy(.70f)
    128   -> t.secondary.copy(.55f)
    256   -> t.secondary.copy(.70f)
    512   -> t.accent.copy(.60f)
    1024  -> t.accent.copy(.80f)
    2048  -> t.warning
    else  -> t.warning  // 4096+
}

@Composable
private fun ScoreBox(label: String, value: String, t: GameTheme, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(t.surface)
            .border(1.dp, t.border, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = t.textSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(value, color = t.primary, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}
