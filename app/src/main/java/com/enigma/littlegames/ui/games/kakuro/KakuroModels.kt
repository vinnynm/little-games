package com.enigma.littlegames.ui.games.kakuro

// ─────────────────────────────────────────────────────────────────────────────
// KakuroModels — data types for the procedural Kakuro game.
// Ported from com.example.kakuro.model into the hub package.
// ─────────────────────────────────────────────────────────────────────────────

/** A single (row, col) coordinate on the grid. Row 0 and column 0 are always
 * black frame cells, so playable cells start at (1, 1). */
data class KPos(val row: Int, val col: Int)

/** Grid-size presets exposed in the UI. */
enum class KakuroGridSize(val label: String, val innerRows: Int, val innerCols: Int) {
    TINY   ("6 × 6",   6,  6),
    SMALL  ("8 × 8",   8,  8),
    CLASSIC("10 × 10", 10, 10),
    LARGE  ("13 × 13", 13, 13),
}

/** Difficulty controls the density of black wall cells.
 * More walls → shorter runs → easier to solve (fewer digit combinations). */
enum class KakuroDifficulty(val label: String, val emoji: String, val blackDensity: Double) {
    EASY  ("Easy",   "🌿", 0.24),
    MEDIUM("Medium", "⚡", 0.19),
    HARD  ("Hard",   "🔥", 0.15),
    EXPERT("Expert", "💀", 0.12),
}

/** One contiguous run of white cells — horizontal ("across") or vertical ("down").
 * [clue] is the (row, col) of the black cell that displays this run's sum. */
data class KRun(
    val isAcross: Boolean,
    val cells: List<KPos>,
    val clue: KPos,
    val sum: Int,
) {
    val length: Int get() = cells.size
}

/**
 * A fully generated Kakuro puzzle.
 *
 * [solution] holds the digit (1-9) for every white cell and 0 for black cells.
 * Win checking validates the player's grid against the displayed clues (not
 * this solution), so alternate valid completions are accepted.
 */
class KakuroPuzzleData(
    val totalRows: Int,
    val totalCols: Int,
    val black: Array<BooleanArray>,
    val solution: Array<IntArray>,
    val acrossRuns: List<KRun>,
    val downRuns: List<KRun>,
) {
    /** White cell → the across run it belongs to. */
    val acrossRunAt: Map<KPos, KRun> = buildMap {
        acrossRuns.forEach { run -> run.cells.forEach { put(it, run) } }
    }

    /** White cell → the down run it belongs to. */
    val downRunAt: Map<KPos, KRun> = buildMap {
        downRuns.forEach { run -> run.cells.forEach { put(it, run) } }
    }

    /** Black cell → across clue value shown in its top-right triangle. */
    val acrossClueAt: Map<KPos, Int> = acrossRuns.associate { it.clue to it.sum }

    /** Black cell → down clue value shown in its bottom-left triangle. */
    val downClueAt: Map<KPos, Int> = downRuns.associate { it.clue to it.sum }

    val allWhiteCells: List<KPos> = acrossRunAt.keys.toList()

    fun isWhite(row: Int, col: Int): Boolean = !black[row][col]
}
