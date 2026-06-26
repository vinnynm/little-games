package com.enigma.littlegames.ui.games.kakuro

// ─────────────────────────────────────────────────────────────────────────────
// Kakuro — ViewModel
//
// Difficulty now drives actual grid size:
//   Easy   →  7×7  (small, approachable)
//   Medium →  9×9  (standard)
//   Hard   → 11×11 (large)
//   Expert → 13×13 (very large)
//
// Clue-cell convention (standard Kakuro):
//   The diagonal runs TOP-LEFT → BOTTOM-RIGHT.
//
//   • RIGHT (across) clue — applies to the horizontal run starting to the
//     right of this cell.  Sits in the TOP-RIGHT triangle.
//     The clue cell is the BLACK cell immediately to the LEFT of that run.
//
//   • DOWN clue — applies to the vertical run starting below this cell.
//     Sits in the BOTTOM-LEFT triangle.
//     The clue cell is the BLACK cell immediately ABOVE that run.
//
//   In the data model we keep field names `down` and `right` as before;
//   only the rendering positions change (handled in KakuroScreen).
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ── Cell model ────────────────────────────────────────────────────────────────

sealed class KakuroCell {
    /** A clue cell (black square with optional down/right clue numbers) */
    data class Clue(val down: Int? = null, val right: Int? = null) : KakuroCell()
    /** A white cell the player fills in */
    data class White(val value: Int = 0, val notes: Set<Int> = emptySet()) : KakuroCell()
}

/** A "run" — a horizontal or vertical sequence of white cells with a target sum */
data class KakuroRun(
    val cells: List<Pair<Int, Int>>,  // (row, col) of each white cell in the run
    val clueRow: Int,                 // row of the clue cell
    val clueCol: Int,                 // col of the clue cell
    val isHorizontal: Boolean,
    val sum: Int,
)

enum class KakuroDifficulty(val label: String, val emoji: String, val gridKey: String) {
    EASY  ("Easy",   "🌿", "easy"),
    MEDIUM("Medium", "⚡", "medium"),
    HARD  ("Hard",   "🔥", "hard"),
    EXPERT("Expert", "💀", "expert"),
}

data class KakuroState(
    val size: Int                          = 9,
    val cells: Array<Array<KakuroCell>>    = Array(9) { Array(9) { KakuroCell.Clue() } },
    val runs: List<KakuroRun>              = emptyList(),
    val solution: Array<IntArray>          = Array(9) { IntArray(9) },   // 0 for non-white
    val selected: Pair<Int, Int>?          = null,
    val errors: Set<Pair<Int, Int>>        = emptySet(),
    val isComplete: Boolean                = false,
    val difficulty: KakuroDifficulty       = KakuroDifficulty.MEDIUM,
    val elapsedSecs: Long                  = 0L,
    val errorCount: Int                    = 0,
    val generating: Boolean                = true,
)

// ── Hardcoded layouts (white-cell masks) ──────────────────────────────────────
// 'W' = white (player fills in), 'B' = black/clue cell.
//
// Grid sizes by difficulty:
//   Easy   =  7×7
//   Medium =  9×9
//   Hard   = 11×11
//   Expert = 13×13
//
// Layout rules:
//   • Every white cell must belong to exactly one horizontal run AND one
//     vertical run, each of length ≥ 2.
//   • Black border rows/cols ensure every run has a valid clue cell to its
//     left (horizontal) or above (vertical).

// ── Easy 7×7 ─────────────────────────────────────────────────────────────────
private val EASY_LAYOUT = arrayOf(
    "BBBBBBB",
    "BBBWWWB",
    "BBWWWBB",
    "BWWWWWB",
    "BBWWWBB",
    "BWWWBBB",
    "BBBBBBB",
)

// ── Medium 9×9 ───────────────────────────────────────────────────────────────
private val MEDIUM_LAYOUT = arrayOf(
    "BBBBBBBBB",
    "BBBWWBWWB",
    "BBWWWWWBB",
    "BWWBWWBWB",
    "BWWWWWWWB",
    "BWBWWBWWB",
    "BBWWWWWBB",
    "BWWBWWBBB",
    "BBBBBBBBB",
)

// ── Hard 11×11 ───────────────────────────────────────────────────────────────
private val HARD_LAYOUT = arrayOf(
    "BBBBBBBBBBB",
    "BBBWWWBWWBB",
    "BBWWWWWWWBB",
    "BWWBWWWBWWB",
    "BWWWWBWWWWB",
    "BBWWWWWWWBB",
    "BWWWWBWWWWB",
    "BWWBWWWBWWB",
    "BBWWWWWWWBB",
    "BBBWWWBWWBB",
    "BBBBBBBBBBB",
)

