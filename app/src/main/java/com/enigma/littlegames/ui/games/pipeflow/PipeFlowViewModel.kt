package com.enigma.littlegames.ui.games.pipeflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

// ── Port / pipe math ──────────────────────────────────────────────────────────

val BASE_PORTS = mapOf(
    'I' to booleanArrayOf(true,  false, true,  false),
    'L' to booleanArrayOf(true,  true,  false, false),
    'T' to booleanArrayOf(true,  true,  false, true),
    'X' to booleanArrayOf(true,  true,  true,  true),
    'E' to booleanArrayOf(true,  false, false, false),
)
val OPP = intArrayOf(2, 3, 0, 1)

fun rotatePorts(ports: BooleanArray, steps: Int): BooleanArray {
    val s = ((steps % 4) + 4) % 4
    return BooleanArray(4) { d -> ports[(d - s + 4) % 4] }
}

data class PipeCell(
    val type: Char,
    val rot: Int      = 0,
    val fixed: Boolean  = false,
    val locked: Boolean = false,
    val nodeDir: Int    = 0,
    val group: Int      = 0,
    val nodeLabel: Char = ' ',
)

fun activePorts(cell: PipeCell): BooleanArray = when (cell.type) {
    ' ', '#'    -> BooleanArray(4)
    'N', 'O'    -> BooleanArray(4).also { it[cell.nodeDir] = true }
    else        -> BASE_PORTS[cell.type]?.let { rotatePorts(it, cell.rot) } ?: BooleanArray(4)
}

fun dirBetween(from: Int, to: Int, sz: Int): Int {
    val dr = to / sz - from / sz
    val dc = to % sz - from % sz
    return when {
        dr == -1 && dc == 0 -> 0
        dr == 0  && dc == 1 -> 1
        dr == 1  && dc == 0 -> 2
        dr == 0  && dc == -1 -> 3
        else -> -1
    }
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
    val solution: List<Pair<Int, Int>>,
    val par: Int,
    val difficulty: Int,
    val title: String,
    val bossMode: Boolean = false,
)

// ── Generator ─────────────────────────────────────────────────────────────────

private val TITLES = listOf(
    "Aqua Nexus","Iron Conduit","Flow Matrix","Copper Maze","Delta Cross",
    "Pressure Line","Rock Garden","The Gauntlet","Junction 7","Steel River",
    "TWIN GAUNTLET","Hydra Path","Fault Zone","Labyrinth Run","Grid Current",
    "Crossover","Quad Valve","Four-Way Junction","The X-Factor","DUAL NETWORK",
)
private val BOSS_TITLES = listOf("TWIN GAUNTLET", "DUAL NETWORK")

/**
 * Generate a standard (single inlet → outlet) puzzle.
 * X pipes are placed on straight sections with a valid loop
 * connecting their two side arms so ALL 4 ports are used.
 */
