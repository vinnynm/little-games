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
    val colorId: Int? = null,
    val isDot: Boolean = false,
)

data class FlowPuzzle(
    val size: Int,
    val dots: Map<Pair<Int,Int>, Int>,
    val solution: Map<Pair<Int,Int>, Int>,
)

enum class FlowDifficulty(val label: String, val emoji: String, val size: Int, val pairs: Int) {
    EASY  ("Easy",   "🌿", 5,  3),
    MEDIUM("Medium", "⚡", 7,  5),
    HARD  ("Hard",   "🔥", 9,  7),
    EXPERT("Expert", "💀", 10, 9),
}

data class FlowState(
    val puzzle: FlowPuzzle?                      = null,
    val grid: Array<Array<FlowCell>>             = emptyArray(),
    val paths: Map<Int, List<Pair<Int,Int>>>     = emptyMap(),
    val activeColor: Int?                        = null,
    val isComplete: Boolean                      = false,
    val difficulty: FlowDifficulty               = FlowDifficulty.MEDIUM,
    val moves: Int                               = 0,
    val generating: Boolean                      = true,
)

// ─────────────────────────────────────────────────────────────────────────────
// Generator — produces guaranteed-solvable puzzles
//
// Strategy:
//   1. Place dot pairs with minimum Manhattan distance
//   2. For each colour, find a path via DFS with backtracking
//   3. After each path, verify remaining dots stay reachable
//   4. Fill remaining empty cells by extending paths (branch-free)
//   5. Validate: every colour's cells form a simple path connecting its dots
//   6. For small grids, run a backtracking solver to confirm solvability
// ─────────────────────────────────────────────────────────────────────────────

fun generateFlowPuzzle(d: FlowDifficulty): FlowPuzzle {
    val n = d.size
    val pairs = d.pairs

    repeat(500) {
        val dots = placeDots(n, pairs) ?: return@repeat
        val solution = buildSolution(n, dots) ?: return@repeat
        if (!validateSimplePaths(n, dots, solution)) return@repeat
        // Run solver validation on small grids
        if (n <= 7 && !solverCanSolve(n, dots)) return@repeat
        return FlowPuzzle(n, dots, solution)
    }

    return fallbackPuzzle()
}

// ── Dot placement ────────────────────────────────────────────────────────────

private fun placeDots(n: Int, pairs: Int): Map<Pair<Int,Int>, Int>? {
    val cells = (0 until n).flatMap { r -> (0 until n).map { c -> r to c } }.shuffled()
    if (cells.size < pairs * 2) return null

    val dots = mutableMapOf<Pair<Int,Int>, Int>()
    val minDist = maxOf(2, n / 3)

    for (i in 0 until pairs) {
        val first = cells.first { it !in dots.keys }
        dots[first] = i

        val candidates = cells.filter {
            it !in dots.keys && manhattanDist(it, first) >= minDist
        }
        val second = candidates.ifEmpty {
            cells.filter { it !in dots.keys }
        }.firstOrNull() ?: return null
        dots[second] = i
    }
    return dots
}

// ── Solution builder ─────────────────────────────────────────────────────────

private fun buildSolution(
    n: Int,
    dots: Map<Pair<Int,Int>, Int>,
): Map<Pair<Int,Int>, Int>? {
    val grid = Array(n) { IntArray(n) { -1 } }
    val colours = dots.values.toSet().shuffled()

    for (colourId in colours) {
        val positions = dots.entries.filter { it.value == colourId }.map { it.key }
        if (positions.size != 2) return null

        val path = findPathDFS(grid, n, positions[0], positions[1]) ?: return null
        for ((r, c) in path) grid[r][c] = colourId

        if (!checkRemainingReachable(n, grid, dots)) return null
    }

    if (!fillEmptyCells(grid, n, dots)) return null
    if (grid.any { row -> row.any { it == -1 } }) return null

    return (0 until n).flatMap { r ->
        (0 until n).map { c -> (r to c) to grid[r][c] }
    }.toMap()
}

// ── Path finding with DFS + backtracking ─────────────────────────────────────

