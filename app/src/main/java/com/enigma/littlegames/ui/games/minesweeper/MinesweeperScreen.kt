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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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
private const val CELL_PADDING = 16f
private const val MINE_RADIUS_RATIO = 0.28f
private const val NUMBER_SIZE_RATIO = 0.70f
private const val NUMBER_Y_OFFSET_RATIO = 0.25f

private val ADJ_COLOURS = listOf(
    Color.Transparent,      // 0
    Color(0xFF42A5F5),      // 1 — blue
    Color(0xFF66BB6A),      // 2 — green
    Color(0xFFEF5350),      // 3 — red
    Color(0xFFAB47BC),      // 4 — purple
    Color(0xFFFF7043),      // 5 — deep orange
    Color(0xFF26C6DA),      // 6 — cyan
    Color(0xFFFFCA28),      // 7 — yellow
    Color(0xFF78909C),      // 8 — grey
)

private val MINE_COLORS = listOf(
    Color(0xFFE53935),      // primary
    Color(0xFFB71C1C),      // dark
    Color(0xFFFFCDD2),      // light
)

// ── Composable ───────────────────────────────────────────────────────────────

@Composable
fun MinesweeperScreen(hub: HubViewModel) {
    val t = LocalGameTheme.current
    val vm: MinesweeperViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()

    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var pressedCell by remember { mutableStateOf<HexCoord?>(null) }

    // Pre-calculate hex geometry
    val hexGeometry = remember(state.difficulty, canvasSize) {
        if (canvasSize == Size.Zero) null
        else HexGeometry.calculate(state.difficulty.radius, canvasSize)
    }

    // Text paints — cached to avoid allocation each frame
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

    LaunchedEffect(state.phase) {
        when (state.phase) {
            MinesweeperPhase.WON -> {
                hub.recordMinesweeperWin(state.difficulty.label, state.elapsedSecs)
                hub.sound.play(Sfx.VICTORY)
                particles.burst(
                    center = Offset(canvasSize.width / 2f, canvasSize.height / 2f),
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
                    MineCounterDisplay(
                        remaining = state.mineCount - state.flagCount,
                        theme = t
                    )
                    Spacer(Modifier.width(12.dp))
                    TimerDisplay(timer, t)
                }
            )

            StatsRow(state, t)
            PhaseBanner(state.phase, timer, t)

            // ── Hex board ───────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        canvasSize = Size(
                            coords.size.width.toFloat(),
                            coords.size.height.toFloat()
                        )
                    }
            ) {
                hexGeometry?.let { geo ->
                    HexBoardCanvas(
                        state = state,
                        geometry = geo,
                        numberPaints = numberPaints,
                        theme = t,
                        pressedCell = pressedCell,
                        onTap = { coord ->
                            pressedCell = null
                            if (state.board.containsKey(coord)) {
                                hub.sound.play(Sfx.TAP)
                                vm.reveal(coord)
                            }
                        },
                        onLongPress = { coord ->
                            if (state.board.containsKey(coord)) {
                                hub.sound.play(Sfx.ROTATE)
                                vm.toggleFlag(coord)
                            }
                        },
                        onPressChange = { coord ->
                            pressedCell = coord
                        }
                    )
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
                onClick = {
                    hub.sound.play(Sfx.TAP)
                    vm.newGame(state.difficulty)
                },
                modifier = Modifier.fillMaxWidth(),
                outlined = true
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
        contentColor = when {
            remaining < 0 -> Color(0xFFFF5252)
            remaining == 0 -> theme.success
            else -> Color(0xFFFF5252)
        }
    ) {
        Text(
            displayValue,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun TimerDisplay(time: String, theme: GameTheme) {
    Surface(
        color = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp),
        contentColor = theme.primary
    ) {
        Text(
            time,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun StatsRow(state: MinesweeperState, t: GameTheme) {
    val progress = if (state.safeCount > 0) {
        state.revealCount.toFloat() / state.safeCount.toFloat()
    } else 0f

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniStat("FLAGS", "${state.flagCount}")

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(progress * 100).toInt()}%",
                color = t.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = t.primary,
                trackColor = t.surface,
                strokeCap = StrokeCap.Round
            )
        }

        MiniStat("REMAIN", "${state.safeCount - state.revealCount}")
    }
}

@Composable
private fun PhaseBanner(phase: MinesweeperPhase, timer: String, t: GameTheme) {
    AnimatedVisibility(
        visible = phase == MinesweeperPhase.WON,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(),
        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically()
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            color = t.success.copy(alpha = 0.15f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, t.success.copy(alpha = 0.5f))
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("🎉 CLEARED!", color = t.success, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(" · $timer", color = t.textSecondary, fontSize = 11.sp)
            }
        }
    }

    AnimatedVisibility(
        visible = phase == MinesweeperPhase.LOST,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(),
        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically()
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            color = Color(0xFFE53935).copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.4f))
        ) {
            Text(
                "💥 BOOM! Try again.",
                color = Color(0xFFE53935),
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    AnimatedVisibility(
        visible = phase == MinesweeperPhase.IDLE,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Text(
            "Tap to start · Long-press to flag · Tap number to chord",
            color = t.textSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun DifficultySelector(
    state: MinesweeperState,
    t: GameTheme,
    onSelect: (MineDifficulty) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(t.surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        MineDifficulty.entries.forEach { d ->
            val selected = state.difficulty == d
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onSelect(d) },
                color = if (selected) t.primary else Color.Transparent,
                shape = RoundedCornerShape(7.dp),
                border = if (!selected) BorderStroke(1.dp, t.border.copy(alpha = 0.3f)) else null
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${d.emoji} ${d.label}",
                        color = if (selected) t.background else t.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ── Hex Board Canvas ─────────────────────────────────────────────────────────

@Composable
private fun HexBoardCanvas(
    state: MinesweeperState,
    geometry: HexGeometry,
    numberPaints: List<Paint>,
    theme: GameTheme,
    pressedCell: HexCoord?,
    onTap: (HexCoord) -> Unit,
    onLongPress: (HexCoord) -> Unit,
    onPressChange: (HexCoord?) -> Unit,
) {
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(state.board, geometry) {
                detectTapGestures(
                    onTap = { offset: Offset ->
                        val coord = geometry.pixelToHex(offset) ?: return@detectTapGestures
                        onPressChange(null)
                        onTap(coord)
                    },
                    onLongPress = { offset: Offset ->
                        val coord = geometry.pixelToHex(offset) ?: return@detectTapGestures
                        onPressChange(null)
                        onLongPress(coord)
                    },
                    onPress = { offset: Offset ->
                        val coord = geometry.pixelToHex(offset)
                        onPressChange(coord)
                        tryAwaitRelease()
                        onPressChange(null)
                    }
                )
            }
    ) {
        // Draw shadows first
        state.board.forEach { (coord, _) ->
            val (cx, cy) = geometry.hexToPixel(coord)
            drawHexShadow(cx, cy, geometry.radius)
        }

        // Draw cells
        state.board.forEach { (coord, cell) ->
            val (cx, cy) = geometry.hexToPixel(coord)
            val isPressed = coord == pressedCell && !cell.isRevealed && !cell.isFlagged
            drawHexCell(cell, cx, cy, geometry.radius, theme, numberPaints, isPressed)
        }

        // Highlight neighbours of pressed revealed number cell (chord hint)
        pressedCell?.let { coord ->
            val cell = state.board[coord]
            if (cell?.isRevealed == true && cell.adjMines > 0) {
                coord.neighbours().forEach { nb ->
                    val nbCell = state.board[nb]
                    if (nbCell != null && !nbCell.isRevealed && !nbCell.isFlagged) {
                        val (nx, ny) = geometry.hexToPixel(nb)
                        drawHexHighlight(nx, ny, geometry.radius, theme.primary.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

// ── Drawing functions ────────────────────────────────────────────────────────

private fun DrawScope.drawHexShadow(cx: Float, cy: Float, radius: Float) {
    val shadowPath = hexPath(cx + 2f, cy + 2f, radius * HEX_GAP_RATIO)
    drawPath(path = shadowPath, color = Color.Black.copy(alpha = 0.15f))
}

private fun DrawScope.drawHexCell(
    cell: HexCell,
    cx: Float,
    cy: Float,
    radius: Float,
    theme: GameTheme,
    numberPaints: List<Paint>,
    isPressed: Boolean,
) {
    val r = radius * HEX_GAP_RATIO
    val path = hexPath(cx, cy, r)

    when {
        cell.isMine && cell.isRevealed -> drawRevealedMine(path, cx, cy, r, cell.isWrongFlag)
        cell.isRevealed -> drawRevealedCell(path, cx, cy, r, cell, theme, numberPaints)
        cell.isFlagged -> drawFlaggedCell(path, cx, cy, r, theme, cell.isWrongFlag)
        else -> drawHiddenCell(path, cx, cy, r, theme, isPressed)
    }
}

private fun DrawScope.drawHiddenCell(
    path: Path,
    cx: Float,
    cy: Float,
    r: Float,
    theme: GameTheme,
    isPressed: Boolean,
) {
    val bgColor = if (isPressed) theme.surfaceVariant else theme.surface
    drawPath(path, bgColor)

    // Inner bevel effect
    val innerPath = hexPath(cx, cy, r * 0.85f)
    drawPath(innerPath, theme.surface.copy(alpha = 0.5f))

    drawPath(path, theme.border.copy(alpha = 0.4f), style = Stroke(1.2f))
}

private fun DrawScope.drawRevealedCell(
    path: Path,
    cx: Float,
    cy: Float,
    r: Float,
    cell: HexCell,
    theme: GameTheme,
    numberPaints: List<Paint>,
) {
    val bgColor = if (cell.adjMines == 0) {
        theme.surfaceVariant.copy(alpha = 0.7f)
    } else {
        theme.surfaceVariant
    }
    drawPath(path, bgColor)
    drawPath(path, theme.border.copy(alpha = 0.2f), style = Stroke(0.8f))

    if (cell.adjMines > 0) {
        val paintIndex = cell.adjMines.coerceIn(numberPaints.indices)
        val paint = numberPaints[paintIndex]
        paint.textSize = r * NUMBER_SIZE_RATIO
        drawContext.canvas.nativeCanvas.drawText(
            "${cell.adjMines}",
            cx,
            cy + r * NUMBER_Y_OFFSET_RATIO,
            paint
        )
    }
}

private fun DrawScope.drawFlaggedCell(
    path: Path,
    cx: Float,
    cy: Float,
    r: Float,
    theme: GameTheme,
    isWrong: Boolean,
) {
    drawPath(path, theme.warning.copy(alpha = 0.2f))
    drawPath(path, theme.border.copy(alpha = 0.4f), style = Stroke(1.2f))

    // Flag pole
    val poleColor = if (isWrong) Color(0xFFE53935) else Color(0xFF795548)
    drawLine(
        poleColor,
        Offset(cx, cy - r * 0.35f),
        Offset(cx, cy + r * 0.35f),
        strokeWidth = 2f
    )

    // Flag triangle
    val flagColor = if (isWrong) Color(0xFFE53935) else theme.warning
    val flagPath = Path().apply {
        moveTo(cx, cy - r * 0.35f)
        lineTo(cx + r * 0.3f, cy - r * 0.15f)
        lineTo(cx, cy + r * 0.05f)
        close()
    }
    drawPath(flagPath, flagColor)

    // Base
    drawLine(
        poleColor,
        Offset(cx - r * 0.2f, cy + r * 0.35f),
        Offset(cx + r * 0.2f, cy + r * 0.35f),
        strokeWidth = 2f
    )

    // Wrong indicator
    if (isWrong) {
        drawLine(Color.White, Offset(cx - r * 0.3f, cy - r * 0.3f), Offset(cx + r * 0.3f, cy + r * 0.3f), strokeWidth = 3f)
        drawLine(Color.White, Offset(cx + r * 0.3f, cy - r * 0.3f), Offset(cx - r * 0.3f, cy + r * 0.3f), strokeWidth = 3f)
    }
}

private fun DrawScope.drawRevealedMine(
    path: Path,
    cx: Float,
    cy: Float,
    r: Float,
    isWrongFlag: Boolean,
) {
    drawPath(path, Color(0xFFE53935).copy(alpha = 0.5f))
    drawPath(path, Color(0xFFB71C1C).copy(alpha = 0.3f), style = Stroke(1.5f))

    if (isWrongFlag) {
        drawLine(Color.White, Offset(cx - r * 0.3f, cy - r * 0.3f), Offset(cx + r * 0.3f, cy + r * 0.3f), strokeWidth = 3f)
        drawLine(Color.White, Offset(cx + r * 0.3f, cy - r * 0.3f), Offset(cx - r * 0.3f, cy + r * 0.3f), strokeWidth = 3f)
        return
    }

    // Mine body
    drawCircle(MINE_COLORS[0], r * MINE_RADIUS_RATIO, Offset(cx, cy))

    // Spikes
    val spikeLength = r * 0.35f
    val spikeWidth = r * 0.06f
    for (i in 0 until 8) {
        val angle = Math.toRadians(45.0 * i)
        val dx = cos(angle).toFloat() * spikeLength
        val dy = sin(angle).toFloat() * spikeLength
        drawLine(
            MINE_COLORS[0],
            Offset(cx, cy),
            Offset(cx + dx, cy + dy),
            strokeWidth = spikeWidth,
            cap = StrokeCap.Round
        )
    }

    // Shine highlight
    drawCircle(MINE_COLORS[2].copy(alpha = 0.8f), r * 0.10f, Offset(cx - r * 0.08f, cy - r * 0.08f))
}

private fun DrawScope.drawHexHighlight(cx: Float, cy: Float, radius: Float, color: Color) {
    val path = hexPath(cx, cy, radius * HEX_GAP_RATIO)
    drawPath(path, color)
}

private fun hexPath(cx: Float, cy: Float, radius: Float): Path {
    return Path().apply {
        for (i in 0..5) {
            val angle = Math.toRadians(60.0 * i)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
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
        val q = (2f / 3f * dx) / radius
        val r = (-1f / 3f * dx + SQRT3 / 3f * dy) / radius
        val result = cubeRound(q, -q - r, r)
        val coord = HexCoord(result.first, result.second)
        return if (isValidCoord(coord)) coord else null
    }

    private fun isValidCoord(coord: HexCoord): Boolean {
        val s = -coord.q - coord.r
        return maxOf(abs(coord.q), abs(coord.r), abs(s)) <= gridSize
    }

    companion object {
        private const val SQRT3 = 1.7320508f

        fun calculate(gridRadius: Int, canvasSize: Size): HexGeometry {
            val margin = CELL_PADDING * 2
            val w = canvasSize.width - margin
            val h = canvasSize.height - margin
            val hexW = w / (gridRadius * 3f + 1.5f)
            val hexH = h / ((gridRadius * 2 + 1) * SQRT3)
            val radius = minOf(hexW, hexH).coerceAtLeast(8f)
            return HexGeometry(
                radius = radius,
                originX = canvasSize.width / 2f,
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

    val qDiff = abs(rq.toDouble() - q)
    val rDiff = abs(rr.toDouble() - r)
    val sDiff = abs(rs.toDouble() - s)

    when {
        qDiff > rDiff && qDiff > sDiff -> rq = -rr - rs
        rDiff > sDiff -> rr = -rq - rs
        else -> rs = -rq - rr
    }

    return Triple(rq, rr, rs)
}

private fun formatTime(secs: Long): String {
    val m = (secs / 60).coerceAtMost(99)
    val s = (secs % 60)
    return "%02d:%02d".format(m, s)
}