fun generatePipePuzzle(
    size: Int,
    difficulty: Int,
    rockCount: Int  = 0,
    lockCount: Int  = 0,
    xPipeChance: Int = 0,
    bossMode: Boolean = false,
): PipePuzzle {
    if (bossMode) return generateBossPuzzle(size, difficulty, lockCount)

    repeat(80) {
        val N     = size * size
        val rocks = mutableSetOf<Int>()
        repeat(minOf(rockCount, (N * 0.15).toInt())) { rocks.add(Random.nextInt(N)) }

        val cells = Array(N) { i -> if (i in rocks) PipeCell('#', fixed = true) else PipeCell(' ', fixed = true) }
        val inIdx = (0 until N).first { it !in rocks }
        var outIdx: Int
        do { outIdx = Random.nextInt(N) } while (outIdx == inIdx || outIdx in rocks)

        // Find main path via DFS
        val visited = BooleanArray(N)
        val path = mutableListOf<Int>()
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
            for (n in nbrs) {
                if (!visited[n] && n !in rocks && dfs(n)) return true
            }
            path.removeAt(path.lastIndex)
            return false
        }
        if (!dfs(inIdx) || path.size < 3) return@repeat

        val inDir  = dirBetween(inIdx, path[1], size)
        val outDir = dirBetween(outIdx, path[path.size - 2], size)
        if (inDir == -1 || outDir == -1) return@repeat

        cells[inIdx]  = PipeCell('N', nodeDir = inDir,  fixed = true)
        cells[outIdx] = PipeCell('O', nodeDir = outDir, fixed = true)

        val usedCells = mutableSetOf<Int>()  // cells occupied by main path + loops
        val solution  = mutableListOf<Pair<Int, Int>>()
        var valid     = true

        for (pi in 1 until path.size - 1) {
            val idx  = path[pi]
            val prev = path[pi - 1]
            val next = path[pi + 1]
            val dirPrev = dirBetween(idx, prev, size)
            val dirNext = dirBetween(idx, next, size)
            if (dirPrev == -1 || dirNext == -1 || dirPrev == dirNext) { valid = false; break }

            val isStraight = dirNext == OPP[dirPrev]

            // Try X pipe: main flow goes through dirPrev/dirNext,
            // side arms (perpendicular) must form a loop
            var madeX = false
            if (isStraight && xPipeChance > 0 && Random.nextInt(100) < xPipeChance) {
                madeX = tryPlaceXPipe(cells, idx, dirPrev, dirNext, usedCells, rocks, size, solution)
                if (!madeX) { /* fall through to regular pipe */ }
                else { valid = true } // tryPlaceX handles its own validity
            }

            if (!madeX) {
                val needed = BooleanArray(4).also { it[dirPrev] = true; it[dirNext] = true }
                val type = if (isStraight) 'I' else 'L'
                val rot = findRot(BASE_PORTS[type]!!, needed)
                if (rot == -1) { valid = false; break }
                cells[idx] = PipeCell(type, rot, fixed = false)
                solution.add(idx to rot)
                usedCells.add(idx)
            }

            if (!valid) break
        }
        if (!valid) return@repeat

        // Fill empty cells with distractors
        val fillers = charArrayOf('I', 'L', 'T', 'E')
        for (i in 0 until N) {
            if (cells[i].type == ' ')
                cells[i] = PipeCell(fillers.random(), Random.nextInt(4), false)
        }

        // Lock some solution pipes (never lock X pipes — they look same at any rotation)
        val lockable = solution.filter { (idx, _) -> cells[idx].type != 'X' }.map { it.first }
        val lockSet = lockable.shuffled().take(minOf(lockCount, lockable.size)).toSet()

        solution.forEach { (idx, solRot) ->
            val c = cells[idx]
            if (c.type == 'X') return@forEach
            if (idx in lockSet) {
                cells[idx] = c.copy(locked = true, fixed = true)
            } else {
                var r: Int
                do { r = Random.nextInt(4) } while (r == solRot)
                cells[idx] = c.copy(rot = r)
            }
        }

        return PipePuzzle(size, cells.toList(), solution,
            maxOf(1, (solution.size * 1.2).toInt()), difficulty,
            TITLES.random(), bossMode = false)
    }

    return fallbackPuzzle(size)
}

/**
 * Try to place an X pipe at [idx] where main flow goes dirPrev→dirNext.
 * The two perpendicular side arms must connect via a loop.
 * Returns true on success, false if no valid loop exists.
 *
 * Example: main flow is North-South (dirs 0,2)
 *   Side arms are East (dir 1) and West (dir 3)
 *   We find a path from the East neighbor to the West neighbor,
 *   avoiding all already-used cells and rocks.
 *   The loop: X → East → ... → West → X
 */
