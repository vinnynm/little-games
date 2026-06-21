package com.enigma.littlegames.ui.games.sudoku

// ─────────────────────────────────────────────────────────────────────────────
// Classic Sudoku — ViewModel
// Standard 9×9 with four difficulty levels controlling how many givens remain.
// Uses the same backtracking solver from KillerSudoku.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SudokuCell(
    val value: Int = 0,
    val isGiven: Boolean = false,
    val notes: Set<Int> = emptySet(),
)

enum class SudokuDifficulty(val givens: Int, val label: String, val emoji: String) {
    EASY  (42, "Easy",   "🌿"),
    MEDIUM(32, "Medium", "⚡"),
    HARD  (25, "Hard",   "🔥"),
    EXPERT(20, "Expert", "💀"),
}

data class SudokuState(
    val board: Array<Array<SudokuCell>> = Array(9) { Array(9) { SudokuCell() } },
    val solution: Array<IntArray>       = Array(9) { IntArray(9) },
    val selected: Pair<Int, Int>?       = null,
    val noteMode: Boolean               = false,
    val errors: Set<Pair<Int, Int>>     = emptySet(),
    val isComplete: Boolean             = false,
    val difficulty: SudokuDifficulty    = SudokuDifficulty.MEDIUM,
    val elapsedSecs: Long               = 0L,
    val errorCount: Int                 = 0,
    val generating: Boolean             = true,
)

// ── Generator ─────────────────────────────────────────────────────────────────

private fun generateSolution(): Array<IntArray> {
    val g = Array(9) { IntArray(9) }
    fun safe(r: Int, c: Int, n: Int): Boolean {
        if (g[r].any { it == n }) return false
        if (g.any { it[c] == n }) return false
        val sr = r / 3 * 3; val sc = c / 3 * 3
        for (dr in 0..2) for (dc in 0..2) if (g[sr + dr][sc + dc] == n) return false
        return true
    }
    for (box in 0..2) {
        val nums = (1..9).shuffled(); var i = 0
        for (r in box * 3 until box * 3 + 3) for (c in box * 3 until box * 3 + 3) g[r][c] = nums[i++]
    }
    fun fill(): Boolean {
        for (r in 0..8) for (c in 0..8) {
            if (g[r][c] != 0) continue
            for (n in (1..9).shuffled()) {
                if (safe(r, c, n)) { g[r][c] = n; if (fill()) return true; g[r][c] = 0 }
            }
            return false
        }
        return true
    }
    fill(); return g
}

private fun makeBoard(sol: Array<IntArray>, givens: Int): Array<Array<SudokuCell>> {
    val b = Array(9) { r -> Array(9) { c -> SudokuCell(sol[r][c], isGiven = true) } }
    // Symmetrically remove cells for aesthetic appeal
    val indices = (0..80).shuffled()
    var removed = 0
    for (i in indices) {
        if (81 - removed <= givens) break
        val r = i / 9; val c = i % 9
        if (b[r][c].isGiven) {
            b[r][c] = SudokuCell()
            removed++
        }
    }
    return b
}

fun computeSudokuErrors(board: Array<Array<SudokuCell>>): Set<Pair<Int, Int>> {
    val err = mutableSetOf<Pair<Int, Int>>()
    fun rowOk(r: Int) = board[r].map { it.value }.filter { it != 0 }.let { v -> v.size == v.toSet().size }
    fun colOk(c: Int) = board.map { it[c].value }.filter { it != 0 }.let { v -> v.size == v.toSet().size }
    fun boxOk(r: Int, c: Int): Boolean {
        val sr = r / 3 * 3; val sc = c / 3 * 3
        val v = (sr until sr + 3).flatMap { dr -> (sc until sc + 3).map { dc -> board[dr][dc].value } }.filter { it != 0 }
        return v.size == v.toSet().size
    }
    for (r in 0..8) for (c in 0..8)
        if (board[r][c].value != 0 && (!rowOk(r) || !colOk(c) || !boxOk(r, c))) err.add(r to c)
    return err
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class SudokuViewModel : ViewModel() {
    private val _state = MutableStateFlow(SudokuState())
    val state: StateFlow<SudokuState> = _state.asStateFlow()
    private var timerJob: Job? = null

    init { newGame(SudokuDifficulty.MEDIUM) }

    fun newGame(d: SudokuDifficulty) {
        timerJob?.cancel()
        _state.update { it.copy(generating = true, difficulty = d, elapsedSecs = 0L, errorCount = 0, isComplete = false, selected = null) }
        viewModelScope.launch(Dispatchers.Default) {
            val sol   = generateSolution()
            val board = makeBoard(sol, d.givens)
            _state.update { it.copy(generating = false, solution = sol, board = board, errors = emptySet()) }
        }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (!_state.value.isComplete) _state.update { it.copy(elapsedSecs = it.elapsedSecs + 1) }
            }
        }
    }

    fun select(r: Int, c: Int) {
        _state.update { it.copy(selected = if (it.selected == r to c) null else r to c) }
    }

    fun place(n: Int) {
        val s = _state.value; val (r, c) = s.selected ?: return
        if (s.board[r][c].isGiven) return
        val nb = s.board.map { it.clone() }.toTypedArray()
        if (s.noteMode && n != 0) {
            val notes = nb[r][c].notes.toMutableSet()
            if (n in notes) notes.remove(n) else notes.add(n)
            nb[r][c] = nb[r][c].copy(notes = notes, value = 0)
            _state.update { it.copy(board = nb, errors = computeSudokuErrors(nb)) }
            return
        }
        val wasErr = (r to c) in s.errors
        nb[r][c] = nb[r][c].copy(value = n, notes = emptySet())
        val errs    = computeSudokuErrors(nb)
        val newErrs = if (n != 0 && (r to c) in errs && !wasErr) s.errorCount + 1 else s.errorCount
        val done    = errs.isEmpty() && nb.all { row -> row.all { it.value != 0 } }
        _state.update { it.copy(board = nb, errors = errs, errorCount = newErrs, isComplete = done) }
    }

    fun toggleNotes() { _state.update { it.copy(noteMode = !it.noteMode) } }

    fun solve() {
        val s = _state.value
        val solved = Array(9) { r -> Array(9) { c -> SudokuCell(s.solution[r][c], isGiven = true) } }
        _state.update { it.copy(board = solved, isComplete = true, errors = emptySet()) }
    }

    fun hint() {
        // Fill one random incorrect/empty cell with the correct value
        val s     = _state.value
        val empties = (0..80).filter { i ->
            val r = i / 9; val c = i % 9
            !s.board[r][c].isGiven && s.board[r][c].value != s.solution[r][c]
        }.shuffled()
        val pick = empties.firstOrNull() ?: return
        val r = pick / 9; val c = pick % 9
        val nb = s.board.map { it.clone() }.toTypedArray()
        nb[r][c] = SudokuCell(s.solution[r][c], isGiven = false)
        val errs = computeSudokuErrors(nb)
        val done = errs.isEmpty() && nb.all { row -> row.all { it.value != 0 } }
        _state.update { it.copy(board = nb, errors = errs, isComplete = done, selected = r to c) }
    }

    override fun onCleared() { timerJob?.cancel() }
}