private fun findPathDFS(
    grid: Array<IntArray>,
    n: Int,
    start: Pair<Int,Int>,
    end: Pair<Int,Int>,
    maxAttempts: Int = 30,
): List<Pair<Int,Int>>? {
    repeat(maxAttempts) {
        val path = mutableListOf<Pair<Int,Int>>()
        val visited = mutableSetOf<Pair<Int,Int>>()

        if (dfs(grid, n, start, end, path, visited)) {
            return path.toList()
        }
    }
    // Fallback: BFS shortest path
    return bfsPath(grid, n, start, end)
}

private fun dfs(
    grid: Array<IntArray>,
    n: Int,
    current: Pair<Int,Int>,
    end: Pair<Int,Int>,
    path: MutableList<Pair<Int,Int>>,
    visited: MutableSet<Pair<Int,Int>>,
): Boolean {
    path.add(current)
    visited.add(current)

    if (current == end) return true

    val neighbors = getNeighbors(current, n)
        .filter { it !in visited && (grid[it.first][it.second] == -1 || it == end) }
        .shuffled()

    for (next in neighbors) {
        if (dfs(grid, n, next, end, path, visited)) return true
    }

    path.removeAt(path.size - 1)
    visited.remove(current)
    return false
}

private fun bfsPath(
    grid: Array<IntArray>,
    n: Int,
    start: Pair<Int,Int>,
    end: Pair<Int,Int>,
): List<Pair<Int,Int>>? {
    val prev = mutableMapOf<Pair<Int,Int>, Pair<Int,Int>?>()
    val queue = ArrayDeque<Pair<Int,Int>>()
    queue.add(start)
    prev[start] = null

    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        if (cur == end) {
            val path = mutableListOf<Pair<Int,Int>>()
            var node: Pair<Int,Int>? = end
            while (node != null) {
                path.add(node)
                node = prev[node]
            }
            return path.reversed()
        }
        for (next in getNeighbors(cur, n).shuffled()) {
            if (next !in prev && (grid[next.first][next.second] == -1 || next == end)) {
                prev[next] = cur
                queue.add(next)
            }
        }
    }
    return null
}

// ── Connectivity check ───────────────────────────────────────────────────────

private fun checkRemainingReachable(
    n: Int,
    grid: Array<IntArray>,
    dots: Map<Pair<Int,Int>, Int>,
): Boolean {
    // Find all unplaced dot pairs (dots on cells that are still -1)
    val unplacedDots = dots.entries
        .filter { (pos, _) -> grid[pos.first][pos.second] == -1 }
        .groupBy { it.value }

    // Each pair's two dots must be in the same connected component of empty cells
    for ((_, entries) in unplacedDots) {
        if (entries.size < 2) continue
        val positions = entries.map { it.key }
        // Check all pairs of same-colour dots are connected via empty cells
        for (i in positions.indices) {
            for (j in i + 1 until positions.size) {
                if (!areConnectedViaEmpty(positions[i], positions[j], grid, n)) {
                    return false
                }
            }
        }
    }
    return true
}

private fun areConnectedViaEmpty(
    a: Pair<Int,Int>,
    b: Pair<Int,Int>,
    grid: Array<IntArray>,
    n: Int,
): Boolean {
    val visited = mutableSetOf<Pair<Int,Int>>()
    val queue = ArrayDeque<Pair<Int,Int>>()
    queue.add(a)
    visited.add(a)

    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        if (cur == b) return true
        for (nb in getNeighbors(cur, n)) {
            if (nb !in visited && grid[nb.first][nb.second] == -1) {
                visited.add(nb)
                queue.add(nb)
            }
        }
    }
    return false
}

// ── Fill remaining empty cells ───────────────────────────────────────────────

