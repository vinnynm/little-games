package com.enigma.littlegames.ui.games.minesweeper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── Hex coordinate ────────────────────────────────────────────────────────────

@JvmInline
value class HexCoord(val packed: Long) {
    constructor(q: Int, r: Int) : this((q.toLong() shl 32) or (r.toLong() and 0xFFFFFFFFL))

    val q: Int get() = (packed shr 32).toInt()
    val r: Int get() = packed.toInt()

    fun neighbours(): List<HexCoord> = HEX_NEIGHBOURS.map { offset ->
        HexCoord(q + offset.q, r + offset.r)
    }

    override fun toString(): String = "($q,$r)"

    companion object {
        private val HEX_NEIGHBOURS = listOf(
            HexCoord(1, 0), HexCoord(-1, 0),
            HexCoord(0, 1), HexCoord(0, -1),
            HexCoord(1, -1), HexCoord(-1, 1),
        )
    }
}

// ── Cell ──────────────────────────────────────────────────────────────────────

data class HexCell(
    val coord: HexCoord,
    val isMine: Boolean = false,
    val isRevealed: Boolean = false,
    val isFlagged: Boolean = false,
    val adjMines: Int = 0,
    val isWrongFlag: Boolean = false,
)

enum class MinesweeperPhase { IDLE, PLAYING, WON, LOST }

enum class MineDifficulty(
    val label: String,
    val emoji: String,
    val radius: Int,
    val mineDensity: Float,
) {
    EASY   ("Easy",   "🌿", 3, 0.10f),
    MEDIUM ("Medium", "⚡", 4, 0.16f),
    HARD   ("Hard",   "🔥", 5, 0.20f),
    EXPERT ("Expert", "💀", 6, 0.25f),
}

data class MinesweeperState(
    val board: Map<HexCoord, HexCell> = emptyMap(),
    val phase: MinesweeperPhase = MinesweeperPhase.IDLE,
    val difficulty: MineDifficulty = MineDifficulty.MEDIUM,
    val mineCount: Int = 0,
    val flagCount: Int = 0,
    val revealCount: Int = 0,
    val safeCount: Int = 0,
    val elapsedSecs: Long = 0L,
    val explodedCell: HexCoord? = null,
)

class MinesweeperViewModel : ViewModel() {
    private val _state = MutableStateFlow(MinesweeperState())
    val state: StateFlow<MinesweeperState> = _state.asStateFlow()
    private var timerJob: Job? = null

    init { newGame(MineDifficulty.MEDIUM) }

    fun newGame(d: MineDifficulty) {
        timerJob?.cancel()
        val coords = hexCoordsInRadius(d.radius)
        val board = coords.associateWith { HexCell(it) }
        _state.value = MinesweeperState(
            board = board,
            phase = MinesweeperPhase.IDLE,
            difficulty = d,
        )
    }

    fun reveal(coord: HexCoord) {
        val s = _state.value
        if (s.phase == MinesweeperPhase.WON || s.phase == MinesweeperPhase.LOST) return
        val cell = s.board[coord] ?: return

        // Chord reveal
        if (cell.isRevealed && cell.adjMines > 0) {
            chordReveal(coord, s)
            return
        }

        if (cell.isFlagged || cell.isRevealed) return

        // First tap: generate board
        val board = if (s.phase == MinesweeperPhase.IDLE) {
            generateBoard(s.difficulty, s.board.keys, safeZone = coord)
        } else {
            s.board
        }

        val tapped = board[coord] ?: return

        if (tapped.isMine) {
            timerJob?.cancel()
            _state.update {
                it.copy(
                    board = revealAllMines(board, coord),
                    phase = MinesweeperPhase.LOST,
                    explodedCell = coord,
                )
            }
            return
        }

        performReveal(coord, board, wasFirstTap = s.phase == MinesweeperPhase.IDLE)
    }

    fun toggleFlag(coord: HexCoord) {
        val s = _state.value
        if (s.phase == MinesweeperPhase.WON || s.phase == MinesweeperPhase.LOST) return
        val cell = s.board[coord] ?: return
        if (cell.isRevealed) return

        val newFlagged = !cell.isFlagged
        val newBoard = s.board.toMutableMap()
        newBoard[coord] = cell.copy(isFlagged = newFlagged)

        _state.update {
            it.copy(
                board = newBoard,
                flagCount = it.flagCount + if (newFlagged) 1 else -1,
            )
        }
    }