private fun tryPlaceXPipe(
    cells: Array<PipeCell>,
    idx: Int,
    dirPrev: Int,
    dirNext: Int,
    usedCells: MutableSet<Int>,
    rocks: Set<Int>,
    size: Int,
    solution: MutableList<Pair<Int, Int>>,
): Boolean {
    // Determine the two side directions (perpendicular to main flow)
    val side1 = (dirPrev + 1) % 4   // e.g. if dirPrev=0(N), side1=1(E)
    val side2 = (dirPrev + 3) % 4   // side2=3(W)

    val cell1 = neighbor(idx, side1, size)  // East neighbor
    val cell2 = neighbor(idx, side2, size)  // West neighbor

    // Both side neighbors must exist, be free, and not be rocks
    if (cell1 == -1 || cell2 == -1) return false
    if (cell1 in usedCells || cell2 in usedCells) return false
    if (cell1 in rocks || cell2 in rocks) return false
    // Need at least 2 cells for a minimal loop (cell1 → cell2 must be adjacent OR have a path)
    if (cell1 == cell2) return false  // can't happen with opposite sides, but safety check

    // Find a path from cell1 to cell2 avoiding used cells, rocks, and the X cell itself
    val blocked = usedCells + idx
    val loopPath = findPathAvoiding(cell1, cell2, blocked, rocks, size)
    if (loopPath == null || loopPath.size < 1) return false

    // ── Place the X pipe ──
    cells[idx] = PipeCell('X', rot = 0, fixed = false)
    solution.add(idx to 0)
    usedCells.add(idx)

    // ── Place loop pipes ──
    // The loop connects: X(side1) → cell1 → ... → cell2 → X(side2)
    // cell1 must have port facing X (OPP[side1]) and port facing next in loop
    // cell2 must have port facing prev in loop and port facing X (OPP[side2])

    val dirFromXto1 = OPP[side1]  // direction from cell1 back toward X
    val dirFromXto2 = OPP[side2]  // direction from cell2 back toward X

    return if (loopPath.size == 1) {
        // cell1 and cell2 are adjacent — single cell connects both sides
        // This cell needs ports toward X-side1 and X-side2
        val needed = BooleanArray(4).also { it[dirFromXto1] = true; it[dirFromXto2] = true }
        val type = if (dirFromXto2 == OPP[dirFromXto1]) 'I' else 'L'
        val rot = findRot(BASE_PORTS[type]!!, needed)
        if (rot == -1) {
            rollbackXPlacement(cells, idx, loopPath, usedCells, solution)
            false
        } else {
            cells[loopPath[0]] = PipeCell(type, rot, fixed = false)
            solution.add(loopPath[0] to rot)
            usedCells.add(loopPath[0])
            true
        }
    } else {
        // Multi-cell loop
        // First cell: connects to X and to loopPath[1]
        val dirToNext1 = dirBetween(cell1, loopPath[1], size)
        if (dirToNext1 == -1) {
            rollbackXPlacement(cells, idx, loopPath, usedCells, solution)
            return false
        }
        val needed1 = BooleanArray(4).also { it[dirFromXto1] = true; it[dirToNext1] = true }
        val type1 = if (dirToNext1 == OPP[dirFromXto1]) 'I' else 'L'
        val rot1 = findRot(BASE_PORTS[type1]!!, needed1)
        if (rot1 == -1) {
            rollbackXPlacement(cells, idx, loopPath, usedCells, solution)
            return false
        }
        cells[cell1] = PipeCell(type1, rot1, fixed = false)
        solution.add(cell1 to rot1)
        usedCells.add(cell1)

        // Middle cells
        for (li in 1 until loopPath.size - 1) {
            val lIdx  = loopPath[li]
            val lPrev = loopPath[li - 1]
            val lNext = loopPath[li + 1]
            val lDP = dirBetween(lIdx, lPrev, size)
            val lDN = dirBetween(lIdx, lNext, size)
            if (lDP == -1 || lDN == -1 || lDP == lDN) {
                rollbackXPlacement(cells, idx, loopPath.take(li + 1), usedCells, solution)
                return false
            }
            val lNeeded = BooleanArray(4).also { it[lDP] = true; it[lDN] = true }
            val lType = if (lDN == OPP[lDP]) 'I' else 'L'
            val lRot = findRot(BASE_PORTS[lType]!!, lNeeded)
            if (lRot == -1) {
                rollbackXPlacement(cells, idx, loopPath.take(li + 1), usedCells, solution)
                return false
            }
            cells[lIdx] = PipeCell(lType, lRot, fixed = false)
            solution.add(lIdx to lRot)
            usedCells.add(lIdx)
        }

        // Last cell: connects to prev in loop and to X
        val dirFromPrev = dirBetween(cell2, loopPath[loopPath.size - 2], size)
        if (dirFromPrev == -1) {
            rollbackXPlacement(cells, idx, loopPath, usedCells, solution)
            return false
        }
        val needed2 = BooleanArray(4).also { it[dirFromPrev] = true; it[dirFromXto2] = true }
        val type2 = if (dirFromXto2 == OPP[dirFromPrev]) 'I' else 'L'
        val rot2 = findRot(BASE_PORTS[type2]!!, needed2)
        if (rot2 == -1) {
            rollbackXPlacement(cells, idx, loopPath, usedCells, solution)
            return false
        }
        cells[cell2] = PipeCell(type2, rot2, fixed = false)
        solution.add(cell2 to rot2)
        usedCells.add(cell2)

        true
    }
}

