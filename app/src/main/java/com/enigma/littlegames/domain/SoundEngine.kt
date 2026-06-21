package com.enigma.littlegames.domain

// ─────────────────────────────────────────────────────────────────────────────
// Phase 2 · Sound Engine
// Wraps SoundPool for short SFX + MediaPlayer for optional ambient track.
// All calls are safe to make from the main thread; pool operations are fast.
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.enigma.littlegames.R

// IDs for each sound effect
enum class Sfx { TAP, ROTATE, FLOW_CHECK, FLOW_SUCCESS, FLOW_FAIL, SUDOKU_PLACE, SUDOKU_ERROR, ACHIEVEMENT, VICTORY }

class SoundEngine(context: Context) {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(attributes)
        .build()

    // Map Sfx enum → SoundPool sound ID
    // Replace R.raw.* references with your actual raw audio file names.
    private val soundIds: Map<Sfx, Int> = mapOf(
        Sfx.TAP             to pool.load(context, R.raw.sfx_tap,             1),
        Sfx.ROTATE          to pool.load(context, R.raw.sfx_rotate,          1),
        Sfx.FLOW_CHECK      to pool.load(context, R.raw.sfx_flow_check,      1),
        Sfx.FLOW_SUCCESS    to pool.load(context, R.raw.sfx_flow_success,    1),
        Sfx.FLOW_FAIL       to pool.load(context, R.raw.sfx_flow_fail,       1),
        Sfx.SUDOKU_PLACE    to pool.load(context, R.raw.sfx_sudoku_place,    1),
        Sfx.SUDOKU_ERROR    to pool.load(context, R.raw.sfx_sudoku_error,    1),
        Sfx.ACHIEVEMENT     to pool.load(context, R.raw.sfx_achievement,     1),
        Sfx.VICTORY         to pool.load(context, R.raw.sfx_victory,         1),
    )

    // Optional ambient MediaPlayer (looping background track)
    private var ambientPlayer: MediaPlayer? = null
    private var currentAmbientRes: Int? = null

    var enabled: Boolean = true

    /** Play a one-shot SFX at full volume. */
    fun play(sfx: Sfx, volume: Float = 0.85f) {
        if (!enabled) return
        soundIds[sfx]?.let { id ->
            pool.play(id, volume, volume, 1, 0, 1f)
        }
    }

    /**
     * Start looping an ambient track from res/raw.
     * Calling with the same resId while already playing is a no-op.
     */
    fun startAmbient(context: Context, resId: Int, volume: Float = 0.25f) {
        if (!enabled || currentAmbientRes == resId) return
        stopAmbient()
        ambientPlayer = MediaPlayer.create(context, resId)?.apply {
            isLooping = true
            setVolume(volume, volume)
            start()
        }
        currentAmbientRes = resId
    }

    fun stopAmbient() {
        ambientPlayer?.stop()
        ambientPlayer?.release()
        ambientPlayer = null
        currentAmbientRes = null
    }

    fun setAmbientVolume(v: Float) {
        ambientPlayer?.setVolume(v, v)
    }

    fun release() {
        stopAmbient()
        pool.release()
    }
}

// ── Per-theme ambient track mapping ──────────────────────────────────────────
// Map themeId → ambient raw resource. Add your actual .ogg / .mp3 files to res/raw/.

object ThemeAmbient {
    fun resForTheme(themeId: String): Int = when (themeId) {
        "lava"  -> R.raw.ambient_lava
        "bio"   -> R.raw.ambient_bio
        "ice"   -> R.raw.ambient_ice
        "gold"  -> R.raw.ambient_gold
        else    -> R.raw.ambient_cyber   // default / "cyber"
    }
}
