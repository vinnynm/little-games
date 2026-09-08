package com.enigma.littlegames.ui.games.minesweeper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── Cell ──────────────────────────────────────────────────────────────────────

data class MineCell(
    val row: Int,
    val col: Int,
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
    val rows: Int,
    val cols: Int,
    val mines: Int,
) {
    EASY   ("Easy",   "🌿", 8,  8,  10),
    MEDIUM ("Medium", "⚡", 10, 10, 18),
    HARD   ("Hard",   "🔥", 12, 12, 30),
    EXPERT ("Expert", "💀", 14, 14, 48),
}

data class MinesweeperState(
    val board: List<List<MineCell>> = emptyList(),
    val phase: MinesweeperPhase = MinesweeperPhase.IDLE,
    val difficulty: MineDifficulty = MineDifficulty.MEDIUM,
    val mineCount: Int = 0,
    val flagCount: Int = 0,
    val revealCount: Int = 0,
    val safeCount: Int = 0,
    val elapsedSecs: Long = 0L,
    val explodedCell: Pair<Int, Int>? = null,
)

class MinesweeperViewModel : ViewModel() {

    private val _state = MutableStateFlow(MinesweeperState())
    val state: StateFlow<MinesweeperState> = _state.asStateFlow()

    private var timerJob: Job? = null

    init { newGame(MineDifficulty.MEDIUM) }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    fun newGame(d: MineDifficulty) {
        timerJob?.cancel()
        val board = List(d.rows) { r -> List(d.cols) { c -> MineCell(r, c) } }
        _state.value = MinesweeperState(
            board       = board,
            phase       = MinesweeperPhase.IDLE,
            difficulty  = d,
            mineCount   = d.mines,
            safeCount   = d.rows * d.cols - d.mines,
        )
    }

    // ── Interactions ─────────────────────────────────────────────────────────

    fun reveal(row: Int, col: Int) {
        val s = _state.value
        if (s.phase == MinesweeperPhase.WON || s.phase == MinesweeperPhase.LOST) return
        val cell = s.board.getOrNull(row)?.getOrNull(col) ?: return

        // Chording: tap an already-revealed numbered cell to reveal its neighbours
        if (cell.isRevealed && cell.adjMines > 0) {
            chordReveal(row, col)
            return
        }
        if (cell.isFlagged || cell.isRevealed) return

        val board = if (s.phase == MinesweeperPhase.IDLE) {
            generateBoard(s.difficulty, safeRow = row, safeCol = col)
        } else {
            s.board
        }

        val tapped = board[row][col]
        if (tapped.isMine) {
            timerJob?.cancel()
            _state.update {
                it.copy(
                    board = revealAllMines(board, row, col),
                    phase = MinesweeperPhase.LOST,
                    explodedCell = row to col,
                )
            }
            return
        }

        val wasFirstTap = s.phase == MinesweeperPhase.IDLE
        val revealedBoard = floodReveal(board, row, col)
        if (wasFirstTap) startTimer()
        updateAfterReveal(revealedBoard)
    }

    fun toggleFlag(row: Int, col: Int) {
        val s = _state.value
        if (s.phase == MinesweeperPhase.WON || s.phase == MinesweeperPhase.LOST) return
        val cell = s.board.getOrNull(row)?.getOrNull(col) ?: return
        if (cell.isRevealed) return

        val newFlagged = !cell.isFlagged
        val newBoard = s.board.map { r -> r.toMutableList() }
        newBoard[row][col] = cell.copy(isFlagged = newFlagged)

        _state.update {
            it.copy(
                board = newBoard,
                flagCount = it.flagCount + if (newFlagged) 1 else -1,
            )
        }
    }

    // ── Chord reveal ─────────────────────────────────────────────────────────