private fun fillEmptyCells(
    grid: Array<IntArray>,
    n: Int,
    dots: Map<Pair<Int,Int>, Int>,
): Boolean {
    // Iteratively fill cells that have exactly one valid colour choice,
    // then fill cells that have multiple choices (pick best)
    var changed = true
    var iterations = 0
    val maxIterations = n * n * 3

    while (changed && iterations < maxIterations) {
        changed = false
        iterations++

        for (r in 0 until n) for (c in 0 until n) {
            if (grid[r][c] != -1) continue
            val cell = r to c

            val adjColors = getNeighbors(cell, n)
                .map { (nr, nc) -> grid[nr][nc] }
                .filter { it != -1 }
                .toSet()

            if (adjColors.isEmpty()) return false  // isolated cell!

            // Find colours that won't create a branch
            val validColors = adjColors.filter { colour ->
                canExtendToCell(grid, n, cell, colour)
            }

            if (validColors.isEmpty()) return false

            // Prefer colour with most adjacency (greedy, reduces fragmentation)
            val best = validColors.maxByOrNull { colour ->
                getNeighbors(cell, n).count { (nr, nc) -> grid[nr][nc] == colour }
            }!!

            grid[r][c] = best
            changed = true
        }
    }

    return grid.all { row -> row.all { it != -1 } }
}

private fun canExtendToCell(
    grid: Array<IntArray>,
    n: Int,
    cell: Pair<Int,Int>,
    colour: Int,
): Boolean {
    val (r, c) = cell
    val sameColorAdj = getNeighbors(cell, n).filter { (nr, nc) -> grid[nr][nc] == colour }

    // More than 2 same-colour neighbors = branch
    if (sameColorAdj.size > 2) return false

    // Exactly 2: they must be in a straight line through this cell
    if (sameColorAdj.size == 2) {
        val (r1, c1) = sameColorAdj[0]
        val (r2, c2) = sameColorAdj[1]
        // Valid if both in same row as cell OR both in same column as cell
        if (!((r1 == r && r2 == r) || (c1 == c && c2 == c))) return false
    }

    return true
}

// ── Solution validation ──────────────────────────────────────────────────────

private fun validateSimplePaths(
    n: Int,
    dots: Map<Pair<Int,Int>, Int>,
    solution: Map<Pair<Int,Int>, Int>,
): Boolean {
    val colours = dots.values.toSet()

    for (colour in colours) {
        val cells = solution.entries.filter { it.value == colour }.map { it.key }
        val dotCells = dots.entries.filter { it.value == colour }.map { it.key }

        if (dotCells.size != 2) return false
        if (!cells.containsAll(dotCells)) return false

        // Each cell must have ≤ 2 same-colour neighbors (no branches)
        for (cell in cells) {
            if (getNeighbors(cell, n).count { solution[it] == colour } > 2) {
                return false
            }
        }

        // All cells of this colour must be connected
        val visited = mutableSetOf<Pair<Int,Int>>()
        val queue = ArrayDeque<Pair<Int,Int>>()
        queue.add(dotCells[0])
        visited.add(dotCells[0])

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (nb in getNeighbors(cur, n)) {
                if (nb !in visited && solution[nb] == colour) {
                    visited.add(nb)
                    queue.add(nb)
                }
            }
        }

        if (visited.size != cells.size) return false
        if (dotCells[1] !in visited) return false
    }

    return true
}

// ── Backtracking solver (for validation on small grids) ──────────────────────

private fun solverCanSolve(n: Int, dots: Map<Pair<Int,Int>, Int>): Boolean {
    val grid = Array(n) { IntArray(n) { -1 } }
    val colours = dots.values.toSet().toList()

    // Initialize: each colour's path starts at its first dot
    val pathEnds = mutableMapOf<Int, Pair<Int,Int>>()
    val pathStarts = mutableMapOf<Int, Pair<Int,Int>>()
    val goals = mutableMapOf<Int, Pair<Int,Int>>()

    for (colour in colours) {
        val positions = dots.entries.filter { it.value == colour }.map { it.key }
        if (positions.size != 2) return false
        pathStarts[colour] = positions[0]
        pathEnds[colour] = positions[0]
        goals[colour] = positions[1]
        grid[positions[0].first][positions[0].second] = colour
        grid[positions[1].first][positions[1].second] = colour
    }

    return solveBacktrack(n, grid, pathStarts, pathEnds, goals, 0)
}

