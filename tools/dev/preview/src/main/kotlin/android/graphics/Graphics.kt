package android.graphics

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

object Color {
    const val WHITE = -0x1
    const val BLACK = -0x1000000
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a and 255 shl 24) or (r and 255 shl 16) or (g and 255 shl 8) or (b and 255)
}

open class Shader { enum class TileMode { CLAMP } }
class LinearGradient(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val colors: IntArray, val pos: FloatArray?, @Suppress("UNUSED_PARAMETER") m: Shader.TileMode) : Shader() {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, m: Shader.TileMode) : this(x0, y0, x1, y1, intArrayOf(c0, c1), null, m)
}
class RadialGradient(val cx: Float, val cy: Float, val r: Float, val colors: IntArray, val pos: FloatArray?, @Suppress("UNUSED_PARAMETER") m: Shader.TileMode) : Shader()

class Typeface private constructor(val bold: Boolean) { companion object { val DEFAULT_BOLD = Typeface(true) } }

class Paint(@Suppress("UNUSED_PARAMETER") flags: Int = 0) {
    enum class Style { FILL, STROKE }
    enum class Align { LEFT, CENTER, RIGHT }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    companion object { const val ANTI_ALIAS_FLAG = 1 }
    var color: Int = Color.BLACK
    var alpha: Int
        get() = color ushr 24
        set(v) { color = (color and 0xFFFFFF) or ((v and 255) shl 24) }
    var style = Style.FILL
    var isFilterBitmap = false
    var strokeWidth = 1f
    var strokeCap = Cap.BUTT
    var strokeJoin = Join.MITER
    var textSize = 12f
    var textAlign = Align.LEFT
    var typeface: Typeface? = null
    var shader: Shader? = null
    fun font() = Font("DejaVu Sans", Font.BOLD, 1).deriveFont(textSize)
    fun measureText(s: String): Float {
        val img = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        val w = g.getFontMetrics(font()).getStringBounds(s, g).width.toFloat()
        g.dispose()
        return w
    }
}

class Bitmap(val img: java.awt.image.BufferedImage) {
    val width get() = img.width
    val height get() = img.height
    fun getPixels(px: IntArray, off: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        img.getRGB(x, y, w, h, px, off, stride)
    }

    companion object {
        fun createBitmap(b: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap {
            val o = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            o.createGraphics().apply { drawImage(b.img.getSubimage(x, y, w, h), 0, 0, null); dispose() }
            return Bitmap(o)
        }
    }
}

object BitmapFactory {
    fun decodeStream(s: java.io.InputStream): Bitmap? = javax.imageio.ImageIO.read(s)?.let { Bitmap(it) }
}

class Rect(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0)

class RectF {
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun width() = right - left
    fun height() = bottom - top
}

class Path {
    enum class Direction { CW, CCW }
    val p = Path2D.Float()
    fun addRoundRect(r: RectF, rx: Float, ry: Float, @Suppress("UNUSED_PARAMETER") d: Direction) = p.append(RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), false)
    fun reset() = p.reset()
    fun moveTo(x: Float, y: Float) = p.moveTo(x, y)
    fun lineTo(x: Float, y: Float) { if (p.currentPoint == null) p.moveTo(x, y) else p.lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) = p.quadTo(x1, y1, x2, y2)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = p.curveTo(x1, y1, x2, y2, x3, y3)
    fun close() = p.closePath()
    fun offset(dx: Float, dy: Float) = p.transform(AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble()))
}

class Canvas(private val g: Graphics2D) {
    private val stack = ArrayList<Triple<AffineTransform, Float, java.awt.Shape?>>()
    private var layerAlpha = 1f

    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    fun save(): Int { stack.add(Triple(g.transform, layerAlpha, g.clip)); return stack.size }
    fun clipPath(p: Path) = g.clip(p.p)
    fun clipRect(l: Float, t: Float, r: Float, b: Float) = g.clip(Rectangle2D.Float(l, t, r - l, b - t))
    fun saveLayerAlpha(@Suppress("UNUSED_PARAMETER") b: RectF?, a: Int): Int { val n = save(); layerAlpha *= a / 255f; return n }
    fun restore() { val (t, a, cl) = stack.removeAt(stack.size - 1); g.transform = t; g.clip = cl; layerAlpha = a }
    fun translate(x: Float, y: Float) = g.translate(x.toDouble(), y.toDouble())
    fun scale(x: Float, y: Float) = g.scale(x.toDouble(), y.toDouble())
    fun rotate(deg: Float) = g.rotate(Math.toRadians(deg.toDouble()))

