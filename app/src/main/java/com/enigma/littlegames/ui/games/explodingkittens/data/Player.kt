package com.enigma.littlegames.ui.games.explodingkittens.data

import com.enigma.littlegames.ui.games.explodingkittens.data.types.AIDifficulty
import com.enigma.littlegames.ui.games.explodingkittens.data.types.PlayerType
import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: Int,
    val name: String,
    val hand: MutableList<Card>,
    val isAlive: Boolean = true,
    val type: PlayerType = PlayerType.HUMAN,
    val aiDifficulty: AIDifficulty = AIDifficulty.MEDIUM,
    val turnsToTake: Int = 1,
)
