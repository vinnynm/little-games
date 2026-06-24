package com.enigma.littlegames.ui.games.explodingkittens.ui

// ─────────────────────────────────────────────────────────────────────────────
// Exploding Kittens — fully integrated into the Enigma Game Hub theme system.
// Replaces all MaterialTheme colour references with LocalGameTheme.
// Card images use emoji rendering (no drawable assets required).
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.common.*
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.rememberParticleSystem
import com.enigma.littlegames.ui.games.explodingkittens.data.Card
import com.enigma.littlegames.ui.games.explodingkittens.data.Player
import com.enigma.littlegames.ui.games.explodingkittens.data.states.GameMode
import com.enigma.littlegames.ui.games.explodingkittens.data.states.GameState
import com.enigma.littlegames.ui.games.explodingkittens.data.states.GameUiState
import com.enigma.littlegames.ui.games.explodingkittens.data.types.AIDifficulty
import com.enigma.littlegames.ui.games.explodingkittens.data.types.CardType
import com.enigma.littlegames.ui.games.explodingkittens.data.types.PlayerType
import com.enigma.littlegames.ui.games.explodingkittens.game.EKViewModel

@Composable
fun ExplodingKittensScreen(hub: HubViewModel) {
    val t         = LocalGameTheme.current
    val vm: EKViewModel = viewModel()
    val state     by vm.uiState.collectAsStateWithLifecycle()
    val particles = rememberParticleSystem()

    // Wire win callback
    LaunchedEffect(Unit) {
        vm.onWin = { winnerName, isHuman ->
            hub.recordEKWin(winnerName, vsAI = state.gameMode == GameMode.SINGLE_PLAYER)
            hub.sound.play(Sfx.VICTORY)
        }
    }

    Box(
        Modifier.fillMaxSize().background(t.background)
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                title    = "EXPLODING KITTENS",
                subtitle = "💣 Don't draw the kitten!",
                onBack   = { if (state.gameState == GameState.PLAYING) vm.onExitGame() else hub.navigate(HubScreen.Home) },
            )

            when (state.gameState) {
                GameState.MENU     -> EKMenuScreen(state, vm::onGameModeSelected, vm::onShowTutorial, vm::onResumeGame)
                GameState.SETUP    -> EKSetupScreen(state, vm::onPlayerCountChange, vm::onAIDifficultyChange, vm::onStartGame, vm::onBackToMenu)
                GameState.LOBBY    -> EKLobbyScreen(state, vm::onHostIpChange, vm::onStartHost, vm::onJoinHost, vm::onStartGame, vm::onBackToMenu)
                GameState.TUTORIAL -> EKTutorialScreen(vm::onBackToMenu)
                GameState.PLAYING,
                GameState.NOPE_CHANCE,
                GameState.STEAL_CARD -> {
                    EKGameplayScreen(state, vm::onPlayCard, vm::onEndTurnAndDraw, vm::onCloseFuture)
                }
                GameState.AWAITING_KITTEN_PLACEMENT -> {
                    EKGameplayScreen(state, vm::onPlayCard, vm::onEndTurnAndDraw, vm::onCloseFuture)
                    EKPlaceKittenDialog(state.deckSize, vm::onKittenPlaced)
                }
                GameState.HANDOFF  -> EKHandoffScreen(state, vm::onHandoffConfirmed)
                GameState.GAME_OVER -> EKGameOverScreen(state.winner, vm::onBackToMenu)
            }
        }

        ParticleOverlay(particles)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Menu screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EKMenuScreen(
    state: GameUiState,
    onMode: (GameMode) -> Unit,
    onTutorial: () -> Unit,
    onResume: () -> Unit,
) {
    val t = LocalGameTheme.current
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("💣", fontSize = 64.sp)
        Text("EXPLODING KITTENS", color = t.primary, fontSize = 20.sp,
            fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Don't draw the kitten — last player alive wins!",
            color = t.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp))

        Spacer(Modifier.height(8.dp))

        if (state.hasSavedGame) {
            ThemedButton("▶  Resume Game", onResume, Modifier.fillMaxWidth())
        }
        ThemedButton("👤  Single Player vs AI",   { onMode(GameMode.SINGLE_PLAYER) }, Modifier.fillMaxWidth())
        ThemedButton("👥  Pass & Play",            { onMode(GameMode.PASS_AND_PLAY) }, Modifier.fillMaxWidth())
        ThemedButton("📡  Host WiFi Game",         { onMode(GameMode.NETWORK_HOST) },  Modifier.fillMaxWidth(), outlined = true)
        ThemedButton("📶  Join WiFi Game",         { onMode(GameMode.NETWORK_JOIN) },  Modifier.fillMaxWidth(), outlined = true)
        ThemedButton("📖  How to Play",            onTutorial,                         Modifier.fillMaxWidth(), outlined = true)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EKSetupScreen(
    state: GameUiState,
    onCount: (Int) -> Unit,
    onDifficulty: (AIDifficulty) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalGameTheme.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Player count
        Surface(color = t.surface, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (state.gameMode == GameMode.SINGLE_PLAYER) "Total Players (You + AI)" else "Number of Players",
                    color = t.textSecondary, fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ThemedButton("−", { onCount(state.playerCount - 1) },
                        enabled = state.playerCount > 2, outlined = true)
                    Text("${state.playerCount}", color = t.primary, fontSize = 28.sp,
                        fontWeight = FontWeight.Black)
                    ThemedButton("+", { onCount(state.playerCount + 1) },
                        enabled = state.playerCount < 5, outlined = true)
                }
            }
        }

        // AI difficulty
        if (state.gameMode == GameMode.SINGLE_PLAYER) {
            Surface(color = t.surface, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI Difficulty", color = t.textSecondary, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AIDifficulty.entries.forEach { d ->
                            val sel = state.aiDifficulty == d
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) t.primary else t.surfaceVariant)
                                    .border(1.dp, if (sel) t.primary else t.border, RoundedCornerShape(8.dp))
                                    .clickable { onDifficulty(d) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d.name.take(1) + d.name.drop(1).lowercase(),
                                    color = if (sel) t.background else t.textSecondary,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        when (state.aiDifficulty) {
                            AIDifficulty.EASY   -> "🟢 Easy — random plays, good for learning"
                            AIDifficulty.MEDIUM -> "🟡 Medium — some strategy, balanced"
                            AIDifficulty.HARD   -> "🔴 Hard — advanced AI, challenging"
                        },
                        color = t.textSecondary, fontSize = 11.sp
                    )
                }
            }
        }

        ThemedButton("Start Game", onStart, Modifier.fillMaxWidth())
        ThemedButton("Back", onBack, Modifier.fillMaxWidth(), outlined = true)
        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lobby screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EKLobbyScreen(
    state: GameUiState,
    onIpChange: (String) -> Unit,
    onStartHost: () -> Unit,
    onJoinHost: (String) -> Unit,
    onStartGame: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalGameTheme.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.connectionStatus.isNotBlank()) {
            Text(state.connectionStatus, color = t.primary, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        if (state.gameMode == GameMode.NETWORK_HOST) {
            Surface(color = t.surface, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your IP:", color = t.textSecondary, fontSize = 12.sp)
                    Text(state.localIP, color = t.primary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    ThemedButton("Start Hosting", onStartHost, Modifier.fillMaxWidth())
                    Text("Connected: ${state.connectedPlayers.size + 1} player(s)",
                        color = t.textSecondary, fontSize = 12.sp)
                    ThemedButton("Start Game", onStartGame, Modifier.fillMaxWidth(),
                        enabled = state.connectedPlayers.isNotEmpty())
                }
            }
        } else {
            Surface(color = t.surface, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.hostIP, onValueChange = onIpChange,
                        label = { Text("Host IP Address") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = t.primary, unfocusedBorderColor = t.border,
                            focusedLabelColor = t.primary, cursorColor = t.primary,
                        )
                    )
                    ThemedButton("Connect", { onJoinHost(state.hostIP) }, Modifier.fillMaxWidth())
                    Text("Waiting for host to start…", color = t.textSecondary, fontSize = 11.sp)
                }
            }
        }
        ThemedButton("Back", onBack, Modifier.fillMaxWidth(), outlined = true)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Gameplay screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EKGameplayScreen(
    state: GameUiState,
    onPlayCard: (Card) -> Unit,
    onDraw: () -> Unit,
    onCloseFuture: () -> Unit,
) {
    val t = LocalGameTheme.current

    if (state.players.isEmpty() || state.currentPlayerIndex >= state.players.size) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = t.primary)
        }
        return
    }

    val cur = state.players[state.currentPlayerIndex]

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {

        // Player status bar
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(state.players) { player ->
                EKPlayerChip(player, isCurrent = player.id == cur.id, t)
            }
        }

        // Deck + discard area
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EKCardPile("DECK", "💣", "${state.deckSize}", t)
            EKCardPile("DISCARD", "🗂️",
                if (state.discardPile.isEmpty()) "—" else state.discardPile.last().name.take(10), t)
        }

        // Game message
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            color = t.surface, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, t.border)
        ) {
            Text(state.gameMessage, color = t.textPrimary, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp),
                fontWeight = FontWeight.Medium)
        }

        // Nope countdown
        if (state.gameState == GameState.NOPE_CHANCE) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚡ NOPE WINDOW: ${state.actionCountdown}s",
                    color = t.error, fontSize = 13.sp, fontWeight = FontWeight.Black)
                LinearProgressIndicator(
                    progress = { state.actionCountdown / 5f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(4.dp),
                    color = t.error, trackColor = t.border,
                )
            }
        }

        // Draw button
        ThemedButton(
            if (state.gameState == GameState.NOPE_CHANCE) "⚡ Play Nope from hand ↓" else "End Turn & Draw Card",
            onDraw,
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            enabled = cur.type == PlayerType.HUMAN,
        )

        // Hand
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(t.surface)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val label = if (state.gameState == GameState.NOPE_CHANCE) "QUICK — PLAY A NOPE?" else "Your Hand (${cur.hand.size})"
            Text(label,
                color = if (state.gameState == GameState.NOPE_CHANCE) t.error else t.textSecondary,
                fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp))

            if (cur.isAlive) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cur.hand) { card ->
                        val isNope   = card.type == CardType.NOPE
                        val canPlay  = if (state.gameState == GameState.NOPE_CHANCE) isNope
                                       else card.type !in listOf(CardType.EXPLODING_KITTEN, CardType.DEFUSE)
                        EKCardView(card, canPlay && cur.type == PlayerType.HUMAN) { onPlayCard(card) }
                    }
                }
            }
        }
    }

    // See the Future dialog
    if (state.showFutureCards) {
        AlertDialog(
            onDismissRequest = onCloseFuture,
            containerColor   = t.surface,
            title = { Text("🔮 Next 3 Cards", color = t.primary, fontWeight = FontWeight.Black) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.futureCards.forEachIndexed { i, card ->
                        Surface(color = t.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", color = t.primary, fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(end = 10.dp))
                                Text(card.name, color = t.textPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                ThemedButton("Got it", onCloseFuture)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Place kitten dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EKPlaceKittenDialog(deckSize: Int, onPlace: (Int) -> Unit) {
    val t = LocalGameTheme.current
    var pos by remember { mutableFloatStateOf(0f) }
    val posInt = pos.toInt()
    AlertDialog(
        onDismissRequest = {},
        containerColor   = t.surface,
        title = { Text("💣 Hide the Kitten!", color = t.primary, fontWeight = FontWeight.Black) },
        text  = {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose where to place the Exploding Kitten back in the deck.",
                    color = t.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                if (deckSize > 0) {
                    Slider(value = pos, onValueChange = { pos = it },
                        valueRange = 0f..deckSize.toFloat(), steps = deckSize,
                        colors = SliderDefaults.colors(thumbColor = t.primary, activeTrackColor = t.primary))
                    Text(when (posInt) {
                        0        -> "Top of the deck"
                        deckSize -> "Bottom of the deck"
                        else     -> "Position $posInt from top"
                    }, color = t.primary, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { ThemedButton("Place Kitten 💣", { onPlace(posInt) }) }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Handoff screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EKHandoffScreen(state: GameUiState, onConfirm: () -> Unit) {
    val t    = LocalGameTheme.current
    val name = state.players.getOrNull(state.currentPlayerIndex)?.name ?: "next player"
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Pass the device to", color = t.textSecondary, fontSize = 14.sp)
        Text(name, color = t.primary, fontSize = 32.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(vertical = 16.dp))
        ThemedButton("I am $name — show my hand!", onConfirm, Modifier.fillMaxWidth(.8f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Game over screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EKGameOverScreen(winner: Player?, onReset: () -> Unit) {
    val t = LocalGameTheme.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text("🎉", fontSize = 64.sp)
        Text("GAME OVER", color = t.primary, fontSize = 22.sp, fontWeight = FontWeight.Black,
            letterSpacing = 4.sp)
        winner?.let {
            Text("${it.name} Wins!", color = t.success, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        } ?: Text("Everyone exploded!", color = t.error, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        ThemedButton("Play Again", onReset, Modifier.fillMaxWidth(.7f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tutorial screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EKTutorialScreen(onBack: () -> Unit) {
    val t = LocalGameTheme.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        listOf(
            "🎯 The Goal" to "Avoid drawing an Exploding Kitten. Last player alive wins.",
            "🃏 Your Turn" to "Play any cards from your hand, then end your turn by drawing from the deck.",
            "💣 Exploding Kitten" to "Draw this and you're out — unless you have a Defuse card.",
            "🛡️ Defuse" to "Cancels an Exploding Kitten. You then secretly place the kitten back anywhere in the deck.",
            "⚔️ Attack" to "End your turn without drawing. Force the next player to take two turns.",
            "⏭️ Skip" to "End your turn without drawing.",
            "🔮 See Future" to "Secretly peek at the top 3 cards of the deck.",
            "🔀 Shuffle" to "Shuffle the deck.",
            "🐱 Cat Cards" to "Play a matching pair to steal a random card from another player.",
            "🙅 Nope" to "Cancel any action card. Nopes can be Noped — odd stacks cancel, even stacks allow.",
        ).forEach { (title, text) ->
            Surface(color = t.surface, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = t.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text, color = t.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ThemedButton("Back to Menu", onBack, Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small reusable composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EKPlayerChip(player: Player, isCurrent: Boolean, t: GameTheme) {
    val bg     = if (!player.isAlive) t.error.copy(.15f)
                 else if (isCurrent) t.primary.copy(.18f)
                 else t.surface
    val border = if (isCurrent) t.primary else t.border

    Surface(color = bg, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, border)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (player.isAlive) "😸" else "💀", fontSize = 18.sp)
            Text(player.name.take(8),
                color = if (!player.isAlive) t.error else if (isCurrent) t.primary else t.textPrimary,
                fontSize = 10.sp, fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Normal)
            if (player.isAlive) {
                Text("${player.hand.size} cards", color = t.textSecondary, fontSize = 9.sp)
                if (player.turnsToTake > 1)
                    Text("×${player.turnsToTake}", color = t.error, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EKCardPile(label: String, emoji: String, value: String, t: GameTheme) {
    Surface(color = t.surface, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, t.border)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = t.textSecondary, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(emoji, fontSize = 28.sp)
            Text(value, color = t.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1)
        }
    }
}

@Composable
private fun EKCardView(card: Card, enabled: Boolean, onClick: () -> Unit) {
    val t = LocalGameTheme.current
    val cardColor = when (card.type) {
        CardType.EXPLODING_KITTEN -> Color(0xFFFF1744)
        CardType.DEFUSE           -> Color(0xFF00E676)
        CardType.SKIP             -> Color(0xFF2196F3)
        CardType.ATTACK           -> Color(0xFFFF5722)
        CardType.SEE_FUTURE       -> Color(0xFF9C27B0)
        CardType.SHUFFLE          -> Color(0xFFFF9800)
        CardType.NOPE             -> Color(0xFF607D8B)
        CardType.NORMAL           -> t.primary
    }
    val cardEmoji = when (card.type) {
        CardType.EXPLODING_KITTEN -> "💣"
        CardType.DEFUSE           -> "🛡️"
        CardType.SKIP             -> "⏭️"
        CardType.ATTACK           -> "⚔️"
        CardType.SEE_FUTURE       -> "🔮"
        CardType.SHUFFLE          -> "🔀"
        CardType.NOPE             -> "🙅"
        CardType.NORMAL           -> "🐱"
    }
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        color = cardColor.copy(alpha = 0.18f * alpha),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (enabled) 1.5.dp else 0.5.dp, cardColor.copy(alpha * if (enabled) 0.8f else 0.3f)),
        modifier = Modifier.size(80.dp, 110.dp).clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly) {
            Text(cardEmoji, fontSize = 26.sp)
            Text(card.name.removePrefix("💣 ").removePrefix("🛡️ ").removePrefix("⏭️ ")
                    .removePrefix("⚔️ ").removePrefix("🔮 ").removePrefix("🔀 ")
                    .removePrefix("🙅 ").removePrefix("🐱 "),
                color = if (enabled) cardColor else cardColor.copy(.4f),
                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp)
        }
    }
}
