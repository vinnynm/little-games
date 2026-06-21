package com.enigma.littlegames.common

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI composables used across all game screens
// Includes: GameTopBar, ThemedButton, MiniStat, AchievementToast,
//           ParticleOverlay, SolvedBanner
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.enigma.littlegames.domain.Achievement
import com.enigma.littlegames.domain.ParticleSystem
import kotlinx.coroutines.delay

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
fun GameTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = LocalGameTheme.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = t.textPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = t.primary, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            if (subtitle != null) Text(subtitle, color = t.textSecondary, fontSize = 11.sp)
        }
        actions()
    }
}

// ── Themed button ─────────────────────────────────────────────────────────────

@Composable
fun ThemedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val t = LocalGameTheme.current
    if (outlined) {
        OutlinedButton(
            onClick, modifier, enabled = enabled,
            border = BorderStroke(1.dp, if (enabled) t.primary else t.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = t.primary)
        ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    } else {
        Button(
            onClick, modifier, enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = t.background),
            shape = RoundedCornerShape(10.dp)
        ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}

// ── Mini stat tile ────────────────────────────────────────────────────────────

@Composable
fun MiniStat(label: String, value: String) {
    val t = LocalGameTheme.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = t.primary, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label, color = t.textSecondary, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

// ── Achievement toast ─────────────────────────────────────────────────────────

@Composable
fun AchievementToast(
    achievement: Achievement?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalGameTheme.current

    LaunchedEffect(achievement) {
        if (achievement != null) {
            delay(3000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = achievement != null,
        enter   = slideInVertically { -it } + fadeIn(),
        exit    = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)
    ) {
        if (achievement != null) {
            Surface(
                color  = t.warning.copy(alpha = 0.15f),
                shape  = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, t.warning.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(achievement.emoji, fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Achievement Unlocked!", color = t.warning, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(achievement.title, color = t.textPrimary, fontSize = 14.sp,
                            fontWeight = FontWeight.Black)
                        Text(achievement.description, color = t.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ── Particle overlay ──────────────────────────────────────────────────────────

/**
 * Overlay a particle Canvas on top of any game content.
 * The [system] should be provided by rememberParticleSystem() in the parent.
 */
@Composable
fun ParticleOverlay(system: ParticleSystem, modifier: Modifier = Modifier) {
    if (!system.hasParticles) return
    // Tick on every frame
    LaunchedEffect(Unit) {
        while (true) {
            system.tick()
            delay(16)   // ~60 fps
        }
    }
    Canvas(modifier.fillMaxSize()) {
        with(system) { draw() }
    }
}

// ── Solved/victory banner ─────────────────────────────────────────────────────

@Composable
fun SolvedBanner(
    message: String,
    stars: Int = 3,
    subLabel: String = "",
    onNext: (() -> Unit)? = null,
    nextLabel: String = "Next Level ▶",
) {
    val t = LocalGameTheme.current
    val scale by animateFloatAsState(1f, spring(Spring.DampingRatioMediumBouncy), label = "banner_scale")

    Surface(
        Modifier.fillMaxWidth().scale(scale),
        color  = t.success.copy(alpha = 0.15f),
        shape  = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, t.success.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(message, color = t.success, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 2.sp)
            if (stars > 0) Text("⭐".repeat(stars) + "☆".repeat(3 - stars), fontSize = 24.sp, modifier = Modifier.padding(vertical = 6.dp))
            if (subLabel.isNotBlank()) Text(subLabel, color = t.textSecondary, fontSize = 12.sp)
            if (onNext != null) {
                Spacer(Modifier.height(12.dp))
                ThemedButton(nextLabel, onNext, Modifier.fillMaxWidth())
            }
        }
    }
}