/** Undo an X pipe placement if the loop fails. */
private fun rollbackXPlacement(
    cells: Array<PipeCell>,
    xIdx: Int,
    loopPath: List<Int>,
    usedCells: MutableSet<Int>,
    solution: MutableList<Pair<Int, Int>>,
) {
    cells[xIdx] = PipeCell(' ', fixed = true)
    usedCells.remove(xIdx)
    // Remove last (loopPath.size + 1) entries from solution
    repeat(loopPath.size + 1) {
        if (solution.isNotEmpty()) solution.removeAt(solution.lastIndex)
    }
    loopPath.forEach { usedCells.remove(it) }
}

// ── Boss mode generator ──────────────────────────────────────────────────────

private fun generateBossPuzzle(size: Int, difficulty: Int, lockCount: Int): PipePuzzle {
    val minSize = 6
    val sz = maxOf(size, minSize)

    repeat(100) {
        val N     = sz * sz
        val cells = Array(N) { PipeCell(' ', fixed = true) }
        val allCells = (0 until N).shuffled()
        val inA = allCells[0]; val outA = allCells[1]
        val inB = allCells[2]; val outB = allCells[3]

        val distAB = kotlin.math.abs(inA / sz - inB / sz) + kotlin.math.abs(inA % sz - inB % sz)
        val distAO = kotlin.math.abs(inA / sz - outA / sz) + kotlin.math.abs(inA % sz - outA % sz)
        val distBO = kotlin.math.abs(inB / sz - outB / sz) + kotlin.math.abs(inB % sz - outB % sz)
        if (distAB < sz / 2 || distAO < 2 || distBO < 2) return@repeat

        val pathA = findPathAvoiding(inA, outA, emptySet(), emptySet(), sz) ?: return@repeat
        if (pathA.size < 3) return@repeat
        val usedA = pathA.toSet()
        val pathB = findPathAvoiding(inB, outB, usedA, emptySet(), sz) ?: return@repeat
        if (pathB.size < 3) return@repeat
        if (usedA.intersect(pathB.toSet()).isNotEmpty()) return@repeat

        val inDirA  = dirBetween(inA, pathA[1], sz)
        val outDirA = dirBetween(outA, pathA[pathA.size - 2], sz)
        val inDirB  = dirBetween(inB, pathB[1], sz)
        val outDirB = dirBetween(outB, pathB[pathB.size - 2], sz)
        if (listOf(inDirA, outDirA, inDirB, outDirB).any { it == -1 }) return@repeat

        cells[inA]  = PipeCell('N', nodeDir = inDirA,  fixed = true, group = 1, nodeLabel = 'A')
        cells[outA] = PipeCell('O', nodeDir = outDirA, fixed = true, group = 1, nodeLabel = 'A')
        cells[inB]  = PipeCell('N', nodeDir = inDirB,  fixed = true, group = 2, nodeLabel = 'B')
        cells[outB] = PipeCell('O', nodeDir = outDirB, fixed = true, group = 2, nodeLabel = 'B')

        val solution = mutableListOf<Pair<Int, Int>>()
        val usedCells = mutableSetOf<Int>()
        var valid = true

        for ((path, group) in listOf(pathA to 1, pathB to 2)) {
            for (pi in 1 until path.size - 1) {
                val idx = path[pi]
                val prev = path[pi - 1]; val next = path[pi + 1]
                val dP = dirBetween(idx, prev, sz); val dN = dirBetween(idx, next, sz)
                if (dP == -1 || dN == -1 || dP == dN) { valid = false; break }
                val needed = BooleanArray(4).also { it[dP] = true; it[dN] = true }
                val type = if (dN == OPP[dP]) 'I' else 'L'
                val rot = findRot(BASE_PORTS[type]!!, needed)
                if (rot == -1) { valid = false; break }
                cells[idx] = PipeCell(type, rot, fixed = false, group = group)
                solution.add(idx to rot)
                usedCells.add(idx)
            }
            if (!valid) break
        }
        if (!valid) return@repeat

        val fillers = charArrayOf('I', 'L', 'T', 'E')
        for (i in 0 until N) {
            if (cells[i].type == ' ')
                cells[i] = PipeCell(fillers.random(), Random.nextInt(4), false)
        }

        val lockable = solution.map { it.first }.shuffled()
            .take(minOf(lockCount, solution.size)).toSet()
        solution.forEach { (idx, solRot) ->
            val c = cells[idx]
            if (idx in lockable) cells[idx] = c.copy(locked = true, fixed = true)
            else { var r: Int; do { r = Random.nextInt(4) } while (r == solRot); cells[idx] = c.copy(rot = r) }
        }

        return PipePuzzle(sz, cells.toList(), solution,
            maxOf(1, (solution.size * 1.5).toInt()), difficulty,
            BOSS_TITLES.random(), bossMode = true)
    }

    return fallbackBossPuzzle()
}

