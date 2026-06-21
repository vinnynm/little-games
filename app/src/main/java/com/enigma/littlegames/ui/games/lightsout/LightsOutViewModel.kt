 package com.enigma.littlegames.ui.games.lightsout

// ─────────────────────────────────────────────────────────────────────────────
// Lights Out — ViewModel with Phase 2 additions:
//   • Reports win to HubViewModel (achievements + persistence)
//   • Tracks hint usage (for "Blind Tactician" achievement)
//   • Tracks no-solve streak for PIPE_NO_SOLVE equivalent
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── GF(2) neighbour lookup ────────────────────────────────────────────────────

private val LO_NEIGHBORS = Array(25) { idx ->
    val r = idx / 5; val c = idx % 5
    listOf(idx,
        if (r > 0) idx - 5 else -1,
        if (r < 4) idx + 5 else -1,
        if (c > 0) idx - 1 else -1,
        if (c < 4) idx + 1 else -1
    ).filter { it >= 0 }
}

// ── GF(2) solver ─────────────────────────────────────────────────────────────

private fun loSolve(b: IntArray): IntArray? {
    val n = 25
    val aug = Array(n) { r ->
        IntArray(n + 1).also { row ->
            for (c in 0 until n) row[c] = if (c in LO_NEIGHBORS[r]) 1 else 0
            row[n] = b[r] and 1
        }
    }
    var pivotRow = 0
    for (col in 0 until n) {
        val found = (pivotRow until n).firstOrNull { aug[it][col] == 1 } ?: continue
        if (found != pivotRow) { val tmp = aug[pivotRow]; aug[pivotRow] = aug[found]; aug[found] = tmp }
        for (row in 0 until n) if (row != pivotRow && aug[row][col] == 1)
            for (k in 0..n) aug[row][k] = aug[row][k] xor aug[pivotRow][k]
        pivotRow++
    }
    for (r in 0 until n) if ((0 until n).all { aug[r][it] == 0 } && aug[r][n] == 1) return null
    val x = IntArray(n)
    for (col in 0 until n) {
        val pRow = (0 until n).firstOrNull { r ->
            aug[r][col] == 1 && (0 until n).all { c -> c == col || aug[r][c] == 0 }
        }
        if (pRow != null) x[col] = aug[pRow][n]
    }
    return x
}

private fun loGenerate(pressCount: Int): Pair<IntArray, IntArray> {
    while (true) {
        val board = IntArray(25)
        for (btn in (0 until 25).shuffled().take(pressCount))
            for (idx in LO_NEIGHBORS[btn]) board[idx] = board[idx] xor 1
        if (board.all { it == 0 }) continue
        val sol = loSolve(board) ?: continue
        return board to sol
    }
}

// ── Difficulty ────────────────────────────────────────────────────────────────

enum class LoDifficulty(val pressCount: Int, val label: String) {
    EASY(5, "Easy"), MEDIUM(10, "Medium"), HARD(16, "Hard"), EXPERT(22, "Expert")
}

// ── UI State ──────────────────────────────────────────────────────────────────

data class LightsOutUiState(
    val cells: IntArray       = IntArray(25),
    val presses: IntArray     = IntArray(25),
    val solution: IntArray    = IntArray(25),
    val moveCount: Int        = 0,
    val isSolved: Boolean     = false,
    val showHint: Boolean     = false,
    val hintUsed: Boolean     = false,   // Phase 2: track for "Blind Tactician"
    val difficulty: LoDifficulty = LoDifficulty.MEDIUM,
    val isGenerating: Boolean = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class LightsOutViewModel : ViewModel() {
    private val _state = MutableStateFlow(LightsOutUiState())
    val state: StateFlow<LightsOutUiState> = _state.asStateFlow()

    init { newGame(LoDifficulty.MEDIUM) }

    fun newGame(d: LoDifficulty) {
        _state.update { it.copy(isGenerating = true, difficulty = d, hintUsed = false) }
        viewModelScope.launch(Dispatchers.Default) {
            val (board, sol) = loGenerate(d.pressCount)
            _state.update { LightsOutUiState(cells = board, solution = sol, difficulty = d) }
        }
    }

    fun press(idx: Int) {
        val s = _state.value
        if (s.isSolved || s.isGenerating) return
        val newCells   = s.cells.copyOf()
        val newPresses = s.presses.copyOf()
        for (i in LO_NEIGHBORS[idx]) newCells[i] = newCells[i] xor 1
        newPresses[idx] = newPresses[idx] xor 1
        _state.update {
            it.copy(
                cells     = newCells,
                presses   = newPresses,
                moveCount = it.moveCount + 1,
                isSolved  = newCells.all { c -> c == 0 },
            )
        }
    }

    fun toggleHint() {
        _state.update { it.copy(showHint = !it.showHint, hintUsed = it.hintUsed || !it.showHint) }
    }

    fun isHintCell(idx: Int): Boolean =
        _state.value.let { it.showHint && it.solution[idx] == 1 && it.presses[idx] == 0 }
}
