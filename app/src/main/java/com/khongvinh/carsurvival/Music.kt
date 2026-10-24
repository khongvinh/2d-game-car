package com.khongvinh.carsurvival

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Background music, composed and synthesised in code at start-up (no audio assets):
 * a chill loop for menus and the start of a run, an intense loop for the dangerous
 * part, and a victory fanfare that flows into a cheering loop. Each track is a static
 * [AudioTrack] with loop points; switching tracks cross-fades.
 */
class Music(private val cacheDir: File? = null, private val startAfter: CountDownLatch? = null) {
    enum class Track { CHILL, INTENSE, VICTORY }

    @Volatile
    var enabled = true

    /** True while the app is in the background: everything is silent. */
    @Volatile
    var suspended = false

    private val tracks = arrayOfNulls<AudioTrack>(Track.entries.size)
    private val vol = FloatArray(Track.entries.size)
    private var want: Track? = null
    private var wantVol = 1f

    @Volatile
    private var ready = false

    init {
        Thread {
            try {
                // sound effects first: composing music must not delay them
                startAfter?.await(15, TimeUnit.SECONDS)
                for (t in Track.entries) {
                    val (pcm, loopStart) = loadOrCompose(t)
                    tracks[t.ordinal] = makeTrack(pcm, loopStart)
                }
                ready = true
            } catch (_: Throwable) {
                // no music on devices where the audio track cannot be created
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** Requests a track (or keeps the current one) at a relative volume. */
    fun play(t: Track, volume: Float) {
        if (want != t) {
            want = t
            if (t == Track.VICTORY) restart(t)
        }
        wantVol = volume
    }

    fun stop() {
        want = null
    }

    /** Silences everything at once (app going to the background). */
    fun suspend() {
        suspended = true
        for (i in tracks.indices) {
            vol[i] = 0f
            try {
                tracks[i]?.pause()
            } catch (_: IllegalStateException) {
            }
        }
    }

    /** Cross-fades towards the requested track; call once per frame. */
    fun update(dt: Float) {
        if (!ready) return
        for (t in Track.entries) {
            val track = tracks[t.ordinal] ?: continue
            val target = if (enabled && !suspended && t == want) wantVol * MASTER else 0f
            val v = vol[t.ordinal]
            val nv = if (v < target) min(target, v + dt * 0.8f) else max(target, v - dt * 0.8f)
            vol[t.ordinal] = nv
            try {
                if (nv > 0.001f) {
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                    track.setVolume(nv)
                } else if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    // the victory jingle starts from the top next time
                    if (t == Track.VICTORY) rewind(track)
                }
            } catch (_: IllegalStateException) {
            }
        }
    }

    private fun restart(t: Track) {
        val track = tracks[t.ordinal] ?: return
        try {
            track.pause()
            rewind(track)
        } catch (_: IllegalStateException) {
        }
    }

    private fun rewind(track: AudioTrack) {
        track.playbackHeadPosition = 0
    }

    fun release() {
        for (t in tracks) try {
            t?.release()
        } catch (_: Exception) {
        }
    }

    /** Composed music is cached as raw PCM so it is ready instantly on later launches. */
    private fun loadOrCompose(t: Track): Pair<ShortArray, Int> {
        val f = cacheDir?.let { File(it, "music_v1_${t.name.lowercase()}.pcm") }
        if (f != null && f.exists() && f.length() > 8) {
            try {
                val bb = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
                val loop = bb.int
                val pcm = ShortArray(bb.remaining() / 2) { bb.short }
                if (pcm.isNotEmpty()) return pcm to loop
            } catch (_: Exception) {
            }
        }
        val res = when (t) {
            Track.CHILL -> chill()
            Track.INTENSE -> intense()
            Track.VICTORY -> victory()
        }
        if (f != null) {
            try {
                val bb = ByteBuffer.allocate(4 + res.first.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                bb.putInt(res.second)
                for (v in res.first) bb.putShort(v)
                f.writeBytes(bb.array())
            } catch (_: Exception) {
            }
        }
        return res
    }

    private fun makeTrack(pcm: ShortArray, loopStart: Int): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setLoopPoints(loopStart, pcm.size, -1)
        track.setVolume(0f)
        return track
    }

    // ------------------------------------------------------------ synthesis

    companion object {
        const val RATE = 22050
        const val MASTER = 0.55f

        fun midi(n: Int) = 440f * 2f.pow((n - 69) / 12f)
    }

    private enum class Wave { SINE, TRI, SQUARE, SAW }

    private class Song(val bpm: Float, beats: Float) {
        val spb = 60f / bpm
        val buf = FloatArray((beats * spb * RATE).toInt())
        fun at(beat: Float) = (beat * spb * RATE).toInt()
    }

    private fun osc(w: Wave, phase: Double): Float {
        val p = phase - Math.floor(phase)
        return when (w) {
            Wave.SINE -> sin(2 * PI * p).toFloat()
            Wave.TRI -> (4 * abs(p - 0.5) - 1).toFloat()
            Wave.SQUARE -> if (p < 0.5) 0.7f else -0.7f
            Wave.SAW -> (2 * p - 1).toFloat() * 0.8f
        }
    }

    /** One note: frequency, start/length in beats, simple attack/decay/sustain/release. */
    private fun note(
        s: Song,
        midiNote: Int,
        beat: Float,
        len: Float,
        v: Float,
        w: Wave,
        attack: Float = 0.01f,
        decay: Float = 0.15f,
        sustain: Float = 0.6f,
        release: Float = 0.08f,
        detune: Float = 0f,
    ) {
        val f = midi(midiNote) * (1f + detune)
        val start = s.at(beat)
        val hold = (len * s.spb * RATE).toInt()
        val rel = (release * RATE).toInt()
        val total = hold + rel
        var phase = 0.0
        val inc = f.toDouble() / RATE
        for (i in 0 until total) {
            val idx = start + i
            if (idx >= s.buf.size) break
            val t = i.toFloat() / RATE
            val env = when {
                t < attack -> t / attack
                t < attack + decay -> 1f - (1f - sustain) * (t - attack) / decay
                i < hold -> sustain
                else -> sustain * (1f - (i - hold).toFloat() / rel)
            }
            phase += inc
            s.buf[idx] += osc(w, phase) * env * v
        }
    }

    private fun kick(s: Song, beat: Float, v: Float) {
        val start = s.at(beat)
        val n = (0.28f * RATE).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= s.buf.size) break
            val t = i.toFloat() / RATE
            val f = 45f + 110f * exp(-t * 30f)
            phase += f / RATE
            s.buf[idx] += sin(2 * PI * phase).toFloat() * exp(-t * 9f) * v
        }
    }

