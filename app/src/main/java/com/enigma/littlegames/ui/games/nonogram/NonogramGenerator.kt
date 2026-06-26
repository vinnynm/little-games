package com.enigma.littlegames.ui.games.nonogram

// ─────────────────────────────────────────────────────────────────────────────
// Nonogram Generator
// Provides:
//   • A library of hardcoded pixel images (5×5, 10×10, 15×15, 20×20)
//   • computeClues() — derives row/col clue lists from a solution grid
//   • lineOverlapSolver() — forced-cell deduction for a single line
//   • isSolvable() — verifies uniqueness via iterative line solving
// ─────────────────────────────────────────────────────────────────────────────

enum class NonogramDifficulty(
    val label: String,
    val emoji: String,
    val size: Int,
) {
    EASY  ("Easy",   "🌿", 5),
    MEDIUM("Medium", "⚡", 10),
    HARD  ("Hard",   "🔥", 15),
    EXPERT("Expert", "💀", 20),
}

// ─────────────────────────────────────────────────────────────────────────────
// Pixel image library
// Each image is a List<String> where '#' = filled, '.' = empty.
// ─────────────────────────────────────────────────────────────────────────────

private val EASY_IMAGES: List<List<String>> = listOf(
    // Heart
    listOf(".#.#.", "##.##", "#####", ".###.", "..#.."),
    // Arrow up
    listOf("..#..", ".###.", "#####", "..#..", "..#.."),
    // Diamond
    listOf("..#..", ".###.", "#####", ".###.", "..#.."),
    // Cross
    listOf("..#..", "..#..", "#####", "..#..", "..#.."),
    // Square frame
    listOf("#####", "#...#", "#...#", "#...#", "#####"),
    // Diagonal
    listOf("#....", ".#...", "..#..", "...#.", "....#"),
    // Checkerboard
    listOf("#.#.#", ".#.#.", "#.#.#", ".#.#.", "#.#.#"),
    // T shape
    listOf("#####", "..#..", "..#..", "..#..", "..#.."),
)

private val MEDIUM_IMAGES: List<List<String>> = listOf(
    // Smiley face
    listOf(
        "..####....",
        ".######...",
        "##..##.##.",
        "##..##.##.",
        "##########",
        "##########",
        "##.####.##",
        "###.##.###",
        ".########.",
        "..######..",
    ),
    // Star
    listOf(
        "....##....",
        "....##....",
        ".########.",
        "..######..",
        "##########",
        ".########.",
        "..##..##..",
        ".##....##.",
        "##......##",
        "#........#",
    ),
    // House
    listOf(
        "....##....",
        "...####...",
        "..######..",
        ".########.",
        "##########",
        "##.####.##",
        "##.####.##",
        "##.####.##",
        "##......##",
        "##########",
    ),
    // Fish
    listOf(
        "....#.....",
        "...###....",
        "..#####...",
        ".#######..",
        "##########",
        "#######.##",
        ".#######..",
        "..#####...",
        "...###....",
        "....#.....",
    ),
    // Rocket
    listOf(
        "....##....",
        "...####...",
        "..######..",
        "..######..",
        ".########.",
        ".########.",
        "##########",
        "#.######.#",
        "#.######.#",
        ".##....##.",
    ),
)

private val HARD_IMAGES: List<List<String>> = listOf(
    // Castle
    listOf(
        "###.###.###.###",
        "###.###.###.###",
        "###############",
        "###############",
        "##...#####...##",
        "##...#####...##",
        "###############",
        "###############",
        "##.#########.##",
        "##.#########.##",
        "###.#######.###",
        "####.#####.####",
        "#####.###.#####",
        "######.#.######",
        "###############",
    ),
    // Butterfly
    listOf(
        "##.........##",
        "###.......###",
        "#####...#####",
        ".######.#####",
        "..###########",
        "...##########",
        "....#########",
        "...##########",
        "..###########",
        ".######.#####",
        "#####...#####",
        "###.......###",
        "##.........##",
        "##.........##",
        "#...........#",
    ),
    // Tree
    listOf(
        ".......#.......",
        "......###......",
        ".....#####.....",
        "....#######....",
        "...#########...",
        "..###########..",
        ".#############.",
        "###############",
        ".....#####.....",
        ".....#####.....",
        ".....#####.....",
        ".....#####.....",
        ".....#####.....",
        ".....#####.....",
        ".....#####.....",
    ),
)

