package com.enigma.littlegames.ui.games.explodingkittens.data

import com.enigma.littlegames.ui.games.explodingkittens.data.types.CardType
import kotlinx.serialization.Serializable

@Serializable
data class Card(
    val id: String = java.util.UUID.randomUUID().toString(),
    val suit: String,
    val rank: String,
    val type: CardType,
    val name: String,
    // imageId is intentionally nullable — Phase 4a uses emoji rendering
    // instead of drawables so the EK assets don't need to be copied.
    val imageId: Int? = null,
)
