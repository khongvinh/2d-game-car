package com.khongvinh.carsurvival

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.util.concurrent.CountDownLatch
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
    enum class S { COIN, POWER, HIT, EXPLODE, SHOOT, BOW, CATAPULT, BOOM, BEEP, GO, WIN, STAGE, LOSE, STORM, CLONE, CLICK, SPLAT, WOOD, BOOST, LAND, ROAR, ENGINE }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = IntArray(S.entries.size)

    /** A sample can only be played once SoundPool has finished decoding it. */
    private val loaded = BooleanArray(S.entries.size)
    private val lastPlay = LongArray(S.entries.size)
    private val loadedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    var enabled = true

    /** Slider volumes (0..1) for the effects and for the engine loop. */
    @Volatile
    var fxVol = 1f

    @Volatile
    var engineMaster = 1f

    /** Released once every effect has been generated and handed to SoundPool. */
    val ready = CountDownLatch(1)

    init {
        val dir = context.cacheDir
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                // may arrive before ids[] is filled in, so remember the sample id too
                loadedIds.add(sampleId)
                val i = ids.indexOf(sampleId)
                if (i >= 0) loaded[i] = true
            }
        }
        Thread {
            for (s in S.entries) {
                try {
                    // generated once and kept in the cache, so later launches are instant
                    val f = File(dir, "sfx_${VERSION}_${s.name.lowercase()}.wav")
                    if (!f.exists() || f.length() < 64) writeWav(f, synth(s))
                    ids[s.ordinal] = pool.load(f.absolutePath, 1)
                } catch (_: Exception) {
                }
            }
            ready.countDown()
        }.apply { priority = Thread.NORM_PRIORITY + 1 }.start()
    }

    fun play(s: S, volume0: Float = 1f) {
        val volume = (volume0 * fxVol).coerceIn(0f, 1f)
        if (!enabled || volume <= 0.005f) return
        val i = s.ordinal
        val id = ids[i]
        if (id == 0) return
        if (!loaded[i]) {
            if (!loadedIds.contains(id)) return
            loaded[i] = true
        }
        // rapid repeats of the same effect (gunfire, splats) are thinned out so they
        // never occupy every stream and starve the other effects
        val now = System.nanoTime()
        if (now - lastPlay[i] < MIN_GAP_NS[i]) return
        lastPlay[i] = now
        pool.play(id, volume, volume, PRIORITY[i], 0, 1f)
    }

    // ---------------------------------------------------------------- engine

    private var engineStream = 0
    private var engineRate = 0f
    private var engineVol = 0f

    /**
     * Keeps the looping engine sample running: [rate] is the pitch (0.5..2,
     * follows the revs) and [volume] grows with the throttle. Call with
     * [on] = false to silence it (menus, pause, wreck).
     */
    fun engine(on: Boolean, rate: Float = 1f, volume: Float = 0.3f) {
        if (!on || !enabled || engineMaster <= 0.005f) {
            stopEngine()
            return
        }
        val i = S.ENGINE.ordinal
        val id = ids[i]
        if (id == 0 || !(loaded[i] || loadedIds.contains(id))) return
        val r = rate.coerceIn(0.5f, 2f)
        val v = (volume * engineMaster).coerceIn(0f, 1f)
        if (engineStream == 0) {
            engineStream = pool.play(id, v, v, 3, -1, r)
            engineRate = r
            engineVol = v
            return
        }
        // only talk to the mixer when the change is audible
        if (kotlin.math.abs(r - engineRate) > 0.015f) {
            pool.setRate(engineStream, r)
            engineRate = r
        }
        if (kotlin.math.abs(v - engineVol) > 0.02f) {
            pool.setVolume(engineStream, v, v)
            engineVol = v
        }
    }

    fun stopEngine() {
        if (engineStream != 0) {
            pool.stop(engineStream)
            engineStream = 0
        }
    }

    private companion object {
        const val VERSION = 4
        val MIN_GAP_NS = LongArray(S.entries.size) { i ->
            when (S.entries[i]) {
                S.SHOOT -> 90_000_000L
                S.SPLAT, S.COIN, S.WOOD, S.LAND -> 45_000_000L
                else -> 25_000_000L
            }
        }
        val PRIORITY = IntArray(S.entries.size) { i ->
            when (S.entries[i]) {
                S.SHOOT, S.LAND -> 0
                S.BOOM, S.EXPLODE, S.HIT, S.ROAR, S.STAGE, S.LOSE, S.WIN, S.BEEP, S.STORM -> 2
                else -> 1
            }
        }
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
        S.ROAR -> buf(1.1f).also {
            noise(it, 0f, 1.1f, 0.8f, 0.1f, 2.2f)
            tone(it, 0f, 1.0f, 95f, 60f, 0.5f, true, 1.8f)
            tone(it, 0.05f, 0.9f, 140f, 80f, 0.35f, false, 2f)
        }
        S.ENGINE -> engineLoop()
        S.LAND -> buf(0.2f).also {
            noise(it, 0f, 0.2f, 0.6f, 0.06f, 6f)
            tone(it, 0f, 0.15f, 90f, 50f, 0.5f, false, 6f)
        }
    }

    /**
     * A seamless 0.4 s idle loop of a four-stroke engine: 50 Hz crank with two
     * firing pulses per turn, a little exhaust rasp, and noise that wraps around
     * so the loop point never clicks. SoundPool's rate raises the revs.
     */
    private fun engineLoop(): Buf {
        val b = buf(0.4f)
        val n = b.d.size
        val f0 = 50.0
        // circular low-passed noise for the rasp
        val raw = FloatArray(n) { Random.nextFloat() * 2f - 1f }
        val rasp = FloatArray(n)
        var y = 0f
        for (pass in 0..1) for (i in 0 until n) {
            y += (raw[i] - y) * 0.2f
            if (pass == 1) rasp[i] = y
        }
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val crank = 2 * PI * f0 * t
            // firing pulses: sharp attack, quick decay, twice per crank turn
            val ph = ((f0 * 2 * t) % 1.0).toFloat()
            val pulse = exp(-ph * 9f) * (1f - exp(-ph * 120f))
            var s = 0.55f * pulse * sin(2 * PI * f0 * 4 * t).toFloat()
            s += 0.35f * sin(crank).toFloat() + 0.22f * sin(2 * crank + 0.6).toFloat() + 0.12f * sin(3 * crank + 1.3).toFloat()
            s += 0.18f * rasp[i] * (0.4f + pulse)
            b.d[i] = s * 0.75f
        }
        return b
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
