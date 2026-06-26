package com.enigma.littlegames.ui.games.twentyfortyeight

// ─────────────────────────────────────────────────────────────────────────────
// 2048 — ViewModel
// 4×4 grid, swipe to slide tiles, merge equals, reach 2048 to win.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*
import kotlin.random.Random

enum class SwipeDir { UP, DOWN, LEFT, RIGHT }

data class TileData(
    val id: Int,
    val value: Int,
    val row: Int,
    val col: Int,
    val mergedFrom: List<Int> = emptyList(),  // IDs of source tiles (for animation)
)

data class TFEState(
    val tiles: List<TileData>  = emptyList(),
    val score: Int             = 0,
    val bestScore: Int         = 0,
    val isWon: Boolean         = false,
    val isOver: Boolean        = false,
    val keepPlaying: Boolean   = false,  // continue after reaching 2048
    val lastMergeScore: Int    = 0,      // for "score pop" animation
)

class TwentyFortyEightViewModel : ViewModel() {
    private val _state = MutableStateFlow(TFEState())
    val state: StateFlow<TFEState> = _state.asStateFlow()

    private var nextId = 0

    init { newGame() }

    fun newGame() {
        nextId = 0
        val tiles = mutableListOf<TileData>()
        val positions = (0..15).shuffled().take(2)
        positions.forEach { i ->
            tiles.add(TileData(nextId++, randomValue(), i / 4, i % 4))
        }
        _state.update { it.copy(
            tiles = tiles, score = 0, isWon = false,
            isOver = false, keepPlaying = false, lastMergeScore = 0,
        )}
    }

    fun keepPlaying() {
        _state.update { it.copy(keepPlaying = true, isWon = false) }
    }

    fun swipe(dir: SwipeDir) {
        val s = _state.value
        if ((s.isWon && !s.keepPlaying) || s.isOver) return

        val grid = Array(4) { r -> IntArray(4) { c ->
            s.tiles.find { it.row == r && it.col == c }?.value ?: 0
        }}

        val (newGrid, mergeScore) = slideGrid(grid, dir)
        if (newGrid.contentDeepEquals(grid)) return  // no change — ignore swipe

        val newTiles = mutableListOf<TileData>()
        var won = false
        for (r in 0..3) for (c in 0..3) {
            val v = newGrid[r][c]
            if (v != 0) {
                newTiles.add(TileData(nextId++, v, r, c))
                if (v == 2048) won = true
            }
        }

        // Place a new 2 or 4
        val empties = (0..15).filter { i ->
            newGrid[i / 4][i % 4] == 0
        }.shuffled()
        if (empties.isNotEmpty()) {
            val i = empties.first()
            newTiles.add(TileData(nextId++, randomValue(), i / 4, i % 4))
        }

        val newScore = s.score + mergeScore
        val newBest  = maxOf(s.bestScore, newScore)
        val over     = isGameOver(newGrid.apply {
            if (empties.isNotEmpty()) {
                val i = empties.first()
                this[i / 4][i % 4] = newTiles.last().value
            }
        })

        _state.update { it.copy(
            tiles = newTiles,
            score = newScore,
            bestScore = newBest,
            isWon = won && !it.keepPlaying,
            isOver = over && !won,
            lastMergeScore = mergeScore,
        )}
    }

    // ── Slide algorithm ───────────────────────────────────────────────────────

    private fun slideRow(row: IntArray): Pair<IntArray, Int> {
        val nonZero = row.filter { it != 0 }.toMutableList()
        var mergeScore = 0
        var i = 0
        while (i < nonZero.size - 1) {
            if (nonZero[i] == nonZero[i + 1]) {
                val merged = nonZero[i] * 2
                mergeScore += merged
                nonZero[i] = merged
                nonZero.removeAt(i + 1)
            }
            i++
        }
        val result = IntArray(4) { nonZero.getOrElse(it) { 0 } }
        return result to mergeScore
    }

    private fun slideGrid(grid: Array<IntArray>, dir: SwipeDir): Pair<Array<IntArray>, Int> {
        val rotated = when (dir) {
            SwipeDir.LEFT  -> grid
            SwipeDir.RIGHT -> rotateGrid(rotateGrid(grid))  // 180°
            SwipeDir.UP    -> rotateGrid(grid, ccw = true)   // CCW = slide up becomes slide left
            SwipeDir.DOWN  -> rotateGrid(grid)               // CW
        }

        var totalMerge = 0
        val slid = Array(4) { r ->
            val (row, ms) = slideRow(rotated[r])
            totalMerge += ms
            row
        }

        val result = when (dir) {
            SwipeDir.LEFT  -> slid
            SwipeDir.RIGHT -> rotateGrid(rotateGrid(slid))
            SwipeDir.UP    -> rotateGrid(slid)               // undo CCW
            SwipeDir.DOWN  -> rotateGrid(slid, ccw = true)   // undo CW
        }
        return result to totalMerge
    }

    /**
     * Rotate grid 90° clockwise (default) or counter-clockwise.
     * Clockwise: new[r][c] = old[3-c][r]
     */
    private fun rotateGrid(g: Array<IntArray>, ccw: Boolean = false): Array<IntArray> =
        if (ccw)
            Array(4) { r -> IntArray(4) { c -> g[c][3 - r] } }
        else
            Array(4) { r -> IntArray(4) { c -> g[3 - c][r] } }

    private fun isGameOver(grid: Array<IntArray>): Boolean {
        for (r in 0..3) for (c in 0..3) {
            if (grid[r][c] == 0) return false
            if (r < 3 && grid[r][c] == grid[r + 1][c]) return false
            if (c < 3 && grid[r][c] == grid[r][c + 1]) return false
        }
        return true
    }

    private fun randomValue() = if (Random.nextFloat() < 0.9f) 2 else 4
}
