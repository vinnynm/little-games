package com.enigma.littlegames.ui.games.explodingkittens.game.gamelogic

import com.enigma.littlegames.ui.games.explodingkittens.data.Card
import kotlinx.serialization.Serializable

@Serializable
data class GameAction(
    val actionType: String,   // "PLAY_CARD" | "END_TURN"
    val card: Card? = null,
    val playerId: Int,
)
