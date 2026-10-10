package com.khongvinh.carsurvival

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Draws every game state with plain Canvas primitives (no bitmaps needed). */
class Renderer(private val g: Game) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val path = Path()
    private val r = RectF()

    fun render(c: Canvas) {
        c.save()
        if (g.shake > 0f) {
            val s = g.shake * 28f
            c.translate((Random.nextFloat() - 0.5f) * s, (Random.nextFloat() - 0.5f) * s)
        }
        when (g.state) {
            State.MENU -> {
                drawRoad(c, g.menuScroll)
                drawMenu(c)
            }
            State.SHOP -> {
                drawRoad(c, g.menuScroll)
                drawShop(c)
            }
            else -> {
                drawRoad(c, g.distance)
                drawWorld(c)
                drawStorm(c)
                drawHud(c)
                drawOverlay(c)
            }
        }
        c.restore()
    }

    // ------------------------------------------------------------ background

    private fun drawRoad(c: Canvas, scroll: Float) {
        val W = g.W
        val H = g.H
        // Grass with scrolling stripes
        c.drawColor(0xFF2E7D32.toInt())
        fill.color = 0xFF388E3C.toInt()
        val stripe = 120f
        var y = (scroll % (stripe * 2)) - stripe * 2
        while (y < H) {
            c.drawRect(0f, y, W, y + stripe, fill)
            y += stripe * 2
        }
        // Trees
        for (t in g.trees) {
            fill.color = 0x44000000
            c.drawCircle(t.x + 6f, t.y + 8f, t.size, fill)
            fill.color = if (t.dark) 0xFF1B5E20.toInt() else 0xFF43A047.toInt()
            c.drawCircle(t.x, t.y, t.size, fill)
            fill.color = if (t.dark) 0xFF2E7D32.toInt() else 0xFF66BB6A.toInt()
            c.drawCircle(t.x - t.size * 0.3f, t.y - t.size * 0.3f, t.size * 0.5f, fill)
        }
        // Asphalt
        fill.color = 0xFF37474F.toInt()
        c.drawRect(Game.ROAD_LEFT, 0f, Game.ROAD_RIGHT, H, fill)
        // Curbs
        val curb = 40f
        y = (scroll % (curb * 2)) - curb * 2
        var red = true
        while (y < H) {
            fill.color = if (red) 0xFFE53935.toInt() else Color.WHITE
            c.drawRect(Game.ROAD_LEFT - 12f, y, Game.ROAD_LEFT, y + curb, fill)
            c.drawRect(Game.ROAD_RIGHT, y, Game.ROAD_RIGHT + 12f, y + curb, fill)
            y += curb
            red = !red
        }
        // Lane dashes
        fill.color = 0xCCFFFFFF.toInt()
        val dash = 90f
        for (i in 1 until Game.LANES) {
            val x = Game.ROAD_LEFT + g.laneW * i
            y = (scroll % dash) - dash
            while (y < H) {
                c.drawRect(x - 3f, y, x + 3f, y + dash * 0.5f, fill)
                y += dash
            }
        }
    }

    // ----------------------------------------------------------------- world

    private fun drawWorld(c: Canvas) {
        drawFinishLine(c)
        for (o in g.obstacles) if (o.type == ObstacleType.OIL) drawObstacle(c, o)
        for (o in g.obstacles) if (o.type != ObstacleType.OIL) drawObstacle(c, o)
        for (it in g.items) drawItem(c, it)
        fill.color = 0xFFFFEB3B.toInt()
        for (b in g.bullets) {
            fill.color = 0x66FF9800
            c.drawRoundRect(b.x - 5f, b.y, b.x + 5f, b.y + 40f, 5f, 5f, fill)
            fill.color = 0xFFFFEB3B.toInt()
            c.drawRoundRect(b.x - 4f, b.y - 10f, b.x + 4f, b.y + 14f, 4f, 4f, fill)
        }
        if (g.state != State.GAME_OVER) drawPlayer(c)
        for (p in g.particles) {
            fill.color = p.color
            fill.alpha = (255 * (p.life / p.maxLife)).toInt().coerceIn(0, 255)
            c.drawCircle(p.x, p.y, p.size, fill)
        }
        fill.alpha = 255
        for (t in g.texts) {
            text.textSize = 40f
            text.color = Color.BLACK
            text.alpha = (255 * min(1f, t.life)).toInt()
            c.drawText(t.text, t.x + 2f, t.y + 2f, text)
            text.color = t.color
            text.alpha = (255 * min(1f, t.life)).toInt()
            c.drawText(t.text, t.x, t.y, text)
        }
        text.alpha = 255
    }

    private fun drawFinishLine(c: Canvas) {
        val y = g.player.y - (g.goal - g.distance)
        if (y < -80f || y > g.H + 80f) return
        val sq = 30f
        var x = Game.ROAD_LEFT
        var i = 0
        while (x < Game.ROAD_RIGHT) {
            for (row in 0 until 2) {
                fill.color = if ((i + row) % 2 == 0) Color.WHITE else Color.BLACK
                c.drawRect(x, y - sq + row * sq, min(x + sq, Game.ROAD_RIGHT), y + row * sq, fill)
            }
            x += sq
            i++
        }
        text.textSize = 44f
        text.color = Color.WHITE
        c.drawText("ĐÍCH", g.W / 2, y - sq - 16f, text)
    }

    private fun drawPlayer(c: Canvas) {
        val p = g.player
        val blink = p.hurtT > 0f && ((g.anim * 20f).toInt() % 2 == 0)
        if (!blink) drawCar(c, p.x, p.y, p.w, p.h, 0xFFE53935.toInt(), p.tilt, true)
        if (p.weaponT > 0f) {
            // twin guns on the hood
            fill.color = 0xFF263238.toInt()
            c.drawRect(p.x - 24f, p.y - p.h / 2 - 8f, p.x - 16f, p.y - p.h / 2 + 22f, fill)
            c.drawRect(p.x + 16f, p.y - p.h / 2 - 8f, p.x + 24f, p.y - p.h / 2 + 22f, fill)
        }
        if (p.shieldT > 0f) {
            val fade = if (p.shieldT < 1.5f && (g.anim * 8f).toInt() % 2 == 0) 0.4f else 1f
            fill.color = 0x3340C4FF
            fill.alpha = (0x33 * fade).toInt()
            c.drawCircle(p.x, p.y, p.h * 0.68f, fill)
            fill.alpha = 255
            stroke.color = 0xFF40C4FF.toInt()
            stroke.alpha = (220 * fade).toInt()
            stroke.strokeWidth = 5f + sin(g.anim * 10f) * 2f
            c.drawCircle(p.x, p.y, p.h * 0.68f, stroke)
            stroke.alpha = 255
        }
        if (g.countdown <= 0f && p.speed > p.maxSpeed * 0.8f && g.state == State.PLAYING) {
            fill.color = 0x88FFAB40.toInt()
            val f = 10f + Random.nextFloat() * 14f
            c.drawCircle(p.x - 18f, p.y + p.h / 2 + f * 0.5f, 7f, fill)
            c.drawCircle(p.x + 18f, p.y + p.h / 2 + f * 0.5f, 7f, fill)
        }
    }

    private fun drawCar(c: Canvas, x: Float, y: Float, w: Float, h: Float, body: Int, tilt: Float, player: Boolean) {
        c.save()
        c.translate(x, y)
        c.rotate(tilt)
        val hw = w / 2
        val hh = h / 2
        fill.color = 0x55000000
        c.drawRoundRect(-hw + 6f, -hh + 10f, hw + 6f, hh + 10f, w * 0.3f, w * 0.3f, fill)
        fill.color = 0xFF111111.toInt()
        c.drawRoundRect(-hw - 5f, -hh * 0.72f, -hw + 8f, -hh * 0.3f, 4f, 4f, fill)
        c.drawRoundRect(hw - 8f, -hh * 0.72f, hw + 5f, -hh * 0.3f, 4f, 4f, fill)
        c.drawRoundRect(-hw - 5f, hh * 0.35f, -hw + 8f, hh * 0.78f, 4f, 4f, fill)
        c.drawRoundRect(hw - 8f, hh * 0.35f, hw + 5f, hh * 0.78f, 4f, 4f, fill)
        fill.color = body
        c.drawRoundRect(-hw, -hh, hw, hh, w * 0.32f, w * 0.32f, fill)
        // roof highlight
        fill.color = 0x33FFFFFF
        c.drawRoundRect(-hw * 0.7f, -hh * 0.1f, hw * 0.7f, hh * 0.3f, 10f, 10f, fill)
        // windshield & rear window
        fill.color = 0xFF263238.toInt()
        c.drawRoundRect(-hw * 0.74f, -hh * 0.42f, hw * 0.74f, -hh * 0.1f, 8f, 8f, fill)
        c.drawRoundRect(-hw * 0.68f, hh * 0.38f, hw * 0.68f, hh * 0.6f, 8f, 8f, fill)
        if (player) {
            fill.color = Color.WHITE
            c.drawRect(-6f, -hh + 4f, 6f, -hh * 0.42f, fill)
            c.drawRect(-6f, -hh * 0.1f, 6f, hh * 0.38f, fill)
        }
        // lights
        fill.color = 0xFFFFF59D.toInt()
        c.drawCircle(-hw * 0.6f, -hh + 8f, 6f, fill)
        c.drawCircle(hw * 0.6f, -hh + 8f, 6f, fill)
        fill.color = 0xFFFF1744.toInt()
        c.drawRect(-hw * 0.8f, hh - 8f, -hw * 0.4f, hh - 3f, fill)
        c.drawRect(hw * 0.4f, hh - 8f, hw * 0.8f, hh - 3f, fill)
        c.restore()
    }

    private fun drawTruck(c: Canvas, o: Obstacle) {
        val hw = o.type.w / 2
        val hh = o.type.h / 2
        c.save()
        c.translate(o.x, o.y)
        fill.color = 0x55000000
        c.drawRoundRect(-hw + 6f, -hh + 10f, hw + 6f, hh + 10f, 10f, 10f, fill)
        fill.color = 0xFF111111.toInt()
        for (wy in floatArrayOf(-hh + 20f, hh * 0.2f, hh - 50f)) {
            c.drawRect(-hw - 5f, wy, -hw + 6f, wy + 34f, fill)
            c.drawRect(hw - 6f, wy, hw + 5f, wy + 34f, fill)
        }
        // cab
        fill.color = o.color
        c.drawRoundRect(-hw, -hh, hw, -hh + 64f, 14f, 14f, fill)
        fill.color = 0xFF263238.toInt()
        c.drawRoundRect(-hw * 0.75f, -hh + 12f, hw * 0.75f, -hh + 32f, 6f, 6f, fill)
        // trailer
        fill.color = 0xFFCFD8DC.toInt()
        c.drawRoundRect(-hw, -hh + 70f, hw, hh, 6f, 6f, fill)
        stroke.color = 0xFF90A4AE.toInt()
        stroke.strokeWidth = 3f
        var ly = -hh + 90f
        while (ly < hh - 10f) {
            c.drawLine(-hw + 8f, ly, hw - 8f, ly, stroke)
            ly += 26f
        }
        c.restore()
    }

    private fun drawObstacle(c: Canvas, o: Obstacle) {
        val x = o.x
        val y = o.y
        when (o.type) {
            ObstacleType.CAR -> drawCar(c, x, y, o.type.w, o.type.h, o.color, 0f, false)
            ObstacleType.TRUCK -> drawTruck(c, o)
            ObstacleType.BARREL -> {
                fill.color = 0x55000000
                c.drawCircle(x + 5f, y + 7f, 29f, fill)
                fill.color = o.color
                c.drawCircle(x, y, 29f, fill)
                stroke.color = 0xFFBF360C.toInt()
                stroke.strokeWidth = 5f
                c.drawCircle(x, y, 20f, stroke)
                c.drawCircle(x, y, 10f, stroke)
                text.textSize = 22f
                text.color = Color.BLACK
                c.drawText("☢", x, y + 8f, text)
            }
            ObstacleType.ROCK -> {
                fill.color = 0x55000000
                c.drawOval(x - 40f, y - 26f, x + 46f, y + 40f, fill)
                path.reset()
                path.moveTo(x - 40f, y + 10f)
                path.lineTo(x - 28f, y - 26f)
                path.lineTo(x + 2f, y - 36f)
                path.lineTo(x + 32f, y - 20f)
                path.lineTo(x + 42f, y + 14f)
                path.lineTo(x + 18f, y + 34f)
                path.lineTo(x - 22f, y + 32f)
                path.close()
                fill.color = 0xFF616161.toInt()
                c.drawPath(path, fill)
                fill.color = 0xFF9E9E9E.toInt()
                c.drawCircle(x - 8f, y - 12f, 14f, fill)
                fill.color = 0xFF757575.toInt()
                c.drawCircle(x + 14f, y + 10f, 12f, fill)
            }
            ObstacleType.CONE -> {
                fill.color = 0x55000000
                c.drawCircle(x + 4f, y + 18f, 18f, fill)
                fill.color = 0xFF212121.toInt()
                c.drawRect(x - 20f, y + 12f, x + 20f, y + 22f, fill)
                path.reset()
                path.moveTo(x, y - 23f)
                path.lineTo(x + 16f, y + 14f)
                path.lineTo(x - 16f, y + 14f)
                path.close()
                fill.color = o.color
                c.drawPath(path, fill)
                fill.color = Color.WHITE
                c.drawRect(x - 9f, y - 4f, x + 9f, y + 3f, fill)
            }
            ObstacleType.OIL -> {
                fill.color = 0xE6101010.toInt()
                c.drawOval(x - 58f, y - 34f, x + 50f, y + 34f, fill)
                c.drawOval(x - 20f, y - 42f, x + 58f, y + 18f, fill)
                fill.color = 0x553F51B5
                c.drawOval(x - 30f, y - 22f, x + 10f, y - 6f, fill)
                fill.color = 0x4400BCD4
                c.drawOval(x + 6f, y - 28f, x + 34f, y - 14f, fill)
            }
        }
        if (o.flash > 0f) {
            fill.color = 0x99FFFFFF.toInt()
            c.drawCircle(x, y, max(o.type.w, o.type.h) * 0.4f, fill)
        }
    }

    private fun drawItem(c: Canvas, it: Item) {
        val bob = sin(g.anim * 5f + it.x) * 5f
        val y = it.y + bob
        fill.color = it.type.color
        fill.alpha = 70
        c.drawCircle(it.x, y, 40f + sin(g.anim * 8f) * 4f, fill)
        fill.alpha = 255
        c.drawCircle(it.x, y, 30f, fill)
        stroke.color = Color.WHITE
        stroke.strokeWidth = 4f
        c.drawCircle(it.x, y, 30f, stroke)
        text.textSize = if (it.type == ItemType.COIN) 38f else 32f
        text.color = Color.WHITE
        c.drawText(it.type.icon, it.x, y + text.textSize * 0.36f, text)
    }

    // ----------------------------------------------------------------- storm

    private fun drawStorm(c: Canvas) {
        val radius = g.stormRadius()
        val cx = g.W / 2
        val cy = g.player.y
        val pulse = 0.5f + 0.5f * sin(g.anim * 6f)
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(-50f, -50f, g.W + 50f, g.H + 50f, Path.Direction.CW)
        if (radius > 0f) path.addCircle(cx, cy, radius, Path.Direction.CW)
        fill.color = Color.argb((110 + 40 * pulse).toInt(), 123, 31, 162)
        c.drawPath(path, fill)
        path.fillType = Path.FillType.WINDING
        if (radius > 0f) {
            stroke.color = 0xFFE040FB.toInt()
            stroke.strokeWidth = 8f
            stroke.alpha = (180 + 75 * pulse).toInt()
            c.drawCircle(cx, cy, radius, stroke)
            stroke.color = Color.WHITE
            stroke.strokeWidth = 2f
            c.drawCircle(cx, cy, radius - 6f, stroke)
            stroke.alpha = 255
        }
        if (g.stormHurt > 0f) {
            fill.color = 0x40FF00FF
            c.drawRect(0f, 0f, g.W, g.H, fill)
        }
    }

    // ------------------------------------------------------------------- HUD

    private fun drawHud(c: Canvas) {
        val W = g.W
        val p = g.player
        fill.color = 0xAA000000.toInt()
        c.drawRect(0f, 0f, W, 170f + g.topInset, fill)
        c.save()
        c.translate(0f, g.topInset)

        // HP bar
        text.textAlign = Paint.Align.LEFT
        text.textSize = 26f
        text.color = Color.WHITE
        c.drawText("MÁU", 22f, 46f, text)
        r.set(22f, 56f, 262f, 84f)
        fill.color = 0xFF424242.toInt()
        c.drawRoundRect(r, 10f, 10f, fill)
        val hpFrac = (p.hp / p.maxHp).coerceIn(0f, 1f)
        fill.color = when {
            hpFrac > 0.5f -> 0xFF43A047.toInt()
            hpFrac > 0.25f -> 0xFFFFA000.toInt()
            else -> 0xFFE53935.toInt()
        }
        r.right = r.left + (r.width() * hpFrac)
        c.drawRoundRect(r, 10f, 10f, fill)
        text.textSize = 22f
        c.drawText("${max(0, p.hp.toInt())}/${p.maxHp.toInt()}", 100f, 79f, text)

        // Level & status
        text.textSize = 28f
        text.color = 0xFFB0BEC5.toInt()
        c.drawText("MÀN ${g.level}", 22f, 122f, text)
        var sx = 150f
        if (p.shieldT > 0f) {
            text.color = 0xFF82B1FF.toInt()
            c.drawText("🛡 ${p.shieldT.toInt() + 1}s", sx, 122f, text)
            sx += 110f
        }
        if (p.weaponT > 0f) {
            text.color = 0xFFFF8A80.toInt()
            c.drawText("🚀 ${p.weaponT.toInt() + 1}s", sx, 122f, text)
        }

        // Money
        text.textAlign = Paint.Align.RIGHT
        text.textSize = 34f
        text.color = 0xFFFFD740.toInt()
        c.drawText("${g.save.money} $", W - 110f, 70f, text)

        // Timer
        text.textAlign = Paint.Align.CENTER
        val t = g.timeLeft
        val danger = t <= 10f
        text.textSize = if (danger) 64f + sin(g.anim * 10f) * 4f else 60f
        text.color = when {
            t <= 0f -> 0xFFE040FB.toInt()
            danger -> 0xFFFF5252.toInt()
            else -> Color.WHITE
        }
        val label = if (t <= 0f) "0.0" else if (danger) String.format("%.1f", t) else t.toInt().toString()
        c.drawText(label, W / 2, 82f, text)

        // Progress bar
        r.set(22f, 140f, W - 70f, 154f)
        fill.color = 0xFF424242.toInt()
        c.drawRoundRect(r, 7f, 7f, fill)
        fill.color = 0xFF29B6F6.toInt()
        val px = r.left + r.width() * g.progress
        c.drawRoundRect(r.left, r.top, px, r.bottom, 7f, 7f, fill)
        fill.color = 0xFFE53935.toInt()
        c.drawCircle(px, r.centerY(), 12f, fill)
        text.textSize = 30f
        c.drawText("🏁", W - 40f, 160f, text)

        // Storm warning
        if (g.state == State.PLAYING && g.countdown <= 0f && g.stormWarning) {
            text.textSize = 34f
            val a = (155 + 100 * sin(g.anim * 8f)).toInt()
            text.color = Color.argb(a, 234, 128, 252)
            val msg = if (g.timeLeft <= 0f) "HẾT GIỜ! VÒNG BO ĐANG TIÊU DIỆT BẠN!" else "⚠ VÒNG BO ĐANG THU HẸP!"
            c.drawText(msg, W / 2, 220f, text)
        }
        c.restore()

        // Pause button
        val pr = g.pauseRect
        fill.color = 0x55FFFFFF
        c.drawRoundRect(pr, 16f, 16f, fill)
        fill.color = Color.WHITE
        c.drawRect(pr.centerX() - 14f, pr.top + 20f, pr.centerX() - 5f, pr.bottom - 20f, fill)
        c.drawRect(pr.centerX() + 5f, pr.top + 20f, pr.centerX() + 14f, pr.bottom - 20f, fill)
    }

    // -------------------------------------------------------------- overlays

    private fun drawOverlay(c: Canvas) {
        val W = g.W
        val H = g.H
        when (g.state) {
            State.PLAYING -> if (g.countdown > 0f) {
                val n = g.countdown.toInt() + 1
                text.textSize = 180f
                text.color = Color.WHITE
                drawShadowText(c, n.toString(), W / 2, H * 0.45f)
                text.textSize = 40f
                drawShadowText(c, "Đến đích trong ${g.timeLimit.toInt()} giây!", W / 2, H * 0.45f + 80f)
                text.textSize = 30f
                text.color = 0xFFE0E0E0.toInt()
                drawShadowText(c, "Kéo ngón tay để lái xe", W / 2, H * 0.45f + 130f)
            }
            State.PAUSED -> {
                dim(c)
                title(c, "TẠM DỪNG", H * 0.32f, Color.WHITE)
                drawButtons(c)
            }
            State.LEVEL_DONE -> {
                dim(c)
                title(c, "HOÀN THÀNH MÀN ${g.level}!", H * 0.26f, 0xFF69F0AE.toInt())
                text.textSize = 36f
                text.color = Color.WHITE
                var y = H * 0.36f
                for (line in listOf(
                    "Tiền nhặt được: ${g.levelCoins} $",
                    "Thưởng thời gian: ${g.timeBonus} $",
                    "Phá hủy: ${g.levelKills} xe/vật cản",
                    "Máu còn lại: ${g.player.hp.toInt()}",
                )) {
                    c.drawText(line, W / 2, y, text)
                    y += 56f
                }
                drawButtons(c)
            }
            State.GAME_OVER -> {
                dim(c)
                title(c, "THUA CUỘC", H * 0.26f, 0xFFFF5252.toInt())
                text.textSize = 36f
                text.color = if (g.deathByStorm) 0xFFEA80FC.toInt() else 0xFFFFAB91.toInt()
                c.drawText(
                    if (g.deathByStorm) "Bạn đã bị vòng bo tiêu diệt!" else "Xe của bạn đã bị phá hủy!",
                    W / 2, H * 0.35f, text,
                )
                text.color = Color.WHITE
                c.drawText("Đã tới màn: ${g.level}", W / 2, H * 0.42f, text)
                c.drawText("Tiền kiếm được: ${g.runCoins} $", W / 2, H * 0.42f + 56f, text)
                c.drawText("Kỷ lục: màn ${g.save.bestLevel}", W / 2, H * 0.42f + 112f, text)
                drawButtons(c)
            }
            else -> {}
        }
    }

    private fun drawMenu(c: Canvas) {
        val W = g.W
        val H = g.H
        fill.color = 0x99000000.toInt()
        c.drawRect(0f, 0f, W, H, fill)
        val bob = sin(g.anim * 3f) * 10f
        drawCar(c, W / 2, H * 0.4f + bob, 110f, 190f, 0xFFE53935.toInt(), sin(g.anim * 2f) * 6f, true)
        title(c, "ĐUA XE", H * 0.14f, 0xFFFFD740.toInt())
        title(c, "SINH TỒN", H * 0.14f + 100f, 0xFFE040FB.toInt())
        text.textSize = 30f
        text.color = 0xFFE0E0E0.toInt()
        c.drawText("Về đích trước khi vòng bo khép lại!", W / 2, H * 0.14f + 160f, text)
        text.textSize = 34f
        text.color = 0xFFFFD740.toInt()
        c.drawText("Tiền: ${g.save.money} $   •   Kỷ lục: màn ${g.save.bestLevel}", W / 2, H * 0.53f, text)
        drawButtons(c)
        text.textSize = 26f
        text.color = 0xFFB0BEC5.toInt()
        var y = H - 200f
        for (line in listOf(
            "✚ Hồi máu    $ Tiền    🛡 Lá chắn",
            "🚀 Vũ khí tự bắn    ⏱ +5 giây",
            "Né xe, đá, thùng, nón giao thông và vết dầu",
        )) {
            c.drawText(line, W / 2, y, text)
            y += 42f
        }
    }

    private fun drawShop(c: Canvas) {
        val W = g.W
        fill.color = 0xCC000000.toInt()
        c.drawRect(0f, 0f, W, g.H, fill)
        title(c, "NÂNG CẤP", 140f, 0xFFFFD740.toInt())
        text.textSize = 38f
        text.color = 0xFFFFD740.toInt()
        c.drawText("Tiền: ${g.save.money} $", W / 2, 220f, text)
        Upgrade.entries.forEachIndexed { i, u ->
            val y = 300f + i * 190f
            r.set(30f, y, W - 30f, y + 170f)
            fill.color = 0xFF263238.toInt()
            c.drawRoundRect(r, 20f, 20f, fill)
            text.textAlign = Paint.Align.LEFT
            text.textSize = 38f
            text.color = Color.WHITE
            c.drawText(u.title, 60f, y + 58f, text)
            text.textSize = 26f
            text.color = 0xFFB0BEC5.toInt()
            c.drawText(u.desc, 60f, y + 98f, text)
            val lv = g.save.level(u)
            for (k in 0 until Upgrade.MAX_LEVEL) {
                fill.color = if (k < lv) 0xFF69F0AE.toInt() else 0xFF455A64.toInt()
                c.drawRoundRect(60f + k * 44f, y + 120f, 96f + k * 44f, y + 140f, 6f, 6f, fill)
            }
            text.textAlign = Paint.Align.CENTER
        }
        drawButtons(c)
    }

    private fun drawButtons(c: Canvas) {
        for (b in g.buttons()) {
            fill.color = 0x66000000
            c.drawRoundRect(b.rect.left + 4f, b.rect.top + 8f, b.rect.right + 4f, b.rect.bottom + 8f, 26f, 26f, fill)
            fill.color = if (b.enabled) b.color else 0xFF616161.toInt()
            c.drawRoundRect(b.rect, 26f, 26f, fill)
            fill.color = 0x33FFFFFF
            c.drawRoundRect(b.rect.left, b.rect.top, b.rect.right, b.rect.centerY(), 26f, 26f, fill)
            text.textSize = min(46f, b.rect.height() * 0.42f)
            text.color = if (b.enabled) Color.WHITE else 0xFFBDBDBD.toInt()
            c.drawText(b.label, b.rect.centerX(), b.rect.centerY() + text.textSize * 0.36f, text)
        }
    }

    private fun dim(c: Canvas) {
        fill.color = 0xB0000000.toInt()
        c.drawRect(-50f, -50f, g.W + 50f, g.H + 50f, fill)
    }

    private fun title(c: Canvas, s: String, y: Float, color: Int) {
        text.textSize = 84f
        text.color = color
        val w = text.measureText(s)
        if (w > g.W - 40f) text.textSize *= (g.W - 40f) / w
        drawShadowText(c, s, g.W / 2, y)
    }

    private fun drawShadowText(c: Canvas, s: String, x: Float, y: Float) {
        val col = text.color
        text.color = Color.BLACK
        c.drawText(s, x + 4f, y + 4f, text)
        text.color = col
        c.drawText(s, x, y, text)
    }
}
