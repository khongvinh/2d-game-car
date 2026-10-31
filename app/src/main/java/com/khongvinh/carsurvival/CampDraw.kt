package com.khongvinh.carsurvival

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** The camp by day and under siege at night (fixed view, ground at 80% of the screen). */
internal fun Renderer.drawCamp(c: Canvas) {
    val cp = g.campaign
    val s = g.save
    val W = g.W
    val H = g.H
    val night = g.state == State.SIEGE
    if (!backdrop(c, Sprites.background(cp.region))) drawBackground(c)
    if (night) {
        fill.color = 0x99101830.toInt()
        c.drawRect(0f, 0f, W, H, fill)
    }
    c.save()
    val z = g.campZoom
    c.translate(0f, H * 0.8f)
    c.scale(z, z)
    c.translate(150f, 0f)
    // ground
    val th = g.theme
    fill.color = th.ground[0]
    c.drawRect(-400f, 0f, 2800f, 600f, fill)
    fill.color = th.crust[2]
    c.drawRect(-400f, 0f, 2800f, 10f, fill)
    fill.color = shade(th.ground[0], 0.85f)
    for (k in 0 until 30) {
        val x = -300f + (hash(k * 31) % 3000)
        c.drawOval(x, 30f + (k % 5) * 40f, x + 60f, 44f + (k % 5) * 40f, fill)
    }
    // the camp behind the wall: tents, the car, a fire barrel
    tent(c, 40f, 0xFF8D6E63.toInt())
    tent(c, 260f, 0xFF6D4C41.toInt())
    val v = Vehicles.of(VehicleClass.entries[s.carTier.coerceIn(0, 4)], 0)
    if (!sprite(c, Sprites.CARS[s.carTier.coerceIn(0, 4)], 150f, 4f + sin(g.anim * 9f) * 0.8f, 130f)) {
        c.save()
        c.translate(140f, -60f)
        c.scale(0.9f, 0.9f)
        art.draw(c, v, 0f, 0f, 0f, 0f, -0.3f, false, s.weaponType, false)
        c.restore()
    }
    // fire barrel
    fill.color = 0xFF5D4037.toInt()
    c.drawRect(560f, -70f, 610f, 0f, fill)
    for (k in 0..3) {
        fill.color = if (k % 2 == 0) 0xFFFF6D00.toInt() else 0xFFFFD740.toInt()
        val fy = -70f - 20f - 18f * sin(g.anim * 9f + k)
        c.drawCircle(575f + k * 8f, fy, 10f - k, fill)
    }
    // watchtower under slot 1
    if (!sprite(c, "def_watchtower", 470f, 6f, 300f)) {
    fill.color = 0xFF6D4C41.toInt()
    c.drawRect(420f, -190f, 432f, 0f, fill)
    c.drawRect(508f, -190f, 520f, 0f, fill)
    c.drawRect(410f, -200f, 530f, -184f, fill)
    stroke.color = 0xFF5D4037.toInt()
    stroke.strokeWidth = 5f
    c.drawLine(426f, -180f, 514f, -20f, stroke)
    c.drawLine(514f, -180f, 426f, -20f, stroke)
    }
    // the wall
    drawWall(c, s.wallLevel, cp.wallHp / cp.wallMax)
    // slots and what is built on them
    for (i in 0 until Campaign.SLOTS) {
        val x = Campaign.SLOT_X[i]
        val y = cp.slotY(i)
        val d = cp.defs[i]
        if (d == null) {
            if (i >= 2) {
                fill.color = 0xFF616161.toInt()
                c.drawRect(x - 46f, -6f, x + 46f, 6f, fill)
                fill.color = 0xFFF9C02E.toInt()
                var sx = x - 46f
                while (sx < x + 46f) {
                    c.drawRect(sx, -6f, sx + 10f, 6f, fill)
                    sx += 20f
                }
            }
            if (!night || cp.selSlot == i) {
                stroke.color = if (cp.selSlot == i) 0xFFFFD740.toInt() else 0x88FFFFFF.toInt()
                stroke.strokeWidth = 4f
                c.drawCircle(x, y - 50f, 34f, stroke)
                text.textAlign = Paint.Align.CENTER
                text.textSize = 44f
                text.color = stroke.color
                c.drawText("+", x, y - 34f, text)
            }
        } else {
            drawDef(c, d, x, y)
            if (cp.selSlot == i) {
                stroke.color = 0xFFFFD740.toInt()
                stroke.strokeWidth = 4f
                c.drawCircle(x, y - 50f, 80f, stroke)
            }
        }
    }
    if (cp.selSlot == 99) {
        stroke.color = 0xFFFFD740.toInt()
        stroke.strokeWidth = 5f
        c.drawRect(Campaign.WALL_X - 6f, -Campaign.WALL_H[s.wallLevel.coerceIn(0, 2)] - 6f, Campaign.WALL_X + 66f, 0f, stroke)
    }
    // monsters
    for (f in cp.foes) {
        val zz = f.z
        val pic = Sprites.foe(f, cp.isBoss(f), cp.region)
        val sway = sin(zz.phase)
        val big = if (cp.isBoss(f)) 220f else zz.type.h * 1.3f
        val steps = (cp.isBoss(f) && pic == "boss_brute") || (!cp.isBoss(f) && Sprites.walks(zz.type))
        if (steps && walkSprite(c, pic, zz.x, zz.y + 4f, big, true, zz.phase, if (cp.isBoss(f)) 16f else Sprites.stride(zz.type), lean = 4f)) {
            // legs stepping, cut from the AI picture
        } else if (sprite(c, pic, zz.x, zz.y + 4f - kotlin.math.abs(sway) * 5f, big, flip = true,
                rot = sway * 6f, squash = 1f + 0.05f * kotlin.math.abs(sin(zz.phase * 2f)))) {
            // drawn from the AI picture, facing the camp
        } else if (cp.isBoss(f)) {
            c.save()
            c.translate(zz.x, 0f)
            c.scale(-2.2f, 2.2f)
            zombieFigure(c, 60f, 92f, 0xFF6D1B1B.toInt(), sin(zz.phase), 1f, true)
            c.restore()
        } else {
            c.save()
            c.translate(zz.x, zz.y)
            if (zz.type == ZombieType.WALKER || zz.type == ZombieType.RUNNER || zz.type == ZombieType.FAT) {
                zombieFigure(c, zz.type.w, zz.type.h, zz.shirt, sin(zz.phase), -1f, zz.type == ZombieType.FAT)
            } else {
                creatureFigure(c, zz.type, sin(zz.phase), -1f, zz.phase)
            }
            c.restore()
        }
        if (f.hitT > 0f) {
            fill.color = 0x66FFFFFF
            c.drawCircle(zz.x, zz.y - zz.type.h / 2, zz.type.w * 0.6f, fill)
        }
        if (f.burnT > 0f) burning(c, zz.x, zz.y + 4f, big)
        if (f.slowT > 0f) {
            fill.color = 0x5580D8FF
            c.drawCircle(zz.x, zz.y - zz.type.h / 2, zz.type.w * 0.7f, fill)
        }
    }
    // shots, tracers, lightning
    for (t in cp.tracers) {
        stroke.color = 0xFFFFF176.toInt()
        stroke.strokeWidth = 4f
        c.drawLine(t[0], t[1], t[2], t[3], stroke)
    }
    for (sh in cp.shots) {
        when (sh.kind) {
            0 -> {
                fill.color = 0xFF76FF03.toInt()
                c.drawCircle(sh.x, sh.y, 10f, fill)
            }
            else -> {
                fill.color = 0xFF263238.toInt()
                c.drawCircle(sh.x, sh.y, if (sh.kind == 4) 14f else 11f, fill)
                fill.color = 0xFFFFAB40.toInt()
                c.drawCircle(sh.x - sh.vx * 0.01f, sh.y - sh.vy * 0.01f, 5f, fill)
            }
        }
    }
    for (zp in cp.zaps) {
        stroke.color = 0xFF80D8FF.toInt()
        stroke.strokeWidth = 5f
        var i = 0
        while (i + 3 < zp.pts.size) {
            val mx = (zp.pts[i] + zp.pts[i + 2]) / 2 + (Random.nextFloat() - 0.5f) * 40f
            val my = (zp.pts[i + 1] + zp.pts[i + 3]) / 2 + (Random.nextFloat() - 0.5f) * 40f
            c.drawLine(zp.pts[i], zp.pts[i + 1], mx, my, stroke)
            c.drawLine(mx, my, zp.pts[i + 2], zp.pts[i + 3], stroke)
            i += 2
        }
    }
    drawFx(c)
    for (p in g.particles) {
        val ratio = (p.life / p.maxLife).coerceIn(0f, 1f)
        fill.color = p.color
        fill.alpha = (255 * ratio * (if (p.grow > 0f) 0.55f else 1f)).toInt().coerceIn(0, 255)
        c.drawCircle(p.x, p.y, p.size * (1f + p.grow * (1f - ratio)), fill)
    }
    fill.alpha = 255
    for (t in g.texts) {
        text.textAlign = Paint.Align.CENTER
        text.textSize = t.size
        text.color = t.color
        text.alpha = (255 * min(1f, t.life)).toInt()
        c.drawText(t.text, t.x, t.y, text)
    }
    text.alpha = 255
    c.restore()

    campHud(c)
}