    private fun chordReveal(row: Int, col: Int) {
        val s = _state.value
        val cell = s.board[row][col]
        val neighbours = neighboursOf(row, col, s.difficulty.rows, s.difficulty.cols)
        val flagCount = neighbours.count { (r, c) -> s.board[r][c].isFlagged }
        if (flagCount != cell.adjMines) return

        var hit: Pair<Int, Int>? = null
        var board = s.board

        for ((r, c) in neighbours) {
            val nb = board[r][c]
            if (nb.isFlagged || nb.isRevealed) continue
            if (nb.isMine) { hit = r to c; break }
        }

        if (hit != null) {
            timerJob?.cancel()
            _state.update {
                it.copy(
                    board = revealAllMines(board, hit.first, hit.second),
                    phase = MinesweeperPhase.LOST,
                    explodedCell = hit,
                )
            }
            return
        }

        for ((r, c) in neighbours) {
            val nb = board[r][c]
            if (!nb.isFlagged && !nb.isRevealed) {
                board = floodRevealBoard(board, r, c)
            }
        }
        updateAfterReveal(board)
    }

    // ── Reveal helpers ───────────────────────────────────────────────────────

    /** Reveal (row,col) and flood-fill outward through zero-adjacency cells. */
    private fun floodReveal(board: List<List<MineCell>>, row: Int, col: Int): List<List<MineCell>> =
        floodRevealBoard(board, row, col)

    private fun floodRevealBoard(board: List<List<MineCell>>, row: Int, col: Int): List<List<MineCell>> {
        val rows = board.size
        val cols = board[0].size
        val mutable = board.map { it.toMutableList() }
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(row to col)
        val visited = HashSet<Pair<Int, Int>>()

        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            if (!visited.add(r to c)) continue
            val cell = mutable[r][c]
            if (cell.isRevealed || cell.isFlagged || cell.isMine) continue
            mutable[r][c] = cell.copy(isRevealed = true)
            if (cell.adjMines == 0) {
                for ((nr, nc) in neighboursOf(r, c, rows, cols)) {
                    if ((nr to nc) !in visited) queue.add(nr to nc)
                }
            }
        }
        return mutable
    }

    private fun updateAfterReveal(board: List<List<MineCell>>) {
        val revealed = board.sumOf { row -> row.count { it.isRevealed && !it.isMine } }
        val safe = board.sumOf { row -> row.count { !it.isMine } }
        val won = revealed == safe
        if (won) timerJob?.cancel()

        _state.update {
            it.copy(
                board = board,
                phase = if (won) MinesweeperPhase.WON else MinesweeperPhase.PLAYING,
                revealCount = revealed,
                safeCount = safe,
            )
        }
    }

    private fun revealAllMines(
        board: List<List<MineCell>>,
        explodedRow: Int,
        explodedCol: Int,
    ): List<List<MineCell>> = board.map { row ->
        row.map { cell ->
            when {
                cell.row == explodedRow && cell.col == explodedCol -> cell.copy(isRevealed = true)
                cell.isMine && !cell.isFlagged -> cell.copy(isRevealed = true)
                cell.isFlagged && !cell.isMine -> cell.copy(isWrongFlag = true)
                else -> cell
            }
        }
    }

    // ── Timer ────────────────────────────────────────────────────────────────

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

private fun generateBoard(d: MineDifficulty, safeRow: Int, safeCol: Int): List<List<MineCell>> {
    val rows = d.rows
    val cols = d.cols

    // Never place a mine on the first-tapped cell or its immediate neighbours
    val safeZone = (neighboursOf(safeRow, safeCol, rows, cols) + (safeRow to safeCol)).toSet()

    val allCells = (0 until rows).flatMap { r -> (0 until cols).map { c -> r to c } }
    val eligible = allCells.filter { it !in safeZone }.shuffled()
    val mineCount = d.mines.coerceAtMost(eligible.size)
    val mineSet = eligible.take(mineCount).toSet()

    return (0 until rows).map { r ->
        (0 until cols).map { c ->
            val isMine = (r to c) in mineSet
            val adj = neighboursOf(r, c, rows, cols).count { it in mineSet }
            MineCell(r, c, isMine = isMine, adjMines = adj)
        }
    }
}

private fun neighboursOf(row: Int, col: Int, rows: Int, cols: Int): List<Pair<Int, Int>> {
    val result = ArrayList<Pair<Int, Int>>(8)
    for (dr in -1..1) for (dc in -1..1) {
        if (dr == 0 && dc == 0) continue
        val nr = row + dr
        val nc = col + dc
        if (nr in 0 until rows && nc in 0 until cols) result.add(nr to nc)
    }
    return result
}
