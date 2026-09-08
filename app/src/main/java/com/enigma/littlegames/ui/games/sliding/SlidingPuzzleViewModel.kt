package com.enigma.littlegames.ui.games.sliding

// ─────────────────────────────────────────────────────────────────────────────
// Sliding Puzzle — ViewModel
// Classic 15-puzzle (4×4) or 8-puzzle (3×3). Slide numbered tiles into order.
// Scramble via random valid moves (always solvable). A* hint for one-step.
//
// Bug fix (audit #8 — medium):
// This file used to declare TWO competing size enums — a dead `SlidingSize`
// (THREE/FOUR/FIVE) that nothing referenced, and the actually-used
// `SlideGrid` (EASY/MEDIUM/HARD/EXPERT). The dead enum was pure noise for
// anyone reading the file and risked someone "fixing" the wrong one later.
// Removed `SlidingSize` entirely; `SlideGrid` (with its label/scrambleMoves/
// emoji) remains the single source of truth for sizing + difficulty.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class SlideGrid(val size: Int, val label: String, val scrambleMoves: Int, val emoji: String) {
    EASY  (3, "Easy",   25,  "🌿"),
    MEDIUM(4, "Medium", 80,  "⚡"),
    HARD  (4, "Hard",   200, "🔥"),
    EXPERT(5, "Expert", 400, "💀"),
}

data class SlidingState(
    val tiles: IntArray        = IntArray(0),   // 0 = blank
    val size: Int              = 4,
    val moves: Int             = 0,
    val isComplete: Boolean    = false,
    val difficulty: SlideGrid  = SlideGrid.MEDIUM,
    val elapsedSecs: Long      = 0L,
    val generating: Boolean    = true,
    val hintBlank: Int         = -1,            // index the blank should move to
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SlidingState

        if (size != other.size) return false
        if (moves != other.moves) return false
        if (isComplete != other.isComplete) return false
        if (elapsedSecs != other.elapsedSecs) return false
        if (generating != other.generating) return false
        if (hintBlank != other.hintBlank) return false
        if (!tiles.contentEquals(other.tiles)) return false
        if (difficulty != other.difficulty) return false

        return true
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + moves
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + elapsedSecs.hashCode()
        result = 31 * result + generating.hashCode()
        result = 31 * result + hintBlank
        result = 31 * result + tiles.contentHashCode()
        result = 31 * result + difficulty.hashCode()
        return result
    }
}

class SlidingPuzzleViewModel : ViewModel() {
    private val _state = MutableStateFlow(SlidingState())
    val state: StateFlow<SlidingState> = _state.asStateFlow()
    private var timerJob: Job? = null

    init { newGame(SlideGrid.MEDIUM) }

    fun newGame(d: SlideGrid) {
        timerJob?.cancel()
        _state.update { it.copy(generating = true, difficulty = d, moves = 0, isComplete = false, elapsedSecs = 0, hintBlank = -1) }
        viewModelScope.launch(Dispatchers.Default) {
            val tiles = scramble(d.size, d.scrambleMoves)
            _state.update { it.copy(tiles = tiles, size = d.size, generating = false) }
        }
        startTimer()
    }

    // Slide tile adjacent to blank
    fun slideTile(tileIdx: Int) {
        val s = _state.value
        if (s.isComplete) return
        val blankIdx = s.tiles.indexOf(0)
        if (!areAdjacent(tileIdx, blankIdx, s.size)) return
        val newTiles = s.tiles.copyOf()
        newTiles[blankIdx] = newTiles[tileIdx]
        newTiles[tileIdx] = 0
        val done = isSolved(newTiles)
        _state.update { it.copy(tiles = newTiles, moves = it.moves + 1, isComplete = done, hintBlank = -1) }
    }

    fun hint() {
        val s = _state.value
        // Simple hint: find the nearest out-of-place tile and move blank toward it
        val blankIdx = s.tiles.indexOf(0)
        val blankR   = blankIdx / s.size
        val blankC   = blankIdx % s.size
        // Suggest slide that moves blank toward solving the first wrong tile
        for (idx in s.tiles.indices) {
            val v = s.tiles[idx]
            if (v == 0) continue
            val targetIdx = v - 1   // solved position of this tile
            if (idx != targetIdx) {
                // Can we move blank to be adjacent to this tile?
                val adjToTile = neighbours(idx, s.size).firstOrNull { neighbour ->
                    neighbour == blankIdx ||
                    neighbours(neighbour, s.size).contains(blankIdx)
                }
                if (adjToTile != null) {
                    _state.update { it.copy(hintBlank = adjToTile) }
                    return
                }
            }
        }
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (!_state.value.isComplete) _state.update { it.copy(elapsedSecs = it.elapsedSecs + 1) }
            }
        }
    }

    override fun onCleared() { timerJob?.cancel() }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Generate solvable scramble by making N random moves from solved state. */
private fun scramble(n: Int, moves: Int): IntArray {
    val tiles = IntArray(n * n) { it }  // solved: [0, 1, 2, …, n²-1]  (0 = blank)
    var blankIdx = 0
    var lastMove = -1
    repeat(moves) {
        val nbrs = neighbours(blankIdx, n).filter { it != lastMove }
        val next = nbrs.random()
        tiles[blankIdx] = tiles[next]
        tiles[next]     = 0
        lastMove = blankIdx
        blankIdx = next
    }
    return tiles
}

private fun neighbours(idx: Int, n: Int): List<Int> {
    val r = idx / n; val c = idx % n
    return buildList {
        if (r > 0)     add(idx - n)
        if (r < n - 1) add(idx + n)
        if (c > 0)     add(idx - 1)
        if (c < n - 1) add(idx + 1)
    }
}

private fun areAdjacent(a: Int, b: Int, n: Int): Boolean = b in neighbours(a, n)

private fun isSolved(tiles: IntArray): Boolean {
    for (i in tiles.indices) if (tiles[i] != i) return false
    return true
}
