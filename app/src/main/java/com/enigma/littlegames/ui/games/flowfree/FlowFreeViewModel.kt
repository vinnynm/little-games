package com.enigma.littlegames.ui.games.flowfree

// ─────────────────────────────────────────────────────────────────────────────
// Flow Free — ViewModel
// Player draws paths connecting colour dot pairs.
// All cells must be filled and all pairs connected to win.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

data class FlowCell(
    val colorId: Int? = null,   // null = empty, Int = path colour
    val isDot: Boolean = false, // endpoint dot
)

data class FlowPuzzle(
    val size: Int,
    val dots: Map<Pair<Int,Int>, Int>,          // position → colorId
    val solution: Map<Pair<Int,Int>, Int>,       // full solved grid
)

enum class FlowDifficulty(val label: String, val emoji: String, val size: Int, val pairs: Int) {
    EASY  ("Easy",   "🌿", 5,  3),
    MEDIUM("Medium", "⚡", 7,  5),
    HARD  ("Hard",   "🔥", 9,  7),
    EXPERT("Expert", "💀", 10, 9),
}

data class FlowState(
    val puzzle: FlowPuzzle?               = null,
    val grid: Array<Array<FlowCell>>      = emptyArray(),
    val paths: Map<Int, List<Pair<Int,Int>>> = emptyMap(),  // colorId → ordered path cells
    val activeColor: Int?                 = null,
    val isComplete: Boolean               = false,
    val difficulty: FlowDifficulty        = FlowDifficulty.MEDIUM,
    val moves: Int                        = 0,
    val generating: Boolean               = true,
)

// ─────────────────────────────────────────────────────────────────────────────
// Generator
// Strategy: lay solution paths first, then expose only endpoints as dots.
// ─────────────────────────────────────────────────────────────────────────────

private val COLOUR_COUNT = 10   // max distinct colours supported

fun generateFlowPuzzle(d: FlowDifficulty): FlowPuzzle {
    val n = d.size
    val pairs = d.pairs

    repeat(100) {
        val grid = Array(n) { IntArray(n) { -1 } }   // -1 = unoccupied
        val paths = mutableListOf<List<Pair<Int,Int>>>()
        var ok = true

        for (colourId in 0 until pairs) {
            val path = randomPath(grid, n, colourId)
            if (path.size < 2) { ok = false; break }
            paths.add(path)
            path.forEach { (r, c) -> grid[r][c] = colourId }
        }
        if (!ok) return@repeat

        // Fill remaining cells with nearest colour (simple flood)
        var changed = true
        while (changed) {
            changed = false
            for (r in 0 until n) for (c in 0 until n) {
                if (grid[r][c] != -1) continue
                val nbColour = listOf(r-1 to c, r+1 to c, r to c-1, r to c+1)
                    .filter { (nr, nc) -> nr in 0 until n && nc in 0 until n && grid[nr][nc] != -1 }
                    .map { (nr, nc) -> grid[nr][nc] }
                    .groupBy { it }.maxByOrNull { it.value.size }?.key
                if (nbColour != null) { grid[r][c] = nbColour; changed = true }
            }
        }
        if ((0 until n).any { r -> (0 until n).any { c -> grid[r][c] == -1 } }) return@repeat

        val dots = mutableMapOf<Pair<Int,Int>, Int>()
        paths.forEachIndexed { colourId, path ->
            dots[path.first()] = colourId
            dots[path.last()]  = colourId
        }

        val solution = mutableMapOf<Pair<Int,Int>, Int>()
        for (r in 0 until n) for (c in 0 until n) solution[r to c] = grid[r][c]

        return FlowPuzzle(n, dots, solution)
    }

    // Fallback: trivial 5×5 with 3 straight paths
    return fallbackPuzzle()
}