private fun findPathAvoiding(
    start: Int, end: Int, blocked: Set<Int>, rocks: Set<Int>, size: Int
): List<Int>? {
    val visited = mutableSetOf<Int>()
    val path = mutableListOf<Int>()
    fun dfs(idx: Int): Boolean {
        if (idx == end) { path.add(idx); return true }
        visited.add(idx); path.add(idx)
        val r = idx / size; val c = idx % size
        val nbrs = buildList {
            if (r > 0)        add(idx - size)
            if (r < size - 1) add(idx + size)
            if (c > 0)        add(idx - 1)
            if (c < size - 1) add(idx + 1)
        }.shuffled()
        for (n in nbrs) {
            if (n !in visited && n !in blocked && n !in rocks && dfs(n)) return true
        }
        path.removeAt(path.lastIndex)
        return false
    }
    return if (dfs(start)) path else null
}

private fun fallbackPuzzle(size: Int): PipePuzzle {
    val N = size * size
    val c = Array(N) { PipeCell(' ', fixed = true) }
    c[0] = PipeCell('N', nodeDir = 1, fixed = true)
    c[size - 1] = PipeCell('O', nodeDir = 3, fixed = true)
    val sol = (1 until size - 1).map { i -> c[i] = PipeCell('I', 1); i to 1 }
    return PipePuzzle(size, c.toList(), sol, size - 2, 1, "Simple Path")
}

private fun fallbackBossPuzzle(): PipePuzzle {
    val sz = 6; val N = sz * sz
    val c = Array(N) { PipeCell(' ', fixed = true) }
    c[0]  = PipeCell('N', nodeDir = 1, fixed = true, group = 1, nodeLabel = 'A')
    c[3]  = PipeCell('O', nodeDir = 3, fixed = true, group = 1, nodeLabel = 'A')
    c[24] = PipeCell('N', nodeDir = 1, fixed = true, group = 2, nodeLabel = 'B')
    c[29] = PipeCell('O', nodeDir = 3, fixed = true, group = 2, nodeLabel = 'B')
    val sol = mutableListOf<Pair<Int, Int>>()
    for (i in 1..2)  { c[i] = PipeCell('I', 1); sol.add(i to 1) }
    for (i in 25..28) { c[i] = PipeCell('I', 1); sol.add(i to 1) }
    val fillers = charArrayOf('I', 'L', 'T', 'E')
    for (i in 0 until N) if (c[i].type == ' ') c[i] = PipeCell(fillers.random(), Random.nextInt(4))
    return PipePuzzle(sz, c.toList(), sol, 10, 3, "TWIN GAUNTLET", bossMode = true)
}

// ── Validator — ALL ports must connect, including X pipes ────────────────────
// No special-casing. An open port facing edge/rock/empty/mismatched = leak.

data class FlowResult(val solved: Boolean, val leaked: Boolean, val visited: Set<Int>)

