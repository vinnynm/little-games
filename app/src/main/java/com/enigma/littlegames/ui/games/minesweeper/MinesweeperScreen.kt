package com.enigma.littlegames.ui.games.minesweeper

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.*

// ── Constants ─────────────────────────────────────────────────────────────────

private const val HEX_GAP_RATIO = 0.92f
private const val MINE_RADIUS_RATIO = 0.28f
private const val NUMBER_SIZE_RATIO = 0.70f
private const val NUMBER_Y_OFFSET_RATIO = 0.25f

private val ADJ_COLOURS = listOf(
    Color.Transparent,
    Color(0xFF42A5F5),
    Color(0xFF66BB6A),
    Color(0xFFEF5350),
    Color(0xFFAB47BC),
    Color(0xFFFF7043),
    Color(0xFF26C6DA),
    Color(0xFFFFCA28),
    Color(0xFF78909C),
)

private val MINE_COLORS = listOf(
    Color(0xFFE53935),
    Color(0xFFB71C1C),
    Color(0xFFFFCDD2),
)

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun MinesweeperScreen(hub: HubViewModel) {
    val t = LocalGameTheme.current
    val vm: MinesweeperViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()
    val haptic = LocalHapticFeedback.current

    // Canvas pixel size tracked via onSizeChanged (more reliable than onGloballyPositioned for Canvas)
    var canvasWidthPx by remember { mutableStateOf(0) }
    var canvasHeightPx by remember { mutableStateOf(0) }

    val density = LocalDensity.current

    // Pre-calculate hex geometry whenever canvas size or difficulty changes
    val hexGeometry = remember(state.difficulty, canvasWidthPx, canvasHeightPx) {
        if (canvasWidthPx == 0 || canvasHeightPx == 0) null
        else HexGeometry.calculate(
            state.difficulty.radius,
            Size(canvasWidthPx.toFloat(), canvasHeightPx.toFloat()),
        )
    }

    val numberPaints = remember {
        ADJ_COLOURS.map { color ->
            Paint().apply {
                this.color = color.toArgb()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    // Track which cell is currently pressed for chord highlight
    var pressedCell by remember { mutableStateOf<HexCoord?>(null) }

    LaunchedEffect(state.phase) {
        when (state.phase) {
            MinesweeperPhase.WON -> {
                hub.recordMinesweeperWin(state.difficulty.label, state.elapsedSecs)
                hub.sound.play(Sfx.VICTORY)
                particles.burst(
                    center = Offset(canvasWidthPx / 2f, canvasHeightPx / 2f),
                    colors = listOf(t.primary, t.accent, Color.White, t.warning, t.success),
                    count = 80,
                    speed = 0.5f,
                )
            }
            MinesweeperPhase.LOST -> hub.sound.play(Sfx.FLOW_FAIL)
            else -> {}
        }
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
            val timer = formatTime(state.elapsedSecs)

            GameTopBar(
                title = "MINESWEEPER",
                subtitle = "${state.difficulty.emoji} ${state.difficulty.label}  ·  hex grid",
                onBack = { hub.navigate(HubScreen.Home) },
                actions = {
                    MineCounterDisplay(remaining = state.mineCount - state.flagCount, theme = t)
                    Spacer(Modifier.width(12.dp))
                    TimerDisplay(timer, t)
                }
            )

            StatsRow(state, t)
            PhaseBanner(state.phase, timer, t)

            // ── Hex board ─────────────────────────────────────────────────────
            // Single Canvas with a single pointerInput — no nesting.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { size ->
                        canvasWidthPx  = size.width
                        canvasHeightPx = size.height
                    }
                    .pointerInput(state.board, state.phase, hexGeometry) {
                        val geo = hexGeometry ?: return@pointerInput
                        detectTapGestures(
                            onPress = { offset ->
                                // Show press highlight while finger is down
                                val coord = geo.pixelToHex(offset)
                                pressedCell = coord
                                // Wait for release or cancel
                                val released = tryAwaitRelease()
                                pressedCell = null
                            },
                            onTap = { offset ->
                                if (state.phase == MinesweeperPhase.WON ||
                                    state.phase == MinesweeperPhase.LOST) return@detectTapGestures
                                val coord = geo.pixelToHex(offset) ?: return@detectTapGestures
                                if (!state.board.containsKey(coord)) return@detectTapGestures
                                hub.sound.play(Sfx.TAP)
                                vm.reveal(coord)
                            },
                            onLongPress = { offset ->
                                if (state.phase == MinesweeperPhase.WON ||
                                    state.phase == MinesweeperPhase.LOST) return@detectTapGestures
                                val coord = geo.pixelToHex(offset) ?: return@detectTapGestures
                                if (!state.board.containsKey(coord)) return@detectTapGestures
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hub.sound.play(Sfx.ROTATE)
                                vm.toggleFlag(coord)
                            },
                        )
                    }
            ) {
                val geo = hexGeometry ?: return@Canvas

                // Draw shadows
                state.board.forEach { (coord, _) ->
                    val (cx, cy) = geo.hexToPixel(coord)
                    drawHexShadow(cx, cy, geo.radius)
                }

                // Draw cells
                state.board.forEach { (coord, cell) ->
                    val (cx, cy) = geo.hexToPixel(coord)
                    val isPressed = coord == pressedCell && !cell.isRevealed && !cell.isFlagged
                    drawHexCell(cell, cx, cy, geo.radius, t, numberPaints, isPressed)
                }

                // Chord hint highlight
                pressedCell?.let { coord ->
                    val cell = state.board[coord]
                    if (cell?.isRevealed == true && cell.adjMines > 0) {
                        coord.neighbours().forEach { nb ->
                            val nbCell = state.board[nb]
                            if (nbCell != null && !nbCell.isRevealed && !nbCell.isFlagged) {
                                val (nx, ny) = geo.hexToPixel(nb)
                                drawHexHighlight(nx, ny, geo.radius, t.primary.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            DifficultySelector(state, t) { d ->
                hub.sound.play(Sfx.TAP)
                vm.newGame(d)
            }

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

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun MineCounterDisplay(remaining: Int, theme: GameTheme) {
    val displayValue = remaining.coerceIn(-99, 999).toString().padStart(3, ' ')
    Surface(
        color = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp),
        contentColor = if (remaining <= 0) theme.success else Color(0xFFFF5252)
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
    val progress = if (state.safeCount > 0)
        state.revealCount.toFloat() / state.safeCount.toFloat()
    else 0f

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniStat("FLAGS", "${state.flagCount}")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(progress * 100).toInt()}%",
                color = t.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
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
        exit  = fadeOut(tween(200)) + shrinkVertically(),
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            color  = t.success.copy(.15f),
            shape  = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, t.success.copy(.5f)),
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("🎉 CLEARED!", color = t.success, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(" · $timer", color = t.textSecondary, fontSize = 11.sp)
            }
        }
    }

    AnimatedVisibility(
        visible = phase == MinesweeperPhase.LOST,
        enter = fadeIn(tween(300)) + expandVertically(),
        exit  = fadeOut(tween(200)) + shrinkVertically(),
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            color  = Color(0xFFE53935).copy(.12f),
            shape  = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFE53935).copy(.4f)),
        ) {
            Text(
                "💥 BOOM! Try again.",
                color = Color(0xFFE53935), fontWeight = FontWeight.Black, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp),
            )
        }
    }

    AnimatedVisibility(
        visible = phase == MinesweeperPhase.IDLE,
        enter = fadeIn(),
        exit  = fadeOut(),
    ) {
        Text(
            "Tap to reveal · Long-press to flag",
            color = t.textSecondary, fontSize = 11.sp,
        )
    }
}

@Composable
private fun DifficultySelector(
    state: MinesweeperState,
    t: GameTheme,
    onSelect: (MineDifficulty) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(t.surface).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MineDifficulty.entries.forEach { d ->
            val sel = state.difficulty == d
            Surface(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                    .clickable { onSelect(d) },
                color  = if (sel) t.primary else Color.Transparent,
                shape  = RoundedCornerShape(7.dp),
                border = if (!sel) BorderStroke(1.dp, t.border.copy(.3f)) else null,
            ) {
                Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "${d.emoji} ${d.label}",
                        color      = if (sel) t.background else t.textSecondary,
                        fontSize   = 10.sp,
                        fontWeight = if (sel) FontWeight.Black else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── Drawing ──────────────────────────────────────────────────────────────────

private fun DrawScope.drawHexShadow(cx: Float, cy: Float, radius: Float) {
    drawPath(hexPath(cx + 2f, cy + 2f, radius * HEX_GAP_RATIO), Color.Black.copy(.15f))
}

private fun DrawScope.drawHexCell(
    cell: HexCell,
    cx: Float, cy: Float, radius: Float,
    theme: GameTheme,
    numberPaints: List<Paint>,
    isPressed: Boolean,
) {
    val r    = radius * HEX_GAP_RATIO
    val path = hexPath(cx, cy, r)

    when {
        cell.isMine && cell.isRevealed -> drawRevealedMine(path, cx, cy, r, cell.isWrongFlag)
        cell.isRevealed                -> drawRevealedCell(path, cx, cy, r, cell, theme, numberPaints)
        cell.isFlagged                 -> drawFlaggedCell(path, cx, cy, r, theme, cell.isWrongFlag)
        else                           -> drawHiddenCell(path, cx, cy, r, theme, isPressed)
    }
}

private fun DrawScope.drawHiddenCell(
    path: Path, cx: Float, cy: Float, r: Float,
    theme: GameTheme, isPressed: Boolean,
) {
    drawPath(path, if (isPressed) theme.surfaceVariant else theme.surface)
    drawPath(hexPath(cx, cy, r * 0.85f), theme.surface.copy(.5f))
    drawPath(path, theme.border.copy(.4f), style = Stroke(1.2f))
}

private fun DrawScope.drawRevealedCell(
    path: Path, cx: Float, cy: Float, r: Float,
    cell: HexCell, theme: GameTheme, numberPaints: List<Paint>,
) {
    drawPath(path, if (cell.adjMines == 0) theme.surfaceVariant.copy(.7f) else theme.surfaceVariant)
    drawPath(path, theme.border.copy(.2f), style = Stroke(.8f))
    if (cell.adjMines > 0) {
        val idx   = cell.adjMines.coerceIn(numberPaints.indices)
        val paint = numberPaints[idx]
        paint.textSize = r * NUMBER_SIZE_RATIO
        drawContext.canvas.nativeCanvas.drawText(
            "${cell.adjMines}", cx, cy + r * NUMBER_Y_OFFSET_RATIO, paint,
        )
    }
}

private fun DrawScope.drawFlaggedCell(
    path: Path, cx: Float, cy: Float, r: Float,
    theme: GameTheme, isWrong: Boolean,
) {
    drawPath(path, theme.warning.copy(.2f))
    drawPath(path, theme.border.copy(.4f), style = Stroke(1.2f))
    val poleColor = if (isWrong) Color(0xFFE53935) else Color(0xFF795548)
    drawLine(poleColor, Offset(cx, cy - r * .35f), Offset(cx, cy + r * .35f), 2f)
    val flagColor = if (isWrong) Color(0xFFE53935) else theme.warning
    drawPath(Path().apply {
        moveTo(cx, cy - r * .35f)
        lineTo(cx + r * .3f, cy - r * .15f)
        lineTo(cx, cy + r * .05f)
        close()
    }, flagColor)
    drawLine(poleColor, Offset(cx - r * .2f, cy + r * .35f), Offset(cx + r * .2f, cy + r * .35f), 2f)
    if (isWrong) {
        drawLine(Color.White, Offset(cx - r * .3f, cy - r * .3f), Offset(cx + r * .3f, cy + r * .3f), 3f)
        drawLine(Color.White, Offset(cx + r * .3f, cy - r * .3f), Offset(cx - r * .3f, cy + r * .3f), 3f)
    }
}

private fun DrawScope.drawRevealedMine(
    path: Path, cx: Float, cy: Float, r: Float, isWrongFlag: Boolean,
) {
    drawPath(path, Color(0xFFE53935).copy(.5f))
    drawPath(path, Color(0xFFB71C1C).copy(.3f), style = Stroke(1.5f))
    if (isWrongFlag) {
        drawLine(Color.White, Offset(cx - r * .3f, cy - r * .3f), Offset(cx + r * .3f, cy + r * .3f), 3f)
        drawLine(Color.White, Offset(cx + r * .3f, cy - r * .3f), Offset(cx - r * .3f, cy + r * .3f), 3f)
        return
    }
    drawCircle(MINE_COLORS[0], r * MINE_RADIUS_RATIO, Offset(cx, cy))
    val spikeLen = r * .35f
    val spikeW   = r * .06f
    for (i in 0 until 8) {
        val angle = Math.toRadians(45.0 * i)
        drawLine(MINE_COLORS[0], Offset(cx, cy),
            Offset(cx + cos(angle).toFloat() * spikeLen, cy + sin(angle).toFloat() * spikeLen),
            strokeWidth = spikeW, cap = StrokeCap.Round)
    }
    drawCircle(MINE_COLORS[2].copy(.8f), r * .10f, Offset(cx - r * .08f, cy - r * .08f))
}

private fun DrawScope.drawHexHighlight(cx: Float, cy: Float, radius: Float, color: Color) {
    drawPath(hexPath(cx, cy, radius * HEX_GAP_RATIO), color)
}

private fun hexPath(cx: Float, cy: Float, radius: Float): Path = Path().apply {
    for (i in 0..5) {
        val angle = Math.toRadians(60.0 * i)
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

// ── Hex Geometry ─────────────────────────────────────────────────────────────

@Stable
data class HexGeometry(
    val radius: Float,
    val originX: Float,
    val originY: Float,
    private val gridSize: Int,
) {
    fun hexToPixel(coord: HexCoord): Pair<Float, Float> {
        val x = originX + radius * (3f / 2f * coord.q)
        val y = originY + radius * (SQRT3 * (coord.r + coord.q / 2f))
        return x to y
    }

    fun pixelToHex(offset: Offset): HexCoord? {
        val dx = offset.x - originX
        val dy = offset.y - originY
        val q  = (2f / 3f * dx) / radius
        val r  = (-1f / 3f * dx + SQRT3 / 3f * dy) / radius
        val result = cubeRound(q, -q - r, r)
        val coord  = HexCoord(result.first, result.second)
        return if (isValid(coord)) coord else null
    }

    private fun isValid(coord: HexCoord): Boolean {
        val s = -coord.q - coord.r
        return maxOf(abs(coord.q), abs(coord.r), abs(s)) <= gridSize
    }

    companion object {
        const val SQRT3 = 1.7320508f

        fun calculate(gridRadius: Int, canvasSize: Size): HexGeometry {
            val margin = 32f
            val w = canvasSize.width  - margin
            val h = canvasSize.height - margin
            val hexW   = w / (gridRadius * 3f + 1.5f)
            val hexH   = h / ((gridRadius * 2 + 1) * SQRT3)
            val radius = minOf(hexW, hexH).coerceAtLeast(8f)
            return HexGeometry(
                radius  = radius,
                originX = canvasSize.width  / 2f,
                originY = canvasSize.height / 2f,
                gridSize = gridRadius,
            )
        }
    }
}

private fun cubeRound(q: Float, r: Float, s: Float): Triple<Int, Int, Int> {
    var rq = q.roundToInt()
    var rr = r.roundToInt()
    var rs = s.roundToInt()
    val qD = abs(rq.toDouble() - q)
    val rD = abs(rr.toDouble() - r)
    val sD = abs(rs.toDouble() - s)
    when {
        qD > rD && qD > sD -> rq = -rr - rs
        rD > sD            -> rr = -rq - rs
        else               -> rs = -rq - rr
    }
    return Triple(rq, rr, rs)
}

private fun formatTime(secs: Long): String {
    val m = (secs / 60).coerceAtMost(99)
    val s = secs % 60
    return "%02d:%02d".format(m, s)
}