// ── Expert 13×13 ─────────────────────────────────────────────────────────────
private val EXPERT_LAYOUT = arrayOf(
    "BBBBBBBBBBBBB",
    "BBBWWWBWWWBBB",
    "BBWWWWWWWWWBB",
    "BWWBWWWWWBWWB",
    "BWWWWBWBWWWWB",
    "BBWWWWWWWWWBB",
    "BWWBWWWWWBWWB",
    "BBWWWWWWWWWBB",
    "BWWWWBWBWWWWB",
    "BWWBWWWWWBWWB",
    "BBWWWWWWWWWBB",
    "BBBWWWBWWWBBB",
    "BBBBBBBBBBBBB",
)

private fun layoutFor(d: KakuroDifficulty) = when (d) {
    KakuroDifficulty.EASY   -> EASY_LAYOUT
    KakuroDifficulty.MEDIUM -> MEDIUM_LAYOUT
    KakuroDifficulty.HARD   -> HARD_LAYOUT
    KakuroDifficulty.EXPERT -> EXPERT_LAYOUT
}

// ── Solver / generator ────────────────────────────────────────────────────────

/**
 * Build runs from a white-cell mask.
 * Runs with fewer than 2 white cells are excluded (no valid Kakuro run).
 */
private fun buildRunsFromLayout(mask: Array<String>): List<KakuroRun> {
    val rows = mask.size; val cols = mask[0].length
    val runs = mutableListOf<KakuroRun>()

    // Horizontal runs
    for (r in 0 until rows) {
        var c = 0
        while (c < cols) {
            if (mask[r][c] == 'W') {
                val cells = mutableListOf<Pair<Int, Int>>()
                while (c < cols && mask[r][c] == 'W') { cells.add(r to c); c++ }
                if (cells.size >= 2) {
                    // clue cell is the black cell immediately to the left of the run
                    runs.add(KakuroRun(cells, r, cells.first().second - 1, true, 0))
                }
            } else c++
        }
    }

    // Vertical runs
    for (c in 0 until cols) {
        var r = 0
        while (r < rows) {
            if (mask[r][c] == 'W') {
                val cells = mutableListOf<Pair<Int, Int>>()
                while (r < rows && mask[r][c] == 'W') { cells.add(r to c); r++ }
                if (cells.size >= 2) {
                    // clue cell is the black cell immediately above the run
                    runs.add(KakuroRun(cells, cells.first().first - 1, c, false, 0))
                }
            } else r++
        }
    }
    return runs
}

/**
 * Solve a Kakuro board: place digits 1–9, no repeats within a run,
 * using backtracking. Returns true on success.
 */
private fun solveBoard(
    rows: Int, cols: Int,
    runs: List<KakuroRun>,
    grid: Array<IntArray> = Array(rows) { IntArray(cols) },
    pos: Int = 0,
    whiteCells: List<Pair<Int, Int>> = runs.flatMap { it.cells }.distinct()
        .sortedWith(compareBy({ it.first }, { it.second })),
): Boolean {
    if (pos >= whiteCells.size) return true
    val (r, c) = whiteCells[pos]
    if (grid[r][c] != 0) return solveBoard(rows, cols, runs, grid, pos + 1, whiteCells)

    val hRun = runs.firstOrNull { it.isHorizontal  && (r to c) in it.cells }
    val vRun = runs.firstOrNull { !it.isHorizontal && (r to c) in it.cells }
    val usedH = hRun?.cells?.filter { it != r to c }?.map { (pr, pc) -> grid[pr][pc] }?.toSet() ?: emptySet()
    val usedV = vRun?.cells?.filter { it != r to c }?.map { (pr, pc) -> grid[pr][pc] }?.toSet() ?: emptySet()
    val forbidden = usedH + usedV

    for (d in (1..9).shuffled()) {
        if (d in forbidden) continue
        grid[r][c] = d
        if (solveBoard(rows, cols, runs, grid, pos + 1, whiteCells)) return true
        grid[r][c] = 0
    }
    return false
}

