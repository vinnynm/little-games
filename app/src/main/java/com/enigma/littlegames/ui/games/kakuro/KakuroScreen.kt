package com.enigma.littlegames.ui.games.kakuro

// ─────────────────────────────────────────────────────────────────────────────
// KakuroScreen — procedural random Kakuro, hub-themed.
//
// Key rendering decisions (matching the conventions corrected in earlier sprints):
//   • Diagonal in clue cell runs TOP-LEFT → BOTTOM-RIGHT
//   • DOWN clue (applies to the run below):  TOP-RIGHT  triangle, right-aligned
//   • RIGHT/ACROSS clue (run to the right): BOTTOM-LEFT triangle, left-aligned
//   • Board uses KakuroColors (light board palette) so clues are readable at
//     every zoom level regardless of the dark hub theme.
//   • Outer board border drawn as Canvas overlay so it's never obscured.
//
// The clue-cell canvas now uses Compose TextMeasurer (no android.graphics.Paint)
// for cleaner integration with the Compose rendering pipeline.
//
// Controls:
//   • Number pad 1-9
//   • Notes toggle, Undo, Erase, Hint
//   • Difficulty tabs (Easy / Medium / Hard / Expert) — generates a new puzzle
//   • Size picker row (6×6 up to 13×13) — generates a new puzzle
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import com.enigma.littlegames.common.*

// ── Board color palette (light, echoes a printed Kakuro grid) ─────────────────
private object KakuroColors {
    val boardBg          = Color(0xFF14131A)
    val blackCell        = Color(0xFF1F1E27)
    val divider          = Color(0xFF3A3946)
    val clueText         = Color(0xFFEFE7D8)
    val whiteCell        = Color(0xFFFFFDF7)
    val whiteSel         = Color(0xFFFFE2B0)
    val whiteRunHi       = Color(0xFFFFF1D6)
    val whiteConflict    = Color(0xFFFFC2BC)
    val whiteBorder      = Color(0xFFD9D2C2)
    val mainDigitText    = Color(0xFF2A2730)
    val noteDigitText    = Color(0xFF8C8579)
    val conflictDigit    = Color(0xFFB3261E)
}

private const val OUTER_BORDER_W = 3.5f

