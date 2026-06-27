package com.enigma.littlegames.ui.games.pipeflow

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

// ── Port / pipe math ──────────────────────────────────────────────────────────

val BASE_PORTS = mapOf(
    'I' to booleanArrayOf(true,  false, true,  false),
    'L' to booleanArrayOf(true,  true,  false, false),
    'T' to booleanArrayOf(true,  true,  false, true ),
    'X' to booleanArrayOf(true,  true,  true,  true ), // True 4-way junction
    'E' to booleanArrayOf(true,  false, false, false),
)
val OPP = intArrayOf(2, 3, 0, 1)

fun rotatePorts(ports: BooleanArray, steps: Int): BooleanArray {
    val s = ((steps % 4) + 4) % 4
    return BooleanArray(4) { d -> ports[(d - s + 4) % 4] }
}

data class PipeCell(
    val type: Char,          // I L T X E  or  N=inlet  O=outlet  ' '=empty  '#'=rock
    val rot: Int     = 0,
    val fixed: Boolean  = false,
    val locked: Boolean = false,
    val nodeDir: Int = 0,
)

fun activePorts(cell: PipeCell): BooleanArray = when (cell.type) {
    ' ', '#'    -> BooleanArray(4)
    'N', 'O'    -> BooleanArray(4).also { it[cell.nodeDir] = true }
    else        -> BASE_PORTS[cell.type]?.let { rotatePorts(it, cell.rot) } ?: BooleanArray(4)
}

fun dirBetween(from: Int, to: Int, sz: Int): Int {
    val dr = to / sz - from / sz; val dc = to % sz - from % sz
    return when { dr == -1 && dc == 0 -> 0; dr == 0 && dc == 1 -> 1; dr == 1 && dc == 0 -> 2; dr == 0 && dc == -1 -> 3; else -> -1 }
}

fun neighbor(idx: Int, dir: Int, size: Int): Int {
    val r = idx / size; val c = idx % size
    return when (dir) {
        0 -> if (r > 0) idx - size else -1
        1 -> if (c < size - 1) idx + 1 else -1
        2 -> if (r < size - 1) idx + size else -1
        3 -> if (c > 0) idx - 1 else -1
        else -> -1
    }
}

fun findRot(base: BooleanArray, target: BooleanArray): Int {
    for (r in 0..3) if (rotatePorts(base, r).contentEquals(target)) return r
    return -1
}

// ── Puzzle model ──────────────────────────────────────────────────────────────

data class PipePuzzle(
    val size: Int,
    val cells: List<PipeCell>,
    val solution: List<Pair<Int, Int>>,   // idx -> correct rot
    val par: Int,
    val difficulty: Int,
    val title: String,
)

// ── Generator ─────────────────────────────────────────────────────────────────

private val TITLES = listOf(
    "Aqua Nexus","Iron Conduit","Flow Matrix","Copper Maze","Delta Cross",
    "Pressure Line","Rock Garden","The Gauntlet","Junction 7","Steel River",
    "Pipe Dream","Hydra Path","Fault Zone","Labyrinth Run","Grid Current",
    "Crossover","Quad Valve","Four-Way Junction","The X-Factor","Network Hub",
)

/**
 * Finds a path between two points avoiding blocked cells and rocks.
 * Used to create loops for 4-way junctions.
 */
