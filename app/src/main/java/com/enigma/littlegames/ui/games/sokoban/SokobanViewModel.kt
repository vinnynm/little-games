package com.enigma.littlegames.ui.games.sokoban

// ─────────────────────────────────────────────────────────────────────────────
// Sokoban — ViewModel
// XSB parser, move engine with undo stack, win detection.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*

// ── Direction ─────────────────────────────────────────────────────────────────

enum class SokoDir(val dr: Int, val dc: Int, val label: String) {
    UP   (-1,  0, "↑"),
    DOWN ( 1,  0, "↓"),
    LEFT ( 0, -1, "←"),
    RIGHT( 0,  1, "→"),
}

// ── Board model ───────────────────────────────────────────────────────────────

data class SokobanBoard(
    val playerRow: Int,
    val playerCol: Int,
    val boxes: Set<Pair<Int, Int>>,
    val targets: Set<Pair<Int, Int>>,
    val walls: Set<Pair<Int, Int>>,
    val width: Int,
    val height: Int,
    val moveCount: Int,
    val pushCount: Int,
    // History for undo — each entry is (playerRow, playerCol, boxes, moveCount, pushCount)
    val history: List<Snapshot>,
) {
    val isComplete: Boolean get() = boxes == targets
}

data class Snapshot(
    val playerRow: Int,
    val playerCol: Int,
    val boxes: Set<Pair<Int, Int>>,
    val moveCount: Int,
    val pushCount: Int,
)

data class SokobanUiState(
    val board: SokobanBoard?     = null,
    val level: Int               = 1,
    val totalLevels: Int         = SOKOBAN_LEVELS.size,
    val isLoading: Boolean       = true,
    val isComplete: Boolean      = false,
    val bestMoves: Map<Int, Int> = emptyMap(),  // level → best move count
)

// ── XSB Parser ───────────────────────────────────────────────────────────────

fun parseLevel(xsb: String): SokobanBoard {
    val lines  = xsb.lines().filter { it.isNotBlank() }
    val width  = lines.maxOf { it.length }
    val height = lines.size
    val walls   = mutableSetOf<Pair<Int, Int>>()
    val boxes   = mutableSetOf<Pair<Int, Int>>()
    val targets = mutableSetOf<Pair<Int, Int>>()
    var playerRow = 0; var playerCol = 0

    lines.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch ->
            when (ch) {
                '#'  -> walls.add(r to c)
                '@'  -> { playerRow = r; playerCol = c }
                '+'  -> { playerRow = r; playerCol = c; targets.add(r to c) }
                '$'  -> boxes.add(r to c)
                '*'  -> { boxes.add(r to c); targets.add(r to c) }
                '.'  -> targets.add(r to c)
            }
        }
    }

    return SokobanBoard(playerRow, playerCol, boxes, targets, walls, width, height, 0, 0, emptyList())
}

// ── Move engine ───────────────────────────────────────────────────────────────

fun applyMove(board: SokobanBoard, dir: SokoDir): SokobanBoard {
    val nr = board.playerRow + dir.dr
    val nc = board.playerCol + dir.dc
    val dest = nr to nc

    if (dest in board.walls) return board  // wall — blocked

    if (dest in board.boxes) {
        // Try to push the box
        val br = nr + dir.dr
        val bc = nc + dir.dc
        val boxDest = br to bc
        if (boxDest in board.walls || boxDest in board.boxes) return board  // box can't move
        val snapshot = Snapshot(board.playerRow, board.playerCol, board.boxes, board.moveCount, board.pushCount)
        val newBoxes = board.boxes - dest + boxDest
        return board.copy(
            playerRow = nr, playerCol = nc,
            boxes     = newBoxes,
            moveCount = board.moveCount + 1,
            pushCount = board.pushCount + 1,
            history   = board.history + snapshot,
        )
    }

    // Empty cell — just move player
    val snapshot = Snapshot(board.playerRow, board.playerCol, board.boxes, board.moveCount, board.pushCount)
    return board.copy(
        playerRow = nr, playerCol = nc,
        moveCount = board.moveCount + 1,
        history   = board.history + snapshot,
    )
}

fun undoMove(board: SokobanBoard): SokobanBoard {
    val snap = board.history.lastOrNull() ?: return board
    return board.copy(
        playerRow = snap.playerRow,
        playerCol = snap.playerCol,
        boxes     = snap.boxes,
        moveCount = snap.moveCount,
        pushCount = snap.pushCount,
        history   = board.history.dropLast(1),
    )
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class SokobanViewModel : ViewModel() {
    private val _state = MutableStateFlow(SokobanUiState())
    val state: StateFlow<SokobanUiState> = _state.asStateFlow()

    // Keep original board per level for restart
    private var originalBoard: SokobanBoard? = null

    init { loadLevel(1) }

    fun loadLevel(lvl: Int) {
        val idx = (lvl - 1).coerceIn(0, SOKOBAN_LEVELS.size - 1)
        val board = parseLevel(SOKOBAN_LEVELS[idx])
        originalBoard = board
        _state.update { it.copy(board = board, level = lvl, isLoading = false, isComplete = false) }
    }

    fun move(dir: SokoDir) {
        val s = _state.value
        val b = s.board ?: return
        if (s.isComplete) return
        val newBoard = applyMove(b, dir)
        if (newBoard === b) return  // no change
        val complete = newBoard.isComplete
        if (complete) {
            val newBest = s.bestMoves.toMutableMap()
            val prev    = newBest[s.level]
            if (prev == null || newBoard.moveCount < prev) newBest[s.level] = newBoard.moveCount
            _state.update { it.copy(board = newBoard, isComplete = true, bestMoves = newBest) }
        } else {
            _state.update { it.copy(board = newBoard) }
        }
    }

    fun undo() {
        val b = _state.value.board ?: return
        _state.update { it.copy(board = undoMove(b), isComplete = false) }
    }

    fun restart() {
        val orig = originalBoard ?: return
        _state.update { it.copy(board = orig, isComplete = false) }
    }

    fun nextLevel() {
        val next = (_state.value.level + 1).coerceAtMost(SOKOBAN_LEVELS.size)
        loadLevel(next)
    }

    fun prevLevel() {
        val prev = (_state.value.level - 1).coerceAtLeast(1)
        loadLevel(prev)
    }
}
