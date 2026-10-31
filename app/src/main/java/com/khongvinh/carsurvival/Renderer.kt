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
class Renderer(internal val g: Game) {
    internal val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    internal val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    internal val path = Path()
    internal val r = RectF()
    internal val art = CarArt()

    fun render(c: Canvas) {
        art.time = g.anim
        val inn = g.interior
        val run = g.rooms
        if (g.state == State.CAMP || g.state == State.SIEGE) {
            drawCamp(c)
            return
        }
        if (g.state == State.WORKSHOP) {
            drawWorkshop(c)
            return
        }
        if (run != null && g.state != State.MENU && g.state != State.MAPS && g.state != State.GARAGE && g.state != State.STORY) {
            drawRooms(c, run)
        } else if (inn != null && g.state != State.MENU && g.state != State.MAPS && g.state != State.GARAGE && g.state != State.STORY) {
            drawInterior(c, inn)
        } else if (g.state == State.MENU && backdrop(c, "bg_menu_island")) {
            // menu: AI landscape behind the floating island
        } else {
            drawBackground(c)
            drawWorld(c)
            drawAtmosphere(c)
            fill.alpha = 255 // a paint's alpha also fades its shader
            fill.shader = vignette
            c.drawRect(0f, 0f, g.W, g.H, fill)
            fill.shader = null
        }
        when (g.state) {
            State.MENU -> drawMenu(c)
            State.MAPS -> drawMaps(c)
            State.GARAGE -> drawGarage(c)
            State.STORY -> drawStory(c)
            State.LAYOUT -> drawLayout(c)
            State.SOUND -> drawSound(c)
            State.MISSIONS -> drawMissions(c)
            State.PICK -> g.rooms?.let { drawRoomHud(c, it); drawPick(c, it) }
            State.PLAYING, State.PAUSED -> {
                drawDamageOverlay(c)
                drawHud(c)
                drawOverlay(c)
            }
            else -> drawOverlay(c)
        }
    }

    // ------------------------------------------------------------ backdrop

    internal fun drawBackground(c: Canvas) {
        ensureShaders()
        val th = g.theme
        val W = g.W
        val H = g.H
        // desert road: the AI picture replaces the painted sky
        if (!(th.region == Region.DESERT && backdrop(c, "bg_desert_road"))) {
            fill.alpha = 255 // a paint's alpha also fades its shader
            fill.shader = skyShader
            c.drawRect(0f, 0f, W, H, fill)
            if (th.stars) {
                for (k in 0 until 70) {
                    val h = hash(k * 97 + th.stage)
                    val sx = (h % 1600) / 1600f * W
                    val sy = ((h / 1600) % 400) / 400f * H * 0.55f
                    val tw = 0.5f + 0.5f * sin(g.anim * (1f + k % 3) + k)
                    fill.color = Color.argb((90 + 150 * tw).toInt(), 255, 255, 240)
                    c.drawCircle(sx, sy, 1.2f + (k % 3) * 0.7f, fill)
                }
            }
            fill.alpha = 255 // a paint's alpha also fades its shader
            fill.shader = sunShader
            c.drawRect(0f, 0f, W, H * 0.8f, fill)
            fill.shader = null
            val sunX = W * 0.78f
            val sunY = H * th.sunY
            fill.color = th.sunColor
            c.drawCircle(sunX, sunY, if (th.moon) 46f else 54f, fill)
            if (th.moon) {
                fill.color = 0x22000000
                c.drawCircle(sunX - 14f, sunY - 8f, 10f, fill)
                c.drawCircle(sunX + 12f, sunY + 14f, 7f, fill)
                c.drawCircle(sunX + 6f, sunY - 18f, 5f, fill)
            }
            if (th.region == Region.ALIEN) {
                // a ringed gas giant and a second moon
                fill.color = 0xFF7E57C2.toInt()
                c.drawCircle(W * 0.22f, H * 0.2f, 70f, fill)
                fill.color = 0x55FFFFFF
                c.drawOval(W * 0.22f - 70f, H * 0.2f - 30f, W * 0.22f + 10f, H * 0.2f - 5f, fill)
                stroke.color = 0xAAE1BEE7.toInt()
                stroke.strokeWidth = 5f
                c.drawOval(W * 0.22f - 120f, H * 0.2f - 18f, W * 0.22f + 120f, H * 0.2f + 18f, stroke)
                fill.color = 0xFFB2EBF2.toInt()
                c.drawCircle(W * 0.6f, H * 0.12f, 18f, fill)
            }

        }
        // drifting clouds (smoke over the volcano, mist at night)
        for (k in 0 until 6) {
            val span = W + 700f
            val raw = k * 431f + g.anim * (6f + k * 2.5f) - g.camX * Game.ZOOM * 0.02f
            val cx = ((raw % span) + span) % span - 350f
            cloud(c, cx, 60f + (k * 47) % 140, 0.6f + (k % 3) * 0.25f, th.cloud)
        }
        if (th.region == Region.DESERT || th.region == Region.NIGHT) {
            stroke.color = if (th.region == Region.NIGHT) 0xAA0B0B12.toInt() else 0xAA3E2723.toInt()
            stroke.strokeWidth = 3f
            for (k in 0 until 3) {
                val a = g.anim * 0.35f + k * 2.1f
                val bx = W * 0.45f + cos(a) * (160f + k * 40f)
                val by = 150f + sin(a) * 40f + k * 20f
                val flap = sin(g.anim * (if (th.region == Region.NIGHT) 9f else 5f) + k) * 7f
                c.drawLine(bx - 16f, by - flap, bx, by, stroke)
                c.drawLine(bx, by, bx + 16f, by - flap, stroke)
            }
        }

        mesas(c, 0.05f, H * 0.6f, farShader, 1.0f + th.stage * 0.37f, 180f, th.region)
        fill.color = th.haze
        c.drawRect(0f, H * 0.45f, W, H, fill)
        when (th.skyline) {
            1 -> city(c, 0.12f, H * 0.68f)
            2 -> volcanoes(c, 0.1f, H * 0.7f)
            3 -> castleForest(c, 0.12f, H * 0.7f)
            4 -> alienSpires(c, 0.12f, H * 0.7f)
        }
        mesas(c, 0.22f, H * 0.76f, midShader, 1.9f + th.stage * 0.21f, 120f, th.region)
        // floating embers (volcano) or fireflies / spores
        if (th.region != Region.DESERT) {
            for (k in 0 until 24) {
                val h = hash(k * 31 + 7)
                val speed = 20f + (h % 40)
                val px = ((h % 1700) + g.anim * (if (th.region == Region.VOLCANO) 15f else 8f) * (k % 3 + 1)) % (W + 40f)
                val py = H - ((g.anim * speed + (h % 700)) % (H + 40f))
                fill.color = when (th.region) {
                    Region.VOLCANO -> Color.argb(180, 255, 140 + (h % 80), 40)
                    Region.NIGHT -> Color.argb((120 + 120 * sin(g.anim * 3f + k)).toInt().coerceIn(0, 255), 200, 255, 120)
                    else -> Color.argb(150, 120, 255, 230)
                }
                c.drawCircle(px, py, 2f + (k % 3), fill)
            }
        }
    }

    /** Ash and grit drifting across the screen (volcano), dust streaks (desert). */
    private fun drawAtmosphere(c: Canvas) {
        val W = g.W
        val H = g.H
        when (g.region) {
            Region.VOLCANO -> for (k in 0 until 46) {
                val h = hash(k * 53 + 5)
                val sp = 40f + (h % 70)
                val px = (((h % 2000) - g.anim * (30f + h % 40) - g.camX * Game.ZOOM * 0.3f) % (W + 60f) + W + 60f) % (W + 60f) - 30f
                val py = ((g.anim * sp + (h / 7) % 800) % (H + 40f)) - 20f
                if (k % 3 == 0) {
                    // a tumbling chunk of debris
                    fill.color = 0xCC2A2522.toInt()
                    val sz = 3f + (h % 4)
                    c.save()
                    c.translate(px, py)
                    c.rotate(g.anim * (60f + h % 120))
                    c.drawRect(-sz, -sz * 0.6f, sz, sz * 0.6f, fill)
                    c.restore()
                } else {
                    fill.color = if (k % 3 == 1) 0x88B0A8A0.toInt() else 0x55FFB74D
                    c.drawCircle(px, py, 1.4f + (h % 3) * 0.6f, fill)
                }
            }
            Region.DESERT -> {
                stroke.color = 0x22FFE0B2
                stroke.strokeWidth = 2f
                for (k in 0 until 14) {
                    val h = hash(k * 41 + 9)
                    val px = (((h % 2000) - g.anim * (220f + h % 160)) % (W + 200f) + W + 200f) % (W + 200f) - 100f
                    val py = H * 0.35f + (h / 3 % 1000) / 1000f * H * 0.6f
                    c.drawLine(px, py, px + 60f + h % 50, py - 2f, stroke)
                }
            }
            else -> {}
        }
    }

