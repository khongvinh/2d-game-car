package com.khongvinh.carsurvival

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Draws every game state with plain Canvas primitives (no bitmaps). */
class Renderer(private val g: Game) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val path = Path()
    private val r = RectF()

    fun render(c: Canvas) {
        drawBackground(c)
        drawWorld(c)
        fill.shader = vignette
        c.drawRect(0f, 0f, g.W, g.H, fill)
        fill.shader = null
        when (g.state) {
            State.MENU -> drawMenu(c)
            State.SHOP -> drawShop(c)
            State.GARAGE -> drawGarage(c)
            State.PLAYING, State.PAUSED -> {
                drawDamageOverlay(c)
                drawHud(c)
                drawOverlay(c)
            }
            else -> drawOverlay(c)
        }
    }

    // ------------------------------------------------------------ backdrop

    private fun drawBackground(c: Canvas) {
        ensureShaders()
        val W = g.W
        val H = g.H
        fill.shader = skyShader
        c.drawRect(0f, 0f, W, H, fill)
        fill.shader = sunShader
        c.drawRect(0f, 0f, W, H * 0.75f, fill)
        fill.shader = null
        fill.color = 0xFFFFF6E0.toInt()
        c.drawCircle(W * 0.78f, H * 0.26f, 54f, fill)

        // drifting clouds
        for (k in 0 until 6) {
            val span = W + 700f
            val raw = k * 431f + g.anim * (6f + k * 2.5f) - g.camX * Game.ZOOM * 0.02f
            val cx = ((raw % span) + span) % span - 350f
            cloud(c, cx, 60f + (k * 47) % 140, 0.6f + (k % 3) * 0.25f)
        }
        // vultures circling
        stroke.color = 0xAA3E2723.toInt()
        stroke.strokeWidth = 3f
        for (k in 0 until 3) {
            val a = g.anim * 0.35f + k * 2.1f
            val bx = W * 0.45f + cos(a) * (160f + k * 40f)
            val by = 150f + sin(a) * 40f + k * 20f
            val flap = sin(g.anim * 5f + k) * 7f
            c.drawLine(bx - 16f, by - flap, bx, by, stroke)
            c.drawLine(bx, by, bx + 16f, by - flap, stroke)
        }

        mesas(c, 0.05f, H * 0.6f, farShader, 1.0f, 180f)
        // dusty haze between layers
        fill.color = 0x33FBE3B0
        c.drawRect(0f, H * 0.45f, W, H, fill)
        city(c, 0.12f, H * 0.68f)
        mesas(c, 0.22f, H * 0.76f, midShader, 1.9f, 120f)
    }

    private fun mesas(c: Canvas, par: Float, base: Float, shader: Shader?, seed: Float, amp: Float) {
        val W = g.W
        path.reset()
        topPath.reset()
        path.moveTo(0f, g.H)
        var sx = 0f
        val off = g.camX * Game.ZOOM * par
        val lift = (g.camY - 100f) * Game.ZOOM * par * 0.3f
        while (sx <= W + 24f) {
            val u = (sx + off) * 0.004f
            var h = amp * (0.55f + 0.45f * sin(u + seed) + 0.25f * sin(u * 2.3f + seed * 3f))
            h = h.coerceIn(amp * 0.15f, amp * 0.85f) // flat mesa tops
            val y = base - h - lift
            path.lineTo(sx, y)
            if (sx == 0f) topPath.moveTo(sx, y) else topPath.lineTo(sx, y)
            sx += 24f
        }
        path.lineTo(W, g.H)
        path.close()
        fill.shader = shader
        c.drawPath(path, fill)
        fill.shader = null
        stroke.color = 0x40FFF3E0
        stroke.strokeWidth = 3f
        c.drawPath(topPath, stroke)
    }

    /** Silhouettes of a ruined city on the horizon. */

    // --------------------------------------------------------------- world

    private fun drawWorld(c: Canvas) {
        c.save()
        if (g.shake > 0f) {
            val s = g.shake * 30f
            c.translate((Random.nextFloat() - 0.5f) * s, (Random.nextFloat() - 0.5f) * s)
        }
        c.scale(Game.ZOOM, Game.ZOOM)
        c.translate(-g.camX, -g.camY)
        val x0 = g.camX - 200f
        val x1 = g.camX + g.viewW + 200f

        for (d in g.terrain.decor) if (d.x in x0..x1) drawDecor(c, d)
        drawTerrain(c)
        drawFinish(c)
        drawRecord(c)
        drawCheckpoints(c)

        // soft shadows on the ground
        fill.color = 0x40000000
        for (z in g.zombies) if (z.x in x0..x1) c.drawOval(z.x - z.type.w * 0.8f, z.y - 6f, z.x + z.type.w * 0.8f, z.y + 6f, fill)
        val car = g.car
        val gy = g.terrain.heightAt(car.x)
        val lift = ((gy - car.y - 50f) / 500f).coerceIn(0f, 0.8f)
        fill.color = Color.argb((90 * (1f - lift)).toInt(), 0, 0, 0)
        c.drawOval(car.x - 100f * (1f - lift * 0.5f), gy - 9f, car.x + 100f * (1f - lift * 0.5f), gy + 9f, fill)

        for (o in g.obstacles) if (o.x in x0..x1) drawObstacle(c, o)
        for (it in g.items) if (it.x in x0..x1) drawItem(c, it)
        for (z in g.zombies) if (z.x in x0..x1) drawZombie(c, z)
        g.boss?.let { b -> if (b.alive && b.x in x0 - 200f..x1 + 200f) drawBoss(c, b) }
        for (d in g.debris) if (d.x in x0..x1) drawDebris(c, d)
        drawShots(c)
        for (rk in g.bossRocks) drawBossRock(c, rk)
        drawPlayerCar(c)
        for (p in g.particles) {
            val ratio = (p.life / p.maxLife).coerceIn(0f, 1f)
            fill.color = p.color
            fill.alpha = (255 * ratio * (if (p.grow > 0f) 0.55f else 1f)).toInt().coerceIn(0, 255)
            c.drawCircle(p.x, p.y, p.size * (1f + p.grow * (1f - ratio)), fill)
        }
        fill.alpha = 255
        for (t in g.texts) {
            val a = (255 * min(1f, t.life)).toInt()
            // pop in, then float
            text.textSize = t.size * (1f + max(0f, t.life - 1.1f) * 1.2f)
            text.style = Paint.Style.STROKE
            text.strokeWidth = 7f
            text.color = 0xFF1A1A1A.toInt()
            text.alpha = a
            c.drawText(t.text, t.x, t.y, text)
            text.style = Paint.Style.FILL
            text.color = t.color
            text.alpha = a
            c.drawText(t.text, t.x, t.y, text)
        }
        text.alpha = 255
        if (g.state != State.MENU && g.state != State.SHOP && g.state != State.GARAGE) drawStorm(c)
        c.restore()
    }

    private fun drawTerrain(c: Canvas) {
        val t = g.terrain
        val i0 = t.indexOf(g.camX) - 2
        val i1 = t.indexOf(g.camX + g.viewW) + 3
        val bottom = g.camY + g.viewH + 400f
        path.reset()
        topPath.reset()
        for (i in i0..i1) {
            val x = Terrain.START_X + i * Terrain.SEG
            val y = t.point(i)
            if (i == i0) {
                path.moveTo(x, bottom); path.lineTo(x, y); topPath.moveTo(x, y)
            } else {
                path.lineTo(x, y); topPath.lineTo(x, y)
            }
        }
        path.lineTo(Terrain.START_X + i1 * Terrain.SEG, bottom)
        path.close()
        fill.shader = groundShader
        c.drawPath(path, fill)
        fill.shader = null

        // buried rocks and strata
        for (i in i0..i1) {
            val x = Terrain.START_X + i * Terrain.SEG
            val y = t.point(i)
            val h = hash(i)
            if (h % 6 == 0) {
                fill.color = 0x30000000
                c.drawOval(x - 18f, y + 50f + (h % 4) * 30f, x + 22f + (h % 3) * 10f, y + 72f + (h % 4) * 30f, fill)
                fill.color = 0x22FFFFFF
                c.drawOval(x - 12f, y + 52f + (h % 4) * 30f, x + 6f, y + 60f + (h % 4) * 30f, fill)
            }
            if (h % 9 == 1) {
                fill.color = 0x26000000
                c.drawRect(x, y + 110f + (h % 5) * 12f, x + 90f, y + 117f + (h % 5) * 12f, fill)
            }
        }
        // road crust: dark edge, sandy top, bright rim
        stroke.color = 0xFF4A2B17.toInt()
        stroke.strokeWidth = 20f
        topPath.offset(0f, 9f)
        c.drawPath(topPath, stroke)
        stroke.color = 0xFFB27A47.toInt()
        stroke.strokeWidth = 9f
        topPath.offset(0f, -6f)
        c.drawPath(topPath, stroke)
        stroke.color = 0xFFE9BC85.toInt()
        stroke.strokeWidth = 3f
        topPath.offset(0f, -4f)
        c.drawPath(topPath, stroke)

        // dry grass, pebbles and bushes on the surface
        for (i in i0..i1) {
            val x = Terrain.START_X + i * Terrain.SEG
            val y = t.point(i) - 1f
            val h = hash(i * 7 + 3)
            when (h % 7) {
                0, 1 -> {
                    stroke.color = if (h % 2 == 0) 0xFFA1A33A.toInt() else 0xFF7F7A2A.toInt()
                    stroke.strokeWidth = 3f
                    val bx = x + (h % 30)
                    val sway = sin(g.anim * 2f + i) * 3f
                    c.drawLine(bx, y, bx - 6f + sway, y - 16f, stroke)
                    c.drawLine(bx + 3f, y, bx + 3f + sway, y - 22f, stroke)
                    c.drawLine(bx + 6f, y, bx + 12f + sway, y - 14f, stroke)
                }
                2 -> {
                    fill.color = 0xFF6D4C41.toInt()
                    c.drawCircle(x + 14f, y + 2f, 5f, fill)
                    fill.color = 0xFF8D6E63.toInt()
                    c.drawCircle(x + 26f, y + 3f, 3.5f, fill)
                }
                3 -> if (h % 3 == 0) {
                    stroke.color = 0xFF5D4037.toInt()
                    stroke.strokeWidth = 2.5f
                    c.drawLine(x, y, x - 10f, y - 20f, stroke)
                    c.drawLine(x, y, x + 8f, y - 24f, stroke)
                    c.drawLine(x - 5f, y - 10f, x - 16f, y - 16f, stroke)
                    c.drawLine(x + 4f, y - 12f, x + 15f, y - 18f, stroke)
                }
            }
        }
    }

    private fun drawDecor(c: Canvas, d: Decor) {
        val gy = g.terrain.heightAt(d.x)
        val s = d.size
        when (d.kind) {
            0 -> { // cactus
                fill.color = 0xFF558B2F.toInt()
                c.drawRoundRect(d.x - 12 * s, gy - 130 * s, d.x + 12 * s, gy + 5f, 12 * s, 12 * s, fill)
                c.drawRoundRect(d.x - 44 * s, gy - 95 * s, d.x - 26 * s, gy - 50 * s, 9 * s, 9 * s, fill)
                c.drawRect(d.x - 44 * s, gy - 62 * s, d.x - 10 * s, gy - 48 * s, fill)
                c.drawRoundRect(d.x + 26 * s, gy - 110 * s, d.x + 44 * s, gy - 70 * s, 9 * s, 9 * s, fill)
                c.drawRect(d.x + 10 * s, gy - 82 * s, d.x + 44 * s, gy - 68 * s, fill)
            }
            1 -> { // dead tree
                stroke.color = 0xFF4E342E.toInt()
                stroke.strokeWidth = 10f * s
                c.drawLine(d.x, gy, d.x, gy - 140 * s, stroke)
                stroke.strokeWidth = 6f * s
                c.drawLine(d.x, gy - 90 * s, d.x - 40 * s, gy - 130 * s, stroke)
                c.drawLine(d.x, gy - 110 * s, d.x + 36 * s, gy - 160 * s, stroke)
                c.drawLine(d.x - 20 * s, gy - 110 * s, d.x - 30 * s, gy - 150 * s, stroke)
            }
            2 -> { // rocks
                fill.color = 0xFF795548.toInt()
                c.drawOval(d.x - 50 * s, gy - 40 * s, d.x + 40 * s, gy + 10f, fill)
                fill.color = 0xFF8D6E63.toInt()
                c.drawOval(d.x - 30 * s, gy - 34 * s, d.x + 10 * s, gy - 8 * s, fill)
            }
            3 -> { // telegraph pole
                stroke.color = 0xFF5D4037.toInt()
                stroke.strokeWidth = 9f
                c.drawLine(d.x, gy, d.x, gy - 230f, stroke)
                stroke.strokeWidth = 7f
                c.drawLine(d.x - 40f, gy - 210f, d.x + 40f, gy - 210f, stroke)
            }
            else -> { // warning sign
                stroke.color = 0xFF5D4037.toInt()
                stroke.strokeWidth = 7f
                c.drawLine(d.x, gy, d.x, gy - 110f, stroke)
                fill.color = 0xFFFFC107.toInt()
                path.reset()
                path.moveTo(d.x, gy - 170f)
                path.lineTo(d.x + 40f, gy - 110f)
                path.lineTo(d.x - 40f, gy - 110f)
                path.close()
                c.drawPath(path, fill)
                text.textAlign = Paint.Align.CENTER
                text.textSize = 40f
                text.color = Color.BLACK
                c.drawText("!", d.x, gy - 118f, text)
            }
        }
    }

    private fun drawFinish(c: Canvas) {
        val x = g.goalX
        if (x < g.camX - 600f || x > g.camX + g.viewW + 600f) return
        val gy = g.terrain.heightAt(x)
        for (k in 0 until 8) {
            fill.color = if (k % 2 == 0) Color.WHITE else Color.BLACK
            c.drawRect(x - 10f, gy - k * 25f - 25f, x + 10f, gy - k * 25f, fill)
        }
        fill.color = 0xFF546E7A.toInt()
        c.drawRect(x + 60f, gy - 260f, x + 110f, gy, fill)
        c.drawRect(x + 400f, gy - 260f, x + 450f, gy, fill)
        fill.color = 0xFF37474F.toInt()
        c.drawRect(x + 50f, gy - 290f, x + 120f, gy - 250f, fill)
        c.drawRect(x + 390f, gy - 290f, x + 460f, gy - 250f, fill)
        g.boss?.let { b ->
            if (b.alive) {
                // closed barrier in front of the station while the boss lives
                for (k in 0 until 4) {
                    val by = gy - 40f - k * 45f
                    for (s in 0 until 6) {
                        fill.color = if ((s + k) % 2 == 0) 0xFFD32F2F.toInt() else Color.WHITE
                        c.drawRect(x - 130f + s * 40f, by - 14f, x - 90f + s * 40f, by, fill)
                    }
                }
                fill.color = 0xFF424242.toInt()
                c.drawRect(x - 140f, gy - 190f, x - 125f, gy, fill)
                c.drawRect(x + 105f, gy - 190f, x + 120f, gy, fill)
            }
        }
        fill.color = 0xFF2E7D32.toInt()
        c.drawRect(x + 110f, gy - 230f, x + 400f, gy - 170f, fill)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 40f
        text.color = Color.WHITE
        c.drawText("TRẠM CỨU HỘ", x + 255f, gy - 186f, text)
        stroke.color = 0xFFECEFF1.toInt()
        stroke.strokeWidth = 6f
        c.drawLine(x + 255f, gy - 230f, x + 255f, gy - 380f, stroke)
        fill.color = 0xFF43A047.toInt()
        val wave = sin(g.anim * 6f) * 8f
        path.reset()
        path.moveTo(x + 255f, gy - 380f)
        path.lineTo(x + 340f, gy - 365f + wave)
        path.lineTo(x + 255f, gy - 340f)
        path.close()
        c.drawPath(path, fill)
        text.textSize = 34f
        c.drawText("ĐÍCH", x, gy - 220f, text)
    }

    private fun drawCheckpoints(c: Canvas) {
        for (k in 1 until Game.LEVELS_PER_STAGE) {
            val x = g.goalX * k / Game.LEVELS_PER_STAGE
            if (x < g.camX - 100f || x > g.camX + g.viewW + 100f) continue
            val gy = g.terrain.heightAt(x)
            stroke.color = 0xFFECEFF1.toInt()
            stroke.strokeWidth = 5f
            c.drawLine(x, gy, x, gy - 170f, stroke)
            fill.color = 0xFF1E88E5.toInt()
            c.drawRect(x, gy - 170f, x + 100f, gy - 125f, fill)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 24f
            text.color = Color.WHITE
            c.drawText("MÀN ${k + 1}", x + 50f, gy - 139f, text)
        }
    }

    private fun drawRecord(c: Canvas) {
        val m = g.save.best[g.stage]
        if (m <= 0) return
        val x = m * Game.METER
        if (x < g.camX - 100f || x > g.camX + g.viewW + 100f || x >= g.goalX) return
        val gy = g.terrain.heightAt(x)
        stroke.color = Color.WHITE
        stroke.strokeWidth = 4f
        c.drawLine(x, gy, x, gy - 150f, stroke)
        fill.color = 0xFFFFC107.toInt()
        c.drawRect(x, gy - 150f, x + 80f, gy - 110f, fill)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 22f
        text.color = Color.BLACK
        c.drawText("KỶ LỤC", x + 40f, gy - 122f, text)
    }

    // ----------------------------------------------------------------- car

    private fun drawPlayerCar(c: Canvas) {
        val car = g.car
        val type = g.save.carType
        if (car.cloneT > 0f && g.state == State.PLAYING) {
            val blink = car.cloneT < 2f && (g.anim * 10f).toInt() % 2 == 0
            if (!blink) {
                c.saveLayerAlpha(null, 110)
                drawCar(c, type, car.x - 70f, car.y - 30f, car.angle, car.wheelRot, car.aim, false, g.save.weaponType, true)
                c.restore()
            }
        }
        // headlight beam
        if (!g.wrecked) {
            c.save()
            c.translate(car.x, car.y)
            c.rotate(car.angle * 180f / PI.toFloat())
            fill.color = 0x26FFF59D
            path.reset()
            path.moveTo(86f, -8f)
            path.lineTo(330f, -60f)
            path.lineTo(330f, 50f)
            path.close()
            c.drawPath(path, fill)
            c.restore()
        }
        val hurt = car.hurtT > 0f && (g.anim * 20f).toInt() % 2 == 0
        if (!hurt) drawCar(c, type, car.x, car.y, car.angle, car.wheelRot, car.aim, car.boosting, g.save.weaponType, false, car.compress, g.wrecked)
        if (car.shieldT > 0f) {
            val fade = if (car.shieldT < 1.5f && (g.anim * 8f).toInt() % 2 == 0) 0.4f else 1f
            fill.color = 0x3340C4FF
            fill.alpha = (0x33 * fade).toInt()
            c.drawCircle(car.x, car.y - 10f, 120f, fill)
            fill.alpha = 255
            stroke.color = 0xFF40C4FF.toInt()
            stroke.alpha = (220 * fade).toInt()
            stroke.strokeWidth = 5f + sin(g.anim * 10f) * 2f
            c.drawCircle(car.x, car.y - 10f, 120f, stroke)
            stroke.alpha = 255
        }
    }

    /** Draws a car around its body centre (x forward, y down), wheels at [Car.WHEEL_X]. */

    fun drawCar(
        c: Canvas,
        type: CarType,
        x: Float,
        y: Float,
        angle: Float,
        wheelRot: Float,
        aim: Float,
        boosting: Boolean,
        weapon: WeaponType,
        ghost: Boolean,
        compress: FloatArray? = null,
        wreck: Boolean = false,
    ) {
        c.save()
        c.translate(x, y)
        c.rotate(angle * 180f / PI.toFloat())
        val body = when {
            wreck -> 0xFF2E2A27.toInt()
            ghost -> 0xFF64FFDA.toInt()
            else -> type.color
        }

        // suspension struts and wheels (behind the body)
        for (i in 0..1) {
            val wy = Car.WHEEL_Y - min(12f, (compress?.get(i) ?: 0f) * 0.6f)
            stroke.color = 0xFF37474F.toInt()
            stroke.strokeWidth = 7f
            c.drawLine(Car.WHEEL_X[i], 4f, Car.WHEEL_X[i], wy, stroke)
        }

        // booster rocket on the back
        fill.color = 0xFF616161.toInt()
        c.drawRoundRect(-104f, -30f, -66f, -10f, 8f, 8f, fill)
        fill.color = 0x44FFFFFF
        c.drawRoundRect(-102f, -28f, -70f, -22f, 4f, 4f, fill)
        fill.color = 0xFFB71C1C.toInt()
        c.drawRect(-74f, -32f, -66f, -8f, fill)
        if (boosting) {
            val f = 30f + Random.nextFloat() * 40f
            fill.color = 0xCCFF6D00.toInt()
            path.reset()
            path.moveTo(-104f, -32f)
            path.lineTo(-104f - f - 40f, -20f)
            path.lineTo(-104f, -8f)
            path.close()
            c.drawPath(path, fill)
            fill.color = 0xFFFFEB3B.toInt()
            path.reset()
            path.moveTo(-104f, -26f)
            path.lineTo(-104f - f * 0.6f, -20f)
            path.lineTo(-104f, -14f)
            path.close()
            c.drawPath(path, fill)
            fill.color = 0xFFFFFFFF.toInt()
            c.drawCircle(-106f, -20f, 5f, fill)
        }

        path.reset()
        when (type) {
            CarType.PICKUP -> {
                path.moveTo(-86f, -14f); path.lineTo(-24f, -14f); path.lineTo(-18f, -44f)
                path.lineTo(22f, -44f); path.lineTo(40f, -18f); path.lineTo(84f, -12f)
                path.lineTo(88f, 12f); path.lineTo(84f, 20f); path.lineTo(-84f, 20f); path.lineTo(-88f, 0f)
            }
            CarType.MUSCLE -> {
                path.moveTo(-88f, -8f); path.lineTo(-60f, -18f); path.lineTo(-30f, -20f)
                path.lineTo(-12f, -38f); path.lineTo(26f, -38f); path.lineTo(48f, -18f)
                path.lineTo(88f, -12f); path.lineTo(90f, 10f); path.lineTo(84f, 16f); path.lineTo(-86f, 16f)
            }
            CarType.VAN -> {
                path.moveTo(-88f, -58f); path.lineTo(36f, -58f); path.lineTo(58f, -22f)
                path.lineTo(86f, -16f); path.lineTo(88f, 14f); path.lineTo(82f, 20f); path.lineTo(-86f, 20f)
            }
            CarType.TANK -> {
                path.moveTo(-90f, -22f); path.lineTo(-60f, -40f); path.lineTo(30f, -40f)
                path.lineTo(60f, -18f); path.lineTo(92f, -14f); path.lineTo(96f, 14f)
                path.lineTo(86f, 22f); path.lineTo(-86f, 22f)
            }
        }
        path.close()
        fill.color = body
        c.drawPath(path, fill)
        // shading: bright upper band, dark lower band, a few scratches
        c.save()
        c.clipPath(path)
        fill.color = 0x40FFFFFF
        c.drawRect(-120f, -80f, 120f, -24f, fill)
        fill.color = 0x26FFFFFF
        c.drawRect(-120f, -24f, 120f, -14f, fill)
        fill.color = 0x40000000
        c.drawRect(-120f, 6f, 120f, 40f, fill)
        fill.color = 0x30000000
        c.drawCircle(-50f, 2f, 6f, fill)
        c.drawCircle(60f, -4f, 4f, fill)
        c.drawCircle(-20f, 10f, 5f, fill)
        if (wreck) {
            // glowing embers on the burnt shell
            for (k in 0 until 6) {
                val on = (g.anim * 8f + k * 1.7f).toInt() % 3 != 0
                fill.color = if (on) 0xFFFF6D00.toInt() else 0xFF8D2B00.toInt()
                c.drawCircle(-70f + k * 28f, -10f + (k % 3) * 8f, 3.5f, fill)
            }
        }
        c.restore()
        stroke.color = 0xAA1A1A1A.toInt()
        stroke.strokeWidth = 3f
        c.drawPath(path, stroke)

        val glass = when {
            wreck -> 0xFF111111.toInt()
            ghost -> 0x8800695C.toInt()
            else -> 0xFF263238.toInt()
        }
        fill.color = glass
        stroke.color = 0x66FFFFFF
        stroke.strokeWidth = 3f
        when (type) {
            CarType.PICKUP -> {
                path.reset()
                path.moveTo(-10f, -38f); path.lineTo(18f, -38f); path.lineTo(32f, -18f); path.lineTo(-10f, -18f); path.close()
                c.drawPath(path, fill)
                if (!wreck) c.drawLine(4f, -36f, 14f, -20f, stroke)
                fill.color = 0x33000000
                c.drawRect(-82f, -12f, -28f, -4f, fill)
                stroke.color = 0x55000000
                c.drawLine(-82f, -14f, -26f, -14f, stroke)
            }
            CarType.MUSCLE -> {
                path.reset()
                path.moveTo(-6f, -33f); path.lineTo(22f, -33f); path.lineTo(38f, -18f); path.lineTo(-6f, -18f); path.close()
                c.drawPath(path, fill)
                if (!wreck) c.drawLine(8f, -31f, 18f, -20f, stroke)
                if (!wreck) {
                    fill.color = Color.WHITE
                    c.drawRect(-80f, -6f, 86f, 0f, fill)
                }
                fill.color = 0xFF212121.toInt()
                c.drawRect(-88f, -22f, -70f, -16f, fill)
            }
            CarType.VAN -> {
                path.reset()
                path.moveTo(38f, -52f); path.lineTo(52f, -26f); path.lineTo(20f, -26f); path.lineTo(20f, -52f); path.close()
                c.drawPath(path, fill)
                if (!wreck) c.drawLine(32f, -48f, 40f, -30f, stroke)
                if (!wreck) {
                    fill.color = 0xFFE53935.toInt()
                    c.drawRect(-60f, -40f, -20f, -30f, fill)
                    c.drawRect(-45f, -50f, -35f, -20f, fill)
                }
            }
            CarType.TANK -> {
                c.drawRect(-20f, -34f, 20f, -24f, fill)
                fill.color = 0x66000000
                for (k in 0 until 6) c.drawCircle(-70f + k * 28f, 10f, 3f, fill)
                fill.color = 0xFF33401F.toInt()
                c.drawRect(-86f, 0f, 86f, 6f, fill)
            }
        }
        // front ram
        fill.color = 0xFF9E9E9E.toInt()
        c.drawRect(84f, -8f, 98f, 20f, fill)
        fill.color = 0x55FFFFFF
        c.drawRect(84f, -8f, 98f, -2f, fill)
        fill.color = 0xFF757575.toInt()
        for (k in 0 until 3) {
            path.reset()
            path.moveTo(98f, -6f + k * 9f); path.lineTo(112f, -2f + k * 9f); path.lineTo(98f, 2f + k * 9f); path.close()
            c.drawPath(path, fill)
        }
        if (!wreck) {
            fill.color = 0x66FFF59D
            c.drawCircle(84f, -6f, 10f, fill)
            fill.color = 0xFFFFF9C4.toInt()
            c.drawCircle(84f, -6f, 5f, fill)
            fill.color = 0xFFFF1744.toInt()
            c.drawRect(-88f, -6f, -82f, 4f, fill)
        }

        for (i in 0..1) {
            val wy = Car.WHEEL_Y - min(12f, (compress?.get(i) ?: 0f) * 0.6f)
            drawWheel(c, Car.WHEEL_X[i], wy, wheelRot, type == CarType.TANK || wreck)
        }

        if (!wreck) {
            // roof weapon (aims independently)
            c.save()
            c.translate(-10f, -Car.HALF_H - 22f)
            fill.color = 0xFF424242.toInt()
            c.drawRect(-14f, 0f, 14f, 12f, fill)
            c.rotate((aim - angle) * 180f / PI.toFloat())
            when (weapon) {
                WeaponType.GUN -> {
                    fill.color = 0xFF37474F.toInt()
                    c.drawRoundRect(-14f, -10f, 16f, 8f, 4f, 4f, fill)
                    fill.color = 0x44FFFFFF
                    c.drawRect(-12f, -9f, 14f, -5f, fill)
                    fill.color = 0xFF212121.toInt()
                    c.drawRect(14f, -6f, 58f, 2f, fill)
                }
                WeaponType.CROSSBOW -> {
                    fill.color = 0xFF6D4C41.toInt()
                    c.drawRect(-16f, -5f, 46f, 3f, fill)
                    stroke.color = 0xFF5D4037.toInt()
                    stroke.strokeWidth = 5f
                    r.set(14f, -30f, 44f, 28f)
                    c.drawArc(r, -90f, 180f, false, stroke)
                    stroke.color = 0xFFECEFF1.toInt()
                    stroke.strokeWidth = 2f
                    c.drawLine(29f, -30f, 18f, -1f, stroke)
                    c.drawLine(29f, 28f, 18f, -1f, stroke)
                }
                WeaponType.CATAPULT -> {
                    fill.color = 0xFF795548.toInt()
                    c.drawRect(-18f, -4f, 18f, 6f, fill)
                    stroke.color = 0xFF6D4C41.toInt()
                    stroke.strokeWidth = 7f
                    c.drawLine(0f, 0f, -30f, -34f, stroke)
                    fill.color = 0xFF5D4037.toInt()
                    c.drawCircle(-32f, -38f, 11f, fill)
                }
            }
            c.restore()
        }
        c.restore()
    }

    private fun drawWheel(c: Canvas, x: Float, y: Float, rot: Float, dark: Boolean) {
        fill.color = 0xFF151515.toInt()
        c.drawCircle(x, y, Car.WHEEL_R, fill)
        // tread blocks
        fill.color = 0xFF2B2B2B.toInt()
        for (k in 0 until 10) {
            val a = rot + k * 0.628f
            c.drawCircle(x + cos(a) * 21f, y + sin(a) * 21f, 4f, fill)
        }
        fill.color = if (dark) 0xFF5D6B3A.toInt() else 0xFFB0B0B0.toInt()
        c.drawCircle(x, y, 12f, fill)
        fill.color = 0x55FFFFFF
        c.drawCircle(x - 3f, y - 3f, 6f, fill)
        stroke.color = 0xFF424242.toInt()
        stroke.strokeWidth = 3f
        for (k in 0 until 5) {
            val a = rot + k * 1.2566f
            c.drawLine(x, y, x + cos(a) * 11f, y + sin(a) * 11f, stroke)
        }
        fill.color = 0xFF424242.toInt()
        c.drawCircle(x, y, 3f, fill)
    }

    /** Zombie with the origin at its feet. */

    // ------------------------------------------------------------- zombies

    private fun drawZombie(c: Canvas, z: Zombie) {
        val dir = sign(g.car.x - z.x).let { if (it == 0f) -1f else it }
        c.save()
        c.translate(z.x, z.y)
        zombieFigure(c, z.type.w, z.type.h, z.shirt, sin(z.phase), dir, z.type == ZombieType.FAT)
        c.restore()
    }

    /** Zombie with the origin at its feet. */
    private fun zombieFigure(c: Canvas, w: Float, h: Float, shirt: Int, swing: Float, dir: Float, fat: Boolean) {
        val skin = 0xFF8BC34A.toInt()
        val skinDark = 0xFF689F38.toInt()
        val outline = 0xFF1B1B1B.toInt()
        val legW = if (fat) 14f else 9f
        val armW = if (fat) 10f else 7f
        // outlines first
        stroke.color = outline
        stroke.strokeWidth = legW + 4f
        c.drawLine(0f, -h * 0.45f, swing * 12f, 0f, stroke)
        c.drawLine(0f, -h * 0.45f, -swing * 12f, 0f, stroke)
        stroke.strokeWidth = armW + 4f
        c.drawLine(0f, -h * 0.76f, dir * w * 0.95f, -h * 0.72f + swing * 4f, stroke)
        c.drawLine(0f, -h * 0.74f, dir * w * 1.05f, -h * 0.7f - swing * 4f, stroke)
        // legs (torn trousers) and feet
        stroke.color = 0xFF37474F.toInt()
        stroke.strokeWidth = legW
        c.drawLine(0f, -h * 0.45f, swing * 12f, 0f, stroke)
        c.drawLine(0f, -h * 0.45f, -swing * 12f, 0f, stroke)
        fill.color = 0xFF3E2723.toInt()
        c.drawOval(swing * 12f - 6f + dir * 3f, -5f, swing * 12f + 6f + dir * 5f, 2f, fill)
        c.drawOval(-swing * 12f - 6f + dir * 3f, -5f, -swing * 12f + 6f + dir * 5f, 2f, fill)
        // back arm
        stroke.color = skinDark
        stroke.strokeWidth = armW
        c.drawLine(0f, -h * 0.76f, dir * w * 0.95f, -h * 0.72f + swing * 4f, stroke)
        // torso with torn hem and a blood stain
        r.set(-w / 2, -h * 0.86f, w / 2, -h * 0.42f)
        fill.color = outline
        c.drawRoundRect(r.left - 2f, r.top - 2f, r.right + 2f, r.bottom + 2f, 12f, 12f, fill)
        fill.color = shirt
        c.drawRoundRect(r, 10f, 10f, fill)
        fill.color = 0x33FFFFFF
        c.drawRect(-w / 2 + 3f, -h * 0.84f, w / 2 - 3f, -h * 0.78f, fill)
        fill.color = 0xFF2E2E2E.toInt()
        c.drawRect(-w / 2, -h * 0.47f, w / 2, -h * 0.43f, fill)
        fill.color = 0xAA7F0000.toInt()
        c.drawCircle(w * 0.12f, -h * 0.6f, w * 0.16f, fill)
        fill.color = 0x55000000
        c.drawRect(w * 0.1f, -h * 0.75f, w * 0.3f, -h * 0.7f, fill)
        // head
        val hr = if (fat) h * 0.12f else h * 0.11f
        val hx = dir * 3f
        val hy = -h * 0.86f - hr * 0.9f
        fill.color = outline
        c.drawCircle(hx, hy, hr + 2.5f, fill)
        fill.color = skin
        c.drawCircle(hx, hy, hr, fill)
        fill.color = skinDark
        c.drawCircle(hx - dir * hr * 0.4f, hy - hr * 0.3f, hr * 0.35f, fill)
        // glowing eye and jaw
        fill.color = 0x66FF1744
        c.drawCircle(dir * (hr * 0.5f + 3f), hy - hr * 0.1f, 5f, fill)
        fill.color = 0xFFFF1744.toInt()
        c.drawCircle(dir * (hr * 0.5f + 3f), hy - hr * 0.1f, 2.6f, fill)
        fill.color = 0xFF33691E.toInt()
        val mx0 = dir * hr * 0.2f
        val mx1 = dir * (hr * 0.8f + 3f)
        c.drawRect(min(mx0, mx1), hy + hr * 0.4f, max(mx0, mx1), hy + hr * 0.6f, fill)
        // front arm reaching for the car
        stroke.color = skin
        stroke.strokeWidth = armW
        c.drawLine(0f, -h * 0.74f, dir * w * 1.05f, -h * 0.7f - swing * 4f, stroke)
    }

    private fun drawDebris(c: Canvas, d: Debris) {
        val a = if (d.life < 1f) (255 * d.life).toInt().coerceIn(0, 255) else 255
        c.save()
        c.translate(d.x, d.y)
        c.rotate(d.rot * 180f / PI.toFloat())
        when (d.kind) {
            Debris.ZOMBIE, Debris.FAT_ZOMBIE -> {
                c.saveLayerAlpha(null, a)
                c.translate(0f, d.h / 2)
                zombieFigure(c, d.w, d.h, d.color, 0.8f, 1f, d.kind == Debris.FAT_ZOMBIE)
                c.restore()
            }
            Debris.PLANK -> {
                fill.color = d.color
                fill.alpha = a
                c.drawRect(-d.w / 2, -d.h / 2, d.w / 2, d.h / 2, fill)
                fill.alpha = 255
            }
            else -> {
                fill.color = d.color
                fill.alpha = a
                c.drawRect(-d.w / 2, -d.h / 2, d.w / 2, d.h / 2, fill)
                fill.color = 0xFF5D4037.toInt()
                fill.alpha = a
                c.drawRect(-d.w / 2, -d.h / 2, d.w / 2, -d.h / 4, fill)
                fill.alpha = 255
            }
        }
        c.restore()
    }

    // ----------------------------------------------------------- obstacles

    private fun drawObstacle(c: Canvas, o: Obstacle) {
        val w = o.type.w
        val h = o.type.h
        val l = o.x - w / 2
        val t = o.y - h
        when (o.type) {
            ObstacleType.CRATE -> {
                fill.color = 0xFFA1887F.toInt()
                c.drawRect(l, t, l + w, t + h, fill)
                stroke.color = 0xFF5D4037.toInt()
                stroke.strokeWidth = 5f
                c.drawRect(l + 2f, t + 2f, l + w - 2f, t + h - 2f, stroke)
                c.drawLine(l + 6f, t + 6f, l + w - 6f, t + h - 6f, stroke)
                c.drawLine(l + w - 6f, t + 6f, l + 6f, t + h - 6f, stroke)
            }
            ObstacleType.BARREL -> {
                fill.color = 0xFFC62828.toInt()
                c.drawRoundRect(l, t, l + w, t + h, 10f, 10f, fill)
                fill.color = 0xFF8E0000.toInt()
                c.drawRect(l, t + 12f, l + w, t + 18f, fill)
                c.drawRect(l, t + h - 18f, l + w, t + h - 12f, fill)
                fill.color = 0xFFFFC107.toInt()
                path.reset()
                path.moveTo(o.x, t + 24f); path.lineTo(o.x + 11f, t + 44f); path.lineTo(o.x - 11f, t + 44f); path.close()
                c.drawPath(path, fill)
            }
            ObstacleType.WRECK -> {
                fill.color = 0xFF6D4C41.toInt()
                path.reset()
                path.moveTo(l, t + h - 14f); path.lineTo(l + 6f, t + 26f); path.lineTo(l + 50f, t + 22f)
                path.lineTo(l + 70f, t); path.lineTo(l + 130f, t); path.lineTo(l + 150f, t + 24f)
                path.lineTo(l + w, t + 30f); path.lineTo(l + w, t + h - 14f); path.close()
                c.drawPath(path, fill)
                fill.color = 0xFF3E2723.toInt()
                c.drawRect(l + 76f, t + 6f, l + 124f, t + 22f, fill)
                fill.color = 0xFFBF360C.toInt()
                c.drawCircle(l + 40f, t + 40f, 8f, fill)
                c.drawCircle(l + 110f, t + 46f, 6f, fill)
                fill.color = 0xFF212121.toInt()
                c.drawOval(l + 14f, t + h - 26f, l + 54f, t + h, fill)
                c.drawOval(l + w - 54f, t + h - 26f, l + w - 14f, t + h, fill)
            }
        }
        if (o.flash > 0f) {
            fill.color = 0x88FFFFFF.toInt()
            c.drawRect(l, t, l + w, t + h, fill)
        }
        if (o.barT > 0f || o.ramHp < 1f) drawObstacleBar(c, o, t)
    }

    private fun drawBoss(c: Canvas, b: Boss) {
        val dir = if (g.car.x >= b.x) 1f else -1f
        val w = b.w
        val h = b.h
        val stomp = if (b.slamAnim > 0f) sin(b.slamAnim / 0.5f * 3.14f) else 0f
        val walk = sin(b.anim * 3f)
        c.save()
        c.translate(b.x, b.y)
        fill.color = 0x55000000
        c.drawOval(-w, -10f, w, 10f, fill)
        c.translate(0f, -stomp * 30f)
        val hit = b.flash > 0f
        val skin = if (hit) 0xFFF1F8E9.toInt() else when (b.tier) {
            1 -> 0xFF7CB342.toInt()
            2 -> 0xFF8E7CC3.toInt()
            3 -> 0xFFBF5B2C.toInt()
            else -> 0xFF5C8A3A.toInt()
        }
        val skinDark = if (hit) 0xFFCFD8DC.toInt() else when (b.tier) {
            1 -> 0xFF558B2F.toInt()
            2 -> 0xFF5E4B8B.toInt()
            3 -> 0xFF7A2E12.toInt()
            else -> 0xFF33521E.toInt()
        }
        val outline = 0xFF1B1B1B.toInt()
        // legs
        for (side in intArrayOf(-1, 1)) {
            val fx = side * w * 0.25f + walk * side * 10f
            stroke.color = outline
            stroke.strokeWidth = w * 0.32f + 6f
            c.drawLine(side * w * 0.2f, -h * 0.42f, fx, -8f, stroke)
            stroke.color = 0xFF3E2723.toInt()
            stroke.strokeWidth = w * 0.32f
            c.drawLine(side * w * 0.2f, -h * 0.42f, fx, -8f, stroke)
            fill.color = outline
            c.drawOval(fx - w * 0.22f + dir * 8f, -18f, fx + w * 0.22f + dir * 8f, 2f, fill)
        }
        // back arm
        stroke.color = outline
        stroke.strokeWidth = w * 0.26f + 6f
        c.drawLine(-dir * w * 0.35f, -h * 0.8f, -dir * w * 0.55f, -h * 0.45f + walk * 8f, stroke)
        stroke.color = skinDark
        stroke.strokeWidth = w * 0.26f
        c.drawLine(-dir * w * 0.35f, -h * 0.8f, -dir * w * 0.55f, -h * 0.45f + walk * 8f, stroke)
        // torso
        r.set(-w * 0.55f, -h * 0.9f, w * 0.55f, -h * 0.38f)
        fill.color = outline
        c.drawRoundRect(r.left - 4f, r.top - 4f, r.right + 4f, r.bottom + 4f, 30f, 30f, fill)
        fill.color = skin
        c.drawRoundRect(r, 28f, 28f, fill)
        fill.color = skinDark
        c.drawCircle(-w * 0.2f, -h * 0.72f, w * 0.18f, fill)
        c.drawCircle(w * 0.2f, -h * 0.72f, w * 0.18f, fill)
        fill.color = 0x40000000
        c.drawRect(-w * 0.55f, -h * 0.5f, w * 0.55f, -h * 0.38f, fill)
        // torn trousers / belt
        fill.color = 0xFF4E342E.toInt()
        c.drawRect(-w * 0.55f, -h * 0.46f, w * 0.55f, -h * 0.38f, fill)
        when (b.tier) {
            2 -> { // armour plates
                fill.color = 0xFF78909C.toInt()
                c.drawRoundRect(-w * 0.45f, -h * 0.86f, w * 0.45f, -h * 0.62f, 10f, 10f, fill)
                fill.color = 0xFF455A64.toInt()
                for (k in 0 until 4) c.drawCircle(-w * 0.35f + k * w * 0.23f, -h * 0.82f, 4f, fill)
            }
            3 -> { // glowing lava cracks
                stroke.color = Color.argb((160 + 90 * sin(b.anim * 6f)).toInt(), 255, 171, 0)
                stroke.strokeWidth = 5f
                c.drawLine(-w * 0.3f, -h * 0.85f, -w * 0.05f, -h * 0.6f, stroke)
                c.drawLine(-w * 0.05f, -h * 0.6f, w * 0.25f, -h * 0.7f, stroke)
                c.drawLine(w * 0.1f, -h * 0.55f, w * 0.35f, -h * 0.45f, stroke)
            }
            4 -> { // chains
                stroke.color = 0xFFB0BEC5.toInt()
                stroke.strokeWidth = 4f
                c.drawLine(-w * 0.5f, -h * 0.85f, w * 0.45f, -h * 0.5f, stroke)
            }
        }
        // head
        val hr = w * 0.3f
        val hy = -h * 0.9f - hr * 0.7f
        fill.color = outline
        c.drawCircle(dir * 6f, hy, hr + 4f, fill)
        fill.color = skin
        c.drawCircle(dir * 6f, hy, hr, fill)
        fill.color = skinDark
        c.drawRect(dir * 6f - hr * 0.7f, hy - hr * 0.35f, dir * 6f + hr * 0.7f, hy - hr * 0.2f, fill)
        val glow = 0.6f + 0.4f * sin(b.anim * 8f)
        fill.color = Color.argb((120 * glow).toInt(), 255, 23, 68)
        c.drawCircle(dir * (hr * 0.45f + 6f), hy - hr * 0.05f, 11f, fill)
        c.drawCircle(dir * (hr * 0.05f + 6f), hy - hr * 0.05f, 9f, fill)
        fill.color = 0xFFFF1744.toInt()
        c.drawCircle(dir * (hr * 0.45f + 6f), hy - hr * 0.05f, 5f, fill)
        c.drawCircle(dir * (hr * 0.05f + 6f), hy - hr * 0.05f, 4f, fill)
        // jaw with teeth
        fill.color = 0xFF3E0F0F.toInt()
        val jx0 = dir * 6f - hr * 0.1f
        c.drawRect(min(jx0, jx0 + dir * hr * 0.8f), hy + hr * 0.3f, max(jx0, jx0 + dir * hr * 0.8f), hy + hr * 0.62f, fill)
        fill.color = 0xFFFFF8E1.toInt()
        for (k in 0 until 4) {
            val tx = jx0 + dir * (k * hr * 0.2f + 4f)
            c.drawRect(min(tx, tx + dir * 8f), hy + hr * 0.3f, max(tx, tx + dir * 8f), hy + hr * 0.42f, fill)
        }
        if (b.tier == 4) { // crown
            fill.color = 0xFFFFC107.toInt()
            path.reset()
            val cy = hy - hr * 0.85f
            path.moveTo(dir * 6f - hr * 0.6f, cy + 16f)
            path.lineTo(dir * 6f - hr * 0.6f, cy - 18f); path.lineTo(dir * 6f - hr * 0.3f, cy)
            path.lineTo(dir * 6f, cy - 26f); path.lineTo(dir * 6f + hr * 0.3f, cy)
            path.lineTo(dir * 6f + hr * 0.6f, cy - 18f); path.lineTo(dir * 6f + hr * 0.6f, cy + 16f)
            path.close()
            c.drawPath(path, fill)
            fill.color = 0xFFE53935.toInt()
            c.drawCircle(dir * 6f, cy - 4f, 6f, fill)
        }
        // front arm: raised with a boulder while throwing, reaching otherwise
        val raise = (b.throwAnim / 0.45f).coerceIn(0f, 1f)
        val hx = dir * (w * 0.75f - raise * w * 0.5f)
        val hyArm = -h * (0.62f + raise * 0.5f) + walk * 6f
        stroke.color = outline
        stroke.strokeWidth = w * 0.28f + 6f
        c.drawLine(dir * w * 0.4f, -h * 0.82f, hx, hyArm, stroke)
        stroke.color = skin
        stroke.strokeWidth = w * 0.28f
        c.drawLine(dir * w * 0.4f, -h * 0.82f, hx, hyArm, stroke)
        if (raise > 0.2f) {
            fill.color = 0xFF6D4C41.toInt()
            c.drawCircle(hx, hyArm - 26f, 30f, fill)
        }
        c.restore()
    }

    private fun drawBossRock(c: Canvas, rk: BossRock) {
        c.save()
        c.translate(rk.x, rk.y)
        c.rotate(rk.rot * 57.3f)
        fill.color = 0x55000000
        c.drawCircle(4f, 6f, 30f, fill)
        fill.color = 0xFF6D4C41.toInt()
        c.drawCircle(0f, 0f, 30f, fill)
        fill.color = 0xFF8D6E63.toInt()
        c.drawCircle(-9f, -9f, 13f, fill)
        stroke.color = 0xFF3E2723.toInt()
        stroke.strokeWidth = 3f
        c.drawLine(-12f, 6f, 10f, -4f, stroke)
        c.restore()
    }

    /** Durability bar in four 25% notches above an obstacle the car failed to smash. */
    private fun drawObstacleBar(c: Canvas, o: Obstacle, top: Float) {
        val f = o.barFrac
        val bw = max(90f, o.type.w)
        val bx = o.x - bw / 2
        val by = top - 26f
        r.set(bx - 3f, by - 3f, bx + bw + 3f, by + 17f)
        fill.color = 0xCC1B1B1B.toInt()
        c.drawRoundRect(r, 8f, 8f, fill)
        if (f > 0f) {
            fill.color = when {
                f > 0.5f -> 0xFF43A047.toInt()
                f > 0.25f -> 0xFFFFA000.toInt()
                else -> 0xFFE53935.toInt()
            }
            r.set(bx, by, bx + bw * f, by + 14f)
            c.drawRoundRect(r, 6f, 6f, fill)
            fill.color = 0x44FFFFFF
            r.set(bx + 2f, by + 2f, max(bx + 3f, bx + bw * f - 2f), by + 6f)
            c.drawRoundRect(r, 3f, 3f, fill)
        }
        fill.color = 0xCC1B1B1B.toInt()
        for (k in 1 until 4) c.drawRect(bx + bw * k / 4f - 1.5f, by, bx + bw * k / 4f + 1.5f, by + 14f, fill)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 20f
        text.style = Paint.Style.STROKE
        text.strokeWidth = 4f
        text.color = 0xCC000000.toInt()
        c.drawText("${(f * 100).toInt()}%", o.x, by - 6f, text)
        text.style = Paint.Style.FILL
        text.color = Color.WHITE
        c.drawText("${(f * 100).toInt()}%", o.x, by - 6f, text)
    }

    private fun drawItem(c: Canvas, it: Item) {
        val bob = sin(g.anim * 4f + it.x * 0.01f) * 8f
        val y = it.y + bob
        val small = it.type == ItemType.COIN
        val rad = if (small) 20f else 30f
        fill.color = it.type.color
        fill.alpha = 70
        c.drawCircle(it.x, y, rad + 10f + sin(g.anim * 8f) * 3f, fill)
        fill.alpha = 255
        c.drawCircle(it.x, y, rad, fill)
        stroke.color = Color.WHITE
        stroke.strokeWidth = 3f
        c.drawCircle(it.x, y, rad, stroke)
        text.textAlign = Paint.Align.CENTER
        text.textSize = if (small) 26f else 32f
        text.color = if (small) 0xFF5D4037.toInt() else Color.WHITE
        c.drawText(it.type.icon, it.x, y + text.textSize * 0.36f, text)
    }

    private fun drawShots(c: Canvas) {
        for (s in g.shots) {
            val l = hypot(s.vx, s.vy).coerceAtLeast(1f)
            when (s.kind) {
                WeaponType.GUN -> {
                    stroke.color = 0xFFFFEB3B.toInt()
                    stroke.strokeWidth = 4f
                    c.drawLine(s.x, s.y, s.x - s.vx / l * 26f, s.y - s.vy / l * 26f, stroke)
                }
                WeaponType.CROSSBOW -> {
                    stroke.color = 0xFF8D6E63.toInt()
                    stroke.strokeWidth = 5f
                    c.drawLine(s.x, s.y, s.x - s.vx / l * 50f, s.y - s.vy / l * 50f, stroke)
                    fill.color = 0xFFB0BEC5.toInt()
                    c.drawCircle(s.x, s.y, 6f, fill)
                }
                WeaponType.CATAPULT -> {
                    fill.color = 0xFF6D4C41.toInt()
                    c.drawCircle(s.x, s.y, 18f, fill)
                    fill.color = 0xFF8D6E63.toInt()
                    c.drawCircle(s.x - 5f, s.y - 5f, 8f, fill)
                }
            }
        }
    }

    // --------------------------------------------------------------- storm

    private fun stormEdge(y: Float) = g.stormX + sin(y * 0.02f + g.anim * 4f) * 30f

    private fun drawStorm(c: Canvas) {
        val sx = g.stormX
        if (sx < g.camX - 100f) return
        val top = g.camY - 300f
        val bottom = g.camY + g.viewH + 300f
        val pulse = 0.5f + 0.5f * sin(g.anim * 5f)
        path.reset()
        path.moveTo(g.camX - 400f, top)
        var y = top
        while (y <= bottom) {
            path.lineTo(stormEdge(y), y)
            y += 30f
        }
        path.lineTo(g.camX - 400f, bottom)
        path.close()
        fill.color = Color.argb((150 + 50 * pulse).toInt(), 106, 27, 154)
        c.drawPath(path, fill)
        path.reset()
        y = top
        path.moveTo(stormEdge(y), y)
        while (y <= bottom) {
            path.lineTo(stormEdge(y), y)
            y += 30f
        }
        stroke.color = 0xFFE040FB.toInt()
        stroke.strokeWidth = 8f
        stroke.alpha = (160 + 90 * pulse).toInt()
        c.drawPath(path, stroke)
        stroke.alpha = 255
        if (Random.nextFloat() < 0.08f) {
            stroke.color = 0xFFF3E5F5.toInt()
            stroke.strokeWidth = 4f
            var lx = sx - 150f - Random.nextFloat() * 300f
            var ly = top + 300f
            repeat(7) {
                val nx = lx + (Random.nextFloat() - 0.5f) * 80f
                val ny = ly + 70f
                c.drawLine(lx, ly, nx, ny, stroke)
                lx = nx; ly = ny
            }
        }
    }

    // ----------------------------------------------------------------- HUD

    private fun bar(c: Canvas, x: Float, y: Float, w: Float, frac: Float, color: Int, label: String) {
        text.textAlign = Paint.Align.CENTER
        text.textSize = 24f
        text.color = Color.WHITE
        c.drawText(label, x + 16f, y + 21f, text)
        r.set(x + 40f, y, x + 40f + w, y + 26f)
        fill.color = 0xCC1B1B1B.toInt()
        c.drawRoundRect(r, 13f, 13f, fill)
        val f = frac.coerceIn(0f, 1f)
        if (f > 0f) {
            r.set(x + 42f, y + 2f, x + 42f + (w - 4f) * f, y + 24f)
            fill.color = color
            c.drawRoundRect(r, 11f, 11f, fill)
            fill.color = 0x55FFFFFF
            r.set(r.left + 3f, r.top + 2f, max(r.left + 4f, r.right - 3f), r.top + 9f)
            c.drawRoundRect(r, 5f, 5f, fill)
        }
        // tick marks
        fill.color = 0x40000000
        for (k in 1 until 4) c.drawRect(x + 40f + w * k / 4f - 1f, y + 3f, x + 40f + w * k / 4f + 1f, y + 23f, fill)
    }

    private fun drawHud(c: Canvas) {
        val W = g.W
        val H = g.H
        val car = g.car

        // left: health, fuel, boost, ammo
        glassPanel(c, 12f, 12f, 330f, 176f)
        val hpF = car.hp / car.maxHp
        bar(c, 22f, 22f, 270f, hpF, if (hpF > 0.3f) 0xFF43A047.toInt() else 0xFFE53935.toInt(), "✚")
        val fuelLow = car.fuel / car.maxFuel < 0.2f && (g.anim * 4f).toInt() % 2 == 0
        bar(c, 22f, 58f, 270f, car.fuel / car.maxFuel, if (fuelLow) 0xFFFF5252.toInt() else 0xFFFF9800.toInt(), "⛽")
        bar(c, 22f, 94f, 270f, car.boost / car.maxBoost, 0xFF29B6F6.toInt(), "🚀")
        text.textAlign = Paint.Align.LEFT
        text.textSize = 28f
        text.color = Color.WHITE
        c.drawText("${g.save.weaponType.icon} ${car.ammo}", 26f, 158f, text)
        var sx = 150f
        if (car.shieldT > 0f) {
            text.color = 0xFF82B1FF.toInt()
            c.drawText("🛡${car.shieldT.toInt() + 1}", sx, 158f, text)
            sx += 80f
        }
        if (car.cloneT > 0f) {
            text.color = 0xFF64FFDA.toInt()
            c.drawText("👥${car.cloneT.toInt() + 1}", sx, 158f, text)
        }
        text.textAlign = Paint.Align.CENTER

        // top centre: progress bar (start -> goal) with level ticks, record and storm markers
        val pl = W * 0.3f
        val pr = W * 0.7f
        val py = 36f
        glassPanel(c, pl - 30f, 8f, pr + 64f, 132f)
        stroke.color = 0xCC1B1B1B.toInt()
        stroke.strokeWidth = 14f
        c.drawLine(pl, py, pr, py, stroke)
        val prog = (g.distanceM / g.goalM).coerceIn(0f, 1f)
        fill.color = 0xFFECEFF1.toInt()
        for (k in 1 until Game.LEVELS_PER_STAGE) {
            val kx = pl + (pr - pl) * k / Game.LEVELS_PER_STAGE
            c.drawRect(kx - 2f, py - 11f, kx + 2f, py + 11f, fill)
        }
        stroke.color = 0xFF29B6F6.toInt()
        stroke.strokeWidth = 9f
        c.drawLine(pl, py, pl + (pr - pl) * prog, py, stroke)
        val best = g.save.best[g.stage] / g.goalM
        if (best > 0f) {
            fill.color = 0xFFFFC107.toInt()
            val bx = pl + (pr - pl) * best.coerceIn(0f, 1f)
            c.drawRect(bx - 3f, py - 17f, bx + 3f, py + 17f, fill)
        }
        if (g.stormX > 0f) {
            val sxp = pl + (pr - pl) * (g.stormX / Game.METER / g.goalM).coerceIn(0f, 1f)
            fill.color = 0x66E040FB
            c.drawCircle(sxp, py, 17f, fill)
            fill.color = 0xFFE040FB.toInt()
            c.drawCircle(sxp, py, 10f, fill)
        }
        val cxp = pl + (pr - pl) * prog
        fill.color = Color.WHITE
        c.drawCircle(cxp, py, 15f, fill)
        fill.color = 0xFFE53935.toInt()
        c.drawCircle(cxp, py, 11f, fill)
        text.textSize = 30f
        c.drawText("🏁", pr + 30f, py + 10f, text)
        text.color = Color.WHITE
        fitText(c, "${g.distanceM.toInt()}m / ${g.goalM.toInt()}m  •  ẢI ${g.stage} — MÀN ${g.levelInStage}/${Game.LEVELS_PER_STAGE}  •  NGÀY ${g.save.day}", W / 2 + 17f, py + 44f, 24f, pr - pl + 70f)

        val closed = g.phase >= Game.PHASE_TIME.size
        val secs = ceil(g.phaseLeft).toInt()
        val timer = when {
            closed -> "VÒNG BO ĐÃ KHÉP"
            g.phase == 2 -> "BO 3: $secs"
            else -> "BO ${g.phase + 1}  ${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"
        }
        val gap = ((car.x - Car.HALF_LEN - g.stormX) / Game.METER).toInt()
        text.color = if (closed || g.phase == 2) 0xFFFF5252.toInt() else 0xFFEA80FC.toInt()
        fitText(c, "🌪 $timer   •   Bão cách ${max(0, gap)}m", W / 2 + 17f, py + 84f, 28f, pr - pl + 70f)

        // money & pause
        val moneyText = "+${g.runMoney}$"
        text.textSize = 32f
        val mw = text.measureText(moneyText)
        glassPanel(c, W - 132f - mw, 18f, W - 108f, 86f, 30f)
        text.textAlign = Paint.Align.RIGHT
        text.color = 0xFFFFD740.toInt()
        c.drawText(moneyText, W - 120f, 64f, text)
        text.textAlign = Paint.Align.CENTER
        drawButton(c, g.pauseBtn, false)

        // warnings
        val warn = when {
            g.wrecked -> null
            gap in 0..25 -> "⚠ BÃO ĐANG ĐUỔI KỊP!"
            car.fuel <= 0f -> "HẾT XĂNG!"
            car.flipT > 0.3f -> "LẬT XE! Nhấn ↺ ↻ để lật lại"
            car.hp < car.maxHp * 0.15f -> "🔥 XE ĐANG CHÁY!"
            else -> null
        }
        if (warn != null && g.state == State.PLAYING) {
            text.textSize = 42f
            text.style = Paint.Style.STROKE
            text.strokeWidth = 8f
            text.color = 0xCC000000.toInt()
            c.drawText(warn, W / 2, H * 0.4f, text)
            text.style = Paint.Style.FILL
            text.color = Color.argb((180 + 75 * sin(g.anim * 8f)).toInt(), 255, 82, 82)
            c.drawText(warn, W / 2, H * 0.4f, text)
        }
        if (g.stormHurt > 0f) {
            fill.color = 0x40E040FB
            c.drawRect(0f, 0f, W, H, fill)
        }
        g.boss?.let { b ->
            if (b.engaged && b.alive) {
                val bw = min(620f, W * 0.42f)
                val bl = W / 2 - bw / 2
                val bt = 148f
                glassPanel(c, bl - 16f, bt - 8f, bl + bw + 16f, bt + 58f, 16f)
                text.textAlign = Paint.Align.CENTER
                text.color = 0xFFFF8A80.toInt()
                fitText(c, "👹 ${b.name}", W / 2, bt + 18f, 22f, bw)
                r.set(bl, bt + 26f, bl + bw, bt + 46f)
                fill.color = 0xCC1B1B1B.toInt()
                c.drawRoundRect(r, 10f, 10f, fill)
                val f = (b.hp / b.maxHp).coerceIn(0f, 1f)
                if (f > 0f) {
                    r.set(bl + 2f, bt + 28f, bl + 2f + (bw - 4f) * f, bt + 44f)
                    fill.color = 0xFFD50000.toInt()
                    c.drawRoundRect(r, 8f, 8f, fill)
                    fill.color = 0x55FFFFFF
                    r.set(r.left + 3f, r.top + 2f, max(r.left + 4f, r.right - 3f), r.top + 6f)
                    c.drawRoundRect(r, 3f, 3f, fill)
                }
            }
        }

        // speedometer
        val kmh = abs(car.forwardSpeed) / Game.METER * 3.6f
        val cx = W / 2
        val cy = H + 12f
        fill.color = 0xE61B1B1B.toInt()
        c.drawCircle(cx, cy, 134f, fill)
        stroke.color = 0x66FFFFFF
        stroke.strokeWidth = 3f
        c.drawCircle(cx, cy, 134f, stroke)
        r.set(cx - 118f, cy - 118f, cx + 118f, cy + 118f)
        stroke.strokeWidth = 8f
        stroke.color = 0xFF43A047.toInt()
        c.drawArc(r, 180f, 108f, false, stroke)
        stroke.color = 0xFFFFA000.toInt()
        c.drawArc(r, 288f, 45f, false, stroke)
        stroke.color = 0xFFE53935.toInt()
        c.drawArc(r, 333f, 27f, false, stroke)
        stroke.color = Color.WHITE
        stroke.strokeWidth = 3f
        for (k in 0..12) {
            val a = PI.toFloat() + k / 12f * PI.toFloat()
            val inner = if (k % 2 == 0) 98f else 104f
            c.drawLine(cx + cos(a) * inner, cy + sin(a) * inner, cx + cos(a) * 112f, cy + sin(a) * 112f, stroke)
        }
        val na = PI.toFloat() + (kmh / 240f).coerceIn(0f, 1f) * PI.toFloat()
        stroke.color = 0xFFFF5252.toInt()
        stroke.strokeWidth = 6f
        c.drawLine(cx, cy, cx + cos(na) * 104f, cy + sin(na) * 104f, stroke)
        fill.color = 0xFF424242.toInt()
        c.drawCircle(cx, cy, 14f, fill)
        text.textSize = 32f
        text.color = Color.WHITE
        c.drawText("${kmh.toInt()}", cx, cy - 44f, text)
        text.textSize = 18f
        text.color = 0xFFB0BEC5.toInt()
        c.drawText("km/h", cx, cy - 22f, text)

        if (g.state == State.PLAYING && !g.wrecked) for (b in g.controls()) drawButton(c, b, g.isHeld(b.id))

        if (g.state == State.PLAYING && g.phase == 2) {
            val n = ceil(g.phaseLeft).toInt().coerceAtLeast(1)
            val frac = (g.phaseLeft - (n - 1)).coerceIn(0f, 1f)
            text.textSize = 180f + 60f * frac
            text.color = Color.argb((80 + 120 * frac).toInt(), 255, 82, 82)
            c.drawText(n.toString(), W / 2, H * 0.6f, text)
        }
    }

    private fun drawButton(c: Canvas, b: Button, held: Boolean) {
        val grow = if (held) 4f else 0f
        r.set(b.l - grow, b.t - grow, b.r + grow, b.b + grow)
        fill.color = 0x40000000
        c.drawRoundRect(r.left + 3f, r.top + 6f, r.right + 3f, r.bottom + 6f, 26f, 26f, fill)
        fill.color = b.color
        fill.alpha = if (held) 230 else (b.color ushr 24).coerceAtLeast(110)
        c.drawRoundRect(r, 26f, 26f, fill)
        fill.color = 0x30FFFFFF
        c.drawRoundRect(r.left + 4f, r.top + 4f, r.right - 4f, r.top + (r.bottom - r.top) * 0.45f, 22f, 22f, fill)
        fill.alpha = 255
        stroke.color = if (held) 0xEEFFFFFF.toInt() else 0x88FFFFFF.toInt()
        stroke.strokeWidth = if (held) 4f else 3f
        c.drawRoundRect(r, 26f, 26f, stroke)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        val size = min(52f, (b.b - b.t) * 0.38f)
        fitText(c, b.label, b.cx, b.cy + size * 0.36f, size, b.r - b.l - 16f)
    }

    // ------------------------------------------------------------ screens

    private fun dim(c: Canvas, a: Int = 0xB0) {
        fill.color = Color.argb(a, 0, 0, 0)
        c.drawRect(0f, 0f, g.W, g.H, fill)
    }

    private fun title(c: Canvas, s: String, x: Float, y: Float, color: Int, size: Float = 76f, maxW: Float = g.W - 40f) {
        text.textAlign = Paint.Align.CENTER
        text.textSize = size
        val w = text.measureText(s)
        if (w > maxW) text.textSize *= maxW / w
        text.style = Paint.Style.STROKE
        text.strokeWidth = text.textSize * 0.14f
        text.color = 0xFF1A0E08.toInt()
        c.drawText(s, x + 3f, y + 5f, text)
        c.drawText(s, x, y, text)
        text.style = Paint.Style.FILL
        text.color = color
        c.drawText(s, x, y, text)
        // glossy top highlight
        c.save()
        c.clipRect(x - w, y - text.textSize, x + w, y - text.textSize * 0.45f)
        text.color = 0x55FFFFFF
        c.drawText(s, x, y, text)
        c.restore()
    }

    private fun drawMenuButtons(c: Canvas) {
        for (b in g.buttons()) {
            fill.color = 0x66000000
            r.set(b.l + 4f, b.t + 8f, b.r + 4f, b.b + 8f)
            c.drawRoundRect(r, 24f, 24f, fill)
            fill.color = if (b.enabled) b.color else 0xFF616161.toInt()
            r.set(b.l, b.t, b.r, b.b)
            c.drawRoundRect(r, 24f, 24f, fill)
            fill.color = 0x38FFFFFF
            r.set(b.l + 4f, b.t + 4f, b.r - 4f, b.cy)
            c.drawRoundRect(r, 20f, 20f, fill)
            fill.color = 0x30000000
            r.set(b.l + 4f, b.b - 14f, b.r - 4f, b.b - 4f)
            c.drawRoundRect(r, 8f, 8f, fill)
            stroke.color = 0x66FFFFFF
            stroke.strokeWidth = 2f
            r.set(b.l, b.t, b.r, b.b)
            c.drawRoundRect(r, 24f, 24f, stroke)
            text.textAlign = Paint.Align.CENTER
            val size = min(40f, (b.b - b.t) * 0.45f)
            text.textSize = size
            val w = text.measureText(b.label)
            if (w > b.r - b.l - 20f) text.textSize *= (b.r - b.l - 20f) / w
            text.style = Paint.Style.STROKE
            text.strokeWidth = 5f
            text.color = 0x66000000
            c.drawText(b.label, b.cx, b.cy + text.textSize * 0.36f, text)
            text.style = Paint.Style.FILL
            text.color = if (b.enabled) Color.WHITE else 0xFFBDBDBD.toInt()
            c.drawText(b.label, b.cx, b.cy + text.textSize * 0.36f, text)
        }
    }

    private fun fitText(c: Canvas, s: String, x: Float, y: Float, size: Float, maxW: Float) {
        text.textSize = size
        val w = text.measureText(s)
        if (w > maxW) text.textSize = size * maxW / w
        c.drawText(s, x, y, text)
    }

    private fun drawMenu(c: Canvas) {
        val W = g.W
        val s = g.save
        fill.color = 0x40000000
        c.drawRect(0f, 0f, W, g.H, fill)
        val lx = W * 0.27f
        val lw = W * 0.5f
        val bob = sin(g.anim * 2f) * 4f
        title(c, "ĐUA XE", lx, 115f + bob, 0xFFFFD740.toInt(), 104f, lw)
        title(c, "SINH TỒN", lx, 215f + bob, 0xFFFF5252.toInt(), 104f, lw)

        // slogan ribbon
        val slogan = "CHẠY CÀNG XA — CÀNG SỐNG SÓT!"
        val pulse = 1f + 0.03f * sin(g.anim * 4f)
        text.textSize = 34f * pulse
        val sw = min(lw, text.measureText(slogan) + 60f)
        val sy = 248f
        fill.color = 0x55000000
        path.reset()
        path.moveTo(lx - sw / 2 - 18f, sy + 6f); path.lineTo(lx + sw / 2 + 18f, sy + 6f)
        path.lineTo(lx + sw / 2 + 4f, sy + 34f); path.lineTo(lx + sw / 2 + 18f, sy + 62f)
        path.lineTo(lx - sw / 2 - 18f, sy + 62f); path.lineTo(lx - sw / 2 - 4f, sy + 34f); path.close()
        c.drawPath(path, fill)
        path.offset(-3f, -6f)
        fill.color = 0xF0B71C1C.toInt()
        c.drawPath(path, fill)
        stroke.color = 0xFFFFD740.toInt()
        stroke.strokeWidth = 3f
        c.drawPath(path, stroke)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        fitText(c, slogan, lx - 3f, sy + 40f, 34f * pulse, sw - 30f)

        // stats chips
        val chips = listOf("💰 ${s.money}$", "📅 Ngày ${s.day}", "🏆 ${s.bestStage}/${Game.STAGES} ải", "⭐ ${s.bestScore}")
        text.textSize = 26f
        var total = 0f
        for (ch in chips) total += text.measureText(ch) + 26f * 1.2f + 14f
        var cx = lx - total / 2
        for (ch in chips) cx += chip(c, ch, cx, 330f, 26f, 0xAA1B1B1B.toInt(), 0xFFFFE082.toInt()) + 14f

        // stage selector
        val sx = W * 0.74f
        text.textSize = 46f
        text.color = Color.WHITE
        title(c, "ẢI ${s.stageSel}", sx, 172f, Color.WHITE, 50f)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 22f
        text.color = 0xFFFFF3E0.toInt()
        c.drawText("${g.stageGoalM(s.stageSel).toInt()}m • kỷ lục ${s.best[s.stageSel]}m", sx, 204f, text)
        drawMenuButtons(c)
    }

    private fun card(c: Canvas, rc: FloatArray, selected: Boolean) {
        r.set(rc[0], rc[1], rc[2], rc[3])
        fill.color = 0xEE263238.toInt()
        c.drawRoundRect(r, 20f, 20f, fill)
        if (selected) {
            stroke.color = 0xFF69F0AE.toInt()
            stroke.strokeWidth = 6f
            c.drawRoundRect(r, 20f, 20f, stroke)
        }
    }

    private fun drawShop(c: Canvas) {
        val W = g.W
        val s = g.save
        dim(c, 0xC0)
        title(c, "NÂNG CẤP XE", W / 2, 76f, 0xFFFFD740.toInt(), 60f)
        text.textAlign = Paint.Align.RIGHT
        text.textSize = 34f
        text.color = 0xFFFFD740.toInt()
        c.drawText("${s.money}$", W - 40f, 70f, text)
        Upgrade.entries.forEachIndexed { i, u ->
            val rc = g.upgradeCard(i)
            val lv = s.level(u)
            card(c, rc, false)
            text.textAlign = Paint.Align.LEFT
            text.color = Color.WHITE
            fitText(c, u.title, rc[0] + 24f, rc[1] + 54f, 36f, rc[2] - rc[0] - 48f)
            text.color = 0xFFB0BEC5.toInt()
            fitText(c, u.desc, rc[0] + 24f, rc[1] + 92f, 24f, rc[2] - rc[0] - 48f)
            val pw = ((rc[2] - rc[0]) - 48f - 4 * 10f) / Upgrade.MAX_LEVEL
            for (k in 0 until Upgrade.MAX_LEVEL) {
                fill.color = if (k < lv) 0xFF69F0AE.toInt() else 0xFF455A64.toInt()
                val l = rc[0] + 24f + k * (pw + 10f)
                r.set(l, rc[1] + 112f, l + pw, rc[1] + 132f)
                c.drawRoundRect(r, 6f, 6f, fill)
            }
            text.textAlign = Paint.Align.CENTER
            text.textSize = 32f
            val cost = u.cost(lv)
            val (label, color) = when {
                lv >= Upgrade.MAX_LEVEL -> "TỐI ĐA" to 0xFF69F0AE.toInt()
                s.money >= cost -> "MUA $cost$" to 0xFFFFD740.toInt()
                else -> "$cost$" to 0xFF9E9E9E.toInt()
            }
            text.color = color
            c.drawText(label, (rc[0] + rc[2]) / 2, rc[3] - 28f, text)
        }
        drawMenuButtons(c)
    }

    private fun drawGarage(c: Canvas) {
        val W = g.W
        val s = g.save
        dim(c, 0xC0)
        title(c, "GARA", W / 2, 76f, 0xFFE040FB.toInt(), 60f)
        text.textAlign = Paint.Align.RIGHT
        text.textSize = 34f
        text.color = 0xFFFFD740.toInt()
        c.drawText("${s.money}$", W - 40f, 70f, text)
        CarType.entries.forEachIndexed { i, ct ->
            val rc = g.carCard(i)
            card(c, rc, s.car == i)
            val mx = (rc[0] + rc[2]) / 2
            c.save()
            c.translate(mx, rc[1] + 90f)
            c.scale(0.85f, 0.85f)
            drawCar(c, ct, 0f, 0f, 0f, g.anim * 3f, 0f, false, s.weaponType, false)
            c.restore()
            text.textAlign = Paint.Align.CENTER
            text.color = Color.WHITE
            fitText(c, ct.title, mx, rc[1] + 168f, 30f, rc[2] - rc[0] - 20f)
            text.color = 0xFFB0BEC5.toInt()
            fitText(c, "Máu x${ct.hpMul} • Tốc x${ct.speedMul} • Giáp ${(ct.armor * 100).toInt()}%", mx, rc[1] + 198f, 19f, rc[2] - rc[0] - 20f)
            status(c, rc, s.ownsCar(i), s.car == i, ct.cost)
        }
        WeaponType.entries.forEachIndexed { i, w ->
            val rc = g.weaponCard(i)
            card(c, rc, s.weapon == i)
            val mx = (rc[0] + rc[2]) / 2
            text.textAlign = Paint.Align.CENTER
            text.textSize = 56f
            text.color = Color.WHITE
            c.drawText(w.icon, mx, rc[1] + 70f, text)
            fitText(c, w.title, mx, rc[1] + 112f, 30f, rc[2] - rc[0] - 20f)
            text.color = 0xFFB0BEC5.toInt()
            fitText(c, w.desc, mx, rc[1] + 142f, 22f, rc[2] - rc[0] - 20f)
            status(c, rc, s.ownsWeapon(i), s.weapon == i, w.cost)
        }
        drawMenuButtons(c)
    }

    private fun status(c: Canvas, rc: FloatArray, owned: Boolean, selected: Boolean, cost: Int) {
        val (label, color) = when {
            selected -> "ĐANG DÙNG" to 0xFF69F0AE.toInt()
            owned -> "CHỌN" to Color.WHITE
            g.save.money >= cost -> "MUA $cost$" to 0xFFFFD740.toInt()
            else -> "🔒 $cost$" to 0xFF9E9E9E.toInt()
        }
        text.textAlign = Paint.Align.CENTER
        text.textSize = 26f
        text.color = color
        c.drawText(label, (rc[0] + rc[2]) / 2, rc[3] - 18f, text)
    }

    private fun drawOverlay(c: Canvas) {
        val W = g.W
        val H = g.H
        val tiles = listOf(
            Triple("📏", "${g.maxDist.toInt()}m", "quãng đường"),
            Triple("🧟", "${g.kills}", "zombie"),
            Triple("💥", "${g.smashed}", "vật cản"),
            Triple("💰", "+${g.distMoney + g.runMoney + g.stageBonus}$", "tiền"),
        )
        when (g.state) {
            State.PAUSED -> {
                dim(c)
                title(c, "TẠM DỪNG", W / 2, H * 0.24f, Color.WHITE)
                drawMenuButtons(c)
            }
            State.RESULT -> resultPanel(
                c, "NGÀY ${g.save.day - 1} KẾT THÚC", 0xFFFF5252.toInt(),
                g.endReason, 0xFFFFAB91.toInt(), tiles, emptyList(),
            )
            State.STAGE_DONE -> {
                val unlock = when (g.stage) {
                    1 -> listOf("🔓 Mở khóa GARA")
                    5 -> listOf("🔓 Mở khóa 👥 Nhân bản")
                    else -> emptyList()
                }
                resultPanel(
                    c, "VƯỢT ẢI ${g.stage}!", 0xFFFFD740.toInt(),
                    "Đã tới trạm cứu hộ!", 0xFF69F0AE.toInt(), tiles, unlock,
                )
            }
            State.VICTORY -> resultPanel(
                c, "CHIẾN THẮNG!", 0xFFFFD740.toInt(),
                "Bạn đã sống sót qua cả ${Game.STAGES} ải!", 0xFF69F0AE.toInt(), tiles, listOf("👑 Huyền thoại sinh tồn"),
            )
            else -> {}
        }
    }

    private var bgW = -1f


    private var bgH = -1f


    private var skyShader: Shader? = null


    private var sunShader: Shader? = null


    private var farShader: Shader? = null


    private var midShader: Shader? = null


    private var vignette: Shader? = null


    private var hurtShader: Shader? = null


    private val groundShader = LinearGradient(
        0f, Terrain.BASE_Y - 260f, 0f, Terrain.BASE_Y + 560f,
        intArrayOf(0xFFC99059.toInt(), 0xFF9C653B.toInt(), 0xFF5A361E.toInt()),
        floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
    )


    private val topPath = Path()


    private fun ensureShaders() {
        if (bgW == g.W && bgH == g.H) return
        bgW = g.W
        bgH = g.H
        val W = g.W
        val H = g.H
        skyShader = LinearGradient(
            0f, 0f, 0f, H * 0.85f,
            intArrayOf(0xFF8E3B46.toInt(), 0xFFD9714A.toInt(), 0xFFF6B26B.toInt(), 0xFFFBE3B0.toInt()),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
        sunShader = RadialGradient(
            W * 0.78f, H * 0.26f, 260f,
            intArrayOf(0xFFFFF4D6.toInt(), 0x88FFD59E.toInt(), 0x00FFD59E),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP,
        )
        farShader = LinearGradient(0f, H * 0.3f, 0f, H * 0.7f, 0xFFC98F7A.toInt(), 0xFFE7B892.toInt(), Shader.TileMode.CLAMP)
        midShader = LinearGradient(0f, H * 0.45f, 0f, H * 0.85f, 0xFFA35E3F.toInt(), 0xFFC4855A.toInt(), Shader.TileMode.CLAMP)
        vignette = RadialGradient(
            W / 2, H / 2, max(W, H) * 0.72f,
            intArrayOf(0x00000000, 0x00000000, 0x66000000),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP,
        )
        hurtShader = RadialGradient(
            W / 2, H / 2, max(W, H) * 0.65f,
            intArrayOf(0x00FF0000, 0x00FF0000, 0xFFD50000.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }


    private fun hash(k: Int): Int = ((k * 73856093) xor ((k shr 2) * 19349663) xor 0x5bd1e995) and 0x7fffffff


    private fun cloud(c: Canvas, x: Float, y: Float, s: Float) {
        fill.color = 0x22000000
        c.drawOval(x - 80f * s, y - 6f * s, x + 90f * s, y + 30f * s, fill)
        fill.color = 0xCCFFF1DC.toInt()
        c.drawOval(x - 90f * s, y - 20f * s, x + 90f * s, y + 22f * s, fill)
        c.drawCircle(x - 30f * s, y - 22f * s, 32f * s, fill)
        c.drawCircle(x + 25f * s, y - 30f * s, 40f * s, fill)
        fill.color = 0x66FFFFFF
        c.drawCircle(x + 15f * s, y - 40f * s, 22f * s, fill)
    }


    private fun city(c: Canvas, par: Float, base: Float) {
        val off = g.camX * Game.ZOOM * par
        val slot = 80f
        var k = floor(off / slot).toInt() - 1
        val end = floor((off + g.W) / slot).toInt() + 1
        while (k <= end) {
            val h = hash(k)
            if (h % 7 < 3) {
                val x = k * slot - off
                val bh = 50f + (h % 9) * 16f
                val bw = slot * (0.55f + (h % 4) * 0.12f)
                fill.color = 0xFFA86E52.toInt()
                path.reset()
                path.moveTo(x, base + 40f)
                path.lineTo(x, base - bh)
                if (h % 2 == 0) {
                    // broken top
                    path.lineTo(x + bw * 0.35f, base - bh)
                    path.lineTo(x + bw * 0.5f, base - bh + 22f)
                    path.lineTo(x + bw * 0.7f, base - bh + 8f)
                } else {
                    path.lineTo(x + bw * 0.5f, base - bh)
                    path.lineTo(x + bw * 0.5f, base - bh - 18f)
                    path.lineTo(x + bw * 0.6f, base - bh - 18f)
                    path.lineTo(x + bw * 0.6f, base - bh)
                }
                path.lineTo(x + bw, base - bh * 0.85f)
                path.lineTo(x + bw, base + 40f)
                path.close()
                c.drawPath(path, fill)
                fill.color = 0x55693A28
                var wy = base - bh + 30f
                while (wy < base) {
                    var wx = x + 8f
                    while (wx < x + bw - 12f) {
                        if (hash(k * 31 + (wx + wy).toInt()) % 3 != 0) c.drawRect(wx, wy, wx + 7f, wy + 9f, fill)
                        wx += 16f
                    }
                    wy += 20f
                }
            }
            k++
        }
    }


    private fun drawDamageOverlay(c: Canvas) {
        val car = g.car
        var a = 0f
        if (g.hurtFlash > 0f) a = min(1f, g.hurtFlash * 2.2f) * 0.85f
        if (car.hp < car.maxHp * 0.25f && !g.wrecked && g.state == State.PLAYING) {
            a = max(a, 0.25f + 0.2f * sin(g.anim * 6f))
        }
        if (a <= 0f) return
        fill.shader = hurtShader
        fill.alpha = (255 * a).toInt().coerceIn(0, 255)
        c.drawRect(0f, 0f, g.W, g.H, fill)
        fill.shader = null
        fill.alpha = 255
    }


    private fun glassPanel(c: Canvas, l: Float, t: Float, rr: Float, b: Float, radius: Float = 18f) {
        r.set(l, t, rr, b)
        fill.color = 0x8C101418.toInt()
        c.drawRoundRect(r, radius, radius, fill)
        stroke.color = 0x40FFFFFF
        stroke.strokeWidth = 2f
        c.drawRoundRect(r, radius, radius, stroke)
    }


    private fun chip(c: Canvas, s: String, x: Float, y: Float, size: Float, bg: Int, fg: Int): Float {
        text.textSize = size
        val w = text.measureText(s) + size * 1.2f
        r.set(x, y, x + w, y + size * 1.6f)
        fill.color = bg
        c.drawRoundRect(r, size * 0.8f, size * 0.8f, fill)
        stroke.color = 0x55FFFFFF
        stroke.strokeWidth = 2f
        c.drawRoundRect(r, size * 0.8f, size * 0.8f, stroke)
        text.textAlign = Paint.Align.CENTER
        text.color = fg
        c.drawText(s, x + w / 2, y + size * 1.15f, text)
        return w
    }


    private fun resultPanel(
        c: Canvas,
        heading: String,
        headColor: Int,
        sub: String,
        subColor: Int,
        tiles: List<Triple<String, String, String>>,
        extraChips: List<String>,
    ) {
        val W = g.W
        val H = g.H
        dim(c, 0x8C)
        val l = W / 2 - 540f
        val rr = W / 2 + 540f
        glassPanel(c, l, 24f, rr, H - 150f, 30f)
        title(c, heading, W / 2, 98f, headColor, 64f, rr - l - 60f)
        text.textAlign = Paint.Align.CENTER
        text.color = subColor
        fitText(c, sub, W / 2, 140f, 28f, rr - l - 60f)

        // score
        text.textSize = 22f
        text.color = 0xFFB0BEC5.toInt()
        c.drawText("ĐIỂM", W / 2, 182f, text)
        title(c, "%,d".format(g.score).replace(',', '.'), W / 2, 250f, 0xFFFFD740.toInt(), 68f)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 22f
        text.color = if (g.newBestScore) 0xFF69F0AE.toInt() else 0xFFB0BEC5.toInt()
        c.drawText(if (g.newBestScore) "★ ĐIỂM CAO NHẤT MỚI ★" else "Cao nhất: ${g.save.bestScore}", W / 2, 282f, text)

        // stat tiles
        val tw = 230f
        val gap = 20f
        var tx = W / 2 - (tiles.size * tw + (tiles.size - 1) * gap) / 2
        for ((icon, value, label) in tiles) {
            r.set(tx, 300f, tx + tw, 404f)
            fill.color = 0x33FFFFFF
            c.drawRoundRect(r, 18f, 18f, fill)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 30f
            text.color = Color.WHITE
            c.drawText(icon, tx + tw / 2, 336f, text)
            fitText(c, value, tx + tw / 2, 372f, 32f, tw - 16f)
            text.color = 0xFFB0BEC5.toInt()
            fitText(c, label, tx + tw / 2, 396f, 18f, tw - 16f)
            tx += tw + gap
        }

        // achievements
        text.textSize = 26f
        text.color = 0xFFFFD740.toInt()
        c.drawText("🏆 THÀNH TÍCH", W / 2, 446f, text)
        val all = extraChips + g.achievements
        if (all.isEmpty()) {
            text.textSize = 22f
            text.color = 0xFF90A4AE.toInt()
            c.drawText("—", W / 2, 486f, text)
        } else {
            // centre each row of chips
            text.textSize = 22f
            val rows = ArrayList<ArrayList<String>>()
            var rowW = 0f
            rows.add(ArrayList())
            for (a in all) {
                val w = text.measureText(a) + 22f * 1.2f + 12f
                if (rowW + w > rr - l - 60f && rows.last().isNotEmpty()) {
                    rows.add(ArrayList()); rowW = 0f
                }
                rows.last().add(a); rowW += w
            }
            var y = 462f
            for (row in rows.take(2)) {
                var w = 0f
                for (a in row) w += text.measureText(a) + 22f * 1.2f + 12f
                var x = W / 2 - w / 2
                for (a in row) x += chip(c, a, x, y, 22f, 0xCC4E3B00.toInt(), 0xFFFFE082.toInt()) + 12f
                y += 46f
                text.textSize = 22f
            }
        }
        drawMenuButtons(c)
    }

}
