package com.enigma.littlegames.ui.games.minesweeper

// ─────────────────────────────────────────────────────────────────────────────
// Minesweeper — ViewModel
// Hex grid with axial coordinates, safe-first-tap board generation,
// flood-fill auto-reveal, and long-press flag.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.sqrt

// ── Hex coordinate ────────────────────────────────────────────────────────────

data class HexCoord(val q: Int, val r: Int)

private val HEX_NEIGHBOURS = listOf(
    HexCoord(+1,  0), HexCoord(-1,  0),
    HexCoord( 0, +1), HexCoord( 0, -1),
    HexCoord(+1, -1), HexCoord(-1, +1),
)

private fun HexCoord.neighbours() = HEX_NEIGHBOURS.map { HexCoord(q + it.q, r + it.r) }

// ── Cell ──────────────────────────────────────────────────────────────────────

data class HexCell(
    val coord: HexCoord,
    val isMine: Boolean    = false,
    val isRevealed: Boolean= false,
    val isFlagged: Boolean = false,
    val adjMines: Int      = 0,
)

enum class MinesweeperPhase { IDLE, PLAYING, WON, LOST }

enum class MineDifficulty(
    val label: String,
    val emoji: String,
    val radius: Int,
    val mineDensity: Float,
) {
    EASY  ("Easy",   "🌿", 3, 0.10f),   //  37 cells,  ~4 mines
    MEDIUM("Medium", "⚡", 4, 0.16f),   //  61 cells, ~10 mines
    HARD  ("Hard",   "🔥", 5, 0.20f),   //  91 cells, ~18 mines
    EXPERT("Expert", "💀", 6, 0.25f),   // 127 cells, ~32 mines
}

data class MinesweeperState(
    val board: Map<HexCoord, HexCell> = emptyMap(),
    val phase: MinesweeperPhase       = MinesweeperPhase.IDLE,
    val difficulty: MineDifficulty    = MineDifficulty.MEDIUM,
    val mineCount: Int                = 0,
    val flagCount: Int                = 0,
    val revealCount: Int              = 0,
    val safeCount: Int                = 0,   // total non-mine cells
    val elapsedSecs: Long             = 0L,
    val generating: Boolean           = true,
)

class MinesweeperViewModel : ViewModel() {
    private val _state = MutableStateFlow(MinesweeperState())
    val state: StateFlow<MinesweeperState> = _state.asStateFlow()
    private var timerJob: Job? = null

    init { newGame(MineDifficulty.MEDIUM) }

    fun newGame(d: MineDifficulty) {
        timerJob?.cancel()
        val coords = hexCoordsInRadius(d.radius)
        // Empty board — populated on first tap
        val board = coords.associateWith { HexCell(it) }
        _state.value = MinesweeperState(
            board      = board,
            phase      = MinesweeperPhase.IDLE,
            difficulty = d,
            generating = false,
            safeCount  = 0,
        )
    }

    // First reveal triggers safe board generation
    fun reveal(coord: HexCoord) {
        val s = _state.value
        if (s.phase == MinesweeperPhase.WON || s.phase == MinesweeperPhase.LOST) return
        val cell = s.board[coord] ?: return
        if (cell.isFlagged) return

        val board = if (s.phase == MinesweeperPhase.IDLE) {
            // Generate board now, keeping `coord` and its neighbours safe
            generateBoard(s.difficulty, s.board.keys.toList(), safeZone = coord)
        } else {
            s.board
        }

        val tapped = board[coord] ?: return
        if (tapped.isRevealed) return
        if (tapped.isMine) {
            // Game over — reveal all mines
            val lostBoard = board.mapValues { (_, c) ->
                if (c.isMine) c.copy(isRevealed = true) else c
            }
            timerJob?.cancel()
            _state.update { it.copy(board = lostBoard, phase = MinesweeperPhase.LOST) }
            return
        }

        val revealed = floodReveal(coord, board)
        val newBoard  = board.toMutableMap()
        revealed.forEach { c -> newBoard[c] = newBoard[c]!!.copy(isRevealed = true) }

        val revealCount = newBoard.values.count { it.isRevealed && !it.isMine }
        val safeCount   = newBoard.values.count { !it.isMine }
        val won         = revealCount == safeCount

        if (s.phase == MinesweeperPhase.IDLE) startTimer()
        if (won) timerJob?.cancel()

        val mineCount = newBoard.values.count { it.isMine }
        _state.update { it.copy(
            board       = newBoard,
            phase       = if (won) MinesweeperPhase.WON else MinesweeperPhase.PLAYING,
            mineCount   = mineCount,
            safeCount   = safeCount,
            revealCount = revealCount,
            generating  = false,
        )}
    }