@Composable
fun KakuroScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: KakuroViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    var gridCenter by remember { mutableStateOf(Offset(400f, 400f)) }

    // Win → report to hub + particles
    LaunchedEffect(state.isSolved) {
        if (state.isSolved) {
            hub.recordSudokuWin(
                difficulty  = "Kakuro ${state.difficulty.label} ${state.gridSize.label}",
                errorCount  = state.errorCount,
                elapsedSecs = state.elapsedSecs.toLong(),
            )
            hub.sound.play(Sfx.VICTORY)
            particles.burst(
                center = gridCenter,
                colors = listOf(t.primary, t.secondary, Color.White, t.accent),
                count  = 60, speed = 0.4f,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().background(t.background).padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val timer = remember(state.elapsedSecs) {
                "%d:%02d".format(state.elapsedSecs / 60, state.elapsedSecs % 60)
            }

            GameTopBar(
                title    = "KAKURO",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label} · ${state.gridSize.label}",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    Text(timer, color = t.primary, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                    IconButton(onClick = { vm.toggleShowMistakes(); hub.sound.play(Sfx.TAP) }) {
                        Icon(
                            if (state.showMistakes) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle mistakes",
                            tint = if (state.showMistakes) t.primary else t.textSecondary,
                        )
                    }
                }
            )

            // Stats row
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("ERRORS",  "${state.errorCount}")
                val whiteFilled = state.puzzle?.allWhiteCells?.count { state.playerDigits.containsKey(it) } ?: 0
                val whiteTotal  = state.puzzle?.allWhiteCells?.size ?: 0
                MiniStat("FILLED",  "$whiteFilled/$whiteTotal")
                MiniStat("HINTS",   "${state.hintsUsed}")
                MiniStat("TIME",    timer)
            }

            // Win banner
            AnimatedVisibility(state.isSolved) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    color  = t.success.copy(.15f),
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, t.success.copy(.5f)),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("🎉  KAKURO SOLVED!", color = t.success, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("  ·  ${state.hintsUsed} hints  ·  $timer", color = t.textSecondary, fontSize = 11.sp)
                    }
                }
            }

            // ── Board ─────────────────────────────────────────────────────────
            if (state.generating) {
                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = t.primary) }
            } else {
                val puzzle = state.puzzle
                if (puzzle != null) {
                    val textMeasurer = rememberTextMeasurer()

                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .onGloballyPositioned { c ->
                                gridCenter = Offset(c.size.width / 2f, c.size.height / 2f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Cell size: fit the whole board into available space
                        val cellDp = minOf(
                            (maxWidth  / puzzle.totalCols).value,
                            (maxHeight / puzzle.totalRows).value,
                        ).coerceIn(18f, 52f).dp

                        Column(
                            Modifier
                                .wrapContentSize()
                                .background(KakuroColors.boardBg)
                                .border(BorderStroke(2.dp, KakuroColors.divider)),
                        ) {
                            for (r in 0 until puzzle.totalRows) {
                                Row {
                                    for (c in 0 until puzzle.totalCols) {
                                        val pos = KPos(r, c)
                                        if (puzzle.black[r][c]) {
                                            KakuroBlackCell(pos, puzzle, cellDp, textMeasurer)
                                        } else {
                                            KakuroWhiteCell(pos, state, vm, cellDp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Number pad ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (d in 1..9) {
                    OutlinedButton(
                        onClick = { hub.sound.play(Sfx.SUDOKU_PLACE); vm.inputDigit(d) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        border = BorderStroke(1.dp, t.primary.copy(.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = t.textPrimary),
                        shape = RoundedCornerShape(6.dp),
                    ) { Text("$d", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                }
            }

            // ── Action row ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                KakuroAction(Icons.Default.Backspace, "Erase",  enabled = true)          { hub.sound.play(Sfx.TAP); vm.eraseSelected() }
                KakuroAction(Icons.Default.EditNote,  "Notes",  highlighted = state.notesMode) { hub.sound.play(Sfx.TAP); vm.toggleNotesMode() }
                KakuroAction(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = state.canUndo) { hub.sound.play(Sfx.TAP); vm.undo() }
                KakuroAction(Icons.Default.Lightbulb, "Hint",   enabled = !state.isSolved) { hub.sound.play(Sfx.TAP); vm.useHint() }
                KakuroAction(Icons.Default.RestartAlt,"Reset",  enabled = true)          { hub.sound.play(Sfx.TAP); vm.resetProgress() }
            }

            // ── Size picker ───────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(t.surface).padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                KakuroGridSize.entries.forEach { sz ->
                    val sel = state.gridSize == sz
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (sel) t.primary.copy(.25f) else Color.Transparent)
                            .border(if (sel) 1.dp else 0.dp, if (sel) t.primary else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { hub.sound.play(Sfx.TAP); vm.newGame(sz, state.difficulty) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(sz.label, color = if (sel) t.primary else t.textSecondary, fontSize = 9.sp,
                            fontWeight = if (sel) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Difficulty tabs ───────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                KakuroDifficulty.entries.forEach { d ->
                    val sel = state.difficulty == d
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                            .background(if (sel) t.primary else Color.Transparent)
                            .clickable { hub.sound.play(Sfx.TAP); vm.newGame(state.gridSize, d) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.emoji} ${d.label.take(3)}",
                            color = if (sel) t.background else t.textSecondary,
                            fontSize = 10.sp,
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
// Black clue cell
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KakuroBlackCell(
    pos: KPos,
    puzzle: KakuroPuzzleData,
    size: Dp,
    textMeasurer: TextMeasurer,
) {
    val across = puzzle.acrossClueAt[pos]
    val down   = puzzle.downClueAt[pos]
    val clueTextStyle = TextStyle(
        color      = KakuroColors.clueText,
        fontSize   = (size.value * 0.26f).sp,
        fontWeight = FontWeight.Medium,
    )

    Canvas(
        Modifier.size(size).border(BorderStroke(0.5.dp, KakuroColors.divider))
    ) {
        drawRect(color = KakuroColors.blackCell)

        // Diagonal only when both clues are present
        if (across != null && down != null) {
            drawLine(
                color       = KakuroColors.divider,
                start       = Offset(0f, this.size.height),
                end         = Offset(this.size.width, 0f),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val pad = this.size.width * 0.08f

        // ── ACROSS clue — TOP-RIGHT triangle ─────────────────────────────────
        // (The run goes to the right, so the clue sits in the upper-right half.)
        if (across != null) {
            val layout = textMeasurer.measure("$across", clueTextStyle)
            drawText(layout, topLeft = Offset(this.size.width - layout.size.width - pad, pad))
        }

        // ── DOWN clue — BOTTOM-LEFT triangle ─────────────────────────────────
        // (The run goes downward, so the clue sits in the lower-left half.)
        if (down != null) {
            val layout = textMeasurer.measure("$down", clueTextStyle)
            drawText(layout, topLeft = Offset(pad, this.size.height - layout.size.height - pad))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// White playable cell
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KakuroWhiteCell(
    pos: KPos,
    state: KakuroUiState,
    vm: KakuroViewModel,
    size: Dp,
) {
    val puzzle    = state.puzzle ?: return
    val digit     = state.playerDigits[pos]
    val cellNotes = state.notes[pos] ?: emptySet()
    val isSel     = state.selectedCell == pos
    val isInRun   = !isSel && state.selectedCell?.let { sel ->
        puzzle.acrossRunAt[pos] == puzzle.acrossRunAt[sel] ||
        puzzle.downRunAt[pos]   == puzzle.downRunAt[sel]
    } == true
    val conflict  = digit != null && vm.cellHasConflict(puzzle, state.playerDigits, state.showMistakes, pos)

    val bg by animateColorAsState(
        when {
            isSel    -> KakuroColors.whiteSel
            conflict -> KakuroColors.whiteConflict
            isInRun  -> KakuroColors.whiteRunHi
            else     -> KakuroColors.whiteCell
        }, tween(80), label = "kk_bg"
    )

    Box(
        Modifier.size(size)
            .border(BorderStroke(0.5.dp, KakuroColors.whiteBorder))
            .background(bg)
            .clickable { vm.selectCell(pos) },
        contentAlignment = Alignment.Center,
    ) {
        if (digit != null) {
            Text(
                text       = "$digit",
                fontSize   = (size.value * 0.50f).sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (conflict) KakuroColors.conflictDigit else KakuroColors.mainDigitText,
            )
        } else if (cellNotes.isNotEmpty()) {
            KakuroNotesGrid(cellNotes, size)
        }
    }
}

@Composable
private fun KakuroNotesGrid(notes: Set<Int>, cellSize: Dp) {
    val fontSize = (cellSize.value * 0.22f).sp
    Column(
        Modifier.fillMaxSize().padding(1.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (row in 0..2) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0..2) {
                    val d = row * 3 + col + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (d in notes) Text("$d", fontSize = fontSize, color = KakuroColors.noteDigitText)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KakuroAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalGameTheme.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                icon,
                contentDescription = label,
                tint = when {
                    !enabled   -> t.textSecondary.copy(.4f)
                    highlighted -> t.primary
                    else       -> t.textPrimary
                },
            )
        }
        Text(label, color = t.textSecondary, fontSize = 9.sp)
    }
}
