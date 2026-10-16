package com.khongvinh.carsurvival

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sound effects synthesised at start-up (no audio assets): each effect is rendered
 * to a small WAV file in the cache dir and played through a [SoundPool].
 */
class Sfx(context: Context) {
    enum class S { COIN, POWER, HIT, EXPLODE, SHOOT, BOW, CATAPULT, BOOM, BEEP, GO, WIN, STAGE, LOSE, STORM, CLONE, CLICK, SPLAT, WOOD, BOOST, LAND }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = IntArray(S.entries.size)

    @Volatile
    var enabled = true

    init {
        val dir = context.cacheDir
        Thread {
            for (s in S.entries) {
                try {
                    val f = File(dir, "sfx_${s.name.lowercase()}.wav")
                    writeWav(f, synth(s))
                    ids[s.ordinal] = pool.load(f.absolutePath, 1)
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    fun play(s: S, volume: Float = 1f) {
        if (!enabled) return
        val id = ids[s.ordinal]
        if (id != 0) pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() = pool.release()

    // ------------------------------------------------------------- synthesis

    private val rate = 22050

    private class Buf(n: Int) {
        val d = FloatArray(n)
    }

    private fun buf(seconds: Float) = Buf((seconds * rate).toInt())

    /** Adds a frequency sweep with a short attack and exponential decay. */
    private fun tone(
        b: Buf,
        start: Float,
        dur: Float,
        f0: Float,
        f1: Float,
        vol: Float,
        square: Boolean = false,
        decay: Float = 4f,
    ) {
        val s0 = (start * rate).toInt()
        val n = (dur * rate).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= b.d.size) break
            val t = i.toFloat() / n
            val f = f0 + (f1 - f0) * t
            phase += 2 * PI * f / rate
            val raw = sin(phase).toFloat()
            val w = if (square) (if (raw >= 0f) 0.6f else -0.6f) else raw
            val env = min1(i / (0.005f * rate)) * exp(-decay * t)
            b.d[idx] += w * env * vol
        }
    }

    /** Adds low-passed white noise (explosions, crashes). */
    private fun noise(b: Buf, start: Float, dur: Float, vol: Float, smooth: Float, decay: Float = 5f) {
        val s0 = (start * rate).toInt()
        val n = (dur * rate).toInt()
        var y = 0f
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= b.d.size) break
            val t = i.toFloat() / n
            y += (Random.nextFloat() * 2f - 1f - y) * smooth
            b.d[idx] += y * vol * exp(-decay * t)
        }
    }

    private fun min1(v: Float) = if (v > 1f) 1f else v

    private fun synth(s: S): Buf = when (s) {
        S.COIN -> buf(0.18f).also {
            tone(it, 0f, 0.06f, 988f, 988f, 0.5f, true, 1f)
            tone(it, 0.06f, 0.12f, 1319f, 1319f, 0.5f, true, 3f)
        }
        S.POWER -> buf(0.35f).also {
            tone(it, 0f, 0.35f, 300f, 1200f, 0.5f, true, 2f)
        }
        S.HIT -> buf(0.35f).also {
            noise(it, 0f, 0.35f, 0.9f, 0.35f, 6f)
            tone(it, 0f, 0.2f, 160f, 60f, 0.6f, false, 5f)
        }
        S.EXPLODE -> buf(0.6f).also {
            noise(it, 0f, 0.6f, 1.0f, 0.12f, 4f)
            tone(it, 0f, 0.4f, 110f, 40f, 0.5f, false, 4f)
        }
        S.SHOOT -> buf(0.08f).also {
            noise(it, 0f, 0.08f, 0.5f, 0.6f, 8f)
            tone(it, 0f, 0.06f, 900f, 300f, 0.3f, true, 6f)
        }
        S.BOW -> buf(0.2f).also {
            tone(it, 0f, 0.2f, 420f, 180f, 0.6f, false, 6f)
            noise(it, 0f, 0.1f, 0.3f, 0.8f, 10f)
        }
        S.CATAPULT -> buf(0.3f).also {
            noise(it, 0f, 0.3f, 0.6f, 0.08f, 3f)
            tone(it, 0f, 0.25f, 200f, 120f, 0.4f, false, 4f)
        }
        S.BOOM -> buf(0.8f).also {
            noise(it, 0f, 0.8f, 1.0f, 0.07f, 3.5f)
            tone(it, 0f, 0.6f, 80f, 30f, 0.7f, false, 3f)
        }
        S.BEEP -> buf(0.15f).also { tone(it, 0f, 0.15f, 880f, 880f, 0.5f, true, 3f) }
        S.GO -> buf(0.4f).also { tone(it, 0f, 0.4f, 1320f, 1320f, 0.5f, true, 2f) }
        S.WIN -> buf(0.7f).also {
            val notes = floatArrayOf(523f, 659f, 784f, 1047f)
            notes.forEachIndexed { i, f -> tone(it, i * 0.12f, if (i == 3) 0.34f else 0.14f, f, f, 0.45f, true, 2f) }
        }
        S.STAGE -> buf(1.3f).also {
            val notes = floatArrayOf(523f, 523f, 784f, 784f, 880f, 1047f, 1319f)
            notes.forEachIndexed { i, f -> tone(it, i * 0.13f, if (i == 6) 0.5f else 0.15f, f, f, 0.45f, true, 1.5f) }
        }
        S.LOSE -> buf(1.0f).also {
            val notes = floatArrayOf(392f, 330f, 262f, 196f)
            notes.forEachIndexed { i, f -> tone(it, i * 0.2f, if (i == 3) 0.4f else 0.22f, f, f * 0.97f, 0.45f, true, 1.5f) }
        }
        S.STORM -> buf(1.2f).also {
            tone(it, 0f, 1.2f, 70f, 55f, 0.6f, false, 1.5f)
            tone(it, 0f, 1.2f, 140f, 110f, 0.3f, true, 2f)
            noise(it, 0f, 1.2f, 0.4f, 0.05f, 2f)
        }
        S.CLONE -> buf(0.45f).also {
            tone(it, 0f, 0.45f, 600f, 900f, 0.35f, false, 2f)
            tone(it, 0.05f, 0.4f, 900f, 1350f, 0.3f, false, 2f)
        }
        S.CLICK -> buf(0.05f).also { tone(it, 0f, 0.05f, 1500f, 1000f, 0.4f, true, 8f) }
        S.SPLAT -> buf(0.25f).also {
            noise(it, 0f, 0.25f, 0.9f, 0.18f, 7f)
            tone(it, 0f, 0.12f, 220f, 70f, 0.5f, false, 6f)
        }
        S.WOOD -> buf(0.25f).also {
            noise(it, 0f, 0.2f, 0.7f, 0.45f, 9f)
            tone(it, 0f, 0.15f, 260f, 180f, 0.5f, true, 7f)
        }
        S.BOOST -> buf(0.5f).also {
            noise(it, 0f, 0.5f, 0.7f, 0.25f, 1.5f)
            tone(it, 0f, 0.5f, 90f, 160f, 0.3f, true, 1.5f)
        }
        S.LAND -> buf(0.2f).also {
            noise(it, 0f, 0.2f, 0.6f, 0.06f, 6f)
            tone(it, 0f, 0.15f, 90f, 50f, 0.5f, false, 6f)
        }
    }

    private fun writeWav(f: File, b: Buf) {
        val n = b.d.size
        val bb = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(n * 2)
        for (v in b.d) bb.putShort((v.coerceIn(-1f, 1f) * 30000f).toInt().toShort())
        FileOutputStream(f).use { it.write(bb.array()) }
    }
}