private fun Renderer.tent(c: Canvas, x: Float, col: Int) {
    fill.color = col
    path.reset()
    path.moveTo(x, 0f); path.lineTo(x + 90f, -110f); path.lineTo(x + 180f, 0f); path.close()
    c.drawPath(path, fill)
    fill.color = shade(col, 0.6f)
    path.reset()
    path.moveTo(x + 70f, 0f); path.lineTo(x + 90f, -60f); path.lineTo(x + 110f, 0f); path.close()
    c.drawPath(path, fill)
}

private fun Renderer.drawWall(c: Canvas, level: Int, hpF: Float) {
    val x = Campaign.WALL_X
    val h = Campaign.WALL_H[level.coerceIn(0, 2)]
    when (level) {
        0 -> { // planks and logs
            for (k in 0 until 6) {
                fill.color = if (k % 2 == 0) 0xFF8D6E63.toInt() else 0xFF795548.toInt()
                c.drawRect(x + k * 10f, -h + (k % 3) * 8f, x + k * 10f + 10f, 0f, fill)
                fill.color = 0xFF6D4C41.toInt()
                path.reset()
                path.moveTo(x + k * 10f, -h + (k % 3) * 8f); path.lineTo(x + k * 10f + 5f, -h - 14f + (k % 3) * 8f); path.lineTo(x + k * 10f + 10f, -h + (k % 3) * 8f); path.close()
                c.drawPath(path, fill)
            }
            fill.color = 0xFF5D4037.toInt()
            c.drawRect(x - 6f, -h * 0.7f, x + 66f, -h * 0.7f + 14f, fill)
            c.drawRect(x - 6f, -h * 0.3f, x + 66f, -h * 0.3f + 14f, fill)
        }
        1 -> { // scrap metal sheets
            for (k in 0 until 4) {
                fill.color = if (k % 2 == 0) 0xFF78909C.toInt() else 0xFF607D8B.toInt()
                c.drawRect(x, -h + k * h / 4f, x + 60f, -h + (k + 1) * h / 4f, fill)
                for (j in 0..2) rivet(c, x + 10f + j * 20f, -h + k * h / 4f + 8f, 3f)
            }
            fill.color = 0x55834012
            c.drawOval(x + 8f, -h * 0.6f, x + 40f, -h * 0.5f, fill)
        }
        else -> { // concrete with a parapet
            fill.color = 0xFF9E9E9E.toInt()
            c.drawRect(x, -h, x + 64f, 0f, fill)
            fill.color = 0xFF757575.toInt()
            for (k in 0 until 3) c.drawRect(x + k * 24f, -h - 18f, x + k * 24f + 14f, -h, fill)
            stroke.color = 0xFF616161.toInt()
            stroke.strokeWidth = 2f
            var y = -40f
            while (y > -h) {
                c.drawLine(x, y, x + 64f, y, stroke)
                y -= 40f
            }
        }
    }
    // cracks as it is battered
    if (hpF < 0.66f) {
        stroke.color = 0xFF212121.toInt()
        stroke.strokeWidth = 4f
        c.drawLine(x + 30f, -h * 0.8f, x + 14f, -h * 0.55f, stroke)
        c.drawLine(x + 14f, -h * 0.55f, x + 40f, -h * 0.35f, stroke)
        if (hpF < 0.33f) c.drawLine(x + 50f, -h * 0.5f, x + 26f, -h * 0.15f, stroke)
    }
    // health bar
    fill.color = 0xCC000000.toInt()
    c.drawRect(x - 30f, -h - 60f, x + 90f, -h - 44f, fill)
    fill.color = if (hpF > 0.35f) 0xFF43A047.toInt() else 0xFFE53935.toInt()
    c.drawRect(x - 28f, -h - 58f, x - 28f + 116f * hpF.coerceIn(0f, 1f), -h - 46f, fill)
}

