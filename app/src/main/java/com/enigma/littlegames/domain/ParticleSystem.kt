package com.enigma.littlegames.domain

// ─────────────────────────────────────────────────────────────────────────────
// Phase 2 · Particle system (pure Compose Canvas, no external lib)
// Usage:
//   val particles = rememberParticleSystem()
//   particles.burst(center, color)          // call on solve/victory
//   Canvas(...) { particles.draw(this) }    // call every frame
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.*
import kotlin.random.Random

data class Particle(
    val origin: Offset,
    val velocity: Offset,       // px per ms
    val color: Color,
    val radius: Float,
    val lifetime: Long,         // ms total
    var age: Long = 0L,         // ms elapsed
    val rotSpeed: Float = 0f,   // rad/ms (for square particles)
    val shape: ParticleShape = ParticleShape.CIRCLE,
) {
    val alpha: Float get() = (1f - age.toFloat() / lifetime).coerceIn(0f, 1f)
    val isDead: Boolean get() = age >= lifetime
    fun position(dt: Long): Offset = Offset(
        origin.x + velocity.x * age,
        origin.y + velocity.y * age + 0.0002f * age * age   // gravity
    )
}

enum class ParticleShape { CIRCLE, SQUARE, STAR }

class ParticleSystem {
    private val _particles = mutableStateListOf<Particle>()
    val particles: List<Particle> get() = _particles

    private var lastTickMs: Long = System.currentTimeMillis()

    /**
     * Emit a burst of [count] particles from [center] using [colors].
     * Call this when a puzzle is solved.
     */
    fun burst(
        center: Offset,
        colors: List<Color>,
        count: Int = 40,
        speed: Float = 0.3f,     // max px/ms
        minLifetime: Long = 800,
        maxLifetime: Long = 1600,
    ) {
        repeat(count) {
            val angle  = Random.nextFloat() * 2 * PI.toFloat()
            val spd    = speed * (0.3f + Random.nextFloat() * 0.7f)
            val vx     = cos(angle) * spd
            val vy     = sin(angle) * spd - 0.1f  // slight upward bias
            val shape  = ParticleShape.values().random()
            _particles.add(
                Particle(
                    origin    = center,
                    velocity  = Offset(vx, vy),
                    color     = colors.random(),
                    radius    = 4f + Random.nextFloat() * 6f,
                    lifetime  = minLifetime + (Random.nextFloat() * (maxLifetime - minLifetime)).toLong(),
                    rotSpeed  = (Random.nextFloat() - 0.5f) * 0.005f,
                    shape     = shape,
                )
            )
        }
    }

    /** Emit a small ripple effect (e.g. on pipe rotate). */
    fun ripple(center: Offset, color: Color, count: Int = 8) {
        burst(center, listOf(color.copy(alpha = 0.7f)), count, speed = 0.15f, minLifetime = 400, maxLifetime = 700)
    }

    /** Tick all particles. Call this from a LaunchedEffect loop. */
    fun tick() {
        val now = System.currentTimeMillis()
        val dt  = (now - lastTickMs).coerceIn(0, 50)
        lastTickMs = now
        val dead = mutableListOf<Particle>()
        _particles.forEach { p ->
            p.age += dt
            if (p.isDead) dead.add(p)
        }
        _particles.removeAll(dead)
    }

    /** Draw all live particles onto the given DrawScope. */
    fun DrawScope.draw() {
        _particles.forEach { p ->
            val pos = p.position(p.age)
            val a   = p.alpha
            val c   = p.color.copy(alpha = a)
            when (p.shape) {
                ParticleShape.CIRCLE -> drawCircle(c, p.radius * a, pos)
                ParticleShape.SQUARE -> {
                    val r = p.radius * a
                    drawRect(c, topLeft = Offset(pos.x - r, pos.y - r),
                        size = Size(r * 2, r * 2)
                    )
                }
                ParticleShape.STAR   -> {
                    // Draw a simple 4-point star via two overlapping rects (rotated)
                    val r = p.radius * a
                    drawRect(c, topLeft = Offset(pos.x - r * 0.3f, pos.y - r),
                        size = Size(r * 0.6f, r * 2)
                    )
                    drawRect(c, topLeft = Offset(pos.x - r, pos.y - r * 0.3f),
                        size = Size(r * 2, r * 0.6f)
                    )
                }
            }
        }
    }

    fun clear() { _particles.clear() }
    val hasParticles: Boolean get() = _particles.isNotEmpty()
}

@Composable
fun rememberParticleSystem(): ParticleSystem = remember { ParticleSystem() }
