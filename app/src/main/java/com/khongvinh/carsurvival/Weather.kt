package com.khongvinh.carsurvival

import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Random weather on the road (it replaced the closing storm): clear spells, rain,
 * thunderstorms with wind and lightning, and now and then a tornado that sweeps
 * along the road throwing monsters about and lifting the car.
 */
class Weather(private val g: Game) {
    enum class Kind(val title: String) { CLEAR(""), RAIN("TRỜI MƯA"), STORM("BÃO SẤM SÉT"), TORNADO("LỐC XOÁY!") }

    var kind = Kind.CLEAR
    internal var left = 20f

    /** 0..1 fade in/out of the current spell. */
    var power = 0f
    var flash = 0f
    private var boltCd = 4f
    var boltX = 0f
    var boltT = 0f

    /** Tornado position (world) and its drift. */
    var tx = 0f
    var ty = 0f
    private var tdir = -1f
    var spin = 0f

    fun reset() {
        kind = Kind.CLEAR
        left = 14f + Random.nextFloat() * 14f
        power = 0f
        flash = 0f
        boltT = 0f
    }

    fun update(dt: Float) {
        val c = g.car
        left -= dt
        if (left <= 0f) next()
        // fade the spell in, and out during its last 3 seconds
        val want = if (kind == Kind.CLEAR || left < 3f) 0f else 1f
        power += (want - power) * min(1f, dt * 0.8f)
        if (flash > 0f) flash -= dt * 3f
        if (boltT > 0f) boltT -= dt
        spin += dt * 9f
        when (kind) {
            Kind.CLEAR, Kind.RAIN -> {}
            Kind.STORM -> {
                // headwind and lightning
                c.vx -= 70f * power * dt
                boltCd -= dt
                if (boltCd <= 0f && power > 0.5f) {
                    boltCd = 3f + Random.nextFloat() * 4f
                    flash = 1f
                    boltT = 0.25f
                    boltX = c.x + 300f + Random.nextFloat() * 900f
                    g.sfx.play(Sfx.S.BOOM, 0.45f)
                    g.shake = max(g.shake, 0.15f)
                    // the bolt fries the monsters it lands among
                    for (z in g.zombies) if (z.alive && abs(z.x - boltX) < 110f) g.flingZombie(z, sign(z.x - boltX) * 300f, -500f)
                    g.explodeAt(boltX, g.terrain.heightAt(boltX) - 10f, 20)
                }
            }
            Kind.TORNADO -> {
                tx += tdir * (130f + 60f * sin(spin * 0.2f)) * dt
                ty = g.terrain.heightAt(tx)
                val dx = c.x - tx
                // monsters near the funnel are sucked up and thrown away
                for (z in g.zombies) if (z.alive && abs(z.x - tx) < 150f * power) g.flingZombie(z, sign(z.x - tx + 0.1f) * 500f, -900f)
                // the car is tugged towards it, lifted and spun; a little damage while inside
                if (abs(dx) < 520f) c.vx -= sign(dx) * 260f * power * dt
                if (abs(dx) < 170f && power > 0.3f) {
                    c.vy -= 1500f * power * dt
                    c.spin += sign(dx + 0.1f) * 4f * dt
                    g.hurtCar(6f * dt)
                }
                if (abs(tx - c.x) > 2600f) left = min(left, 3f)
            }
        }
    }

    internal fun next() {
        val r = Random.nextFloat()
        start(when {
            kind != Kind.CLEAR -> Kind.CLEAR
            r < 0.4f -> Kind.RAIN
            r < 0.75f -> Kind.STORM
            else -> Kind.TORNADO
        })
    }

    /** Begins a spell of weather [k] (also used by the preview tool). */
    internal fun start(k: Kind) {
        kind = k
        left = if (kind == Kind.CLEAR) 18f + Random.nextFloat() * 22f else 16f + Random.nextFloat() * 12f
        boltCd = 2f
        if (kind == Kind.TORNADO) {
            // it comes down the road towards the car from ahead
            tx = g.car.x + 1250f
            tdir = -1f
        }
        if (kind != Kind.CLEAR) {
            g.texts.add(FloatText(kind.title, g.car.x, g.car.y - 220f, 0xFF80D8FF.toInt()).also { it.size = 48f; it.life = 2.2f })
            g.sfx.play(Sfx.S.STORM, 0.5f)
        }
    }
}