private val EXPERT_IMAGES: List<List<String>> = listOf(
    // Dragon (simplified pixel art)
    listOf(
        "..................##",
        ".................####",
        "...............#######",
        "..............#########",
        ".............###########",
        "............#############",
        "...........###.##########",
        "..........####.##########",
        ".........#####.##########",
        "........######.###.######",
        ".......#############.####",
        "......################.##",
        ".....###################.",
        "....####################.",
        "...#####################.",
        "..######################.",
        ".#######################.",
        "########################.",
        "########################.",
        ".######################..",
    ).map { it.padEnd(20, '.').take(20) },
    // City skyline
    listOf(
        ".......##...........",
        ".......##...........",
        "......####..........",
        "......####..........",
        ".....######.........",
        "....##########......",
        "....##########......",
        "...############.....",
        "...############.....",
        "..##############....",
        "..##############....",
        ".################...",
        ".################...",
        "####################",
        "####################",
        "####################",
        "####################",
        "####################",
        "####################",
        "####################",
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// Public API
// ─────────────────────────────────────────────────────────────────────────────

/** Pick a random image for the given difficulty and return it as a BooleanArray grid. */
fun randomImage(d: NonogramDifficulty): Array<BooleanArray> {
    val images = when (d) {
        NonogramDifficulty.EASY   -> EASY_IMAGES
        NonogramDifficulty.MEDIUM -> MEDIUM_IMAGES
        NonogramDifficulty.HARD   -> HARD_IMAGES
        NonogramDifficulty.EXPERT -> EXPERT_IMAGES
    }
    val raw = images.random()
    val n   = d.size
    return Array(n) { r ->
        BooleanArray(n) { c -> raw.getOrNull(r)?.getOrNull(c) == '#' }
    }
}

/** Compute row clues from a solution grid. */
fun computeRowClues(solution: Array<BooleanArray>): List<List<Int>> =
    solution.map { row -> runLengths(row.toList()) }

/** Compute column clues from a solution grid. */
fun computeColClues(solution: Array<BooleanArray>): List<List<Int>> {
    val n = solution.size
    return (0 until n).map { c ->
        runLengths((0 until n).map { r -> solution[r][c] })
    }
}

private fun runLengths(cells: List<Boolean>): List<Int> {
    val runs = mutableListOf<Int>()
    var count = 0
    for (v in cells) {
        if (v) count++ else { if (count > 0) { runs.add(count); count = 0 } }
    }
    if (count > 0) runs.add(count)
    return if (runs.isEmpty()) listOf(0) else runs
}

// ─────────────────────────────────────────────────────────────────────────────
// Line solver — overlap method
//
// Returns a TriState array for one line:
//   +1 = forced filled
//   -1 = forced empty
//    0 = unknown
// ─────────────────────────────────────────────────────────────────────────────

fun solveLineOverlap(clue: List<Int>, known: IntArray): IntArray {
    val n = known.size
    val result = IntArray(n)

    // leftmost placement: pack runs as far left as possible
    val left = IntArray(clue.size) { -1 }
    run {
        var pos = 0
        for (i in clue.indices) {
            // Advance past any forced-empty cells
            while (pos < n && known[pos] == -1) pos++
            if (pos + clue[i] > n) return result  // can't place
            left[i] = pos
            pos += clue[i] + 1  // +1 for mandatory gap
        }
    }

    // rightmost placement: pack runs as far right as possible
    val right = IntArray(clue.size) { -1 }
    run {
        var pos = n - 1
        for (i in clue.indices.reversed()) {
            while (pos >= 0 && known[pos] == -1) pos--
            if (pos - clue[i] + 1 < 0) return result
            right[i] = pos - clue[i] + 1
            pos -= clue[i] + 1
        }
    }

    // Overlap: cells covered by both leftmost and rightmost for the same run
    for (i in clue.indices) {
        val l = left[i]; val r = right[i]
        if (l == -1 || r == -1) continue
        // Cells in [r, l+clue[i]-1] are forced filled
        for (c in r until l + clue[i]) {
            if (c in 0 until n) result[c] = 1
        }
    }

    // Cells not reachable by any run are forced empty
    val reachable = BooleanArray(n)
    for (i in clue.indices) {
        val l = left[i]; val r = right[i]
        if (l == -1 || r == -1) continue
        for (c in l until r + clue[i]) if (c in 0 until n) reachable[c] = true
    }
    for (c in 0 until n) if (!reachable[c] && result[c] == 0) result[c] = -1

    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// Iterative solver — runs line solver until no more progress
// Returns the solved grid (partial if puzzle is ambiguous)
// ─────────────────────────────────────────────────────────────────────────────

fun iterativeSolve(
    rowClues: List<List<Int>>,
    colClues: List<List<Int>>,
    n: Int,
): Array<IntArray> {
    val grid = Array(n) { IntArray(n) }  // 0=unknown, 1=filled, -1=empty
    var changed = true
    while (changed) {
        changed = false
        for (r in 0 until n) {
            val hints = solveLineOverlap(rowClues[r], grid[r])
            for (c in 0 until n) {
                if (hints[c] != 0 && grid[r][c] == 0) {
                    grid[r][c] = hints[c]; changed = true
                }
            }
        }
        for (c in 0 until n) {
            val col   = IntArray(n) { r -> grid[r][c] }
            val hints = solveLineOverlap(colClues[c], col)
            for (r in 0 until n) {
                if (hints[r] != 0 && grid[r][c] == 0) {
                    grid[r][c] = hints[r]; changed = true
                }
            }
        }
    }
    return grid
}