fun validateFlow(cells: List<PipeCell>, size: Int): FlowResult {
    val inIdx  = cells.indexOfFirst { it.type == 'N' }
    val outIdx = cells.indexOfFirst { it.type == 'O' }
    if (inIdx < 0 || outIdx < 0) return FlowResult(false, false, emptySet())

    val visited = mutableSetOf<Int>()
    val queue = ArrayDeque<Int>()
    queue.add(inIdx)
    var leaked = false
    var reached = false
    val dirs = listOf(Triple(-1, 0, 0), Triple(0, 1, 1), Triple(1, 0, 2), Triple(0, -1, 3))

    while (queue.isNotEmpty()) {
        val idx = queue.removeFirst()
        if (idx in visited) continue
        visited.add(idx)
        if (idx == outIdx) reached = true

        val ports = activePorts(cells[idx])
        val r = idx / size; val c = idx % size

        for ((dr, dc, myPort) in dirs) {
            if (!ports[myPort]) continue
            val nr = r + dr; val nc = c + dc
            if (nr < 0 || nr >= size || nc < 0 || nc >= size) { leaked = true; continue }
            val ni = nr * size + nc
            val neighborCell = cells[ni]
            if (neighborCell.type == ' ' || neighborCell.type == '#') { leaked = true; continue }
            if (activePorts(neighborCell)[OPP[myPort]]) queue.add(ni) else leaked = true
        }
    }
    return FlowResult(reached && !leaked, leaked, visited)
}

/** Boss mode: validate all inlet/outlet pairs connect without leaks. */
fun validateBossFlow(cells: List<PipeCell>, size: Int): FlowResult {
    val inlets  = cells.mapIndexedNotNull { i, c -> if (c.type == 'N') i to c.group else null }
    val outlets = cells.mapIndexedNotNull { i, c -> if (c.type == 'O') i to c.group else null }
    if (inlets.isEmpty() || outlets.isEmpty()) return FlowResult(false, false, emptySet())

    val allVisited = mutableSetOf<Int>()
    var anyLeaked = false
    var allReached = true
    val dirs = listOf(Triple(-1, 0, 0), Triple(0, 1, 1), Triple(1, 0, 2), Triple(0, -1, 3))

    for ((inIdx, group) in inlets) {
        val targetOut = outlets.firstOrNull { it.second == group } ?: continue
        val visited = mutableSetOf<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(inIdx)
        var reached = false
        var leaked = false

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            if (idx in visited || idx in allVisited) continue
            visited.add(idx)
            if (idx == targetOut.first) reached = true

            val ports = activePorts(cells[idx])
            val r = idx / size; val c = idx % size

            for ((dr, dc, myPort) in dirs) {
                if (!ports[myPort]) continue
                val nr = r + dr; val nc = c + dc
                if (nr < 0 || nr >= size || nc < 0 || nc >= size) { leaked = true; continue }
                val ni = nr * size + nc
                val neighborCell = cells[ni]
                if (neighborCell.type == ' ' || neighborCell.type == '#') { leaked = true; continue }
                if (ni !in allVisited && activePorts(neighborCell)[OPP[myPort]]) {
                    queue.add(ni)
                } else {
                    leaked = true
                }
            }
        }

        if (!reached) allReached = false
        if (leaked) anyLeaked = true
        allVisited.addAll(visited)
    }

    return FlowResult(allReached && !anyLeaked, anyLeaked, allVisited)
}

// ── Campaign spec ─────────────────────────────────────────────────────────────

val PIPE_CAMPAIGN = listOf(
    intArrayOf(4, 1, 0, 0,  0),
    intArrayOf(4, 2, 0, 1,  0),
    intArrayOf(4, 2, 1, 1,  0),
    intArrayOf(4, 3, 1, 2,  0),
    intArrayOf(5, 2, 0, 0,  0),
    intArrayOf(5, 2, 1, 1, 15),
    intArrayOf(5, 3, 2, 2, 20),
    intArrayOf(5, 3, 2, 3, 25),
    intArrayOf(6, 3, 0, 1, 15),
    intArrayOf(6, 3, 2, 2, 25),   // Level 10 → BOSS
    intArrayOf(6, 4, 3, 2, 30),
    intArrayOf(6, 4, 3, 3, 35),
    intArrayOf(7, 3, 2, 2, 25),
    intArrayOf(7, 4, 3, 3, 30),
    intArrayOf(7, 4, 4, 3, 35),
    intArrayOf(7, 5, 4, 4, 40),
    intArrayOf(8, 4, 3, 3, 30),
    intArrayOf(8, 4, 4, 4, 35),
    intArrayOf(8, 5, 5, 4, 40),
    intArrayOf(9, 4, 4, 4, 35),   // Level 20 → BOSS
    intArrayOf(9, 5, 5, 5, 45),
    intArrayOf(10, 5, 5, 5, 40),
    intArrayOf(10, 5, 6, 5, 45),
    intArrayOf(10, 5, 6, 6, 50),
)

