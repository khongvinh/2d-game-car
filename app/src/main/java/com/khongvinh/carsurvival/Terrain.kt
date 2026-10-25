package com.khongvinh.carsurvival

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Procedural road: a height field sampled every [SEG] units, shaped by the stage's
 * [RoadStyle] (plains, dunes, highway with tunnels, caves, bridges over chasms).
 * World y points down, so a smaller y is higher ground.
 */
class Terrain(stage: Int, val length: Float, seed: Int, val style: RoadStyle = Themes.road(stage)) {
    companion object {
        const val SEG = 40f
        const val START_X = -2400f
        const val BASE_Y = 520f

        /** Headroom under a tunnel / cave ceiling. */
        const val CLEARANCE = 300f
    }

    private val ys: FloatArray

    /** Ceiling height per sample, NaN where the sky is open. */
    private val ceil: FloatArray
    val decor = ArrayList<Decor>()

    /** x positions of ramp tops (good spots to place obstacles after). */
    val rampEnds = ArrayList<Float>()

    /** Tunnel / cave spans (x from, x to). */
    val tunnels = ArrayList<FloatArray>()

    /** Bridge spans over a chasm (x from, x to). */
    val bridges = ArrayList<FloatArray>()

    init {
        val rnd = Random(seed)
        val n = ((length - START_X + 4000f) / SEG).toInt() + 2
        ys = FloatArray(n)
        ceil = FloatArray(n) { Float.NaN }
        val amp = min(230f, 75f + stage * 8f) * style.amp
        val p1 = rnd.nextFloat() * 6f
        val p2 = rnd.nextFloat() * 6f
        val p3 = rnd.nextFloat() * 6f
        val maxStep = SEG * 0.62f
        val boss = stage % 5 == 0
        // the boss arena (and its approach) is always open ground
        val featureEnd = length - (if (boss) 2900f else 700f)

        // plan tunnels, the cave and bridges first so the road can be shaped for them
        if (style.cave) {
            tunnels.add(floatArrayOf(length * 0.18f, min(featureEnd, length * 0.85f)))
        } else if (style.tunnels > 0) {
            val span = (featureEnd - 900f) / style.tunnels
            for (k in 0 until style.tunnels) {
                val len = 700f + rnd.nextFloat() * 600f
                val a = 900f + span * k + rnd.nextFloat() * max(0f, span - len - 300f)
                tunnels.add(floatArrayOf(a, min(featureEnd, a + len)))
            }
        }
        if (style.bridges > 0) {
            val span = (featureEnd - 1000f) / style.bridges
            for (k in 0 until style.bridges) {
                val len = 360f + rnd.nextFloat() * 320f
                val a = 1000f + span * k + rnd.nextFloat() * max(0f, span - len - 300f)
                bridges.add(floatArrayOf(a, a + len))
            }
        }
        fun inSpan(list: List<FloatArray>, x: Float, pad: Float) = list.any { x > it[0] - pad && x < it[1] + pad }

        // 1) rolling hills: several octaves, slope-limited so they stay climbable
        var prev = BASE_Y
        for (i in 0 until n) {
            val x = START_X + i * SEG
            val t = x * 0.0011f
            var hills = amp * sin(t + p1) +
                amp * 0.6f * sin(t * 2.7f + p2) +
                amp * 0.35f * sin(t * 6.3f + p3) +
                amp * 0.12f * sin(t * 17f + p1)
            // tunnels and caves run on gentler ground
            if (inSpan(tunnels, x, 200f)) hills *= 0.45f
            // fade the hills in after the start and out before the evacuation zone;
            // boss stages get a wide, gentle arena before the station
            val endFade = if (boss) 2600f else 600f
            val fade = min(((x - 200f) / 800f).coerceIn(0f, 1f), ((length - 200f - x) / endFade).coerceIn(0f, 1f))
            val target = BASE_Y + hills * fade
            val y = prev + (target - prev).coerceIn(-maxStep, maxStep)
            ys[i] = y
            prev = y
        }

        // 2) rough features on top: rocks, potholes, washboard stretches, jump ramps
        val off = FloatArray(n)
        val rough = (1f + min(0.6f, stage * 0.03f)) * style.rough
        var i = 0
        while (i < n - 20) {
            val x = START_X + i * SEG
            if (x < 500f || x > length - (if (boss) 2300f else 500f) || inSpan(bridges, x, 260f)) {
                i++; continue
            }
            val covered = inSpan(tunnels, x, 500f)
            val r = rnd.nextFloat()
            when {
                !covered && r < (0.01f + min(0.012f, stage * 0.0008f)) * style.rough &&
                    abs(ys[i + 9] - ys[max(0, i - 4)]) < 13 * SEG * 0.18f -> { // jump ramp on fairly level ground
                    val len = 5 + rnd.nextInt(4)
                    val rise = (12f + rnd.nextFloat() * 8f) * rough
                    for (k in 0 until len) off[i + k] -= rise * (k + 1)
                    rampEnds.add(START_X + (i + len) * SEG)
                    i += len + 2
                }
                r < 0.05f -> { // rock / bump
                    val h = (12f + rnd.nextFloat() * 12f) * rough * (if (covered) 0.6f else 1f)
                    off[i] -= h * 0.45f; off[i + 1] -= h; off[i + 2] -= h * 0.45f
                    i += 3
                }
                r < 0.068f -> { // pothole
                    val d = (24f + rnd.nextFloat() * 26f) * rough
                    off[i] += d * 0.5f; off[i + 1] += d; off[i + 2] += d * 0.5f
                    i += 4
                }
                r < 0.078f -> { // washboard
                    val len = 8 + rnd.nextInt(8)
                    val h = (6f + rnd.nextFloat() * 5f) * rough
                    for (k in 0 until len) off[i + k] += if (k % 2 == 0) -h else h * 0.5f
                    i += len
                }
                else -> i++
            }
        }
        for (k in 0 until n) ys[k] += off[k]

        // 3) no uphill face steeper than ~38° (drops may be as steep as they like),
        //    so a bump stacked on a hillside never becomes a wall
        val maxUp = SEG * 0.8f
        for (k in 0 until n - 1) if (ys[k] - ys[k + 1] > maxUp) ys[k + 1] = ys[k] - maxUp

        // 4) round off sharp valleys (V shapes make the car nose-dive); cliff drops stay
        for (pass in 0 until 3) {
            val tmp = ys.copyOf()
            for (k in 1 until n - 1) {
                val isDrop = tmp[k + 1] - tmp[k] > 60f || tmp[k] - tmp[k - 1] > 60f
                if (!isDrop) ys[k] = (tmp[k - 1] + tmp[k] * 2f + tmp[k + 1]) / 4f
            }
        }

        // 5) bridge decks run straight from rim to rim
        for (b in bridges) {
            val a = indexOf(b[0]).coerceIn(0, n - 1)
            val e = indexOf(b[1]).coerceIn(0, n - 1)
            for (k in a..e) ys[k] = ys[a] + (ys[e] - ys[a]) * (k - a) / max(1, e - a)
        }

        // 6) ceilings: tunnels have a level roof, caves a wavy one
        for (tn in tunnels) {
            val a = indexOf(tn[0]).coerceIn(0, n - 1)
            val e = indexOf(tn[1]).coerceIn(0, n - 1)
            for (k in a..e) {
                // the roof follows the road loosely
                val win = 6
                var m = Float.MAX_VALUE
                for (j in max(0, k - win)..min(n - 1, k + win)) m = min(m, ys[j])
                val wave = if (style.cave) 50f * sin(k * 0.21f) + 30f * sin(k * 0.53f + 1f) else 0f
                ceil[k] = m - CLEARANCE - wave.coerceAtLeast(-40f)
            }
        }

        var dx = -1500f
        while (dx < length + 2000f) {
            if (!inSpan(tunnels, dx, 60f) && !inSpan(bridges, dx, 80f)) decor.add(Decor(rnd.nextInt(5), dx, 0.7f + rnd.nextFloat() * 0.7f))
            dx += 180f + rnd.nextFloat() * 420f
        }
    }

