package com.khongvinh.carsurvival

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
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
    private var skyH = -1f
    private var sky: LinearGradient? = null

    fun render(c: Canvas) {
        drawBackground(c)
        drawWorld(c)
        when (g.state) {
            State.MENU -> drawMenu(c)
            State.SHOP -> drawShop(c)
            State.GARAGE -> drawGarage(c)
            else -> {
                drawHud(c)
                drawOverlay(c)
            }
        }
    }

    // ------------------------------------------------------------ backdrop

    private fun drawBackground(c: Canvas) {
        val W = g.W
        val H = g.H
        if (skyH != H) {
            skyH = H
            sky = LinearGradient(0f, 0f, 0f, H, 0xFFD9824B.toInt(), 0xFFF7D9A6.toInt(), Shader.TileMode.CLAMP)
        }
        fill.shader = sky
        c.drawRect(0f, 0f, W, H, fill)
        fill.shader = null
        fill.color = 0x66FFF3E0
        c.drawCircle(W * 0.78f, H * 0.22f, 120f, fill)
        fill.color = 0xCCFFF8E1.toInt()
        c.drawCircle(W * 0.78f, H * 0.22f, 70f, fill)
        mesas(c, 0.08f, H * 0.62f, 0xFFD39A6A.toInt(), 1.0f, 150f)
        mesas(c, 0.2f, H * 0.72f, 0xFFB9774A.toInt(), 1.9f, 120f)
    }

    private fun mesas(c: Canvas, par: Float, base: Float, color: Int, seed: Float, amp: Float) {
        val W = g.W
        path.reset()
        path.moveTo(0f, g.H)
        var sx = 0f
        val off = g.camX * Game.ZOOM * par
        val lift = (g.camY - 100f) * Game.ZOOM * par * 0.3f
        while (sx <= W + 24f) {
            val u = (sx + off) * 0.004f
            var h = amp * (0.55f + 0.45f * sin(u + seed) + 0.25f * sin(u * 2.3f + seed * 3f))
            h = h.coerceIn(amp * 0.15f, amp * 0.85f) // flat mesa tops
            path.lineTo(sx, base - h - lift)
            sx += 24f
        }
        path.lineTo(W, g.H)
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }

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

        for (o in g.obstacles) if (o.x in x0..x1) drawObstacle(c, o)
        for (it in g.items) if (it.x in x0..x1) drawItem(c, it)
        for (z in g.zombies) if (z.x in x0..x1) drawZombie(c, z)
        for (d in g.debris) if (d.x in x0..x1) drawDebris(c, d)
        drawShots(c)
        drawPlayerCar(c)
        for (p in g.particles) {
            fill.color = p.color
            fill.alpha = (255 * (p.life / p.maxLife)).toInt().coerceIn(0, 255)
            c.drawCircle(p.x, p.y, p.size, fill)
        }
        fill.alpha = 255
        for (t in g.texts) {
            text.textSize = 44f
            val a = (255 * min(1f, t.life)).toInt()
            text.color = Color.BLACK
            text.alpha = a
            c.drawText(t.text, t.x + 3f, t.y + 3f, text)
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
        for (i in i0..i1) {
            val x = Terrain.START_X + i * Terrain.SEG
            val y = t.point(i)
            if (i == i0) {
                path.moveTo(x, bottom); path.lineTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.lineTo(Terrain.START_X + i1 * Terrain.SEG, bottom)
        path.close()
        fill.color = 0xFF8D5B36.toInt()
        c.drawPath(path, fill)
        fill.color = 0x33000000
        for (i in i0..i1) {
            val x = Terrain.START_X + i * Terrain.SEG
            val y = t.point(i)
            val h = (i * 7919) and 0x7fffffff
            if (h % 5 == 0) c.drawCircle(x + 10f, y + 60f + (h % 3) * 30f, 8f + (h % 4) * 3f, fill)
            if (h % 7 == 0) c.drawRect(x, y + 120f, x + 80f, y + 130f, fill)
        }
        path.reset()
        for (i in i0..i1) {
            val x = Terrain.START_X + i * Terrain.SEG
            val y = t.point(i)
            if (i == i0) path.moveTo(x, y + 6f) else path.lineTo(x, y + 6f)
        }
        stroke.color = 0xFF5D3A22.toInt()
        stroke.strokeWidth = 16f
        c.drawPath(path, stroke)
        stroke.color = 0xFFC08A5B.toInt()
        stroke.strokeWidth = 5f
        path.offset(0f, -7f)
        c.drawPath(path, stroke)
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
        val hurt = car.hurtT > 0f && (g.anim * 20f).toInt() % 2 == 0
        if (!hurt) drawCar(c, type, car.x, car.y, car.angle, car.wheelRot, car.aim, car.boosting, g.save.weaponType, false)
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
    ) {
        c.save()
        c.translate(x, y)
        c.rotate(angle * 180f / PI.toFloat())
        val body = if (ghost) 0xFF64FFDA.toInt() else type.color

        // booster rocket on the back
        fill.color = 0xFF616161.toInt()
        c.drawRoundRect(-104f, -30f, -66f, -10f, 8f, 8f, fill)
        fill.color = 0xFFB71C1C.toInt()
        c.drawRect(-74f, -32f, -66f, -8f, fill)
        if (boosting) {
            val f = 30f + Random.nextFloat() * 40f
            fill.color = 0xCCFF9800.toInt()
            path.reset()
            path.moveTo(-104f, -30f)
            path.lineTo(-104f - f - 30f, -20f)
            path.lineTo(-104f, -10f)
            path.close()
            c.drawPath(path, fill)
            fill.color = 0xFFFFEB3B.toInt()
            path.reset()
            path.moveTo(-104f, -26f)
            path.lineTo(-104f - f * 0.6f, -20f)
            path.lineTo(-104f, -14f)
            path.close()
            c.drawPath(path, fill)
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
        stroke.color = 0x88000000.toInt()
        stroke.strokeWidth = 3f
        c.drawPath(path, stroke)

        fill.color = if (ghost) 0x8800695C.toInt() else 0xFF263238.toInt()
        when (type) {
            CarType.PICKUP -> {
                path.reset()
                path.moveTo(-10f, -38f); path.lineTo(18f, -38f); path.lineTo(32f, -18f); path.lineTo(-10f, -18f); path.close()
                c.drawPath(path, fill)
                fill.color = 0x33000000
                c.drawRect(-82f, -12f, -28f, -4f, fill)
            }
            CarType.MUSCLE -> {
                path.reset()
                path.moveTo(-6f, -33f); path.lineTo(22f, -33f); path.lineTo(38f, -18f); path.lineTo(-6f, -18f); path.close()
                c.drawPath(path, fill)
                fill.color = Color.WHITE
                c.drawRect(-80f, -6f, 86f, 0f, fill)
                fill.color = 0xFF212121.toInt()
                c.drawRect(-88f, -22f, -70f, -16f, fill)
            }
            CarType.VAN -> {
                path.reset()
                path.moveTo(38f, -52f); path.lineTo(52f, -26f); path.lineTo(20f, -26f); path.lineTo(20f, -52f); path.close()
                c.drawPath(path, fill)
                fill.color = 0xFFE53935.toInt()
                c.drawRect(-60f, -40f, -20f, -30f, fill)
                c.drawRect(-45f, -50f, -35f, -20f, fill)
            }
            CarType.TANK -> {
                c.drawRect(-20f, -34f, 20f, -24f, fill)
                fill.color = 0x55000000
                for (k in 0 until 6) c.drawCircle(-70f + k * 28f, 10f, 3f, fill)
                fill.color = 0xFF33401F.toInt()
                c.drawRect(-86f, 0f, 86f, 6f, fill)
            }
        }
        // front ram
        fill.color = 0xFF9E9E9E.toInt()
        c.drawRect(84f, -8f, 98f, 20f, fill)
        fill.color = 0xFF757575.toInt()
        for (k in 0 until 3) {
            path.reset()
            path.moveTo(98f, -6f + k * 9f); path.lineTo(110f, -2f + k * 9f); path.lineTo(98f, 2f + k * 9f); path.close()
            c.drawPath(path, fill)
        }
        fill.color = 0xFFFFF59D.toInt()
        c.drawCircle(84f, -6f, 5f, fill)
        fill.color = 0xFFFF1744.toInt()
        c.drawRect(-88f, -6f, -82f, 4f, fill)

        for (i in 0..1) drawWheel(c, Car.WHEEL_X[i], Car.WHEEL_Y, wheelRot, type == CarType.TANK)

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
        c.restore()
    }

    private fun drawWheel(c: Canvas, x: Float, y: Float, rot: Float, tank: Boolean) {
        fill.color = 0xFF1B1B1B.toInt()
        c.drawCircle(x, y, Car.WHEEL_R, fill)
        fill.color = if (tank) 0xFF5D6B3A.toInt() else 0xFF9E9E9E.toInt()
        c.drawCircle(x, y, 12f, fill)
        stroke.color = 0xFF424242.toInt()
        stroke.strokeWidth = 3f
        for (k in 0 until 3) {
            val a = rot + k * 2.094f
            c.drawLine(x, y, x + cos(a) * 12f, y + sin(a) * 12f, stroke)
        }
        fill.color = Color.BLACK
        for (k in 0 until 8) {
            val a = rot + k * 0.785f
            c.drawCircle(x + cos(a) * 22f, y + sin(a) * 22f, 3.5f, fill)
        }
    }

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
        stroke.color = 0xFF37474F.toInt()
        stroke.strokeWidth = if (fat) 14f else 9f
        c.drawLine(0f, -h * 0.45f, swing * 12f, 0f, stroke)
        c.drawLine(0f, -h * 0.45f, -swing * 12f, 0f, stroke)
        stroke.color = skinDark
        stroke.strokeWidth = if (fat) 10f else 7f
        c.drawLine(0f, -h * 0.76f, dir * w * 0.95f, -h * 0.72f + swing * 4f, stroke)
        fill.color = shirt
        c.drawRoundRect(-w / 2, -h * 0.86f, w / 2, -h * 0.42f, 10f, 10f, fill)
        fill.color = 0x55000000
        c.drawRect(-w * 0.2f, -h * 0.55f, w * 0.1f, -h * 0.5f, fill)
        c.drawRect(w * 0.1f, -h * 0.75f, w * 0.3f, -h * 0.7f, fill)
        val hr = if (fat) h * 0.12f else h * 0.11f
        fill.color = skin
        c.drawCircle(dir * 3f, -h * 0.86f - hr * 0.9f, hr, fill)
        fill.color = 0xFFFF1744.toInt()
        c.drawCircle(dir * (hr * 0.5f + 3f), -h * 0.86f - hr, 2.8f, fill)
        fill.color = 0xFF33691E.toInt()
        val mx0 = dir * hr * 0.2f
        val mx1 = dir * (hr * 0.8f + 3f)
        c.drawRect(min(mx0, mx1), -h * 0.86f - hr * 0.5f, max(mx0, mx1), -h * 0.86f - hr * 0.3f, fill)
        stroke.color = skin
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
        text.textAlign = Paint.Align.LEFT
        text.textSize = 26f
        text.color = Color.WHITE
        c.drawText(label, x, y + 22f, text)
        r.set(x + 44f, y, x + 44f + w, y + 26f)
        fill.color = 0xAA212121.toInt()
        c.drawRoundRect(r, 9f, 9f, fill)
        fill.color = color
        r.right = r.left + w * frac.coerceIn(0f, 1f)
        if (frac > 0f) c.drawRoundRect(r, 9f, 9f, fill)
        text.textAlign = Paint.Align.CENTER
    }

    private fun drawHud(c: Canvas) {
        val W = g.W
        val H = g.H
        val car = g.car

        val hpF = car.hp / car.maxHp
        bar(c, 24f, 20f, 240f, hpF, if (hpF > 0.3f) 0xFF43A047.toInt() else 0xFFE53935.toInt(), "✚")
        val fuelLow = car.fuel / car.maxFuel < 0.2f && (g.anim * 4f).toInt() % 2 == 0
        bar(c, 24f, 56f, 240f, car.fuel / car.maxFuel, if (fuelLow) 0xFFFF5252.toInt() else 0xFFFF9800.toInt(), "⛽")
        bar(c, 24f, 92f, 240f, car.boost / car.maxBoost, 0xFF29B6F6.toInt(), "🚀")
        text.textAlign = Paint.Align.LEFT
        text.textSize = 28f
        text.color = Color.WHITE
        c.drawText("${g.save.weaponType.icon} ${car.ammo}", 24f, 152f, text)
        var sx = 150f
        if (car.shieldT > 0f) {
            text.color = 0xFF82B1FF.toInt()
            c.drawText("🛡${car.shieldT.toInt() + 1}", sx, 152f, text)
            sx += 80f
        }
        if (car.cloneT > 0f) {
            text.color = 0xFF64FFDA.toInt()
            c.drawText("👥${car.cloneT.toInt() + 1}", sx, 152f, text)
        }
        text.textAlign = Paint.Align.CENTER

        // progress bar (start -> goal) with record and storm markers
        val pl = W * 0.3f
        val pr = W * 0.7f
        val py = 34f
        stroke.color = 0xAA212121.toInt()
        stroke.strokeWidth = 12f
        c.drawLine(pl, py, pr, py, stroke)
        val prog = (g.distanceM / g.goalM).coerceIn(0f, 1f)
        fill.color = 0xFFECEFF1.toInt()
        for (k in 1 until Game.LEVELS_PER_STAGE) {
            val kx = pl + (pr - pl) * k / Game.LEVELS_PER_STAGE
            c.drawRect(kx - 2f, py - 10f, kx + 2f, py + 10f, fill)
        }
        stroke.color = 0xFF29B6F6.toInt()
        stroke.strokeWidth = 8f
        c.drawLine(pl, py, pl + (pr - pl) * prog, py, stroke)
        val best = g.save.best[g.stage] / g.goalM
        if (best > 0f) {
            fill.color = 0xFFFFC107.toInt()
            val bx = pl + (pr - pl) * best.coerceIn(0f, 1f)
            c.drawRect(bx - 3f, py - 16f, bx + 3f, py + 16f, fill)
        }
        if (g.stormX > 0f) {
            fill.color = 0xFFE040FB.toInt()
            c.drawCircle(pl + (pr - pl) * (g.stormX / Game.METER / g.goalM).coerceIn(0f, 1f), py, 11f, fill)
        }
        fill.color = 0xFFE53935.toInt()
        c.drawCircle(pl + (pr - pl) * prog, py, 13f, fill)
        text.textSize = 30f
        c.drawText("🏁", pr + 26f, py + 10f, text)
        text.textSize = 24f
        text.color = Color.WHITE
        c.drawText("${g.distanceM.toInt()}m / ${g.goalM.toInt()}m  •  ẢI ${g.stage} — MÀN ${g.levelInStage}/${Game.LEVELS_PER_STAGE}  •  NGÀY ${g.save.day}", W / 2, py + 44f, text)

        val closed = g.phase >= Game.PHASE_TIME.size
        val secs = ceil(g.phaseLeft).toInt()
        val timer = when {
            closed -> "VÒNG BO ĐÃ KHÉP"
            g.phase == 2 -> "BO 3: $secs"
            else -> "BO ${g.phase + 1}  ${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"
        }
        val gap = ((car.x - Car.HALF_LEN - g.stormX) / Game.METER).toInt()
        text.textSize = 30f
        text.color = if (closed || g.phase == 2) 0xFFFF5252.toInt() else 0xFFEA80FC.toInt()
        c.drawText("$timer   •   Bão cách ${max(0, gap)}m", W / 2, py + 84f, text)

        text.textAlign = Paint.Align.RIGHT
        text.textSize = 34f
        text.color = 0xFFFFD740.toInt()
        c.drawText("+${g.runMoney}$", W - 116f, 66f, text)
        text.textAlign = Paint.Align.CENTER
        drawButton(c, g.pauseBtn, false)

        val warn = when {
            gap in 0..25 -> "⚠ BÃO ĐANG ĐUỔI KỊP!"
            car.fuel <= 0f -> "HẾT XĂNG!"
            car.flipT > 0.3f -> "LẬT XE! Nhấn ↺ ↻ để lật lại"
            else -> null
        }
        if (warn != null && g.state == State.PLAYING) {
            text.textSize = 40f
            text.color = Color.argb((160 + 95 * sin(g.anim * 8f)).toInt(), 255, 82, 82)
            c.drawText(warn, W / 2, H * 0.3f, text)
        }
        if (g.stormHurt > 0f) {
            fill.color = 0x40E040FB
            c.drawRect(0f, 0f, W, H, fill)
        }

        // speedometer
        val kmh = abs(car.forwardSpeed) / Game.METER * 3.6f
        val cx = W / 2
        val cy = H + 10f
        fill.color = 0xDD212121.toInt()
        c.drawCircle(cx, cy, 130f, fill)
        stroke.color = Color.WHITE
        stroke.strokeWidth = 3f
        for (k in 0..10) {
            val a = PI.toFloat() + k / 10f * PI.toFloat()
            c.drawLine(cx + cos(a) * 112f, cy + sin(a) * 112f, cx + cos(a) * 124f, cy + sin(a) * 124f, stroke)
        }
        val na = PI.toFloat() + (kmh / 240f).coerceIn(0f, 1f) * PI.toFloat()
        stroke.color = 0xFFFF5252.toInt()
        stroke.strokeWidth = 6f
        c.drawLine(cx, cy, cx + cos(na) * 108f, cy + sin(na) * 108f, stroke)
        text.textSize = 30f
        text.color = Color.WHITE
        c.drawText("${kmh.toInt()}", cx, cy - 40f, text)
        text.textSize = 18f
        c.drawText("km/h", cx, cy - 18f, text)

        if (g.state == State.PLAYING) for (b in g.controls()) drawButton(c, b, g.isHeld(b.id))

        if (g.state == State.PLAYING && g.phase == 2) {
            val n = ceil(g.phaseLeft).toInt().coerceAtLeast(1)
            val frac = (g.phaseLeft - (n - 1)).coerceIn(0f, 1f)
            text.textSize = 180f + 60f * frac
            text.color = Color.argb((80 + 120 * frac).toInt(), 255, 82, 82)
            c.drawText(n.toString(), W / 2, H * 0.6f, text)
        }
    }

    private fun drawButton(c: Canvas, b: Button, held: Boolean) {
        fill.color = b.color
        if (held) fill.alpha = 200
        r.set(b.l, b.t, b.r, b.b)
        c.drawRoundRect(r, 24f, 24f, fill)
        fill.alpha = 255
        stroke.color = 0x88FFFFFF.toInt()
        stroke.strokeWidth = 3f
        c.drawRoundRect(r, 24f, 24f, stroke)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        fitText(c, b.label, b.cx, b.cy + min(52f, (b.b - b.t) * 0.38f) * 0.36f, min(52f, (b.b - b.t) * 0.38f), b.r - b.l - 16f)
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
        text.color = Color.BLACK
        c.drawText(s, x + 4f, y + 4f, text)
        text.color = color
        c.drawText(s, x, y, text)
    }

    private fun drawMenuButtons(c: Canvas) {
        for (b in g.buttons()) {
            fill.color = 0x66000000
            r.set(b.l + 4f, b.t + 8f, b.r + 4f, b.b + 8f)
            c.drawRoundRect(r, 24f, 24f, fill)
            fill.color = if (b.enabled) b.color else 0xFF616161.toInt()
            r.set(b.l, b.t, b.r, b.b)
            c.drawRoundRect(r, 24f, 24f, fill)
            fill.color = 0x33FFFFFF
            r.set(b.l, b.t, b.r, b.cy)
            c.drawRoundRect(r, 24f, 24f, fill)
            text.textAlign = Paint.Align.CENTER
            text.textSize = min(40f, (b.b - b.t) * 0.45f)
            val w = text.measureText(b.label)
            if (w > b.r - b.l - 20f) text.textSize *= (b.r - b.l - 20f) / w
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
        fill.color = 0x55000000
        c.drawRect(0f, 0f, W, g.H, fill)
        val lx = W * 0.27f
        val lw = W * 0.5f
        title(c, "ĐUA XE", lx, 110f, 0xFFFFD740.toInt(), 96f, lw)
        title(c, "SINH TỒN", lx, 205f, 0xFFFF5252.toInt(), 96f, lw)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        fitText(c, "Lái xe xuyên đàn zombie — chạy trước cơn bão!", lx, 258f, 30f, lw)
        text.color = 0xFFFFD740.toInt()
        fitText(c, "💰 ${s.money}$   •   📅 Ngày ${s.day}   •   🏆 ${s.bestStage}/${Game.STAGES} ải", lx, 306f, 30f, lw)

        val cx = W * 0.74f
        text.textSize = 46f
        text.color = Color.WHITE
        c.drawText("ẢI ${s.stageSel}", cx, 172f, text)
        text.textSize = 22f
        text.color = 0xFFFFE0B2.toInt()
        c.drawText("${g.stageGoalM(s.stageSel).toInt()}m • kỷ lục ${s.best[s.stageSel]}m", cx, 204f, text)
        drawMenuButtons(c)

        text.color = 0xFFFFF3E0.toInt()
        var y = g.H - 110f
        for (line in listOf(
            "GA: tăng tốc • PHANH: lùi • ↺ ↻: nghiêng xe • 🚀: tên lửa đẩy • súng tự bắn",
            "Vòng bo: Bo 1 (60s) → Bo 2 (20s) → Bo 3 (5s) • 1 ải = 5 màn • màn đầu: ${g.perHundredFor(s.stageSel)} chướng ngại/100m",
            "Qua ải 1 mở GARA (xe, nỏ, máy ném đá) • qua ải 5 mở vật phẩm 👥 Nhân bản",
        )) {
            fitText(c, line, lx, y, 22f, lw)
            y += 34f
        }
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

    private fun lines(c: Canvas, x: Float, top: Float, vararg ls: String) {
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        var y = top
        for (l in ls) {
            fitText(c, l, x, y, 32f, g.W - 80f)
            y += 46f
        }
    }

    private fun drawOverlay(c: Canvas) {
        val W = g.W
        val H = g.H
        when (g.state) {
            State.PAUSED -> {
                dim(c)
                title(c, "TẠM DỪNG", W / 2, H * 0.24f, Color.WHITE)
                drawMenuButtons(c)
            }
            State.RESULT -> {
                dim(c)
                title(c, "NGÀY ${g.save.day - 1} KẾT THÚC", W / 2, 100f, 0xFFFF5252.toInt(), 68f)
                text.textAlign = Paint.Align.CENTER
                text.textSize = 36f
                text.color = 0xFFFFAB91.toInt()
                c.drawText(g.endReason, W / 2, 152f, text)
                lines(
                    c, W / 2, 220f,
                    "Quãng đường: ${g.maxDist.toInt()}m / ${g.goalM.toInt()}m" + if (g.newRecord) "  🏆 KỶ LỤC MỚI!" else "",
                    "Zombie bị tông: ${g.kills}   •   Vật cản phá: ${g.smashed}",
                    "Tiền quãng đường: ${g.distMoney}$   •   Tiền thưởng: ${g.runMoney}$",
                    "Tổng nhận: ${g.distMoney + g.runMoney}$   •   Ví: ${g.save.money}$",
                )
                text.color = 0xFFFFE0B2.toInt()
                fitText(c, "Nâng cấp động cơ, xăng, tên lửa… để đi xa hơn vào ngày mai!", W / 2, H - 160f, 26f, W - 80f)
                drawMenuButtons(c)
            }
            State.STAGE_DONE -> {
                dim(c)
                title(c, "VƯỢT ẢI ${g.stage}!", W / 2, 110f, 0xFFFFD740.toInt(), 80f)
                lines(
                    c, W / 2, 200f,
                    "Đã tới trạm cứu hộ!   •   Ngày ${g.save.day - 1}",
                    "Zombie bị tông: ${g.kills}   •   Vật cản phá: ${g.smashed}",
                    "Thưởng vượt ải: ${g.stageBonus}$   •   Tổng nhận: ${g.distMoney + g.runMoney + g.stageBonus}$",
                )
                val unlock = when (g.stage) {
                    1 -> "🔓 MỞ KHÓA GARA: đổi xe & vũ khí (Nỏ, Máy ném đá)!"
                    5 -> "🔓 MỞ KHÓA VẬT PHẨM 👥 NHÂN BẢN: thêm 1 mạng trong 10 giây!"
                    else -> null
                }
                if (unlock != null) {
                    text.color = Color.argb((180 + 75 * sin(g.anim * 6f)).toInt(), 100, 255, 218)
                    fitText(c, unlock, W / 2, 380f, 34f, W - 80f)
                }
                drawMenuButtons(c)
            }
            State.VICTORY -> {
                dim(c)
                title(c, "CHIẾN THẮNG!", W / 2, 140f, 0xFFFFD740.toInt(), 90f)
                lines(
                    c, W / 2, 240f,
                    "Bạn đã vượt qua cả ${Game.STAGES} ải và sống sót!",
                    "Số ngày: ${g.save.day - 1}",
                    "Ví: ${g.save.money}$",
                )
                drawMenuButtons(c)
            }
            else -> {}
        }
    }
}