    private fun mesas(c: Canvas, par: Float, base: Float, shader: Shader?, seed: Float, amp: Float, region: Region) {
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
            h = when (region) {
                Region.DESERT -> h.coerceIn(amp * 0.15f, amp * 0.85f) // flat mesa tops
                Region.VOLCANO -> h + amp * 0.25f * kotlin.math.abs(sin(u * 3.1f)) // jagged ridges
                Region.NIGHT -> h * 0.8f + amp * 0.08f * sin(u * 9f) // rolling, wooded
                Region.ALIEN -> h + amp * 0.3f * (sin(u * 5.3f).let { it * it * it }) // spiky
            }
            val y = base - h - lift
            path.lineTo(sx, y)
            if (sx == 0f) topPath.moveTo(sx, y) else topPath.lineTo(sx, y)
            sx += 24f
        }
        path.lineTo(W, g.H)
        path.close()
        fill.alpha = 255 // a paint's alpha also fades its shader
        fill.shader = shader
        c.drawPath(path, fill)
        fill.shader = null
        stroke.color = if (region == Region.VOLCANO) 0x55FF7043 else 0x40FFF3E0
        stroke.strokeWidth = 3f
        c.drawPath(topPath, stroke)
    }

    /** Distant volcanoes with glowing lava. */

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
        drawTunnels(c, x0 - 300f, x1 + 300f)
        for (b in g.buildings) if (b.x in x0 - 200f..x1 + 200f) drawBuilding(c, b)
        drawTerrain(c)
        drawBridges(c, x0 - 200f, x1 + 200f)
        drawTunnelFront(c, x0 - 300f, x1 + 300f)
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
        g.boss?.let { b -> if (b.alive && b.x in x0 - 300f..x1 + 300f) drawBossAny(c, b) }
        for (d in g.debris) if (d.x in x0..x1) drawDebris(c, d)
        drawShots(c)
        for (rk in g.bossRocks) drawBossRock(c, rk)
        drawPlayerCar(c)
        if (g.driver.out) drawDriver(c)
        for (p in g.particles) {
            val ratio = (p.life / p.maxLife).coerceIn(0f, 1f)
            fill.color = p.color
            fill.alpha = (255 * ratio * (if (p.grow > 0f) 0.55f else 1f)).toInt().coerceIn(0, 255)
            c.drawCircle(p.x, p.y, p.size * (1f + p.grow * (1f - ratio)), fill)
        }
        fill.alpha = 255
        if (g.terrain.covered(car.x, 80f) && g.state == State.PLAYING) {
            fill.color = 0x50000000
            c.drawRect(g.camX - 400f, g.camY - 400f, g.camX + g.viewW + 400f, g.camY + g.viewH + 400f, fill)
        }
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
        if (g.state != State.MENU && g.state != State.MAPS && g.state != State.GARAGE && g.state != State.STORY && g.state != State.LAYOUT) drawStorm(c)
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
        fill.alpha = 255 // a paint's alpha also fades its shader
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
        // road surface: dirt, asphalt highway, metal deck or cobbled stone
        val th = g.theme
        when (t.style.surface) {
            1 -> {
                stroke.color = 0xFF2B2B2B.toInt()
                stroke.strokeWidth = 26f
                topPath.offset(0f, 12f)
                c.drawPath(topPath, stroke)
                stroke.color = 0xFF4E4E4E.toInt()
                stroke.strokeWidth = 5f
                topPath.offset(0f, -11f)
                c.drawPath(topPath, stroke)
                // lane markings and guardrail posts
                stroke.color = 0xFFFFD54F.toInt()
                stroke.strokeWidth = 3f
                for (i in i0..i1) {
                    if (i % 3 != 0) continue
                    val x = Terrain.START_X + i * Terrain.SEG
                    c.drawLine(x, t.point(i) + 12f, x + 24f, t.point(i) * 0.4f + t.point(i + 1) * 0.6f + 12f, stroke)
                }
            }
            2 -> {
                stroke.color = 0xFF263238.toInt()
                stroke.strokeWidth = 24f
                topPath.offset(0f, 11f)
                c.drawPath(topPath, stroke)
                stroke.color = 0xFF78909C.toInt()
                stroke.strokeWidth = 6f
                topPath.offset(0f, -9f)
                c.drawPath(topPath, stroke)
                stroke.color = 0xFF64FFDA.toInt()
                stroke.strokeWidth = 2f
                topPath.offset(0f, 5f)
                c.drawPath(topPath, stroke)
                stroke.color = 0xFF102027.toInt()
                stroke.strokeWidth = 2f
                for (i in i0..i1) {
                    if (i % 2 != 0) continue
                    val x = Terrain.START_X + i * Terrain.SEG
                    c.drawLine(x, t.point(i) + 2f, x, t.point(i) + 22f, stroke)
                }
            }
            3 -> {
                stroke.color = 0xFF2E2A27.toInt()
                stroke.strokeWidth = 22f
                topPath.offset(0f, 10f)
                c.drawPath(topPath, stroke)
                stroke.color = 0xFF6D6660.toInt()
                stroke.strokeWidth = 7f
                topPath.offset(0f, -7f)
                c.drawPath(topPath, stroke)
                fill.color = 0xFF57504A.toInt()
                for (i in i0..i1) {
                    val x = Terrain.START_X + i * Terrain.SEG
                    val y = t.point(i)
                    c.drawOval(x + 2f, y + 6f, x + 18f, y + 14f, fill)
                    c.drawOval(x + 22f, y + 8f, x + 36f, y + 16f, fill)
                }
            }
            else -> {
                stroke.color = th.crust[0]
                stroke.strokeWidth = 20f
                topPath.offset(0f, 9f)
                c.drawPath(topPath, stroke)
                stroke.color = th.crust[1]
                stroke.strokeWidth = 9f
                topPath.offset(0f, -6f)
                c.drawPath(topPath, stroke)
                stroke.color = th.crust[2]
                stroke.strokeWidth = 3f
                topPath.offset(0f, -4f)
                c.drawPath(topPath, stroke)
            }
        }
        if (t.style.surface == 1) {
            // guardrail along the highway (not inside tunnels)
            for (i in i0..i1) {
                if (i % 5 != 0) continue
                val x = Terrain.START_X + i * Terrain.SEG
                if (t.covered(x) || t.onBridge(x)) continue
                val y = t.point(i)
                stroke.color = 0xFF90A4AE.toInt()
                stroke.strokeWidth = 4f
                c.drawLine(x, y - 2f, x, y - 30f, stroke)
                stroke.strokeWidth = 5f
                c.drawLine(x - 2f, y - 26f, x + 5 * Terrain.SEG + 2f, t.point(i + 5) - 26f, stroke)
            }
        }

        // dry grass, pebbles and bushes on the surface
        if (t.style.surface != 0) return
        for (i in i0..i1) {
            val x = Terrain.START_X + i * Terrain.SEG
            if (t.covered(x) || t.onBridge(x)) continue
            val y = t.point(i) - 1f
            val h = hash(i * 7 + 3)
            when (h % 7) {
                0, 1 -> {
                    stroke.color = if (h % 2 == 0) th.grass[0] else th.grass[1]
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

    // ------------------------------------------------------- tunnels & bridges

    private val rockPath = Path()

    private fun rockColor(): Int = when (g.region) {
        Region.DESERT -> 0xFF8A6446.toInt()
        Region.VOLCANO -> 0xFF3A2E2B.toInt()
        Region.NIGHT -> 0xFF39404D.toInt()
        Region.ALIEN -> 0xFF4A2F66.toInt()
    }

    internal fun shade(col: Int, f: Float) = Color.argb(
        255, (((col shr 16) and 255) * f).toInt().coerceIn(0, 255), (((col shr 8) and 255) * f).toInt().coerceIn(0, 255), ((col and 255) * f).toInt().coerceIn(0, 255),
    )

    /** Top of the rock mass above a tunnel (caves fill the whole sky). */
    private fun rockTop(x: Float, ce: Float): Float =
        if (g.terrain.style.cave) g.camY - 400f else ce - 150f - 45f * (0.5f + 0.5f * sin(x * 0.006f)) - 30f * sin(x * 0.017f)

    /** Mountain the tunnel runs through, and the dark tunnel bore behind the road. */
    private fun drawTunnels(c: Canvas, x0: Float, x1: Float) {
        val t = g.terrain
        val rock = rockColor()
        for (tn in t.tunnels) {
            val a = tn[0]
            val b = tn[1]
            if (b + 300f < x0 || a - 300f > x1) continue
            val from = max(a - 260f, x0)
            val to = min(b + 260f, x1)
            if (to - from < 30f) continue
            val ceA = t.ceilingAt(a + 1f)
            val ceB = t.ceilingAt(b - 1f)
            // the mountain: rises from the ground before the portal, falls after it
            rockPath.reset()
            var x = from
            var first = true
            while (x <= to) {
                val ce = t.ceilingAt(x)
                val y = when {
                    x < a -> {
                        val k = ((x - (a - 260f)) / 260f).coerceIn(0f, 1f)
                        val f = k * k * (3f - 2f * k)
                        t.heightAt(x) + (rockTop(a, ceA) - t.heightAt(x)) * f
                    }
                    x > b -> {
                        val k = (((b + 260f) - x) / 260f).coerceIn(0f, 1f)
                        val f = k * k * (3f - 2f * k)
                        t.heightAt(x) + (rockTop(b, ceB) - t.heightAt(x)) * f
                    }
                    else -> rockTop(x, if (ce.isNaN()) ceA else ce)
                }
                if (first) {
                    rockPath.moveTo(x, t.heightAt(x) + 30f); rockPath.lineTo(x, y); first = false
                } else rockPath.lineTo(x, y)
                x += 30f
            }
            rockPath.lineTo(to, t.heightAt(to) + 30f)
            rockPath.close()
            fill.color = rock
            c.drawPath(rockPath, fill)
            c.save()
            c.clipPath(rockPath)
            // strata and boulders in the rock face
            for (k in 0 until 14) {
                val h = hash(k * 31 + (a.toInt() / 7))
                val bx = from + (h % 1000) / 1000f * (to - from)
                val by = t.heightAt(bx) - 120f - (h % 400)
                fill.color = if (k % 2 == 0) shade(rock, 0.8f) else shade(rock, 1.15f)
                c.drawOval(bx - 40f - (h % 30), by - 14f, bx + 40f + (h % 40), by + 14f, fill)
            }
            stroke.color = shade(rock, 0.7f)
            stroke.strokeWidth = 4f
            for (k in 0 until 4) {
                val yy = t.heightAt((a + b) / 2) - 380f - k * 70f
                c.drawLine(from, yy + sin(k * 1.7f) * 20f, to, yy + 30f + cos(k * 2.1f) * 20f, stroke)
            }
            c.restore()

            // the bore: between the road and the roof
            val ia = t.indexOf(max(a, x0 - 40f))
            val ib = t.indexOf(min(b, x1 + 40f)) + 1
            if (ib <= ia) continue
            rockPath.reset()
            for (i in ia..ib) {
                val xx = Terrain.START_X + i * Terrain.SEG
                val ce = t.ceilingPoint(i)
                val yy = if (ce.isNaN()) t.point(i) else ce
                if (i == ia) rockPath.moveTo(xx, yy) else rockPath.lineTo(xx, yy)
            }
            for (i in ib downTo ia) rockPath.lineTo(Terrain.START_X + i * Terrain.SEG, t.point(i) + 20f)
            rockPath.close()
            fill.color = shade(rock, if (t.style.cave) 0.45f else 0.32f)
            c.drawPath(rockPath, fill)
            c.save()
            c.clipPath(rockPath)
            val sfc = t.style.surface
            for (i in ia..ib) {
                val xx = Terrain.START_X + i * Terrain.SEG
                val ce = t.ceilingPoint(i)
                if (ce.isNaN()) continue
                val gy = t.point(i)
                if (!t.style.cave && i % 5 == 0) {
                    // concrete / steel ribs of a built tunnel
                    fill.color = if (sfc == 2) 0xFF2F3D44.toInt() else shade(rock, 0.45f)
                    c.drawRect(xx, ce, xx + 18f, gy, fill)
                } else if (t.style.cave && i % 3 == 0) {
                    // glowing veins in the cave wall
                    val h = hash(i * 13)
                    val col = when (g.region) {
                        Region.VOLCANO -> 0x88FF6D00.toInt()
                        Region.ALIEN -> 0x8864FFDA.toInt()
                        Region.NIGHT -> 0x5590A4AE
                        else -> 0x55FFCC80
                    }
                    stroke.color = col
                    stroke.strokeWidth = 3f
                    val my = ce + (gy - ce) * (0.25f + (h % 50) / 100f)
                    c.drawLine(xx, my, xx + 30f, my - 18f + (h % 30), stroke)
                }
            }
            c.restore()
        }
    }

    /** Portals, lamps, stalactites and crystals: drawn over the road, under the cars. */
    private fun drawTunnelFront(c: Canvas, x0: Float, x1: Float) {
        val t = g.terrain
        val rock = rockColor()
        for (tn in t.tunnels) {
            val a = tn[0]
            val b = tn[1]
            if (b + 100f < x0 || a - 100f > x1) continue
            val ia = t.indexOf(max(a, x0))
            val ib = t.indexOf(min(b, x1))
            for (i in ia..ib) {
                val xx = Terrain.START_X + i * Terrain.SEG
                val ce = t.ceilingPoint(i)
                if (ce.isNaN()) continue
                val h = hash(i * 7 + 1)
                if (t.style.cave) {
                    if (h % 3 == 0) {
                        // stalactite (or a crystal on alien worlds)
                        val len = 22f + (h % 50)
                        fill.color = when (g.region) {
                            Region.ALIEN -> 0xFF4DD0E1.toInt()
                            Region.VOLCANO -> shade(rock, 1.2f)
                            else -> shade(rock, 1.3f)
                        }
                        path.reset()
                        path.moveTo(xx - 9f, ce - 2f); path.lineTo(xx + 9f, ce - 2f); path.lineTo(xx + 1f, ce + len); path.close()
                        c.drawPath(path, fill)
                        if (g.region == Region.VOLCANO && h % 2 == 0) {
                            // dripping lava
                            val drip = ((g.anim * 120f + h) % 220f)
                            fill.color = 0xFFFF6D00.toInt()
                            c.drawCircle(xx + 1f, ce + len + drip, 3f, fill)
                        }
                        if (g.region == Region.ALIEN) {
                            fill.color = 0x3364FFDA
                            c.drawCircle(xx, ce + len * 0.5f, len * 0.7f, fill)
                        }
                    }
                } else if (i % 6 == 0) {
                    // ceiling lamp with a warm pool of light
                    fill.color = 0x22FFE082
                    path.reset()
                    path.moveTo(xx - 12f, ce + 10f); path.lineTo(xx + 12f, ce + 10f)
                    path.lineTo(xx + 90f, t.point(i)); path.lineTo(xx - 90f, t.point(i)); path.close()
                    c.drawPath(path, fill)
                    fill.color = 0xFF37474F.toInt()
                    c.drawRect(xx - 16f, ce - 2f, xx + 16f, ce + 8f, fill)
                    fill.color = 0xFFFFF59D.toInt()
                    c.drawRect(xx - 12f, ce + 6f, xx + 12f, ce + 11f, fill)
                }
            }
            // roof edge
            stroke.color = shade(rock, 0.6f)
            stroke.strokeWidth = 8f
            rockPath.reset()
            for (i in ia..ib + 1) {
                val ce = t.ceilingPoint(i)
                if (ce.isNaN()) continue
                val xx = Terrain.START_X + i * Terrain.SEG
                if (i == ia) rockPath.moveTo(xx, ce) else rockPath.lineTo(xx, ce)
            }
            c.drawPath(rockPath, stroke)
            // portals
            for (px in floatArrayOf(a, b)) {
                if (px < x0 - 100f || px > x1 + 100f) continue
                val ce = t.ceilingAt(if (px == a) a + 2f else b - 2f)
                if (ce.isNaN()) continue
                val gy = t.heightAt(px)
                val built = !t.style.cave
                fill.color = if (built) (if (t.style.surface == 2) 0xFF546E7A.toInt() else 0xFF9E9A94.toInt()) else shade(rock, 0.75f)
                val dir = if (px == a) -1f else 1f
                c.drawRect(min(px, px + dir * 36f), ce - 40f, max(px, px + dir * 36f), gy, fill)
                c.drawRect(min(px, px + dir * 36f) - 10f, ce - 52f, max(px, px + dir * 36f) + 10f, ce - 30f, fill)
                if (built) {
                    // hazard stripes on the portal beam
                    fill.color = 0xFFFFC107.toInt()
                    var sy = ce - 50f
                    while (sy < ce - 32f) {
                        c.drawRect(min(px, px + dir * 36f) - 8f, sy, max(px, px + dir * 36f) + 8f, sy + 4f, fill)
                        sy += 9f
                    }
                }
                stroke.color = 0x66000000
                stroke.strokeWidth = 3f
                c.drawLine(px, ce - 40f, px, gy, stroke)
            }
        }
    }

    /** Chasms under bridge decks, the decks themselves, posts and rails. */
    private fun drawBridges(c: Canvas, x0: Float, x1: Float) {
        val t = g.terrain
        val bottom = g.camY + g.viewH + 400f
        val kind = t.style.chasm
        for (br in t.bridges) {
            val a = br[0]
            val b = br[1]
            if (b < x0 || a > x1) continue
            val ya = t.heightAt(a)
            val yb = t.heightAt(b)
            // the gap
            path.reset()
            path.moveTo(a - 6f, ya + 14f)
            path.lineTo(a + 50f, bottom)
            path.lineTo(b - 50f, bottom)
            path.lineTo(b + 6f, yb + 14f)
            path.close()
            fill.color = when (kind) {
                1 -> 0xFF1A0A06.toInt()
                2 -> 0xFF0E1A12.toInt()
                3 -> 0xFF12051F.toInt()
                else -> 0xFF22140D.toInt()
            }
            c.drawPath(path, fill)
            c.save()
            c.clipPath(path)
            val mid = (ya + yb) / 2
            when (kind) {
                1 -> { // molten lava far below, glowing up the walls
                    val ly = mid + 220f
                    fill.color = 0x55FF5722
                    c.drawRect(a, ly - 120f, b, ly, fill)
                    fill.color = 0xFFFF6D00.toInt()
                    c.drawRect(a, ly, b, bottom, fill)
                    fill.color = 0xFFFFC107.toInt()
                    for (k in 0 until 6) {
                        val bx = a + ((hash(k + a.toInt()) % 1000) / 1000f) * (b - a)
                        val pop = (g.anim * 1.3f + k * 0.37f) % 1f
                        c.drawCircle(bx, ly + 8f - pop * 14f, 5f + pop * 6f, fill)
                    }
                }
                2 -> { // murky swamp water
                    val wy = mid + 160f
                    fill.color = 0xFF2E4A2C.toInt()
                    c.drawRect(a, wy, b, bottom, fill)
                    stroke.color = 0x6690A97A
                    stroke.strokeWidth = 3f
                    for (k in 0 until 5) {
                        val rx = a + (b - a) * (k + 0.5f) / 5f + sin(g.anim + k) * 12f
                        c.drawLine(rx - 20f, wy + 8f + k % 2 * 10f, rx + 20f, wy + 8f + k % 2 * 10f, stroke)
                    }
                }
                3 -> { // purple void with drifting stars
                    for (k in 0 until 18) {
                        val h = hash(k * 17 + a.toInt())
                        fill.color = Color.argb(150 + (h % 100), 200, 160, 255)
                        c.drawCircle(a + (h % 1000) / 1000f * (b - a), mid + 60f + (h % 500), 1.5f + (h % 3), fill)
                    }
                }
                else -> { // canyon walls falling away into shadow
                    fill.color = 0x40A1887F
                    c.drawRect(a, mid + 20f, b, mid + 90f, fill)
                    fill.color = 0x30000000
                    c.drawRect(a, mid + 140f, b, bottom, fill)
                }
            }
            c.restore()

            val energy = kind == 3
            // supports
            stroke.color = if (energy) 0xFF546E7A.toInt() else 0xFF4E342E.toInt()
            stroke.strokeWidth = 10f
            c.drawLine(a + 10f, ya + 10f, a + 60f, ya + 170f, stroke)
            c.drawLine(b - 10f, yb + 10f, b - 60f, yb + 170f, stroke)
            // deck
            if (energy) {
                stroke.color = 0x8864FFDA.toInt()
                stroke.strokeWidth = 16f
                c.drawLine(a, ya + 4f, b, yb + 4f, stroke)
                stroke.color = 0xFFE0F7FA.toInt()
                stroke.strokeWidth = 4f
                c.drawLine(a, ya, b, yb, stroke)
            } else {
                var x = a
                var k = 0
                while (x < b) {
                    val y = ya + (yb - ya) * (x - a) / (b - a)
                    fill.color = if (k % 3 == 0) 0xFF6D4C41.toInt() else if (k % 3 == 1) 0xFF795548.toInt() else 0xFF5D4037.toInt()
                    c.drawRect(x, y - 1f, x + 19f, y + 12f, fill)
                    fill.color = 0x40000000
                    c.drawRect(x, y + 8f, x + 19f, y + 12f, fill)
                    x += 22f
                    k++
                }
            }
            // posts and sagging rails
            for (px in floatArrayOf(a, b)) {
                val py = t.heightAt(px)
                fill.color = if (energy) 0xFF455A64.toInt() else 0xFF4E342E.toInt()
                c.drawRect(px - 6f, py - 70f, px + 6f, py + 4f, fill)
            }
            stroke.color = if (energy) 0xAA64FFDA.toInt() else 0xFF8D6E63.toInt()
            stroke.strokeWidth = 3f
            for (hgt in floatArrayOf(66f, 36f)) {
                path.reset()
                path.moveTo(a, ya - hgt)
                path.quadTo((a + b) / 2, mid - hgt + 34f, b, yb - hgt)
                c.drawPath(path, stroke)
            }
            if (!energy) {
                stroke.strokeWidth = 2f
                var x = a + 40f
                while (x < b - 20f) {
                    val k = (x - a) / (b - a)
                    val y = ya + (yb - ya) * k
                    val sag = 4f * k * (1f - k) * 34f
                    c.drawLine(x, y, x, y - 66f + sag, stroke)
                    x += 44f
                }
            }
        }
    }

    private fun drawDecor(c: Canvas, d: Decor) {
        when (g.theme.region) {
            Region.DESERT -> drawDesertDecor(c, d)
            Region.VOLCANO -> drawVolcanoDecor(c, d)
            Region.NIGHT -> drawNightDecor(c, d)
            Region.ALIEN -> drawAlienDecor(c, d)
        }
    }

    private fun drawVolcanoDecor(c: Canvas, d: Decor) {
        val gy = g.terrain.heightAt(d.x)
        val s = d.size
        val pulse = 0.6f + 0.4f * sin(g.anim * 3f + d.x)
        when (d.kind) {
            0, 2 -> { // lava rock with glowing cracks
                fill.color = 0xFF2B2422.toInt()
                c.drawOval(d.x - 55 * s, gy - 50 * s, d.x + 50 * s, gy + 10f, fill)
                stroke.color = Color.argb((220 * pulse).toInt(), 255, 112, 30)
                stroke.strokeWidth = 3f
                c.drawLine(d.x - 30 * s, gy - 30 * s, d.x, gy - 15 * s, stroke)
                c.drawLine(d.x, gy - 15 * s, d.x + 25 * s, gy - 35 * s, stroke)
            }
            1 -> { // charred tree
                stroke.color = 0xFF1A1414.toInt()
                stroke.strokeWidth = 10f * s
                c.drawLine(d.x, gy, d.x + 6f, gy - 130 * s, stroke)
                stroke.strokeWidth = 5f * s
                c.drawLine(d.x + 3f, gy - 80 * s, d.x - 36 * s, gy - 120 * s, stroke)
                c.drawLine(d.x + 5f, gy - 100 * s, d.x + 40 * s, gy - 140 * s, stroke)
                fill.color = Color.argb((180 * pulse).toInt(), 255, 87, 34)
                c.drawCircle(d.x + 6f, gy - 130 * s, 5f, fill)
            }
            3 -> { // lava pool
                fill.color = Color.argb(200, 255, (90 + 60 * pulse).toInt(), 20)
                c.drawOval(d.x - 70 * s, gy - 6f, d.x + 70 * s, gy + 12f, fill)
                fill.color = 0x88FFEB3B.toInt()
                c.drawOval(d.x - 30 * s, gy - 3f, d.x + 20 * s, gy + 5f, fill)
            }
            else -> { // skull on a pike
                stroke.color = 0xFF3E2723.toInt()
                stroke.strokeWidth = 6f
                c.drawLine(d.x, gy, d.x, gy - 110f, stroke)
                fill.color = 0xFFE0D6C8.toInt()
                c.drawCircle(d.x, gy - 120f, 16f, fill)
                c.drawRect(d.x - 9f, gy - 112f, d.x + 9f, gy - 100f, fill)
                fill.color = 0xFF1A1A1A.toInt()
                c.drawCircle(d.x - 6f, gy - 122f, 4f, fill)
                c.drawCircle(d.x + 6f, gy - 122f, 4f, fill)
            }
        }
    }

    private fun drawNightDecor(c: Canvas, d: Decor) {
        val gy = g.terrain.heightAt(d.x)
        val s = d.size
        when (d.kind) {
            0 -> { // tombstone
                fill.color = 0xFF616A78.toInt()
                c.drawRoundRect(d.x - 22 * s, gy - 60 * s, d.x + 22 * s, gy + 4f, 20 * s, 20 * s, fill)
                fill.color = 0xFF3E4451.toInt()
                c.drawRect(d.x - 3f, gy - 50 * s, d.x + 3f, gy - 22 * s, fill)
                c.drawRect(d.x - 12 * s, gy - 42 * s, d.x + 12 * s, gy - 36 * s, fill)
            }
            1 -> { // twisted dead tree
                stroke.color = 0xFF1B1B24.toInt()
                stroke.strokeWidth = 11f * s
                c.drawLine(d.x, gy, d.x - 10f, gy - 150 * s, stroke)
                stroke.strokeWidth = 5f * s
                c.drawLine(d.x - 6f, gy - 100 * s, d.x - 55 * s, gy - 140 * s, stroke)
                c.drawLine(d.x - 8f, gy - 120 * s, d.x + 40 * s, gy - 170 * s, stroke)
                c.drawLine(d.x - 30 * s, gy - 120 * s, d.x - 45 * s, gy - 165 * s, stroke)
            }
            2 -> { // jack-o'-lantern
                fill.color = 0xFFEF6C00.toInt()
                c.drawOval(d.x - 22f, gy - 34f, d.x + 22f, gy + 2f, fill)
                fill.color = Color.argb((160 + 90 * sin(g.anim * 6f + d.x)).toInt().coerceIn(0, 255), 255, 235, 59)
                c.drawRect(d.x - 12f, gy - 22f, d.x - 5f, gy - 15f, fill)
                c.drawRect(d.x + 5f, gy - 22f, d.x + 12f, gy - 15f, fill)
                c.drawRect(d.x - 10f, gy - 10f, d.x + 10f, gy - 6f, fill)
                fill.color = 0xFF33691E.toInt()
                c.drawRect(d.x - 3f, gy - 42f, d.x + 3f, gy - 32f, fill)
            }
            3 -> { // old street lamp
                stroke.color = 0xFF263238.toInt()
                stroke.strokeWidth = 7f
                c.drawLine(d.x, gy, d.x, gy - 200f, stroke)
                c.drawLine(d.x, gy - 200f, d.x + 30f, gy - 200f, stroke)
                val on = (g.anim * 2f + d.x).toInt() % 7 != 0
                fill.color = if (on) 0x55FFF59D else 0x11FFF59D
                c.drawCircle(d.x + 30f, gy - 190f, 40f, fill)
                fill.color = if (on) 0xFFFFF59D.toInt() else 0xFF8D8D6A.toInt()
                c.drawCircle(d.x + 30f, gy - 190f, 9f, fill)
            }
            else -> { // broken fence
                stroke.color = 0xFF4E3B30.toInt()
                stroke.strokeWidth = 6f
                for (k in 0 until 4) c.drawLine(d.x + k * 22f, gy, d.x + k * 22f + (k % 2) * 4f, gy - 50f - (k % 3) * 8f, stroke)
                c.drawLine(d.x - 4f, gy - 30f, d.x + 70f, gy - 36f, stroke)
            }
        }
    }

    private fun drawAlienDecor(c: Canvas, d: Decor) {
        val gy = g.terrain.heightAt(d.x)
        val s = d.size
        val glow = 0.6f + 0.4f * sin(g.anim * 2.5f + d.x * 0.01f)
        when (d.kind) {
            0, 2 -> { // crystal cluster
                for (k in -1..1) {
                    path.reset()
                    val h = (60f + k * k * -20f + 30f) * s
                    path.moveTo(d.x + k * 18f * s - 10f * s, gy)
                    path.lineTo(d.x + k * 26f * s, gy - h)
                    path.lineTo(d.x + k * 18f * s + 10f * s, gy)
                    path.close()
                    fill.color = Color.argb(230, 100, (200 + 55 * glow).toInt(), 230)
                    c.drawPath(path, fill)
                }
                fill.color = Color.argb((90 * glow).toInt(), 100, 255, 218)
                c.drawCircle(d.x, gy - 30f * s, 45f * s, fill)
            }
            1 -> { // alien bulb plant
                stroke.color = 0xFF4A148C.toInt()
                stroke.strokeWidth = 6f * s
                c.drawLine(d.x, gy, d.x + sin(g.anim + d.x) * 8f, gy - 110f * s, stroke)
                fill.color = Color.argb(255, (180 + 60 * glow).toInt(), 80, 220)
                c.drawCircle(d.x + sin(g.anim + d.x) * 8f, gy - 118f * s, 18f * s, fill)
                fill.color = 0x88FFFFFF.toInt()
                c.drawCircle(d.x + sin(g.anim + d.x) * 8f - 5f, gy - 124f * s, 5f * s, fill)
            }
            3 -> { // beacon antenna
                stroke.color = 0xFF90A4AE.toInt()
                stroke.strokeWidth = 5f
                c.drawLine(d.x, gy, d.x, gy - 180f, stroke)
                c.drawLine(d.x - 30f, gy, d.x, gy - 120f, stroke)
                c.drawLine(d.x + 30f, gy, d.x, gy - 120f, stroke)
                val on = (g.anim * 2f).toInt() % 2 == 0
                fill.color = if (on) 0xFFFF1744.toInt() else 0x55FF1744
                c.drawCircle(d.x, gy - 185f, 8f, fill)
            }
            else -> { // crashed saucer
                fill.color = 0xFF78909C.toInt()
                c.drawOval(d.x - 70f * s, gy - 30f * s, d.x + 70f * s, gy + 6f, fill)
                fill.color = Color.argb(200, 128, 222, 234)
                c.drawOval(d.x - 30f * s, gy - 52f * s, d.x + 30f * s, gy - 18f * s, fill)
                fill.color = Color.argb((200 * glow).toInt(), 178, 255, 89)
                for (k in -2..2) c.drawCircle(d.x + k * 24f * s, gy - 10f * s, 4f, fill)
                fill.color = 0x55424242
                c.drawCircle(d.x + 50f * s, gy - 40f * s, 18f * s, fill)
            }
        }
    }

    private fun drawDesertDecor(c: Canvas, d: Decor) {
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

    private val inGarageScreens get() = g.state == State.MENU || g.state == State.MAPS || g.state == State.GARAGE || g.state == State.STORY

    private fun drawPlayerCar(c: Canvas) {
        val car = g.car
        val v = if (inGarageScreens) g.save.vehicleType else g.vehicle
        val w = if (inGarageScreens) g.save.weaponType else g.weapon
        if (car.cloneT > 0f && g.state == State.PLAYING) {
            val blink = car.cloneT < 2f && (g.anim * 10f).toInt() % 2 == 0
            if (!blink) {
                c.saveLayerAlpha(null, 110)
                art.draw(c, v, car.x - 70f, car.y - 30f, car.angle, car.wheelRot, car.aim, false, w, true)
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
        art.noRider = g.driver.out
        val carPic = Sprites.CARS[v.cls.ordinal]
        if (!hurt && !g.wrecked && Sprites.has(carPic)) {
            c.save()
            c.translate(car.x, car.y)
            c.rotate(car.angle * 180f / PI.toFloat() + car.squat * 57f)
            val bounce = (car.compress[0] + car.compress[1]) * 0.25f
            sprite(c, carPic, 0f, Car.WHEEL_Y + Car.WHEEL_R + 6f + bounce, if (v.cls == VehicleClass.MOTO) 118f else 104f)
            c.restore()
        } else if (!hurt) art.draw(c, v, car.x, car.y, car.angle, car.wheelRot, car.aim, car.boosting, w, false, car.compress, g.wrecked, car.squat, car.slashT)
        art.noRider = false
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

    /** Zombie with the origin at its feet. */

    // ------------------------------------------------------------- zombies

    internal fun drawZombie(c: Canvas, z: Zombie) {
        val px = if (g.driver.out) g.driver.x else g.car.x
        val dir = sign(px - z.x).let { if (it == 0f) -1f else it }
        // AI sprites face right; turn them towards the player
        val sway = sin(z.phase)
        val pic = Sprites.zombie(z.type)
        if (Sprites.walks(z.type) && walkSprite(c, pic, z.x, z.y + 4f, z.type.h * 1.3f, dir < 0f, z.phase, Sprites.stride(z.type), lean = 4f)) return
        if (sprite(c, pic, z.x, z.y + 4f - kotlin.math.abs(sway) * 4f, z.type.h * 1.3f, flip = dir < 0f,
                rot = sway * (if (z.type.flying) 10f else 6f), squash = 1f + 0.04f * kotlin.math.abs(sin(z.phase * 2f)))) return
        c.save()
        c.translate(z.x, z.y)
        when (z.type) {
            ZombieType.WALKER, ZombieType.RUNNER, ZombieType.FAT ->
                zombieFigure(c, z.type.w, z.type.h, z.shirt, sin(z.phase), dir, z.type == ZombieType.FAT)
            else -> creatureFigure(c, z.type, sin(z.phase), dir, z.phase)
        }
        c.restore()
    }

    /** Regional monsters, origin at the feet (bats: bottom of the body). */

    internal fun zombieFigure(c: Canvas, w: Float, h: Float, shirt: Int, swing: Float, dir: Float, fat: Boolean) {
        // charred in the volcano, pale ghouls at night
        val skin = when (g.theme.region) {
            Region.VOLCANO -> 0xFF8D6E63.toInt()
            Region.NIGHT -> 0xFFB0BEC5.toInt()
            else -> 0xFF8BC34A.toInt()
        }
        val skinDark = when (g.theme.region) {
            Region.VOLCANO -> 0xFF4E342E.toInt()
            Region.NIGHT -> 0xFF78909C.toInt()
            else -> 0xFF689F38.toInt()
        }
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
        when {
            d.kind == Debris.ZOMBIE || d.kind == Debris.FAT_ZOMBIE -> {
                c.saveLayerAlpha(null, a)
                c.translate(0f, d.h / 2)
                zombieFigure(c, d.w, d.h, d.color, 0.8f, 1f, d.kind == Debris.FAT_ZOMBIE)
                c.restore()
            }
            d.kind >= Debris.CREATURE -> {
                c.saveLayerAlpha(null, a)
                c.translate(0f, d.h / 2)
                creatureFigure(c, ZombieType.entries[d.kind - Debris.CREATURE], 0.8f, 1f, 0f)
                c.restore()
            }
            d.kind == Debris.PLANK -> {
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
            ObstacleType.BUNKER -> drawBunker(c, o, l, t, w, h)
            ObstacleType.WRECK -> if (!sprite(c, "car_wreck_burning", o.x, t + h + 6f, h * 1.35f)) {
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
        if (o.flash > 0f && o.type != ObstacleType.BUNKER) {
            fill.color = 0x88FFFFFF.toInt()
            c.drawRect(l, t, l + w, t + h, fill)
        }
        if (o.barT > 0f || o.ramHp < 1f || (o.type == ObstacleType.BUNKER && o.hp < o.maxHp)) drawObstacleBar(c, o, t)
    }

    /** A roadside building to loot; its look depends on the kind. */
    private fun drawBuilding(c: Canvas, b: Building) {
        val gy = g.terrain.heightAt(b.x) + 26f
        val w = Building.W
        val l = b.x - w / 2
        val rr = b.x + w / 2
        val h = when (b.kind) { 0 -> 190f; 2 -> 150f; 3 -> 175f; else -> 200f }
        val top = gy - h
        val wall = when (b.kind) {
            0 -> 0xFF8D6E63.toInt()
            1 -> 0xFF9E8B6E.toInt()
            2 -> 0xFFB0A89A.toInt()
            3 -> 0xFF78858C.toInt()
            else -> 0xFFD7D2C8.toInt()
        }
        if (b.kind == 2) {
            // fuel canopy on two posts with a pump under it
            fill.color = 0xFF616161.toInt()
            c.drawRect(rr + 20f, gy - 170f, rr + 30f, gy, fill)
            c.drawRect(rr + 150f, gy - 170f, rr + 160f, gy, fill)
            fill.color = 0xFFD32F2F.toInt()
            c.drawRect(rr, gy - 196f, rr + 180f, gy - 166f, fill)
            fill.color = 0xFFFFFFFF.toInt()
            c.drawRect(rr, gy - 184f, rr + 180f, gy - 178f, fill)
            fill.color = 0xFF455A64.toInt()
            c.drawRoundRect(rr + 70f, gy - 80f, rr + 104f, gy, 6f, 6f, fill)
            fill.color = 0xFFFFC107.toInt()
            c.drawRect(rr + 76f, gy - 70f, rr + 98f, gy - 52f, fill)
        }
        // walls
        fill.color = wall
        c.drawRect(l, top, rr, gy, fill)
        fill.color = 0x30000000
        c.drawRect(l, gy - 40f, rr, gy, fill)
        fill.color = 0x22FFFFFF
        c.drawRect(l, top, rr, top + 20f, fill)
        // cracks and grime
        stroke.color = 0x55000000
        stroke.strokeWidth = 2f
        c.drawLine(l + 30f, top + 40f, l + 52f, top + 90f, stroke)
        c.drawLine(l + 52f, top + 90f, l + 44f, top + 120f, stroke)
        c.drawLine(rr - 40f, top + 30f, rr - 60f, top + 70f, stroke)
        when (b.kind) {
            0 -> { // pitched roof
                fill.color = 0xFF4E342E.toInt()
                path.reset()
                path.moveTo(l - 20f, top + 4f); path.lineTo(b.x, top - 80f); path.lineTo(rr + 20f, top + 4f); path.close()
                c.drawPath(path, fill)
                fill.color = 0xFF3E2723.toInt()
                c.drawRect(rr - 70f, top - 70f, rr - 46f, top - 20f, fill)
            }
            1 -> { // striped awning
                for (k in 0 until 7) {
                    fill.color = if (k % 2 == 0) 0xFFC62828.toInt() else 0xFFEEEEEE.toInt()
                    path.reset()
                    val ax = l + k * w / 7f
                    path.moveTo(ax, top + 60f); path.lineTo(ax + w / 7f, top + 60f); path.lineTo(ax + w / 7f + 6f, top + 92f); path.lineTo(ax + 6f, top + 92f); path.close()
                    c.drawPath(path, fill)
                }
            }
            3 -> { // corrugated metal
                stroke.color = 0x33000000
                stroke.strokeWidth = 3f
                var x = l + 8f
                while (x < rr) {
                    c.drawLine(x, top, x, gy, stroke)
                    x += 14f
                }
            }
            4 -> { // green cross sign
                fill.color = 0xFF2E7D32.toInt()
                c.drawRect(rr - 70f, top + 20f, rr - 30f, top + 70f, fill)
                fill.color = Color.WHITE
                c.drawRect(rr - 56f, top + 26f, rr - 44f, top + 64f, fill)
                c.drawRect(rr - 64f, top + 39f, rr - 36f, top + 51f, fill)
            }
        }
        // boarded-up windows
        for (wx in floatArrayOf(l + 24f, rr - 84f)) {
            if (b.kind == 4 && wx > b.x) continue
            fill.color = 0xFF1E1A18.toInt()
            c.drawRect(wx, top + 104f, wx + 60f, top + 150f, fill)
            fill.color = 0xFF8D6E63.toInt()
            c.save()
            c.translate(wx + 30f, top + 127f)
            c.rotate(-12f)
            c.drawRect(-36f, -5f, 36f, 4f, fill)
            c.restore()
        }
        // door
        val dl = b.x - 32f
        val dt = gy - 112f
        fill.color = 0xFF1A1512.toInt()
        c.drawRect(dl, dt, dl + 64f, gy - 26f, fill)
        if (!b.looted) {
            // warm light leaking from inside
            val p = 0.6f + 0.4f * sin(g.anim * 3f + b.x)
            fill.color = Color.argb((90 * p).toInt(), 255, 214, 120)
            c.drawRect(dl + 4f, dt + 4f, dl + 60f, gy - 30f, fill)
            fill.color = 0xFF5D4037.toInt()
            c.drawRect(dl + 4f, dt + 4f, dl + 30f, gy - 30f, fill)
            fill.color = 0xFFFFC107.toInt()
            c.drawCircle(dl + 24f, dt + 50f, 3f, fill)
        } else {
            // kicked open
            fill.color = 0xFF5D4037.toInt()
            path.reset()
            path.moveTo(dl - 2f, dt + 4f); path.lineTo(dl - 22f, dt + 14f); path.lineTo(dl - 22f, gy - 22f); path.lineTo(dl - 2f, gy - 30f); path.close()
            c.drawPath(path, fill)
        }
        // sign
        fill.color = 0xFF2B2622.toInt()
        c.drawRoundRect(b.x - 76f, top + 18f, b.x + 76f, top + 52f, 6f, 6f, fill)
        text.textAlign = Paint.Align.CENTER
        text.color = if (b.looted) 0xFF9E9E9E.toInt() else 0xFFFFE082.toInt()
        fitText(c, Building.NAMES[b.kind], b.x, top + 44f, 22f, 140f)
        // chests searched so far, once the driver has been in
        val inn = b.interior
        if (inn != null && !b.looted) {
            fill.color = 0xCC000000.toInt()
            c.drawRoundRect(b.x - 70f, dt - 44f, b.x + 70f, dt - 12f, 8f, 8f, fill)
            text.color = 0xFFFFC107.toInt()
            fitText(c, "RƯƠNG ${inn.chests.count { it.opened }}/${inn.chests.size}", b.x, dt - 20f, 20f, 130f)
        }
    }

    /** The driver on foot with a pistol that follows its aim. */
    private fun drawDriver(c: Canvas) {
        val d = g.driver
        val x = d.x
        val y = d.y
        val f = d.face
        val swing = if (d.dir != 0f) sin(d.walk) * 9f else 0f
        if (d.hurtT > 0f && (g.anim * 30f).toInt() % 2 == 0 && g.dyingT <= 0f) return
        if (g.dyingT > 0f) {
            // fallen
            c.save()
            c.translate(x, y - 6f)
            c.rotate(-90f * f)
            c.translate(-x, -y)
        } else {
            c.save()
        }
        drawDriverBody(c, d, x, y, f, swing)
        c.restore()
    }

    /** Muzzle flash: the AI effect picture if there is one, else a hot star. */
    private fun muzzle(c: Canvas, x: Float, y: Float, a: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(a * 180f / PI.toFloat())
        if (!spriteBox(c, "fx_muzzle", -6f, -22f, 50f, 22f)) {
            path.reset()
            for (k in 0 until 10) {
                val rr = if (k % 2 == 0) (if (k == 0) 34f else 18f) else 7f
                val t = k * PI.toFloat() / 5
                if (k == 0) path.moveTo(cos(t) * rr, sin(t) * rr) else path.lineTo(cos(t) * rr, sin(t) * rr)
            }
            path.close()
            fill.color = 0xFFFFC107.toInt()
            c.drawPath(path, fill)
            fill.color = 0xFFFFF9C4.toInt()
            c.drawCircle(4f, 0f, 7f, fill)
        }
        c.restore()
    }

    internal fun drawDriverBody(c: Canvas, d: Driver, x: Float, y: Float, f: Float, swing: Float) {
        // shadow
        fill.color = 0x40000000
        c.drawOval(x - 20f, y - 4f, x + 20f, y + 4f, fill)
        val moving = d.dir != 0f && g.state == State.PLAYING
        val jumping = d.air > 0.05f
        val pic = when {
            jumping && Sprites.has("survivor_jump") -> "survivor_jump"
            d.atkT > 0f && Sprites.has("survivor_shoot") -> "survivor_shoot"
            moving && Sprites.has("survivor_run") -> "survivor_run"
            else -> "survivor_idle"
        }
        // legs really step (picture cut at the hip), body leans into the run
        val stride = if (jumping) 0f else if (moving) 28f else 0f
        val phase = if (moving) d.walk else g.anim * 3f
        val hurt = d.hurtT > 0f && (g.anim * 20f).toInt() % 2 == 0
        // landing squash: shorter for a moment
        val squat = if (d.landT > 0f) 1f - 0.12f * (d.landT / 0.16f) else 1f
        val kick = if (d.muzzleT > 0f) -4f * f else 0f
        if (walkSprite(c, pic, x + kick, y + 4f, 108f * squat, f < 0f, phase, stride, if (hurt) 150 else 255, if (moving) 5f else 0f)) {
            if (d.muzzleT > 0f) muzzle(c, x + cos(d.aim) * 62f, y - 58f + sin(d.aim) * 62f, d.aim)
            driverHp(c, d, x, y)
            return
        }
        // legs
        stroke.color = 0xFF283593.toInt()
        stroke.strokeWidth = 9f
        c.drawLine(x - 4f, y - 34f, x - 4f + swing, y - 2f, stroke)
        c.drawLine(x + 4f, y - 34f, x + 4f - swing, y - 2f, stroke)
        fill.color = 0xFF212121.toInt()
        c.drawRect(x - 9f + swing, y - 6f, x + 3f + swing + 6f * f, y, fill)
        c.drawRect(x - 3f - swing, y - 6f, x + 9f - swing + 6f * f, y, fill)
        // torso
        val jacket = 0xFF5D4037.toInt()
        fill.color = jacket
        c.drawRoundRect(x - 12f, y - 70f, x + 12f, y - 30f, 6f, 6f, fill)
        fill.color = 0xFF3E2723.toInt()
        c.drawRect(x - 12f, y - 40f, x + 12f, y - 34f, fill)
        // head with a cap
        fill.color = 0xFFE0B48C.toInt()
        c.drawCircle(x, y - 82f, 11f, fill)
        fill.color = 0xFFB71C1C.toInt()
        c.drawRect(x - 12f, y - 94f, x + 11f, y - 85f, fill)
        c.drawRect(x + (if (f > 0f) 4f else -18f), y - 88f, x + (if (f > 0f) 18f else -4f), y - 84f, fill)
        // arm and hand weapon along the aim (melee weapons swing)
        val w = g.save.sideWeapon
        c.save()
        c.translate(x, y - 60f)
        var rot = d.aim * 180f / PI.toFloat()
        if (w.melee && d.atkT > 0f) {
            val t = d.atkT / 0.25f
            rot += if (w == SideWeapon.AXE) (t - 0.5f) * -140f * f else 0f
            if (w == SideWeapon.SPEAR) c.translate(cos(d.aim) * 30f * sin(t * PI.toFloat()), sin(d.aim) * 30f * sin(t * PI.toFloat()))
        }
        c.rotate(rot)
        stroke.color = jacket
        stroke.strokeWidth = 8f
        c.drawLine(0f, 0f, 22f, 0f, stroke)
        c.translate(22f, 0f)
        if (abs(rot % 360f) > 90f && abs(rot % 360f) < 270f) c.scale(1f, -1f)
        handWeapon(c, w, 1f)
        c.restore()
        // shield bubble / clone glow
        if (d.shieldT > 0f) {
            val pulse = 0.5f + 0.5f * sin(g.anim * 8f)
            fill.color = 0x3382B1FF
            c.drawCircle(x, y - 50f, 72f, fill)
            stroke.color = 0xFF82B1FF.toInt()
            stroke.strokeWidth = 4f
            stroke.alpha = if (d.shieldT < 1.5f && (g.anim * 10f).toInt() % 2 == 0) 80 else (170 + 80 * pulse).toInt()
            c.drawCircle(x, y - 50f, 72f, stroke)
            stroke.alpha = 255
        }
        if (d.cloneT > 0f) {
            stroke.color = 0xAA64FFDA.toInt()
            stroke.strokeWidth = 3f
            c.drawOval(x - 30f, y - 10f, x + 30f, y + 6f, stroke)
        }
        driverHp(c, d, x, y)
    }

    private fun driverHp(c: Canvas, d: Driver, x: Float, y: Float) {
        if (g.state == State.MENU) return
        val hf = (d.hp / d.maxHp).coerceIn(0f, 1f)
        fill.color = 0xCC000000.toInt()
        c.drawRoundRect(x - 30f, y - 124f, x + 30f, y - 112f, 4f, 4f, fill)
        fill.color = if (hf > 0.35f) 0xFF66BB6A.toInt() else 0xFFE53935.toInt()
        c.drawRoundRect(x - 28f, y - 122f, x - 28f + 56f * hf, y - 114f, 3f, 3f, fill)
    }

    /** Concrete pillbox: sandbags, a dome with a firing slit and a swinging machine gun. */
    private fun drawBunker(c: Canvas, o: Obstacle, l: Float, t: Float, w: Float, h: Float) {
        val gx = o.x - 10f
        val gy = o.y - 52f
        // gun first, so the dome hides its breech
        c.save()
        c.translate(gx, gy)
        c.rotate(o.aim * 180f / PI.toFloat())
        fill.color = 0xFF263238.toInt()
        c.drawRect(0f, -5f, 50f, 5f, fill)
        fill.color = 0xFF37474F.toInt()
        c.drawRect(36f, -7f, 44f, 7f, fill)
        if (o.muzzleT > 0f) {
            fill.color = 0xFFFFE082.toInt()
            path.reset()
            path.moveTo(50f, -9f); path.lineTo(74f, 0f); path.lineTo(50f, 9f); path.close()
            c.drawPath(path, fill)
        }
        c.restore()
        // dome
        val hit = o.flash > 0f
        path.reset()
        path.moveTo(l + 8f, t + h)
        path.lineTo(l + 22f, t + 18f)
        path.quadTo(o.x, t - 14f, l + w - 22f, t + 18f)
        path.lineTo(l + w - 8f, t + h)
        path.close()
        fill.color = if (hit) 0xFFBDBDBD.toInt() else 0xFF77736B.toInt()
        c.drawPath(path, fill)
        c.save()
        c.clipPath(path)
        fill.color = 0x30FFFFFF
        c.drawRect(l, t - 20f, l + w, t + 22f, fill)
        fill.color = 0x40000000
        c.drawRect(l, t + h - 20f, l + w, t + h, fill)
        fill.color = 0x66834012
        c.drawOval(l + 26f, t + 28f, l + 60f, t + 42f, fill)
        c.drawOval(l + w - 50f, t + 40f, l + w - 24f, t + 52f, fill)
        stroke.color = 0x44000000
        stroke.strokeWidth = 2f
        c.drawLine(l + 40f, t + 8f, l + 34f, t + h, stroke)
        c.drawLine(l + w - 40f, t + 8f, l + w - 32f, t + h, stroke)
        c.restore()
        stroke.color = 0xFF3A3632.toInt()
        stroke.strokeWidth = 3f
        c.drawPath(path, stroke)
        // firing slit
        fill.color = 0xFF121212.toInt()
        c.drawRoundRect(gx - 34f, gy - 7f, gx + 34f, gy + 7f, 5f, 5f, fill)
        if (o.muzzleT > 0f) {
            fill.color = 0x88FFC107.toInt()
            c.drawRoundRect(gx - 30f, gy - 5f, gx + 30f, gy + 5f, 4f, 4f, fill)
        }
        // sandbags along the base
        for (k in 0 until 6) {
            val bx = l - 6f + k * (w + 12f) / 6f
            fill.color = if (k % 2 == 0) 0xFFA1887F.toInt() else 0xFF8D7B68.toInt()
            c.drawOval(bx, t + h - 18f, bx + (w + 12f) / 6f + 6f, t + h + 2f, fill)
        }
        // warning paint
        fill.color = 0xFFB71C1C.toInt()
        c.drawCircle(o.x + 34f, t + 26f, 6f, fill)
    }

    private fun drawBoss(c: Canvas, b: Boss) {
        val dir = if (targetX() >= b.x) 1f else -1f
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
        when (rk.kind) {
            3 -> {
                val l = hypot(rk.vx, rk.vy).coerceAtLeast(1f)
                stroke.color = 0x66FF3D00
                stroke.strokeWidth = 8f
                c.drawLine(rk.x, rk.y, rk.x - rk.vx / l * 30f, rk.y - rk.vy / l * 30f, stroke)
                stroke.color = 0xFFFF8A65.toInt()
                stroke.strokeWidth = 3.5f
                c.drawLine(rk.x, rk.y, rk.x - rk.vx / l * 30f, rk.y - rk.vy / l * 30f, stroke)
            }
            1 -> {
                fill.color = 0x66FF6D00
                c.drawCircle(rk.x, rk.y, 34f, fill)
                fill.color = 0xFFFF6D00.toInt()
                c.drawCircle(rk.x, rk.y, 22f, fill)
                fill.color = 0xFFFFEB3B.toInt()
                c.drawCircle(rk.x - 4f, rk.y - 4f, 11f, fill)
            }
            2 -> {
                val p = 0.6f + 0.4f * sin(g.anim * 20f)
                fill.color = Color.argb((90 * p).toInt(), 100, 255, 218)
                c.drawCircle(rk.x, rk.y, 30f, fill)
                fill.color = 0xFF64FFDA.toInt()
                c.drawCircle(rk.x, rk.y, 15f, fill)
                fill.color = Color.WHITE
                c.drawCircle(rk.x, rk.y, 6f, fill)
            }
            else -> {
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
        }
    }

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
        if (it.type == ItemType.GEM) {
            drawGem(c, it.x, y, 1f)
            return
        }
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

    /** A faceted red gem with a glint. */
    internal fun drawGem(c: Canvas, x: Float, y: Float, s: Float) {
        if (sprite(c, "item_gem", x, y + 20f * s + sin(g.anim * 4f + x * 0.01f) * 2f * s, 40f * s)) return
        val glow = 0.6f + 0.4f * sin(g.anim * 6f + x * 0.01f)
        fill.color = Color.argb((90 * glow).toInt(), 255, 60, 60)
        c.drawCircle(x, y, 30f * s, fill)
        path.reset()
        path.moveTo(x - 18f * s, y - 6f * s); path.lineTo(x - 9f * s, y - 17f * s); path.lineTo(x + 9f * s, y - 17f * s)
        path.lineTo(x + 18f * s, y - 6f * s); path.lineTo(x, y + 20f * s); path.close()
        fill.color = 0xFFC62828.toInt()
        c.drawPath(path, fill)
        fill.color = 0xFFFF5252.toInt()
        path.reset()
        path.moveTo(x - 9f * s, y - 17f * s); path.lineTo(x + 9f * s, y - 17f * s); path.lineTo(x + 5f * s, y - 6f * s); path.lineTo(x - 5f * s, y - 6f * s); path.close()
        c.drawPath(path, fill)
        fill.color = 0xFF8E0000.toInt()
        path.reset()
        path.moveTo(x + 5f * s, y - 6f * s); path.lineTo(x + 18f * s, y - 6f * s); path.lineTo(x, y + 20f * s); path.close()
        c.drawPath(path, fill)
        stroke.color = 0xFFFFCDD2.toInt()
        stroke.strokeWidth = 1.5f * s
        c.drawLine(x - 18f * s, y - 6f * s, x + 18f * s, y - 6f * s, stroke)
        fill.color = Color.argb((230 * glow).toInt(), 255, 255, 255)
        c.drawCircle(x - 6f * s, y - 12f * s, 2.5f * s, fill)
    }

    private fun drawShots(c: Canvas) {
        for (s in g.shots) {
            if (s.side != null) {
                sideShot(c, s)
                continue
            }
            val l = hypot(s.vx, s.vy).coerceAtLeast(1f)
            val ux = s.vx / l
            val uy = s.vy / l
            when (s.kind) {
                WeaponType.PISTOL, WeaponType.SMG, WeaponType.MINIGUN, WeaponType.SHOTGUN -> {
                    val len = if (s.kind == WeaponType.SHOTGUN) 16f else 26f
                    stroke.color = 0x66FFAB00
                    stroke.strokeWidth = 8f
                    c.drawLine(s.x, s.y, s.x - ux * len, s.y - uy * len, stroke)
                    stroke.color = 0xFFFFF176.toInt()
                    stroke.strokeWidth = 3.5f
                    c.drawLine(s.x, s.y, s.x - ux * len, s.y - uy * len, stroke)
                }
                WeaponType.CROSSBOW -> {
                    stroke.color = 0xFF8D6E63.toInt()
                    stroke.strokeWidth = 5f
                    c.drawLine(s.x, s.y, s.x - ux * 50f, s.y - uy * 50f, stroke)
                    fill.color = 0xFFB0BEC5.toInt()
                    c.drawCircle(s.x, s.y, 6f, fill)
                }
                WeaponType.CATAPULT -> {
                    fill.color = 0xFF6D4C41.toInt()
                    c.drawCircle(s.x, s.y, 18f, fill)
                    fill.color = 0xFF8D6E63.toInt()
                    c.drawCircle(s.x - 5f, s.y - 5f, 8f, fill)
                }
                WeaponType.FLAMER -> {
                    // a puff of fire that grows and cools from yellow to smoky red
                    val t = (s.age / (s.age + s.life)).coerceIn(0f, 1f)
                    val rad = 10f + s.age * 70f
                    fill.color = when {
                        t < 0.3f -> 0xFFFFEE58.toInt()
                        t < 0.6f -> 0xFFFF9100.toInt()
                        else -> 0xFFD84315.toInt()
                    }
                    fill.alpha = (230 * (1f - t * 0.8f)).toInt()
                    c.drawCircle(s.x, s.y, rad, fill)
                    fill.color = 0xFFFFF8E1.toInt()
                    fill.alpha = (180 * (1f - t)).toInt()
                    c.drawCircle(s.x, s.y, rad * 0.45f, fill)
                    fill.alpha = 255
                }
                WeaponType.ROCKET -> {
                    c.save()
                    c.translate(s.x, s.y)
                    c.rotate(atan2Deg(s.vy, s.vx))
                    val f = 14f + Random.nextFloat() * 14f
                    fill.color = 0xDDFF6D00.toInt()
                    path.reset()
                    path.moveTo(-16f, -5f); path.lineTo(-16f - f, 0f); path.lineTo(-16f, 5f); path.close()
                    c.drawPath(path, fill)
                    fill.color = 0xFFECEFF1.toInt()
                    c.drawRoundRect(-18f, -5f, 12f, 5f, 4f, 4f, fill)
                    fill.color = 0xFFE53935.toInt()
                    path.reset()
                    path.moveTo(10f, -5f); path.lineTo(22f, 0f); path.lineTo(10f, 5f); path.close()
                    c.drawPath(path, fill)
                    fill.color = 0xFF455A64.toInt()
                    c.drawRect(-18f, -9f, -10f, 9f, fill)
                    c.restore()
                }
                WeaponType.KNIFE, WeaponType.LASER -> {}
            }
        }
        for (b in g.beams) {
            val a = (b.life / 0.18f).coerceIn(0f, 1f)
            stroke.color = 0xFF00E5FF.toInt()
            stroke.alpha = (110 * a).toInt()
            stroke.strokeWidth = 22f * a + 4f
            c.drawLine(b.x1, b.y1, b.x2, b.y2, stroke)
            stroke.color = 0xFFE0F7FA.toInt()
            stroke.alpha = (255 * a).toInt()
            stroke.strokeWidth = 6f * a + 1f
            c.drawLine(b.x1, b.y1, b.x2, b.y2, stroke)
            stroke.alpha = 255
        }
    }

    internal fun atan2Deg(y: Float, x: Float) = (kotlin.math.atan2(y, x) * 180f / PI.toFloat())

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

    internal fun bar(c: Canvas, x: Float, y: Float, w: Float, frac: Float, color: Int, label: String) {
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
        g.rooms?.let {
            drawRoomHud(c, it)
            return
        }
        val W = g.W
        val H = g.H
        val car = g.car

        // left: health, fuel, boost, ammo
        glassPanel(c, 12f, 12f, 330f, 176f)
        val d = g.driver
        // on foot the health bar is the driver's own
        val hpF = if (d.out) d.hp / d.maxHp else car.hp / car.maxHp
        bar(c, 22f, 22f, 270f, hpF, if (d.out) (if (hpF > 0.3f) 0xFF29B6F6.toInt() else 0xFFE53935.toInt()) else if (hpF > 0.3f) 0xFF43A047.toInt() else 0xFFE53935.toInt(), if (d.out) "♥" else "✚")
        val fuelLow = car.fuel / car.maxFuel < 0.2f && (g.anim * 4f).toInt() % 2 == 0
        bar(c, 22f, 58f, 270f, car.fuel / car.maxFuel, if (fuelLow) 0xFFFF5252.toInt() else 0xFFFF9800.toInt(), "⛽")
        bar(c, 22f, 94f, 270f, car.boost / car.maxBoost, 0xFF29B6F6.toInt(), "🚀")
        text.textAlign = Paint.Align.LEFT
        text.textSize = 28f
        text.color = Color.WHITE
        c.drawText("${g.weapon.icon} ${if (g.weapon == WeaponType.KNIFE) "∞" else car.ammo.toInt().toString()}", 26f, 158f, text)
        var sx = 150f
        val shieldT = if (d.out) d.shieldT else car.shieldT
        val cloneT = if (d.out) d.cloneT else car.cloneT
        if (shieldT > 0f) {
            text.color = 0xFF82B1FF.toInt()
            c.drawText("🛡${shieldT.toInt() + 1}", sx, 158f, text)
            sx += 80f
        }
        if (cloneT > 0f) {
            text.color = 0xFF64FFDA.toInt()
            c.drawText("👥${cloneT.toInt() + 1}", sx, 158f, text)
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
        val inn = g.interior
        if (inn != null) fitText(c, "🏠 ${Building.NAMES[inn.b.kind]} — ${inn.roomName(d.x)}  •  RƯƠNG ${inn.chests.count { it.opened }}/${inn.chests.size}  •  🧟 ${inn.zombiesLeft}", W / 2 + 17f, py + 44f, 24f, pr - pl + 70f)
        else fitText(c, "+${g.distanceM.toInt()}m  •  tổng ${g.save.distM + g.distanceM.toInt()}m / ${Campaign.GOAL_M}m  •  NGÀY ${g.save.day}", W / 2 + 17f, py + 44f, 24f, pr - pl + 70f)

        val closed = g.phase >= Game.PHASE_TIME.size
        val secs = ceil(g.phaseLeft).toInt()
        val timer = when {
            closed -> "VÒNG BO ĐÃ KHÉP"
            g.phase == 2 -> "BO 3: $secs"
            else -> "BO ${g.phase + 1}  ${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"
        }
        val gap = ((g.playerX - (if (d.out) 0f else Car.HALF_LEN) - g.stormX) / Game.METER).toInt()
        text.color = if (closed || g.phase == 2) 0xFFFF5252.toInt() else 0xFFEA80FC.toInt()
        if (g.stormX < -1e8f) fitText(c, "⛽ ${car.fuel.toInt()}L xăng   •   đi càng xa, trại mới càng gần trạm cứu hộ", W / 2 + 17f, py + 84f, 26f, pr - pl + 70f)
        else fitText(c, "🌪 $timer   •   Bão cách ${max(0, gap)}m", W / 2 + 17f, py + 84f, 28f, pr - pl + 70f)

        // money & pause
        val moneyText = "+${g.runMoney}$  +${g.runGems}♦"
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
            gap < 0 && d.out -> if (g.interior != null) "⚠ BÃO TRÀN VÀO NHÀ! RA NGOÀI CHẠY!" else "⚠ ĐANG Ở TRONG BÃO!"
            gap in 0..25 -> "⚠ BÃO ĐANG ĐUỔI KỊP!"
            car.fuel <= 0f && !g.driver.out -> "HẾT XĂNG!"
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

        if (g.state == State.PLAYING && g.dyingT <= 0f && d.out) {
            for (b in g.controls()) drawButton(c, b, g.isHeld(b.id))
            drawJoystick(c)
        }
        if (d.out) {
            drawCountdown(c)
            return
        }
        // speedometer
        val kmh = abs(car.forwardSpeed) / Game.METER * 3.6f
        val cx = W / 2
        val cy = H + 12f
        // steel bezel with bolts
        fill.color = 0x66000000
        c.drawCircle(cx + 3f, cy + 5f, 150f, fill)
        fill.color = 0xFF55504A.toInt()
        c.drawCircle(cx, cy, 150f, fill)
        fill.color = 0x2EFFFFFF
        c.drawCircle(cx, cy - 6f, 144f, fill)
        fill.color = 0x60834012
        c.drawOval(cx - 130f, cy - 120f, cx - 80f, cy - 100f, fill)
        c.drawOval(cx + 70f, cy - 135f, cx + 120f, cy - 118f, fill)
        stroke.color = 0xFF221F1C.toInt()
        stroke.strokeWidth = 3f
        c.drawCircle(cx, cy, 150f, stroke)
        for (k in 0..6) {
            val a = PI.toFloat() * (1.06f + k * 0.147f)
            rivet(c, cx + cos(a) * 141f, cy + sin(a) * 141f, 3.6f)
        }
        fill.color = 0xF0161514.toInt()
        c.drawCircle(cx, cy, 132f, fill)
        stroke.color = 0x55000000
        stroke.strokeWidth = 3f
        c.drawCircle(cx, cy, 132f, stroke)
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

        if (g.state == State.PLAYING && g.dyingT <= 0f && (!g.wrecked || g.driver.out)) for (b in g.controls()) drawButton(c, b, g.isHeld(b.id))

        drawCountdown(c)
    }

    /** Bo 3: the big 5-4-3-2-1. */
    private fun drawCountdown(c: Canvas) {
        if (g.state == State.PLAYING && g.phase == 2) {
            val n = ceil(g.phaseLeft).toInt().coerceAtLeast(1)
            val frac = (g.phaseLeft - (n - 1)).coerceIn(0f, 1f)
            text.textSize = 180f + 60f * frac
            text.color = Color.argb((80 + 120 * frac).toInt(), 255, 82, 82)
            c.drawText(n.toString(), g.W / 2, g.H * 0.6f, text)
        }
    }

    internal fun drawButton(c: Canvas, b: Button, held: Boolean) {
        val grow = if (held) 3f else 0f
        val l = b.l - grow
        val t = b.t - grow
        val rr = b.r + grow
        val bb = b.b + grow
        metalPlate(c, l, t, rr, bb, 22f)
        // coloured face set into the plate
        val rgb = b.color and 0xFFFFFF
        val face = if (rgb == 0xFFFFFF) 0xFF6B655F.toInt() else blend(0xFF3A3632.toInt(), 0xFF000000.toInt() or rgb, if (held) 0.95f else 0.7f)
        r.set(l + 9f, t + 9f, rr - 9f, bb - 9f)
        fill.color = face
        c.drawRoundRect(r, 15f, 15f, fill)
        platePath.reset()
        platePath.addRoundRect(r, 15f, 15f, Path.Direction.CW)
        c.save()
        c.clipPath(platePath)
        fill.color = 0x30FFFFFF
        c.drawRect(r.left, r.top, r.right, r.top + (r.bottom - r.top) * 0.42f, fill)
        if (b.id == "gas") {
            // flame stripes along the bottom of the throttle
            var x = r.left - 30f
            var k = 0
            while (x < r.right) {
                fill.color = if (k % 2 == 0) 0xFFFF6D00.toInt() else 0xFFFFC107.toInt()
                path.reset()
                path.moveTo(x, r.bottom); path.lineTo(x + 22f, r.bottom - 46f); path.lineTo(x + 40f, r.bottom - 46f); path.lineTo(x + 18f, r.bottom); path.close()
                c.drawPath(path, fill)
                x += 34f
                k++
            }
        }
        c.restore()
        stroke.color = if (held) 0xEEFFFFFF.toInt() else 0x66000000
        stroke.strokeWidth = if (held) 3f else 2f
        c.drawRoundRect(r, 15f, 15f, stroke)
        rivet(c, l + 6f, t + 6f, 3f)
        rivet(c, rr - 6f, t + 6f, 3f)
        rivet(c, l + 6f, bb - 6f, 3f)
        rivet(c, rr - 6f, bb - 6f, 3f)
        text.textAlign = Paint.Align.CENTER
        val size = min(52f, (b.b - b.t) * 0.38f)
        text.textSize = size
        val w = text.measureText(b.label)
        if (w > b.r - b.l - 24f) text.textSize *= (b.r - b.l - 24f) / w
        text.style = Paint.Style.STROKE
        text.strokeWidth = 6f
        text.color = 0xAA000000.toInt()
        c.drawText(b.label, b.cx, b.cy + text.textSize * 0.36f, text)
        text.style = Paint.Style.FILL
        text.color = Color.WHITE
        c.drawText(b.label, b.cx, b.cy + text.textSize * 0.36f, text)
    }

    // ------------------------------------------------------------ screens

    internal fun dim(c: Canvas, a: Int = 0xB0) {
        fill.color = Color.argb(a, 0, 0, 0)
        c.drawRect(0f, 0f, g.W, g.H, fill)
    }

    internal fun title(c: Canvas, s: String, x: Float, y: Float, color: Int, size: Float = 76f, maxW: Float = g.W - 40f) {
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

    internal fun drawMenuButtons(c: Canvas) {
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
            stroke.color = 0xFF221F1C.toInt()
            stroke.strokeWidth = 4f
            r.set(b.l, b.t, b.r, b.b)
            c.drawRoundRect(r, 24f, 24f, stroke)
            stroke.color = 0x55FFFFFF
            stroke.strokeWidth = 1.5f
            c.drawRoundRect(b.l + 4f, b.t + 4f, b.r - 4f, b.b - 4f, 20f, 20f, stroke)
            if (b.b - b.t > 60f && b.r - b.l > 120f) {
                rivet(c, b.l + 14f, b.t + 14f, 3.4f)
                rivet(c, b.r - 14f, b.t + 14f, 3.4f)
                rivet(c, b.l + 14f, b.b - 14f, 3.4f)
                rivet(c, b.r - 14f, b.b - 14f, 3.4f)
            }
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

    internal fun fitText(c: Canvas, s: String, x: Float, y: Float, size: Float, maxW: Float) {
        text.textSize = size
        val w = text.measureText(s)
        if (w > maxW) text.textSize = size * maxW / w
        c.drawText(s, x, y, text)
    }

    private fun drawMenu(c: Canvas) {
        val W = g.W
        val H = g.H
        val s = g.save
        fill.color = 0x30000000
        c.drawRect(0f, 0f, W, H, fill)
        garageNav(c, home = true)
        garageTopBar(c)
        val mx = g.menuCenterX()
        val th = Themes.of(g.campaign.zone)
        // floating island with the camp on it
        val isl = g.menuIsland()
        val bob = sin(g.anim * 1.6f) * 6f
        val top = 330f + bob
        val iw = (isl[2] - isl[0]) / 2
        fill.color = 0x40000000
        c.drawOval(mx - iw * 0.8f, 520f, mx + iw * 0.8f, 548f, fill)
        path.reset()
        path.moveTo(mx - iw, top)
        path.lineTo(mx - iw * 0.8f, top + 60f)
        path.lineTo(mx - iw * 0.5f, top + 110f)
        path.lineTo(mx - iw * 0.2f, top + 150f)
        path.lineTo(mx, top + 175f)
        path.lineTo(mx + iw * 0.25f, top + 140f)
        path.lineTo(mx + iw * 0.6f, top + 100f)
        path.lineTo(mx + iw * 0.9f, top + 50f)
        path.lineTo(mx + iw, top)
        path.close()
        fill.color = shade(th.ground[0], 0.7f)
        c.drawPath(path, fill)
        stroke.color = shade(th.ground[0], 0.5f)
        stroke.strokeWidth = 3f
        c.drawLine(mx - iw * 0.5f, top + 40f, mx - iw * 0.35f, top + 110f, stroke)
        c.drawLine(mx + iw * 0.3f, top + 30f, mx + iw * 0.45f, top + 90f, stroke)
        // mesa on the island
        fill.color = th.mid[0]
        path.reset()
        path.moveTo(mx - iw * 0.75f, top)
        path.lineTo(mx - iw * 0.6f, top - 120f)
        path.lineTo(mx - iw * 0.2f, top - 130f)
        path.lineTo(mx - iw * 0.05f, top)
        path.close()
        c.drawPath(path, fill)
        fill.color = th.ground[0]
        c.drawRoundRect(mx - iw - 6f, top - 14f, mx + iw + 6f, top + 16f, 14f, 14f, fill)
        fill.color = th.crust[2]
        c.drawRect(mx - iw, top - 14f, mx + iw, top - 9f, fill)
        // wreck, signpost with a skull flag, the survivor
        if (!sprite(c, Sprites.CARS[s.carTier.coerceIn(0, 4)], mx + iw * 0.5f, top - 4f + sin(g.anim * 6f) * 1.5f, 96f)) {
            fill.color = 0xFF5D4037.toInt()
            c.drawRoundRect(mx + iw * 0.25f, top - 44f, mx + iw * 0.75f, top - 14f, 10f, 10f, fill)
            c.drawRoundRect(mx + iw * 0.35f, top - 66f, mx + iw * 0.6f, top - 40f, 8f, 8f, fill)
            fill.color = 0xFF212121.toInt()
            c.drawCircle(mx + iw * 0.33f, top - 14f, 12f, fill)
            c.drawCircle(mx + iw * 0.67f, top - 14f, 12f, fill)
        }
        fill.color = 0xFF6D4C41.toInt()
        c.drawRect(mx - iw * 0.45f, top - 170f, mx - iw * 0.42f, top - 10f, fill)
        c.drawRect(mx - iw * 0.62f, top - 168f, mx - iw * 0.18f, top - 108f, fill)
        fill.color = 0xFFF5E6C8.toInt()
        c.drawRect(mx - iw * 0.6f, top - 164f, mx - iw * 0.2f, top - 112f, fill)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 34f
        text.color = 0xFF3E2723.toInt()
        c.drawText("${s.distM / 1000}km", mx - iw * 0.4f, top - 124f, text)
        if ((s.nights + 1) % 5 == 0 && !spriteBox(c, "ui_skull", mx - iw * 0.4f - 24f, top - 226f, mx - iw * 0.4f + 24f, top - 178f)) {
            text.textSize = 30f
            c.drawText("👹", mx - iw * 0.4f, top - 182f, text)
        }
        val d = g.driver
        drawDriverBody(c, d, mx + iw * 0.02f, top - 12f, 1f, sin(g.anim * 3f) * 3f)
        // journey: 20 km to the rescue station, four regions on the way
        val bl = mx - 220f
        val br = mx + 220f
        val by = 548f
        fill.color = 0xCC1B1714.toInt()
        c.drawRoundRect(bl - 16f, by - 16f, br + 16f, by + 16f, 16f, 16f, fill)
        val prog = (s.distM / Campaign.GOAL_M.toFloat()).coerceIn(0f, 1f)
        fill.color = 0xFF29B6F6.toInt()
        if (prog > 0f) c.drawRoundRect(bl - 12f, by - 10f, bl + (br - bl) * prog, by + 10f, 10f, 10f, fill)
        for (k in 1..3) {
            val kx = bl + (br - bl) * k / 4f
            fill.color = if (prog >= k / 4f) 0xFF69F0AE.toInt() else 0xFF546E7A.toInt()
            c.drawCircle(kx, by, 10f, fill)
        }
        fill.color = 0xFFFFD740.toInt()
        c.drawCircle(bl + (br - bl) * prog, by, 14f, fill)
        text.textSize = 30f
        c.drawText("🏁", br + 34f, by + 11f, text)
        text.color = 0xFFFFF3E0.toInt()
        fitText(c, "Đã sống sót ${s.nights} đêm • Đã đi ${s.distM}m / ${Campaign.GOAL_M}m • ⚙${s.scrap} • ⛽${s.fuel}L", mx, 596f, 22f, 640f)
        drawMenuButtons(c)
        // red dot when something can be claimed
        if (s.claimable() > 0) {
            val b = g.buttons().firstOrNull { it.id == "missions" }
            if (b != null) {
                fill.color = 0xFFE53935.toInt()
                c.drawCircle(b.r - 8f, b.t + 8f, 13f, fill)
                text.textSize = 18f
                text.color = Color.WHITE
                c.drawText("${s.claimable()}", b.r - 8f, b.t + 14f, text)
            }
        }
        text.textAlign = Paint.Align.LEFT
        text.textSize = 20f
        text.color = 0x99FFFFFF.toInt()
        c.drawText("v${Game.VERSION}", 14f, H - 12f, text)
        if (g.confirmReset) {
            dim(c, 0xC8)
            metalPanel(c, W / 2 - 420f, H * 0.2f, W / 2 + 420f, H * 0.62f + 120f, 20f)
            title(c, "CHƠI LẠI TỪ ĐẦU?", W / 2, H * 0.2f + 80f, 0xFFFF5252.toInt(), 52f, 760f)
            text.textAlign = Paint.Align.CENTER
            text.color = Color.WHITE
            fitText(c, "Xóa toàn bộ tiền, ngọc, xe, vũ khí, nâng cấp, ải đã vượt và bảng xếp hạng.", W / 2, H * 0.2f + 140f, 24f, 760f)
            text.color = 0xFFFFAB91.toInt()
            fitText(c, "Không thể khôi phục!", W / 2, H * 0.2f + 180f, 26f, 760f)
            drawMenuButtons(c)
        }
    }

    /** Daily missions, achievements and the leaderboard. */
    private fun drawMissions(c: Canvas) {
        val W = g.W
        val s = g.save
        dim(c, 0xD0)
        when (g.missionsTab) {
            0 -> {
                text.textAlign = Paint.Align.CENTER
                text.textSize = 22f
                text.color = 0xFFB0BEC5.toInt()
                c.drawText("Nhiệm vụ mới mỗi ngày lúc 0 giờ", W / 2, 118f, text)
                for (i in 0 until 3) {
                    val r = g.missionRow(i)
                    metalPanel(c, r[0], r[1], r[2], r[3], 16f)
                    val prog = s.missionProgress(i).coerceAtLeast(0)
                    val tgt = s.missionTarget(i)
                    text.textAlign = Paint.Align.LEFT
                    text.color = Color.WHITE
                    fitText(c, "📋 ${s.missionText(i)}", r[0] + 30f, r[1] + 48f, 30f, r[2] - r[0] - 320f)
                    val bw = r[2] - r[0] - 330f
                    fill.color = 0xCC111111.toInt()
                    c.drawRoundRect(r[0] + 30f, r[1] + 72f, r[0] + 30f + bw, r[1] + 100f, 12f, 12f, fill)
                    fill.color = if (prog >= tgt) 0xFF69F0AE.toInt() else 0xFF29B6F6.toInt()
                    val f = (prog.toFloat() / tgt).coerceIn(0f, 1f)
                    if (f > 0f) c.drawRoundRect(r[0] + 33f, r[1] + 75f, r[0] + 33f + (bw - 6f) * f, r[1] + 97f, 10f, 10f, fill)
                    text.textAlign = Paint.Align.CENTER
                    text.textSize = 20f
                    text.color = Color.WHITE
                    c.drawText("${min(prog, tgt)} / $tgt", r[0] + 30f + bw / 2, r[1] + 93f, text)
                }
            }
            1 -> for (i in Missions.achievements.indices) {
                val a = Missions.achievements[i]
                val r = g.achRow(i)
                metalPanel(c, r[0], r[1], r[2], r[3], 12f)
                val prog = s.achProgress(i)
                text.textAlign = Paint.Align.LEFT
                text.color = if (s.achDone(i)) 0xFFFFD740.toInt() else Color.WHITE
                fitText(c, "🏆 ${a.title}", r[0] + 20f, r[1] + 30f, 22f, r[2] - r[0] - 160f)
                val bw = r[2] - r[0] - 170f
                fill.color = 0xCC111111.toInt()
                c.drawRoundRect(r[0] + 20f, r[1] + 40f, r[0] + 20f + bw, r[1] + 54f, 7f, 7f, fill)
                fill.color = 0xFF29B6F6.toInt()
                val f = (prog.toFloat() / a.target).coerceIn(0f, 1f)
                if (f > 0f) c.drawRoundRect(r[0] + 21f, r[1] + 41f, r[0] + 21f + (bw - 2f) * f, r[1] + 53f, 6f, 6f, fill)
            }
            else -> drawLeaderboard(c, W / 2 - 400f, 120f, W / 2 + 400f, g.H - 30f)
        }
        drawMenuButtons(c)
    }

    /** The merchant from the video: bandana, green vest and a hook, with a speech bubble. */
    private fun drawMerchant(c: Canvas) {
        val x = g.W - 170f
        val y = g.H + 10f
        val bob = sin(g.anim * 2f) * 3f
        if (!sprite(c, "merchant", x - 40f, y + bob, 420f, rot = sin(g.anim * 1.3f) * 2f, squash = 1f + 0.015f * sin(g.anim * 2.5f))) {
        c.save()
        c.translate(x, y + bob)
        // body
        fill.color = 0xFFF5F5F5.toInt()
        c.drawRoundRect(-120f, -230f, 120f, 0f, 60f, 60f, fill)
        fill.color = 0xFF558B2F.toInt()
        c.drawRect(-120f, -200f, -40f, 0f, fill)
        c.drawRect(40f, -200f, 120f, 0f, fill)
        fill.color = 0xFF33691E.toInt()
        for (k in 0..3) c.drawRect(-108f, -180f + k * 40f, -60f, -160f + k * 40f, fill)
        // shoulder pad with spikes
        fill.color = 0xFF616161.toInt()
        c.drawOval(-170f, -250f, -70f, -170f, fill)
        fill.color = 0xFFBDBDBD.toInt()
        for (k in 0..2) {
            path.reset()
            path.moveTo(-160f + k * 30f, -230f); path.lineTo(-150f + k * 30f, -268f); path.lineTo(-140f + k * 30f, -230f); path.close()
            c.drawPath(path, fill)
        }
        // arm with the hook
        fill.color = 0xFFD7A07A.toInt()
        c.drawRoundRect(-210f, -150f, -110f, -90f, 30f, 30f, fill)
        fill.color = 0xFF5D4037.toInt()
        c.drawRect(-226f, -146f, -196f, -94f, fill)
        stroke.color = 0xFFCFD8DC.toInt()
        stroke.strokeWidth = 7f
        r.set(-268f, -170f, -222f, -124f)
        c.drawArc(r, 0f, 260f, false, stroke)
        // head: neck, face, beard, bandana
        fill.color = 0xFFD7A07A.toInt()
        c.drawRect(-30f, -260f, 30f, -220f, fill)
        c.drawOval(-62f, -360f, 62f, -240f, fill)
        fill.color = 0xFF4E342E.toInt()
        path.reset()
        path.moveTo(-58f, -300f); path.quadTo(-50f, -230f, 0f, -226f); path.quadTo(50f, -230f, 58f, -300f)
        path.quadTo(0f, -268f, -58f, -300f)
        c.drawPath(path, fill)
        fill.color = 0xFFC62828.toInt()
        c.drawRoundRect(-66f, -366f, 66f, -326f, 18f, 18f, fill)
        path.reset()
        path.moveTo(60f, -350f); path.lineTo(96f, -366f); path.lineTo(90f, -330f); path.close()
        c.drawPath(path, fill)
        fill.color = 0xFF212121.toInt()
        c.drawCircle(-24f, -306f, 6f, fill)
        c.drawCircle(22f, -306f, 6f, fill)
        stroke.color = 0xFF3E2723.toInt()
        stroke.strokeWidth = 5f
        c.drawLine(-36f, -322f, -12f, -316f, stroke)
        c.drawLine(36f, -322f, 12f, -316f, stroke)
        c.restore()
        }
        // speech bubble
        val bl = 120f
        val bt = g.H - 140f
        fill.color = 0xFFF5ECD7.toInt()
        c.drawRoundRect(bl, bt, g.W - 330f, g.H - 40f, 16f, 16f, fill)
        path.reset()
        path.moveTo(g.W - 330f, bt + 30f); path.lineTo(g.W - 290f, bt + 46f); path.lineTo(g.W - 330f, bt + 60f); path.close()
        c.drawPath(path, fill)
        text.textAlign = Paint.Align.CENTER
        text.color = 0xFF3E2723.toInt()
        fitText(c, "Chọn 1 món đi, nhóc! Hàng tốt cả đấy.", (bl + g.W - 330f) / 2, bt + 60f, 28f, g.W - 480f)
    }

    /** Workbench in a rest room: a table with tools under a lamp. */
    private fun drawBench(c: Canvas, x: Float, used: Boolean) {
        fill.color = 0xFF5D4037.toInt()
        c.drawRect(x - 130f, -90f, x + 130f, -76f, fill)
        c.drawRect(x - 120f, -76f, x - 106f, 0f, fill)
        c.drawRect(x + 106f, -76f, x + 120f, 0f, fill)
        fill.color = 0xFF455A64.toInt()
        c.drawRect(x - 120f, -240f, x + 120f, -96f, fill)
        stroke.color = 0xFFB0BEC5.toInt()
        stroke.strokeWidth = 5f
        for (k in 0..4) c.drawLine(x - 100f + k * 46f, -220f, x - 100f + k * 46f, -150f - (k % 2) * 20f, stroke)
        fill.color = 0xFFF9A825.toInt()
        c.drawRect(x - 100f, -120f, x - 30f, -90f, fill)
        fill.color = 0xFFC62828.toInt()
        c.drawRect(x + 20f, -126f, x + 100f, -90f, fill)
        // lamp
        stroke.color = 0xFF263238.toInt()
        stroke.strokeWidth = 5f
        c.drawLine(x + 110f, -96f, x + 140f, -200f, stroke)
        c.drawLine(x + 140f, -200f, x + 100f, -230f, stroke)
        fill.color = if (used) 0xFF616161.toInt() else 0xFFFFF59D.toInt()
        c.drawCircle(x + 96f, -222f, 14f, fill)
        if (!used) {
            fill.color = 0x33FFF59D
            path.reset()
            path.moveTo(x + 96f, -222f); path.lineTo(x - 140f, -90f); path.lineTo(x + 130f, -90f); path.close()
            c.drawPath(path, fill)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 26f
            text.color = 0xFFFFD740.toInt()
            c.drawText("🔧 BÀN ĐỒ NGHỀ", x, -270f + sin(g.anim * 3f) * 5f, text)
        }
    }

    /** Top runs by distance, kept on this phone. */
    private fun drawLeaderboard(c: Canvas, l: Float, t: Float, rr: Float, b: Float) {
        val s = g.save
        metalPanel(c, l, t, rr, b)
        text.textAlign = Paint.Align.CENTER
        text.color = 0xFFFFD740.toInt()
        fitText(c, "🏆 BẢNG XẾP HẠNG — CHẠY XA NHẤT", (l + rr) / 2, t + 38f, 24f, rr - l - 40f)
        val rows = s.records.take(5)
        if (rows.isEmpty()) {
            text.color = 0xFFBDBDBD.toInt()
            fitText(c, "Chưa có lượt chạy nào", (l + rr) / 2, t + 110f, 22f, rr - l - 40f)
            return
        }
        val step = (b - t - 58f) / 5f
        rows.forEachIndexed { i, row ->
            val y = t + 58f + step * i + step * 0.62f
            if (i % 2 == 0) {
                fill.color = 0x22FFFFFF
                c.drawRoundRect(l + 14f, y - step * 0.62f + 2f, rr - 14f, y + step * 0.38f - 2f, 8f, 8f, fill)
            }
            val medal = when (i) {
                0 -> 0xFFFFD740.toInt()
                1 -> 0xFFCFD8DC.toInt()
                2 -> 0xFFFFAB73.toInt()
                else -> 0xFF78909C.toInt()
            }
            fill.color = medal
            c.drawCircle(l + 40f, y - 7f, 15f, fill)
            text.textAlign = Paint.Align.CENTER
            text.color = 0xFF1A1A1A.toInt()
            text.textSize = 18f
            c.drawText("${i + 1}", l + 40f, y - 1f, text)
            text.textAlign = Paint.Align.LEFT
            text.color = Color.WHITE
            fitText(c, "${row[0]}m", l + 66f, y, 24f, 110f)
            text.color = 0xFFB0BEC5.toInt()
            fitText(c, "Ải ${row[1]} • ${Vehicles.of(row[2]).title} • ngày ${row[3]}", l + 180f, y - 1f, 18f, rr - l - 196f)
        }
    }

    private fun drawMaps(c: Canvas) {
        val W = g.W
        val s = g.save
        dim(c, 0xC8)
        title(c, "CHỌN MAP", W / 2, 70f, 0xFFFFD740.toInt(), 56f)
        for (i in 0 until Game.STAGES) {
            val th = Themes.all[i]
            val rc = g.mapCard(i)
            val l = rc[0]
            val t = rc[1]
            val w = rc[2] - rc[0]
            val h = rc[3] - rc[1]
            val unlocked = i + 1 <= g.maxStartStage()
            c.save()
            r.set(l, t, rc[2], rc[3])
            path.reset()
            path.addRoundRect(r, 16f, 16f, Path.Direction.CW)
            c.clipPath(path)
            // mini scene: sky bands, sun/moon, mountains, ground
            val bands = 6
            for (k in 0 until bands) {
                val f = k / (bands - 1f)
                fill.color = if (f < 0.5f) blend(th.sky[0], th.sky[1], f * 2f) else blend(th.sky[1], th.sky[2], (f - 0.5f) * 2f)
                c.drawRect(l, t + h * 0.65f * k / bands, rc[2], t + h * 0.65f * (k + 1) / bands + 1f, fill)
            }
            fill.color = th.sunColor
            c.drawCircle(l + w * 0.78f, t + h * 0.22f, 10f, fill)
            path.reset()
            path.moveTo(l, t + h * 0.7f)
            var px = 0f
            while (px <= w) {
                val hh = 18f + 14f * sin(px * 0.05f + i) + 8f * sin(px * 0.13f + i * 2)
                path.lineTo(l + px, t + h * 0.62f - hh)
                px += 8f
            }
            path.lineTo(rc[2], t + h * 0.7f)
            path.close()
            fill.color = th.mid[0]
            c.drawPath(path, fill)
            fill.color = th.ground[0]
            c.drawRect(l, t + h * 0.62f, rc[2], rc[3], fill)
            fill.color = th.crust[2]
            c.drawRect(l, t + h * 0.62f, rc[2], t + h * 0.62f + 3f, fill)
            // label strip
            fill.color = 0xAA000000.toInt()
            c.drawRect(l, rc[3] - 44f, rc[2], rc[3], fill)
            text.textAlign = Paint.Align.LEFT
            text.color = Color.WHITE
            fitText(c, "${i + 1}. ${th.name}", l + 10f, rc[3] - 24f, 20f, w - 20f)
            text.color = 0xFFFFE082.toInt()
            fitText(c, if (s.best[i + 1] > 0) "kỷ lục ${s.best[i + 1]}m" else "${g.stageGoalM(i + 1).toInt()}m", l + 10f, rc[3] - 6f, 16f, w - 20f)
            if ((i + 1) % 5 == 0) {
                text.textAlign = Paint.Align.RIGHT
                text.textSize = 26f
                c.drawText("👹", rc[2] - 8f, t + 30f, text)
            }
            if (i + 1 <= s.bestStage) {
                text.textAlign = Paint.Align.LEFT
                text.textSize = 22f
                text.color = 0xFF69F0AE.toInt()
                c.drawText("✔", l + 10f, t + 28f, text)
            }
            if (!unlocked) {
                fill.color = 0xB0000000.toInt()
                c.drawRect(l, t, rc[2], rc[3], fill)
                text.textAlign = Paint.Align.CENTER
                text.textSize = 34f
                text.color = Color.WHITE
                c.drawText("🔒", l + w / 2, t + h / 2 + 4f, text)
            }
            c.restore()
            r.set(l, t, rc[2], rc[3])
            stroke.color = if (s.stageSel == i + 1) 0xFF69F0AE.toInt() else 0x55FFFFFF
            stroke.strokeWidth = if (s.stageSel == i + 1) 5f else 2f
            c.drawRoundRect(r, 16f, 16f, stroke)
        }
        text.textAlign = Paint.Align.CENTER
        drawMenuButtons(c)
    }

    internal fun blend(a: Int, b: Int, t: Float): Int {
        val u = 1f - t
        return Color.argb(
            255,
            (((a shr 16) and 255) * u + ((b shr 16) and 255) * t).toInt(),
            (((a shr 8) and 255) * u + ((b shr 8) and 255) * t).toInt(),
            ((a and 255) * u + (b and 255) * t).toInt(),
        )
    }

    internal fun card(c: Canvas, rc: FloatArray, selected: Boolean) {
        metalPanel(c, rc[0], rc[1], rc[2], rc[3], 18f)
        r.set(rc[0], rc[1], rc[2], rc[3])
        if (selected) {
            stroke.color = 0xFF69F0AE.toInt()
            stroke.strokeWidth = 6f
            c.drawRoundRect(r, 20f, 20f, stroke)
        }
    }

    // ------------------------------------------------------------- story

    /** Word-wraps [s] into lines no wider than [maxW]; returns the y after the last line. */
    internal fun wrapText(c: Canvas, s: String, x: Float, y: Float, maxW: Float, size: Float, lineH: Float): Float {
        text.textSize = size
        var line = ""
        var yy = y
        for (w in s.split(" ")) {
            val tryLine = if (line.isEmpty()) w else "$line $w"
            if (text.measureText(tryLine) > maxW && line.isNotEmpty()) {
                c.drawText(line, x, yy, text)
                yy += lineH
                line = w
            } else {
                line = tryLine
            }
        }
        if (line.isNotEmpty()) {
            c.drawText(line, x, yy, text)
            yy += lineH
        }
        return yy
    }

    private fun drawStory(c: Canvas) {
        val W = g.W
        val H = g.H
        dim(c, 0xD0)
        val page = g.storyPages.getOrNull(g.storyIndex) ?: return
        val l = W * 0.08f
        val rr = W * 0.92f
        metalPanel(c, l, 30f, rr, H - 120f, 20f)
        title(c, page.title, W / 2, 104f, 0xFFFFD740.toInt(), 50f, rr - l - 80f)
        // fade the text in
        val a = (g.stateTime / 0.5f).coerceIn(0f, 1f)
        text.textAlign = Paint.Align.LEFT
        var y = 168f
        for ((i, para) in page.text.withIndex()) {
            val loud = para.endsWith("!") && para == para.uppercase()
            text.color = if (loud) 0xFFFF5252.toInt() else Color.WHITE
            text.alpha = (255 * a).toInt()
            y = wrapText(c, para, l + 50f, y, rr - l - 100f, if (loud) 38f else 29f, if (loud) 48f else 40f) + 14f
            if (i == 0) text.alpha = 255
        }
        text.alpha = 255
        // the player's war rig rolling along the bottom of the page
        val vx = l + 120f + ((g.anim * 60f) % (rr - l - 240f))
        art.draw(c, g.save.vehicleType, vx, H - 176f, 0f, g.anim * 4f, 0f, false, g.save.weaponType, false)
        text.textAlign = Paint.Align.LEFT
        text.textSize = 22f
        text.color = 0xFFB0BEC5.toInt()
        c.drawText("${g.storyIndex + 1}/${g.storyPages.size}   •   Chạm để tiếp tục", l + 20f, H - 50f, text)
        drawMenuButtons(c)
    }

    // ------------------------------------------------------------ garage

    /** Brick-walled workshop: pipes, hanging lamps, a roller door and a concrete floor. */
    private fun garageRoom(c: Canvas) {
        val W = g.W
        val H = g.H
        if (backdrop(c, "bg_garage")) {
            // AI picture of the workshop, darkened so the cards stay readable
            fill.color = 0x66000000
            c.drawRect(0f, 0f, W, H, fill)
            return
        }
        fill.color = 0xFF2A1F1A.toInt()
        c.drawRect(0f, 0f, W, H, fill)
        // bricks
        val bw = 64f
        val bh = 26f
        var row = 0
        var y = 70f
        while (y < H - 150f) {
            var x = if (row % 2 == 0) 0f else -bw / 2
            var k = 0
            while (x < W) {
                val h = hash(row * 131 + k)
                val base = 0xFF6B4636.toInt()
                fill.color = blend(base, if (h % 3 == 0) 0xFF4E342E.toInt() else 0xFF8D5A44.toInt(), (h % 100) / 260f)
                c.drawRect(x + 2f, y + 2f, x + bw - 2f, y + bh - 2f, fill)
                x += bw
                k++
            }
            y += bh
            row++
        }
        // grime and damp stains
        fill.color = 0x40000000
        c.drawRect(0f, 70f, W, 130f, fill)
        for (k in 0 until 7) {
            val h = hash(k * 71 + 3)
            fill.color = 0x30000000
            c.drawOval((h % 1000) / 1000f * W - 80f, 160f + (h % 300), (h % 1000) / 1000f * W + 90f, 260f + (h % 300), fill)
        }
        // pipes along the ceiling
        fill.color = 0xFF4A3B33.toInt()
        c.drawRect(0f, 0f, W, 70f, fill)
        fill.color = 0xFF7A5C45.toInt()
        c.drawRoundRect(0f, 78f, W, 100f, 10f, 10f, fill)
        fill.color = 0x55FFFFFF
        c.drawRect(0f, 80f, W, 85f, fill)
        for (k in 0 until 6) {
            fill.color = 0xFF3E2E25.toInt()
            c.drawRect(k * W / 6f + 40f, 74f, k * W / 6f + 52f, 104f, fill)
        }
        // roller door behind the vehicle
        val dx = g.showroomX()
        fill.color = 0xFF4E4038.toInt()
        c.drawRect(dx - 230f, 150f, dx + 230f, H - 150f, fill)
        stroke.color = 0x55000000
        stroke.strokeWidth = 3f
        var sy = 160f
        while (sy < H - 150f) {
            c.drawLine(dx - 226f, sy, dx + 226f, sy, stroke)
            sy += 22f
        }
        fill.color = 0x40834012
        c.drawOval(dx - 160f, 260f, dx - 40f, 340f, fill)
        c.drawOval(dx + 60f, 380f, dx + 190f, 450f, fill)
        stroke.color = 0xFF2B211C.toInt()
        stroke.strokeWidth = 10f
        c.drawRect(dx - 236f, 144f, dx + 236f, H - 150f, stroke)
        // floor
        fill.color = 0xFF3B2F27.toInt()
        c.drawRect(0f, H - 150f, W, H, fill)
        fill.color = 0x30FFFFFF
        c.drawRect(0f, H - 150f, W, H - 146f, fill)
        // hanging lamps and their light
        for (k in 0 until 4) {
            val lx = g.garageL + 80f + k * (W - g.garageL - 160f) / 3f
            stroke.color = 0xFF222222.toInt()
            stroke.strokeWidth = 2f
            c.drawLine(lx, 100f, lx, 128f, stroke)
            fill.color = 0x16FFE082
            path.reset()
            path.moveTo(lx - 16f, 136f); path.lineTo(lx + 16f, 136f); path.lineTo(lx + 150f, H - 150f); path.lineTo(lx - 150f, H - 150f); path.close()
            c.drawPath(path, fill)
            fill.color = 0xFF5D4E44.toInt()
            path.reset()
            path.moveTo(lx - 22f, 140f); path.lineTo(lx - 8f, 126f); path.lineTo(lx + 8f, 126f); path.lineTo(lx + 22f, 140f); path.close()
            c.drawPath(path, fill)
            fill.color = 0xFFFFF3C4.toInt()
            c.drawCircle(lx, 140f, 5f, fill)
        }
        // vignette
        fill.alpha = 255
        fill.shader = vignette
        c.drawRect(0f, 0f, W, H, fill)
        fill.shader = null
    }

    /** Round metal nav buttons down the left side: home, vehicles, weapons, skills. */
    internal fun garageNav(c: Canvas, home: Boolean = false) {
        for (i in 0..3) {
            val rc = g.navRect(i)
            val cx = (rc[0] + rc[2]) / 2
            val cy = (rc[1] + rc[3]) / 2
            val on = if (home) i == 0 else i > 0 && (g.garageTab == i - 1 || (i == 2 && g.garageTab == 3))
            val rad = (rc[2] - rc[0]) / 2
            fill.color = 0x88000000.toInt()
            c.drawCircle(cx + 3f, cy + 5f, rad, fill)
            fill.color = if (on) 0xFFC28A3C.toInt() else 0xFFB8B4AE.toInt()
            c.drawCircle(cx, cy, rad, fill)
            fill.color = if (on) 0xFF8A5A22.toInt() else 0xFF8D8983.toInt()
            c.drawCircle(cx, cy, rad - 10f, fill)
            fill.color = 0x40FFFFFF
            c.drawCircle(cx - 8f, cy - 12f, 30f, fill)
            stroke.color = 0xFF2B2622.toInt()
            stroke.strokeWidth = 3f
            c.drawCircle(cx, cy, rad, stroke)
            fill.color = Color.WHITE
            stroke.color = Color.WHITE
            stroke.strokeWidth = 5f
            val ir = rad * 0.6f
            if (spriteBox(c, Sprites.NAV[i], cx - ir, cy - ir + sin(g.anim * 2f + i) * 2f, cx + ir, cy + ir + sin(g.anim * 2f + i) * 2f)) continue
            when (i) {
                0 -> { // house
                    path.reset()
                    path.moveTo(cx - 24f, cy); path.lineTo(cx, cy - 22f); path.lineTo(cx + 24f, cy); path.close()
                    c.drawPath(path, fill)
                    c.drawRect(cx - 16f, cy, cx + 16f, cy + 22f, fill)
                    fill.color = 0xFF8D8983.toInt()
                    c.drawRect(cx - 5f, cy + 8f, cx + 5f, cy + 22f, fill)
                }
                1 -> { // car
                    c.drawRoundRect(cx - 28f, cy - 4f, cx + 28f, cy + 10f, 4f, 4f, fill)
                    c.drawRoundRect(cx - 16f, cy - 16f, cx + 12f, cy, 4f, 4f, fill)
                    fill.color = if (on) 0xFF8A5A22.toInt() else 0xFF8D8983.toInt()
                    c.drawCircle(cx - 15f, cy + 12f, 8f, fill)
                    c.drawCircle(cx + 15f, cy + 12f, 8f, fill)
                    fill.color = Color.WHITE
                    c.drawCircle(cx - 15f, cy + 12f, 5f, fill)
                    c.drawCircle(cx + 15f, cy + 12f, 5f, fill)
                }
                2 -> { // gun
                    c.drawRect(cx - 26f, cy - 8f, cx + 22f, cy + 2f, fill)
                    c.drawRect(cx - 22f, cy, cx - 10f, cy + 18f, fill)
                    c.drawRect(cx + 18f, cy - 6f, cx + 30f, cy - 2f, fill)
                }
                4 -> { // hand weapons: a bow over a spear
                    c.save()
                    c.translate(cx - 20f, cy + 18f)
                    c.rotate(-40f)
                    handWeapon(c, SideWeapon.SPEAR, 0.55f)
                    c.restore()
                    stroke.color = Color.WHITE
                    stroke.strokeWidth = 4f
                    r.set(cx - 6f, cy - 24f, cx + 26f, cy + 24f)
                    c.drawArc(r, -90f, 180f, false, stroke)
                    stroke.strokeWidth = 2f
                    c.drawLine(cx + 10f, cy - 24f, cx + 10f, cy + 24f, stroke)
                }
                else -> { // skill tree: star over three boxes
                    star(c, cx, cy - 12f, 13f, Color.WHITE)
                    for (k in -1..1) c.drawRoundRect(cx + k * 18f - 7f, cy + 10f, cx + k * 18f + 7f, cy + 22f, 3f, 3f, fill)
                    c.drawLine(cx - 18f, cy + 6f, cx + 18f, cy + 6f, stroke)
                }
            }
        }
    }

    internal fun star(c: Canvas, x: Float, y: Float, r0: Float, color: Int) {
        path.reset()
        for (k in 0 until 10) {
            val rr = if (k % 2 == 0) r0 else r0 * 0.45f
            val a = -PI.toFloat() / 2 + k * PI.toFloat() / 5
            if (k == 0) path.moveTo(x + cos(a) * rr, y + sin(a) * rr) else path.lineTo(x + cos(a) * rr, y + sin(a) * rr)
        }
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }

    /** Player rank star with an XP bar, and the money / gem counters. */
    internal fun garageTopBar(c: Canvas) {
        val W = g.W
        val s = g.save
        val (lv, into, need) = Rank.of(s.xp)
        val l = g.garageL - 10f
        fill.color = 0xCC1B1714.toInt()
        c.drawRoundRect(l + 40f, 26f, l + 320f, 62f, 12f, 12f, fill)
        fill.color = 0xFF1E88E5.toInt()
        c.drawRoundRect(l + 44f, 30f, l + 44f + 272f * (into.toFloat() / need), 58f, 10f, 10f, fill)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        text.textSize = 22f
        c.drawText("$into/$need XP", l + 190f, 52f, text)
        star(c, l + 30f, 44f, 38f, 0xFFFFC107.toInt())
        star(c, l + 30f, 44f, 28f, 0xFF1565C0.toInt())
        text.textSize = 26f
        c.drawText("$lv", l + 30f, 54f, text)
        // resources
        fill.color = 0xCC1B1714.toInt()
        c.drawRoundRect(W - 470f, 22f, W - 24f, 66f, 14f, 14f, fill)
        drawGem(c, W - 440f, 44f, 0.7f)
        text.textAlign = Paint.Align.LEFT
        text.textSize = 28f
        text.color = 0xFFFF8A80.toInt()
        c.drawText("${s.gems}", W - 416f, 54f, text)
        fill.color = 0xFF43A047.toInt()
        c.drawRoundRect(W - 250f, 32f, W - 214f, 56f, 4f, 4f, fill)
        fill.color = 0xFF2E7D32.toInt()
        c.drawCircle(W - 232f, 44f, 7f, fill)
        text.color = 0xFFFFD740.toInt()
        c.drawText("${s.money}$", W - 204f, 54f, text)
    }

    /** Drag the controls to new places over the live road. */
    private fun drawLayout(c: Canvas) {
        dim(c, 0x70)
        title(c, "KÉO PHÍM ĐỂ ĐỔI VỊ TRÍ", g.W / 2, 92f, 0xFFFFD740.toInt(), 46f)
        for (b in g.layoutButtons()) drawButton(c, b, false)
        text.textAlign = Paint.Align.CENTER
        text.color = 0xFFE0E0E0.toInt()
        fitText(c, "Ô trái/phải: nghiêng xe • khi đi bộ: cần gạt ở nửa trái màn hình • tên lửa = NHẢY • GA = VÀO NHÀ/LÊN XE", g.W / 2, 236f, 22f, g.W - 80f)
        drawMenuButtons(c)
    }

    private fun drawGarage(c: Canvas) {
        garageRoom(c)
        when (g.garageTab) {
            0 -> garageVehicles(c)
            1 -> garageWeapons(c)
            3 -> garageSide(c)
            else -> garageSkills(c)
        }
        garageNav(c)
        garageTopBar(c)
        drawMenuButtons(c)
    }

    private fun lockedLayer(c: Canvas, locked: Boolean) {
        if (locked) c.saveLayerAlpha(null, 80) else c.save()
    }

    /** The showroom: one vehicle on the floor, arrows to browse, family chips, stats. */
    private fun garageVehicles(c: Canvas) {
        val s = g.save
        val W = g.W
        val H = g.H
        val v = Vehicles.of(g.showVeh)
        val cur = s.vehicleType
        val unlocked = s.classUnlocked(v.cls)
        val owned = s.ownsVehicle(v.id)
        val cx = g.showroomX()
        // name banner
        fill.color = 0xDD141210.toInt()
        c.drawRoundRect(cx - 280f, 118f, cx + 280f, 178f, 14f, 14f, fill)
        title(c, v.title, cx, 164f, Color.WHITE, 42f, 520f)
        val status = when {
            s.vehicle == v.id -> "Đang dùng"
            owned -> "Đã có"
            else -> "Khóa"
        }
        text.textAlign = Paint.Align.CENTER
        text.color = if (owned) 0xFF69F0AE.toInt() else Color.WHITE
        fitText(c, status, cx, 222f, 34f, 400f)
        // floor spot and the vehicle
        val floorY = H - 160f
        fill.color = 0x40000000
        c.drawOval(cx - 230f, floorY - 14f, cx + 230f, floorY + 14f, fill)
        c.save()
        c.translate(cx, floorY - 54f * 1.55f)
        c.scale(1.55f, 1.55f)
        lockedLayer(c, !owned)
        art.draw(c, v, 0f, 0f, 0f, g.anim * 1.5f, -0.2f, false, s.weaponType, false)
        c.restore()
        c.restore()
        if (!owned) {
            // padlock over a vehicle not yet built
            val ly = floorY - 110f
            fill.color = 0xFF2B2B2B.toInt()
            stroke.color = 0xFF9E9E9E.toInt()
            stroke.strokeWidth = 9f
            r.set(cx - 24f, ly - 54f, cx + 24f, ly)
            c.drawArc(r, 180f, 180f, false, stroke)
            fill.color = 0xFF8D8983.toInt()
            c.drawRoundRect(cx - 38f, ly - 28f, cx + 38f, ly + 34f, 8f, 8f, fill)
            fill.color = 0xFF2B2B2B.toInt()
            c.drawCircle(cx, ly, 8f, fill)
            c.drawRect(cx - 3f, ly, cx + 3f, ly + 18f, fill)
        }
        // browse arrows
        for (right in listOf(false, true)) {
            val rc = g.arrowRect(right)
            val ax = (rc[0] + rc[2]) / 2
            val ay = (rc[1] + rc[3]) / 2
            fill.color = 0xAA000000.toInt()
            c.drawCircle(ax, ay, 40f, fill)
            fill.color = Color.WHITE
            path.reset()
            val d = if (right) 1f else -1f
            path.moveTo(ax - 10f * d, ay - 20f); path.lineTo(ax + 16f * d, ay); path.lineTo(ax - 10f * d, ay + 20f); path.close()
            c.drawPath(path, fill)
        }
        // how to get it
        val how = when {
            owned -> "${v.cls.title} • phiên bản ${v.variant + 1}/${Vehicles.PER_CLASS}"
            !unlocked -> "Mở dòng ${v.cls.title} khi qua ải ${v.cls.unlockStage}"
            !s.hasAllParts(v) -> "Tìm xe này bằng cách lục soát nhà (${s.parts[v.id]}/${SaveData.PARTS_NEEDED} mảnh)"
            else -> "Đủ mảnh! Lắp ráp với ${v.cost}$"
        }
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        text.style = Paint.Style.STROKE
        text.strokeWidth = 5f
        text.color = 0xCC000000.toInt()
        fitText(c, how, cx, H - 122f, 26f, W - 420f - g.garageL)
        text.style = Paint.Style.FILL
        text.color = if (!owned && unlocked) 0xFFFFE082.toInt() else Color.WHITE
        fitText(c, how, cx, H - 122f, 26f, W - 420f - g.garageL)
        // family chips
        for (cl in VehicleClass.entries) {
            val rc = g.classChip(cl.ordinal)
            val on = v.cls == cl
            val open = s.classUnlocked(cl)
            fill.color = if (on) 0xEE8A5A22.toInt() else 0xCC1B1714.toInt()
            c.drawRoundRect(rc[0], rc[1], rc[2], rc[3], 14f, 14f, fill)
            c.save()
            c.translate((rc[0] + rc[2]) / 2, rc[1] + 34f)
            c.scale(0.3f, 0.3f)
            lockedLayer(c, !open)
            art.draw(c, Vehicles.of(cl, 0), 0f, 0f, 0f, 0f, 0f, false, WeaponType.PISTOL, false)
            c.restore()
            c.restore()
            text.textAlign = Paint.Align.CENTER
            text.color = if (open) Color.WHITE else 0xFF9E9E9E.toInt()
            fitText(c, if (open) cl.title else "🔒 ${cl.title}", (rc[0] + rc[2]) / 2, rc[3] - 10f, 20f, rc[2] - rc[0] - 10f)
        }
        // stats
        val pl = W - 370f
        metalPanel(c, pl, 92f, W - 24f, H - 204f, 16f)
        text.textAlign = Paint.Align.LEFT
        text.color = 0xFFFFD740.toInt()
        fitText(c, "CHỈ SỐ", pl + 24f, 128f, 24f, 300f)
        val rows = listOf(
            Triple("Máu", v.maxHp / 850f to cur.maxHp / 850f, "${v.maxHp.toInt()}"),
            Triple("Tốc độ", v.topSpeed / 1450f to cur.topSpeed / 1450f, "${(v.topSpeed / Game.METER * 3.6f).toInt()} km/h"),
            Triple("Lực kéo", v.engineAcc / 1800f to cur.engineAcc / 1800f, "${(v.engineAcc / 5.2f).toInt()}%"),
            Triple("Giáp", v.totalArmor / 0.7f to cur.totalArmor / 0.7f, "${(v.totalArmor * 100).toInt()}%"),
            Triple("Xăng", v.fuelTime / 80f to cur.fuelTime / 80f, "${v.fuelTime.toInt()}s"),
            Triple("Bám", v.traction / 2.4f to cur.traction / 2.4f, "${(v.traction * 100).toInt()}%"),
            Triple("Tên lửa", v.boostTime / 7.5f to cur.boostTime / 7.5f, "%.1fs".format(v.boostTime)),
            Triple("Đạn", v.ammo / 120f to cur.ammo / 120f, "${v.ammo}"),
            Triple("Sức húc", v.ram / 2.9f to cur.ram / 2.9f, "${(v.ram * 100).toInt()}%"),
        )
        statRows(c, rows, pl + 22f, W - 44f, 158f, (H - 204f - 180f) / 9f, 100f, 74f)
    }

    /** Horizontal skill line like a workshop board: stat nodes on top, flame perks below. */
    private fun garageSkills(c: Canvas) {
        val s = g.save
        val W = g.W
        val H = g.H
        val bits = s.skills
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        title(c, "CÂY KỸ NĂNG — VÒNG ${g.skillPage + 1}/${Skills.ROUNDS}", (g.garageL + W) / 2, 132f, 0xFFFFD740.toInt(), 40f, W - g.garageL - 60f)
        val page = Skills.nodes.filter { it.page == g.skillPage }
        // links first
        for (n in page) {
            val rc = g.skillRect(n)
            val cx = (rc[0] + rc[2]) / 2
            if (n.perk != null) {
                val p = Skills.nodes[n.parent]
                val pr = g.skillRect(p)
                stroke.color = if (Skills.owned(bits, n.parent)) 0xFF4FC3F7.toInt() else 0xFF2B2B2B.toInt()
                stroke.strokeWidth = 10f
                c.drawLine(cx, pr[3], cx, rc[1], stroke)
            } else if (n.slot < 6) {
                val nx = Skills.nodes[n.id + 1]
                val nr = g.skillRect(nx)
                stroke.color = if (Skills.owned(bits, nx.id)) 0xFF4FC3F7.toInt() else 0xFF2B2B2B.toInt()
                stroke.strokeWidth = 14f
                c.drawLine(rc[2], (rc[1] + rc[3]) / 2, nr[0], (rc[1] + rc[3]) / 2, stroke)
            }
        }
        for (n in page) {
            val rc = g.skillRect(n)
            val cx = (rc[0] + rc[2]) / 2
            val cy = (rc[1] + rc[3]) / 2
            val owned = Skills.owned(bits, n.id)
            val avail = Skills.available(bits, n)
            val base = when {
                n.perk != null && owned -> 0xFFFF7043.toInt()
                n.stat != null && owned -> n.stat.color
                else -> 0xFFB0ACA6.toInt()
            }
            fill.color = 0x88000000.toInt()
            c.drawRoundRect(rc[0] + 4f, rc[1] + 6f, rc[2] + 4f, rc[3] + 6f, 16f, 16f, fill)
            fill.color = 0xFF2B2622.toInt()
            c.drawRoundRect(rc[0], rc[1], rc[2], rc[3], 16f, 16f, fill)
            fill.color = base
            c.drawRoundRect(rc[0] + 6f, rc[1] + 6f, rc[2] - 6f, rc[3] - 6f, 12f, 12f, fill)
            fill.color = 0x38FFFFFF
            c.drawRoundRect(rc[0] + 8f, rc[1] + 8f, rc[2] - 8f, cy - 4f, 10f, 10f, fill)
            if (g.skillSel == n.id) {
                stroke.color = 0xFFFFEB3B.toInt()
                stroke.strokeWidth = 5f
                c.drawRoundRect(rc[0] - 5f, rc[1] - 5f, rc[2] + 5f, rc[3] + 5f, 18f, 18f, stroke)
            }
            skillIcon(c, n, cx, cy, if (owned) Color.WHITE else 0xFF5C5853.toInt())
            if (avail) {
                // a red arrow marks what can be opened next
                fill.color = 0xFFE53935.toInt()
                path.reset()
                path.moveTo(rc[2] - 22f, rc[1] - 2f); path.lineTo(rc[2] - 4f, rc[1] - 22f); path.lineTo(rc[2] + 14f, rc[1] - 2f); path.close()
                c.drawPath(path, fill)
                c.drawRect(rc[2] - 10f, rc[1] - 4f, rc[2] + 2f, rc[1] + 8f, fill)
            }
            text.textAlign = Paint.Align.CENTER
            if (n.stat != null) {
                text.style = Paint.Style.STROKE
                text.strokeWidth = 6f
                text.color = 0xFF000000.toInt()
                fitText(c, Skills.label(n), cx, rc[1] - 14f, 32f, 160f)
                text.style = Paint.Style.FILL
                text.color = Color.WHITE
                fitText(c, Skills.label(n), cx, rc[1] - 14f, 32f, 160f)
            } else {
                fill.color = 0xFF111111.toInt()
                c.drawRoundRect(cx - 64f, rc[1] - 46f, cx + 64f, rc[1] - 8f, 8f, 8f, fill)
                text.color = Color.WHITE
                fitText(c, n.perk!!.title, cx, rc[1] - 19f, 22f, 120f)
            }
        }
        // page arrows
        for (right in listOf(false, true)) {
            if ((right && g.skillPage >= Skills.ROUNDS - 1) || (!right && g.skillPage == 0)) continue
            val rc = g.skillArrow(right)
            val ax = (rc[0] + rc[2]) / 2
            val ay = (rc[1] + rc[3]) / 2
            fill.color = 0xAA000000.toInt()
            c.drawCircle(ax, ay, 34f, fill)
            fill.color = Color.WHITE
            val d = if (right) 1f else -1f
            path.reset()
            path.moveTo(ax - 8f * d, ay - 16f); path.lineTo(ax + 14f * d, ay); path.lineTo(ax - 8f * d, ay + 16f); path.close()
            c.drawPath(path, fill)
        }
        // description of the selected node
        val n = Skills.nodes[g.skillSel]
        metalPanel(c, g.garageL, H - 196f, W - 390f, H - 22f, 16f)
        text.textAlign = Paint.Align.LEFT
        text.color = 0xFFFFD740.toInt()
        val head = n.perk?.title ?: "${n.stat!!.title} ${Skills.label(n)}"
        fitText(c, head.uppercase(), g.garageL + 26f, H - 148f, 30f, W - 440f - g.garageL)
        text.color = Color.WHITE
        val desc = n.perk?.desc ?: when (n.stat!!) {
            SkillStat.BOOST -> "Thêm ${Skills.label(n)} tên lửa đẩy cho mọi xe"
            SkillStat.HP -> "Thêm ${Skills.label(n)} máu cho mọi xe"
            SkillStat.ARMOR -> "Thêm ${Skills.label(n)} giáp"
            SkillStat.ENGINE -> "Động cơ mạnh hơn: tốc độ và lực kéo ${Skills.label(n)}"
            SkillStat.FUEL -> "Bình xăng lớn hơn ${Skills.label(n)}"
            SkillStat.AMMO -> "Thêm ${Skills.label(n)} viên đạn mỗi lượt"
            SkillStat.GRIP -> "Lốp bám đường hơn ${Skills.label(n)}"
        }
        fitText(c, desc, g.garageL + 26f, H - 108f, 24f, W - 440f - g.garageL)
        text.color = 0xFFB0BEC5.toInt()
        fitText(c, "Đã mở ${Skills.count(bits)}/${Skills.nodes.size} nút • mỗi nút sau cần nhiều ngọc hơn 20%", g.garageL + 26f, H - 66f, 20f, W - 440f - g.garageL)
    }

    private fun skillIcon(c: Canvas, n: SkillNode, x: Float, y: Float, col: Int) {
        fill.color = col
        stroke.color = col
        stroke.strokeWidth = 6f
        if (n.perk != null) {
            // flame
            path.reset()
            path.moveTo(x, y - 34f)
            path.quadTo(x + 30f, y - 4f, x + 22f, y + 18f)
            path.quadTo(x + 12f, y + 34f, x, y + 32f)
            path.quadTo(x - 22f, y + 32f, x - 24f, y + 12f)
            path.quadTo(x - 26f, y - 8f, x - 8f, y - 16f)
            path.quadTo(x - 4f, y - 4f, x + 2f, y - 2f)
            path.quadTo(x + 6f, y - 18f, x, y - 34f)
            path.close()
            c.drawPath(path, fill)
            return
        }
        when (n.stat!!) {
            SkillStat.HP -> {
                c.drawCircle(x - 12f, y - 8f, 14f, fill)
                c.drawCircle(x + 12f, y - 8f, 14f, fill)
                path.reset()
                path.moveTo(x - 26f, y - 2f); path.lineTo(x, y + 26f); path.lineTo(x + 26f, y - 2f); path.close()
                c.drawPath(path, fill)
            }
            SkillStat.ARMOR -> {
                path.reset()
                path.moveTo(x - 24f, y - 24f); path.lineTo(x + 24f, y - 24f); path.lineTo(x + 22f, y + 4f)
                path.quadTo(x + 14f, y + 22f, x, y + 30f); path.quadTo(x - 14f, y + 22f, x - 22f, y + 4f); path.close()
                c.drawPath(path, stroke)
                c.drawLine(x, y - 24f, x, y + 28f, stroke)
            }
            SkillStat.BOOST -> {
                c.save()
                c.translate(x, y)
                c.rotate(-35f)
                c.drawRoundRect(-26f, -10f, 18f, 10f, 10f, 10f, fill)
                path.reset()
                path.moveTo(18f, -10f); path.lineTo(32f, 0f); path.lineTo(18f, 10f); path.close()
                c.drawPath(path, fill)
                path.reset()
                path.moveTo(-26f, -6f); path.lineTo(-40f, 0f); path.lineTo(-26f, 6f); path.close()
                c.drawPath(path, fill)
                c.restore()
            }
            SkillStat.ENGINE -> { // lightning bolt
                path.reset()
                path.moveTo(x + 6f, y - 30f); path.lineTo(x - 18f, y + 4f); path.lineTo(x - 2f, y + 4f)
                path.lineTo(x - 8f, y + 30f); path.lineTo(x + 18f, y - 6f); path.lineTo(x + 2f, y - 6f); path.close()
                c.drawPath(path, fill)
            }
            SkillStat.FUEL -> { // jerrycan
                c.drawRoundRect(x - 18f, y - 20f, x + 18f, y + 26f, 6f, 6f, fill)
                c.drawRect(x + 6f, y - 30f, x + 16f, y - 20f, fill)
            }
            SkillStat.AMMO -> { // bullets
                for (k in -1..1) {
                    c.drawRoundRect(x + k * 14f - 5f, y - 16f, x + k * 14f + 5f, y + 24f, 4f, 4f, fill)
                    c.drawCircle(x + k * 14f, y - 18f, 5f, fill)
                }
            }
            SkillStat.GRIP -> { // tyre
                c.drawCircle(x, y, 22f, stroke)
                c.drawCircle(x, y, 8f, fill)
                for (k in 0 until 8) {
                    val a = k * 0.785f
                    c.drawCircle(x + cos(a) * 26f, y + sin(a) * 26f, 4f, fill)
                }
            }
        }
    }

    /** Label, bar (value and a white tick for what is in use now) and value text. */
    internal fun statRows(
        c: Canvas,
        rows: List<Triple<String, Pair<Float, Float>, String>>,
        x0: Float,
        x1: Float,
        y0: Float,
        step: Float,
        labelW: Float = 140f,
        valueW: Float = 150f,
    ) {
        val bx0 = x0 + labelW
        val bx1 = x1 - valueW
        rows.forEachIndexed { i, (label, frac, value) ->
            val y = y0 + i * step
            text.textAlign = Paint.Align.LEFT
            text.textSize = if (step < 23f) 17f else 19f
            text.color = 0xFFB0BEC5.toInt()
            c.drawText(label, x0, y + 6f, text)
            fill.color = 0xFF37474F.toInt()
            c.drawRoundRect(bx0, y - 6f, bx1, y + 6f, 6f, 6f, fill)
            val f = frac.first.coerceIn(0.03f, 1f)
            val old = frac.second.coerceIn(0.03f, 1f)
            fill.color = when {
                f > old + 0.005f -> 0xFF69F0AE.toInt()
                f < old - 0.005f -> 0xFFFF7043.toInt()
                else -> 0xFF4FC3F7.toInt()
            }
            c.drawRoundRect(bx0, y - 6f, bx0 + (bx1 - bx0) * f, y + 6f, 6f, 6f, fill)
            fill.color = 0x40FFFFFF
            c.drawRoundRect(bx0, y - 6f, bx0 + (bx1 - bx0) * f, y - 1f, 4f, 4f, fill)
            stroke.color = Color.WHITE
            stroke.strokeWidth = 3f
            val ox = bx0 + (bx1 - bx0) * old
            c.drawLine(ox, y - 9f, ox, y + 9f, stroke)
            text.textAlign = Paint.Align.RIGHT
            text.color = Color.WHITE
            c.drawText(value, x1, y + 6f, text)
        }
    }

    private fun garageWeapons(c: Canvas) {
        val s = g.save
        for (w in WeaponType.entries) {
            val rc = g.weaponRect(w.ordinal)
            val unlocked = s.weaponUnlocked(w)
            val owned = s.ownsWeapon(w.ordinal)
            card(c, rc, g.garageWeapon == w)
            val cx = (rc[0] + rc[2]) / 2
            lockedLayer(c, !unlocked)
            val bob = if (g.garageWeapon == w) sin(g.anim * 3f) * 3f else 0f
            if (!spriteBox(c, Sprites.weapon(w), rc[0] + 18f, rc[1] + 10f + bob, rc[2] - 18f, rc[1] + 70f + bob)) {
                c.translate(cx - 10f, rc[1] + 48f)
                c.scale(1.25f, 1.25f)
                art.weaponArt(c, w)
            }
            c.restore()
            text.textAlign = Paint.Align.CENTER
            text.color = if (unlocked) Color.WHITE else 0xFF9E9E9E.toInt()
            fitText(c, w.title, cx, rc[1] + 92f, 22f, rc[2] - rc[0] - 14f)
            val (label, color) = when {
                s.weapon == w.ordinal -> "ĐANG DÙNG • C${s.weaponLevel(w) + 1}" to 0xFF69F0AE.toInt()
                owned -> "CẤP ${s.weaponLevel(w) + 1}" to Color.WHITE
                !unlocked -> "🔒 Ải ${w.unlockStage}" to 0xFF9E9E9E.toInt()
                s.money >= w.cost -> "${w.cost}$" to 0xFFFFD740.toInt()
                else -> "${w.cost}$" to 0xFF9E9E9E.toInt()
            }
            text.color = color
            fitText(c, label, cx, rc[3] - 10f, 19f, rc[2] - rc[0] - 14f)
        }

        val w = g.garageWeapon
        val pr = g.weaponPanelRect()
        card(c, pr, false)
        val unlocked = s.weaponUnlocked(w)
        val owned = s.ownsWeapon(w.ordinal)
        lockedLayer(c, !unlocked)
        val bob = sin(g.anim * 2f) * 4f
        if (!spriteBox(c, Sprites.weapon(w), pr[0] + 20f, pr[1] + 20f + bob, pr[0] + 230f, pr[1] + 200f + bob)) {
            c.translate(pr[0] + 110f, pr[1] + 110f)
            c.scale(2.3f, 2.3f)
            art.weaponArt(c, w)
        }
        c.restore()
        val tx = pr[0] + 250f
        text.textAlign = Paint.Align.LEFT
        text.color = Color.WHITE
        fitText(c, w.title.uppercase(), tx, pr[1] + 44f, 34f, 420f)
        text.color = 0xFFB0BEC5.toInt()
        fitText(c, w.desc, tx, pr[1] + 74f, 21f, pr[2] - 360f - tx)
        val lv = s.weaponLevel(w)
        for (i in 0 until WeaponType.MAX_LEVEL) {
            fill.color = if (owned && i < lv) 0xFFE040FB.toInt() else 0xFF455A64.toInt()
            c.drawRoundRect(tx + i * 34f, pr[1] + 88f, tx + i * 34f + 28f, pr[1] + 100f, 4f, 4f, fill)
        }
        text.color = 0xFFE1BEE7.toInt()
        text.textSize = 18f
        c.drawText("Cấp ${lv + 1}/${WeaponType.MAX_LEVEL + 1}", tx + WeaponType.MAX_LEVEL * 34f + 10f, pr[1] + 100f, text)
        val st = w.stats(lv)
        val nx = if (owned && lv < WeaponType.MAX_LEVEL) w.stats(lv + 1) else null
        fun dps(x: WeaponStats) = x.damage * w.pellets
        fun fmt(f: Float) = if (f >= 10f) f.toInt().toString() else "%.1f".format(f)
        val rows = listOf(
            Triple("Sát thương", kotlin.math.sqrt(dps(st) / 14f) to 0f, fmt(dps(st)) + (nx?.let { " → " + fmt(dps(it)) } ?: "")),
            Triple("Tốc bắn", kotlin.math.sqrt((1f / st.interval) / 20f) to 0f, fmt(1f / st.interval) + "/giây" + (nx?.let { " → " + fmt(1f / it.interval) } ?: "")),
            Triple("Tầm bắn", st.range / 1250f to 0f, "${(st.range / Game.METER).toInt()}m" + (nx?.let { " → ${(it.range / Game.METER).toInt()}m" } ?: "")),
            Triple("Xuyên", min(1f, st.pierce / 5f) to 0f, if (st.pierce >= 99) "∞" else "${st.pierce}"),
            Triple("Nổ", st.splash / 260f to 0f, if (st.splash > 0f) "${(st.splash / Game.METER).toInt()}m" else "—"),
        )
        statRows(c, rows.map { (a, f, v) -> Triple(a, f.first to f.first, v) }, tx, pr[2] - 350f, pr[1] + 128f, 20f)
    }

    private fun drawOverlay(c: Canvas) {
        val W = g.W
        val H = g.H
        val tiles = listOf(
            Triple("📏", "${g.maxDist.toInt()}m", "quãng đường"),
            Triple("🧟", "${g.kills}", "zombie"),
            Triple("💥", "${g.smashed}", "vật cản"),
            Triple("💰", "+${g.distMoney + g.runMoney + g.stageBonus}$", "tiền"),
            Triple("♦", "+${g.runGems}", "ngọc đỏ"),
            Triple("🔧", "+${g.runParts}", "mảnh xe"),
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
                val unlock = ArrayList<String>()
                if (g.stage == 5) unlock.add("🔓 Mở khóa 👥 Nhân bản")
                VehicleClass.entries.filter { it.unlockStage == g.stage }.forEach { unlock.add("🔓 ${it.title.uppercase()}") }
                WeaponType.entries.filter { it.unlockStage == g.stage }.forEach { unlock.add("🔓 ${it.title}") }
                resultPanel(
                    c, "VƯỢT ẢI ${g.stage}!", 0xFFFFD740.toInt(),
                    "Đã tới trạm cứu hộ!", 0xFF69F0AE.toInt(), tiles, unlock,
                )
            }
            State.VICTORY -> resultPanel(
                c, "CHIẾN THẮNG!", 0xFFFFD740.toInt(),
                "Bạn đã sống sót qua cả ${Game.STAGES} ải!", 0xFF69F0AE.toInt(), tiles, listOf("👑 Huyền thoại sinh tồn", "🔓 XE TĂNG"),
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


    private val topPath = Path()


    private fun ensureShaders() {
        val th = g.theme
        if (bgW == g.W && bgH == g.H && shaderStage == th.stage) return
        bgW = g.W
        bgH = g.H
        shaderStage = th.stage
        val W = g.W
        val H = g.H
        skyShader = LinearGradient(
            0f, 0f, 0f, H * 0.85f, th.sky, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        val sc = th.sunColor and 0xFFFFFF
        sunShader = RadialGradient(
            W * 0.78f, H * th.sunY, if (th.moon) 200f else 260f,
            intArrayOf(0xAA000000.toInt() or sc, 0x55000000 or sc, sc),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP,
        )
        farShader = LinearGradient(0f, H * 0.3f, 0f, H * 0.7f, th.far[0], th.far[1], Shader.TileMode.CLAMP)
        midShader = LinearGradient(0f, H * 0.45f, 0f, H * 0.85f, th.mid[0], th.mid[1], Shader.TileMode.CLAMP)
        groundShader = LinearGradient(
            0f, Terrain.BASE_Y - 260f, 0f, Terrain.BASE_Y + 560f, th.ground,
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
        )
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

    internal fun hash(k: Int): Int = ((k * 73856093) xor ((k shr 2) * 19349663) xor 0x5bd1e995) and 0x7fffffff


    private fun cloud(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        fill.color = 0x22000000
        c.drawOval(x - 80f * s, y - 6f * s, x + 90f * s, y + 30f * s, fill)
        fill.color = color
        c.drawOval(x - 90f * s, y - 20f * s, x + 90f * s, y + 22f * s, fill)
        c.drawCircle(x - 30f * s, y - 22f * s, 32f * s, fill)
        c.drawCircle(x + 25f * s, y - 30f * s, 40f * s, fill)
        fill.color = 0x44FFFFFF
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


    internal fun drawDamageOverlay(c: Canvas) {
        val car = g.car
        var a = 0f
        if (g.hurtFlash > 0f) a = min(1f, g.hurtFlash * 2.2f) * 0.85f
        if (car.hp < car.maxHp * 0.25f && !g.wrecked && g.rooms == null && g.state == State.PLAYING) {
            a = max(a, 0.25f + 0.2f * sin(g.anim * 6f))
        }
        if (a <= 0f) return
        fill.shader = hurtShader
        fill.alpha = (255 * a).toInt().coerceIn(0, 255)
        c.drawRect(0f, 0f, g.W, g.H, fill)
        fill.shader = null
        fill.alpha = 255
    }


    internal fun glassPanel(c: Canvas, l: Float, t: Float, rr: Float, b: Float, radius: Float = 18f) =
        metalPanel(c, l, t, rr, b, min(radius, 16f))

    // --------------------------------------------------- rusty metal look

    private val platePath = Path()

    internal fun rivet(c: Canvas, x: Float, y: Float, rad: Float = 4f) {
        fill.color = 0x88000000.toInt()
        c.drawCircle(x + 1f, y + 1.5f, rad, fill)
        fill.color = 0xFF9A9188.toInt()
        c.drawCircle(x, y, rad, fill)
        fill.color = 0xFFDDD5CA.toInt()
        c.drawCircle(x - rad * 0.3f, y - rad * 0.3f, rad * 0.38f, fill)
    }

    /** A worn steel plate with rust stains, scratches and a dark rim. */
    private fun metalPlate(c: Canvas, l: Float, t: Float, rr: Float, b: Float, radius: Float, base: Int = 0xFF55504A.toInt()) {
        r.set(l, t, rr, b)
        fill.color = 0x66000000
        c.drawRoundRect(l + 3f, t + 5f, rr + 3f, b + 5f, radius, radius, fill)
        fill.color = base
        c.drawRoundRect(r, radius, radius, fill)
        platePath.reset()
        platePath.addRoundRect(r, radius, radius, Path.Direction.CW)
        c.save()
        c.clipPath(platePath)
        fill.color = 0x2EFFFFFF
        c.drawRect(l, t, rr, t + (b - t) * 0.35f, fill)
        fill.color = 0x38000000
        c.drawRect(l, b - (b - t) * 0.3f, rr, b, fill)
        val seed = (l * 7f + t * 13f).toInt()
        for (k in 0 until 6) {
            val h = hash(seed + k * 31)
            val x = l + (h % 1000) / 1000f * (rr - l)
            val y = t + ((h / 1000) % 1000) / 1000f * (b - t)
            val w = 10f + (h % 34)
            fill.color = if (k % 2 == 0) 0x70834012 else 0x48A0522D
            c.drawOval(x - w, y - w * 0.45f, x + w, y + w * 0.45f, fill)
            fill.color = 0x40301808
            c.drawCircle(x + w * 0.3f, y, w * 0.18f, fill)
        }
        stroke.color = 0x24FFFFFF
        stroke.strokeWidth = 1.5f
        for (k in 0 until 3) {
            val h = hash(seed * 3 + k)
            val x = l + (h % 1000) / 1000f * (rr - l)
            val y = t + ((h / 7) % 1000) / 1000f * (b - t)
            c.drawLine(x, y, x + 18f + (h % 20), y + 4f - (h % 9), stroke)
        }
        c.restore()
        stroke.color = 0xFF221F1C.toInt()
        stroke.strokeWidth = 3f
        c.drawRoundRect(r, radius, radius, stroke)
        stroke.color = 0x30FFFFFF
        stroke.strokeWidth = 1.5f
        c.drawRoundRect(l + 3f, t + 3f, rr - 3f, b - 3f, radius * 0.8f, radius * 0.8f, stroke)
    }

    /** Metal frame with corner rivets around a dark well for content. */
    internal fun metalPanel(c: Canvas, l: Float, t: Float, rr: Float, b: Float, radius: Float = 16f) {
        metalPlate(c, l, t, rr, b, radius)
        fill.color = 0xD81B1917.toInt()
        c.drawRoundRect(l + 11f, t + 11f, rr - 11f, b - 11f, radius * 0.55f, radius * 0.55f, fill)
        stroke.color = 0x66000000
        stroke.strokeWidth = 2f
        c.drawRoundRect(l + 11f, t + 11f, rr - 11f, b - 11f, radius * 0.55f, radius * 0.55f, stroke)
        rivet(c, l + 6f, t + 6f, 3.2f)
        rivet(c, rr - 6f, t + 6f, 3.2f)
        rivet(c, l + 6f, b - 6f, 3.2f)
        rivet(c, rr - 6f, b - 6f, 3.2f)
    }


    internal fun chip(c: Canvas, s: String, x: Float, y: Float, size: Float, bg: Int, fg: Int): Float {
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
        val gap = 20f
        val tw = min(230f, (1020f - (tiles.size - 1) * gap) / tiles.size)
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


    private var shaderStage = -1


    private var groundShader: Shader? = null


    private fun volcanoes(c: Canvas, par: Float, base: Float) {
        val off = g.camX * Game.ZOOM * par
        val slot = 520f
        var k = floor(off / slot).toInt() - 1
        val end = floor((off + g.W) / slot).toInt() + 1
        while (k <= end) {
            val h = hash(k + 11)
            val x = k * slot - off + (h % 120)
            val vh = 170f + (h % 5) * 30f
            val vw = 260f + (h % 3) * 50f
            path.reset()
            path.moveTo(x - vw / 2, base + 40f)
            path.lineTo(x - 40f, base - vh)
            path.lineTo(x + 40f, base - vh)
            path.lineTo(x + vw / 2, base + 40f)
            path.close()
            fill.color = 0xFF2B1A18.toInt()
            c.drawPath(path, fill)
            // lava streaks and glowing crater
            val pulse = 0.6f + 0.4f * sin(g.anim * 2f + k)
            // glowing lava rivers running down the flanks
            for (pass in 0..1) {
                stroke.color = if (pass == 0) Color.argb((70 * pulse).toInt(), 255, 90, 20) else Color.argb((230 * pulse).toInt(), 255, 140, 40)
                stroke.strokeWidth = if (pass == 0) 14f else 4f
                c.drawLine(x - 10f, base - vh + 4f, x - 34f, base - vh * 0.55f, stroke)
                c.drawLine(x - 34f, base - vh * 0.55f, x - 70f, base - vh * 0.1f, stroke)
                c.drawLine(x - 34f, base - vh * 0.55f, x - 24f, base - vh * 0.05f, stroke)
                c.drawLine(x + 12f, base - vh + 4f, x + 36f, base - vh * 0.45f, stroke)
                c.drawLine(x + 36f, base - vh * 0.45f, x + 80f, base + 10f, stroke)
            }
            fill.color = Color.argb((220 * pulse).toInt(), 255, 150, 40)
            c.drawOval(x - 38f, base - vh - 8f, x + 38f, base - vh + 8f, fill)
            // smoke column
            for (s in 0 until 4) {
                fill.color = Color.argb(90 - s * 18, 60, 50, 50)
                val sy = base - vh - 40f - s * 50f - ((g.anim * 20f) % 50f)
                c.drawCircle(x + s * 14f, sy, 30f + s * 12f, fill)
            }
            k++
        }
    }

    /** Dead forest with a gothic castle on a hill. */


    private fun castleForest(c: Canvas, par: Float, base: Float) {
        val off = g.camX * Game.ZOOM * par
        fill.color = 0xFF0E1222.toInt()
        // tree line
        var sx = -40f
        while (sx < g.W + 40f) {
            val h = hash(((sx + off) / 30f).toInt())
            val th = 40f + (h % 60)
            val tx = sx - (off % 30f)
            path.reset()
            path.moveTo(tx - 16f, base + 30f)
            path.lineTo(tx, base - th)
            path.lineTo(tx + 16f, base + 30f)
            path.close()
            c.drawPath(path, fill)
            sx += 30f
        }
        // castle every ~1400 px of parallax
        val slot = 1400f
        var k = floor(off / slot).toInt() - 1
        val end = floor((off + g.W) / slot).toInt() + 1
        while (k <= end) {
            val x = k * slot - off + 500f
            fill.color = 0xFF0B0E1A.toInt()
            c.drawRect(x, base - 150f, x + 200f, base + 30f, fill)
            for (t in 0 until 3) {
                val tx = x - 20f + t * 100f
                c.drawRect(tx, base - 230f + (t % 2) * 40f, tx + 40f, base, fill)
                path.reset()
                path.moveTo(tx - 6f, base - 230f + (t % 2) * 40f)
                path.lineTo(tx + 20f, base - 290f + (t % 2) * 40f)
                path.lineTo(tx + 46f, base - 230f + (t % 2) * 40f)
                path.close()
                c.drawPath(path, fill)
            }
            fill.color = Color.argb((160 + 80 * sin(g.anim * 3f + k)).toInt().coerceIn(0, 255), 255, 213, 79)
            c.drawRect(x + 40f, base - 110f, x + 52f, base - 94f, fill)
            c.drawRect(x + 130f, base - 90f, x + 142f, base - 74f, fill)
            c.drawRect(x + 80f, base - 190f, x + 92f, base - 174f, fill)
            k++
        }
    }

    /** Crystal spires and domes of an alien world. */


    private fun alienSpires(c: Canvas, par: Float, base: Float) {
        val off = g.camX * Game.ZOOM * par
        val slot = 160f
        var k = floor(off / slot).toInt() - 1
        val end = floor((off + g.W) / slot).toInt() + 1
        while (k <= end) {
            val h = hash(k * 5 + 3)
            val x = k * slot - off
            if (h % 3 == 0) {
                fill.color = 0xFF2A1F4F.toInt()
                c.drawOval(x - 70f, base - 70f, x + 70f, base + 70f, fill)
                fill.color = Color.argb((140 + 100 * sin(g.anim * 2f + k)).toInt().coerceIn(0, 255), 100, 255, 218)
                c.drawCircle(x, base - 40f, 6f, fill)
            } else {
                val sh = 120f + (h % 6) * 30f
                path.reset()
                path.moveTo(x - 18f, base + 30f)
                path.lineTo(x - 6f, base - sh)
                path.lineTo(x + 6f, base - sh - 20f)
                path.lineTo(x + 18f, base + 30f)
                path.close()
                fill.color = 0xFF34265E.toInt()
                c.drawPath(path, fill)
                stroke.color = 0x8864FFDA.toInt()
                stroke.strokeWidth = 2f
                c.drawLine(x, base - sh, x, base, stroke)
            }
            k++
        }
    }


    internal fun creatureFigure(c: Canvas, type: ZombieType, swing: Float, dir: Float, phase: Float) {
        val w = type.w
        val h = type.h
        val outline = 0xFF1B1B1B.toInt()
        when (type) {
            ZombieType.LIZARD -> {
                // tail
                stroke.color = outline
                stroke.strokeWidth = 12f
                c.drawLine(-dir * w * 0.3f, -h * 0.5f, -dir * w * 0.75f, -h * 0.3f + swing * 6f, stroke)
                stroke.color = 0xFFD84315.toInt()
                stroke.strokeWidth = 8f
                c.drawLine(-dir * w * 0.3f, -h * 0.5f, -dir * w * 0.75f, -h * 0.3f + swing * 6f, stroke)
                // legs
                stroke.color = 0xFF8D2B0B.toInt()
                stroke.strokeWidth = 6f
                for (k in intArrayOf(-1, 1)) {
                    c.drawLine(k * w * 0.2f, -h * 0.4f, k * w * 0.25f + swing * 8f * k, 0f, stroke)
                }
                // body
                fill.color = outline
                c.drawOval(-w * 0.42f, -h * 0.85f, w * 0.42f, -h * 0.2f, fill)
                fill.color = 0xFFE64A19.toInt()
                c.drawOval(-w * 0.4f, -h * 0.82f, w * 0.4f, -h * 0.24f, fill)
                fill.color = 0xFFFFB74D.toInt()
                c.drawOval(-w * 0.3f, -h * 0.45f, w * 0.3f, -h * 0.26f, fill)
                fill.color = 0xFF6D1B00.toInt()
                c.drawCircle(-w * 0.1f, -h * 0.7f, 4f, fill)
                c.drawCircle(w * 0.12f, -h * 0.66f, 3f, fill)
                // head
                fill.color = 0xFFE64A19.toInt()
                c.drawOval(dir * w * 0.3f - 14f, -h * 0.9f, dir * w * 0.3f + 18f * dir.coerceAtLeast(0f) + 14f, -h * 0.45f, fill)
                fill.color = 0xFFFFEB3B.toInt()
                c.drawCircle(dir * w * 0.38f, -h * 0.75f, 4f, fill)
                // flickering flame tongue
                if ((phase * 2f).toInt() % 3 == 0) {
                    fill.color = 0xCCFF9100.toInt()
                    c.drawCircle(dir * (w * 0.55f), -h * 0.6f, 6f, fill)
                    c.drawCircle(dir * (w * 0.65f), -h * 0.62f, 4f, fill)
                }
            }
            ZombieType.BAT -> {
                val flap = sin(phase * 2f)
                fill.color = 0xFF2A1238.toInt()
                path.reset()
                path.moveTo(0f, -h * 0.6f)
                path.lineTo(-w * 0.55f, -h * 0.6f - flap * 22f)
                path.lineTo(-w * 0.4f, -h * 0.35f)
                path.lineTo(-w * 0.2f, -h * 0.45f)
                path.close()
                c.drawPath(path, fill)
                path.reset()
                path.moveTo(0f, -h * 0.6f)
                path.lineTo(w * 0.55f, -h * 0.6f - flap * 22f)
                path.lineTo(w * 0.4f, -h * 0.35f)
                path.lineTo(w * 0.2f, -h * 0.45f)
                path.close()
                c.drawPath(path, fill)
                fill.color = 0xFF3E1A52.toInt()
                c.drawOval(-w * 0.18f, -h * 0.95f, w * 0.18f, -h * 0.2f, fill)
                path.reset()
                path.moveTo(-w * 0.14f, -h * 0.85f); path.lineTo(-w * 0.1f, -h * 1.15f); path.lineTo(-w * 0.02f, -h * 0.88f)
                path.moveTo(w * 0.14f, -h * 0.85f); path.lineTo(w * 0.1f, -h * 1.15f); path.lineTo(w * 0.02f, -h * 0.88f)
                c.drawPath(path, fill)
                fill.color = 0xFFFF1744.toInt()
                c.drawCircle(-w * 0.06f, -h * 0.7f, 2.5f, fill)
                c.drawCircle(w * 0.06f, -h * 0.7f, 2.5f, fill)
            }
            ZombieType.ALIEN -> {
                stroke.color = 0xFF546E7A.toInt()
                stroke.strokeWidth = 6f
                c.drawLine(0f, -h * 0.45f, swing * 10f, 0f, stroke)
                c.drawLine(0f, -h * 0.45f, -swing * 10f, 0f, stroke)
                stroke.strokeWidth = 5f
                c.drawLine(0f, -h * 0.68f, dir * w * 0.8f, -h * 0.55f + swing * 4f, stroke)
                fill.color = outline
                c.drawRoundRect(-w * 0.32f, -h * 0.72f, w * 0.32f, -h * 0.42f, 8f, 8f, fill)
                fill.color = 0xFF90A4AE.toInt()
                c.drawRoundRect(-w * 0.28f, -h * 0.7f, w * 0.28f, -h * 0.44f, 6f, 6f, fill)
                fill.color = Color.argb((160 + 90 * sin(phase * 2f)).toInt().coerceIn(0, 255), 100, 255, 218)
                c.drawCircle(0f, -h * 0.58f, 4f, fill)
                // big head and eyes
                fill.color = outline
                c.drawOval(-w * 0.62f, -h * 1.12f, w * 0.62f, -h * 0.68f, fill)
                fill.color = 0xFFB0BEC5.toInt()
                c.drawOval(-w * 0.58f, -h * 1.1f, w * 0.58f, -h * 0.7f, fill)
                fill.color = 0xFF111111.toInt()
                c.drawOval(dir * w * 0.05f - 10f, -h * 0.95f, dir * w * 0.05f + 2f, -h * 0.82f, fill)
                c.drawOval(dir * w * 0.32f - 9f, -h * 0.95f, dir * w * 0.32f + 3f, -h * 0.82f, fill)
                stroke.color = 0xFF90A4AE.toInt()
                stroke.strokeWidth = 2f
                c.drawLine(0f, -h * 1.1f, -dir * 8f, -h * 1.25f, stroke)
                fill.color = 0xFF64FFDA.toInt()
                c.drawCircle(-dir * 8f, -h * 1.25f, 3.5f, fill)
            }
            else -> { // BLOB
                val squish = 1f + 0.08f * sin(phase * 1.5f)
                fill.color = 0xAA00897B.toInt()
                c.drawOval(-w * 0.5f * squish, -h / squish, w * 0.5f * squish, 2f, fill)
                fill.color = 0x8864FFDA.toInt()
                c.drawOval(-w * 0.35f, -h * 0.85f / squish, w * 0.05f, -h * 0.55f / squish, fill)
                fill.color = 0x55FFFFFF
                c.drawCircle(-w * 0.15f, -h * 0.3f, 5f, fill)
                c.drawCircle(w * 0.2f, -h * 0.5f, 3f, fill)
                fill.color = 0xFF111111.toInt()
                c.drawCircle(dir * w * 0.12f, -h * 0.65f / squish, 5f, fill)
                c.drawCircle(dir * w * 0.3f, -h * 0.62f / squish, 5f, fill)
                fill.color = 0xFFFFFFFF.toInt()
                c.drawCircle(dir * w * 0.12f + 1.5f, -h * 0.67f / squish, 1.6f, fill)
                c.drawCircle(dir * w * 0.3f + 1.5f, -h * 0.64f / squish, 1.6f, fill)
            }
        }
    }


    /** Region boss from its AI picture: big walkers step, others heave. */
    private fun bossSprite(c: Canvas, name: String, x: Float, y: Float, b: Boss, alpha: Int): Boolean {
        val face = g.driver.x > x || (!g.driver.out && g.car.x > x)
        val ph = g.anim * 4f + x * 0.01f
        return if (name == "boss_brute") walkSprite(c, name, x, y, b.h * 1.1f, !face, ph, 16f, alpha, 3f)
        else sprite(c, name, x, y + 6f, b.h * 1.1f, flip = !face, alpha = alpha, rot = sin(ph) * 3f, squash = 1f + 0.04f * sin(ph * 2f))
    }

    internal fun drawBossAny(c: Canvas, b: Boss) {
        Sprites.boss(b.tier)?.let { if (bossSprite(c, it, b.x, b.y, b, if (b.flash > 0f) 160 else 255)) return }
        when (b.tier) {
            2 -> drawDragon(c, b)
            3 -> drawBatBoss(c, b)
            4 -> drawAlienBoss(c, b)
            else -> drawBoss(c, b)
        }
    }


    private fun drawDragon(c: Canvas, b: Boss) {
        val dir = if (targetX() >= b.x) 1f else -1f
        val w = b.w
        val h = b.h
        val hit = b.flash > 0f
        val body = if (hit) 0xFFFFCDD2.toInt() else 0xFFC62828.toInt()
        val dark = if (hit) 0xFFEF9A9A.toInt() else 0xFF7F0000.toInt()
        val flap = sin(b.anim * 5f)
        // shadow on the ground
        fill.color = Color.argb((90 * (1f - (b.lift / 400f).coerceIn(0f, 0.8f))).toInt(), 0, 0, 0)
        c.drawOval(b.x - w * 0.5f, b.y - 10f, b.x + w * 0.5f, b.y + 10f, fill)
        c.save()
        c.translate(b.x, b.cy)
        // far wing
        fill.color = 0xFF5D0B0B.toInt()
        path.reset()
        path.moveTo(-dir * 10f, -h * 0.2f)
        path.lineTo(-dir * w * 0.15f, -h * 0.2f - h * 1.1f * (0.6f + 0.4f * flap))
        path.lineTo(-dir * w * 0.45f, -h * 0.4f - h * 0.5f * flap)
        path.lineTo(-dir * w * 0.3f, -h * 0.1f)
        path.close()
        c.drawPath(path, fill)
        // tail
        stroke.color = dark
        stroke.strokeWidth = 18f
        val tailY = sin(b.anim * 2f) * 20f
        c.drawLine(-dir * w * 0.3f, 0f, -dir * w * 0.55f, h * 0.15f + tailY, stroke)
        stroke.strokeWidth = 10f
        c.drawLine(-dir * w * 0.55f, h * 0.15f + tailY, -dir * w * 0.75f, h * 0.05f + tailY * 1.5f, stroke)
        fill.color = dark
        path.reset()
        path.moveTo(-dir * w * 0.75f, h * 0.05f + tailY * 1.5f - 12f)
        path.lineTo(-dir * w * 0.88f, h * 0.05f + tailY * 1.5f)
        path.lineTo(-dir * w * 0.75f, h * 0.05f + tailY * 1.5f + 12f)
        path.close()
        c.drawPath(path, fill)
        // legs
        stroke.color = dark
        stroke.strokeWidth = 14f
        c.drawLine(-dir * w * 0.12f, h * 0.2f, -dir * w * 0.14f, h * 0.5f, stroke)
        c.drawLine(dir * w * 0.12f, h * 0.2f, dir * w * 0.16f, h * 0.5f, stroke)
        // body
        fill.color = 0xFF1B1B1B.toInt()
        c.drawOval(-w * 0.34f, -h * 0.34f, w * 0.34f, h * 0.36f, fill)
        fill.color = body
        c.drawOval(-w * 0.32f, -h * 0.32f, w * 0.32f, h * 0.33f, fill)
        fill.color = 0xFFFFB74D.toInt()
        c.drawOval(-w * 0.22f, h * 0.02f, w * 0.22f, h * 0.3f, fill)
        // back spikes
        fill.color = dark
        for (k in -2..2) {
            path.reset()
            val sx = k * w * 0.1f
            path.moveTo(sx - 10f, -h * 0.28f)
            path.lineTo(sx, -h * 0.48f)
            path.lineTo(sx + 10f, -h * 0.28f)
            path.close()
            c.drawPath(path, fill)
        }
        // neck and head
        val nx = dir * w * 0.42f
        val ny = -h * 0.4f
        stroke.color = body
        stroke.strokeWidth = 30f
        c.drawLine(dir * w * 0.2f, -h * 0.05f, nx, ny, stroke)
        fill.color = body
        c.drawOval(nx - 34f, ny - 26f, nx + 34f, ny + 22f, fill)
        c.drawOval(nx + dir * 18f - 26f, ny - 12f, nx + dir * 18f + 26f, ny + 14f, fill)
        fill.color = 0xFF3E2723.toInt()
        path.reset()
        path.moveTo(nx - dir * 14f, ny - 20f)
        path.lineTo(nx - dir * 40f, ny - 52f)
        path.lineTo(nx - dir * 4f, ny - 24f)
        path.close()
        c.drawPath(path, fill)
        val open = if (b.breathT > 0f) 1f else 0f
        if (open > 0f) {
            fill.color = 0xFF3E0000.toInt()
            c.drawOval(nx + dir * 20f - 22f, ny + 4f, nx + dir * 20f + 22f, ny + 26f, fill)
            fill.color = 0xFFFFC400.toInt()
            c.drawCircle(nx + dir * 40f, ny + 10f, 10f, fill)
        }
        fill.color = 0xFFFFEB3B.toInt()
        c.drawCircle(nx + dir * 6f, ny - 10f, 7f, fill)
        fill.color = 0xFF111111.toInt()
        c.drawCircle(nx + dir * 8f, ny - 10f, 3f, fill)
        // near wing
        fill.color = 0xFF9E1B1B.toInt()
        path.reset()
        path.moveTo(dir * 10f, -h * 0.15f)
        path.lineTo(dir * w * 0.05f, -h * 0.2f - h * 1.2f * (0.6f + 0.4f * flap))
        path.lineTo(-dir * w * 0.3f, -h * 0.35f - h * 0.6f * flap)
        path.lineTo(-dir * w * 0.15f, h * 0.05f)
        path.close()
        c.drawPath(path, fill)
        stroke.color = 0xFF5D0B0B.toInt()
        stroke.strokeWidth = 4f
        c.drawLine(dir * 10f, -h * 0.15f, dir * w * 0.05f, -h * 0.2f - h * 1.2f * (0.6f + 0.4f * flap), stroke)
        c.restore()
    }


    private fun drawBatBoss(c: Canvas, b: Boss) {
        val dir = if (targetX() >= b.x) 1f else -1f
        val w = b.w
        val h = b.h
        val hit = b.flash > 0f
        val body = if (hit) 0xFFE1BEE7.toInt() else 0xFF311B42.toInt()
        val wing = if (hit) 0xFFCE93D8.toInt() else 0xFF4A148C.toInt()
        val flap = sin(b.anim * (if (b.diveT > 0f) 12f else 6f))
        fill.color = Color.argb((80 * (1f - (b.lift / 400f).coerceIn(0f, 0.8f))).toInt(), 0, 0, 0)
        c.drawOval(b.x - w * 0.4f, b.y - 8f, b.x + w * 0.4f, b.y + 8f, fill)
        c.save()
        c.translate(b.x, b.cy)
        for (side in intArrayOf(-1, 1)) {
            path.reset()
            path.moveTo(side * w * 0.08f, -h * 0.2f)
            path.lineTo(side * w * 0.35f, -h * 0.55f - flap * h * 0.5f)
            path.lineTo(side * w * 0.62f, -h * 0.3f - flap * h * 0.6f)
            path.lineTo(side * w * 0.52f, h * 0.05f - flap * h * 0.3f)
            path.lineTo(side * w * 0.4f, -h * 0.05f - flap * h * 0.25f)
            path.lineTo(side * w * 0.3f, h * 0.15f - flap * h * 0.15f)
            path.lineTo(side * w * 0.12f, h * 0.1f)
            path.close()
            fill.color = wing
            c.drawPath(path, fill)
            stroke.color = 0xFF1A0526.toInt()
            stroke.strokeWidth = 4f
            c.drawLine(side * w * 0.08f, -h * 0.2f, side * w * 0.62f, -h * 0.3f - flap * h * 0.6f, stroke)
        }
        fill.color = 0xFF120318.toInt()
        c.drawOval(-w * 0.17f, -h * 0.47f, w * 0.17f, h * 0.42f, fill)
        fill.color = body
        c.drawOval(-w * 0.15f, -h * 0.45f, w * 0.15f, h * 0.4f, fill)
        // ears
        path.reset()
        path.moveTo(-w * 0.12f, -h * 0.35f); path.lineTo(-w * 0.1f, -h * 0.8f); path.lineTo(-w * 0.02f, -h * 0.4f)
        path.moveTo(w * 0.12f, -h * 0.35f); path.lineTo(w * 0.1f, -h * 0.8f); path.lineTo(w * 0.02f, -h * 0.4f)
        c.drawPath(path, fill)
        // glowing eyes and fangs
        val glow = 0.6f + 0.4f * sin(b.anim * 8f)
        fill.color = Color.argb((140 * glow).toInt(), 255, 23, 68)
        c.drawCircle(-w * 0.06f + dir * 4f, -h * 0.2f, 12f, fill)
        c.drawCircle(w * 0.06f + dir * 4f, -h * 0.2f, 12f, fill)
        fill.color = 0xFFFF1744.toInt()
        c.drawCircle(-w * 0.06f + dir * 4f, -h * 0.2f, 5f, fill)
        c.drawCircle(w * 0.06f + dir * 4f, -h * 0.2f, 5f, fill)
        fill.color = Color.WHITE
        path.reset()
        path.moveTo(-10f, h * 0.02f); path.lineTo(-6f, h * 0.2f); path.lineTo(-2f, h * 0.02f)
        path.moveTo(2f, h * 0.02f); path.lineTo(6f, h * 0.2f); path.lineTo(10f, h * 0.02f)
        c.drawPath(path, fill)
        c.restore()
    }


    private fun drawAlienBoss(c: Canvas, b: Boss) {
        val dir = if (targetX() >= b.x) 1f else -1f
        val w = b.w
        val h = b.h
        val hit = b.flash > 0f
        val skin = if (hit) 0xFFF1F8E9.toInt() else 0xFF8FA9A4.toInt()
        val dark = if (hit) 0xFFCFD8DC.toInt() else 0xFF52706B.toInt()
        val stomp = if (b.slamAnim > 0f) sin(b.slamAnim / 0.5f * 3.14f) else 0f
        c.save()
        c.translate(b.x, b.y)
        fill.color = 0x55000000
        c.drawOval(-w, -10f, w, 10f, fill)
        c.translate(0f, -stomp * 30f)
        // four tentacle legs
        for (k in 0 until 4) {
            val bx = -w * 0.3f + k * w * 0.2f
            val wave = sin(b.anim * 3f + k * 1.3f) * 14f
            stroke.color = 0xFF1B1B1B.toInt()
            stroke.strokeWidth = 18f
            c.drawLine(bx, -h * 0.4f, bx + wave, -h * 0.15f, stroke)
            c.drawLine(bx + wave, -h * 0.15f, bx * 1.4f - wave, -4f, stroke)
            stroke.color = dark
            stroke.strokeWidth = 12f
            c.drawLine(bx, -h * 0.4f, bx + wave, -h * 0.15f, stroke)
            c.drawLine(bx + wave, -h * 0.15f, bx * 1.4f - wave, -4f, stroke)
        }
        // torso
        fill.color = 0xFF1B1B1B.toInt()
        c.drawRoundRect(-w * 0.32f, -h * 0.72f, w * 0.32f, -h * 0.36f, 30f, 30f, fill)
        fill.color = skin
        c.drawRoundRect(-w * 0.29f, -h * 0.7f, w * 0.29f, -h * 0.38f, 26f, 26f, fill)
        val pulse = 0.5f + 0.5f * sin(b.anim * 5f)
        fill.color = Color.argb((120 + 120 * pulse).toInt(), 100, 255, 218)
        c.drawCircle(0f, -h * 0.55f, 18f + 6f * pulse, fill)
        fill.color = Color.WHITE
        c.drawCircle(0f, -h * 0.55f, 7f, fill)
        // arms: raised when firing plasma
        val raise = (b.throwAnim / 0.45f).coerceIn(0f, 1f)
        stroke.color = dark
        stroke.strokeWidth = 12f
        c.drawLine(dir * w * 0.25f, -h * 0.66f, dir * w * (0.6f - 0.15f * raise), -h * (0.55f + 0.25f * raise), stroke)
        c.drawLine(-dir * w * 0.25f, -h * 0.66f, -dir * w * 0.5f, -h * 0.45f, stroke)
        if (raise > 0f) {
            fill.color = Color.argb((200 * raise).toInt(), 100, 255, 218)
            c.drawCircle(dir * w * 0.45f, -h * 0.8f, 16f, fill)
        }
        // huge head with black eyes
        fill.color = 0xFF1B1B1B.toInt()
        c.drawOval(-w * 0.52f, -h * 1.08f, w * 0.52f, -h * 0.66f, fill)
        fill.color = skin
        c.drawOval(-w * 0.49f, -h * 1.06f, w * 0.49f, -h * 0.68f, fill)
        fill.color = 0xFF050505.toInt()
        c.save()
        c.translate(dir * w * 0.06f - w * 0.2f, -h * 0.86f)
        c.rotate(dir * 20f)
        c.drawOval(-26f, -14f, 26f, 14f, fill)
        c.restore()
        c.save()
        c.translate(dir * w * 0.06f + w * 0.2f, -h * 0.86f)
        c.rotate(-dir * 20f)
        c.drawOval(-26f, -14f, 26f, 14f, fill)
        c.restore()
        fill.color = 0x88FFFFFF.toInt()
        c.drawCircle(dir * w * 0.06f - w * 0.2f - 8f, -h * 0.88f, 5f, fill)
        c.drawCircle(dir * w * 0.06f + w * 0.2f - 8f, -h * 0.88f, 5f, fill)
        stroke.color = dark
        stroke.strokeWidth = 4f
        c.drawLine(-w * 0.15f, -h * 1.04f, -w * 0.25f, -h * 1.18f, stroke)
        c.drawLine(w * 0.15f, -h * 1.04f, w * 0.25f, -h * 1.18f, stroke)
        fill.color = Color.argb((160 + 90 * sin(b.anim * 6f)).toInt().coerceIn(0, 255), 178, 255, 89)
        c.drawCircle(-w * 0.25f, -h * 1.18f, 7f, fill)
        c.drawCircle(w * 0.25f, -h * 1.18f, 7f, fill)
        c.restore()
    }

    // ------------------------------------------------------- hand weapons

    /** A hand weapon held at the origin, pointing along +x. */
    internal fun handWeapon(c: Canvas, w: SideWeapon, s: Float) {
        c.save()
        c.scale(s, s)
        when (w) {
            SideWeapon.PISTOL -> {
                fill.color = 0xFF78909C.toInt()
                c.drawRect(-2f, -6f, 18f, 0f, fill)
                c.drawRect(-2f, 0f, 4f, 9f, fill)
            }
            SideWeapon.DART -> {
                c.save()
                c.translate(8f, -2f)
                c.rotate(g.anim * 200f)
                star(c, 0f, 0f, 10f, 0xFFB0BEC5.toInt())
                c.restore()
            }
            SideWeapon.SPEAR -> {
                stroke.color = 0xFF8D6E63.toInt()
                stroke.strokeWidth = 4f
                c.drawLine(-26f, 0f, 62f, 0f, stroke)
                fill.color = 0xFFCFD8DC.toInt()
                path.reset()
                path.moveTo(60f, -6f); path.lineTo(84f, 0f); path.lineTo(60f, 6f); path.close()
                c.drawPath(path, fill)
                fill.color = 0xFFD32F2F.toInt()
                c.drawRect(54f, -4f, 58f, 4f, fill)
            }
            SideWeapon.BOW -> {
                stroke.color = 0xFF6D4C41.toInt()
                stroke.strokeWidth = 4f
                r.set(-14f, -30f, 14f, 30f)
                c.drawArc(r, -90f, 180f, false, stroke)
                stroke.color = 0xFFECEFF1.toInt()
                stroke.strokeWidth = 1.5f
                c.drawLine(0f, -30f, 0f, 30f, stroke)
            }
            SideWeapon.AXE -> {
                stroke.color = 0xFF8D6E63.toInt()
                stroke.strokeWidth = 5f
                c.drawLine(-6f, 0f, 48f, 0f, stroke)
                fill.color = 0xFF90A4AE.toInt()
                path.reset()
                path.moveTo(36f, -2f); path.lineTo(52f, -20f); path.quadTo(62f, -6f, 56f, 6f); path.lineTo(36f, 4f); path.close()
                c.drawPath(path, fill)
                fill.color = 0xFFECEFF1.toInt()
                c.drawRect(52f, -16f, 56f, 4f, fill)
            }
            SideWeapon.SHOTGUN -> {
                fill.color = 0xFF5D4037.toInt()
                c.drawRoundRect(-14f, -5f, 6f, 5f, 3f, 3f, fill)
                fill.color = 0xFF78909C.toInt()
                c.drawRect(4f, -6f, 44f, -1f, fill)
                c.drawRect(4f, 0f, 34f, 3f, fill)
            }
            SideWeapon.SMG -> {
                fill.color = 0xFF78909C.toInt()
                c.drawRect(-4f, -7f, 26f, 1f, fill)
                c.drawRect(24f, -5f, 36f, -2f, fill)
                c.drawRect(6f, 1f, 12f, 14f, fill)
                c.drawRect(-4f, 1f, 2f, 9f, fill)
            }
        }
        c.restore()
    }

    /** Bullets, darts and arrows from the driver's hand weapon. */
    internal fun sideShot(c: Canvas, s: Projectile) {
        val l = hypot(s.vx, s.vy).coerceAtLeast(1f)
        val ux = s.vx / l
        val uy = s.vy / l
        when (s.side) {
            SideWeapon.DART -> {
                c.save()
                c.translate(s.x, s.y)
                c.rotate(s.age * 1400f)
                star(c, 0f, 0f, 11f, 0xFFCFD8DC.toInt())
                c.restore()
            }
            SideWeapon.BOW -> {
                stroke.color = 0xFF8D6E63.toInt()
                stroke.strokeWidth = 4f
                c.drawLine(s.x, s.y, s.x - ux * 46f, s.y - uy * 46f, stroke)
                stroke.color = 0xFFEF5350.toInt()
                stroke.strokeWidth = 3f
                c.drawLine(s.x - ux * 40f, s.y - uy * 40f, s.x - ux * 48f - uy * 6f, s.y - uy * 48f + ux * 6f, stroke)
                c.drawLine(s.x - ux * 40f, s.y - uy * 40f, s.x - ux * 48f + uy * 6f, s.y - uy * 48f - ux * 6f, stroke)
                fill.color = 0xFFB0BEC5.toInt()
                c.drawCircle(s.x, s.y, 5f, fill)
            }
            else -> {
                val len = if (s.side == SideWeapon.SHOTGUN) 16f else 24f
                stroke.color = 0x66FFAB00
                stroke.strokeWidth = 7f
                c.drawLine(s.x, s.y, s.x - ux * len, s.y - uy * len, stroke)
                stroke.color = 0xFFFFF176.toInt()
                stroke.strokeWidth = 3f
                c.drawLine(s.x, s.y, s.x - ux * len, s.y - uy * len, stroke)
            }
        }
    }

    /** Garage tab: the driver's hand weapons, bought with money. */
    private fun garageSide(c: Canvas) {
        val s = g.save
        for (w in SideWeapon.entries) {
            val rc = g.weaponRect(w.ordinal)
            val unlocked = s.sideUnlocked(w) || s.ownsSide(w)
            val owned = s.ownsSide(w)
            card(c, rc, g.garageSide == w)
            val cx = (rc[0] + rc[2]) / 2
            if (!spriteBox(c, Sprites.side(w), rc[0] + 20f, rc[1] + 8f, rc[2] - 20f, rc[1] + 62f)) {
                c.save()
                c.translate(cx - 30f, rc[1] + 50f)
                lockedLayer(c, !unlocked)
                handWeapon(c, w, 1.4f)
                c.restore()
                c.restore()
            }
            text.textAlign = Paint.Align.CENTER
            text.color = if (unlocked) Color.WHITE else 0xFF9E9E9E.toInt()
            fitText(c, w.title, cx, rc[1] + 84f, 22f, rc[2] - rc[0] - 14f)
            val (label, color) = when {
                s.side == w.ordinal -> "ĐANG DÙNG" to 0xFF69F0AE.toInt()
                owned -> "ĐÃ CÓ" to Color.WHITE
                !unlocked -> "🔒 Ải ${w.unlockStage}" to 0xFF9E9E9E.toInt()
                s.money >= w.cost -> "${w.cost}$" to 0xFFFFD740.toInt()
                else -> "${w.cost}$" to 0xFF9E9E9E.toInt()
            }
            text.color = color
            fitText(c, label, cx, rc[3] - 10f, 19f, rc[2] - rc[0] - 14f)
        }
        val w = g.garageSide
        val pr = g.weaponPanelRect()
        card(c, pr, false)
        if (!spriteBox(c, Sprites.side(w), pr[0] + 30f, pr[1] + 30f + sin(g.anim * 2f) * 4f, pr[0] + 260f, pr[1] + 250f + sin(g.anim * 2f) * 4f)) {
            c.save()
            c.translate(pr[0] + 60f, pr[1] + 110f)
            lockedLayer(c, !(s.sideUnlocked(w) || s.ownsSide(w)))
            handWeapon(c, w, 2.6f)
            c.restore()
            c.restore()
        }
        val tx = pr[0] + 290f
        text.textAlign = Paint.Align.LEFT
        text.color = Color.WHITE
        fitText(c, w.title.uppercase(), tx, pr[1] + 44f, 34f, 420f)
        text.color = 0xFFB0BEC5.toInt()
        fitText(c, "${w.kind} • ${w.desc} Dùng khi đi bộ và trong nhà, không tốn đạn.", tx, pr[1] + 76f, 20f, pr[2] - 360f - tx)
        fun fmt(f: Float) = if (f >= 10f) f.toInt().toString() else "%.1f".format(f)
        val rows = listOf(
            Triple("Sát thương", kotlin.math.sqrt(w.dps / 30f), "${fmt(w.dps)}/giây"),
            Triple("Một đòn", min(1f, w.damage * w.pellets / 7f), fmt(w.damage * w.pellets)),
            Triple("Tốc đánh", kotlin.math.sqrt((1f / w.interval) / 10f), fmt(1f / w.interval) + "/giây"),
            Triple("Tầm", w.range / 1000f, "${(w.range / Game.METER).toInt()}m"),
            Triple("Xuyên", min(1f, w.pierce / 5f), if (w.pierce >= 99) "∞" else "${w.pierce}"),
        )
        statRows(c, rows.map { (a, f, v) -> Triple(a, f to f, v) }, tx, pr[2] - 350f, pr[1] + 118f, 20f)
    }

    // ------------------------------------------------------- sound screen

    private fun drawSound(c: Canvas) {
        val W = g.W
        dim(c, 0xC0)
        metalPanel(c, W / 2 - 420f, 70f, W / 2 + 420f, g.H - 140f, 22f)
        title(c, "ÂM THANH", W / 2, 140f, 0xFFFFD740.toInt(), 52f)
        val names = arrayOf("🎵 Nhạc nền", "💥 Hiệu ứng", "🚗 Động cơ xe")
        val cols = intArrayOf(0xFFAB47BC.toInt(), 0xFFFFA726.toInt(), 0xFF66BB6A.toInt())
        for (i in 0..2) {
            val rc = g.volSlider(i)
            val v = g.volValue(i)
            val f = v / 100f
            text.textAlign = Paint.Align.LEFT
            text.textSize = 28f
            text.color = Color.WHITE
            c.drawText(names[i], rc[0], rc[1] - 22f, text)
            text.textAlign = Paint.Align.RIGHT
            text.color = if (v == 0) 0xFFFF8A80.toInt() else 0xFFFFE082.toInt()
            c.drawText(if (v == 0) "TẮT" else "$v%", rc[2], rc[1] - 22f, text)
            fill.color = 0xCC111111.toInt()
            c.drawRoundRect(rc[0], rc[1], rc[2], rc[3], 12f, 12f, fill)
            fill.color = cols[i]
            if (f > 0f) c.drawRoundRect(rc[0] + 3f, rc[1] + 3f, rc[0] + 3f + (rc[2] - rc[0] - 6f) * f, rc[3] - 3f, 9f, 9f, fill)
            val kx = rc[0] + (rc[2] - rc[0]) * f
            val ky = (rc[1] + rc[3]) / 2
            fill.color = if (g.dragVol == i) 0xFFFFFFFF.toInt() else 0xFFE0E0E0.toInt()
            c.drawCircle(kx, ky, if (g.dragVol == i) 26f else 22f, fill)
            stroke.color = 0xFF2B2622.toInt()
            stroke.strokeWidth = 3f
            c.drawCircle(kx, ky, if (g.dragVol == i) 26f else 22f, stroke)
        }
        text.textAlign = Paint.Align.CENTER
        text.color = 0xFFB0BEC5.toInt()
        fitText(c, "Kéo thanh để chỉnh • thả thanh Hiệu ứng để nghe thử • giữ thanh Động cơ để nghe tiếng máy", W / 2, g.volSlider(2)[3] + 70f, 20f, 800f)
        drawMenuButtons(c)
    }

    // ------------------------------------------------------- building interior

    /** Rooms of the building the driver is searching (floor at y = 0). */
    private fun drawInterior(c: Canvas, inn: Interior) {
        val W = g.W
        val H = g.H
        val kind = inn.b.kind
        fill.shader = null
        fill.alpha = 255
        fill.color = 0xFF0E0C0B.toInt()
        c.drawRect(0f, 0f, W, H, fill)
        c.save()
        if (g.shake > 0f) {
            val s = g.shake * 30f
            c.translate((Random.nextFloat() - 0.5f) * s, (Random.nextFloat() - 0.5f) * s)
        }
        c.scale(Game.ZOOM, Game.ZOOM)
        val camX = inn.camX
        val camY = -g.viewH * 0.7f
        c.translate(-camX, -camY)
        val x0 = camX - 100f
        val x1 = camX + g.viewW + 100f
        val ceil = -Interior.CEIL
        val walls = when (kind) {
            0 -> intArrayOf(0xFF6D5A4B.toInt(), 0xFF5E6B5A.toInt(), 0xFF6A5670.toInt(), 0xFF5A534C.toInt())
            1 -> intArrayOf(0xFF7A7058.toInt(), 0xFF6E6A5E.toInt(), 0xFF5F5A50.toInt(), 0xFF655A4A.toInt())
            2 -> intArrayOf(0xFF7D7A72.toInt(), 0xFF6A6C70.toInt(), 0xFF5E5A54.toInt(), 0xFF6E655A.toInt())
            3 -> intArrayOf(0xFF5E666B.toInt(), 0xFF55606A.toInt(), 0xFF5A5A5E.toInt(), 0xFF62605A.toInt())
            else -> intArrayOf(0xFF9AA39E.toInt(), 0xFF8E9A9C.toInt(), 0xFF8A9088.toInt(), 0xFF8C8478.toInt())
        }
        val outside = when (g.region) {
            Region.DESERT -> 0xFFE8B070.toInt()
            Region.VOLCANO -> 0xFFB5482A.toInt()
            Region.NIGHT -> 0xFF1E2A4A.toInt()
            Region.ALIEN -> 0xFF5B3A8C.toInt()
        }
        for (room in 0 until inn.rooms) {
            val l = room * Interior.ROOM_W
            val rr = l + Interior.ROOM_W
            if (rr < x0 || l > x1) continue
            val wall = walls[room % walls.size]
            fill.color = wall
            c.drawRect(l, ceil, rr, 0f, fill)
            // wallpaper: stripes at home, tiles in the shop / pharmacy, bricks in workshops
            when (kind) {
                0 -> {
                    fill.color = shade(wall, 0.88f)
                    var x = l
                    while (x < rr) { c.drawRect(x, ceil, x + 26f, -120f, fill); x += 64f }
                }
                1, 4 -> {
                    stroke.color = shade(wall, 0.82f)
                    stroke.strokeWidth = 2f
                    var y = -120f
                    while (y > ceil) { c.drawLine(l, y, rr, y, stroke); y -= 60f }
                    var x = l
                    while (x < rr) { c.drawLine(x, ceil, x, -120f, stroke); x += 60f }
                }
                else -> {
                    stroke.color = shade(wall, 0.8f)
                    stroke.strokeWidth = 2f
                    var y = -40f
                    var row = 0
                    while (y > ceil) {
                        c.drawLine(l, y, rr, y, stroke)
                        var x = l + (if (row % 2 == 0) 0f else 45f)
                        while (x < rr) { c.drawLine(x, y, x, y - 40f, stroke); x += 90f }
                        y -= 40f; row++
                    }
                }
            }
            // wainscot
            fill.color = shade(wall, 0.7f)
            c.drawRect(l, -120f, rr, 0f, fill)
            fill.color = shade(wall, 0.55f)
            c.drawRect(l, -126f, rr, -118f, fill)
            // a boarded window looking out at the road
            val wx = l + 380f
            fill.color = 0xFF2B2420.toInt()
            c.drawRect(wx - 8f, -420f, wx + 168f, -228f, fill)
            fill.color = if (g.stormX > inn.b.x - Building.W / 2) 0xFF7B1FA2.toInt() else outside
            c.drawRect(wx, -412f, wx + 160f, -236f, fill)
            fill.color = 0x30FFFFFF
            c.drawRect(wx, -412f, wx + 70f, -236f, fill)
            fill.color = 0xFF6D4C41.toInt()
            c.save()
            c.translate(wx + 80f, -330f); c.rotate(-8f); c.translate(-(wx + 80f), -(-330f))
            c.drawRect(wx - 16f, -370f, wx + 176f, -350f, fill)
            c.restore()
            c.save()
            c.translate(wx + 80f, -290f); c.rotate(6f); c.translate(-(wx + 80f), -(-290f))
            c.drawRect(wx - 16f, -300f, wx + 176f, -280f, fill)
            c.restore()
            // stains and cracks
            fill.color = 0x22000000
            c.drawOval(l + 120f, -470f, l + 260f, -380f, fill)
            stroke.color = 0x55000000
            stroke.strokeWidth = 2f
            c.drawLine(l + 700f, -520f, l + 730f, -440f, stroke)
            c.drawLine(l + 730f, -440f, l + 715f, -380f, stroke)
            // hanging lamp, flickering
            val lx = l + Interior.ROOM_W / 2
            val on = (g.anim * 7f + room * 3f).toInt() % 23 != 0
            stroke.color = 0xFF222222.toInt()
            stroke.strokeWidth = 2f
            c.drawLine(lx, ceil, lx, ceil + 70f, stroke)
            fill.color = 0xFF37474F.toInt()
            path.reset()
            path.moveTo(lx - 30f, ceil + 92f); path.lineTo(lx - 12f, ceil + 70f); path.lineTo(lx + 12f, ceil + 70f); path.lineTo(lx + 30f, ceil + 92f); path.close()
            c.drawPath(path, fill)
            if (on) {
                fill.color = 0xFFFFF59D.toInt()
                c.drawCircle(lx, ceil + 94f, 8f, fill)
                fill.color = 0x22FFF59D
                path.reset()
                path.moveTo(lx - 30f, ceil + 92f); path.lineTo(lx - 260f, 0f); path.lineTo(lx + 260f, 0f); path.lineTo(lx + 30f, ceil + 92f); path.close()
                c.drawPath(path, fill)
            }
            // doorway into the next room
            if (room < inn.rooms - 1) {
                fill.color = 0xFF3E2E24.toInt()
                c.drawRect(rr - 70f, -330f, rr + 70f, 0f, fill)
                fill.color = 0xFF120E0C.toInt()
                c.drawRect(rr - 56f, -316f, rr + 56f, 0f, fill)
            }
            // room name plate
            text.textAlign = Paint.Align.CENTER
            text.textSize = 24f
            text.color = 0x66FFFFFF
            c.drawText(inn.roomName(l + 10f), l + 200f, ceil + 50f, text)
        }
        // solid walls at both ends, ceiling beam and floor
        fill.color = 0xFF2A211B.toInt()
        c.drawRect(-400f, ceil - 100f, 0f, 300f, fill)
        c.drawRect(inn.width, ceil - 100f, inn.width + 400f, 300f, fill)
        fill.color = 0xFF3B2F27.toInt()
        c.drawRect(x0, ceil - 100f, x1, ceil, fill)
        fill.color = 0xFF2B221C.toInt()
        c.drawRect(x0, ceil - 8f, x1, ceil + 6f, fill)
        val floorCol = when (kind) { 3 -> 0xFF4A4F52.toInt(); 1, 4 -> 0xFF8C8578.toInt(); else -> 0xFF6B4A33.toInt() }
        fill.color = floorCol
        c.drawRect(x0, 0f, x1, 300f, fill)
        fill.color = shade(floorCol, 1.15f)
        c.drawRect(x0, 0f, x1, 8f, fill)
        stroke.color = shade(floorCol, 0.8f)
        stroke.strokeWidth = 2f
        var fx = floor(x0 / 120f) * 120f
        while (fx < x1) { c.drawLine(fx, 8f, fx - 40f, 300f, stroke); fx += 120f }
        if (kind == 3) {
            fill.color = 0x55000000
            c.drawOval(1000f, 10f, 1200f, 40f, fill)
        }
        // the way out, with daylight beyond and an EXIT sign
        val ex = inn.exitX
        fill.color = 0xFF4E342E.toInt()
        c.drawRect(ex - 70f, -330f, ex + 70f, 0f, fill)
        fill.color = if (g.stormX > inn.b.x - Building.W / 2) 0xFF8E24AA.toInt() else outside
        c.drawRect(ex - 56f, -316f, ex + 56f, 0f, fill)
        fill.color = 0x40FFFFFF
        c.drawRect(ex - 56f, -316f, ex - 10f, 0f, fill)
        fill.color = 0xFF1B5E20.toInt()
        c.drawRoundRect(ex - 70f, -400f, ex + 70f, -350f, 8f, 8f, fill)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 26f
        text.color = 0xFFB9F6CA.toInt()
        c.drawText("LỐI RA", ex, -365f, text)

        for (p in inn.platforms) if (p.r > x0 && p.l < x1) drawFurniture(c, p)
        for (ch in inn.chests) if (ch.x in x0..x1) drawChest(c, ch)
        for (z in inn.zombies) if (z.x in x0..x1) {
            fill.color = 0x40000000
            c.drawOval(z.x - z.type.w * 0.8f, -6f, z.x + z.type.w * 0.8f, 6f, fill)
            drawZombie(c, z)
        }
        drawDriver(c)
        for (s in inn.shots) sideShot(c, s)
        for (p in g.particles) {
            val ratio = (p.life / p.maxLife).coerceIn(0f, 1f)
            fill.color = p.color
            fill.alpha = (255 * ratio * (if (p.grow > 0f) 0.55f else 1f)).toInt().coerceIn(0, 255)
            c.drawCircle(p.x, p.y, p.size * (1f + p.grow * (1f - ratio)), fill)
        }
        fill.alpha = 255
        // search progress over the chest being opened
        inn.chests.firstOrNull { it.openT > 0f && !it.opened }?.let { ch ->
            val f = (ch.openT / Interior.OPEN_TIME).coerceIn(0f, 1f)
            val ty = -ch.y - 130f
            fill.color = 0xCC000000.toInt()
            c.drawRoundRect(ch.x - 60f, ty, ch.x + 60f, ty + 22f, 8f, 8f, fill)
            fill.color = 0xFFFFC107.toInt()
            c.drawRoundRect(ch.x - 56f, ty + 4f, ch.x - 56f + 112f * f, ty + 18f, 6f, 6f, fill)
        }
        for (t in g.texts) {
            val a = (255 * min(1f, t.life)).toInt()
            text.textAlign = Paint.Align.CENTER
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
        c.restore()
        // gloom, and the storm leaking in
        fill.color = 0x30000000
        c.drawRect(0f, 0f, W, H, fill)
        if (g.stormX > inn.b.x - Building.W / 2) {
            fill.color = Color.argb((60 + 30 * sin(g.anim * 5f)).toInt(), 140, 30, 170)
            c.drawRect(0f, 0f, W, H, fill)
        }
    }

    private fun drawFurniture(c: Canvas, p: Platform) {
        val l = p.l
        val rr = p.r
        val t = -p.top
        val wood = 0xFF6D4C41.toInt()
        when (p.kind) {
            0 -> { // table
                fill.color = wood
                c.drawRect(l, t, rr, t + 14f, fill)
                fill.color = shade(wood, 0.75f)
                c.drawRect(l + 10f, t + 14f, l + 22f, 0f, fill)
                c.drawRect(rr - 22f, t + 14f, rr - 10f, 0f, fill)
                fill.color = 0xFFB0BEC5.toInt()
                c.drawRect(l + 40f, t - 18f, l + 60f, t, fill)
                fill.color = 0xFFFFCC80.toInt()
                c.drawOval(rr - 70f, t - 8f, rr - 30f, t, fill)
            }
            1 -> { // shelf: three boards on posts, with junk
                fill.color = shade(wood, 0.7f)
                c.drawRect(l, t, l + 10f, 0f, fill)
                c.drawRect(rr - 10f, t, rr, 0f, fill)
                for (k in 0..2) {
                    val y = t + k * (p.top / 3f)
                    fill.color = wood
                    c.drawRect(l, y, rr, y + 10f, fill)
                    for (j in 0..3) {
                        fill.color = Zombie.SHIRTS[(j + k + l.toInt() / 7) % Zombie.SHIRTS.size]
                        val bx = l + 20f + j * ((rr - l - 40f) / 4f)
                        if (k > 0) c.drawRect(bx, y - 30f + (j % 2) * 8f, bx + 26f, y, fill)
                    }
                }
            }
            2 -> { // sofa
                val col = 0xFF7B3F3F.toInt()
                fill.color = shade(col, 0.8f)
                c.drawRoundRect(l, t - 40f, rr, t + 20f, 16f, 16f, fill)
                fill.color = col
                c.drawRoundRect(l, t, rr, 0f, 12f, 12f, fill)
                fill.color = shade(col, 0.7f)
                c.drawRoundRect(l - 10f, t - 10f, l + 24f, 0f, 10f, 10f, fill)
                c.drawRoundRect(rr - 24f, t - 10f, rr + 10f, 0f, 10f, 10f, fill)
            }
            3 -> { // counter
                val col = if (g.interior?.b?.kind == 4) 0xFFE0E0E0.toInt() else 0xFF8D6E63.toInt()
                fill.color = col
                c.drawRect(l, t, rr, 0f, fill)
                fill.color = shade(col, 0.8f)
                c.drawRect(l, t, rr, t + 12f, fill)
                fill.color = 0xFF455A64.toInt()
                c.drawRect(l + 30f, t - 34f, l + 80f, t, fill)
                fill.color = 0xFF80CBC4.toInt()
                c.drawRect(l + 36f, t - 28f, l + 74f, t - 12f, fill)
            }
            4 -> { // car lift with an old wreck on it
                fill.color = 0xFFF9A825.toInt()
                c.drawRect(l + 20f, t + 10f, l + 34f, 0f, fill)
                c.drawRect(rr - 34f, t + 10f, rr - 20f, 0f, fill)
                fill.color = 0xFF546E7A.toInt()
                c.drawRect(l, t, rr, t + 12f, fill)
                fill.color = 0xFF6D4C41.toInt()
                c.drawRoundRect(l + 10f, t - 56f, rr - 10f, t - 6f, 14f, 14f, fill)
                c.drawRoundRect(l + 60f, t - 90f, rr - 70f, t - 50f, 12f, 12f, fill)
                fill.color = 0xFF263238.toInt()
                c.drawCircle(l + 50f, t - 8f, 18f, fill)
                c.drawCircle(rr - 50f, t - 8f, 18f, fill)
            }
            5 -> { // tall fridge / cabinet
                fill.color = 0xFFCFD8DC.toInt()
                c.drawRoundRect(l, t, rr, 0f, 8f, 8f, fill)
                stroke.color = 0xFF90A4AE.toInt()
                stroke.strokeWidth = 3f
                c.drawLine(l, t + 70f, rr, t + 70f, stroke)
                fill.color = 0xFF78909C.toInt()
                c.drawRect(rr - 16f, t + 20f, rr - 10f, t + 56f, fill)
            }
            else -> { // bed
                fill.color = shade(wood, 0.8f)
                c.drawRect(l, t - 50f, l + 16f, 0f, fill)
                fill.color = 0xFFECEFF1.toInt()
                c.drawRoundRect(l + 10f, t, rr, t + 30f, 10f, 10f, fill)
                fill.color = 0xFF5C6BC0.toInt()
                c.drawRoundRect(l + 70f, t + 2f, rr, t + 34f, 10f, 10f, fill)
                fill.color = wood
                c.drawRect(l, t + 30f, rr, 0f, fill)
            }
        }
    }

    private fun drawChest(c: Canvas, ch: Chest) {
        val x = ch.x
        val b = -ch.y
        val glow = !ch.opened
        if (glow) {
            fill.color = Color.argb((50 + 40 * sin(g.anim * 4f + x)).toInt(), 255, 213, 79)
            c.drawCircle(x, b - 40f, 70f, fill)
        }
        when (ch.kind) {
            3, 5 -> { // fridge / safe
                val col = if (ch.kind == 5) 0xFF455A64.toInt() else 0xFFE0E0E0.toInt()
                fill.color = col
                c.drawRoundRect(x - 36f, b - (if (ch.kind == 5) 76f else 130f), x + 36f, b, 6f, 6f, fill)
                fill.color = shade(col, 0.7f)
                if (ch.kind == 5) {
                    c.drawCircle(x + 8f, b - 40f, 12f, fill)
                    fill.color = 0xFFFFD740.toInt()
                    c.drawCircle(x + 8f, b - 40f, 4f, fill)
                } else {
                    c.drawRect(x + 22f, b - 110f, x + 28f, b - 70f, fill)
                }
                if (ch.opened) {
                    fill.color = 0xFF1A1A1A.toInt()
                    c.drawRect(x - 30f, b - (if (ch.kind == 5) 70f else 124f), x + 30f, b - 6f, fill)
                }
            }
            4 -> { // medicine cabinet
                fill.color = 0xFFFAFAFA.toInt()
                c.drawRoundRect(x - 40f, b - 70f, x + 40f, b, 6f, 6f, fill)
                fill.color = 0xFFE53935.toInt()
                c.drawRect(x - 6f, b - 56f, x + 6f, b - 20f, fill)
                c.drawRect(x - 18f, b - 44f, x + 18f, b - 32f, fill)
            }
            else -> { // cupboard / crate / toolbox
                val col = when (ch.kind) { 0 -> 0xFF8D6E63.toInt(); 2 -> 0xFFC62828.toInt(); else -> 0xFFA1887F.toInt() }
                val h = if (ch.kind == 2) 40f else 62f
                fill.color = col
                c.drawRect(x - 40f, b - h, x + 40f, b, fill)
                fill.color = shade(col, 0.7f)
                c.drawRect(x - 40f, b - h, x + 40f, b - h + 8f, fill)
                if (ch.kind == 1) {
                    stroke.color = shade(col, 0.6f)
                    stroke.strokeWidth = 4f
                    c.drawLine(x - 36f, b - h + 8f, x + 36f, b - 4f, stroke)
                }
                fill.color = 0xFFFFD740.toInt()
                c.drawRect(x - 5f, b - h + 10f, x + 5f, b - h + 22f, fill)
                if (ch.opened) {
                    // lid thrown back
                    fill.color = shade(col, 0.85f)
                    c.save()
                    c.translate(x - 40f, b - h); c.rotate(-60f); c.translate(-(x - 40f), -(b - h))
                    c.drawRect(x - 40f, b - h - 10f, x + 40f, b - h, fill)
                    c.restore()
                }
            }
        }
        if (glow) {
            text.textAlign = Paint.Align.CENTER
            text.textSize = 30f
            text.color = 0xFFFFD740.toInt()
            c.drawText("?", x, b - 150f + sin(g.anim * 3f) * 6f, text)
        }
    }

    // ------------------------------------------------------- warehouse rooms

    /** Who monsters and bosses face: the driver on foot, else the car. */
    private fun targetX() = if (g.driver.out) g.driver.x else g.car.x

    private fun drawRooms(c: Canvas, run: RoomRun) {
        val W = g.W
        val H = g.H
        val rm = run.room
        fill.shader = null
        fill.alpha = 255
        // sunset sky behind everything (seen through the windows and the open ends)
        val sky = when (g.region) {
            Region.DESERT -> intArrayOf(0xFFE8A060.toInt(), 0xFFC8643A.toInt())
            Region.VOLCANO -> intArrayOf(0xFFB04A2A.toInt(), 0xFF5A1E14.toInt())
            Region.NIGHT -> intArrayOf(0xFF2B3A66.toInt(), 0xFF141C33.toInt())
            Region.ALIEN -> intArrayOf(0xFF7A4AA8.toInt(), 0xFF2E1A4A.toInt())
        }
        val hallPic = arrayOf("bg_warehouse", "bg_warehouse_2", "bg_warehouse_3")[rm.palette.coerceIn(0, 2)].let { if (Sprites.has(it)) it else "bg_warehouse" }
        val hall = Sprites.has(hallPic)
        if (hall) {
            backdrop(c, hallPic)
            fill.color = 0x44000000
            c.drawRect(0f, 0f, W, H, fill)
        } else {
            fill.color = sky[1]
            c.drawRect(0f, 0f, W, H, fill)
            fill.color = sky[0]
            c.drawRect(0f, H * 0.3f, W, H, fill)
        }
        c.save()
        if (g.shake > 0f) {
            val s = g.shake * 30f
            c.translate((Random.nextFloat() - 0.5f) * s, (Random.nextFloat() - 0.5f) * s)
        }
        c.scale(Game.ZOOM, Game.ZOOM)
        val camY = -g.viewH * 0.72f
        c.translate(-run.camX, -camY)
        val ceil = -600f
        val x0 = run.camX - 100f
        val x1 = run.camX + g.viewW + 100f
        if (!hall) {
            // far mesas outside
            fill.color = shade(sky[0], 0.75f)
            path.reset()
            path.moveTo(x0 - 200f, 0f)
            var mx = floor((x0 - 200f) / 260f) * 260f
            while (mx < x1 + 300f) {
                path.lineTo(mx, -330f - (hash(mx.toInt()) % 120))
                path.lineTo(mx + 200f, -330f - (hash(mx.toInt()) % 120))
                path.lineTo(mx + 260f, -180f)
                mx += 260f
            }
            path.lineTo(x1 + 300f, 0f)
            path.close()
            c.drawPath(path, fill)
            // the hall: plaster above, brick below, roof trusses
            val plaster = when (rm.palette) { 0 -> 0xFFB79A78.toInt(); 1 -> 0xFFA88D70.toInt(); else -> 0xFF9C8C78.toInt() }
            val brick = 0xFF7A3B2E.toInt()
            fill.color = plaster
            c.drawRect(0f, ceil, rm.width, -260f, fill)
            fill.color = brick
            c.drawRect(0f, -260f, rm.width, 0f, fill)
            stroke.color = shade(brick, 0.75f)
            stroke.strokeWidth = 2f
            var by = -18f
            var row = 0
            while (by > -260f) {
                c.drawLine(0f, by, rm.width, by, stroke)
                var bx = if (row % 2 == 0) 0f else 30f
                while (bx < rm.width) {
                    if (bx > x0 && bx < x1) c.drawLine(bx, by, bx, by + 18f, stroke)
                    bx += 60f
                }
                by -= 18f; row++
            }
            fill.color = shade(plaster, 0.8f)
            c.drawRect(0f, -268f, rm.width, -258f, fill)
            // arched windows glowing with the evening
            var wx = 260f
            while (wx < rm.width - 200f) {
                if (wx > x0 - 300f && wx < x1 + 300f) {
                    for (k in 0..2) {
                        val l = wx + k * 92f
                        fill.color = 0xFF3B2A20.toInt()
                        c.drawRect(l - 6f, -540f, l + 80f, -300f, fill)
                        fill.color = sky[0]
                        c.drawRect(l, -500f, l + 74f, -306f, fill)
                        r.set(l, -540f, l + 74f, -460f)
                        c.drawArc(r, 180f, 180f, true, fill)
                        fill.color = 0x55FFE0A0
                        c.drawRect(l, -480f, l + 30f, -306f, fill)
                        stroke.color = 0xFF3B2A20.toInt()
                        stroke.strokeWidth = 4f
                        c.drawLine(l + 37f, -540f, l + 37f, -306f, stroke)
                        c.drawLine(l, -420f, l + 74f, -420f, stroke)
                        c.drawLine(l, -360f, l + 74f, -360f, stroke)
                    }
                }
                wx += 760f
            }
        }
        // roof
        fill.color = 0xFF4A3A30.toInt()
        c.drawRect(x0, ceil - 120f, x1, ceil, fill)
        stroke.color = 0xFF3A2C24.toInt()
        stroke.strokeWidth = 10f
        var tx = floor(x0 / 240f) * 240f
        while (tx < x1) {
            c.drawLine(tx, ceil, tx + 120f, ceil + 60f, stroke)
            c.drawLine(tx + 240f, ceil, tx + 120f, ceil + 60f, stroke)
            tx += 240f
        }
        c.drawLine(x0, ceil + 60f, x1, ceil + 60f, stroke)
        // background clutter: shelves of boxes, wrecked cars, crates, pallets
        for (dc in rm.decor) {
            val dx = dc[0]
            if (dx < x0 - 200f || dx > x1 + 200f) continue
            val prop = when (dc[1].toInt()) {
                0 -> sprite(c, "prop_shelf", dx, 4f, 240f)
                1 -> sprite(c, "prop_car_rusty", dx, 6f, 120f)
                2 -> sprite(c, "prop_crate_stack", dx, 4f, 120f)
                else -> sprite(c, "prop_pallet", dx, 4f, 60f).also { sprite(c, "prop_barrel", dx + 105f, 4f, 95f) }
            }
            if (!prop) when (dc[1].toInt()) {
                0 -> {
                    fill.color = 0xFF5D4A3A.toInt()
                    c.drawRect(dx - 90f, -220f, dx - 82f, 0f, fill)
                    c.drawRect(dx + 82f, -220f, dx + 90f, 0f, fill)
                    for (k in 0..2) {
                        val y = -220f + k * 75f
                        c.drawRect(dx - 90f, y, dx + 90f, y + 6f, fill)
                        for (j in 0..2) {
                            fill.color = 0xFF9C7B57.toInt()
                            c.drawRect(dx - 80f + j * 55f, y - 44f + 4f * j, dx - 34f + j * 55f, y, fill)
                            fill.color = 0xFF5D4A3A.toInt()
                        }
                    }
                }
                1 -> {
                    fill.color = 0xFF5B4038.toInt()
                    c.drawRoundRect(dx - 120f, -70f, dx + 120f, -18f, 16f, 16f, fill)
                    c.drawRoundRect(dx - 60f, -110f, dx + 60f, -60f, 14f, 14f, fill)
                    fill.color = 0xFF2B2420.toInt()
                    c.drawCircle(dx - 70f, -16f, 20f, fill)
                    c.drawCircle(dx + 70f, -16f, 20f, fill)
                    fill.color = 0x66FFE0A0
                    c.drawRect(dx - 50f, -102f, dx - 6f, -70f, fill)
                }
                2 -> {
                    for (k in 0..2) {
                        fill.color = if (k % 2 == 0) 0xFFA1887F.toInt() else 0xFF8D6E63.toInt()
                        c.drawRect(dx - 40f + k * 30f, -60f - (k % 2) * 56f, dx + 16f + k * 30f, -4f - (k % 2) * 56f, fill)
                    }
                }
                else -> {
                    fill.color = 0xFF6D5A44.toInt()
                    for (k in 0..2) c.drawRect(dx - 70f, -14f - k * 18f, dx + 70f, -4f - k * 18f, fill)
                    fill.color = 0xFF2E7D32.toInt()
                    c.drawRect(dx + 80f, -90f, dx + 130f, 0f, fill)
                }
            }
        }
        // floor
        fill.color = 0xFF4E4842.toInt()
        c.drawRect(x0, 0f, x1, 400f, fill)
        fill.color = 0xFF8A827A.toInt()
        c.drawRect(x0, 0f, x1, 10f, fill)
        fill.color = 0xFF2A2420.toInt()
        c.drawRect(-400f, ceil - 120f, 0f, 400f, fill)
        c.drawRect(rm.width, ceil - 120f, rm.width + 400f, 400f, fill)
        // doors: the one behind and the EXIT ahead
        door(c, rm.entryX, false, true)
        door(c, rm.exitX, true, rm.open)
        if (rm.rest) drawBench(c, rm.benchX, rm.benchUsed)
        // ledges with hazard stripes
        for (p in rm.platforms) {
            if (p.r < x0 || p.l > x1) continue
            val t = -p.top
            if (spriteFill(c, "prop_catwalk", p.l - 6f, t - 6f, p.r + 6f, t + 40f)) continue
            fill.color = 0xFF3A3530.toInt()
            c.drawRect(p.l + 14f, t + 16f, p.l + 22f, t + 40f, fill)
            c.drawRect(p.r - 22f, t + 16f, p.r - 14f, t + 40f, fill)
            fill.color = 0xFF2B2B2B.toInt()
            c.drawRect(p.l, t, p.r, t + 16f, fill)
            fill.color = 0xFFF9C02E.toInt()
            var sx = p.l
            while (sx < p.r) {
                path.reset()
                path.moveTo(sx, t + 16f); path.lineTo(sx + 14f, t); path.lineTo(min(p.r, sx + 28f), t); path.lineTo(min(p.r, sx + 14f), t + 16f); path.close()
                c.drawPath(path, fill)
                sx += 28f
            }
            fill.color = 0x55FFFFFF
            c.drawRect(p.l, t, p.r, t + 3f, fill)
        }
        // money and hearts
        for (cs in rm.cash) {
            if (cs.heart) {
                fill.color = 0xFFE53935.toInt()
                c.drawCircle(cs.x - 7f, cs.y - 4f, 9f, fill)
                c.drawCircle(cs.x + 7f, cs.y - 4f, 9f, fill)
                path.reset()
                path.moveTo(cs.x - 16f, cs.y); path.lineTo(cs.x, cs.y + 16f); path.lineTo(cs.x + 16f, cs.y); path.close()
                c.drawPath(path, fill)
            } else if (!sprite(c, "item_cash", cs.x, cs.y + 14f, 30f)) {
                c.save()
                c.translate(cs.x, cs.y)
                c.rotate(sin(cs.t * 6f + cs.x) * 25f)
                fill.color = 0xFF2E7D32.toInt()
                c.drawRect(-18f, -9f, 18f, 9f, fill)
                fill.color = 0xFF66BB6A.toInt()
                c.drawRect(-15f, -6f, 15f, 6f, fill)
                fill.color = 0xFF2E7D32.toInt()
                c.drawCircle(0f, 0f, 4f, fill)
                c.restore()
            }
        }
        // loot crates still to open
        for (cr in rm.crates) {
            if (cr[2] != 0f || cr[0] < x0 || cr[0] > x1) continue
            val cy = -cr[1]
            if (!sprite(c, "item_crate", cr[0], cy, 64f)) {
                fill.color = 0xFF8D6E63.toInt()
                c.drawRect(cr[0] - 32f, cy - 56f, cr[0] + 32f, cy, fill)
                stroke.color = 0xFF5D4037.toInt()
                stroke.strokeWidth = 5f
                c.drawRect(cr[0] - 32f, cy - 56f, cr[0] + 32f, cy, stroke)
                c.drawLine(cr[0] - 30f, cy - 54f, cr[0] + 30f, cy - 2f, stroke)
            }
            text.textAlign = Paint.Align.CENTER
            text.textSize = 26f
            text.color = 0xFFFFD740.toInt()
            c.drawText("?", cr[0], cy - 70f + sin(g.anim * 3f + cr[0]) * 5f, text)
        }
        // monsters, boss, player
        for (f in rm.foes) {
            val z = f.z
            if (z.x < x0 || z.x > x1) continue
            fill.color = 0x40000000
            val gy = rm.support(z.x, z.y)
            c.drawOval(z.x - z.type.w * 0.7f, gy - 5f, z.x + z.type.w * 0.7f, gy + 5f, fill)
            drawZombie(c, z)
            if (f.slowT > 0f) {
                fill.color = 0x5580D8FF
                c.drawOval(z.x - z.type.w * 0.6f, z.y - z.type.h, z.x + z.type.w * 0.6f, z.y, fill)
            }
            if (f.ranged) {
                fill.color = 0xFF76FF03.toInt()
                c.drawCircle(z.x, z.y - z.type.h - 12f, 5f, fill)
            }
            if (z.hp < z.type.hp * 1.6f) {
                val fr = (z.hp / (z.type.hp * 1.6f)).coerceIn(0f, 1f)
                fill.color = 0xCC000000.toInt()
                c.drawRect(z.x - 22f, z.y - z.type.h - 26f, z.x + 22f, z.y - z.type.h - 20f, fill)
                fill.color = 0xFFE53935.toInt()
                c.drawRect(z.x - 22f, z.y - z.type.h - 26f, z.x - 22f + 44f * fr, z.y - z.type.h - 20f, fill)
            }
        }
        rm.roomBoss?.let { b -> if (b.alive) drawRoomBoss(c, b) }
        // burning aura and orbiting junk around the player
        val d = g.driver
        val match = run.lv(Power.MATCH)
        if (match > 0) {
            fill.color = Color.argb((40 + 20 * sin(g.anim * 8f)).toInt(), 255, 120, 0)
            c.drawOval(d.x - (120f + 20f * match), d.y - 50f - (120f + 20f * match) * 0.6f, d.x + 120f + 20f * match, d.y - 50f + (120f + 20f * match) * 0.6f, fill)
        }
        drawDriver(c)
        val orb = run.lv(Power.ORBIT)
        for (k in 0 until orb) {
            val a = run.orbitA + k * (2f * PI.toFloat() / orb)
            c.save()
            c.translate(d.x + cos(a) * 115f, d.y - 50f + sin(a) * 115f)
            c.rotate(g.anim * 600f)
            fill.color = 0xFF90A4AE.toInt()
            path.reset()
            for (j in 0 until 6) {
                val rr = if (j % 2 == 0) 20f else 9f
                val aa = j * PI.toFloat() / 3f
                if (j == 0) path.moveTo(cos(aa) * rr, sin(aa) * rr) else path.lineTo(cos(aa) * rr, sin(aa) * rr)
            }
            path.close()
            c.drawPath(path, fill)
            fill.color = 0xFFD32F2F.toInt()
            c.drawCircle(0f, 0f, 5f, fill)
            c.restore()
        }
        for (s in g.roomProjectiles) sideShot(c, s)
        for (s in rm.shots) {
            when (s.kind) {
                0 -> {
                    fill.color = 0xFF76FF03.toInt()
                    c.drawCircle(s.x, s.y, 9f, fill)
                    fill.color = 0x8833691E.toInt()
                    c.drawCircle(s.x - s.vx * 0.02f, s.y - s.vy * 0.02f, 7f, fill)
                }
                1 -> {
                    fill.color = 0xCCFF6D00.toInt()
                    c.drawCircle(s.x, s.y, 16f, fill)
                    fill.color = 0xFFFFEB3B.toInt()
                    c.drawCircle(s.x, s.y, 8f, fill)
                }
                else -> {
                    c.save()
                    c.translate(s.x, s.y)
                    c.rotate(g.anim * 720f)
                    fill.color = 0xFFC62828.toInt()
                    c.drawRect(-6f, -16f, 6f, 16f, fill)
                    fill.color = 0xFFFFD740.toInt()
                    c.drawCircle(0f, -18f, 4f + Random.nextFloat() * 3f, fill)
                    c.restore()
                }
            }
        }
        for (z in run.zaps) {
            stroke.color = 0xFF80D8FF.toInt()
            stroke.strokeWidth = 5f
            stroke.alpha = (255 * (z.life / 0.22f)).toInt().coerceIn(0, 255)
            var i = 0
            while (i + 3 < z.pts.size) {
                val ax = z.pts[i]; val ay = z.pts[i + 1]; val bx = z.pts[i + 2]; val by2 = z.pts[i + 3]
                val mx2 = (ax + bx) / 2 + (Random.nextFloat() - 0.5f) * 40f
                val my2 = (ay + by2) / 2 + (Random.nextFloat() - 0.5f) * 40f
                c.drawLine(ax, ay, mx2, my2, stroke)
                c.drawLine(mx2, my2, bx, by2, stroke)
                i += 2
            }
            stroke.alpha = 255
        }
        for (p in g.particles) {
            val ratio = (p.life / p.maxLife).coerceIn(0f, 1f)
            fill.color = p.color
            fill.alpha = (255 * ratio * (if (p.grow > 0f) 0.55f else 1f)).toInt().coerceIn(0, 255)
            c.drawCircle(p.x, p.y, p.size * (1f + p.grow * (1f - ratio)), fill)
        }
        fill.alpha = 255
        for (t in g.texts) {
            val a = (255 * min(1f, t.life)).toInt()
            text.textAlign = Paint.Align.CENTER
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
        c.restore()
        if (run.fade > 0f) {
            fill.color = Color.argb((255 * run.fade.coerceIn(0f, 1f)).toInt(), 0, 0, 0)
            c.drawRect(0f, 0f, W, H, fill)
        }
    }

    private fun door(c: Canvas, x: Float, exit: Boolean, open: Boolean) {
        val pic = if (exit && !open && Sprites.has("prop_door_locked")) "prop_door_locked" else "prop_door"
        if (spriteFill(c, pic, x - 62f, -250f, x + 62f, 0f)) {
            if (!exit) return
            fill.color = if (open) 0xFF1B5E20.toInt() else 0xFF7F1D1D.toInt()
            c.drawRoundRect(x - 50f, -300f, x + 50f, -262f, 6f, 6f, fill)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 24f
            text.color = if (open) 0xFFB9F6CA.toInt() else 0xFFFFCDD2.toInt()
            c.drawText(if (open) "EXIT ➜" else "KHÓA", x, -272f, text)
            if (open) {
                fill.color = Color.argb((60 + 50 * sin(g.anim * 5f)).toInt(), 105, 240, 174)
                c.drawRect(x - 62f, -250f, x + 62f, 0f, fill)
            }
            return
        }
        fill.color = 0xFF3E3A36.toInt()
        c.drawRect(x - 62f, -250f, x + 62f, 0f, fill)
        fill.color = if (exit) 0xFF4DB6AC.toInt() else 0xFF5B7F6E.toInt()
        c.drawRect(x - 50f, -238f, x + 50f, 0f, fill)
        fill.color = shade(if (exit) 0xFF4DB6AC.toInt() else 0xFF5B7F6E.toInt(), 0.8f)
        c.drawRect(x - 38f, -220f, x + 38f, -140f, fill)
        fill.color = 0xFFCFD8DC.toInt()
        c.drawRect(x + 30f, -126f, x + 42f, -118f, fill)
        if (!exit) return
        // EXIT sign, red while locked; planks across a locked door
        fill.color = if (open) 0xFF1B5E20.toInt() else 0xFF7F1D1D.toInt()
        c.drawRoundRect(x - 50f, -300f, x + 50f, -262f, 6f, 6f, fill)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 24f
        text.color = if (open) 0xFFB9F6CA.toInt() else 0xFFFFCDD2.toInt()
        c.drawText(if (open) "EXIT ➜" else "KHÓA", x, -272f, text)
        if (open) {
            fill.color = Color.argb((60 + 50 * sin(g.anim * 5f)).toInt(), 105, 240, 174)
            c.drawRect(x - 62f, -250f, x + 62f, 0f, fill)
        } else {
            fill.color = 0xFF8D6E63.toInt()
            c.save()
            c.translate(x, -160f); c.rotate(-18f); c.drawRect(-70f, -10f, 70f, 10f, fill)
            c.restore()
            c.save()
            c.translate(x, -80f); c.rotate(14f); c.drawRect(-70f, -10f, 70f, 10f, fill)
            c.restore()
        }
    }

    private fun drawRoomBoss(c: Canvas, b: RoomBoss) {
        val x = b.x
        val hit = if (b.flash > 0f) 160 else 255
        val face = b.dir > 0f
        val drawn = when (b.kind) {
            0 -> sprite(c, "item_crate", x, 0f, 80f) &&
                sprite(c, "def_turret_cannon", x, -70f + (if (b.fireCd < 0.15f) 4f else 0f), 120f, flip = g.driver.x < x, alpha = hit)
            1 -> walkSprite(c, "boss_brute", x, 0f, b.h * 1.25f, !face, b.anim * (if (b.chargeT > 0f) 14f else 5f),
                if (b.chargeT > 0f) 30f else 18f, hit, if (b.chargeT > 0f) 10f else 3f)
            else -> Sprites.boss(b.look.tier)?.let { bossSprite(c, it, x, 0f, b.look, hit) } ?: false
        }
        if (!drawn) when (b.kind) {
            0 -> { // cannon turret on a crate base
                fill.color = 0xFF5D4037.toInt()
                c.drawRect(x - 70f, -70f, x + 70f, 0f, fill)
                fill.color = if (b.flash > 0f) Color.WHITE else 0xFFC62828.toInt()
                c.drawRoundRect(x - 55f, -140f, x + 55f, -66f, 20f, 20f, fill)
                c.save()
                c.translate(x, -110f)
                val a = atan2Deg(g.driver.y - 60f + 110f, g.driver.x - x)
                c.rotate(a)
                fill.color = 0xFF37474F.toInt()
                c.drawRect(0f, -14f, 90f, 14f, fill)
                fill.color = 0xFFFF6D00.toInt()
                c.drawRect(84f, -16f, 94f, 16f, fill)
                c.restore()
                fill.color = 0xFFFFD740.toInt()
                c.drawCircle(x - 30f, -116f, 6f, fill)
            }
            1 -> { // butcher brute
                c.save()
                c.translate(x, 0f)
                c.scale(2.1f * (if (b.dir < 0f) -1f else 1f) * -1f, 2.1f)
                zombieFigure(c, 60f, 92f, 0xFF6D1B1B.toInt(), sin(b.anim * (if (b.chargeT > 0f) 14f else 4f)), 1f, true)
                c.restore()
                if (b.flash > 0f) {
                    fill.color = 0x88FFFFFF.toInt()
                    c.drawOval(x - 60f, -190f, x + 60f, 0f, fill)
                }
            }
            else -> {
                b.look.y = 0f
                b.look.flash = b.flash
                drawBossAny(c, b.look)
            }
        }
        // health bar above
        val f = (b.hp / b.maxHp).coerceIn(0f, 1f)
        fill.color = 0xCC000000.toInt()
        c.drawRoundRect(x - 90f, -b.h - 60f, x + 90f, -b.h - 44f, 6f, 6f, fill)
        fill.color = 0xFFD50000.toInt()
        c.drawRoundRect(x - 87f, -b.h - 57f, x - 87f + 174f * f, -b.h - 47f, 4f, 4f, fill)
    }

    /** HUD over the rooms: level bar, room counter, health, power-ups, controls. */
    internal fun drawRoomHud(c: Canvas, run: RoomRun) {
        val W = g.W
        val d = g.driver
        // level bar across the top
        val l = W * 0.18f
        val rr = W * 0.78f
        fill.color = 0xCC1B1714.toInt()
        c.drawRoundRect(l, 14f, rr, 46f, 14f, 14f, fill)
        val xf = (run.xp.toFloat() / run.xpNeed).coerceIn(0f, 1f)
        fill.color = 0xFF29B6F6.toInt()
        if (xf > 0f) c.drawRoundRect(l + 4f, 18f, l + 4f + (rr - l - 8f) * xf, 42f, 11f, 11f, fill)
        fill.color = 0x55FFFFFF
        c.drawRect(l + 10f, 20f, l + 4f + (rr - l - 8f) * xf - 6f, 24f, fill)
        text.textAlign = Paint.Align.LEFT
        text.textSize = 22f
        text.color = Color.WHITE
        c.drawText("Cấp ${run.level}", l + 16f, 38f, text)
        // room counter
        text.textAlign = Paint.Align.CENTER
        val bossRoom = run.room.boss
        val label = "🚪 Phòng ${run.index} / ${run.total}${if (bossRoom) "  👹" else if (run.room.rest) "  • PHÒNG NGHỈ" else ""}"
        text.textSize = 28f
        val tw = text.measureText(label)
        glassPanel(c, W / 2 - tw / 2 - 24f, 56f, W / 2 + tw / 2 + 24f, 100f, 16f)
        text.color = if (bossRoom) 0xFFFF8A80.toInt() else Color.WHITE
        c.drawText(label, W / 2, 88f, text)
        // health and money
        glassPanel(c, 12f, 12f, 230f, 100f)
        val hf = (d.hp / d.maxHp).coerceIn(0f, 1f)
        bar(c, 22f, 22f, 170f, hf, if (hf > 0.3f) 0xFF43A047.toInt() else 0xFFE53935.toInt(), "♥")
        text.textAlign = Paint.Align.LEFT
        text.textSize = 24f
        text.color = Color.WHITE
        c.drawText("${d.hp.toInt()}/${d.maxHp.toInt()}", 64f, 86f, text)
        val money = "+${g.runMoney}$"
        text.textSize = 30f
        val mw = text.measureText(money)
        glassPanel(c, W - 132f - mw, 18f, W - 108f, 86f, 30f)
        text.textAlign = Paint.Align.RIGHT
        text.color = 0xFF69F0AE.toInt()
        c.drawText(money, W - 120f, 64f, text)
        drawButton(c, g.pauseBtn, false)
        // power-ups owned
        var px = 22f
        text.textAlign = Paint.Align.CENTER
        for (p in Power.entries) {
            val n = run.lv(p)
            if (n == 0) continue
            fill.color = 0xAA000000.toInt()
            c.drawRoundRect(px, 110f, px + 44f, 154f, 8f, 8f, fill)
            text.textSize = 24f
            text.color = Color.WHITE
            if (!spriteBox(c, Sprites.power(p), px + 3f, 113f, px + 41f, 151f)) c.drawText(p.icon, px + 22f, 140f, text)
            if (n > 1) {
                text.textSize = 16f
                text.color = 0xFFFFD740.toInt()
                c.drawText("$n", px + 37f, 152f, text)
            }
            px += 50f
            if (px > W * 0.4f) break
        }
        if (run.roomNoteT > 0f && run.room.boss) {
            title(c, "PHÒNG TRÙM!", W / 2, g.H * 0.36f, 0xFFFF5252.toInt(), 64f)
        }
        if (g.state == State.PLAYING && g.dyingT <= 0f) {
            for (b in g.controls()) drawButton(c, b, g.isHeld(b.id))
            drawJoystick(c)
        }
        if (g.stormHurt > 0f) {
            fill.color = 0x40E040FB
            c.drawRect(0f, 0f, W, g.H, fill)
        }
    }

    /** Floating stick: where the thumb went down, and the knob following it. */
    internal fun drawJoystick(c: Canvas) {
        if (!g.driver.out) return
        val on = g.joyId >= 0
        val bx = if (on) g.joyX else 190f
        val by = if (on) g.joyY else g.H - 150f
        fill.color = if (on) 0x44FFFFFF else 0x26FFFFFF
        c.drawCircle(bx, by, 92f, fill)
        stroke.color = 0x88FFFFFF.toInt()
        stroke.strokeWidth = 3f
        c.drawCircle(bx, by, 92f, stroke)
        fill.color = 0x99FFFFFF.toInt()
        val kx = bx + (if (on) g.joyDx else 0f)
        val ky = by + (if (on) g.joyDy else 0f)
        c.drawCircle(kx, ky, 42f, fill)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 26f
        text.color = 0x99FFFFFF.toInt()
        c.drawText("◀", bx - 66f, by + 9f, text)
        c.drawText("▶", bx + 66f, by + 9f, text)
    }

    /** Level-up power-ups, the merchant's mystery boxes, or the boss upgrade. */
    internal fun drawPick(c: Canvas, run: RoomRun) {
        val W = g.W
        dim(c, 0xC8)
        val (head, sub) = when (run.pickKind) {
            1 -> "LÁI BUÔN" to "Chọn 1 hộp quà bí ẩn"
            3 -> "BÀN ĐỒ NGHỀ" to "Chọn 1 việc"
            2 -> "CHỌN NÂNG CẤP" to "Phần thưởng hạ trùm"
            else -> "LÊN CẤP ${run.level}!" to "Chọn 1 sức mạnh"
        }
        fill.color = 0xFFB71C1C.toInt()
        c.drawRoundRect(W / 2 - 330f, 64f, W / 2 + 330f, 136f, 14f, 14f, fill)
        title(c, head, W / 2, 122f, Color.WHITE, 50f)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 24f
        text.color = 0xFFE0E0E0.toInt()
        c.drawText(sub, W / 2, 176f, text)
        if (run.pickKind == 1) drawMerchant(c)
        val n = run.choices.size
        for (i in 0 until n) {
            val p = run.choices[i]
            val rc = g.pickCard(i, n)
            metalPanel(c, rc[0], rc[1], rc[2], rc[3], 18f)
            val cx = (rc[0] + rc[2]) / 2
            // photo-like frame with the icon
            fill.color = 0xFFF5F0E6.toInt()
            c.drawRect(cx - 90f, rc[1] + 26f, cx + 90f, rc[1] + 196f, fill)
            fill.color = 0xFF263238.toInt()
            c.drawRect(cx - 80f, rc[1] + 36f, cx + 80f, rc[1] + 170f, fill)
            if (run.pickKind == 3) {
                val (ic, tt, ds) = g.benchOption(i)
                text.textSize = 76f
                text.color = Color.WHITE
                c.drawText(ic, cx, rc[1] + 132f, text)
                fitText(c, tt, cx, rc[1] + 236f, 30f, rc[2] - rc[0] - 24f)
                text.color = 0xFFB0BEC5.toInt()
                wrapText(c, ds, cx, rc[1] + 274f, rc[2] - rc[0] - 30f, 21f, 26f)
            } else if (run.pickKind == 1) {
                // closed metal case
                fill.color = 0xFFB0BEC5.toInt()
                c.drawRoundRect(cx - 62f, rc[1] + 80f, cx + 62f, rc[1] + 150f, 10f, 10f, fill)
                fill.color = 0xFF78909C.toInt()
                c.drawRect(cx - 62f, rc[1] + 104f, cx + 62f, rc[1] + 112f, fill)
                fill.color = 0xFFFFC107.toInt()
                c.drawRect(cx - 10f, rc[1] + 98f, cx + 10f, rc[1] + 118f, fill)
                text.textSize = 34f
                text.color = Color.WHITE
                c.drawText("??????", cx, rc[1] + 250f, text)
            } else {
                text.textSize = 76f
                text.color = Color.WHITE
                if (!spriteBox(c, Sprites.power(p), cx - 78f, rc[1] + 38f + sin(g.anim * 3f + i) * 5f, cx + 78f, rc[1] + 168f + sin(g.anim * 3f + i) * 5f)) c.drawText(p.icon, cx, rc[1] + 132f, text)
                text.color = Color.WHITE
                fitText(c, p.title, cx, rc[1] + 236f, 30f, rc[2] - rc[0] - 24f)
                text.color = 0xFFB0BEC5.toInt()
                wrapText(c, p.desc, cx, rc[1] + 274f, rc[2] - rc[0] - 30f, 21f, 26f)
                val lv = run.lv(p)
                text.color = 0xFFFFD740.toInt()
                text.textSize = 22f
                c.drawText(if (lv == 0) "MỚI" else "Cấp $lv → ${lv + 1}", cx, rc[3] - 22f, text)
            }
        }
    }
}