    private fun chordReveal(coord: HexCoord, s: MinesweeperState) {
        val cell = s.board[coord] ?: return
        val flagCount = coord.neighbours().count { s.board[it]?.isFlagged == true }
        if (flagCount != cell.adjMines) return

        var hitMine: HexCoord? = null
        val toReveal = mutableListOf<HexCoord>()

        for (nb in coord.neighbours()) {
            val nbCell = s.board[nb] ?: continue
            if (nbCell.isFlagged || nbCell.isRevealed) continue
            if (nbCell.isMine) {
                hitMine = nb
            } else {
                toReveal.add(nb)
            }
        }

        if (hitMine != null) {
            timerJob?.cancel()
            _state.update {
                it.copy(
                    board = revealAllMines(s.board, hitMine),
                    phase = MinesweeperPhase.LOST,
                    explodedCell = hitMine,
                )
            }
            return
        }

        val newBoard = s.board.toMutableMap()
        for (c in toReveal) {
            val revealed = floodReveal(c, newBoard)
            for (rc in revealed) {
                newBoard[rc] = newBoard[rc]!!.copy(isRevealed = true)
            }
        }

        updateAfterReveal(newBoard, s)
    }

    private fun performReveal(
        coord: HexCoord,
        board: Map<HexCoord, HexCell>,
        wasFirstTap: Boolean,
    ) {
        val revealed = floodReveal(coord, board)
        val newBoard = board.toMutableMap()
        for (c in revealed) {
            newBoard[c] = newBoard[c]!!.copy(isRevealed = true)
        }
        if (wasFirstTap) startTimer()
        updateAfterReveal(newBoard, _state.value)
    }

    private fun updateAfterReveal(
        newBoard: Map<HexCoord, HexCell>,
        previousState: MinesweeperState,
    ) {
        val revealCount = newBoard.values.count { it.isRevealed && !it.isMine }
        val safeCount = newBoard.values.count { !it.isMine }
        val won = revealCount == safeCount
        if (won) timerJob?.cancel()

        _state.update {
            it.copy(
                board = newBoard,
                phase = if (won) MinesweeperPhase.WON else MinesweeperPhase.PLAYING,
                mineCount = newBoard.values.count { it.isMine },
                safeCount = safeCount,
                revealCount = revealCount,
            )
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(elapsedSecs = it.elapsedSecs + 1) }
            }
        }
    }

    override fun onCleared() { timerJob?.cancel() }
}

// ── Board generation ──────────────────────────────────────────────────────────

private fun generateBoard(
    d: MineDifficulty,
    coords: Set<HexCoord>,
    safeZone: HexCoord,
): Map<HexCoord, HexCell> {
    val safeSet = (safeZone.neighbours().toSet() + safeZone)
    val eligible = coords.filter { it !in safeSet }.shuffled()
    val mineCount = (coords.size * d.mineDensity).toInt().coerceAtLeast(1)
    val mineSet = eligible.take(mineCount).toSet()

    return coords.associateWith { coord ->
        val isMine = coord in mineSet
        val adj = coord.neighbours().count { it in mineSet }
        HexCell(coord, isMine = isMine, adjMines = adj)
    }
}

private fun revealAllMines(
    board: Map<HexCoord, HexCell>,
    explodedCell: HexCoord,
): Map<HexCoord, HexCell> {
    return board.mapValues { (coord, cell) ->
        when {
            coord == explodedCell -> cell.copy(isRevealed = true)
            cell.isMine && !cell.isFlagged -> cell.copy(isRevealed = true)
            cell.isFlagged && !cell.isMine -> cell.copy(isWrongFlag = true)
            else -> cell
        }
    }
}

// ── Flood-fill reveal ─────────────────────────────────────────────────────────

private fun floodReveal(
    start: HexCoord,
    board: Map<HexCoord, HexCell>,
): Set<HexCoord> {
    val visited = HashSet<HexCoord>()
    val queue = ArrayDeque<HexCoord>()
    queue.add(start)

    while (queue.isNotEmpty()) {
        val coord = queue.removeFirst()
        if (!visited.add(coord)) continue
        val cell = board[coord] ?: continue
        if (cell.isMine || cell.isRevealed) continue

        if (cell.adjMines == 0) {
            for (nb in coord.neighbours()) {
                if (nb !in visited && board.containsKey(nb)) {
                    queue.add(nb)
                }
            }
        }
    }
    return visited
}

// ── Hex coordinate generation ─────────────────────────────────────────────────

fun hexCoordsInRadius(radius: Int): Set<HexCoord> {
    return buildSet {
        for (q in -radius..radius) {
            for (r in maxOf(-radius, -q - radius)..minOf(radius, -q + radius)) {
                add(HexCoord(q, r))
            }
        }
    }
}