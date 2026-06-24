package com.enigma.littlegames.ui.games.explodingkittens.data.states

import kotlinx.serialization.Serializable

@Serializable
enum class GameState {
    MENU, SETUP, LOBBY, PLAYING, GAME_OVER, TUTORIAL,
    AWAITING_KITTEN_PLACEMENT, HANDOFF, NOPE_CHANCE, STEAL_CARD
}
