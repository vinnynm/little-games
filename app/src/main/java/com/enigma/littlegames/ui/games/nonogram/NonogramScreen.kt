package com.enigma.littlegames.ui.games.nonogram

// ─────────────────────────────────────────────────────────────────────────────
// Nonogram Screen
// Tap model:
//   • Single tap  → toggle FILLED / EMPTY
//   • Long press  → toggle CROSSED / EMPTY  (mark cell as definitely empty)
// This makes the most common action (filling a cell) a simple tap, while
// marking empties (which you need less often) is a long press.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.common.GameTheme
import com.enigma.littlegames.common.GameTopBar
import com.enigma.littlegames.common.HubScreen
import com.enigma.littlegames.common.HubViewModel
import com.enigma.littlegames.common.LocalGameTheme
import com.enigma.littlegames.common.MiniStat
import com.enigma.littlegames.common.ParticleOverlay
import com.enigma.littlegames.common.ThemedButton
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem

@Composable
fun NonogramScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: NonogramViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    val haptic    = LocalHapticFeedback.current
    var gridCenter by remember { mutableStateOf(Offset(400f, 500f)) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            hub.recordNonogramWin(
                difficulty  = state.difficulty.label,
                errorCount  = state.errorCount,
                elapsedSecs = state.elapsedSecs,
                size        = state.size,
            )
            hub.sound.play(Sfx.VICTORY)
            particles.burst(
                center = gridCenter,
                colors = listOf(t.primary, t.secondary, t.accent, Color.White, t.warning),
                count  = 90, speed = 0.45f,
            )
        }
    }

    LaunchedEffect(state.errorCount) {
        if (state.errorCount > 0 && !state.isComplete) hub.sound.play(Sfx.SUDOKU_ERROR)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .background(t.background)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val timer = remember(state.elapsedSecs) {
                "%02d:%02d".format(state.elapsedSecs / 60, state.elapsedSecs % 60)
            }

            GameTopBar(
                title    = "NONOGRAM",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}  ·  ${state.size}×${state.size}",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text(timer, color = t.primary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp))
                }
            )

            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("ERRORS", "${state.errorCount}")
                val filled = state.playerGrid.sumOf { r -> r.count { it == CellMark.FILLED } }
                val total  = state.solution.sumOf { r -> r.count { it } }
                MiniStat("FILLED", "$filled/$total")
                MiniStat("TIME",   timer)
            }

            // Gesture legend
            Surface(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                color  = t.surface,
                shape  = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, t.border),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendItem("Tap", "■ Fill / clear", t.primary, t)
                    Text("·", color = t.border, fontSize = 16.sp)
                    LegendItem("Long press", "✕ Mark empty", t.textSecondary, t)
                }
            }

            AnimatedVisibility(state.isComplete) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color  = t.success.copy(.15f),
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.success.copy(.5f)),
                ) {
                    Row(Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("🎉  PICTURE REVEALED!", color = t.success,
                            fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("  ·  ${state.errorCount} errors  ·  $timer",
                            color = t.textSecondary, fontSize = 11.sp)
                    }
                }
            }

            // ── Nonogram grid ─────────────────────────────────────────────────
            if (state.generating) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = t.primary)
                }
            } else {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { c ->
                            gridCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                        }
                ) {
                    val n = state.size
                    val maxRowClueLen = state.rowClues.maxOf { it.size }
                    val maxColClueLen = state.colClues.maxOf { it.size }

                    val clueColW = (maxRowClueLen * 14 + 4).dp.coerceAtLeast(28.dp)
                    val clueRowH = (maxColClueLen * 14 + 4).dp.coerceAtLeast(28.dp)

                    val gridW    = maxWidth - clueColW
                    val cellSize = gridW / n

                    Column {
                        // Column clue headers
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(clueColW))
                            for (c in 0 until n) {
                                NonogramClueCell(
                                    clues    = state.colClues[c],
                                    isCol    = true,
                                    size     = cellSize,
                                    height   = clueRowH,
                                    isSolved = isLineComplete(
                                        (0 until n).map { r -> state.playerGrid[r][c] },
                                        state.colClues[c],
                                    ),
                                    t = t,
                                )
                            }
                        }

                        // Grid rows
                        for (r in 0 until n) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                NonogramClueCell(
                                    clues    = state.rowClues[r],
                                    isCol    = false,
                                    size     = clueColW,
                                    height   = cellSize,
                                    isSolved = isLineComplete(
                                        state.playerGrid[r].toList(),
                                        state.rowClues[r],
                                    ),
                                    t = t,
                                )
                                for (c in 0 until n) {
                                    val mark    = state.playerGrid[r][c]
                                    val solFill = state.solution[r][c]
                                    val isErr   = (r to c) in state.errorCells
                                    val isHint  = (r to c) in state.hintCells
                                    val is5col  = (c % 5 == 4 && c < n - 1)
                                    val is5row  = (r % 5 == 4 && r < n - 1)

                                    NonogramCell(
                                        mark       = mark,
                                        solFill    = if (state.isComplete) solFill else false,
                                        isError    = isErr,
                                        isHint     = isHint,
                                        size       = cellSize,
                                        showRight  = is5col,
                                        showBottom = is5row,
                                        t          = t,
                                        onTap = {
                                            hub.sound.play(Sfx.TAP)
                                            vm.tapCell(r, c)
                                        },
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            hub.sound.play(Sfx.TAP)
                                            vm.longPressCell(r, c)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(color = t.primary, label = "Filled")
                    LegendDot(color = t.textSecondary, label = "✕ Empty")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemedButton("💡 Hint", {
                        hub.sound.play(Sfx.TAP); vm.hint()
                    }, outlined = true)
                    ThemedButton("↺ New", {
                        hub.sound.play(Sfx.TAP); vm.newGame(state.difficulty)
                    }, outlined = true)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(t.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                NonogramDifficulty.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                            .background(if (sel) t.primary else Color.Transparent)
                            .clickable { hub.sound.play(Sfx.TAP); vm.newGame(d) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.emoji} ${d.label.take(3)}",
                            color      = if (sel) t.background else t.textSecondary,
                            fontSize   = 10.sp,
                            fontWeight = if (sel) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Clue label cell
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NonogramClueCell(
    clues: List<Int>,
    isCol: Boolean,
    size: Dp,
    height: Dp,
    isSolved: Boolean,
    t: GameTheme,
) {
    val color = if (isSolved) t.success.copy(.8f) else t.textSecondary
    if (isCol) {
        Box(
            Modifier.width(size).height(height),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                clues.filter { it > 0 }.forEach { n ->
                    Text("$n", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp)
                }
            }
        }
    } else {
        Box(
            Modifier.width(size).height(height),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                clues.filter { it > 0 }.joinToString(" "),
                color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(end = 3.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid cell — tap fills, long press crosses
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NonogramCell(
    mark: CellMark,
    solFill: Boolean,
    isError: Boolean,
    isHint: Boolean,
    size: Dp,
    showRight: Boolean,
    showBottom: Boolean,
    t: GameTheme,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val fillFraction by animateFloatAsState(
        if (mark == CellMark.FILLED || solFill) 1f else 0f,
        tween(100), label = "nono_fill",
    )
    val bgColor = when {
        solFill  -> t.primary
        isError  -> Color(0xFFE94560)
        isHint   -> t.warning
        mark == CellMark.FILLED -> t.primary
        else     -> t.surface
    }

    Box(
        Modifier
            .size(size)
            .background(t.surface)
            .border(0.5.dp, t.border.copy(.35f))
            .then(if (showRight)  Modifier.border(BorderStroke(1.5.dp, t.primary.copy(.4f))).padding(end = 1.5.dp) else Modifier)
            .then(if (showBottom) Modifier.border(BorderStroke(1.5.dp, t.primary.copy(.4f))).padding(bottom = 1.5.dp) else Modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Filled square
        if (mark == CellMark.FILLED || solFill) {
            Box(
                Modifier
                    .fillMaxSize(fillFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(bgColor)
            )
        }
        // X mark for CROSSED
        if (mark == CellMark.CROSSED) {
            Canvas(Modifier.fillMaxSize(.7f)) {
                val p = size.toPx() * 0.7f * 0.5f
                drawLine(Color.Gray.copy(.7f), Offset(0f, 0f), Offset(p * 2, p * 2), 2f)
                drawLine(Color.Gray.copy(.7f), Offset(p * 2, 0f), Offset(0f, p * 2), 2f)
            }
        }
        // Hint pulse border
        if (isHint) {
            Box(Modifier.fillMaxSize().border(2.dp, t.warning, RoundedCornerShape(2.dp)))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun isLineComplete(marks: List<CellMark>, clues: List<Int>): Boolean {
    val runs = mutableListOf<Int>()
    var count = 0
    for (m in marks) {
        if (m == CellMark.FILLED) count++
        else { if (count > 0) { runs.add(count); count = 0 } }
    }
    if (count > 0) runs.add(count)
    val effective = if (runs.isEmpty()) listOf(0) else runs
    return effective == clues
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val t = LocalGameTheme.current
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color = t.textSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun LegendItem(gesture: String, action: String, color: Color, t: GameTheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(gesture, color = t.textSecondary, fontSize = 9.sp, letterSpacing = 0.5.sp)
        Text(action, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
