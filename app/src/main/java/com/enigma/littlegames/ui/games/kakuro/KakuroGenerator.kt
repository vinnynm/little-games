package com.enigma.littlegames.ui.games.kakuro

// ─────────────────────────────────────────────────────────────────────────────
// KakuroGenerator — procedural random Kakuro puzzle generator.
//
// Ported from the standalone Kakuro app into the Enigma Game Hub package.
//
// Bug fix (audit #7 — medium):
// buildFallback() used to build a `downCellLists` list containing an
// obviously wrong self-referential entry — `KPos(3,3).let { KPos(2,3) }`,
// which is really just a duplicate of the previous element "fixed" by a
// trailing `.distinct()` — and then discarded that entire list in favor of
// a separately hand-written `downRunsFixed`. The buggy list was never
// actually used, but it was a landmine for anyone who later "cleaned up"
// the apparently-unused variable without noticing `downRunsFixed` was the
// real source of truth. Removed the dead/buggy list entirely; only the
// correct `downRuns` (renamed from `downRunsFixed`) remains.
//
// How it works (three phases):
//  1. buildLayout — scatter black wall cells at random, then repair any run
//     that's length 1 (invalid) or > 9 (can't hold distinct 1-9 digits).
//  2. fillSolution — randomized most-constrained-cell-first backtracking to
//     place digits 1-9 in every white cell with no repeats within a run.
//  3. Clue derivation — each run's clue = sum of the digits placed in it.
//
// Solution uniqueness is not proven (see README of the source project for
// rationale). Win checking in KakuroViewModel validates against the displayed
// clues, not a stored answer key, so alternate valid completions are accepted.
// ─────────────────────────────────────────────────────────────────────────────

import kotlin.random.Random

internal object KakuroGenerator {

    private const val MAX_LAYOUT_ATTEMPTS = 40
    private const val MIN_WHITE_FRACTION  = 0.40
    private const val MAX_FIXUP_PASSES    = 800

