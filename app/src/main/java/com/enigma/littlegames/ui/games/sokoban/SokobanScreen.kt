package com.enigma.littlegames.ui.games.sokoban

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.common.*
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SokobanScreen(hub: HubViewModel) {
    val t = LocalGameTheme.current
    val vm: SokobanViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    var boardCenter by remember { mutableStateOf(Offset(400f, 500f)) }

    // Timer tick
    LaunchedEffect(state.isLoading, state.isComplete) {
        if (!state.isLoading && !state.isComplete) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                vm.tickTimer()
            }
        }
    }

    // Victory effects
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            hub.recordSokobanWin(state.level, state.board?.moveCount ?: 0)
            hub.sound.play(Sfx.VICTORY)
            particles.burst(
                center = boardCenter,
                colors = listOf(t.primary, t.accent, Color.White, t.warning),
                count = 80, speed = 0.4f,
            )
        }
    }

    // Deadlock sound
    LaunchedEffect(state.isDeadlocked) {
        if (state.isDeadlocked) {
            hub.sound.play(Sfx.SUDOKU_ERROR)
        }
    }

    // Swipe state
    var dragAccum by remember { mutableStateOf(Offset.Zero) }
    val swipeThreshold = 30f

    // Level Select Overlay
    if (state.showLevelSelect) {
        LevelSelectOverlay(
            state = state,
            theme = t,
            onSelect = { vm.hideLevelSelect(); vm.loadLevel(it) },
            onClose = { vm.hideLevelSelect() },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .background(t.background)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top Bar ───────────────────────────────────────────────────────
            GameTopBar(
                title = "SOKOBAN",
                subtitle = state.levelName.ifEmpty { "Level ${state.level}" },
                onBack = { hub.navigate(HubScreen.Home) },
                actions = {
                    // Level select button
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(t.surface)
                            .border(1.dp, t.border, RoundedCornerShape(8.dp))
                            .clickable { hub.sound.play(Sfx.TAP); vm.showLevelSelect() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("☰", color = t.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    // Undo button
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(t.surface)
                            .border(1.dp, t.border, RoundedCornerShape(8.dp))
                            .clickable { hub.sound.play(Sfx.TAP); vm.undo() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("↩", color = t.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            )

            // ── Stats Row ────────────────────────────────────────────────────
            val board = state.board
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniStat("MOVES", "${board?.moveCount ?: 0}")
                MiniStat("PUSHES", "${board?.pushCount ?: 0}")
                MiniStat("TIME", formatTime(state.elapsedSeconds))
                val best = state.progress[state.level]?.bestMoves
                MiniStat("BEST", if (best != null) "$best" else "—")
            }

            // ── Level Progress Bar ────────────────────────────────────────────
            val pct = state.level.toFloat() / state.totalLevels
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = t.primary, trackColor = t.border,
            )
            Spacer(Modifier.height(4.dp))

            // ── Deadlock Warning ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.isDeadlocked && !state.isComplete,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
            ) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    color = t.error.copy(0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.error.copy(0.5f)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Deadlock detected! A box is stuck.",
                            color = t.error, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { hub.sound.play(Sfx.TAP); vm.undo() },
                            colors = ButtonDefaults.textButtonColors(contentColor = t.error),
                        ) {
                            Text("Undo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Complete Banner ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.isComplete,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(),
            ) {
                val levelDef = SOKOBAN_LEVELS.getOrNull(state.level - 1)
                val stars = state.progress[state.level]?.stars ?: StarRating.ONE
                CompleteBanner(
                    theme = t,
                    level = state.level,
                    moves = board?.moveCount ?: 0,
                    pushes = board?.pushCount ?: 0,
                    stars = stars,
                    par = levelDef?.par,
                    isLastLevel = state.level >= state.totalLevels,
                    onNext = { hub.sound.play(Sfx.TAP); vm.nextLevel() },
                    onRestart = { hub.sound.play(Sfx.TAP); vm.restart() },
                )
            }

            // ── Board ────────────────────────────────────────────────────────
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
                    CircularProgressIndicator(color = t.primary, strokeWidth = 3.dp)
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── D-pad Controls ───────────────────────────────────────────────
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

            // ── Bottom Nav ───────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemedButton("◀", { hub.sound.play(Sfx.TAP); vm.prevLevel() },
                    Modifier.size(48.dp), outlined = true)
                ThemedButton("↺ Restart", { hub.sound.play(Sfx.TAP); vm.restart() },
                    Modifier.weight(1f), outlined = true)
                ThemedButton("▶", { hub.sound.play(Sfx.TAP); vm.nextLevel() },
                    Modifier.size(48.dp), outlined = true)
            }
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Complete Banner with Stars
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompleteBanner(
    theme: GameTheme,
    level: Int,
    moves: Int,
    pushes: Int,
    stars: StarRating,
    par: Int?,
    isLastLevel: Boolean,
    onNext: () -> Unit,
    onRestart: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        color = theme.success.copy(0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, theme.success.copy(0.4f)),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stars
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                repeat(3) { i ->
                    val filled = i < when (stars) {
                        StarRating.THREE -> 3
                        StarRating.TWO -> 2
                        StarRating.ONE -> 1
                        StarRating.NONE -> 0
                    }
                    Text(
                        if (filled) "⭐" else "☆",
                        fontSize = 24.sp,
                        color = if (filled) Color(0xFFFFD700) else theme.border,
                    )
                }
            }

            Text(
                "Level $level Complete!",
                color = theme.success, fontWeight = FontWeight.Black, fontSize = 16.sp
            )

            Row(
                Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("$moves moves", color = theme.textSecondary, fontSize = 12.sp)
                Text("$pushes pushes", color = theme.textSecondary, fontSize = 12.sp)
                if (par != null) {
                    Text(
                        "par $par",
                        color = if (moves <= par) theme.success else theme.warning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (!isLastLevel) {
                ThemedButton("Next Level ▶", onNext, Modifier.fillMaxWidth())
            } else {
                Text(
                    "🏆 All levels complete!",
                    color = theme.warning,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(6.dp))
                ThemedButton("Play Again", { onRestart() }, Modifier.fillMaxWidth())
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level Select Overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LevelSelectOverlay(
    state: SokobanUiState,
    theme: GameTheme,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val tiers = listOf(
        "Tutorial" to (1..10),
        "Easy" to (11..20),
        "Medium" to (21..30),
        "Hard" to (31..40),
        "Expert" to (41..50),
    )

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            color = theme.background,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, theme.border),
        ) {
            Column(Modifier.padding(16.dp)) {
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Select Level",
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                    )
                    IconButton(onClick = onClose) {
                        Text("✕", color = theme.textSecondary, fontSize = 18.sp)
                    }
                }

                // Level grid
                LazyVerticalColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    tiers.forEach { (tierName, range) ->
                        item(key = tierName) {
                            Text(
                                tierName.uppercase(),
                                color = theme.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 4.dp),
                            ) {
                                range.forEach { lvl ->
                                    val progress = state.progress[lvl]
                                    val isCompleted = progress?.completed == true
                                    val isCurrent = lvl == state.level

                                    Box(
                                        Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    isCurrent -> theme.primary.copy(0.2f)
                                                    isCompleted -> theme.success.copy(0.1f)
                                                    else -> theme.surface
                                                }
                                            )
                                            .border(
                                                width = if (isCurrent) 2.dp else 1.dp,
                                                color = when {
                                                    isCurrent -> theme.primary
                                                    isCompleted -> theme.success.copy(0.5f)
                                                    else -> theme.border
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                            )
                                            .clickable { onSelect(lvl) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "$lvl",
                                                color = when {
                                                    isCurrent -> theme.primary
                                                    isCompleted -> theme.success
                                                    else -> theme.textSecondary
                                                },
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            if (isCompleted && progress != null) {
                                                Text(
                                                    when (progress.stars) {
                                                        StarRating.THREE -> "⭐⭐⭐"
                                                        StarRating.TWO -> "⭐⭐"
                                                        StarRating.ONE -> "⭐"
                                                        StarRating.NONE -> ""
                                                    },
                                                    fontSize = 6.sp,
                                                    lineHeight = 6.sp,
                                                )
                                            }
                                        }
                                    }
                                    if (lvl == range.last && lvl % 5 != 0) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyVerticalColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(modifier = modifier, verticalArrangement = verticalArrangement, content = content)
}

// ─────────────────────────────────────────────────────────────────────────────
// Enhanced Board Renderer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SokobanGrid(board: SokobanBoard, theme: GameTheme) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellW = maxWidth / board.width
        val cellH = maxHeight / board.height
        val cellSize = minOf(cellW, cellH).coerceAtMost(48.dp)
        val fontSize = (cellSize.value * 0.5f).sp

        // Animate player appearance
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val playerScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "player_pulse",
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            for (r in 0 until board.height) {
                Row {
                    for (c in 0 until board.width) {
                        val pos = r to c
                        val isWall = pos in board.walls
                        val isFloor = pos in board.floor || pos in board.targets
                        val isTarget = pos in board.targets
                        val isBox = pos in board.boxes
                        val isPlayer = r == board.playerRow && c == board.playerCol

                        // Only render cells that are part of the level
                        if (!isWall && !isFloor && !isBox && !isPlayer && !isTarget) {
                            Box(Modifier.size(cellSize))
                            continue
                        }

                        Box(
                            Modifier
                                .size(cellSize)
                                .then(
                                    when {
                                        isWall -> Modifier
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        theme.border.copy(0.7f),
                                                        theme.border.copy(0.5f),
                                                    )
                                                )
                                            )
                                            .border(
                                                0.5.dp,
                                                theme.border.copy(0.3f),
                                            )
                                        isTarget && !isBox -> Modifier
                                            .background(theme.primary.copy(0.08f))
                                            .border(1.5.dp, theme.primary.copy(0.35f))
                                        isFloor -> Modifier.background(theme.surface.copy(0.3f))
                                        else -> Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isWall -> {
                                    // Wall pattern
                                    Canvas(Modifier.fillMaxSize()) {
                                        drawRect(
                                            color = theme.border.copy(0.2f),
                                            topLeft = Offset(0f, size.height * 0.5f),
                                            size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.5f),
                                        )
                                    }
                                }

                                isPlayer -> {
                                    val emoji = if (isTarget) "😊" else when (board.playerDir) {
                                        SokoDir.UP -> "🧑‍🦯"
                                        SokoDir.DOWN -> "🧑"
                                        SokoDir.LEFT -> "🧑‍🦯"
                                        SokoDir.RIGHT -> "🧑‍🦯"
                                    }
                                    Box(Modifier.scale(playerScale)) {
                                        Text(emoji, fontSize = fontSize * 1.1f)
                                    }
                                }

                                isBox && isTarget -> {
                                    // Box on target - green tint
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(theme.success.copy(0.2f))
                                            .border(1.5.dp, theme.success.copy(0.5f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("📦", fontSize = fontSize)
                                    }
                                }

                                isBox -> {
                                    Text("📦", fontSize = fontSize)
                                }

                                isTarget -> {
                                    // Target marker
                                    Text("🎯", fontSize = fontSize * 0.8f, color = theme.primary.copy(0.6f))
                                }

                                else -> { /* empty floor */ }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// D-pad Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DPadButton(label: String, t: GameTheme, onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.85f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "dpad",
    )
    Box(
        Modifier
            .size(54.dp)
            .scale(scale)
            .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = t.primary.copy(0.2f))
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isPressed) t.primary.copy(0.15f) else t.surface
            )
            .border(1.5.dp, t.primary.copy(0.4f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (isPressed) t.primary else t.primary.copy(0.8f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "$m:${s.toString().padStart(2, '0')}" else "${s}s"
}