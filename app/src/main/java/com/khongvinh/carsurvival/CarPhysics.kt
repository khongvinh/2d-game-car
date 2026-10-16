package com.khongvinh.carsurvival

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Rigid-body car on a height-field: two spring wheels with traction limited by
 * friction, lean control, rocket boost, and body/roof contact with the ground.
 * Pure Kotlin (no Android types) so it can be exercised off-device.
 */
class CarPhysics {
    companion object {
        const val G = 900f
        const val SPRING = 120f
        const val DAMP = 10f
        const val INERTIA = 2600f
        const val BOOST_ACC = 1300f
        const val BRAKE_ACC = 700f
    }

    private val nrm = FloatArray(2)

    private val bodyPts = arrayOf(
        floatArrayOf(-Car.HALF_LEN, -Car.HALF_H),
        floatArrayOf(Car.HALF_LEN * 0.55f, -Car.HALF_H - 12f),
        floatArrayOf(-Car.HALF_LEN * 0.2f, -Car.HALF_H - 14f),
        floatArrayOf(-Car.HALF_LEN, Car.HALF_H * 0.5f),
        floatArrayOf(Car.HALF_LEN, Car.HALF_H * 0.4f),
    )

    /** Advances the car by [h] seconds. Returns true on a hard landing. */
    fun step(
        t: Terrain,
        c: Car,
        h: Float,
        gas: Boolean,
        brake: Boolean,
        lean: Float,
        topSpeed: Float,
        engineAcc: Float,
        mu: Float,
    ): Boolean {
        val cs = cos(c.angle)
        val sn = sin(c.angle)
        var fx = 0f
        var fy = G
        var torque = 0f
        var anyGround = false
        var landed = false

        for (i in 0..1) {
            val lx = Car.WHEEL_X[i]
            val ly = Car.WHEEL_Y
            val rx = lx * cs - ly * sn
            val ry = lx * sn + ly * cs
            val wx = c.x + rx
            val wy = c.y + ry
            val gy = t.heightAt(wx)
            t.normal(wx, nrm)
            val nx = nrm[0]
            val ny = nrm[1]
            val dist = (gy - wy) * (-ny)
            val pen = Car.WHEEL_R - dist
            c.grounded[i] = pen > 0f
            c.compress[i] = max(0f, pen)
            if (pen <= 0f) continue
            anyGround = true
            val pvx = c.vx - c.spin * ry
            val pvy = c.vy + c.spin * rx
            val vn = pvx * nx + pvy * ny
            val fn = max(0f, SPRING * pen - DAMP * vn)
            val tx = -ny
            val ty = nx
            val vt = pvx * tx + pvy * ty
            var ft = 0f
            if (gas) ft += engineAcc * 0.5f * max(0f, 1f - vt / topSpeed)
            if (brake) {
                ft += if (vt > 60f) -BRAKE_ACC * 0.5f else -engineAcc * 0.25f * max(0f, 1f + vt / 260f)
            }
            if (!gas && !brake) ft -= vt * 0.25f
            if (abs(vt) < 40f && !gas && !brake) ft -= vt * 6f
            val maxF = mu * fn
            ft = ft.coerceIn(-maxF, maxF)
            val fxw = fn * nx + ft * tx
            val fyw = fn * ny + ft * ty
            fx += fxw
            fy += fyw
            // Only part of the traction torque pitches the body, so a strong engine
            // does not flip the car over backwards on every take-off.
            val nFx = fn * nx
            val nFy = fn * ny
            torque += rx * nFy - ry * nFx + (rx * (ft * ty) - ry * (ft * tx)) * 0.35f
            // hard stop when the suspension bottoms out (big landings)
            val limit = Car.WHEEL_R * 0.7f
            if (pen > limit) {
                val push = pen - limit
                c.x += nx * push
                c.y += ny * push
                if (vn < 0f) {
                    if (vn < -650f) landed = true
                    c.vx -= vn * nx * 0.9f
                    c.vy -= vn * ny * 0.9f
                }
            }
        }

        // lean control (stronger in the air)
        c.spin += lean * (if (anyGround) 2.5f else 6.5f) * h
        if (!anyGround && lean == 0f) {
            // gentle self-levelling in the air so casual players land on their wheels
            var a = c.angle % (2f * PI.toFloat())
            if (a > PI.toFloat()) a -= 2f * PI.toFloat()
            if (a < -PI.toFloat()) a += 2f * PI.toFloat()
            c.spin -= a * 2.2f * h
        }
        c.spin *= (1f - (if (anyGround) 1.5f else 0.6f) * h)

        if (c.boosting) {
            fx += cs * BOOST_ACC
            fy += sn * BOOST_ACC
        }

        c.vx += fx * h
        c.vy += fy * h
        c.spin += torque / INERTIA * h
        c.spin = c.spin.coerceIn(-8f, 8f)
        c.vx *= (1f - 0.03f * h)
        val sp = hypot(c.vx, c.vy)
        val cap = topSpeed * (if (c.boosting) 1.7f else 1.35f)
        if (sp > cap) {
            c.vx *= cap / sp
            c.vy *= cap / sp
        }
        c.x += c.vx * h
        c.y += c.vy * h
        c.angle += c.spin * h

        // body vs ground (roof, bumpers)
        val cs2 = cos(c.angle)
        val sn2 = sin(c.angle)
        for (p in bodyPts) {
            val rx = p[0] * cs2 - p[1] * sn2
            val ry = p[0] * sn2 + p[1] * cs2
            val gy = t.heightAt(c.x + rx)
            val pen = c.y + ry - gy
            if (pen > 0f) {
                c.y -= pen
                val pvy = c.vy + c.spin * rx
                if (pvy > 0f) c.vy -= pvy * 0.7f
                c.spin *= (1f - 6f * h)
                c.vx *= (1f - 2f * h)
            }
        }
        return landed
    }
}