private fun Renderer.drawDef(c: Canvas, d: Def, x: Float, y: Float) {
    val hit = d.flash > 0f
    val h = when {
        d.type == DefType.MINE -> 34f
        d.type.blocks -> 120f
        else -> 130f
    }
    val kick = if (d.fireT > 0f) -8f else 0f
    val shake = if (hit) (kotlin.random.Random.nextFloat() - 0.5f) * 6f else 0f
    val idle = if (d.type.blocks || d.type == DefType.MINE) 0f else sin(g.anim * 1.5f + x) * 2f
    if (sprite(c, Sprites.def(d.type), x + kick + shake, y + 4f, h, alpha = if (hit) 170 else 255, rot = idle,
            squash = if (d.fireT > 0f) 0.96f else 1f)) {
        // picture from the asset pack
    } else when (d.type) {
        DefType.WOOD -> {
            for (k in 0..2) {
                fill.color = if (hit) Color.WHITE else if (k % 2 == 0) 0xFF8D6E63.toInt() else 0xFFA1887F.toInt()
                c.save()
                c.translate(x, y - 30f - k * 26f)
                c.rotate((k - 1) * 12f)
                c.drawRect(-60f, -10f, 60f, 10f, fill)
                c.restore()
            }
            fill.color = 0xFF5D4037.toInt()
            c.drawRect(x - 40f, y - 90f, x - 30f, y, fill)
            c.drawRect(x + 30f, y - 90f, x + 40f, y, fill)
        }
        DefType.SPIKE -> {
            stroke.color = if (hit) Color.WHITE else 0xFF546E7A.toInt()
            stroke.strokeWidth = 10f
            c.drawLine(x - 50f, y, x + 30f, y - 90f, stroke)
            c.drawLine(x + 50f, y, x - 30f, y - 90f, stroke)
            c.drawLine(x - 50f, y - 45f, x + 50f, y - 45f, stroke)
            fill.color = 0xFFCFD8DC.toInt()
            for (k in 0..4) {
                path.reset()
                val sx = x - 50f + k * 25f
                path.moveTo(sx - 6f, y - 50f); path.lineTo(sx + 14f, y - 64f); path.lineTo(sx + 6f, y - 40f); path.close()
                c.drawPath(path, fill)
            }
        }
        DefType.MINE -> {
            fill.color = 0xFF455A64.toInt()
            c.drawOval(x - 26f, y - 14f, x + 26f, y + 4f, fill)
            fill.color = if ((g.anim * 3f).toInt() % 2 == 0) 0xFFFF1744.toInt() else 0xFF7F0000.toInt()
            c.drawCircle(x, y - 12f, 5f, fill)
        }
        else -> {
            // turret: base, then a gun that follows its aim
            fill.color = 0xFF5D4037.toInt()
            if (y >= 0f) c.drawRect(x - 44f, y - 40f, x + 44f, y, fill)
            fill.color = if (hit) Color.WHITE else 0xFF455A64.toInt()
            c.drawRoundRect(x - 34f, y - 74f, x + 34f, y - 36f, 10f, 10f, fill)
            c.save()
            c.translate(x, y - 64f)
            c.rotate(d.aim * 180f / PI.toFloat())
            when (d.type) {
                DefType.MG -> {
                    fill.color = 0xFF263238.toInt()
                    c.drawRect(0f, -7f, 70f, 7f, fill)
                    if (d.fireT > 0f) {
                        fill.color = 0xFFFFE082.toInt()
                        c.drawCircle(76f, 0f, 10f, fill)
                    }
                }
                DefType.CANNON -> {
                    fill.color = 0xFF37474F.toInt()
                    c.drawRect(0f, -13f, 78f, 13f, fill)
                    fill.color = 0xFF263238.toInt()
                    c.drawRect(70f, -16f, 84f, 16f, fill)
                }
                DefType.FLAME -> {
                    fill.color = 0xFFC62828.toInt()
                    c.drawRect(0f, -9f, 60f, 9f, fill)
                    fill.color = 0xFFFFD740.toInt()
                    c.drawCircle(-14f, 0f, 14f, fill)
                }
                DefType.TESLA -> {
                    c.restore()
                    c.save()
                    stroke.color = 0xFFB0BEC5.toInt()
                    stroke.strokeWidth = 6f
                    c.drawLine(x, y - 74f, x, y - 150f, stroke)
                    fill.color = 0xFF80D8FF.toInt()
                    c.drawCircle(x, y - 156f, 16f + 3f * sin(g.anim * 12f), fill)
                }
                DefType.MORTAR -> {
                    c.restore()
                    c.save()
                    c.translate(x, y - 64f)
                    c.rotate(-60f)
                    fill.color = 0xFF33691E.toInt()
                    c.drawRect(0f, -14f, 64f, 14f, fill)
                }
                else -> {}
            }
            c.restore()
        }
    }
    // level stars and health
    text.textAlign = Paint.Align.CENTER
    text.textSize = 22f
    text.color = 0xFFFFD740.toInt()
    if (d.type != DefType.MINE) c.drawText("★".repeat(d.level), x, y - 112f, text)
    if (d.hp < d.maxHp && d.type != DefType.MINE) {
        val f = (d.hp / d.maxHp).coerceIn(0f, 1f)
        fill.color = 0xCC000000.toInt()
        c.drawRect(x - 40f, y - 104f, x + 40f, y - 94f, fill)
        fill.color = if (f > 0.35f) 0xFF43A047.toInt() else 0xFFE53935.toInt()
        c.drawRect(x - 39f, y - 103f, x - 39f + 78f * f, y - 95f, fill)
    }
}

