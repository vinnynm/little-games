package com.enigma.littlegames.ui.games.killerSudoku

// ─────────────────────────────────────────────────────────────────────────────
// Killer Sudoku — ViewModel
// Phase 2: error count, elapsed timer, reports to HubViewModel
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

// ── Data models ───────────────────────────────────────────────────────────────

data class SKCell(val value: Int = 0, val isGiven: Boolean = false, val notes: Set<Int> = emptySet())
data class SKCage(val id: Int, val sum: Int, val cells: List<Pair<Int, Int>>, val colorIdx: Int)

enum class SKDifficulty(val minCage: Int, val maxCage: Int, val reveal: Int, val label: String, val emoji: String) {
    EASY  (1, 3, 40, "Easy",   "🌿"),
    MEDIUM(2, 5, 32, "Medium", "⚡"),
    HARD  (2, 6, 26, "Hard",   "🔥"),
    EXPERT(3, 7, 20, "Expert", "💀"),
}

data class KSState(
    val board: Array<Array<SKCell>>  = Array(9) { Array(9) { SKCell() } },
    val cages: List<SKCage>          = emptyList(),
    val solution: Array<IntArray>    = Array(9) { IntArray(9) },
    val selected: Pair<Int,Int>?     = null,
    val noteMode: Boolean            = false,
    val errors: Set<Pair<Int,Int>>   = emptySet(),
    val isComplete: Boolean          = false,
    val difficulty: SKDifficulty     = SKDifficulty.MEDIUM,
    val elapsedSecs: Long            = 0L,
    val errorCount: Int              = 0,
    val generating: Boolean          = true,
)

// ── Generator helpers ─────────────────────────────────────────────────────────

fun generateSolution(): Array<IntArray> {
    val g = Array(9) { IntArray(9) }
    fun safe(r: Int, c: Int, n: Int): Boolean {
        if (g[r].any { it == n }) return false
        if (g.any { it[c] == n }) return false
        val sr = r / 3 * 3; val sc = c / 3 * 3
        for (dr in 0..2) for (dc in 0..2) if (g[sr+dr][sc+dc] == n) return false
        return true
    }
    for (box in 0..2) {
        val nums = (1..9).shuffled(); var i = 0
        for (r in box*3 until box*3+3) for (c in box*3 until box*3+3) g[r][c] = nums[i++]
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

fun buildCages(sol: Array<IntArray>, d: SKDifficulty): List<SKCage> {
    val assigned = Array(9) { BooleanArray(9) }
    val result = mutableListOf<SKCage>()
    val order = (0..80).map { it / 9 to it % 9 }.shuffled()
    for ((sr, sc) in order) {
        if (assigned[sr][sc]) continue
        val target = d.minCage + Random.nextInt(d.maxCage - d.minCage + 1)
        val cage = mutableListOf(sr to sc); assigned[sr][sc] = true
        repeat(target - 1) {
            val used = cage.map { (r,c) -> sol[r][c] }.toSet()
            val cands = cage.flatMap { (r,c) ->
                listOf(r-1 to c, r+1 to c, r to c-1, r to c+1)
                    .filter { (nr,nc) -> nr in 0..8 && nc in 0..8 && !assigned[nr][nc] && sol[nr][nc] !in used }
            }.distinct().shuffled()
            cands.firstOrNull()?.also { cage.add(it); assigned[it.first][it.second] = true }
        }
        result.add(SKCage(result.size, cage.sumOf { (r,c) -> sol[r][c] }, cage, result.size % 18))
    }
    return result
}

fun makeBoard(sol: Array<IntArray>, reveal: Int): Array<Array<SKCell>> {
    val b = Array(9) { Array(9) { SKCell() } }
    (0..80).shuffled().take(reveal).forEach { i -> val r = i/9; val c = i%9; b[r][c] = SKCell(sol[r][c], isGiven = true) }
    return b
}

fun computeErrors(board: Array<Array<SKCell>>, cages: List<SKCage>): Set<Pair<Int,Int>> {
    val err = mutableSetOf<Pair<Int,Int>>()
    fun rowOk(r: Int) = board[r].map { it.value }.filter { it != 0 }.let { v -> v.size == v.toSet().size }
    fun colOk(c: Int) = board.map { it[c].value }.filter { it != 0 }.let { v -> v.size == v.toSet().size }
    fun boxOk(r: Int, c: Int): Boolean {
        val sr = r/3*3; val sc = c/3*3
        val v = (sr until sr+3).flatMap { dr -> (sc until sc+3).map { dc -> board[dr][dc].value } }.filter { it != 0 }
        return v.size == v.toSet().size
    }
    for (r in 0..8) for (c in 0..8)
        if (board[r][c].value != 0 && (!rowOk(r) || !colOk(c) || !boxOk(r, c))) err.add(r to c)
    for (cage in cages) {
        val vals = cage.cells.map { (r,c) -> board[r][c].value }
        val filled = vals.filter { it != 0 }
        if (filled.size != filled.toSet().size || (vals.all { it != 0 } && vals.sum() != cage.sum))
            cage.cells.filter { (r,c) -> board[r][c].value != 0 }.forEach { err.add(it) }
    }
    return err
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class KillerSudokuViewModel : ViewModel() {
    private val _state = MutableStateFlow(KSState())
    val state: StateFlow<KSState> = _state.asStateFlow()
    private var timerJob: Job? = null

    init { newGame(SKDifficulty.MEDIUM) }

    fun newGame(d: SKDifficulty) {
        timerJob?.cancel()
        _state.update { it.copy(generating = true, difficulty = d, elapsedSecs = 0L, errorCount = 0, isComplete = false, selected = null) }
        viewModelScope.launch(Dispatchers.Default) {
            val sol   = generateSolution()
            val cages = buildCages(sol, d)
            val board = makeBoard(sol, d.reveal)
            _state.update { it.copy(generating = false, solution = sol, cages = cages, board = board) }
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
            _state.update { it.copy(board = nb, errors = computeErrors(nb, s.cages)) }
            return
        }
        val wasErr = (r to c) in s.errors
        nb[r][c] = nb[r][c].copy(value = n, notes = emptySet())
        val errs = computeErrors(nb, s.cages)
        val newErrCount = if (n != 0 && (r to c) in errs && !wasErr) s.errorCount + 1 else s.errorCount
        val complete = errs.isEmpty() && nb.all { row -> row.all { it.value != 0 } }
        _state.update { it.copy(board = nb, errors = errs, errorCount = newErrCount, isComplete = complete) }
    }

    fun toggleNotes() { _state.update { it.copy(noteMode = !it.noteMode) } }

    fun solve() {
        val s = _state.value
        val solved = Array(9) { r -> Array(9) { c -> SKCell(s.solution[r][c], isGiven = true) } }
        _state.update { it.copy(board = solved, isComplete = true, errors = emptySet()) }
    }

    override fun onCleared() { timerJob?.cancel() }
}