/** Tornado funnel and the lightning bolt, drawn in world space. */
internal fun Renderer.drawWeatherWorld(c: Canvas) {
    val w = g.weather
    if (w.kind == Weather.Kind.TORNADO && w.power > 0.02f) {
        val a = (200 * w.power).toInt()
        val h = 620f
        // stacked spinning rings, narrow at the ground and wide at the top
        for (k in 0 until 18) {
            val t = k / 17f
            val y = w.ty - t * h
            val rw = 26f + t * t * 230f
            val sway = sin(w.spin * 0.7f + t * 4f) * 30f * t
            val ph = w.spin + k * 0.8f
            fill.color = Color.argb((a * (0.55f + 0.35f * sin(ph))).toInt(), 120, 108, 96)
            c.drawOval(w.tx + sway - rw, y - rw * 0.22f, w.tx + sway + rw, y + rw * 0.22f, fill)
            stroke.color = Color.argb((a * 0.6f).toInt(), 70, 62, 55)
            stroke.strokeWidth = 4f
            r.set(w.tx + sway - rw, y - rw * 0.22f, w.tx + sway + rw, y + rw * 0.22f)
            c.drawArc(r, (ph * 57f) % 360f, 120f, false, stroke)
        }
        // dust and debris whirling round the base
        for (k in 0 until 10) {
            val ang = w.spin * 1.6f + k * 0.63f
            val rr = 60f + (k % 4) * 30f
            fill.color = Color.argb(a, 90, 70, 50)
            c.drawRect(w.tx + cos(ang) * rr, w.ty - 30f - k * 22f + sin(ang) * 10f, w.tx + cos(ang) * rr + 8f, w.ty - 24f - k * 22f + sin(ang) * 10f, fill)
        }
    }
    if (w.boltT > 0f) {
        val gy = g.terrain.heightAt(w.boltX)
        stroke.color = Color.argb((255 * min(1f, w.boltT * 6f)).toInt(), 230, 240, 255)
        stroke.strokeWidth = 7f
        var x = w.boltX + 60f
        var y = g.camY - 100f
        while (y < gy) {
            val nx = w.boltX + (Random.nextFloat() - 0.5f) * 90f
            val ny = y + 70f + Random.nextFloat() * 50f
            c.drawLine(x, y, nx, min(ny, gy), stroke)
            x = nx; y = ny
        }
    }
}

/** Rain streaks, the darkened sky and the lightning flash, drawn over the screen. */
internal fun Renderer.drawWeatherScreen(c: Canvas) {
    val w = g.weather
    if (w.power < 0.02f) return
    val wet = w.kind == Weather.Kind.RAIN || w.kind == Weather.Kind.STORM || w.kind == Weather.Kind.TORNADO
    val dark = when (w.kind) { Weather.Kind.STORM -> 0.42f; Weather.Kind.TORNADO -> 0.3f; else -> 0.2f } * w.power
    fill.color = Color.argb((255 * dark).toInt(), 20, 28, 40)
    c.drawRect(0f, 0f, g.W, g.H, fill)
    if (wet) {
        val n = if (w.kind == Weather.Kind.STORM) 140 else 80
        val slant = if (w.kind == Weather.Kind.STORM) -0.45f else -0.2f
        stroke.color = Color.argb((140 * w.power).toInt(), 200, 215, 235)
        stroke.strokeWidth = 2f
        for (k in 0 until n) {
            val h = (k * 7919) % 10007
            val sp = 900f + h % 500
            // falling down-left in the wind; the camera's movement drags the drops past
            val span = g.W + 300f
            val x = (((h % 1997) / 1997f * span + g.anim * sp * slant - g.camX * 0.6f) % span + span) % span - 150f
            val y = ((h % 1009) / 1009f * g.H + g.anim * sp) % (g.H + 40f) - 20f
            val len = 26f + h % 18
            c.drawLine(x, y, x + slant * len, y + len, stroke)
        }
    }
    if (w.flash > 0f) {
        fill.color = Color.argb((180 * w.flash).toInt().coerceIn(0, 255), 235, 240, 255)
        c.drawRect(0f, 0f, g.W, g.H, fill)
    }
}
