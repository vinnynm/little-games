package com.enigma.littlegames.ui.games.nonogram

// ─────────────────────────────────────────────────────────────────────────────
// Nonogram ViewModel
// Manages puzzle state: player marks, errors, timer, hints via line solver.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// Player can mark a cell as: EMPTY (untouched), FILLED, CROSSED (definitely empty)
enum class CellMark { EMPTY, FILLED, CROSSED }

data class NonogramState(
    val playerGrid: Array<Array<CellMark>>  = Array(5) { Array(5) { CellMark.EMPTY } },
    val solution: Array<BooleanArray>       = Array(5) { BooleanArray(5) },
    val rowClues: List<List<Int>>           = emptyList(),
    val colClues: List<List<Int>>           = emptyList(),
    val size: Int                           = 5,
    val difficulty: NonogramDifficulty      = NonogramDifficulty.EASY,
    val isComplete: Boolean                 = false,
    val errorCells: Set<Pair<Int, Int>>     = emptySet(),
    val elapsedSecs: Long                   = 0L,
    val errorCount: Int                     = 0,
    val generating: Boolean                 = true,
    // Hint: cells the solver says are forced (to flash for one beat)
    val hintCells: Set<Pair<Int, Int>>      = emptySet(),
    val hintUsed: Boolean                   = false,
)

class NonogramViewModel : ViewModel() {
    private val _state = MutableStateFlow(NonogramState())
    val state: StateFlow<NonogramState> = _state.asStateFlow()
    private var timerJob: Job? = null

    init { newGame(NonogramDifficulty.EASY) }

    fun newGame(d: NonogramDifficulty) {
        timerJob?.cancel()
        _state.update { it.copy(generating = true, difficulty = d, elapsedSecs = 0,
            errorCount = 0, isComplete = false, hintCells = emptySet(), hintUsed = false) }
        viewModelScope.launch(Dispatchers.Default) {
            val solution = randomImage(d)
            val rowClues = computeRowClues(solution)
            val colClues = computeColClues(solution)
            val n        = d.size
            _state.update { it.copy(
                generating = false,
                solution   = solution,
                rowClues   = rowClues,
                colClues   = colClues,
                size       = n,
                playerGrid = Array(n) { Array(n) { CellMark.EMPTY } },
                errorCells = emptySet(),
            )}
        }
        startTimer()
    }

    // Tap cycles: EMPTY → FILLED → CROSSED → EMPTY
    fun toggleCell(r: Int, c: Int) {
        val s = _state.value
        if (s.isComplete || s.generating) return
        val newGrid = s.playerGrid.map { it.clone() }.toTypedArray()
        newGrid[r][c] = when (s.playerGrid[r][c]) {
            CellMark.EMPTY   -> CellMark.FILLED
            CellMark.FILLED  -> CellMark.CROSSED
            CellMark.CROSSED -> CellMark.EMPTY
        }
        val errors  = computeErrors(newGrid, s.solution, s.size)
        val wasErr  = (r to c) in s.errorCells
        val newCnt  = if ((r to c) in errors && !wasErr && newGrid[r][c] == CellMark.FILLED)
            s.errorCount + 1 else s.errorCount
        val done    = errors.isEmpty() && newGrid.all { row ->
            row.none { it == CellMark.EMPTY }
        } && checkComplete(newGrid, s.solution, s.size)
        _state.update { it.copy(playerGrid = newGrid, errorCells = errors,
            errorCount = newCnt, isComplete = done, hintCells = emptySet()) }
    }

    // Hint: run the iterative solver and reveal one forced unknown cell
    fun hint() {
        val s = _state.value
        val known = Array(s.size) { r ->
            IntArray(s.size) { c ->
                when (s.playerGrid[r][c]) {
                    CellMark.FILLED  ->  1
                    CellMark.CROSSED -> -1
                    CellMark.EMPTY   ->  0
                }
            }
        }
        val solved = iterativeSolve(s.rowClues, s.colClues, s.size)
        val hints  = mutableSetOf<Pair<Int, Int>>()
        for (r in 0 until s.size) for (c in 0 until s.size) {
            if (known[r][c] == 0 && solved[r][c] != 0) hints.add(r to c)
        }
        _state.update { it.copy(hintCells = hints.take(3).toSet(), hintUsed = true) }
        // Auto-clear hint flash after 1.5s
        viewModelScope.launch {
            delay(1500)
            _state.update { it.copy(hintCells = emptySet()) }
        }
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (!_state.value.isComplete) _state.update { it.copy(elapsedSecs = it.elapsedSecs + 1) }
            }
        }
    }

    override fun onCleared() { timerJob?.cancel() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun computeErrors(
    grid: Array<Array<CellMark>>,
    solution: Array<BooleanArray>,
    n: Int,
): Set<Pair<Int, Int>> {
    val errors = mutableSetOf<Pair<Int, Int>>()
    for (r in 0 until n) for (c in 0 until n) {
        val mark = grid[r][c]
        val sol  = solution[r][c]
        // A FILLED cell where solution is empty = error
        if (mark == CellMark.FILLED && !sol) errors.add(r to c)
    }
    return errors
}

private fun checkComplete(
    grid: Array<Array<CellMark>>,
    solution: Array<BooleanArray>,
    n: Int,
): Boolean {
    for (r in 0 until n) for (c in 0 until n) {
        val isFilled = grid[r][c] == CellMark.FILLED
        if (isFilled != solution[r][c]) return false
    }
    return true
}
