package com.enigma.littlegames.ui.games.minesweeper

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.common.*
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem

private val ADJ_COLOURS = listOf(
    Color.Transparent,
    Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFEF5350), Color(0xFFAB47BC),
    Color(0xFFFF7043), Color(0xFF26C6DA), Color(0xFFFFCA28), Color(0xFF78909C),
)

@Composable
fun MinesweeperScreen(hub: HubViewModel) {
    val t = LocalGameTheme.current
    val vm: MinesweeperViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    val haptic = LocalHapticFeedback.current
    var boardCenter by remember { mutableStateOf(Offset(400f, 500f)) }

    LaunchedEffect(state.phase) {
        when (state.phase) {
            MinesweeperPhase.WON -> {
                hub.recordMinesweeperWin(state.difficulty.label, state.elapsedSecs)
                hub.sound.play(Sfx.VICTORY)
                particles.burst(
                    center = boardCenter,
                    colors = listOf(t.primary, t.accent, Color.White, t.warning, t.success),
                    count = 80, speed = 0.5f,
                )
            }
            MinesweeperPhase.LOST -> hub.sound.play(Sfx.FLOW_FAIL)
            else -> {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().background(t.background).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val timer = formatTime(state.elapsedSecs)

            GameTopBar(
                title = "MINESWEEPER",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}  ·  ${state.difficulty.rows}×${state.difficulty.cols}",
                onBack = { hub.navigate(HubScreen.Home) },
                actions = {
                    MineCounterDisplay(remaining = state.mineCount - state.flagCount, theme = t)
                    Spacer(Modifier.width(12.dp))
                    TimerDisplay(timer, t)
                }
            )

            StatsRow(state, t)
            PhaseBanner(state.phase, timer, t)

            Spacer(Modifier.height(4.dp))

            // ── Board ─────────────────────────────────────────────────────────
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        boardCenter = Offset(coords.size.width / 2f, coords.size.height / 2f)
                    }
            ) {
                val cols = state.difficulty.cols
                val rows = state.difficulty.rows
                val cellSize = (maxWidth / cols).coerceAtMost(maxHeight / rows)

                Column(
                    Modifier.wrapContentSize().align(Alignment.Center),
                    verticalArrangement = Arrangement.Center,
                ) {
                    for (r in 0 until rows) {
                        Row {
                            for (c in 0 until cols) {
                                val cell = state.board.getOrNull(r)?.getOrNull(c) ?: continue
                                MineCellView(
                                    cell = cell,
                                    size = cellSize,
                                    theme = t,
                                    locked = state.phase == MinesweeperPhase.WON || state.phase == MinesweeperPhase.LOST,
                                    onTap = { hub.sound.play(Sfx.TAP); vm.reveal(r, c) },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        hub.sound.play(Sfx.ROTATE)
                                        vm.toggleFlag(r, c)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            DifficultySelector(state, t) { d -> hub.sound.play(Sfx.TAP); vm.newGame(d) }

            Spacer(Modifier.height(8.dp))

            ThemedButton(
                text = "↺ New Game",
                onClick = { hub.sound.play(Sfx.TAP); vm.newGame(state.difficulty) },
                modifier = Modifier.fillMaxWidth(),
                outlined = true,
            )

            Spacer(Modifier.height(16.dp))
        }

        ParticleOverlay(particles)
    }
}

// ── Cell ─────────────────────────────────────────────────────────────────────

@Composable
private fun MineCellView(
    cell: MineCell,
    size: Dp,
    theme: GameTheme,
    locked: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val bg = when {
        cell.isMine && cell.isRevealed -> Color(0xFFE53935).copy(alpha = 0.55f)
        cell.isRevealed -> theme.surfaceVariant
        cell.isFlagged -> theme.warning.copy(alpha = 0.2f)
        else -> theme.surface
    }
    val border = when {
        cell.isWrongFlag -> Color(0xFFE53935)
        cell.isFlagged -> theme.warning.copy(alpha = 0.5f)
        else -> theme.border.copy(alpha = 0.35f)
    }

    Box(
        Modifier
            .size(size)
            .padding(0.5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .border(0.8.dp, border, RoundedCornerShape(3.dp))
            .pointerInput(cell.row, cell.col, locked) {
                if (locked) return@pointerInput
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            cell.isMine && cell.isRevealed -> Text("💣", fontSize = (size.value * 0.5f).sp)
            cell.isFlagged -> Text(
                "🚩",
                fontSize = (size.value * 0.45f).sp,
                color = if (cell.isWrongFlag) Color(0xFFE53935) else Color.Unspecified,
            )
            cell.isRevealed && cell.adjMines > 0 -> Text(
                "${cell.adjMines}",
                color = ADJ_COLOURS.getOrElse(cell.adjMines) { theme.textPrimary },
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Black,
            )
            else -> {}
        }
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun MineCounterDisplay(remaining: Int, theme: GameTheme) {
    val displayValue = remaining.coerceIn(-99, 999).toString().padStart(3, ' ')
    Surface(
        color = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp),
        contentColor = if (remaining <= 0) theme.success else Color(0xFFFF5252),
    ) {
        Text(
            displayValue,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun TimerDisplay(time: String, theme: GameTheme) {
    Surface(
        color = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp),
        contentColor = theme.primary,
    ) {
        Text(
            time,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun StatsRow(state: MinesweeperState, t: GameTheme) {
    val progress = if (state.safeCount > 0) state.revealCount.toFloat() / state.safeCount.toFloat() else 0f

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniStat("FLAGS", "${state.flagCount}")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(progress * 100).toInt()}%", color = t.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = t.primary,
                trackColor = t.surface,
                strokeCap = StrokeCap.Round,
            )
        }
        MiniStat("REMAIN", "${state.safeCount - state.revealCount}")
    }
}

@Composable
private fun PhaseBanner(phase: MinesweeperPhase, timer: String, t: GameTheme) {
    AnimatedVisibility(
        visible = phase == MinesweeperPhase.WON,
        enter = fadeIn(tween(300)) + expandVertically(),
        exit = fadeOut(tween(200)) + shrinkVertically(),
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            color = t.success.copy(alpha = 0.15f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, t.success.copy(alpha = 0.5f)),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("🎉 CLEARED!", color = t.success, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(" · $timer", color = t.textSecondary, fontSize = 11.sp)
            }
        }
    }

    AnimatedVisibility(
        visible = phase == MinesweeperPhase.LOST,
        enter = fadeIn(tween(300)) + expandVertically(),
        exit = fadeOut(tween(200)) + shrinkVertically(),
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            color = Color(0xFFE53935).copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.4f)),
        ) {
            Text(
                "💥 BOOM! Try again.",
                color = Color(0xFFE53935), fontWeight = FontWeight.Black, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp),
            )
        }
    }

    AnimatedVisibility(visible = phase == MinesweeperPhase.IDLE, enter = fadeIn(), exit = fadeOut()) {
        Text("Tap to reveal · Long-press to flag", color = t.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun DifficultySelector(state: MinesweeperState, t: GameTheme, onSelect: (MineDifficulty) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.surface).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MineDifficulty.entries.forEach { d ->
            val sel = state.difficulty == d
            Surface(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).clickable { onSelect(d) },
                color = if (sel) t.primary else Color.Transparent,
                shape = RoundedCornerShape(7.dp),
                border = if (!sel) BorderStroke(1.dp, t.border.copy(alpha = 0.3f)) else null,
            ) {
                Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "${d.emoji} ${d.label}",
                        color = if (sel) t.background else t.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (sel) FontWeight.Black else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun formatTime(secs: Long): String {
    val m = (secs / 60).coerceAtMost(99)
    val s = secs % 60
    return "%02d:%02d".format(m, s)
}