/** Resources, day/night line, the build panel and the big banner. */
private fun Renderer.campHud(c: Canvas) {
    val cp = g.campaign
    val s = g.save
    val W = g.W
    val H = g.H
    // resources
    val res = "💰 ${s.money}$   ⚙ ${s.scrap}   ⛽ ${s.fuel}L   ♦ ${s.gems}"
    text.textSize = 28f
    val rw = text.measureText(res)
    glassPanel(c, W - rw - 60f, 18f, W - 20f, 76f, 18f)
    text.textAlign = Paint.Align.RIGHT
    text.color = 0xFFFFE082.toInt()
    c.drawText(res, W - 40f, 58f, text)
    // day / journey line
    text.textAlign = Paint.Align.CENTER
    text.textSize = 26f
    text.color = Color.WHITE
    val line = if (g.state == State.SIEGE) {
        "🌙 ĐÊM ${s.nights + 1} • ĐỢT ${cp.wave}/${cp.waves} • 🧟 ${cp.foes.size}"
    } else {
        "☀ NGÀY ${s.day} • ${cp.region.title} • ${s.distM}m / ${Campaign.GOAL_M}m${if (s.dayActed) " • đã dùng lượt hôm nay" else ""}"
    }
    val lw = text.measureText(line)
    glassPanel(c, W / 2 - lw / 2 - 24f, 96f, W / 2 + lw / 2 + 24f, 138f, 14f)
    c.drawText(line, W / 2, 127f, text)
    if (g.state == State.CAMP && cp.selSlot < 0) {
        text.textSize = 22f
        text.color = 0xCCFFFFFF.toInt()
        c.drawText("Chạm ô ＋ để dựng bệ • chạm bệ để nâng cấp • chạm tường để sửa", W / 2, 166f, text)
    }
    // build panel background and labels
    if (cp.selSlot >= 0) {
        metalPanel(c, 24f, H - 236f, W - 24f, H - 8f, 18f)
        text.textAlign = Paint.Align.LEFT
        text.textSize = 26f
        text.color = 0xFFFFD740.toInt()
        val title = when {
            cp.selSlot == 99 -> "${Campaign.WALL_NAMES[s.wallLevel.coerceIn(0, 2)]} • ${cp.wallHp.toInt()}/${cp.wallMax.toInt()} máu"
            cp.defs[cp.selSlot] != null -> cp.defs[cp.selSlot]!!.let { "${it.type.icon} ${it.type.title} cấp ${it.level} • ${it.hp.toInt()}/${it.maxHp.toInt()} máu • ${it.type.desc}" }
            cp.selSlot < 2 -> "Bệ trên cao (chỉ đặt súng) • có ⚙${s.scrap}"
            else -> "Ô trước tường • có ⚙${s.scrap}"
        }
        fitText(c, title, 50f, H - 196f, 26f, W - 100f)
    }
    drawMenuButtons(c)
    if (cp.bannerT > 0f && cp.banner.isNotEmpty()) {
        val a = min(1f, cp.bannerT)
        fill.color = Color.argb((200 * a).toInt(), 120, 20, 20)
        c.drawRect(0f, H * 0.3f, W, H * 0.3f + 120f, fill)
        title(c, cp.banner, W / 2, H * 0.3f + 70f, Color.WHITE, 60f)
        if (cp.bannerSub.isNotEmpty()) {
            text.textAlign = Paint.Align.CENTER
            text.color = 0xFFFFE082.toInt()
            fitText(c, cp.bannerSub, W / 2, H * 0.3f + 108f, 26f, W - 80f)
        }
    }
}