private fun randomPath(grid: Array<IntArray>, n: Int, colourId: Int): List<Pair<Int,Int>> {
    val free = (0 until n).flatMap { r -> (0 until n).map { c -> r to c } }
        .filter { (r, c) -> grid[r][c] == -1 }.shuffled()
    if (free.size < 2) return emptyList()

    val start = free.first()
    val path  = mutableListOf(start)
    val visited = mutableSetOf(start)

    repeat(n * n) {
        val cur = path.last()
        val nbrs = listOf(cur.first-1 to cur.second, cur.first+1 to cur.second,
            cur.first to cur.second-1, cur.first to cur.second+1)
            .filter { (r, c) -> r in 0 until n && c in 0 until n
                    && grid[r][c] == -1 && (r to c) !in visited }
            .shuffled()
        if (nbrs.isEmpty()) return path
        val next = nbrs.first()
        path.add(next)
        visited.add(next)
    }
    return path
}

private fun fallbackPuzzle(): FlowPuzzle {
    val dots = mapOf(
        0 to 0 to 0, 0 to 4 to 0,
        1 to 0 to 1, 1 to 4 to 1,
        2 to 0 to 2, 2 to 4 to 2,
    )
    val sol = mutableMapOf<Pair<Int,Int>, Int>()
    for (c in 0..4) { sol[0 to c] = 0; sol[1 to c] = 1; sol[2 to c] = 2 }
    for (r in 3..4) for (c in 0..4) sol[r to c] = 2
    return FlowPuzzle(5, dots, sol)
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class FlowFreeViewModel : ViewModel() {
    private val _state = MutableStateFlow(FlowState())
    val state: StateFlow<FlowState> = _state.asStateFlow()

    // Drag tracking
    private var dragColor: Int? = null
    private var dragPath: MutableList<Pair<Int,Int>> = mutableListOf()

    init { newGame(FlowDifficulty.MEDIUM) }

    fun newGame(d: FlowDifficulty) {
        _state.update { it.copy(generating = true, difficulty = d, isComplete = false, moves = 0) }
        viewModelScope.launch(Dispatchers.Default) {
            val puzzle = generateFlowPuzzle(d)
            val grid   = buildInitialGrid(puzzle)
            _state.update { it.copy(
                puzzle     = puzzle,
                grid       = grid,
                paths      = emptyMap(),
                generating = false,
                isComplete = false,
            )}
        }
    }

    // Called when finger touches a cell
    fun onDragStart(row: Int, col: Int) {
        val s = _state.value; val puzzle = s.puzzle ?: return
        val cell = s.grid.getOrNull(row)?.getOrNull(col) ?: return
        // Only start drag on a dot or existing path cell
        val colourId = puzzle.dots[row to col] ?: cell.colorId ?: return

        dragColor = colourId
        dragPath  = mutableListOf(row to col)

        // Clear any existing path for this colour
        val newPaths = s.paths.toMutableMap()
        newPaths.remove(colourId)
        val newGrid  = clearColourFromGrid(s.grid, puzzle, colourId)
        newGrid[row][col] = newGrid[row][col].copy(colorId = colourId)
        _state.update { it.copy(grid = newGrid, paths = newPaths) }
    }

    // Called as finger moves through cells
    fun onDragMove(row: Int, col: Int) {
        val colourId = dragColor ?: return
        val s = _state.value; val puzzle = s.puzzle ?: return
        val pos = row to col
        if (pos == dragPath.lastOrNull()) return  // same cell, no-op

        // If we revisit a cell already in our own path — trim back to that point
        val backIdx = dragPath.indexOf(pos)
        if (backIdx >= 0) {
            val trimmed = dragPath.subList(0, backIdx + 1)
            dragPath = trimmed.toMutableList()
            applyDragPath(puzzle, s)
            return
        }

        // Must be adjacent to last cell
        val last = dragPath.last()
        if (abs(row - last.first) + abs(col - last.second) != 1) return

        // Can't place on a dot of a different colour
        val dotColour = puzzle.dots[pos]
        if (dotColour != null && dotColour != colourId) return

        // If cell has another colour's path — clear that path back to avoid crossing
        val existing = s.grid.getOrNull(row)?.getOrNull(col)?.colorId
        if (existing != null && existing != colourId) {
            val clearGrid = clearColourFromGrid(s.grid, puzzle, existing)
            val clearPaths = s.paths.toMutableMap().also { it.remove(existing) }
            _state.update { it.copy(grid = clearGrid, paths = clearPaths) }
        }

        dragPath.add(pos)
        applyDragPath(puzzle, _state.value)
    }

    fun onDragEnd() {
        val colourId = dragColor ?: return
        val puzzle   = _state.value.puzzle ?: return
        val s        = _state.value

        // Commit path if it connects two dots
        val start = dragPath.firstOrNull() ?: return
        val end   = dragPath.lastOrNull()  ?: return
        val startDot = puzzle.dots[start]
        val endDot   = puzzle.dots[end]

        val committed = startDot == colourId && endDot == colourId && start != end

        val newPaths = s.paths.toMutableMap()
        if (committed) {
            newPaths[colourId] = dragPath.toList()
        } else {
            newPaths.remove(colourId)
        }

        val newGrid = rebuildGrid(puzzle, newPaths)
        val complete = checkComplete(puzzle, newGrid)

        _state.update { it.copy(
            grid       = newGrid,
            paths      = newPaths,
            isComplete = complete,
            moves      = if (committed) it.moves + 1 else it.moves,
            activeColor = null,
        )}
        dragColor = null
        dragPath  = mutableListOf()
    }

    private fun applyDragPath(puzzle: FlowPuzzle, s: FlowState) {
        val colourId = dragColor ?: return
        val newPaths = s.paths.toMutableMap()
        newPaths[colourId] = dragPath.toList()
        val newGrid = rebuildGrid(puzzle, newPaths)
        _state.update { it.copy(grid = newGrid, paths = newPaths) }
    }

    private fun clearColourFromGrid(
        grid: Array<Array<FlowCell>>,
        puzzle: FlowPuzzle,
        colourId: Int,
    ): Array<Array<FlowCell>> {
        val newGrid = grid.map { it.clone() }.toTypedArray()
        for (r in newGrid.indices) for (c in newGrid[r].indices) {
            if (newGrid[r][c].colorId == colourId && !newGrid[r][c].isDot) {
                newGrid[r][c] = FlowCell()
            }
        }
        return newGrid
    }

    private fun rebuildGrid(
        puzzle: FlowPuzzle,
        paths: Map<Int, List<Pair<Int,Int>>>,
    ): Array<Array<FlowCell>> {
        val grid = buildInitialGrid(puzzle)
        paths.forEach { (colourId, path) ->
            path.forEach { (r, c) ->
                grid[r][c] = grid[r][c].copy(colorId = colourId)
            }
        }
        return grid
    }

    private fun checkComplete(puzzle: FlowPuzzle, grid: Array<Array<FlowCell>>): Boolean {
        // All cells filled
        if (grid.any { row -> row.any { it.colorId == null } }) return false
        // All dot pairs connected
        val connectedColours = mutableSetOf<Int>()
        puzzle.dots.forEach { (pos, colourId) ->
            if (grid[pos.first][pos.second].colorId == colourId) connectedColours.add(colourId)
        }
        val allColours = puzzle.dots.values.toSet()
        return connectedColours.size == allColours.size * 1  // each colour appears as both endpoints
            && allColours.all { c ->
                puzzle.dots.entries.filter { it.value == c }.map { it.key }
                    .all { pos -> grid[pos.first][pos.second].colorId == c }
            }
    }
}

private fun buildInitialGrid(puzzle: FlowPuzzle): Array<Array<FlowCell>> {
    val grid = Array(puzzle.size) { Array(puzzle.size) { FlowCell() } }
    puzzle.dots.forEach { (pos, colourId) ->
        grid[pos.first][pos.second] = FlowCell(colorId = colourId, isDot = true)
    }
    return grid
}

private fun abs(x: Int) = if (x < 0) -x else x
