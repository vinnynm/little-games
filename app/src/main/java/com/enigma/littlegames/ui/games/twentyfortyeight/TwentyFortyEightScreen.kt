package com.enigma.littlegames.ui.games.twentyfortyeight

// ─────────────────────────────────────────────────────────────────────────────
// 2048 Screen
// Swipe detection via pointerInput; tile colours per value mapped to theme.
// animateFloatAsState used for score bump; each tile keyed by stable ID.
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
import androidx.compose.ui.text.font.FontWeight
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
    var boardCenter by remember { mutableStateOf(Offset(400f, 500f)) }

    LaunchedEffect(Unit) {
        vm.onWin  = { score ->
            hub.record2048Win(score)
            hub.sound.play(Sfx.VICTORY)
            particles.burst(boardCenter,
                listOf(Color(0xFFFFD700), Color(0xFFFFA500), Color.White, t.primary), 80, 0.4f)
        }
        vm.onBest = { hub.record2048Best(it) }
    }

    // Swipe gesture state
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    val swipeThreshold = 40f

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragTotal = Offset.Zero },
                    onDragEnd   = {
                        val dx = dragTotal.x; val dy = dragTotal.y
                        if (maxOf(abs(dx), abs(dy)) >= swipeThreshold) {
                            val dir = if (abs(dx) > abs(dy)) {
                                if (dx > 0) SwipeDir.RIGHT else SwipeDir.LEFT
                            } else {
                                if (dy > 0) SwipeDir.DOWN else SwipeDir.UP
                            }
                            hub.sound.play(Sfx.TAP)
                            vm.swipe(dir)
                        }
                        dragTotal = Offset.Zero
                    },
                ) { _, delta -> dragTotal += delta }
            }
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().background(t.background).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                title    = "2048",
                subtitle = "Swipe to merge — reach 2048!",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    ThemedButton("New", vm::newGame, outlined = true)
                }
            )

            // Score row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("SCORE", "${state.score}")
                MiniStat("BEST",  "${state.best}")
                MiniStat("TILES", "${state.tiles.size}/16")
            }

            Spacer(Modifier.height(16.dp))

            // Win / game over banners
            AnimatedVisibility(state.won && !state.keepPlaying) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = Color(0xFFFFD700).copy(.15f), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(.6f))
                ) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🏆 YOU REACHED 2048!", color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ThemedButton("Keep Going", vm::keepPlaying, outlined = true)
                            ThemedButton("New Game",   vm::newGame)
                        }
                    }
                }
            }

            AnimatedVisibility(state.isOver) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = t.error.copy(.15f), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, t.error.copy(.5f))
                ) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("💀 GAME OVER — Score: ${state.score}", color = t.error,
                            fontWeight = FontWeight.Black, fontSize = 14.sp)
                        ThemedButton("New Game", vm::newGame, Modifier.fillMaxWidth())
                    }
                }
            }

            // Board
            BoxWithConstraints(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.surface)
                    .border(1.dp, t.border, RoundedCornerShape(12.dp))
                    .onGloballyPositioned { c ->
                        boardCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                    }
                    .padding(8.dp)
            ) {
                val cellSize = (maxWidth - 24.dp) / 4   // 3 gaps between 4 cells

                // Empty cell grid (background)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(4) {
                                Box(
                                    Modifier.size(cellSize).clip(RoundedCornerShape(8.dp))
                                        .background(t.surfaceVariant)
                                )
                            }
                        }
                    }
                }

                // Tile layer
                state.tiles.forEach { tile ->
                    key(tile.id) {
                        TileView(tile, cellSize, t)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Swipe hint
            Text("← Swipe to slide tiles →", color = t.textSecondary, fontSize = 11.sp)

            Spacer(Modifier.height(8.dp))

            // D-pad fallback buttons
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemedButton("▲", { vm.swipe(SwipeDir.UP) },
                    Modifier.size(48.dp), outlined = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemedButton("◀", { vm.swipe(SwipeDir.LEFT) },
                        Modifier.size(48.dp), outlined = true)
                    ThemedButton("▶", { vm.swipe(SwipeDir.RIGHT) },
                        Modifier.size(48.dp), outlined = true)
                }
                ThemedButton("▼", { vm.swipe(SwipeDir.DOWN) },
                    Modifier.size(48.dp), outlined = true)
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
private fun BoxWithConstraintsScope.TileView(tile: Tile, cellSize: Dp, t: GameTheme) {
    val gap = 6.dp

    // Target position (absolute within BoxWithConstraints)
    val targetX = tile.col * (cellSize + gap)
    val targetY = tile.row * (cellSize + gap)

    val animX by animateDpAsState(targetX, spring(Spring.DampingRatioMediumBouncy, stiffness = 300f), label = "tx")
    val animY by animateDpAsState(targetY, spring(Spring.DampingRatioMediumBouncy, stiffness = 300f), label = "ty")

    val tileColor = tileColor(tile.value, t)
    val textColor = if (tile.value <= 4) t.background else Color.White

    Box(
        Modifier
            .offset(animX, animY)
            .size(cellSize)
            .clip(RoundedCornerShape(8.dp))
            .background(tileColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${tile.value}",
            color      = textColor,
            fontSize   = when {
                tile.value >= 1024 -> (cellSize.value * 0.22f).sp
                tile.value >= 128  -> (cellSize.value * 0.26f).sp
                else               -> (cellSize.value * 0.32f).sp
            },
            fontWeight = FontWeight.Black,
        )
    }
}

/** Map tile value to a colour that reads well against hub themes. */
private fun tileColor(value: Int, t: GameTheme): Color = when (value) {
    2     -> t.surfaceVariant
    4     -> t.border
    8     -> t.primary.copy(.5f)
    16    -> t.primary.copy(.65f)
    32    -> t.primary.copy(.75f)
    64    -> t.primary
    128   -> t.secondary
    256   -> t.secondary.copy(.85f)
    512   -> t.accent
    1024  -> t.warning
    2048  -> Color(0xFFFFD700)
    else  -> Color(0xFFFFD700).copy(.8f) // 4096+
}