private fun generateKakuro(d: KakuroDifficulty): Triple<Array<Array<KakuroCell>>, List<KakuroRun>, Array<IntArray>> {
    val layout = layoutFor(d)
    val rows = layout.size; val cols = layout[0].length

    val runsNoSum = buildRunsFromLayout(layout)

    // Solve to get digit placements
    val grid = Array(rows) { IntArray(cols) }
    solveBoard(rows, cols, runsNoSum, grid)

    // Compute actual sums
    val runs = runsNoSum.map { run ->
        run.copy(sum = run.cells.sumOf { (r, c) -> grid[r][c] })
    }

    // Build cell array for display
    // Pass 1: mark white cells
    val cells: Array<Array<KakuroCell>> = Array(rows) { r ->
        Array(cols) { c -> if (layout[r][c] == 'W') KakuroCell.White() else KakuroCell.Clue() }
    }
    // Pass 2: assign clues to black cells
    // Convention:
    //   Horizontal run  → clue cell is LEFT of run  → RIGHT clue stored in `right` field
    //   Vertical run    → clue cell is ABOVE run     → DOWN  clue stored in `down`  field
    runs.forEach { run ->
        val cr = run.clueRow; val cc = run.clueCol
        val existing = cells[cr][cc] as? KakuroCell.Clue ?: KakuroCell.Clue()
        cells[cr][cc] = if (run.isHorizontal)
            existing.copy(right = run.sum)
        else
            existing.copy(down = run.sum)
    }

    return Triple(cells, runs, grid)
}

// ── Error computation ─────────────────────────────────────────────────────────

fun computeKakuroErrors(
    cells: Array<Array<KakuroCell>>,
    runs: List<KakuroRun>,
): Set<Pair<Int, Int>> {
    val errors = mutableSetOf<Pair<Int, Int>>()
    for (run in runs) {
        val values = run.cells.map { (r, c) -> (cells[r][c] as? KakuroCell.White)?.value ?: 0 }
        val filled = values.filter { it > 0 }
        // Duplicate digits in run
        if (filled.size != filled.toSet().size) {
            run.cells.forEachIndexed { i, cell -> if (values[i] > 0) errors.add(cell) }
        }
        // Wrong sum when fully filled
        if (values.none { it == 0 } && values.sum() != run.sum) {
            run.cells.forEach { errors.add(it) }
        }
    }
    return errors
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class KakuroViewModel : ViewModel() {
    private val _state = MutableStateFlow(KakuroState())
    val state: StateFlow<KakuroState> = _state.asStateFlow()
    private var timerJob: Job? = null

    init { newGame(KakuroDifficulty.MEDIUM) }

    fun newGame(d: KakuroDifficulty) {
        timerJob?.cancel()
        _state.update {
            it.copy(
                generating  = true,
                difficulty  = d,
                elapsedSecs = 0L,
                errorCount  = 0,
                isComplete  = false,
                selected    = null,
            )
        }
        viewModelScope.launch(Dispatchers.Default) {
            val (cells, runs, sol) = generateKakuro(d)
            _state.update {
                it.copy(
                    generating = false,
                    size       = cells.size,
                    cells      = cells,
                    runs       = runs,
                    solution   = sol,
                    errors     = emptySet(),
                )
            }
        }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (!_state.value.isComplete) _state.update { it.copy(elapsedSecs = it.elapsedSecs + 1) }
            }
        }
    }

    fun select(r: Int, c: Int) {
        val cell = _state.value.cells[r][c]
        if (cell !is KakuroCell.White) return
        _state.update { it.copy(selected = if (it.selected == r to c) null else r to c) }
    }

    fun place(n: Int) {
        val s = _state.value; val (r, c) = s.selected ?: return
        val current = s.cells[r][c] as? KakuroCell.White ?: return
        val newCells = s.cells.map { it.clone() }.toTypedArray()
        newCells[r][c] = current.copy(value = n, notes = emptySet())
        val errs   = computeKakuroErrors(newCells, s.runs)
        val wasErr = (r to c) in s.errors
        val newCnt = if (n != 0 && (r to c) in errs && !wasErr) s.errorCount + 1 else s.errorCount
        val done   = errs.isEmpty() && newCells.all { row ->
            row.all { cell -> cell !is KakuroCell.White || cell.value != 0 }
        }
        _state.update { it.copy(cells = newCells, errors = errs, errorCount = newCnt, isComplete = done) }
    }

    fun erase() {
        val s = _state.value; val (r, c) = s.selected ?: return
        val current = s.cells[r][c] as? KakuroCell.White ?: return
        val newCells = s.cells.map { it.clone() }.toTypedArray()
        newCells[r][c] = current.copy(value = 0, notes = emptySet())
        _state.update { it.copy(cells = newCells, errors = computeKakuroErrors(newCells, s.runs)) }
    }

    fun solve() {
        val s = _state.value
        val newCells = s.cells.map { it.clone() }.toTypedArray()
        for (r in newCells.indices) for (c in newCells[r].indices) {
            if (newCells[r][c] is KakuroCell.White && s.solution[r][c] > 0) {
                newCells[r][c] = KakuroCell.White(s.solution[r][c])
            }
        }
        _state.update { it.copy(cells = newCells, isComplete = true, errors = emptySet()) }
    }

    override fun onCleared() { timerJob?.cancel() }
}
