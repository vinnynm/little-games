package com.enigma.littlegames.ui.games.simon

// ─────────────────────────────────────────────────────────────────────────────
// Simon Says — ViewModel
// A growing-sequence memory game that reuses the Lights Out 5×5 cell grid.
// Three modes: Classic (5×5, 25 cells), Mini (3×3, 9 cells), Speed (5×5 faster).
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

enum class SimonMode(val label: String, val emoji: String, val gridSize: Int) {
    MINI   ("Mini",    "🟢", 3),
    CLASSIC("Classic", "⚡", 5),
    SPEED  ("Speed",   "🔥", 5),
}

enum class SimonPhase {
    IDLE,       // before first game
    SHOWING,    // playing back the sequence
    PLAYER_TURN,
    LEVEL_CLEAR,
    FAILED,
}

data class SimonState(
    val mode: SimonMode            = SimonMode.CLASSIC,
    val sequence: List<Int>        = emptyList(),
    val playerInput: List<Int>     = emptyList(),
    val phase: SimonPhase          = SimonPhase.IDLE,
    val currentHighlight: Int?     = null,   // cell index lit during SHOWING
    val score: Int                 = 0,
    val highScore: Int             = 0,
    val showingIndex: Int          = 0,      // which step we're showing
)

class SimonViewModel : ViewModel() {

    private val _state = MutableStateFlow(SimonState())
    val state: StateFlow<SimonState> = _state.asStateFlow()

    private var showJob: Job? = null

    // Callback for hub win reporting
    var onHighScore: ((score: Int, mode: SimonMode) -> Unit)? = null

    fun setMode(mode: SimonMode) {
        showJob?.cancel()
        _state.update { SimonState(mode = mode) }
    }

    fun startGame() {
        showJob?.cancel()
        val cells = _state.value.mode.gridSize * _state.value.mode.gridSize
        val first = Random.nextInt(cells)
        _state.update { it.copy(
            sequence    = listOf(first),
            playerInput = emptyList(),
            score       = 0,
            phase       = SimonPhase.IDLE,
        )}
        showSequence()
    }

    private fun showSequence() {
        val mode = _state.value.mode
        // Flash duration scales down with score (speed mode always fast)
        val baseDuration = when (mode) {
            SimonMode.SPEED -> 350L
            else            -> 600L
        }
        val flashDuration = maxOf(200L, baseDuration - (_state.value.score * 15L))
        val pauseDuration = maxOf(80L,  200L - (_state.value.score * 5L))

        showJob = viewModelScope.launch {
            _state.update { it.copy(phase = SimonPhase.SHOWING, currentHighlight = null) }
            delay(400)

            _state.value.sequence.forEachIndexed { idx, cell ->
                _state.update { it.copy(currentHighlight = cell, showingIndex = idx) }
                delay(flashDuration)
                _state.update { it.copy(currentHighlight = null) }
                delay(pauseDuration)
            }
            _state.update { it.copy(phase = SimonPhase.PLAYER_TURN, playerInput = emptyList()) }
        }
    }

    fun onCellTap(cellIdx: Int) {
        val s = _state.value
        if (s.phase != SimonPhase.PLAYER_TURN) return

        val expected = s.sequence[s.playerInput.size]

        if (cellIdx != expected) {
            // Wrong tap — game over
            val newHigh = maxOf(s.score, s.highScore)
            if (s.score > 0 && s.score >= s.highScore) {
                onHighScore?.invoke(s.score, s.mode)
            }
            _state.update { it.copy(phase = SimonPhase.FAILED, highScore = newHigh) }
            return
        }

        val newInput = s.playerInput + cellIdx

        if (newInput.size == s.sequence.size) {
            // Completed the round — add one more cell and start next round
            val cells   = s.mode.gridSize * s.mode.gridSize
            val newSeq  = s.sequence + Random.nextInt(cells)
            val newScore = newSeq.size - 1
            _state.update { it.copy(
                sequence    = newSeq,
                playerInput = emptyList(),
                phase       = SimonPhase.LEVEL_CLEAR,
                score       = newScore,
            )}
            viewModelScope.launch {
                delay(600)
                showSequence()
            }
        } else {
            _state.update { it.copy(playerInput = newInput) }
        }
    }

    /** Show the sequence again after a failure (replay mode). */
    fun replay() { showSequence() }
}
