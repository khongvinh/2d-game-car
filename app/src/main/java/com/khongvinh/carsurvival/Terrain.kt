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
        val amp = 50f + stage * 9f
        val p1 = rnd.nextFloat() * 6f
        val p2 = rnd.nextFloat() * 6f
        val maxStep = SEG * (0.5f + min(0.3f, stage * 0.015f))
        var prev = BASE_Y
        var i = 0
        var rampLeft = 0
        var rampRise = 0f
        while (i < n) {
            val x = START_X + i * SEG
            var y: Float
            if (x < 500f || x > length - 300f) {
                // flat start area and flat evacuation zone
                y = prev + (BASE_Y - prev) * 0.1f
            } else if (rampLeft > 0) {
                rampLeft--
                y = prev - rampRise
                if (rampLeft == 0) {
                    rampEnds.add(x)
                    // drop-off after the ramp
                    val drop = 60f + rnd.nextFloat() * 120f
                    val steps = 3
                    for (k in 0 until steps) {
                        if (i >= n) break
                        ys[i] = y + drop * (k + 1) / steps
                        i++
                    }
                    prev = ys[i - 1]
                    continue
                }
            } else {
                val t = x * 0.0011f
                val target = BASE_Y +
                    amp * sin(t + p1) +
                    amp * 0.6f * sin(t * 2.7f + p2) +
                    amp * 0.25f * sin(t * 7.3f)
                y = prev + (target - prev).coerceIn(-maxStep, maxStep) * 0.5f
                if (rnd.nextFloat() < 0.012f + stage * 0.001f && x > 1500f) {
                    rampLeft = 5 + rnd.nextInt(4)
                    rampRise = 10f + rnd.nextFloat() * 8f
                }
            }
            ys[i] = y
            prev = y
            i++
        }
        // smooth a little so the car does not bounce on every vertex
        for (pass in 0 until 2) {
            for (k in 1 until n - 1) {
                if (abs(ys[k - 1] - ys[k + 1]) < 70f) ys[k] = (ys[k - 1] + ys[k] * 2 + ys[k + 1]) / 4f
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
