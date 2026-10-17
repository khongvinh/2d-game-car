package com.khongvinh.carsurvival

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Procedural hilly desert road: a height field sampled every [SEG] units.
 * World y points down, so a smaller y is higher ground.
 */
class Terrain(stage: Int, val length: Float, seed: Int) {
    companion object {
        const val SEG = 40f
        const val START_X = -2400f
        const val BASE_Y = 520f
    }

    private val ys: FloatArray
    val decor = ArrayList<Decor>()

    /** x positions of ramp tops (good spots to place obstacles after). */
    val rampEnds = ArrayList<Float>()

    init {
        val rnd = Random(seed)
        val n = ((length - START_X + 4000f) / SEG).toInt() + 2
        ys = FloatArray(n)
        val amp = min(230f, 75f + stage * 8f)
        val p1 = rnd.nextFloat() * 6f
        val p2 = rnd.nextFloat() * 6f
        val p3 = rnd.nextFloat() * 6f
        val maxStep = SEG * 0.62f

        // 1) rolling hills: several octaves, slope-limited so they stay climbable
        var prev = BASE_Y
        for (i in 0 until n) {
            val x = START_X + i * SEG
            val t = x * 0.0011f
            val hills = amp * sin(t + p1) +
                amp * 0.6f * sin(t * 2.7f + p2) +
                amp * 0.35f * sin(t * 6.3f + p3) +
                amp * 0.12f * sin(t * 17f + p1)
            // fade the hills in after the start and out before the evacuation zone
            // boss stages get a wide, gentle arena before the station
            val endFade = if (stage % 5 == 0) 2600f else 600f
            val fade = min(((x - 200f) / 800f).coerceIn(0f, 1f), ((length - 200f - x) / endFade).coerceIn(0f, 1f))
            val target = BASE_Y + hills * fade
            val y = prev + (target - prev).coerceIn(-maxStep, maxStep)
            ys[i] = y
            prev = y
        }

        // 2) rough features on top: rocks, potholes, washboard stretches, jump ramps
        val off = FloatArray(n)
        val rough = 1f + min(0.6f, stage * 0.03f)
        var i = 0
        while (i < n - 20) {
            val x = START_X + i * SEG
            if (x < 500f || x > length - (if (stage % 5 == 0) 2300f else 500f)) {
                i++; continue
            }
            val r = rnd.nextFloat()
            when {
                r < 0.01f + min(0.012f, stage * 0.0008f) &&
                    abs(ys[i + 9] - ys[max(0, i - 4)]) < 13 * SEG * 0.18f -> { // jump ramp on fairly level ground
                    val len = 5 + rnd.nextInt(4)
                    val rise = (12f + rnd.nextFloat() * 8f) * rough
                    for (k in 0 until len) off[i + k] -= rise * (k + 1)
                    rampEnds.add(START_X + (i + len) * SEG)
                    i += len + 2
                }
                r < 0.05f -> { // rock / bump
                    val h = (12f + rnd.nextFloat() * 12f) * rough
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
        var dx = -1500f
        while (dx < length + 2000f) {
            decor.add(Decor(rnd.nextInt(5), dx, 0.7f + rnd.nextFloat() * 0.7f))
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

    fun point(i: Int) = ys[max(0, min(ys.size - 1, i))]
    fun indexOf(x: Float) = floor((x - START_X) / SEG).toInt()
}
