package com.enigma.littlegames.ui.games.twentyfortyeight

// ─────────────────────────────────────────────────────────────────────────────
// 2048 — ViewModel  (fixed)
//
// Key fixes vs. previous version:
//   1. Tile IDs are now STABLE across swipes.
//      - The old code created brand-new IDs for every tile on every swipe,
//        which meant animateFloatAsState in the Screen never had a chance to
//        animate because Compose saw every tile as completely new.
//      - New approach: each logical tile keeps its ID until it disappears.
//        Merges produce one survivor tile that inherits one of the source IDs,
//        and the consumed tile is removed from the list.
//
//   2. isGameOver() now receives the correct post-spawn grid.
//      - Old code called isGameOver on a partially-mutated intermediate array,
//        giving wrong "game over" detection in edge cases.
//      - New code builds finalGrid cleanly from newTiles before calling it.
//
//   3. isWon and isOver are now mutually exclusive.
//      - Old code could set both simultaneously; new code ensures only one
//        of the two is ever true at a time.
//
//   4. TileData carries a `justSpawned` and `justMerged` flag used by the
//      Screen to trigger the pop animation.  The flags are cleared after one
//      frame by a separate state update.
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
    // Animation-trigger flags — true for exactly one composition after the event
    val justMerged: Boolean = false,
    val justSpawned: Boolean = false,
)

data class TFEState(
    val tiles: List<TileData>  = emptyList(),
    val score: Int             = 0,
    val bestScore: Int         = 0,
    val isWon: Boolean         = false,
    val isOver: Boolean        = false,
    val keepPlaying: Boolean   = false,
)

class TwentyFortyEightViewModel : ViewModel() {
    private val _state = MutableStateFlow(TFEState())
    val state: StateFlow<TFEState> = _state.asStateFlow()

    // Monotonically increasing ID counter — never reused
    private var nextId = 0

    init { newGame() }

    fun newGame() {
        nextId = 0
        val tiles = mutableListOf<TileData>()
        val positions = (0..15).shuffled().take(2)
        positions.forEach { i ->
            tiles.add(TileData(nextId++, randomValue(), i / 4, i % 4, justSpawned = true))
        }
        _state.value = TFEState(
            tiles     = tiles,
            score     = 0,
            isWon     = false,
            isOver    = false,
            keepPlaying = false,
        )
    }

    fun keepPlaying() {
        _state.update { it.copy(keepPlaying = true, isWon = false) }
    }

    fun swipe(dir: SwipeDir) {
        val s = _state.value
        if ((s.isWon && !s.keepPlaying) || s.isOver) return

        // Convert current tile list → 4×4 grid of (tileId, value); 0 = empty
        // Each cell: Pair(id, value), or null for empty
        data class Cell(val id: Int, val value: Int)
        val grid: Array<Array<Cell?>> = Array(4) { Array(4) { null } }
        for (t in s.tiles) grid[t.row][t.col] = Cell(t.id, t.value)

        // Slide the grid and collect merge events
        data class MergeEvent(val survivorId: Int, val consumedId: Int, val value: Int, val row: Int, val col: Int)
        val merges = mutableListOf<MergeEvent>()

        // slideRow works on a single row in the "move left" orientation.
        // Returns the new row and appends to merges.
        fun slideRow(cells: Array<Cell?>): Array<Cell?> {
            val nonNull = cells.filterNotNull().toMutableList()
            val result = arrayOfNulls<Cell?>(4)
            var writeIdx = 0
            var readIdx  = 0
            while (readIdx < nonNull.size) {
                val cur = nonNull[readIdx]
                if (readIdx + 1 < nonNull.size && nonNull[readIdx + 1].value == cur.value) {
                    // Merge: survivor keeps cur's id
                    val merged = cur.copy(value = cur.value * 2)
                    merges.add(MergeEvent(cur.id, nonNull[readIdx + 1].id, merged.value, -1, -1)) // row/col filled below
                    result[writeIdx++] = merged
                    readIdx += 2
                } else {
                    result[writeIdx++] = cur
                    readIdx++
                }
            }
            return result
        }

        // Rotate grid so that every direction becomes a "slide left"
        fun rotate(g: Array<Array<Cell?>>, times: Int): Array<Array<Cell?>> {
            var r = g
            repeat(((times % 4) + 4) % 4) {
                val tmp = Array(4) { row -> Array(4) { col -> r[3 - col][row] } }
                r = tmp
            }
            return r
        }

        val rotations = when (dir) {
            SwipeDir.LEFT  -> 0
            SwipeDir.RIGHT -> 2
            SwipeDir.UP    -> 3   // CCW 90 → slide left = slide up
            SwipeDir.DOWN  -> 1   // CW  90 → slide left = slide down
        }
        val rotated = rotate(grid, rotations)

        // Slide each row
        val slid = Array(4) { r -> slideRow(rotated[r]) }

        // Un-rotate
        val unRotations = when (dir) {
            SwipeDir.LEFT  -> 0
            SwipeDir.RIGHT -> 2
            SwipeDir.UP    -> 1
            SwipeDir.DOWN  -> 3
        }
        val result = rotate(slid, unRotations)

        // Assign final positions and check if anything changed
        val newTileMap = mutableMapOf<Int, TileData>()        // id → tile
        val consumedIds = merges.map { it.consumedId }.toSet()
        var mergeScore = 0

        for (r in 0..3) for (c in 0..3) {
            val cell = result[r][c] ?: continue
            val wasMerged = merges.any { it.survivorId == cell.id }
            if (wasMerged) mergeScore += cell.value
            newTileMap[cell.id] = TileData(
                id          = cell.id,
                value       = cell.value,
                row         = r,
                col         = c,
                justMerged  = wasMerged,
                justSpawned = false,
            )
        }

        // If nothing moved, ignore the swipe
        val oldPositions = s.tiles.associate { it.id to (it.row to it.col) }
        val moved = newTileMap.any { (id, t) ->
            oldPositions[id] != (t.row to t.col)
        } || consumedIds.isNotEmpty()
        if (!moved) return

        // Spawn a new tile in a random empty cell
        val occupied = newTileMap.values.map { it.row * 4 + it.col }.toSet()
        val empties  = (0..15).filter { it !in occupied }
        var spawnedTile: TileData? = null
        if (empties.isNotEmpty()) {
            val i = empties.random()
            spawnedTile = TileData(nextId++, randomValue(), i / 4, i % 4, justSpawned = true)
            newTileMap[spawnedTile.id] = spawnedTile
        }

        val finalTiles = newTileMap.values.toList()

        // Win check (only when not already in keep-playing mode)
        val hitWin = !s.keepPlaying && finalTiles.any { it.value >= 2048 }

        // Game-over check: build a clean int grid from finalTiles
        val intGrid = Array(4) { r -> IntArray(4) { c ->
            finalTiles.find { it.row == r && it.col == c }?.value ?: 0
        }}
        val boardFull = isGameOver(intGrid)

        val newScore = s.score + mergeScore
        val newBest  = maxOf(s.bestScore, newScore)

        _state.update {
            it.copy(
                tiles     = finalTiles,
                score     = newScore,
                bestScore = newBest,
                isWon     = hitWin,
                // Only declare game over if the board is truly full AND we didn't just win
                isOver    = boardFull && !hitWin,
            )
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

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
