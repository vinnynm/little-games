package com.enigma.littlegames.ui.games.twentyfortyeight

// ─────────────────────────────────────────────────────────────────────────────
// 2048 — ViewModel
// Flat IntArray(16) board; slide+merge logic; swipe gesture mapping.
// Tiles are tracked by stable IDs so Compose can animate merges correctly.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*

enum class SwipeDir { UP, DOWN, LEFT, RIGHT }

data class Tile(
    val id: Int,           // stable across moves for animation keying
    val value: Int,
    val row: Int,
    val col: Int,
)

data class TFEState(
    val tiles: List<Tile>      = emptyList(),
    val score: Int             = 0,
    val best: Int              = 0,
    val isOver: Boolean        = false,
    val won: Boolean           = false,         // reached 2048
    val keepPlaying: Boolean   = false,         // continue after 2048
)

class TwentyFortyEightViewModel : ViewModel() {

    private val _state = MutableStateFlow(TFEState())
    val state: StateFlow<TFEState> = _state.asStateFlow()

    // Callback for hub reporting
    var onWin:   ((score: Int) -> Unit)? = null
    var onBest:  ((score: Int) -> Unit)? = null

    private var nextId = 0

    init { newGame() }

    fun newGame() {
        nextId = 0
        val tiles = mutableListOf<Tile>()
        addRandomTile(tiles)
        addRandomTile(tiles)
        _state.update { it.copy(tiles = tiles, score = 0, isOver = false, won = false, keepPlaying = false) }
    }

    fun keepPlaying() { _state.update { it.copy(keepPlaying = true) } }

    fun swipe(dir: SwipeDir) {
        val s = _state.value
        if (s.isOver) return
        if (s.won && !s.keepPlaying) return

        val board   = tilesToBoard(s.tiles)
        val result  = slideBoard(board, dir)
        if (!result.moved) return

        val newScore = s.score + result.points
        val newBest  = maxOf(s.best, newScore)
        val newTiles = boardToTiles(result.board)
        addRandomTile(newTiles)

        val won   = !s.won && newTiles.any { it.value == 2048 }
        val over  = isGameOver(newTiles)

        _state.update { it.copy(tiles = newTiles, score = newScore, best = newBest,
            won = s.won || won, isOver = over) }

        if (won) onWin?.invoke(newScore)
        if (newScore > s.best) onBest?.invoke(newScore)
    }

    // ── Board representation ──────────────────────────────────────────────────

    /** Convert tile list → 4×4 IntArray (row-major). */
    private fun tilesToBoard(tiles: List<Tile>): IntArray {
        val b = IntArray(16)
        tiles.forEach { b[it.row * 4 + it.col] = it.value }
        return b
    }

    /** Convert 4×4 IntArray → tile list with stable IDs. */
    private fun boardToTiles(board: IntArray): MutableList<Tile> {
        val tiles = mutableListOf<Tile>()
        board.forEachIndexed { idx, v ->
            if (v != 0) tiles.add(Tile(nextId++, v, idx / 4, idx % 4))
        }
        return tiles
    }

    private fun addRandomTile(tiles: MutableList<Tile>) {
        val occupied = tiles.map { it.row * 4 + it.col }.toSet()
        val empty    = (0..15).filter { it !in occupied }
        if (empty.isEmpty()) return
        val idx   = empty.random()
        val value = if (Math.random() < 0.9) 2 else 4
        tiles.add(Tile(nextId++, value, idx / 4, idx % 4))
    }

    private fun isGameOver(tiles: List<Tile>): Boolean {
        if (tiles.size < 16) return false
        val board = tilesToBoard(tiles)
        // Check any adjacent equal values
        for (r in 0..3) for (c in 0..3) {
            val v = board[r * 4 + c]
            if (c < 3 && board[r * 4 + c + 1] == v) return false
            if (r < 3 && board[(r + 1) * 4 + c] == v) return false
        }
        return true
    }

    // ── Slide logic ───────────────────────────────────────────────────────────

    data class SlideResult(val board: IntArray, val points: Int, val moved: Boolean)

    private fun slideBoard(board: IntArray, dir: SwipeDir): SlideResult {
        val rotated = rotate(board, dir)   // normalise direction to "slide left"
        var totalPoints = 0
        var moved       = false
        val result      = IntArray(16)

        for (row in 0..3) {
            val line    = IntArray(4) { rotated[row * 4 + it] }
            val (slid, pts, didMove) = slideLeft(line)
            for (c in 0..3) result[row * 4 + c] = slid[c]
            totalPoints += pts
            if (didMove) moved = true
        }

        return SlideResult(unrotate(result, dir), totalPoints, moved)
    }

    private fun slideLeft(row: IntArray): Triple<IntArray, Int, Boolean> {
        val original = row.copyOf()
        val nonZero  = row.filter { it != 0 }.toMutableList()
        var points   = 0
        var i        = 0
        while (i < nonZero.size - 1) {
            if (nonZero[i] == nonZero[i + 1]) {
                nonZero[i] *= 2
                points += nonZero[i]
                nonZero.removeAt(i + 1)
            }
            i++
        }
        val result = IntArray(4) { nonZero.getOrElse(it) { 0 } }
        return Triple(result, points, !result.contentEquals(original))
    }

    /** Rotate board so that the desired slide direction becomes "slide left". */
    private fun rotate(board: IntArray, dir: SwipeDir): IntArray = when (dir) {
        SwipeDir.LEFT  -> board.copyOf()
        SwipeDir.RIGHT -> mirrorH(board)
        SwipeDir.UP    -> transpose(board)
        SwipeDir.DOWN  -> mirrorH(transpose(board))
    }

    private fun unrotate(board: IntArray, dir: SwipeDir): IntArray = when (dir) {
        SwipeDir.LEFT  -> board.copyOf()
        SwipeDir.RIGHT -> mirrorH(board)
        SwipeDir.UP    -> transpose(board)
        SwipeDir.DOWN  -> transpose(mirrorH(board))
    }

    private fun transpose(b: IntArray): IntArray = IntArray(16) { idx ->
        val r = idx / 4; val c = idx % 4; b[c * 4 + r]
    }

    private fun mirrorH(b: IntArray): IntArray = IntArray(16) { idx ->
        val r = idx / 4; val c = idx % 4; b[r * 4 + (3 - c)]
    }
}
