package com.khongvinh.carsurvival

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