    private fun noiseHit(s: Song, beat: Float, len: Float, v: Float, decay: Float, bright: Boolean, rnd: Random) {
        val start = s.at(beat)
        val n = (len * RATE).toInt()
        var last = 0f
        var lp = 0f
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= s.buf.size) break
            val t = i.toFloat() / RATE
            val w = rnd.nextFloat() * 2f - 1f
            val sample = if (bright) w - last else {
                lp += (w - lp) * 0.35f; lp
            }
            last = w
            s.buf[idx] += sample * exp(-t * decay) * v
        }
    }

    private fun snare(s: Song, beat: Float, v: Float, rnd: Random) {
        noiseHit(s, beat, 0.2f, v, 18f, false, rnd)
        note(s, 50, beat, 0.05f, v * 0.5f, Wave.TRI, decay = 0.05f, sustain = 0f, release = 0.02f)
    }

    private fun echo(buf: FloatArray, delaySec: Float, fb: Float) {
        val d = (delaySec * RATE).toInt()
        for (i in d until buf.size) buf[i] += buf[i - d] * fb
    }

    private fun finish(buf: FloatArray): ShortArray {
        var peak = 0.01f
        for (v in buf) peak = max(peak, abs(v))
        val g = 0.9f / peak
        // short fades at the loop seam to avoid clicks
        val fade = RATE / 200
        return ShortArray(buf.size) { i ->
            var v = buf[i] * g
            if (i < fade) v *= i.toFloat() / fade
            if (i > buf.size - fade) v *= (buf.size - i).toFloat() / fade
            (v * 32000f).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    /** A minor, 90 BPM: soft pad, plucked arpeggio, round bass, gentle drums. */
    private fun chill(): Pair<ShortArray, Int> {
        val rnd = Random(1)
        val s = Song(90f, 32f)
        // Am - F - C - G (one bar each), twice
        val chords = arrayOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(48, 52, 55), intArrayOf(55, 59, 62))
        for (bar in 0 until 8) {
            val ch = chords[bar % 4]
            val b0 = bar * 4f
            for (n in ch) note(s, n, b0, 4f, 0.05f, Wave.TRI, attack = 0.4f, decay = 0.5f, sustain = 0.8f, release = 0.6f)
            for (n in ch) note(s, n + 12, b0, 4f, 0.025f, Wave.SINE, attack = 0.6f, sustain = 0.9f, release = 0.6f, detune = 0.004f)
            // arpeggio in eighths
            val arp = intArrayOf(ch[0], ch[1], ch[2], ch[1] + 12, ch[2] + 12, ch[1] + 12, ch[2], ch[1])
            for (k in 0 until 8) note(s, arp[k] + 12, b0 + k * 0.5f, 0.45f, 0.06f, Wave.SINE, decay = 0.25f, sustain = 0.1f, release = 0.15f)
            // bass
            note(s, ch[0] - 12, b0, 1.5f, 0.2f, Wave.TRI, decay = 0.3f, sustain = 0.6f)
            note(s, ch[0] - 12, b0 + 2f, 1f, 0.16f, Wave.TRI, decay = 0.3f, sustain = 0.6f)
            note(s, ch[2] - 24, b0 + 3f, 1f, 0.14f, Wave.TRI, decay = 0.3f, sustain = 0.6f)
            // drums: kick on 1 & 3, soft snare on 4, hats on off-beats
            kick(s, b0, 0.45f)
            kick(s, b0 + 2f, 0.35f)
            snare(s, b0 + 3f, 0.12f, rnd)
            for (k in 0 until 4) noiseHit(s, b0 + k + 0.5f, 0.05f, 0.05f, 60f, true, rnd)
        }
        echo(s.buf, 0.333f, 0.25f)
        return finish(s.buf) to 0
    }

    /** D minor, 140 BPM: pumping bass, four-on-the-floor, driving lead with echo. */
    private fun intense(): Pair<ShortArray, Int> {
        val rnd = Random(2)
        val s = Song(140f, 32f)
        // Dm - Bb - C - A
        val roots = intArrayOf(50, 46, 48, 45)
        val thirds = intArrayOf(53, 50, 52, 49)
        val fifths = intArrayOf(57, 53, 55, 52)
        val riff = intArrayOf(0, 7, 12, 7, 10, 7, 5, 7)
        for (bar in 0 until 8) {
            val r = roots[bar % 4]
            val b0 = bar * 4f
            // bass: driving eighths with octave jumps
            for (k in 0 until 8) {
                val n = if (k % 2 == 0) r - 12 else r
                note(s, n, b0 + k * 0.5f, 0.42f, 0.13f, Wave.SAW, decay = 0.1f, sustain = 0.5f, release = 0.03f)
            }
            // off-beat chord stabs
            for (k in 0 until 4) {
                for (n in intArrayOf(r + 12, thirds[bar % 4] + 12, fifths[bar % 4] + 12)) {
                    note(s, n, b0 + k + 0.5f, 0.2f, 0.03f, Wave.SQUARE, decay = 0.08f, sustain = 0.3f, release = 0.04f)
                }
            }
            // lead riff (second half an octave higher for tension)
            val up = if (bar >= 4) 12 else 0
            for (k in 0 until 8) {
                note(s, r + 12 + riff[k] + up, b0 + k * 0.5f, 0.35f, 0.055f, Wave.SQUARE, decay = 0.1f, sustain = 0.5f, release = 0.05f)
            }
            // drums
            for (k in 0 until 4) kick(s, b0 + k, 0.6f)
            snare(s, b0 + 1f, 0.3f, rnd)
            snare(s, b0 + 3f, 0.3f, rnd)
            if (bar % 4 == 3) for (k in 0 until 4) snare(s, b0 + 3f + k * 0.25f, 0.18f, rnd)
            for (k in 0 until 16) noiseHit(s, b0 + k * 0.25f, 0.04f, if (k % 2 == 1) 0.07f else 0.04f, 70f, true, rnd)
        }
        echo(s.buf, 0.214f, 0.2f)
        return finish(s.buf) to 0
    }

    /** C major fanfare with a drum roll, then a cheerful loop with claps. */
    private fun victory(): Pair<ShortArray, Int> {
        val rnd = Random(3)
        val s = Song(120f, 24f)
        fun brass(n: Int, b: Float, l: Float, v: Float) {
            note(s, n, b, l, v, Wave.SAW, attack = 0.03f, decay = 0.1f, sustain = 0.8f, release = 0.1f)
            note(s, n, b, l, v * 0.6f, Wave.SQUARE, attack = 0.03f, decay = 0.1f, sustain = 0.8f, release = 0.1f, detune = 0.005f)
        }
        // fanfare: ta-ta-ta-taaa (triplets) and a held chord (beats 0..8)
        val tri = 1f / 3f
        brass(67, 0f, tri, 0.12f); brass(67, tri, tri, 0.12f); brass(67, 2 * tri, tri, 0.12f)
        brass(72, 1f, 1.4f, 0.14f)
        brass(70, 2.5f, 0.5f, 0.12f); brass(74, 3f, 0.5f, 0.12f)
        for (n in intArrayOf(72, 76, 79, 84)) brass(n, 4f, 3.5f, 0.09f)
        for (k in 0 until 16) snare(s, 0f + k * 0.25f, 0.06f + k * 0.008f, rnd)
        kick(s, 4f, 0.7f)
        noiseHit(s, 4f, 2.5f, 0.12f, 1.5f, true, rnd) // cymbal crash
        // cheering loop: C - F - G - C, 4 bars from beat 8
        val loopStart = s.at(8f)
        val prog = intArrayOf(48, 53, 55, 48)
        val melody = intArrayOf(76, 79, 81, 79, 77, 76, 74, 72, 74, 76, 79, 84, 83, 79, 76, 72)
        for (bar in 0 until 4) {
            val b0 = 8f + bar * 4f
            val r = prog[bar]
            for (k in 0 until 4) {
                note(s, r - 12, b0 + k, 0.4f, 0.16f, Wave.TRI, decay = 0.15f, sustain = 0.5f)
                note(s, r + 7 - 12, b0 + k + 0.5f, 0.3f, 0.1f, Wave.TRI, decay = 0.15f, sustain = 0.5f)
                for (n in intArrayOf(r + 12, r + 16, r + 19)) note(s, n, b0 + k + 0.5f, 0.25f, 0.03f, Wave.SQUARE, decay = 0.1f, sustain = 0.3f)
                kick(s, b0 + k, 0.45f)
            }
            noiseHit(s, b0 + 1f, 0.12f, 0.25f, 25f, false, rnd) // clap
            noiseHit(s, b0 + 3f, 0.12f, 0.25f, 25f, false, rnd)
            for (k in 0 until 4) note(s, melody[bar * 4 + k], b0 + k, 0.9f, 0.07f, Wave.SQUARE, decay = 0.2f, sustain = 0.5f)
        }
        echo(s.buf, 0.25f, 0.18f)
        return finish(s.buf) to loopStart
    }
}