    fun toggleFlag(coord: HexCoord) {
        val s = _state.value
        if (s.phase == MinesweeperPhase.IDLE || s.phase == MinesweeperPhase.WON
            || s.phase == MinesweeperPhase.LOST) return
        val cell = s.board[coord] ?: return
        if (cell.isRevealed) return
        val newBoard = s.board.toMutableMap()
        newBoard[coord] = cell.copy(isFlagged = !cell.isFlagged)
        val flagCount = newBoard.values.count { it.isFlagged }
        _state.update { it.copy(board = newBoard, flagCount = flagCount) }
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(elapsedSecs = it.elapsedSecs + 1) }
            }
        }
    }

    override fun onCleared() { timerJob?.cancel() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Board generation
// ─────────────────────────────────────────────────────────────────────────────

private fun generateBoard(
    d: MineDifficulty,
    coords: List<HexCoord>,
    safeZone: HexCoord,
): Map<HexCoord, HexCell> {
    val safeSet  = (safeZone.neighbours() + safeZone).toSet()
    val eligible = coords.filter { it !in safeSet }.shuffled()
    val mineCount = (coords.size * d.mineDensity).toInt().coerceAtLeast(1)
    val mineSet  = eligible.take(mineCount).toSet()

    val base = coords.associateWith { coord ->
        HexCell(coord, isMine = coord in mineSet)
    }

    // Compute adjacency counts
    return base.mapValues { (coord, cell) ->
        val adj = coord.neighbours().count { base[it]?.isMine == true }
        cell.copy(adjMines = adj)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Flood-fill reveal
// ─────────────────────────────────────────────────────────────────────────────

private fun floodReveal(start: HexCoord, board: Map<HexCoord, HexCell>): Set<HexCoord> {
    val visited = mutableSetOf<HexCoord>()
    val queue   = ArrayDeque<HexCoord>()
    queue.add(start)
    while (queue.isNotEmpty()) {
        val coord = queue.removeFirst()
        if (coord in visited) continue
        val cell = board[coord] ?: continue
        if (cell.isMine || cell.isRevealed) continue
        visited.add(coord)
        if (cell.adjMines == 0) {
            coord.neighbours().forEach { nb ->
                if (nb !in visited && board.containsKey(nb)) queue.add(nb)
            }
        }
    }
    return visited
}

// ─────────────────────────────────────────────────────────────────────────────
// Hex geometry helpers
// ─────────────────────────────────────────────────────────────────────────────

fun hexCoordsInRadius(radius: Int): List<HexCoord> {
    val result = mutableListOf<HexCoord>()
    for (q in -radius..radius) {
        val rMin = maxOf(-radius, -q - radius)
        val rMax = minOf( radius, -q + radius)
        for (r in rMin..rMax) result.add(HexCoord(q, r))
    }
    return result
}

/** Flat-top hex: pixel centre from axial coords */
fun hexToPixel(coord: HexCoord, size: Float, originX: Float, originY: Float): Pair<Float, Float> {
    val x = originX + size * (3f / 2f * coord.q)
    val y = originY + size * (sqrt(3.0).toFloat() * (coord.r + coord.q / 2f))
    return x to y
}