    private fun apply(p: Paint) {
        val sh = p.shader
        var extra = 1f
        fun fr(n: Int, pos: FloatArray?) = pos?.copyOf() ?: FloatArray(n) { it / (n - 1f) }
        fun cols(a: IntArray) = Array(a.size) { java.awt.Color(a[it], true) }
        if (sh is LinearGradient) {
            val y1 = if (sh.y1 == sh.y0) sh.y0 + 0.01f else sh.y1
            g.paint = java.awt.LinearGradientPaint(sh.x0, sh.y0, sh.x1, y1, fr(sh.colors.size, sh.pos), cols(sh.colors))
            extra = p.alpha / 255f
        } else if (sh is RadialGradient) {
            g.paint = java.awt.RadialGradientPaint(sh.cx, sh.cy, sh.r, fr(sh.colors.size, sh.pos), cols(sh.colors))
            extra = p.alpha / 255f
        } else {
            g.color = java.awt.Color(p.color, true)
        }
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (layerAlpha * extra).coerceIn(0f, 1f))
        val cap = when (p.strokeCap) { Paint.Cap.ROUND -> BasicStroke.CAP_ROUND; Paint.Cap.SQUARE -> BasicStroke.CAP_SQUARE; else -> BasicStroke.CAP_BUTT }
        val join = when (p.strokeJoin) { Paint.Join.ROUND -> BasicStroke.JOIN_ROUND; Paint.Join.BEVEL -> BasicStroke.JOIN_BEVEL; else -> BasicStroke.JOIN_MITER }
        g.stroke = BasicStroke(p.strokeWidth, cap, join)
    }

    private fun draw(s: java.awt.Shape, p: Paint) { apply(p); if (p.style == Paint.Style.STROKE) g.draw(s) else g.fill(s) }

    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) = draw(Rectangle2D.Float(minOf(l, r), minOf(t, b), Math.abs(r - l), Math.abs(b - t)), p)
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) = draw(RoundRectangle2D.Float(l, t, r - l, b - t, rx * 2, ry * 2), p)
    fun drawRoundRect(rc: RectF, rx: Float, ry: Float, p: Paint) = drawRoundRect(rc.left, rc.top, rc.right, rc.bottom, rx, ry, p)
    fun drawBitmap(b: Bitmap, x: Float, y: Float, p: Paint?) {
        val d = RectF()
        d.set(x, y, x + b.width, y + b.height)
        drawBitmap(b, null, d, p)
    }

    fun drawBitmap(b: Bitmap, @Suppress("UNUSED_PARAMETER") src: Rect?, dst: RectF, p: Paint?) {
        val old = g.composite
        val a = ((p?.alpha ?: 255) / 255f) * layerAlpha
        if (a < 1f) g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a.coerceIn(0f, 1f))
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val t = g.transform
        g.translate(dst.left.toDouble(), dst.top.toDouble())
        g.scale(((dst.right - dst.left) / b.width).toDouble(), ((dst.bottom - dst.top) / b.height).toDouble())
        g.drawImage(b.img, 0, 0, null)
        g.transform = t
        g.composite = old
    }
    fun drawCircle(x: Float, y: Float, r: Float, p: Paint) = draw(Ellipse2D.Float(x - r, y - r, r * 2, r * 2), p)
    fun drawOval(l: Float, t: Float, r: Float, b: Float, p: Paint) = draw(Ellipse2D.Float(l, t, r - l, b - t), p)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) { apply(p); g.draw(Line2D.Float(x0, y0, x1, y1)) }
    fun drawPath(path: Path, p: Paint) = draw(path.p, p)
    fun drawArc(rc: RectF, start: Float, sweep: Float, @Suppress("UNUSED_PARAMETER") center: Boolean, p: Paint) =
        draw(Arc2D.Float(rc.left, rc.top, rc.width(), rc.height(), -start, -sweep, Arc2D.OPEN), p)
    fun drawText(s: String, x: Float, y: Float, p: Paint) {
        apply(p)
        g.font = p.font()
        val w = p.measureText(s)
        val dx = when (p.textAlign) { Paint.Align.CENTER -> -w / 2; Paint.Align.RIGHT -> -w; else -> 0f }
        if (p.style == Paint.Style.STROKE) {
            val gv = g.font.createGlyphVector(g.fontRenderContext, s)
            val outline = gv.getOutline(x + dx, y)
            g.draw(outline)
        } else {
            g.drawString(s, x + dx, y)
        }
    }
}