/** Workshop: every blueprint with its parts and the assemble button. */
internal fun Renderer.drawWorkshop(c: Canvas) {
    val s = g.save
    val W = g.W
    if (backdrop(c, "bg_garage")) {
        fill.color = 0x77000000
        c.drawRect(0f, 0f, W, g.H, fill)
    } else workshopRoom(c)
    title(c, "XƯỞNG LẮP RÁP", W / 2, 70f, 0xFFFFD740.toInt(), 50f)
    text.textAlign = Paint.Align.RIGHT
    text.textSize = 26f
    text.color = 0xFFFFE082.toInt()
    c.drawText("💰 ${s.money}$", W - 40f, 70f, text)
    for (bp in Blueprints.all) {
        val r = g.workshopCard(bp.id)
        val owned = g.campaign.owned(bp)
        card(c, r, owned)
        val cx = (r[0] + r[2]) / 2
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        val kind = when (bp.kind) { Blueprint.CAR -> "🚗"; Blueprint.WALL -> "🧱"; else -> DefType.entries[bp.ref].icon }
        fitText(c, "$kind ${bp.title}", cx, r[1] + 40f, 26f, r[2] - r[0] - 20f)
        val pic = when (bp.kind) {
            Blueprint.CAR -> Sprites.CARS[bp.ref.coerceIn(0, 4)]
            Blueprint.WALL -> "def_wall_metal"
            else -> Sprites.def(DefType.entries[bp.ref])
        }
        spriteBox(c, pic, r[0] + 10f, r[1] + 52f, r[0] + 70f, r[1] + 92f)
        spriteBox(c, when (bp.kind) { Blueprint.CAR -> if (bp.id % 2 == 0) "part_engine" else "part_wheel"; Blueprint.WALL -> "part_brick"; else -> "part_gun_barrel" }, r[2] - 60f, r[1] + 52f, r[2] - 14f, r[1] + 92f)
        val have = min(bp.parts, s.bpParts[bp.id])
        // part pips
        val pw = 22f
        val total = bp.parts * (pw + 6f) - 6f
        for (k in 0 until bp.parts) {
            fill.color = if (owned || k < have) 0xFF80D8FF.toInt() else 0xFF37474F.toInt()
            val l = cx - total / 2 + k * (pw + 6f)
            c.drawRoundRect(l, r[1] + 60f, l + pw, r[1] + 82f, 5f, 5f, fill)
        }
        text.textSize = 20f
        text.color = 0xFFB0BEC5.toInt()
        val sub = when (bp.kind) {
            Blueprint.CAR -> "Xe khỏe hơn, bình xăng to hơn"
            Blueprint.WALL -> "Tường ${Campaign.WALL_HP[bp.ref].toInt()} máu"
            else -> DefType.entries[bp.ref].desc
        }
        fitText(c, sub, cx, r[1] + 110f, 20f, r[2] - r[0] - 20f)
    }
    drawMenuButtons(c)
}

/** The garage backdrop is private to the renderer; a simple brick room for the workshop. */
private fun Renderer.workshopRoom(c: Canvas) {
    fill.color = 0xFF3E2A22.toInt()
    c.drawRect(0f, 0f, g.W, g.H, fill)
    stroke.color = 0xFF2A1C17.toInt()
    stroke.strokeWidth = 2f
    var y = 0f
    var row = 0
    while (y < g.H) {
        c.drawLine(0f, y, g.W, y, stroke)
        var x = if (row % 2 == 0) 0f else 40f
        while (x < g.W) {
            c.drawLine(x, y, x, y + 26f, stroke)
            x += 80f
        }
        y += 26f; row++
    }
}