private fun solveBacktrack(
    n: Int,
    grid: Array<IntArray>,
    pathStarts: Map<Int, Pair<Int,Int>>,
    pathEnds: MutableMap<Int, Pair<Int,Int>>,
    goals: Map<Int, Pair<Int,Int>>,
    depth: Int,
): Boolean {
    if (depth > n * n * 2) return false  // safety limit

    // Check if solved
    if (grid.all { row -> row.all { it != -1 } }) {
        return pathEnds.all { (colour, end) -> end == goals[colour] }
    }

    // Pick a colour to extend (prefer incomplete ones closer to goal)
    val incomplete = pathEnds.entries
        .filter { it.value != goals[it.key] }
        .shuffled()
        .sortedBy { manhattanDist(it.value, goals[it.key]!!) }

    for ((colour, currentEnd) in incomplete) {
        val neighbors = getNeighbors(currentEnd, n)
            .filter { (r, c) -> grid[r][c] == -1 || (r to c) == goals[colour] }
            .shuffled()

        for (next in neighbors) {
            grid[next.first][next.second] = colour
            pathEnds[colour] = next

            if (isStillFeasible(n, grid, pathEnds, goals)) {
                if (solveBacktrack(n, grid, pathStarts, pathEnds, goals, depth + 1)) {
                    return true
                }
            }

            grid[next.first][next.second] = -1
            pathEnds[colour] = currentEnd
        }
    }

    return false
}

private fun isStillFeasible(
    n: Int,
    grid: Array<IntArray>,
    pathEnds: Map<Int, Pair<Int,Int>>,
    goals: Map<Int, Pair<Int,Int>>,
): Boolean {
    for ((colour, goal) in goals) {
        if (pathEnds[colour] == goal) continue

        val currentEnd = pathEnds[colour]!!

        // Check: goal must be reachable from current end via empty cells (or directly adjacent)
        if (getNeighbors(currentEnd, n).contains(goal)) continue

        val endAdjacentEmpty = getNeighbors(currentEnd, n)
            .filter { (r, c) -> grid[r][c] == -1 }.toSet()
        val goalAdjacentEmpty = getNeighbors(goal, n)
            .filter { (r, c) -> grid[r][c] == -1 }.toSet()

        if (endAdjacentEmpty.isEmpty() && goalAdjacentEmpty.isEmpty()) return false

        // Check if any end-adjacent empty cell can reach any goal-adjacent empty cell
        var reachable = false
        for (startCell in endAdjacentEmpty) {
            for (endCell in goalAdjacentEmpty) {
                if (areConnectedViaEmpty(startCell, endCell, grid, n)) {
                    reachable = true
                    break
                }
            }
            if (reachable) break
        }
        if (!reachable) return false
    }

    // Check no isolated empty cells
    for (r in 0 until n) for (c in 0 until n) {
        if (grid[r][c] != -1) continue
        val cell = r to c
        val emptyNeighbors = getNeighbors(cell, n).count { (nr, nc) -> grid[nr][nc] == -1 }
        if (emptyNeighbors == 0) {
            // Must be adjacent to an incomplete path end
            val adjToEnd = getNeighbors(cell, n).any { (nr, nc) ->
                val color = grid[nr][nc]
                color != -1 && pathEnds[color] == (nr to nc) && goals[color] != (nr to nc)
            }
            if (!adjToEnd) return false
        }
    }

    return true
}

// ── Fallback puzzle (guaranteed valid) ───────────────────────────────────────

