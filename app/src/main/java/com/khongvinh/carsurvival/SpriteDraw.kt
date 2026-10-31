package com.khongvinh.carsurvival

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

/** Draws sprite [name] standing on ([cx], [bottom]) [h] tall; false if there is no such picture. */
internal fun Renderer.sprite(
    c: Canvas,
    name: String?,
    cx: Float,
    bottom: Float,
    h: Float,
    flip: Boolean = false,
    alpha: Int = 255,
    rot: Float = 0f,
    squash: Float = 1f,
): Boolean {
    val b = Sprites.get(name ?: return false) ?: return false
    val w = h * b.width / b.height
    c.save()
    c.translate(cx, bottom)
    // simple motion for still pictures: tilt about the feet, squash and stretch
    if (rot != 0f) c.rotate(rot)
    if (squash != 1f) c.scale(2f - squash, squash)
    if (flip) c.scale(-1f, 1f)
    r.set(-w / 2, -h, w / 2, 0f)
    bmpPaint.alpha = alpha
    c.drawBitmap(b, null, r, bmpPaint)
    c.restore()
    return true
}

/** Draws sprite [name] fitted (keeping its shape) and centred in the box l,t,r,b. */
internal fun Renderer.spriteBox(c: Canvas, name: String?, l: Float, t: Float, rr: Float, b: Float): Boolean {
    val bm = Sprites.get(name ?: return false) ?: return false
    val s = minOf((rr - l) / bm.width, (b - t) / bm.height)
    val w = bm.width * s
    val h = bm.height * s
    val x = (l + rr) / 2
    return sprite(c, name, x, (t + b) / 2 + h / 2, h).also { if (w <= 0f) return false }
}

/** Fills the screen with background [name] (cropped to cover); false if missing. */
internal fun Renderer.backdrop(c: Canvas, name: String?): Boolean {
    val bm = Sprites.get(name ?: return false) ?: return false
    val s = maxOf(g.W / bm.width, g.H / bm.height)
    val w = bm.width * s
    val h = bm.height * s
    r.set((g.W - w) / 2, (g.H - h) / 2, (g.W + w) / 2, (g.H + h) / 2)
    bmpPaint.alpha = 255
    c.drawBitmap(bm, null, r, bmpPaint)
    return true
}

/**
 * Draws [name] walking: legs swing about the hip by [stride] degrees at [phase],
 * the body bobs and leans. Standing ([stride] 0) just breathes. False if missing.
 */
internal fun Renderer.walkSprite(
    c: Canvas,
    name: String?,
    cx: Float,
    bottom: Float,
    h: Float,
    flip: Boolean,
    phase: Float,
    stride: Float,
    alpha: Int = 255,
    lean: Float = 0f,
): Boolean {
    if (name == null) return false
    // real drawn frames win over the cut-out legs
    val n = Sprites.frames(name)
    if (n > 0 && stride > 0f) {
        val k = ((phase / (2f * kotlin.math.PI.toFloat()) * n).toInt() % n + n) % n
        return sprite(c, "${name}_$k", cx, bottom, h, flip, alpha, lean * 0.5f)
    }
    val rig = Rig.of(name) ?: return sprite(c, name, cx, bottom, h, flip, alpha, lean)
    val s = h / rig.h
    val sw = kotlin.math.sin(phase)
    val lift = kotlin.math.abs(sw)
    c.save()
    c.translate(cx, bottom)
    if (flip) c.scale(-1f, 1f)
    c.scale(s, s)
    c.translate(-rig.w / 2f, -rig.h.toFloat())
    bmpPaint.alpha = alpha
    val hip = rig.hip.toFloat()
    // the body rises a little as a leg passes under it
    val bob = if (stride > 0f) -lift * rig.h * 0.025f else kotlin.math.sin(phase * 0.5f) * rig.h * 0.008f
    fun leg(b: Bitmap, x0: Float, pivot: Float, a: Float, up: Float) {
        c.save()
        c.translate(x0 + pivot, hip + bob * 0.4f - up)
        c.rotate(a)
        c.drawBitmap(b, -pivot, 0f, bmpPaint)
        c.restore()
    }
    // back leg first, the leg moving forward is lifted
    leg(rig.legA, 0f, rig.aX.toFloat(), stride * sw, if (sw > 0f) sw * rig.h * 0.02f * (stride / 30f) else 0f)
    leg(rig.legB, (rig.w - rig.legB.width).toFloat(), rig.bX.toFloat(), -stride * sw, if (sw < 0f) -sw * rig.h * 0.02f * (stride / 30f) else 0f)
    c.save()
    c.translate(rig.w / 2f, hip + bob)
    c.rotate(lean + (if (stride > 0f) sw * stride * 0.08f else 0f))
    c.translate(-rig.w / 2f, -hip)
    c.drawBitmap(rig.body, 0f, 0f, bmpPaint)
    c.restore()
    c.restore()
    return true
}

/** Stretches sprite [name] over the box l,t,r,b (ledges, beams); false if missing. */
internal fun Renderer.spriteFill(c: Canvas, name: String?, l: Float, t: Float, rr: Float, b: Float): Boolean {
    val bm = Sprites.get(name ?: return false) ?: return false
    r.set(l, t, rr, b)
    bmpPaint.alpha = 255
    c.drawBitmap(bm, null, r, bmpPaint)
    return true
}

/** Effect pictures centred on their spot, growing then fading out. */
internal fun Renderer.drawFx(c: Canvas) {
    for (f in g.fxs) {
        val t = 1f - (f.life / f.maxLife).coerceIn(0f, 1f)
        val s = f.size * (1f + f.grow * t)
        val a = (255 * (if (t < 0.5f) 1f else (1f - t) * 2f)).toInt().coerceIn(0, 255)
        c.save()
        c.translate(f.x, f.y)
        c.rotate(f.rot)
        sprite(c, f.name, 0f, s / 2, s, alpha = a)
        c.restore()
    }
}

/** Flames licking a burning monster. */
internal fun Renderer.burning(c: Canvas, x: Float, y: Float, h: Float): Boolean {
    val k = 1f + 0.12f * kotlin.math.sin(g.anim * 18f + x)
    return sprite(c, "fx_fire", x, y, h * 0.8f * k, alpha = 220, squash = k)
}
