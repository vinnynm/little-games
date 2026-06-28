package com.enigma.littlegames.ui.games.wordle

// ─────────────────────────────────────────────────────────────────────────────
// Wordle Screen
// Tile flip animation via Y-scale: 1→0 (colour change at midpoint)→1.
// QWERTY keyboard with per-key result colouring.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.common.*
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import kotlinx.coroutines.delay

private val QWERTY_ROWS = listOf(
    "QWERTYUIOP".toList(),
    "ASDFGHJKL".toList(),
    "ZXCVBNM".toList(),
)

@Composable
fun WordleScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: WordleViewModel = viewModel()
    val state     by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    val haptic    = LocalHapticFeedback.current

    // Particle burst on win
    LaunchedEffect(state.isWon) {
        if (state.isWon) {
            hub.recordWordleWin(
                attempts   = state.currentRow + 1,
                hardMode   = state.hardMode,
            )
            hub.sound.play(Sfx.VICTORY)
            particles.burst(
                center = Offset(400f, 400f),
                colors = listOf(t.primary, t.secondary, Color.White, t.warning),
                count  = 70, speed = 0.4f,
            )
        }
    }

    // Clear shake quickly
    LaunchedEffect(state.shake) {
        if (state.shake) { delay(500); vm.clearShake() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().background(t.background)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameTopBar(
                title    = "WORDLE",
                subtitle = if (state.mode == WordleMode.DAILY) "Daily word · ${state.currentRow}/6"
                           else "Free play · ${state.currentRow}/6",
                onBack   = { hub.navigate(HubScreen.Home) },
                actions  = {
                    // Hard mode toggle
                    if (!state.isWon && !state.isOver && state.currentRow == 0) {
                        Box(
                            Modifier.padding(end = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (state.hardMode) t.primary.copy(.2f) else t.surface)
                                .border(1.dp, if (state.hardMode) t.primary else t.border, RoundedCornerShape(6.dp))
                                .clickable { vm.newGame(state.mode, !state.hardMode) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("Hard", color = if (state.hardMode) t.primary else t.textSecondary,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            // Error / success message
            AnimatedVisibility(
                visible = state.errorMessage.isNotBlank() || state.isWon || state.isOver,
                enter = fadeIn() + slideInVertically { -it },
                exit  = fadeOut(),
            ) {
                val (msg, color) = when {
                    state.isWon  -> "🎉  ${wonMessage(state.currentRow + 1)}" to t.success
                    state.isOver -> "💀  The word was ${state.target}" to Color(0xFFE94560)
                    else         -> state.errorMessage to t.warning
                }
                Surface(Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    color = color.copy(.15f), shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, color.copy(.4f))) {
                    Text(msg, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(10.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Guess grid ────────────────────────────────────────────────────
            val shakeOffset by animateFloatAsState(
                if (state.shake) 12f else 0f,
                spring(Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh),
                label = "shake",
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.offset(x = shakeOffset.dp),
            ) {
                for (rowIdx in 0..5) {
                    val row      = state.guesses[rowIdx]
                    val isCurrent = rowIdx == state.currentRow && !state.isWon && !state.isOver
                    val letters  = if (isCurrent) state.currentInput.padEnd(5).toList()
                                   else if (row.isSubmitted) row.letters
                                   else List(5) { ' ' }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (colIdx in 0..4) {
                            WordleTile(
                                letter   = letters.getOrElse(colIdx) { ' ' },
                                result   = row.results.getOrNull(colIdx),
                                revealed = row.isSubmitted,
                                revealDelay = colIdx * 120,
                                t        = t,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── QWERTY keyboard ───────────────────────────────────────────────
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                QWERTY_ROWS.forEachIndexed { rowIdx, keys ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (rowIdx == 2) {
                            KeyButton("↵", t, width = 52.dp, result = null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hub.sound.play(Sfx.SUDOKU_PLACE)
                                vm.onEnter()
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        keys.forEach { ch ->
                            KeyButton(
                                label  = ch.toString(),
                                t      = t,
                                result = state.keyboardState[ch],
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hub.sound.play(Sfx.TAP)
                                vm.onKey(ch)
                            }
                            if (ch != keys.last()) Spacer(Modifier.width(4.dp))
                        }
                        if (rowIdx == 2) {
                            Spacer(Modifier.width(4.dp))
                            KeyButton("⌫", t, width = 52.dp, result = null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hub.sound.play(Sfx.TAP)
                                vm.onBackspace()
                            }
                        }
                    }
                }
            }

            // Mode + new game
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemedButton("Daily", {
                    hub.sound.play(Sfx.TAP)
                    vm.newGame(WordleMode.DAILY, state.hardMode)
                }, Modifier.weight(1f),
                    outlined = state.mode != WordleMode.DAILY)
                ThemedButton("Free Play", {
                    hub.sound.play(Sfx.TAP)
                    vm.newGame(WordleMode.FREE, state.hardMode)
                }, Modifier.weight(1f),
                    outlined = state.mode != WordleMode.FREE)
            }
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wordle tile — Y-scale flip to reveal result
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WordleTile(
    letter: Char,
    result: LetterResult?,
    revealed: Boolean,
    revealDelay: Int,
    t: GameTheme,
) {
    // Phase 0→1 on reveal trigger
    var phase by remember(revealed, result) { mutableStateOf(if (revealed) 1f else 0f) }
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(revealDelay.toLong())
            phase = 1f
        }
    }

    val flipProgress by animateFloatAsState(
        phase, tween(300), label = "tile_flip",
    )

    // Colour changes at mid-flip
    val bgColor = when {
        flipProgress < 0.5f -> t.surface
        result == LetterResult.CORRECT -> Color(0xFF538D4E)
        result == LetterResult.PRESENT -> Color(0xFFB59F3B)
        result == LetterResult.ABSENT  -> Color(0xFF3A3A3C)
        else -> t.surface
    }
    val borderColor = when {
        letter != ' ' && !revealed -> t.textSecondary
        flipProgress < 0.5f -> t.border
        else -> bgColor
    }

    // Y scale goes 1→0→1 during flip
    val scaleY = if (flipProgress < 0.5f) 1f - flipProgress * 2f else (flipProgress - 0.5f) * 2f

    Box(
        Modifier
            .size(52.dp)
            .scale(scaleY = scaleY.coerceAtLeast(0.01f), scaleX = 1f)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (letter != ' ') {
            Text(
                letter.toString(),
                color = if (revealed && flipProgress > 0.5f) Color.White else t.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Keyboard key
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KeyButton(
    label: String,
    t: GameTheme,
    width: Dp = 32.dp,
    result: LetterResult?,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        when (result) {
            LetterResult.CORRECT -> Color(0xFF538D4E)
            LetterResult.PRESENT -> Color(0xFFB59F3B)
            LetterResult.ABSENT  -> Color(0xFF3A3A3C)
            null                 -> t.surfaceVariant
        }, tween(200), label = "key_bg"
    )
    val textColor = when (result) {
        null -> t.textPrimary
        else -> Color.White
    }

    Box(
        Modifier
            .width(width).height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = if (label.length == 1) 14.sp else 11.sp,
            fontWeight = FontWeight.Bold)
    }
}

private fun wonMessage(attempts: Int) = when (attempts) {
    1    -> "Genius!"
    2    -> "Magnificent!"
    3    -> "Impressive!"
    4    -> "Splendid!"
    5    -> "Great!"
    else -> "Phew!"
}
