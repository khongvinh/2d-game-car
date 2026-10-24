package com.khongvinh.carsurvival

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Vehicle and weapon artwork: five families with five versions each, drawn with
 * Canvas primitives around the body centre (x forward, y down) so the wheels sit
 * at [Car.WHEEL_X] / [Car.WHEEL_Y] like the physics expects.
 */
class CarArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val body = Path()
    private val r = RectF()

    /** Seconds since start, for spinning barrels and flickering lights. */
    var time = 0f

    private var bodyCol = 0
    private var acc = 0
    private var glass = 0
    private var wreck = false
    private var ghost = false

    private fun deg(a: Float) = a * 180f / PI.toFloat()

    private fun pivot(c: Canvas, a: Float, px: Float, py: Float) {
        c.translate(px, py)
        c.rotate(deg(a))
        c.translate(-px, -py)
    }

    private fun darker(col: Int, f: Float = 0.7f) = Color.argb(
        (col ushr 24) and 255, (((col shr 16) and 255) * f).toInt(), (((col shr 8) and 255) * f).toInt(), ((col and 255) * f).toInt(),
    )

    fun draw(
        c: Canvas,
        v: Vehicle,
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
        squat: Float = 0f,
        slash: Float = 0f,
    ) {
        this.wreck = wreck
        this.ghost = ghost
        bodyCol = when {
            wreck -> 0xFF2E2A27.toInt()
            ghost -> 0xFF64FFDA.toInt()
            else -> v.color
        }
        acc = when {
            wreck -> 0xFF1A1A1A.toInt()
            ghost -> 0xFF00BFA5.toInt()
            else -> v.accent
        }
        glass = when {
            wreck -> 0xFF111111.toInt()
            ghost -> 0x8800695C.toInt()
            else -> 0xFF263238.toInt()
        }
        val k = v.variant
        c.save()
        c.translate(x, y)
        c.rotate(deg(angle))
        val wy0 = Car.WHEEL_Y - min(16f, compress?.get(0) ?: 0f)
        val wy1 = Car.WHEEL_Y - min(16f, compress?.get(1) ?: 0f)
        val rim = when {
            wreck -> 0xFF4E4E4E.toInt()
            k == 0 -> 0xFF8D8D7A.toInt()
            k == 4 -> v.accent
            else -> 0xFFCFD8DC.toInt()
        }
        when (v.cls) {
            VehicleClass.MOTO -> {
                booster(c, -6f, boosting)
                motoWheel(c, Car.WHEEL_X[0], wy0, wheelRot, rim, k == 1)
                motoWheel(c, Car.WHEEL_X[1], wy1, wheelRot, rim, k == 1)
                c.save()
                pivot(c, squat * 1.4f, 0f, 24f)
                moto(c, k, wy1)
                mounted(c, v, weapon, aim - angle - squat * 1.4f, slash, 70f, -6f)
                c.restore()
            }
            VehicleClass.TANK -> {
                booster(c, -40f, boosting)
                c.save()
                pivot(c, squat * 0.5f, 0f, 20f)
                tank(c, k)
                c.restore()
                tracks(c, k, wheelRot, (wy0 + wy1) / 2f - Car.WHEEL_Y)
                if (k >= 3) skirts(c, k)
                c.save()
                pivot(c, squat * 0.5f, 0f, 20f)
                mounted(c, v, weapon, aim - angle - squat * 0.5f, slash, 102f, 0f)
                c.restore()
            }
            else -> {
                booster(c, -20f, boosting)
                for (i in 0..1) {
                    stroke.color = 0xFF37474F.toInt()
                    stroke.strokeWidth = 8f
                    c.drawLine(Car.WHEEL_X[i], 0f, Car.WHEEL_X[i], if (i == 0) wy0 else wy1, stroke)
                }
                c.save()
                pivot(c, squat, 0f, 24f)
                when (v.cls) {
                    VehicleClass.CAR -> car(c, k)
                    VehicleClass.PICKUP -> pickup(c, k)
                    else -> armored(c, k)
                }
                val front = if (v.cls == VehicleClass.ARMORED) 98f else 93f
                if (!(v.cls == VehicleClass.ARMORED && k == 3)) ram(c, front, k)
                lights(c, v.cls, k)
                c.restore()
                val style = when {
                    v.cls == VehicleClass.ARMORED -> 2
                    v.cls == VehicleClass.PICKUP && k >= 3 -> 1
                    else -> 0
                }
                wheel(c, Car.WHEEL_X[0], wy0, wheelRot, rim, style)
                wheel(c, Car.WHEEL_X[1], wy1, wheelRot, rim, style)
                c.save()
                pivot(c, squat, 0f, 24f)
                mounted(c, v, weapon, aim - angle - squat, slash, front + 4f, 2f)
                c.restore()
            }
        }
        c.restore()
    }

    // ------------------------------------------------------------ common

    private fun booster(c: Canvas, y: Float, on: Boolean) {
        fill.color = 0xFF616161.toInt()
        c.drawRoundRect(-104f, y - 10f, -66f, y + 10f, 8f, 8f, fill)
        fill.color = 0x44FFFFFF
        c.drawRoundRect(-102f, y - 8f, -70f, y - 2f, 4f, 4f, fill)
        fill.color = 0xFFB71C1C.toInt()
        c.drawRect(-74f, y - 12f, -66f, y + 12f, fill)
        if (!on) return
        val f = 30f + Random.nextFloat() * 40f
        fill.color = 0xCCFF6D00.toInt()
        path.reset()
        path.moveTo(-104f, y - 12f)
        path.lineTo(-104f - f - 40f, y)
        path.lineTo(-104f, y + 12f)
        path.close()
        c.drawPath(path, fill)
        fill.color = 0xFFFFEB3B.toInt()
        path.reset()
        path.moveTo(-104f, y - 6f)
        path.lineTo(-104f - f * 0.6f, y)
        path.lineTo(-104f, y + 6f)
        path.close()
        c.drawPath(path, fill)
        fill.color = Color.WHITE
        c.drawCircle(-106f, y, 5f, fill)
    }

    /** Fills [body] with the paint job, light/dark bands and an outline. */
    private fun paintBody(c: Canvas, col: Int = bodyCol) {
        fill.color = col
        c.drawPath(body, fill)
        c.save()
        c.clipPath(body)
        fill.color = 0x45FFFFFF
        c.drawRect(-140f, -90f, 140f, -30f, fill)
        fill.color = 0x22FFFFFF
        c.drawRect(-140f, -30f, 140f, -18f, fill)
        fill.color = 0x45000000
        c.drawRect(-140f, 4f, 140f, 60f, fill)
        if (wreck) {
            for (i in 0 until 7) {
                val on = (time * 8f + i * 1.7f).toInt() % 3 != 0
                fill.color = if (on) 0xFFFF6D00.toInt() else 0xFF8D2B00.toInt()
                c.drawCircle(-72f + i * 26f, -12f + (i % 3) * 9f, 3.5f, fill)
            }
        }
        c.restore()
        stroke.color = 0xCC1A1A1A.toInt()
        stroke.strokeWidth = 3f
        c.drawPath(body, stroke)
    }

    /** Draws inside the body outline only. */
    private inline fun inBody(c: Canvas, block: () -> Unit) {
        c.save()
        c.clipPath(body)
        block()
        c.restore()
    }

    private fun wheelArches(c: Canvas) = inBody(c) {
        fill.color = 0xFF151515.toInt()
        for (i in 0..1) c.drawCircle(Car.WHEEL_X[i], Car.WHEEL_Y, 30f, fill)
    }

    private fun rivets(c: Canvas, x0: Float, x1: Float, y: Float, n: Int) {
        for (i in 0 until n) {
            val x = x0 + (x1 - x0) * i / (n - 1).coerceAtLeast(1)
            fill.color = 0x66000000
            c.drawCircle(x + 1f, y + 1f, 2.6f, fill)
            fill.color = 0xFFB0BEC5.toInt()
            c.drawCircle(x, y, 2.4f, fill)
        }
    }

    private fun rust(c: Canvas) = inBody(c) {
        fill.color = 0x998D4A1E.toInt()
        c.drawCircle(-60f, 4f, 9f, fill)
        c.drawCircle(30f, 8f, 7f, fill)
        c.drawCircle(-10f, -10f, 5f, fill)
        fill.color = 0x66000000
        c.drawCircle(-40f, 10f, 4f, fill)
        c.drawCircle(64f, -4f, 3f, fill)
    }

    private fun spike(c: Canvas, x: Float, y: Float, dx: Float, dy: Float, w: Float) {
        // a steel cone from (x, y) pointing along (dx, dy)
        val l = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
        val nx = -dy / l * w
        val ny = dx / l * w
        path.reset()
        path.moveTo(x + nx, y + ny)
        path.lineTo(x + dx, y + dy)
        path.lineTo(x - nx, y - ny)
        path.close()
        fill.color = 0xFF9E9E9E.toInt()
        c.drawPath(path, fill)
        fill.color = 0x66FFFFFF
        path.reset()
        path.moveTo(x + nx, y + ny)
        path.lineTo(x + dx, y + dy)
        path.lineTo(x, y)
        path.close()
        c.drawPath(path, fill)
    }

    private fun skull(c: Canvas, x: Float, y: Float, s: Float) {
        fill.color = 0xFFECEFF1.toInt()
        c.drawCircle(x, y, 8f * s, fill)
        c.drawRect(x - 4.5f * s, y + 4f * s, x + 4.5f * s, y + 10f * s, fill)
        fill.color = 0xFF212121.toInt()
        c.drawCircle(x - 3f * s, y, 2.4f * s, fill)
        c.drawCircle(x + 3f * s, y, 2.4f * s, fill)
        c.drawRect(x - 0.8f * s, y + 5f * s, x + 0.8f * s, y + 9f * s, fill)
    }

    private fun flames(c: Canvas, x: Float, y: Float, len: Float) = inBody(c) {
        // flame decal licking back from the front
        for ((col, sc) in listOf(0xFFFF6D00.toInt() to 1f, 0xFFFFD740.toInt() to 0.6f)) {
            fill.color = col
            path.reset()
            path.moveTo(x, y - 8f * sc)
            path.lineTo(x - len * sc, y - 14f * sc)
            path.lineTo(x - len * 0.6f * sc, y - 4f * sc)
            path.lineTo(x - len * 0.95f * sc, y + 2f * sc)
            path.lineTo(x - len * 0.5f * sc, y + 4f * sc)
            path.lineTo(x - len * 0.8f * sc, y + 12f * sc)
            path.lineTo(x, y + 8f * sc)
            path.close()
            c.drawPath(path, fill)
        }
    }

    private fun windowGlass(c: Canvas) {
        fill.color = glass
        c.drawPath(path, fill)
        if (!wreck) {
            c.save()
            c.clipPath(path)
            stroke.color = 0x55FFFFFF
            stroke.strokeWidth = 4f
            c.drawLine(-40f, -10f, 0f, -60f, stroke)
            stroke.strokeWidth = 2f
            c.drawLine(-28f, -10f, 12f, -60f, stroke)
            c.restore()
        }
    }

    private fun grill(c: Canvas) {
        // steel mesh over the current window path
        c.save()
        c.clipPath(path)
        stroke.color = 0xFF9E9E9E.toInt()
        stroke.strokeWidth = 2f
        var gx = -100f
        while (gx < 100f) {
            c.drawLine(gx, -70f, gx + 40f, 0f, stroke)
            c.drawLine(gx + 40f, -70f, gx, 0f, stroke)
            gx += 9f
        }
        c.restore()
    }

    /** Bumper with spikes growing with the version. */
    private fun ram(c: Canvas, x: Float, k: Int) {
        fill.color = 0xFF757575.toInt()
        c.drawRoundRect(x - 10f, -10f, x + 6f, 20f, 4f, 4f, fill)
        fill.color = 0x55FFFFFF
        c.drawRect(x - 10f, -10f, x + 6f, -4f, fill)
        if (k >= 1) {
            // bull bar
            stroke.color = 0xFF424242.toInt()
            stroke.strokeWidth = 6f
            c.drawLine(x - 8f, -18f, x + 10f, -14f, stroke)
            c.drawLine(x + 10f, -14f, x + 10f, 18f, stroke)
            c.drawLine(x + 10f, 2f, x - 4f, 2f, stroke)
        }
        val n = k.coerceAtMost(4)
        for (i in 0 until n) {
            val yy = -12f + i * (30f / n.coerceAtLeast(1)) + 4f
            spike(c, x + 10f, yy, 16f + k * 2f, 0f, 5f)
        }
    }

    private fun lights(c: Canvas, cls: VehicleClass, k: Int) {
        if (wreck) return
        val (hx, hy) = when (cls) {
            VehicleClass.ARMORED -> 92f to -12f
            else -> 86f to -6f
        }
        fill.color = if (cls == VehicleClass.ARMORED && k == 4) 0x88FF1744.toInt() else 0x66FFF59D
        c.drawCircle(hx, hy, 10f, fill)
        fill.color = if (cls == VehicleClass.ARMORED && k == 4) 0xFFFF1744.toInt() else 0xFFFFF9C4.toInt()
        c.drawCircle(hx, hy, 5f, fill)
        fill.color = 0xFFFF1744.toInt()
        c.drawRect(-90f, -8f, -84f, 4f, fill)
    }

    // ----------------------------------------------------------- wheels

    private fun wheel(c: Canvas, x: Float, y: Float, rot: Float, rim: Int, style: Int) {
        fill.color = 0xFF151515.toInt()
        c.drawCircle(x, y, Car.WHEEL_R, fill)
        fill.color = 0xFF2B2B2B.toInt()
        val blocks = if (style == 1) 8 else 12
        for (i in 0 until blocks) {
            val a = rot + i * (2f * PI.toFloat() / blocks)
            if (style == 1) {
                c.drawCircle(x + cos(a) * 22f, y + sin(a) * 22f, 6f, fill)
            } else {
                c.drawCircle(x + cos(a) * 21.5f, y + sin(a) * 21.5f, 3.5f, fill)
            }
        }
        stroke.color = 0xFF000000.toInt()
        stroke.strokeWidth = 2f
        c.drawCircle(x, y, 16f, stroke)
        fill.color = rim
        c.drawCircle(x, y, if (style == 2) 15f else 13f, fill)
        fill.color = 0x55FFFFFF
        c.drawCircle(x - 3f, y - 3f, 6f, fill)
        if (style == 2) {
            fill.color = 0xFF424242.toInt()
            for (i in 0 until 8) {
                val a = rot + i * 0.785f
                c.drawCircle(x + cos(a) * 10f, y + sin(a) * 10f, 2.2f, fill)
            }
        } else {
            stroke.color = darker(rim, 0.55f)
            stroke.strokeWidth = 3f
            for (i in 0 until 5) {
                val a = rot + i * 1.2566f
                c.drawLine(x, y, x + cos(a) * 12f, y + sin(a) * 12f, stroke)
            }
        }
        fill.color = 0xFF424242.toInt()
        c.drawCircle(x, y, 3.5f, fill)
    }

    private fun motoWheel(c: Canvas, x: Float, y: Float, rot: Float, rim: Int, knobby: Boolean) {
        fill.color = 0xFF151515.toInt()
        c.drawCircle(x, y, Car.WHEEL_R, fill)
        if (knobby) {
            fill.color = 0xFF2B2B2B.toInt()
            for (i in 0 until 12) {
                val a = rot + i * 0.5236f
                c.drawCircle(x + cos(a) * 22f, y + sin(a) * 22f, 4f, fill)
            }
        }
        fill.color = 0xFF3A3A3A.toInt()
        c.drawCircle(x, y, 17f, fill)
        stroke.color = rim
        stroke.strokeWidth = 3f
        c.drawCircle(x, y, 17f, stroke)
        stroke.strokeWidth = 1.5f
        for (i in 0 until 10) {
            val a = rot + i * 0.628f
            c.drawLine(x + cos(a) * 3f, y + sin(a) * 3f, x + cos(a + 0.4f) * 16f, y + sin(a + 0.4f) * 16f, stroke)
        }
        fill.color = rim
        c.drawCircle(x, y, 5f, fill)
        // brake disc
        stroke.color = 0x88B0BEC5.toInt()
        stroke.strokeWidth = 2f
        c.drawCircle(x, y, 9f, stroke)
    }

    // ----------------------------------------------------------- bodies

    private fun moto(c: Canvas, k: Int, frontY: Float) {
        val frame = if (wreck) 0xFF1A1A1A.toInt() else 0xFF37474F.toInt()
        val chrome = if (wreck) 0xFF3A3A3A.toInt() else 0xFFCFD8DC.toInt()
        // swingarm and frame
        stroke.color = frame
        stroke.strokeWidth = 7f
        c.drawLine(Car.WHEEL_X[0], Car.WHEEL_Y, -14f, 8f, stroke)
        c.drawLine(-14f, 8f, 34f, -20f, stroke)
        c.drawLine(-30f, -8f, 30f, -18f, stroke)
        // front fork (chopper: long and raked)
        stroke.color = chrome
        stroke.strokeWidth = 6f
        val forkTop = if (k == 3) 22f to -34f else 34f to -26f
        c.drawLine(forkTop.first, forkTop.second, Car.WHEEL_X[1], frontY, stroke)
        // exhaust
        stroke.color = chrome
        stroke.strokeWidth = 7f
        c.drawLine(-4f, 18f, -40f, 22f, stroke)
        c.drawLine(-40f, 22f, -72f, 14f, stroke)
        fill.color = chrome
        c.drawRoundRect(-88f, 8f, -58f, 20f, 6f, 6f, fill)
        if (k >= 2) c.drawRoundRect(-88f, -4f, -58f, 6f, 5f, 5f, fill)
        // engine block
        fill.color = if (wreck) 0xFF222222.toInt() else 0xFF424242.toInt()
        c.drawRoundRect(-28f, -6f, 14f, 22f, 7f, 7f, fill)
        stroke.color = 0xFF616161.toInt()
        stroke.strokeWidth = 2f
        for (i in 0 until 4) c.drawLine(-24f, -2f + i * 6f, 10f, -2f + i * 6f, stroke)
        fill.color = chrome
        c.drawCircle(-6f, 12f, 7f, fill)
        // rear fender / tail cowl
        body.reset()
        body.moveTo(-80f, -6f)
        body.lineTo(-48f, -18f)
        body.lineTo(-8f, -14f)
        body.lineTo(-8f, -4f)
        body.lineTo(-62f, 2f)
        body.close()
        paintBody(c)
        // fuel tank
        body.reset()
        body.moveTo(-8f, -8f)
        body.quadTo(2f, -34f, 34f, -26f)
        body.lineTo(38f, -12f)
        body.close()
        paintBody(c)
        if (k >= 1) inBody(c) {
            fill.color = acc
            c.drawRect(-10f, -22f, 40f, -16f, fill)
        }
        if (k >= 3) skull(c, 16f, -18f, 0.7f)
        // fenders
        stroke.color = bodyCol
        stroke.strokeWidth = 7f
        r.set(-86f, 2f, -26f, 60f)
        c.drawArc(r, 200f, 110f, false, stroke)
        if (k == 1) {
            // dirt bike: high front fender and number plate
            fill.color = bodyCol
            path.reset()
            path.moveTo(40f, -6f); path.lineTo(84f, -2f); path.lineTo(56f, 4f); path.close()
            c.drawPath(path, fill)
            fill.color = Color.WHITE
            c.drawRoundRect(30f, -40f, 46f, -24f, 4f, 4f, fill)
            text.color = Color.BLACK
            text.textSize = 13f
            c.drawText("7", 38f, -27f, text)
        } else {
            r.set(32f, frontY - 28f, 84f, frontY + 28f)
            c.drawArc(r, 205f, 110f, false, stroke)
        }
        // seat
        fill.color = 0xFF212121.toInt()
        c.drawRoundRect(-48f, -24f, -6f, -13f, 6f, 6f, fill)
        if (k >= 2 && k != 3) {
            // sport fairing with a windscreen
            body.reset()
            body.moveTo(28f, -38f)
            body.lineTo(58f, -18f)
            body.lineTo(52f, 2f)
            body.lineTo(26f, -8f)
            body.close()
            paintBody(c)
            fill.color = if (wreck) glass else 0x8890CAF9.toInt()
            path.reset()
            path.moveTo(28f, -38f); path.lineTo(34f, -54f); path.lineTo(44f, -32f); path.close()
            c.drawPath(path, fill)
        }
        if (k == 4) {
            // war bike: armour plate over the engine, spikes on the tail
            fill.color = darker(bodyCol, 0.75f)
            c.drawRoundRect(-32f, -8f, 18f, 18f, 5f, 5f, fill)
            rivets(c, -26f, 12f, -3f, 4)
            rivets(c, -26f, 12f, 13f, 4)
            spike(c, -78f, -8f, -14f, -12f, 4f)
            spike(c, -64f, -14f, -8f, -16f, 4f)
        }
        // handlebar
        stroke.color = 0xFF212121.toInt()
        stroke.strokeWidth = 5f
        if (k == 3) {
            c.drawLine(22f, -34f, 16f, -58f, stroke)
            c.drawLine(16f, -58f, 26f, -60f, stroke)
        } else {
            c.drawLine(34f, -26f, 28f, -40f, stroke)
        }
        if (!wreck) {
            fill.color = 0x66FFF59D
            c.drawCircle(44f, -20f, 11f, fill)
            fill.color = 0xFFFFF9C4.toInt()
            c.drawCircle(44f, -20f, 6f, fill)
            fill.color = 0xFFFF1744.toInt()
            c.drawRect(-82f, -10f, -76f, -4f, fill)
            rider(c, k)
        }
    }

    private fun rider(c: Canvas, k: Int) {
        val jacket = if (ghost) 0xFF00897B.toInt() else darker(acc, 0.55f)
        val hand = if (k == 3) 18f to -56f else 28f to -40f
        val lean = if (k == 2 || k == 4) 8f else 0f
        // leg
        stroke.color = if (ghost) 0xFF26A69A.toInt() else 0xFF283593.toInt()
        stroke.strokeWidth = 11f
        c.drawLine(-22f, -22f, 4f, -16f, stroke)
        c.drawLine(4f, -16f, 2f, 4f, stroke)
        fill.color = 0xFF212121.toInt()
        c.drawRoundRect(-6f, 0f, 12f, 10f, 4f, 4f, fill)
        // torso
        stroke.color = jacket
        stroke.strokeWidth = 18f
        c.drawLine(-22f, -24f, -6f + lean, -54f, stroke)
        // arm
        stroke.strokeWidth = 9f
        c.drawLine(-6f + lean, -50f, hand.first, hand.second, stroke)
        if (k == 4) spike(c, -6f + lean, -60f, -4f, -12f, 4f)
        // helmet
        fill.color = if (ghost) 0xFF64FFDA.toInt() else acc
        c.drawCircle(2f + lean, -68f, 13f, fill)
        fill.color = 0x55FFFFFF
        c.drawCircle(-2f + lean, -73f, 5f, fill)
        fill.color = 0xFF263238.toInt()
        c.drawRoundRect(6f + lean, -72f, 16f + lean, -62f, 3f, 3f, fill)
    }

    private fun car(c: Canvas, k: Int) {
        body.reset()
        body.moveTo(-90f, -4f)
        body.lineTo(-86f, -18f)
        body.lineTo(-48f, -21f)
        body.lineTo(-26f, -42f)
        body.lineTo(20f, -43f)
        body.lineTo(44f, -21f)
        body.lineTo(86f, -15f)
        body.quadTo(96f, -12f, 95f, 4f)
        body.lineTo(90f, 16f)
        body.lineTo(-88f, 16f)
        body.close()
        paintBody(c)
        wheelArches(c)
        if (k == 0) {
            rust(c)
            inBody(c) {
                fill.color = acc
                c.drawRect(-38f, -20f, -2f, 14f, fill)
            }
        }
        if (k == 1 || k == 4) inBody(c) {
            fill.color = acc
            c.drawRect(-100f, -26f, 100f, -21f, fill)
            c.drawRect(-100f, -2f, 100f, 3f, fill)
        }
        // doors
        stroke.color = 0x55000000
        stroke.strokeWidth = 2f
        c.drawLine(-2f, -21f, -2f, 14f, stroke)
        c.drawLine(-38f, -21f, -40f, 14f, stroke)
        fill.color = 0x88000000.toInt()
        c.drawRect(-30f, -12f, -22f, -9f, fill)
        c.drawRect(6f, -12f, 14f, -9f, fill)
        // windows
        path.reset()
        path.moveTo(-20f, -37f); path.lineTo(16f, -38f); path.lineTo(36f, -21f); path.lineTo(-20f, -21f); path.close()
        windowGlass(c)
        if (k >= 3) grill(c)
        stroke.color = bodyCol
        stroke.strokeWidth = 5f
        c.drawLine(0f, -40f, 0f, -21f, stroke)
        when (k) {
            1 -> {
                fill.color = 0xFF212121.toInt()
                c.drawRoundRect(52f, -24f, 74f, -16f, 3f, 3f, fill)
            }
            2 -> {
                // rally: rear wing, roof lights, number roundel
                stroke.color = 0xFF212121.toInt()
                stroke.strokeWidth = 4f
                c.drawLine(-78f, -19f, -74f, -34f, stroke)
                fill.color = acc
                c.drawRoundRect(-96f, -40f, -62f, -33f, 3f, 3f, fill)
                fill.color = 0xFF212121.toInt()
                c.drawRect(-18f, -48f, 14f, -43f, fill)
                fill.color = 0xFFFFEB3B.toInt()
                for (i in 0 until 4) c.drawCircle(-14f + i * 9f, -49f, 3.5f, fill)
                fill.color = Color.WHITE
                c.drawCircle(-20f, -3f, 11f, fill)
                text.color = Color.BLACK
                text.textSize = 15f
                c.drawText("27", -20f, 2f, text)
            }
            3, 4 -> {
                for (i in 0 until 4) spike(c, -18f + i * 11f, -43f, 0f, -12f - k * 2f, 4f)
                fill.color = 0xFFB0BEC5.toInt()
                c.drawRoundRect(-60f, 12f, 30f, 19f, 3f, 3f, fill)
                if (k == 4) {
                    flames(c, 90f, -6f, 80f)
                    skull(c, 66f, -12f, 0.75f)
                    fill.color = darker(bodyCol, 0.6f)
                    c.drawRect(-36f, -18f, -6f, 10f, fill)
                    rivets(c, -32f, -10f, -14f, 3)
                    rivets(c, -32f, -10f, 6f, 3)
                }
            }
        }
    }

    private fun pickup(c: Canvas, k: Int) {
        body.reset()
        body.moveTo(-90f, 0f)
        body.lineTo(-88f, -16f)
        body.lineTo(-26f, -16f)
        body.lineTo(-22f, -48f)
        body.lineTo(22f, -48f)
        body.lineTo(42f, -20f)
        body.lineTo(84f, -14f)
        body.quadTo(92f, -12f, 91f, 6f)
        body.lineTo(86f, 20f)
        body.lineTo(-86f, 20f)
        body.close()
        paintBody(c)
        wheelArches(c)
        if (k == 0) rust(c)
        // bed
        fill.color = 0x40000000
        c.drawRect(-84f, -14f, -30f, -6f, fill)
        stroke.color = 0x66000000
        stroke.strokeWidth = 2f
        c.drawLine(-86f, -16f, -26f, -16f, stroke)
        c.drawLine(-28f, -16f, -28f, 18f, stroke)
        c.drawLine(4f, -20f, 4f, 18f, stroke)
        if (k >= 1 && k != 2) inBody(c) {
            fill.color = acc
            c.drawRect(-100f, -1f, 100f, 4f, fill)
        }
        path.reset()
        path.moveTo(-14f, -42f); path.lineTo(18f, -42f); path.lineTo(34f, -21f); path.lineTo(-14f, -21f); path.close()
        windowGlass(c)
        if (k == 2 || k == 4) grill(c)
        if (k >= 1) {
            // roof light bar
            fill.color = 0xFF212121.toInt()
            c.drawRect(-18f, -55f, 22f, -48f, fill)
            fill.color = if (wreck) 0xFF333333.toInt() else 0xFFFFEB3B.toInt()
            for (i in 0 until 4) c.drawCircle(-12f + i * 10f, -52f, 3.2f, fill)
        }
        if (k >= 2) {
            // roll cage over the bed
            stroke.color = 0xFF9E9E9E.toInt()
            stroke.strokeWidth = 5f
            c.drawLine(-80f, -16f, -74f, -40f, stroke)
            c.drawLine(-74f, -40f, -36f, -40f, stroke)
            c.drawLine(-36f, -40f, -30f, -16f, stroke)
            c.drawLine(-74f, -40f, -30f, -16f, stroke)
        }
        if (k >= 3) {
            // snorkel and fender flares
            stroke.color = 0xFF212121.toInt()
            stroke.strokeWidth = 6f
            c.drawLine(40f, -18f, 38f, -54f, stroke)
            c.drawLine(38f, -54f, 48f, -56f, stroke)
            for (i in 0..1) {
                stroke.color = 0xFF212121.toInt()
                stroke.strokeWidth = 7f
                r.set(Car.WHEEL_X[i] - 32f, Car.WHEEL_Y - 32f, Car.WHEEL_X[i] + 32f, Car.WHEEL_Y + 32f)
                c.drawArc(r, 200f, 140f, false, stroke)
            }
        }
        if (k == 4) {
            flames(c, 88f, -2f, 70f)
            skull(c, 62f, -8f, 0.75f)
            for (i in 0 until 3) spike(c, -10f + i * 12f, -55f, 0f, -14f, 4f)
        }
    }

    private fun armored(c: Canvas, k: Int) {
        body.reset()
        body.moveTo(-90f, -30f)
        body.lineTo(-74f, -56f)
        body.lineTo(40f, -56f)
        body.lineTo(68f, -30f)
        body.lineTo(96f, -20f)
        body.lineTo(98f, 14f)
        body.lineTo(88f, 22f)
        body.lineTo(-88f, 22f)
        body.close()
        paintBody(c)
        wheelArches(c)
        // armour plate seams and rivets
        stroke.color = 0x55000000
        stroke.strokeWidth = 2f
        for (px in floatArrayOf(-60f, -12f, 34f)) c.drawLine(px, -54f, px, 20f, stroke)
        c.drawLine(-88f, -28f, 66f, -28f, stroke)
        rivets(c, -82f, 30f, -50f, 9)
        rivets(c, -84f, 90f, 16f, 12)
        // hatch
        fill.color = darker(bodyCol, 0.75f)
        c.drawRoundRect(-22f, -64f, 10f, -55f, 4f, 4f, fill)
        // windows: front slit and side slits
        path.reset()
        path.moveTo(44f, -50f); path.lineTo(62f, -32f); path.lineTo(40f, -32f); path.close()
        windowGlass(c)
        fill.color = glass
        c.drawRect(-52f, -46f, -30f, -38f, fill)
        c.drawRect(-6f, -46f, 16f, -38f, fill)
        when (k) {
            0 -> {
                inBody(c) {
                    fill.color = acc
                    c.drawRect(-100f, -22f, 110f, -14f, fill)
                }
                fill.color = acc
                c.drawCircle(-34f, 0f, 12f, fill)
                text.color = darker(bodyCol, 0.5f)
                text.textSize = 18f
                c.drawText("$", -34f, 6f, text)
            }
            1 -> {
                inBody(c) {
                    fill.color = acc
                    c.drawRect(-100f, -24f, 110f, -16f, fill)
                }
                text.color = Color.WHITE
                text.textSize = 20f
                c.drawText("SWAT", -20f, 6f, text)
                fill.color = if ((time * 6f).toInt() % 2 == 0) 0xFF2979FF.toInt() else 0xFFFF1744.toInt()
                c.drawRoundRect(-60f, -62f, -40f, -56f, 3f, 3f, fill)
            }
            2 -> {
                inBody(c) {
                    stroke.color = acc
                    stroke.strokeWidth = 4f
                    for (i in 0 until 9) {
                        r.set(-90f + i * 22f, 0f, -68f + i * 22f, 14f)
                        c.drawArc(r, 0f, 180f, false, stroke)
                    }
                }
                // propeller at the back
                fill.color = 0xFF9E9E9E.toInt()
                val a = time * 20f
                for (i in 0..1) {
                    val dy = sin(a + i * PI.toFloat()) * 10f
                    c.drawOval(-100f, 8f + dy - 3f, -90f, 8f + dy + 3f, fill)
                }
            }
            3 -> {
                // bulldozer blade
                stroke.color = 0xFF424242.toInt()
                stroke.strokeWidth = 6f
                c.drawLine(80f, -4f, 104f, -8f, stroke)
                body.reset()
                body.moveTo(98f, -38f)
                body.quadTo(118f, -10f, 112f, 24f)
                body.lineTo(100f, 24f)
                body.quadTo(104f, -6f, 92f, -34f)
                body.close()
                paintBody(c, acc)
                inBody(c) {
                    fill.color = 0xFF212121.toInt()
                    for (i in 0 until 4) {
                        path.reset()
                        path.moveTo(90f, -34f + i * 18f); path.lineTo(120f, -44f + i * 18f)
                        path.lineTo(120f, -36f + i * 18f); path.lineTo(90f, -26f + i * 18f); path.close()
                        c.drawPath(path, fill)
                    }
                }
                stroke.color = 0xFF212121.toInt()
                stroke.strokeWidth = 6f
                c.drawLine(20f, -56f, 20f, -74f, stroke)
            }
            4 -> {
                fill.color = darker(bodyCol, 0.8f)
                c.drawRect(-88f, 2f, 94f, 20f, fill)
                rivets(c, -80f, 86f, 11f, 12)
                for (i in 0 until 4) spike(c, -60f + i * 26f, -56f, 0f, -14f, 4.5f)
                skull(c, 76f, -6f, 0.8f)
                inBody(c) {
                    fill.color = acc
                    c.drawRect(-100f, -30f, 110f, -26f, fill)
                }
            }
        }
    }

    private fun tank(c: Canvas, k: Int) {
        // hull
        body.reset()
        body.moveTo(-94f, -2f)
        body.lineTo(-82f, -30f)
        body.lineTo(72f, -30f)
        body.lineTo(100f, -8f)
        body.lineTo(98f, 6f)
        body.lineTo(-92f, 6f)
        body.close()
        paintBody(c)
        if (k == 2) camo(c)
        rivets(c, -76f, 66f, -24f, 8)
        // main gun (decorative) and turret
        val heavy = k >= 3
        val gun = if (wreck) 0xFF1A1A1A.toInt() else darker(bodyCol, 0.55f)
        fill.color = gun
        c.drawRect(28f, -54f, if (heavy) 140f else 128f, -45f, fill)
        c.drawRect(if (heavy) 132f else 120f, -57f, if (heavy) 146f else 134f, -42f, fill)
        if (k == 4) {
            c.drawRect(28f, -42f, 136f, -35f, fill)
            c.drawRect(128f, -44f, 142f, -33f, fill)
        }
        body.reset()
        if (heavy) {
            body.moveTo(-64f, -30f)
            body.lineTo(-54f, -62f)
            body.lineTo(24f, -66f)
            body.lineTo(46f, -46f)
            body.lineTo(42f, -30f)
        } else {
            body.moveTo(-56f, -30f)
            body.lineTo(-46f, -56f)
            body.lineTo(18f, -60f)
            body.lineTo(36f, -44f)
            body.lineTo(34f, -30f)
        }
        body.close()
        paintBody(c)
        if (k == 2) camo(c)
        if (k == 4) inBody(c) {
            fill.color = acc
            c.drawRect(-80f, -40f, 60f, -35f, fill)
        }
        // hatch under the weapon
        fill.color = darker(bodyCol, 0.75f)
        val top = if (heavy) -66f else -60f
        c.drawRoundRect(-28f, top - 6f, -2f, top + 2f, 4f, 4f, fill)
        fill.color = 0x66000000
        c.drawCircle(10f, -42f, 4f, fill)
        if (k == 1) {
            // desert jerrycans on the back
            fill.color = 0xFF6D4C41.toInt()
            c.drawRoundRect(-90f, -22f, -76f, -4f, 3f, 3f, fill)
            c.drawRoundRect(-74f, -22f, -60f, -4f, 3f, 3f, fill)
        }
    }

    private fun camo(c: Canvas) = inBody(c) {
        fill.color = acc
        c.drawOval(-80f, -30f, -40f, -10f, fill)
        c.drawOval(-20f, -60f, 20f, -36f, fill)
        c.drawOval(30f, -26f, 70f, -6f, fill)
        fill.color = 0x993E2723.toInt()
        c.drawOval(-50f, -14f, -14f, 4f, fill)
        c.drawOval(0f, -36f, 40f, -22f, fill)
    }

    private fun tracks(c: Canvas, k: Int, rot: Float, drop: Float) {
        c.save()
        c.translate(0f, drop * 0.5f)
        fill.color = 0xFF1C1C1C.toInt()
        c.drawRoundRect(-98f, 4f, 104f, 56f, 26f, 26f, fill)
        // links running round the belt
        fill.color = 0xFF3A3A3A.toInt()
        val step = 14f
        val off = ((rot * Car.WHEEL_R) % step + step) % step
        var x = -84f + off
        var xb = -84f + (step - off)
        while (x < 92f) {
            c.drawRect(x, 4f, x + 7f, 9f, fill)
            c.drawRect(xb, 51f, xb + 7f, 56f, fill)
            x += step
            xb += step
        }
        val road = if (wreck) 0xFF3A3A3A.toInt() else darker(bodyCol, 0.85f)
        for ((wx, wr) in listOf(-74f to 21f, -26f to 17f, 22f to 17f, 76f to 21f)) {
            fill.color = 0xFF111111.toInt()
            c.drawCircle(wx, Car.WHEEL_Y, wr, fill)
            fill.color = road
            c.drawCircle(wx, Car.WHEEL_Y, wr - 5f, fill)
            fill.color = 0x44FFFFFF
            c.drawCircle(wx - 2f, Car.WHEEL_Y - 2f, (wr - 5f) * 0.5f, fill)
            fill.color = 0xFF424242.toInt()
            for (i in 0 until 4) {
                val a = rot + i * 1.5708f
                c.drawCircle(wx + cos(a) * (wr - 10f), Car.WHEEL_Y + sin(a) * (wr - 10f), 2f, fill)
            }
        }
        c.restore()
        if (k == 4) {
            spike(c, 100f, 14f, 18f, 0f, 5f)
            spike(c, 100f, 34f, 18f, 0f, 5f)
        }
    }

    private fun skirts(c: Canvas, k: Int) {
        body.reset()
        body.moveTo(-96f, 2f)
        body.lineTo(100f, 2f)
        body.lineTo(98f, 24f)
        body.lineTo(-94f, 24f)
        body.close()
        paintBody(c, darker(bodyCol, 0.85f))
        rivets(c, -86f, 90f, 13f, 12)
        if (k == 4) inBody(c) {
            fill.color = acc
            c.drawRect(-100f, 16f, 110f, 20f, fill)
        }
    }

    // ----------------------------------------------------------- weapons

    /** Weapon on its mount (or the knife blades on the bumper). */
    private fun mounted(c: Canvas, v: Vehicle, w: WeaponType, rel: Float, slash: Float, frontX: Float, frontY: Float) {
        if (wreck) return
        if (w == WeaponType.KNIFE) {
            blades(c, frontX, frontY, slash)
            return
        }
        val mx = v.cls.mountX
        val my = v.cls.mountY
        // post down to the body
        stroke.color = 0xFF37474F.toInt()
        stroke.strokeWidth = 6f
        val base = when (v.cls) {
            VehicleClass.MOTO -> -12f
            VehicleClass.PICKUP -> -14f
            else -> my + 8f
        }
        if (base > my + 8f) c.drawLine(mx, base, mx, my + 6f, stroke)
        c.save()
        c.translate(mx, my)
        fill.color = 0xFF424242.toInt()
        c.drawRoundRect(-14f, 0f, 14f, 10f, 3f, 3f, fill)
        c.rotate(deg(rel))
        weaponArt(c, w)
        c.restore()
    }

    private fun blades(c: Canvas, x: Float, y: Float, slash: Float) {
        val swing = if (slash > 0f) sin((0.2f - slash) / 0.2f * PI.toFloat()) * 0.7f else 0f
        for (s in floatArrayOf(-1f, 1f)) {
            c.save()
            c.translate(x, y + s * 8f)
            c.rotate(deg(s * (0.25f + swing)))
            fill.color = 0xFF424242.toInt()
            c.drawRect(-6f, -4f, 8f, 4f, fill)
            blade(c, 6f, 0f, s, 56f)
            c.restore()
        }
    }

    private fun blade(c: Canvas, x: Float, y: Float, s: Float, len: Float) {
        path.reset()
        path.moveTo(x, y - 5f * s)
        path.quadTo(x + len * 0.55f, y - 18f * s, x + len, y - 2f * s)
        path.lineTo(x + len * 0.92f, y + 4f * s)
        path.quadTo(x + len * 0.5f, y - 4f * s, x, y + 5f * s)
        path.close()
        fill.color = 0xFFCFD8DC.toInt()
        c.drawPath(path, fill)
        stroke.color = Color.WHITE
        stroke.strokeWidth = 2f
        c.drawLine(x + len * 0.2f, y - 8f * s, x + len * 0.9f, y - 4f * s, stroke)
        stroke.color = 0xFF78909C.toInt()
        stroke.strokeWidth = 1.5f
        c.drawPath(path, stroke)
    }

    /** Weapon pointing along +x from its pivot; also used for garage icons. */
    fun weaponArt(c: Canvas, w: WeaponType) {
        when (w) {
            WeaponType.PISTOL -> {
                fill.color = 0xFF5D4037.toInt()
                c.drawRoundRect(-8f, -4f, 4f, 12f, 3f, 3f, fill)
                fill.color = 0xFF455A64.toInt()
                c.drawRoundRect(-12f, -10f, 30f, -1f, 3f, 3f, fill)
                fill.color = 0x44FFFFFF
                c.drawRect(-10f, -9f, 28f, -6f, fill)
                fill.color = 0xFF212121.toInt()
                c.drawRect(28f, -8f, 36f, -3f, fill)
            }
            WeaponType.KNIFE -> {
                fill.color = 0xFF5D4037.toInt()
                c.drawRoundRect(-26f, -5f, -2f, 5f, 4f, 4f, fill)
                fill.color = 0xFF9E9E9E.toInt()
                c.drawRect(-4f, -9f, 2f, 9f, fill)
                blade(c, 2f, 0f, 1f, 44f)
            }
            WeaponType.SMG -> {
                fill.color = 0xFF37474F.toInt()
                c.drawRoundRect(-14f, -10f, 16f, 8f, 4f, 4f, fill)
                fill.color = 0x44FFFFFF
                c.drawRect(-12f, -9f, 14f, -5f, fill)
                fill.color = 0xFF212121.toInt()
                c.drawRect(14f, -6f, 58f, 2f, fill)
                c.drawRect(-2f, 8f, 6f, 20f, fill)
            }
            WeaponType.MINIGUN -> {
                fill.color = 0xFF37474F.toInt()
                c.drawRoundRect(-18f, -12f, 14f, 12f, 5f, 5f, fill)
                fill.color = 0xFFFFC107.toInt()
                c.drawRect(-14f, 10f, -4f, 20f, fill)
                val spin = time * 30f
                for (i in 0 until 3) {
                    val yy = sin(spin + i * 2.094f) * 5f - 1f
                    fill.color = if (cos(spin + i * 2.094f) > 0f) 0xFF616161.toInt() else 0xFF212121.toInt()
                    c.drawRect(14f, yy - 2f, 66f, yy + 2f, fill)
                }
                fill.color = 0xFF424242.toInt()
                c.drawRect(40f, -8f, 46f, 7f, fill)
                c.drawRect(62f, -8f, 68f, 7f, fill)
            }
            WeaponType.SHOTGUN -> {
                fill.color = 0xFF6D4C41.toInt()
                path.reset()
                path.moveTo(-22f, 0f); path.lineTo(2f, -6f); path.lineTo(2f, 6f); path.lineTo(-20f, 10f); path.close()
                c.drawPath(path, fill)
                fill.color = 0xFF455A64.toInt()
                c.drawRect(0f, -9f, 22f, 6f, fill)
                fill.color = 0xFF263238.toInt()
                c.drawRect(20f, -8f, 62f, -2f, fill)
                c.drawRect(20f, -1f, 62f, 5f, fill)
                fill.color = 0x44FFFFFF
                c.drawRect(20f, -8f, 62f, -6f, fill)
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
                fill.color = 0xFFB0BEC5.toInt()
                c.drawRect(18f, -3f, 52f, 1f, fill)
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
            WeaponType.FLAMER -> {
                fill.color = 0xFFC62828.toInt()
                c.drawRoundRect(-22f, -12f, 4f, 10f, 9f, 9f, fill)
                fill.color = 0x44FFFFFF
                c.drawRoundRect(-20f, -10f, 2f, -4f, 4f, 4f, fill)
                fill.color = 0xFF455A64.toInt()
                c.drawRect(2f, -5f, 44f, 3f, fill)
                fill.color = 0xFF263238.toInt()
                c.drawRoundRect(40f, -7f, 52f, 5f, 3f, 3f, fill)
                val f = 4f + sin(time * 30f) * 1.5f
                fill.color = 0xFF29B6F6.toInt()
                c.drawCircle(55f, -1f, f, fill)
            }
            WeaponType.ROCKET -> {
                fill.color = 0xFF4E5B31.toInt()
                c.drawRoundRect(-20f, -16f, 34f, 12f, 4f, 4f, fill)
                fill.color = 0x33FFFFFF
                c.drawRect(-18f, -14f, 32f, -8f, fill)
                fill.color = 0xFF212121.toInt()
                c.drawRect(30f, -14f, 36f, 10f, fill)
                fill.color = 0xFFE53935.toInt()
                for (row in 0..1) for (col in 0..1) c.drawCircle(38f, -8f + row * 12f + col * 0f, 4.5f, fill)
                fill.color = 0xFFFFEB3B.toInt()
                c.drawRect(-6f, -4f, 18f, 0f, fill)
            }
            WeaponType.LASER -> {
                fill.color = 0xFFECEFF1.toInt()
                c.drawRoundRect(-16f, -10f, 28f, 8f, 8f, 8f, fill)
                fill.color = 0xFF00B8D4.toInt()
                c.drawRect(-10f, -3f, 22f, 1f, fill)
                fill.color = 0xFF455A64.toInt()
                c.drawRect(26f, -6f, 40f, 4f, fill)
                val pulse = 0.6f + 0.4f * sin(time * 10f)
                fill.color = Color.argb((120 * pulse).toInt(), 0, 229, 255)
                c.drawCircle(42f, -1f, 10f, fill)
                fill.color = 0xFFE0F7FA.toInt()
                c.drawCircle(42f, -1f, 4f, fill)
            }
        }
    }
}