private fun findLoop(start: Int, end: Int, blocked: Set<Int>, rocks: Set<Int>, size: Int): List<Int>? {
    val visited = mutableSetOf<Int>()
    val path = mutableListOf<Int>()
    fun dfs(curr: Int): Boolean {
        if (curr == end) { path.add(curr); return true }
        visited.add(curr); path.add(curr)
        val r = curr / size; val c = curr % size
        val nbrs = buildList {
            if (r > 0) add(curr - size)
            if (r < size - 1) add(curr + size)
            if (c > 0) add(curr - 1)
            if (c < size - 1) add(curr + 1)
        }.shuffled()
        for (n in nbrs) {
            if (n !in visited && n !in blocked && n !in rocks) {
                if (dfs(n)) return true
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
            path.removeLast() else path.removeAt(path.lastIndex)
        return false
    }
    return if (dfs(start)) path else null
}

fun generatePipePuzzle(
    size: Int,
    difficulty: Int,
    rockCount: Int  = 0,
    lockCount: Int  = 0,
    xPipeChance: Int = 0,
): PipePuzzle {
    repeat(40) {
        val N     = size * size
        val rocks = mutableSetOf<Int>()
        repeat(minOf(rockCount, (N * 0.15).toInt())) { rocks.add(Random.nextInt(N)) }

        val cells = Array(N) { i -> if (i in rocks) PipeCell('#', fixed = true) else PipeCell(' ', fixed = true) }
        val inIdx = (0 until N).first { it !in rocks }
        var outIdx: Int
        do { outIdx = Random.nextInt(N) } while (outIdx == inIdx || outIdx in rocks)

        val visited = BooleanArray(N); val path = mutableListOf<Int>()
        fun dfs(idx: Int): Boolean {
            if (idx == outIdx) { path.add(idx); return true }
            visited[idx] = true; path.add(idx)
            val r = idx / size; val c = idx % size
            val nbrs = buildList {
                if (r > 0)        add(idx - size)
                if (r < size - 1) add(idx + size)
                if (c > 0)        add(idx - 1)
                if (c < size - 1) add(idx + 1)
            }.shuffled()
            for (n in nbrs) if (!visited[n] && n !in rocks && dfs(n)) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                path.removeLast() else path.removeAt(path.lastIndex)
            ; return false
        }
        if (!dfs(inIdx) || path.size < 3) return@repeat

        val inDir  = dirBetween(inIdx, path[1], size)
        val outDir = dirBetween(outIdx, path[path.size - 2], size)
        if (inDir == -1 || outDir == -1) return@repeat

        cells[inIdx]  = PipeCell('N', nodeDir = inDir,  fixed = true)
        cells[outIdx] = PipeCell('O', nodeDir = outDir, fixed = true)

        val usedCells = path.toMutableSet()
        val solution = mutableListOf<Pair<Int, Int>>()
        var valid    = true

        for (pi in 1 until path.size - 1) {
            val idx  = path[pi]
            val prev = path[pi - 1]
            val next = path[pi + 1]
            val dirPrev = dirBetween(idx, prev, size)
            val dirNext = dirBetween(idx, next, size)
            if (dirPrev == -1 || dirNext == -1 || dirPrev == dirNext) { valid = false; break }

            val isStraight = dirNext == OPP[dirPrev]
            val tryX = isStraight && xPipeChance > 0 && Random.nextInt(100) < xPipeChance
            var madeX = false

            if (tryX) {
                val side1 = (dirPrev + 1) % 4
                val side2 = (dirPrev + 3) % 4
                val cell1 = neighbor(idx, side1, size)
                val cell2 = neighbor(idx, side2, size)

                if (cell1 != -1 && cell2 != -1 && cell1 !in usedCells && cell2 !in usedCells && cell1 !in rocks && cell2 !in rocks) {
                    val loopPath = findLoop(cell1, cell2, usedCells + idx, rocks, size)
                    if (loopPath != null && loopPath.size >= 2) {
                        // Create true 4-way junction
                        cells[idx] = PipeCell('X', rot = 0, fixed = true, locked = true)
                        solution.add(idx to 0)
                        madeX = true
                        usedCells.addAll(loopPath)

                        // Process loop pipes
                        val loopWithEnds = listOf(idx) + loopPath + listOf(idx)
                        for (li in 1 until loopWithEnds.size - 1) {
                            val lIdx = loopWithEnds[li]
                            val lPrev = loopWithEnds[li - 1]
                            val lNext = loopWithEnds[li + 1]
                            val lDirPrev = dirBetween(lIdx, lPrev, size)
                            val lDirNext = dirBetween(lIdx, lNext, size)
                            if (lDirPrev == -1 || lDirNext == -1 || lDirPrev == lDirNext) { valid = false; break }

                            val lNeeded = BooleanArray(4).also { it[lDirPrev] = true; it[lDirNext] = true }
                            val lType = if (lDirNext == OPP[lDirPrev]) 'I' else 'L'
                            val lRot = findRot(BASE_PORTS[lType]!!, lNeeded)
                            if (lRot == -1) { valid = false; break }

                            cells[lIdx] = PipeCell(lType, lRot, fixed = false)
                            solution.add(lIdx to lRot)
                        }
                        if (!valid) break
                    }
                }
            }

            if (!madeX) {
                val needed = BooleanArray(4).also { it[dirPrev] = true; it[dirNext] = true }
                val type = if (isStraight) 'I' else 'L'
                val rot = findRot(BASE_PORTS[type]!!, needed)
                if (rot == -1) { valid = false; break }
                cells[idx] = PipeCell(type, rot, fixed = false)
                solution.add(idx to rot)
            }
        }
        if (!valid) return@repeat

        // Fill empty cells with distractors (no X fillers to avoid accidental unsolvable leaks)
        val fillers = charArrayOf('I', 'L', 'T', 'E')
        for (i in 0 until N) if (cells[i].type == ' ')
            cells[i] = PipeCell(fillers.random(), Random.nextInt(4), false)

        // Lock some solution pipes
        val lockSet = solution.map { it.first }.shuffled()
            .take(minOf(lockCount, solution.size)).toSet()

        solution.forEach { (idx, solRot) ->
            val c = cells[idx]
            if (c.type == 'X') return@forEach // X is already locked

            if (idx in lockSet) {
                cells[idx] = c.copy(locked = true, fixed = true)
            } else {
                var r: Int
                do { r = Random.nextInt(4) } while (r == solRot)
                cells[idx] = c.copy(rot = r)
            }
        }

        return PipePuzzle(size, cells.toList(), solution,
            maxOf(1, (solution.size * 1.2).toInt()), difficulty, TITLES.random())
    }

    // Fallback — minimal solvable puzzle
    val N = size * size
    val c = Array(N) { PipeCell(' ', fixed = true) }
    c[0] = PipeCell('N', nodeDir = 1, fixed = true)
    c[size - 1] = PipeCell('O', nodeDir = 3, fixed = true)
    val sol = (1 until size - 1).map { i -> c[i] = PipeCell('I', 1); i to 1 }
    return PipePuzzle(size, c.toList(), sol, size - 2, 1, "Simple Path")
}

// ── Validator ─────────────────────────────────────────────────────────────────

data class FlowResult(val solved: Boolean, val leaked: Boolean, val visited: Set<Int>)

fun validateFlow(cells: List<PipeCell>, size: Int): FlowResult {
    val inIdx  = cells.indexOfFirst { it.type == 'N' }
    val outIdx = cells.indexOfFirst { it.type == 'O' }
    if (inIdx < 0 || outIdx < 0) return FlowResult(false, false, emptySet())
    val visited = mutableSetOf<Int>(); val queue = ArrayDeque<Int>()
    queue.add(inIdx); var leaked = false; var reached = false
    val dirs = listOf(Triple(-1, 0, 0), Triple(0, 1, 1), Triple(1, 0, 2), Triple(0, -1, 3))
    while (queue.isNotEmpty()) {
        val idx = queue.removeFirst(); if (idx in visited) continue
        visited.add(idx); if (idx == outIdx) reached = true
        val ports = activePorts(cells[idx]); val r = idx / size; val c = idx % size
        dirs.forEach { (dr, dc, myPort) ->
            if (!ports[myPort]) return@forEach
            val nr = r + dr; val nc = c + dc
            if (nr < 0 || nr >= size || nc < 0 || nc >= size) { leaked = true; return@forEach }
            val ni = nr * size + nc
            if (cells[ni].type == ' ' || cells[ni].type == '#') { leaked = true; return@forEach }
            if (activePorts(cells[ni])[OPP[myPort]]) queue.add(ni) else leaked = true
        }
    }
    return FlowResult(reached && !leaked, leaked, visited)
}

// ── Campaign spec ─────────────────────────────────────────────────────────────

val PIPE_CAMPAIGN = listOf(
    intArrayOf(4, 1, 0, 0,  0),
    intArrayOf(4, 2, 0, 1,  0),
    intArrayOf(4, 2, 1, 1,  0),
    intArrayOf(4, 3, 1, 2,  0),
    intArrayOf(5, 2, 0, 0,  0),
    intArrayOf(5, 2, 1, 1, 10),
    intArrayOf(5, 3, 2, 2, 15),
    intArrayOf(5, 3, 2, 3, 20),
    intArrayOf(6, 3, 0, 1, 10),
    intArrayOf(6, 3, 2, 2, 20),
    intArrayOf(6, 4, 3, 2, 25),
    intArrayOf(6, 4, 3, 3, 30),
    intArrayOf(7, 3, 2, 2, 20),
    intArrayOf(7, 4, 3, 3, 25),
    intArrayOf(7, 4, 4, 3, 30),
    intArrayOf(7, 5, 4, 4, 35),
    intArrayOf(8, 4, 3, 3, 25),
    intArrayOf(8, 4, 4, 4, 30),
    intArrayOf(8, 5, 5, 4, 35),
    intArrayOf(9, 4, 4, 4, 30),
    intArrayOf(9, 5, 5, 5, 40),
    intArrayOf(10, 5, 5, 5, 35),
    intArrayOf(10, 5, 6, 5, 40),
    intArrayOf(10, 5, 6, 6, 40),
)

// ── UI State ──────────────────────────────────────────────────────────────────

data class PipeFlowUiState(
    val puzzle: PipePuzzle?     = null,
    val cells: List<PipeCell>   = emptyList(),
    val moves: Int              = 0,
    val level: Int              = 1,
    val totalStars: Int         = 0,
    val levelStars: Int         = 0, // Tracks stars for current level to prevent farming
    val flowResult: FlowResult? = null,
    val solved: Boolean         = false,
    val generating: Boolean     = true,
    val lastStars: Int          = 0,
    val autoSolveUsed: Boolean  = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class PipeFlowViewModel : ViewModel() {
    private val _state = MutableStateFlow(PipeFlowUiState())
    val state: StateFlow<PipeFlowUiState> = _state.asStateFlow()

    init { loadLevel(1) }

    fun loadLevel(lvl: Int) {
        _state.update {
            it.copy(
                generating = true, level = lvl, solved = false,
                flowResult = null, moves = 0, autoSolveUsed = false, levelStars = 0
            )
        }
        viewModelScope.launch(Dispatchers.Default) {
            val spec   = PIPE_CAMPAIGN[minOf(lvl - 1, PIPE_CAMPAIGN.size - 1)]
            val puzzle = generatePipePuzzle(spec[0], spec[1], spec[2], spec[3], if (spec.size > 4) spec[4] else 0)
            _state.update { it.copy(puzzle = puzzle, cells = puzzle.cells, generating = false) }
        }
    }

    fun rotateCell(idx: Int) {
        val s = _state.value; if (s.solved) return
        val cell = s.cells[idx]; if (cell.fixed || cell.locked) return
        val newCells = s.cells.toMutableList()
        newCells[idx] = cell.copy(rot = (cell.rot + 1) % 4)
        _state.update { it.copy(cells = newCells, moves = it.moves + 1, flowResult = null) }
    }

    fun checkFlow() {
        val s = _state.value; val puzzle = s.puzzle ?: return
        val result = validateFlow(s.cells, puzzle.size)
        if (result.solved) {
            val stars = if (s.autoSolveUsed) 0 else when {
                s.moves <= puzzle.par                  -> 3
                s.moves <= (puzzle.par * 1.6).toInt() -> 2
                else                                   -> 1
            }
            if (stars > s.levelStars) {
                val diff = stars - s.levelStars
                _state.update { it.copy(flowResult = result, solved = true, lastStars = stars, levelStars = stars, totalStars = it.totalStars + diff) }
            } else {
                _state.update { it.copy(flowResult = result, solved = true, lastStars = stars) }
            }
        } else {
            _state.update { it.copy(flowResult = result) }
        }
    }

    fun autoSolve() {
        val s = _state.value; val puzzle = s.puzzle ?: return
        val newCells = s.cells.toMutableList()
        puzzle.solution.forEach { (idx, rot) ->
            val c = newCells[idx]; if (!c.fixed && !c.locked) newCells[idx] = c.copy(rot = rot)
        }
        _state.update { it.copy(cells = newCells, autoSolveUsed = true) }
        checkFlow()
    }

    fun nextLevel() { loadLevel(minOf(_state.value.level + 1, PIPE_CAMPAIGN.size)) }
    fun reset()     { loadLevel(_state.value.level) }
}