    fun generate(
        innerRows: Int,
        innerCols: Int,
        density: Double,
        random: Random = Random.Default,
    ): KakuroPuzzleData {
        require(innerRows >= 3 && innerCols >= 3) { "Grid must be at least 3×3" }
        val totalRows = innerRows + 1
        val totalCols = innerCols + 1

        repeat(MAX_LAYOUT_ATTEMPTS) {
            val black = buildLayout(innerRows, innerCols, density, random)
            val whiteFraction = countWhite(black, totalRows, totalCols).toDouble() / (innerRows * innerCols)
            if (whiteFraction < MIN_WHITE_FRACTION) return@repeat

            val acrossCellLists = collectAcrossRuns(black, totalRows, totalCols)
            val downCellLists   = collectDownRuns(black, totalRows, totalCols)

            val solution = fillSolution(totalRows, totalCols, acrossCellLists, downCellLists, random)
                ?: return@repeat

            val acrossRuns = acrossCellLists.map { cells -> toRunInfo(cells, solution, isAcross = true) }
            val downRuns   = downCellLists.map   { cells -> toRunInfo(cells, solution, isAcross = false) }

            return KakuroPuzzleData(totalRows, totalCols, black, solution, acrossRuns, downRuns)
        }

        // Hardcoded 5×6 fallback so the game is never stuck
        return buildFallback()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun toRunInfo(cells: List<KPos>, solution: Array<IntArray>, isAcross: Boolean): KRun {
        val first = cells.first()
        val clue  = if (isAcross) KPos(first.row, first.col - 1) else KPos(first.row - 1, first.col)
        val sum   = cells.sumOf { solution[it.row][it.col] }
        return KRun(isAcross, cells, clue, sum)
    }

    private fun countWhite(black: Array<BooleanArray>, totalRows: Int, totalCols: Int): Int {
        var n = 0
        for (r in 1 until totalRows) for (c in 1 until totalCols) if (!black[r][c]) n++
        return n
    }

    // ── Phase 1 ───────────────────────────────────────────────────────────────

    private fun buildLayout(
        innerRows: Int, innerCols: Int, density: Double, random: Random,
    ): Array<BooleanArray> {
        val totalRows = innerRows + 1
        val totalCols = innerCols + 1
        val black = Array(totalRows) { r -> BooleanArray(totalCols) { c -> r == 0 || c == 0 } }
        for (r in 1 until totalRows) for (c in 1 until totalCols) black[r][c] = random.nextDouble() < density

        repeat(MAX_FIXUP_PASSES) {
            val h = fixRunsInRows(black, totalRows, totalCols, random)
            val v = fixRunsInCols(black, totalRows, totalCols, random)
            if (!h && !v) return black
        }
        return black
    }

    private fun fixRunsInRows(
        black: Array<BooleanArray>, totalRows: Int, totalCols: Int, random: Random,
    ): Boolean {
        var changed = false
        for (r in 1 until totalRows) {
            var c = 1
            while (c < totalCols) {
                if (black[r][c]) { c++; continue }
                val start = c
                while (c < totalCols && !black[r][c]) c++
                changed = repairRun(start, c - 1, random) { black[r][it] = true } or changed
            }
        }
        return changed
    }

    private fun fixRunsInCols(
        black: Array<BooleanArray>, totalRows: Int, totalCols: Int, random: Random,
    ): Boolean {
        var changed = false
        for (c in 1 until totalCols) {
            var r = 1
            while (r < totalRows) {
                if (black[r][c]) { r++; continue }
                val start = r
                while (r < totalRows && !black[r][c]) r++
                changed = repairRun(start, r - 1, random) { black[it][c] = true } or changed
            }
        }
        return changed
    }

    private inline fun repairRun(start: Int, end: Int, random: Random, markBlack: (Int) -> Unit): Boolean {
        val length = end - start + 1
        return when {
            length == 1 -> { markBlack(start); true }
            length > 9  -> { markBlack(pickSplitPoint(start, end, random)); true }
            else        -> false
        }
    }

    private fun pickSplitPoint(start: Int, end: Int, random: Random): Int {
        val cands = (start + 1 until end).filter { k ->
            (k - start) in 2..9 && (end - k) in 2..9
        }
        return if (cands.isNotEmpty()) cands[random.nextInt(cands.size)] else start + (end - start) / 2
    }

    // ── Run collection ────────────────────────────────────────────────────────

    private fun collectAcrossRuns(
        black: Array<BooleanArray>, totalRows: Int, totalCols: Int,
    ): List<List<KPos>> {
        val runs = mutableListOf<List<KPos>>()
        for (r in 1 until totalRows) {
            var c = 1
            while (c < totalCols) {
                if (black[r][c]) { c++; continue }
                val start = c
                while (c < totalCols && !black[r][c]) c++
                runs.add((start until c).map { KPos(r, it) })
            }
        }
        return runs
    }

    private fun collectDownRuns(
        black: Array<BooleanArray>, totalRows: Int, totalCols: Int,
    ): List<List<KPos>> {
        val runs = mutableListOf<List<KPos>>()
        for (c in 1 until totalCols) {
            var r = 1
            while (r < totalRows) {
                if (black[r][c]) { r++; continue }
                val start = r
                while (r < totalRows && !black[r][c]) r++
                runs.add((start until r).map { KPos(it, c) })
            }
        }
        return runs
    }

    // ── Phase 2: backtracking fill ────────────────────────────────────────────

    private fun fillSolution(
        totalRows: Int,
        totalCols: Int,
        acrossCellLists: List<List<KPos>>,
        downCellLists: List<List<KPos>>,
        random: Random,
    ): Array<IntArray>? {
        val acrossOf = HashMap<KPos, Int>()
        acrossCellLists.forEachIndexed { idx, cells -> cells.forEach { acrossOf[it] = idx } }
        val downOf = HashMap<KPos, Int>()
        downCellLists.forEachIndexed { idx, cells -> cells.forEach { downOf[it] = idx } }

        val acrossUsed = IntArray(acrossCellLists.size)
        val downUsed   = IntArray(downCellLists.size)
        val grid       = Array(totalRows) { IntArray(totalCols) }

        fun candidates(cell: KPos): List<Int> {
            val used = acrossUsed[acrossOf.getValue(cell)] or downUsed[downOf.getValue(cell)]
            val out  = ArrayList<Int>(9)
            for (d in 1..9) if (used and (1 shl d) == 0) out.add(d)
            return out
        }

        fun backtrack(pool: MutableList<KPos>): Boolean {
            if (pool.isEmpty()) return true
            var bestIndex = -1
            var bestCands: List<Int>? = null
            for (i in pool.indices) {
                val cands = candidates(pool[i])
                if (bestCands == null || cands.size < bestCands.size) {
                    bestIndex = i; bestCands = cands
                    if (cands.isEmpty()) break
                }
            }
            val cands = bestCands ?: return false
            if (cands.isEmpty()) return false

            val cell  = pool[bestIndex]
            val last  = pool.size - 1
            pool[bestIndex] = pool[last]; pool.removeAt(last)

            val ar = acrossOf.getValue(cell)
            val dr = downOf.getValue(cell)
            for (d in cands.shuffled(random)) {
                val bit = 1 shl d
                grid[cell.row][cell.col] = d
                acrossUsed[ar] = acrossUsed[ar] or bit
                downUsed[dr]   = downUsed[dr]   or bit
                if (backtrack(pool)) return true
                acrossUsed[ar] = acrossUsed[ar] and bit.inv()
                downUsed[dr]   = downUsed[dr]   and bit.inv()
                grid[cell.row][cell.col] = 0
            }
            pool.add(cell)
            return false
        }

        val pool = acrossOf.keys.toMutableList().also { it.shuffle(random) }
        return if (backtrack(pool)) grid else null
    }

    // ── Hardcoded fallback ────────────────────────────────────────────────────

    private fun buildFallback(): KakuroPuzzleData {
        // A minimal 5×6 puzzle (innerRows=5, innerCols=6 → 6 rows × 7 cols)
        // Black mask (true = black):
        // Row 0: all black (frame)
        // Col 0: all black (frame)
        //         0     1     2     3     4     5     6
        // Row 0: [B,    B,    B,    B,    B,    B,    B ]
        // Row 1: [B,    B,    W,    W,    B,    W,    W ]
        // Row 2: [B,    W,    W,    W,    W,    W,    B ]
        // Row 3: [B,    W,    W,    B,    W,    W,    W ]
        // Row 4: [B,    B,    W,    W,    W,    W,    B ]
        // Row 5: [B,    B,    B,    W,    W,    B,    B ]
        val totalRows = 6; val totalCols = 7
        val mask = arrayOf(
            booleanArrayOf(true,  true,  true,  true,  true,  true,  true),
            booleanArrayOf(true,  true,  false, false, true,  false, false),
            booleanArrayOf(true,  false, false, false, false, false, true),
            booleanArrayOf(true,  false, false, true,  false, false, false),
            booleanArrayOf(true,  true,  false, false, false, false, true),
            booleanArrayOf(true,  true,  true,  false, false, true,  true),
        )
        val sol = Array(totalRows) { IntArray(totalCols) }
        // Place digits manually (all valid — no row/col repeat per run)
        // Row 1: cols 2,3 → 3,4   cols 5,6 → 1,2
        sol[1][2]=3; sol[1][3]=4; sol[1][5]=1; sol[1][6]=2
        // Row 2: cols 1-5 → 5,6,7,8,9
        sol[2][1]=5; sol[2][2]=6; sol[2][3]=7; sol[2][4]=8; sol[2][5]=9
        // Row 3: cols 1,2 → 2,1  cols 4,5,6 → 4,3,6
        sol[3][1]=2; sol[3][2]=1; sol[3][4]=4; sol[3][5]=3; sol[3][6]=6
        // Row 4: cols 2-5 → 8,7,5,2
        sol[4][2]=8; sol[4][3]=7; sol[4][4]=5; sol[4][5]=2
        // Row 5: cols 3,4 → 6,9
        sol[5][3]=6; sol[5][4]=9

        val acrossCellLists = listOf(
            listOf(KPos(1,2), KPos(1,3)),
            listOf(KPos(1,5), KPos(1,6)),
            listOf(KPos(2,1), KPos(2,2), KPos(2,3), KPos(2,4), KPos(2,5)),
            listOf(KPos(3,1), KPos(3,2)),
            listOf(KPos(3,4), KPos(3,5), KPos(3,6)),
            listOf(KPos(4,2), KPos(4,3), KPos(4,4), KPos(4,5)),
            listOf(KPos(5,3), KPos(5,4)),
        )

        // Bug fix (audit #7): this used to be built twice — once as a buggy
        // `downCellLists` containing a self-referential no-op entry
        // (`KPos(3,3).let { KPos(2,3) }`, a disguised duplicate of the
        // previous element, "fixed" via `.distinct()`), which was computed
        // and then thrown away in favor of a second, correct list called
        // `downRunsFixed`. The buggy list added confusion with zero benefit
        // — only the correct down-run list is defined now.
        val downRuns = listOf(
            listOf(KPos(2,1), KPos(3,1)),
            listOf(KPos(1,2), KPos(2,2), KPos(3,2), KPos(4,2)),
            listOf(KPos(1,3), KPos(2,3), KPos(4,3), KPos(5,3)),
            listOf(KPos(2,4), KPos(3,4), KPos(4,4), KPos(5,4)),
            listOf(KPos(1,5), KPos(2,5), KPos(3,5), KPos(4,5)),
            listOf(KPos(1,6), KPos(3,6)),
        )

        val acrossRuns = acrossCellLists.map { toRunInfo(it, sol, true) }
        val downRunsInfo = downRuns.map { toRunInfo(it, sol, false) }
        return KakuroPuzzleData(totalRows, totalCols, mask, sol, acrossRuns, downRunsInfo)
    }
}