private fun fallbackPuzzle(): FlowPuzzle {
    // 5x5 with 3 horizontal paths
    val dots = mapOf(
        (0 to 0) to 0, (0 to 4) to 0,
        (1 to 0) to 1, (1 to 4) to 1,
        (2 to 0) to 2, (2 to 4) to 2,
    )
    val sol = mutableMapOf<Pair<Int,Int>, Int>()
    for (c in 0..4) {
        sol[0 to c] = 0
        sol[1 to c] = 1
        sol[2 to c] = 2
    }
    // Fill bottom rows
    for (r in 3..4) for (c in 0..4) sol[r to c] = 2
    return FlowPuzzle(5, dots, sol)
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun getNeighbors(pos: Pair<Int,Int>, n: Int): List<Pair<Int,Int>> {
    val (r, c) = pos
    return listOf(
        (r - 1 to c), (r + 1 to c), (r to c - 1), (r to c + 1)
    ).filter { (nr, nc) -> nr in 0 until n && nc in 0 until n }
}

private fun manhattanDist(a: Pair<Int,Int>, b: Pair<Int,Int>): Int {
    return kotlin.math.abs(a.first - b.first) + kotlin.math.abs(a.second - b.second)
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class FlowFreeViewModel : ViewModel() {
    private val _state = MutableStateFlow(FlowState())
    val state: StateFlow<FlowState> = _state.asStateFlow()

    private var dragColor: Int? = null
    private var dragPath: MutableList<Pair<Int,Int>> = mutableListOf()

    init { newGame(FlowDifficulty.MEDIUM) }

    fun newGame(d: FlowDifficulty) {
        dragColor = null
        dragPath = mutableListOf()
        _state.update { it.copy(generating = true, difficulty = d, isComplete = false, moves = 0) }
        viewModelScope.launch(Dispatchers.Default) {
            val puzzle = generateFlowPuzzle(d)
            val grid = buildInitialGrid(puzzle)
            _state.update { it.copy(
                puzzle     = puzzle,
                grid       = grid,
                paths      = emptyMap(),
                generating = false,
                isComplete = false,
            )}
        }
    }

    fun onDragStart(row: Int, col: Int) {
        val s = _state.value; val puzzle = s.puzzle ?: return
        if (s.isComplete) return
        val cell = s.grid.getOrNull(row)?.getOrNull(col) ?: return

        val colourId = puzzle.dots[row to col] ?: cell.colorId ?: return

        dragColor = colourId
        dragPath = mutableListOf(row to col)

        // Clear existing path for this colour
        val newPaths = s.paths.toMutableMap()
        newPaths.remove(colourId)
        val newGrid = clearColourFromGrid(s.grid, colourId)
        newGrid[row][col] = newGrid[row][col].copy(colorId = colourId)
        _state.update { it.copy(grid = newGrid, paths = newPaths) }
    }

    fun onDragMove(row: Int, col: Int) {
        val colourId = dragColor ?: return
        val s = _state.value; val puzzle = s.puzzle ?: return
        val pos = row to col
        if (pos == dragPath.lastOrNull()) return

        // Trim back if revisiting our own path
        val backIdx = dragPath.indexOf(pos)
        if (backIdx >= 0) {
            dragPath = dragPath.subList(0, backIdx + 1).toMutableList()
            applyDragPath(puzzle, _state.value)
            return
        }

        // Must be adjacent
        val last = dragPath.last()
        if (kotlin.math.abs(row - last.first) + kotlin.math.abs(col - last.second) != 1) return

        // Can't cross another colour's dot
        val dotColour = puzzle.dots[pos]
        if (dotColour != null && dotColour != colourId) return

        // Clear another colour's path if we cross it
        val existing = s.grid.getOrNull(row)?.getOrNull(col)?.colorId
        if (existing != null && existing != colourId) {
            val clearGrid = clearColourFromGrid(s.grid, existing)
            val clearPaths = s.paths.toMutableMap().also { it.remove(existing) }
            _state.update { it.copy(grid = clearGrid, paths = clearPaths) }
        }

        dragPath.add(pos)
        applyDragPath(puzzle, _state.value)
    }

    fun onDragEnd() {
        val colourId = dragColor ?: return
        val puzzle = _state.value.puzzle ?: return
        val s = _state.value

        val start = dragPath.firstOrNull() ?: return
        val end = dragPath.lastOrNull() ?: return
        val startDot = puzzle.dots[start]
        val endDot = puzzle.dots[end]

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
            grid        = newGrid,
            paths       = newPaths,
            isComplete  = complete,
            moves       = if (committed) it.moves + 1 else it.moves,
            activeColor = null,
        )}
        dragColor = null
        dragPath = mutableListOf()
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
        colourId: Int,
    ): Array<Array<FlowCell>> {
        return grid.map { row ->
            row.map { cell ->
                if (cell.colorId == colourId && !cell.isDot) FlowCell() else cell
            }.toTypedArray()
        }.toTypedArray()
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
        if (grid.any { row -> row.any { it.colorId == null } }) return false
        val allColours = puzzle.dots.values.toSet()
        return allColours.all { colour ->
            val dotPositions = puzzle.dots.entries.filter { it.value == colour }.map { it.key }
            dotPositions.all { pos -> grid[pos.first][pos.second].colorId == colour }
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