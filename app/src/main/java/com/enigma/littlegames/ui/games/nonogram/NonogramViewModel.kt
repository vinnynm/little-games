package com.enigma.littlegames.ui.games.nonogram

// ─────────────────────────────────────────────────────────────────────────────
// Nonogram ViewModel
// Updated tap model:
//   • Single tap: EMPTY → FILLED, FILLED → EMPTY, CROSSED → EMPTY
//   • Long press: any cell → CROSSED (mark as definitely empty)
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
    val hintCells: Set<Pair<Int, Int>>      = emptySet(),
    val hintUsed: Boolean                   = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NonogramState
        if (size != other.size) return false
        if (difficulty != other.difficulty) return false
        if (isComplete != other.isComplete) return false
        if (elapsedSecs != other.elapsedSecs) return false
        if (errorCount != other.errorCount) return false
        if (generating != other.generating) return false
        if (hintUsed != other.hintUsed) return false
        return true
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + difficulty.hashCode()
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + elapsedSecs.hashCode()
        result = 31 * result + errorCount
        result = 31 * result + generating.hashCode()
        return result
    }
}

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

    /**
     * Single tap: toggle between FILLED and EMPTY.
     * EMPTY → FILLED
     * FILLED → EMPTY
     * CROSSED → EMPTY  (tap removes a cross mark)
     */
    fun tapCell(r: Int, c: Int) {
        val s = _state.value
        if (s.isComplete || s.generating) return
        val newGrid = s.playerGrid.map { it.clone() }.toTypedArray()
        newGrid[r][c] = when (s.playerGrid[r][c]) {
            CellMark.EMPTY   -> CellMark.FILLED
            CellMark.FILLED  -> CellMark.EMPTY
            CellMark.CROSSED -> CellMark.EMPTY
        }
        applyGridUpdate(s, newGrid, r, c)
    }

    /**
     * Long press: mark cell as CROSSED (definitely empty).
     * EMPTY → CROSSED
     * CROSSED → EMPTY  (long press again removes the cross)
     * FILLED → CROSSED  (override a wrong fill)
     */
    fun longPressCell(r: Int, c: Int) {
        val s = _state.value
        if (s.isComplete || s.generating) return
        val newGrid = s.playerGrid.map { it.clone() }.toTypedArray()
        newGrid[r][c] = when (s.playerGrid[r][c]) {
            CellMark.CROSSED -> CellMark.EMPTY
            else             -> CellMark.CROSSED
        }
        applyGridUpdate(s, newGrid, r, c)
    }

    private fun applyGridUpdate(s: NonogramState, newGrid: Array<Array<CellMark>>, r: Int, c: Int) {
        val errors  = computeErrors(newGrid, s.solution, s.size)
        val wasErr  = (r to c) in s.errorCells
        val newCnt  = if ((r to c) in errors && !wasErr && newGrid[r][c] == CellMark.FILLED)
            s.errorCount + 1 else s.errorCount
        val done    = errors.isEmpty() && checkComplete(newGrid, s.solution, s.size)
        _state.update { it.copy(playerGrid = newGrid, errorCells = errors,
            errorCount = newCnt, isComplete = done, hintCells = emptySet()) }
    }

    // Legacy entry point — kept so NonogramScreen can call either
    fun toggleCell(r: Int, c: Int) = tapCell(r, c)

    fun hint() {
        val s = _state.value
        val solved = iterativeSolve(s.rowClues, s.colClues, s.size)
        val hints  = mutableSetOf<Pair<Int, Int>>()
        for (r in 0 until s.size) for (c in 0 until s.size) {
            val currentMark = s.playerGrid[r][c]
            if (currentMark == CellMark.EMPTY && solved[r][c] != 0) hints.add(r to c)
        }
        _state.update { it.copy(hintCells = hints.take(3).toSet(), hintUsed = true) }
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
        if (grid[r][c] == CellMark.FILLED && !solution[r][c]) errors.add(r to c)
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