// ── UI State ──────────────────────────────────────────────────────────────────

data class PipeFlowUiState(
    val puzzle: PipePuzzle?     = null,
    val cells: List<PipeCell>   = emptyList(),
    val moves: Int              = 0,
    val level: Int              = 1,
    val totalStars: Int         = 0,
    val levelStars: Int         = 0,
    val flowResult: FlowResult? = null,
    val solved: Boolean         = false,
    val generating: Boolean     = true,
    val lastStars: Int          = 0,
    val autoSolveUsed: Boolean  = false,
    val altRouteBadge: Boolean  = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class PipeFlowViewModel : ViewModel() {
    private val _state = MutableStateFlow(PipeFlowUiState())
    val state: StateFlow<PipeFlowUiState> = _state.asStateFlow()

    init { loadLevel(1) }

    fun loadLevel(lvl: Int) {
        _state.update {
            it.copy(generating = true, level = lvl, solved = false,
                flowResult = null, moves = 0, autoSolveUsed = false,
                levelStars = 0, altRouteBadge = false)
        }
        viewModelScope.launch(Dispatchers.Default) {
            val spec   = PIPE_CAMPAIGN[minOf(lvl - 1, PIPE_CAMPAIGN.size - 1)]
            val isBoss = lvl % 10 == 0
            val puzzle = generatePipePuzzle(
                size = spec[0], difficulty = spec[1],
                rockCount = spec[2], lockCount = spec[3],
                xPipeChance = if (spec.size > 4) spec[4] else 0,
                bossMode = isBoss,
            )
            _state.update { it.copy(puzzle = puzzle, cells = puzzle.cells, generating = false) }
        }
    }

    fun rotateCell(idx: Int) {
        val s = _state.value; if (s.solved) return
        val cell = s.cells[idx]; if (cell.fixed || cell.locked) return
        if (cell.type == 'X') return  // X looks identical at any rotation
        val newCells = s.cells.toMutableList()
        newCells[idx] = cell.copy(rot = (cell.rot + 1) % 4)
        _state.update { it.copy(cells = newCells, moves = it.moves + 1, flowResult = null, altRouteBadge = false) }
    }

    fun checkFlow() {
        val s = _state.value; val puzzle = s.puzzle ?: return
        val result = if (puzzle.bossMode) validateBossFlow(s.cells, puzzle.size)
        else validateFlow(s.cells, puzzle.size)

        if (result.solved) {
            val isAltRoute = !s.autoSolveUsed && isAlternativeRoute(s.cells, puzzle)
            val stars = if (s.autoSolveUsed) 0 else when {
                s.moves <= puzzle.par                  -> 3
                s.moves <= (puzzle.par * 1.6).toInt() -> 2
                else                                   -> 1
            }
            if (stars > s.levelStars) {
                _state.update {
                    it.copy(flowResult = result, solved = true, lastStars = stars,
                        levelStars = stars, totalStars = it.totalStars + (stars - s.levelStars),
                        altRouteBadge = isAltRoute)
                }
            } else {
                _state.update { it.copy(flowResult = result, solved = true, lastStars = stars, altRouteBadge = isAltRoute) }
            }
        } else {
            _state.update { it.copy(flowResult = result) }
        }
    }

    private fun isAlternativeRoute(cells: List<PipeCell>, puzzle: PipePuzzle): Boolean {
        for ((idx, solRot) in puzzle.solution) {
            val cell = cells[idx]
            if (cell.type == 'X') continue  // X rotation is meaningless
            if (!cell.fixed && !cell.locked && cell.rot != solRot) return true
        }
        return false
    }

    fun autoSolve() {
        val s = _state.value; val puzzle = s.puzzle ?: return
        val newCells = s.cells.toMutableList()
        puzzle.solution.forEach { (idx, rot) ->
            val c = newCells[idx]
            if (!c.fixed && !c.locked) newCells[idx] = c.copy(rot = rot)
        }
        _state.update { it.copy(cells = newCells, autoSolveUsed = true, altRouteBadge = false) }
        checkFlow()
    }

    fun nextLevel() { loadLevel(minOf(_state.value.level + 1, PIPE_CAMPAIGN.size)) }
    fun reset()     { loadLevel(_state.value.level) }
}