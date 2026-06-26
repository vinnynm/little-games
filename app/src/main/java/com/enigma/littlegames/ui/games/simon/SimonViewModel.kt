package com.enigma.littlegames.ui.games.simon

// ─────────────────────────────────────────────────────────────────────────────
// Simon Says — ViewModel
// Displays a growing sequence of flashing cells; player reproduces it.
// Grid reuses the same 5×5 layout as Lights Out.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class SimonPhase { IDLE, SHOWING, PLAYER_TURN, LEVEL_CLEAR, FAILED }

enum class SimonMode(val label: String, val gridSize: Int, val cellCount: Int) {
    MINI   ("Mini",    2, 4),
    CLASSIC("Classic", 5, 25),
}

data class SimonState(
    val sequence: List<Int>       = emptyList(),
    val playerInput: List<Int>    = emptyList(),
    val phase: SimonPhase         = SimonPhase.IDLE,
    val currentHighlight: Int?    = null,
    val score: Int                = 0,
    val highScore: Int            = 0,
    val mode: SimonMode           = SimonMode.CLASSIC,
    val flashDurationMs: Long     = 600L,
)

class SimonViewModel : ViewModel() {
    private val _state = MutableStateFlow(SimonState())
    val state: StateFlow<SimonState> = _state.asStateFlow()

    private var showJob: Job? = null

    fun setMode(mode: SimonMode) {
        showJob?.cancel()
        _state.update { SimonState(mode = mode, highScore = it.highScore) }
    }

    fun startGame() {
        val mode = _state.value.mode
        val first = (0 until mode.cellCount).random()
        _state.update { it.copy(sequence = listOf(first), playerInput = emptyList(), score = 0) }
        showSequence()
    }

    private fun showSequence() {
        showJob?.cancel()
        showJob = viewModelScope.launch {
            _state.update { it.copy(phase = SimonPhase.SHOWING, playerInput = emptyList()) }
            delay(400)
            val seq = _state.value.sequence
            val flashDuration = _state.value.flashDurationMs
            for (idx in seq) {
                _state.update { it.copy(currentHighlight = idx) }
                delay(flashDuration)
                _state.update { it.copy(currentHighlight = null) }
                delay(200)
            }
            _state.update { it.copy(phase = SimonPhase.PLAYER_TURN) }
        }
    }

    fun onTap(idx: Int) {
        val s = _state.value
        if (s.phase != SimonPhase.PLAYER_TURN) return

        val expected = s.sequence[s.playerInput.size]
        if (idx != expected) {
            val newHigh = maxOf(s.score, s.highScore)
            _state.update { it.copy(phase = SimonPhase.FAILED, highScore = newHigh) }
            return
        }

        val newInput = s.playerInput + idx
        if (newInput.size == s.sequence.size) {
            // Level complete — add next cell, increase speed slightly
            val newSeq = s.sequence + (0 until s.mode.cellCount).random()
            val newScore = newSeq.size - 1
            // Speed up every 5 levels, floor at 200ms
            val newFlash = maxOf(200L, 600L - (newScore / 5) * 80L)
            _state.update {
                it.copy(
                    sequence = newSeq,
                    playerInput = emptyList(),
                    phase = SimonPhase.LEVEL_CLEAR,
                    score = newScore,
                    flashDurationMs = newFlash,
                )
            }
            // Brief celebration delay then show next
            showJob = viewModelScope.launch {
                delay(800)
                showSequence()
            }
        } else {
            _state.update { it.copy(playerInput = newInput) }
        }
    }

    fun retry() {
        showJob?.cancel()
        _state.update { it.copy(
            sequence = emptyList(),
            playerInput = emptyList(),
            phase = SimonPhase.IDLE,
            currentHighlight = null,
            score = 0,
            flashDurationMs = 600L,
        )}
    }

    override fun onCleared() { showJob?.cancel() }
}