    fun heightAt(x: Float): Float {
        val f = (x - START_X) / SEG
        val i = floor(f).toInt()
        if (i < 0) return ys[0]
        if (i >= ys.size - 1) return ys[ys.size - 1]
        val t = f - i
        return ys[i] + (ys[i + 1] - ys[i]) * t
    }

    /** Roof height at x, or NaN under open sky. */
    fun ceilingAt(x: Float): Float {
        val f = (x - START_X) / SEG
        val i = floor(f).toInt()
        if (i < 0 || i >= ceil.size - 1) return Float.NaN
        val a = ceil[i]
        val b = ceil[i + 1]
        if (a.isNaN() || b.isNaN()) return Float.NaN
        return a + (b - a) * (f - i)
    }

    fun ceilingPoint(i: Int) = ceil[max(0, min(ceil.size - 1, i))]

    fun slopeAt(x: Float): Float {
        val f = (x - START_X) / SEG
        val i = floor(f).toInt().coerceIn(0, ys.size - 2)
        return (ys[i + 1] - ys[i]) / SEG
    }

    /** Upward unit normal at x (nx, ny) with ny < 0. */
    fun normal(x: Float, out: FloatArray) {
        val s = slopeAt(x)
        val len = sqrt(1f + s * s)
        out[0] = s / len
        out[1] = -1f / len
    }

    fun onBridge(x: Float) = bridges.any { x > it[0] && x < it[1] }
    fun covered(x: Float, pad: Float = 0f) = tunnels.any { x > it[0] - pad && x < it[1] + pad }

    /** True where an obstacle would be hard to escape: steep ground or the bottom of a dip. */
    fun awkward(x: Float): Boolean {
        if (abs(slopeAt(x)) > 0.45f || abs(slopeAt(x - 60f)) > 0.55f || abs(slopeAt(x + 60f)) > 0.55f) return true
        val y = heightAt(x)
        return y - heightAt(x - 180f) > 40f && y - heightAt(x + 180f) > 40f
    }

    fun point(i: Int) = ys[max(0, min(ys.size - 1, i))]
    fun indexOf(x: Float) = floor((x - START_X) / SEG).toInt()
}
