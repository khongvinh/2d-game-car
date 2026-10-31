package com.khongvinh.carsurvival

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Run power-ups picked on level-up or from the merchant; they stack up to [max]. */
enum class Power(val title: String, val desc: String, val icon: String, val max: Int) {
    DUAL("Đạn kép", "Mỗi phát bắn thêm 1 viên", "🔫", 4),
    DYNAMITE("Thuốc nổ", "Tự ném thuốc nổ vào quái", "🧨", 5),
    MATCH("Que diêm", "Đốt cháy quái quanh bạn", "🔥", 5),
    NITRO("Nitơ lỏng", "Trúng đạn thì quái chạy chậm", "❄", 3),
    BOOM("Xác nổ", "Quái nổ tung khi chết", "💥", 5),
    SHOES("Giày chạy", "Chạy nhanh hơn 15%", "👟", 3),
    ORBIT("Mảnh sắt quay", "Mảnh sắt bay quanh người chém quái", "⚙", 4),
    ADREN("Adrenaline", "Hạ quái thì bắn nhanh hơn 3 giây", "💉", 3),
    BATTERY("Pin xe", "Phóng tia sét nhảy qua nhiều quái", "⚡", 5),
    DAMAGE("Sát thương+", "+25% sát thương", "🗡", 6),
    RATE("Tốc bắn+", "+20% tốc độ bắn", "⏩", 5),
    VITAL("Máu+", "+25 máu tối đa và hồi máu", "❤", 6),
    MAGNET("Nam châm", "Hút tiền từ xa hơn", "🧲", 2),
    JUMP2("Nhảy đôi", "Nhảy thêm 1 lần trên không", "🦘", 1),
    VAMP("Hút máu", "Hạ quái hồi 2 máu", "🩸", 3),
}

/** A monster in a room: a [Zombie] for looks plus its own falling, slowing and spitting. */
class Foe(val z: Zombie, val ranged: Boolean) {
    var vy = 0f
    var slowT = 0f
    var burnT = 0f
    var hitT = 0f
    var spitCd = 1.5f + Random.nextFloat() * 1.5f

    /** Platform it walks on (-1 = floor). */
    var plat = -1
    var orbitCd = 0f
}

/** Boss of rooms 10 / 20 / 30. [kind] 0 cannon turret, 1 brute, 2 the region's big boss. */
class RoomBoss(val kind: Int, var x: Float, val tier: Int, hpMul: Float) {
    val maxHp = (if (kind == 0) 60f else if (kind == 1) 80f else 120f) * hpMul
    var hp = maxHp
    var fireCd = 2f
    var chargeT = 0f
    var flash = 0f
    var dir = -1f
    var anim = 0f
    var alive = true

    /** Only for drawing the region boss with the road boss art. */
    val look = Boss(tier.coerceIn(1, 4), x, 0f)
    val w get() = if (kind == 0) 120f else if (kind == 1) 110f else look.w
    val h get() = if (kind == 0) 150f else if (kind == 1) 190f else look.h
}

/** Spit, fireballs, dynamite and other things flying in a room. */
class RoomShot(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int, val dmg: Float) {
    /** 0 enemy spit, 1 boss fireball, 2 dynamite (the player's). */
    var life = 4f
    var alive = true
}

class Cash(var x: Float, var y: Float, var vx: Float, var vy: Float, val value: Int, val heart: Boolean = false) {
    var alive = true
    var t = 0f
}

/** Lightning arc drawn for a moment. */
class Zap(val pts: FloatArray) {
    var life = 0.22f
}

/** One room of the run: platforms, decor, monsters and the doors. */
class Room(val index: Int, stage: Int, region: Region, seed: Int, val boss: Boolean, val rest: Boolean, bossKind: Int = 1) {
    private val rnd = Random(seed)

    /** Loot crates: x, height of the surface it sits on, opened (0/1). */
    val crates = ArrayList<FloatArray>()
    var benchUsed = false
    val width = if (boss) 2000f else 1900f + rnd.nextInt(5) * 140f
    val platforms = ArrayList<Platform>()

    /** Background shelves / wrecks / crates (no collision). [0]=x [1]=kind */
    val decor = ArrayList<FloatArray>()
    val foes = ArrayList<Foe>()
    val shots = ArrayList<RoomShot>()
    val cash = ArrayList<Cash>()
    var roomBoss: RoomBoss? = null
    var wave2 = !boss && !rest && index > 3 && rnd.nextFloat() < 0.4f
    val exitX = width - 130f
    val entryX = 130f
    var open = rest
    val benchX get() = width / 2

    /** Sunset-lit warehouse, or darker / redder / alien depending on the region. */
    val palette = rnd.nextInt(3)

    init {
        // hazard-striped ledges on two or three levels, reachable by jumping
        var x = 360f
        while (x < width - 420f) {
            val w = 220f + rnd.nextInt(4) * 60f
            val lv = rnd.nextInt(3)
            val top = floatArrayOf(150f, 290f, 420f)[lv]
            platforms.add(Platform(x, x + w, top, 10))
            if (lv == 2) platforms.add(Platform(x - 160f, x - 20f, 280f, 10))
            x += w + 160f + rnd.nextFloat() * 180f
        }
        if (rest) platforms.clear()
        if (!rest) {
            // two or three crates to break open, some up on the ledges
            repeat(2 + rnd.nextInt(2)) {
                val up = platforms.isNotEmpty() && rnd.nextFloat() < 0.4f
                if (up) {
                    val p = platforms[rnd.nextInt(platforms.size)]
                    crates.add(floatArrayOf((p.l + p.r) / 2, p.top, 0f))
                } else {
                    crates.add(floatArrayOf(400f + rnd.nextFloat() * (width - 700f), 0f, 0f))
                }
            }
        }
        if (!boss) {
            var dx = 250f
            while (dx < width - 250f) {
                if (!rest || abs(dx - width / 2) > 260f) decor.add(floatArrayOf(dx, rnd.nextInt(4).toFloat()))
                dx += 300f + rnd.nextFloat() * 320f
            }
        }
        val hpMul = 1f + 0.06f * (stage - 1) + 0.03f * index
        if (boss) {
            val kind = bossKind
            roomBoss = RoomBoss(kind, width - 420f, if (stage % 5 == 0) stage / 5 else (region.ordinal + 1), hpMul)
            repeat(2 + index / 10) { foes.add(spawn(stage, region, 500f + it * 300f, -1, hpMul)) }
        } else if (!rest) {
            val n = min(14, 3 + index / 3 + stage / 3)
            for (k in 0 until n) {
                val onPlat = platforms.isNotEmpty() && rnd.nextFloat() < 0.4f
                if (onPlat) {
                    val pi = rnd.nextInt(platforms.size)
                    val p = platforms[pi]
                    foes.add(spawn(stage, region, p.l + 30f + rnd.nextFloat() * (p.r - p.l - 60f), pi, hpMul).also { it.z.y = -p.top })
                } else {
                    foes.add(spawn(stage, region, 600f + rnd.nextFloat() * (width - 800f), -1, hpMul))
                }
            }
        }
    }

    fun spawn(stage: Int, region: Region, x: Float, plat: Int, hpMul: Float): Foe {
        val r = rnd.nextFloat()
        val t = when (region) {
            Region.DESERT -> if (r < 0.18f && stage > 1) ZombieType.FAT else if (r < 0.45f) ZombieType.RUNNER else ZombieType.WALKER
            Region.VOLCANO -> if (r < 0.3f) ZombieType.LIZARD else if (r < 0.5f) ZombieType.FAT else ZombieType.WALKER
            Region.NIGHT -> if (r < 0.25f) ZombieType.BAT else if (r < 0.55f) ZombieType.RUNNER else ZombieType.WALKER
            Region.ALIEN -> if (r < 0.3f) ZombieType.BLOB else ZombieType.ALIEN
        }
        val z = Zombie(t, x, 0f)
        z.hp = t.hp * 1.6f * hpMul
        z.fly = 0f
        val f = Foe(z, t != ZombieType.BAT && t != ZombieType.FAT && rnd.nextFloat() < 0.22f)
        f.plat = plat
        return f
    }

    /** Surface height under [x] for feet at [feet] (one-way ledges). */
    fun support(x: Float, feet: Float): Float {
        var top = 0f
        for (p in platforms) if (x > p.l + 2f && x < p.r - 2f && feet <= -p.top + 10f) top = min(top, -p.top)
        return top
    }
}

/** A whole run of rooms after the drive, with its level, power-ups and timers. */
class RoomRun(val stage: Int, val region: Region, private val seed: Int, val total: Int = 6, private val bossLast: Boolean = false) {
    companion object {
        val MERCHANT_AFTER = intArrayOf(5, 15, 25)
    }

    var index = 1
    var room = make(1)

    private fun make(i: Int) = Room(i, stage, region, seed + i * 101, boss = (i == total && bossLast) || i % 10 == 0, rest = i % 7 == 0 && i < total, bossKind = if (i % 20 == 0) 1 else if (i % 10 == 0) 0 else Random(seed + i).nextInt(2))
    var level = 1
    var xp = 0
    val xpNeed get() = 4 + level * 3
    val powers = IntArray(Power.entries.size)
    fun lv(p: Power) = powers[p.ordinal]

    /** Pending choice: 0 level-up power, 1 merchant boxes, 2 upgrade after a boss. */
    var pickKind = -1
    var choices: List<Power> = emptyList()
    var benchQueued = false
    var levelUps = 0
    var upgradeQueued = 0
    var merchantQueued = 0

    var camX = 0f
    var dynCd = 2f
    var batCd = 2f
    var adrenT = 0f
    var matchT = 0f
    var orbitA = 0f
    var airJumps = 0
    var jumpWas = false
    var fade = 1f
    var roomNoteT = 2f
    val zaps = ArrayList<Zap>()

    fun nextRoom() {
        index++
        room = make(index)
        fade = 1f
        roomNoteT = 2f
    }

    /** Three different power-ups that are not maxed yet. */
    fun roll(): List<Power> = Power.entries.filter { lv(it) < it.max }.shuffled().take(3)
}

/** Rules of the room run (kept apart from the drive in [Game]). */
class RoomRules(private val g: Game) {
    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    fun update(run: RoomRun, dt: Float, left: Boolean, right: Boolean, jump: Boolean) {
        val d = g.driver
        val rm = run.room
        if (run.fade > 0f) run.fade -= dt * 2.5f
        if (run.roomNoteT > 0f) run.roomNoteT -= dt
        if (d.hurtT > 0f) d.hurtT -= dt
        if (d.atkT > 0f) d.atkT -= dt
        if (d.shieldT > 0f) d.shieldT -= dt
        if (run.adrenT > 0f) run.adrenT -= dt

        // movement and jumping (double jump with the power-up)
        val speed = 300f * (1f + 0.15f * run.lv(Power.SHOES))
        d.dir = (if (right) 1f else 0f) - (if (left) 1f else 0f)
        if (d.dir != 0f) {
            d.face = d.dir
            d.x = (d.x + d.dir * speed * dt).coerceIn(40f, rm.width - 40f)
            val step = (d.walk / PI.toFloat()).toInt()
            d.walk += dt * 11f
            if ((d.walk / PI.toFloat()).toInt() != step && d.air < 2f) g.dustAt(d.x - d.dir * 14f, d.y, 2, 50f)
        }
        if (d.landT > 0f) d.landT -= dt
        if (d.muzzleT > 0f) d.muzzleT -= dt
        val grounded = d.air <= 0.5f && d.vy >= 0f
        if (grounded) run.airJumps = run.lv(Power.JUMP2)
        if (jump && !run.jumpWas) {
            if (grounded) {
                d.vy = -700f
                g.dustAt(d.x, d.y, 6)
                g.sfx.play(Sfx.S.CLICK, 0.4f)
            } else if (run.airJumps > 0) {
                run.airJumps--
                d.vy = -620f
                g.burstAt(d.x, d.y, 0xFFB0BEC5.toInt(), 8)
            }
        }
        run.jumpWas = jump
        var feet = d.y + d.vy * dt
        d.vy += Game.G * dt
        val ground = rm.support(d.x, d.y)
        if (feet >= ground) {
            // landing: dust and a quick squash, harder falls kick up more
            if (d.vy > 380f) {
                g.dustAt(d.x, ground, if (d.vy > 800f) 10 else 6, 160f)
                d.landT = 0.16f
            }
            feet = ground
            d.vy = 0f
        }
        d.y = max(feet, -560f)
        d.air = rm.support(d.x, d.y) - d.y

        updateFoes(run, dt)
        updateBoss(run, dt)
        attack(run, dt)
        powersTick(run, dt)
        updateShots(run, dt)
        updateCash(run, dt)
        for (z in run.zaps) z.life -= dt
        run.zaps.removeAll { it.life <= 0f }

        // the workbench in a rest room
        if (rm.rest && !rm.benchUsed && abs(d.x - rm.benchX) < 90f && d.y > -10f) {
            rm.benchUsed = true
            run.benchQueued = true
        }
        // crates burst open when the player touches them
        for (cr in rm.crates) if (cr[2] == 0f && abs(d.x - cr[0]) < 60f && abs(d.y + cr[1]) < 50f) {
            cr[2] = 1f
            g.campaign.lootCrate(cr[0], -cr[1] - 40f)
        }
        // all clear: maybe a second wave from the doors, then the exit opens
        val bossAlive = rm.roomBoss?.alive == true
        if (!rm.open && rm.foes.isEmpty() && !bossAlive) {
            if (rm.wave2) {
                rm.wave2 = false
                val hpMul = 1f + 0.06f * (run.stage - 1) + 0.03f * run.index
                repeat(2 + run.index / 8) {
                    val fx = if (it % 2 == 0) rm.width - 200f else 200f
                    rm.foes.add(rm.spawn(run.stage, run.region, fx, -1, hpMul))
                }
                g.texts.add(FloatText("THÊM ZOMBIE!", d.x, d.y - 200f, 0xFFFF5252.toInt()).also { it.size = 40f })
            } else {
                rm.open = true
                g.texts.add(FloatText("LỐI RA ĐÃ MỞ →", d.x, d.y - 200f, 0xFF69F0AE.toInt()).also { it.size = 40f })
                g.sfx.play(Sfx.S.POWER, 0.7f)
            }
        }
        run.camX += ((d.x - g.viewW * 0.42f).coerceIn(-120f, max(-120f, rm.width - g.viewW + 120f)) - run.camX) * min(1f, dt * 6f)
    }

    private fun updateFoes(run: RoomRun, dt: Float) {
        val d = g.driver
        val rm = run.room
        val bite = 8f + run.stage * 0.7f + run.index * 0.15f
        for (f in rm.foes) {
            val z = f.z
            if (!z.alive) continue
            if (f.slowT > 0f) f.slowT -= dt
            if (f.hitT > 0f) f.hitT -= dt
            if (f.orbitCd > 0f) f.orbitCd -= dt
            if (f.burnT > 0f) {
                f.burnT -= dt
                hurt(run, f, 1.2f * dt, 0f, fx = false)
                if (Random.nextFloat() < dt * 20f) g.particles.add(Particle(z.x + rnd(-12f, 12f), z.y - rnd(10f, z.type.h), rnd(-20f, 20f), -rnd(60f, 140f), 0.4f, 0.4f, rnd(5f, 9f), if (Random.nextBoolean()) 0xFFFF6D00.toInt() else 0xFFFFD740.toInt()))
                if (!z.alive) continue
            }
            z.phase += dt * (if (z.type == ZombieType.RUNNER || z.type == ZombieType.LIZARD) 9f else if (z.type == ZombieType.BAT) 14f else 4f)
            val slow = if (f.slowT > 0f) 1f - 0.25f * run.lv(Power.NITRO) else 1f
            val sp = max(60f, z.type.speed * 1.3f) * slow
            val dx = d.x - z.x
            if (z.type.flying) {
                // bats hover above and swoop at the player
                val ty = if (abs(dx) < 260f) d.y - 50f else d.y - 220f + sin(z.phase * 0.3f) * 30f
                z.x += sign(dx) * sp * dt
                z.y += (ty - z.y) * min(1f, dt * 2.5f)
            } else {
                val wantRange = if (f.ranged) 420f else z.type.w / 2 + 16f
                if (abs(dx) > wantRange) z.x += sign(dx) * sp * dt
                else if (f.ranged && abs(dx) < 260f) z.x -= sign(dx) * sp * 0.5f * dt
                // walk off ledges, fall to whatever is below
                var feet = z.y + f.vy * dt
                f.vy += Game.G * dt
                val gnd = rm.support(z.x, z.y)
                if (feet >= gnd) {
                    feet = gnd
                    f.vy = 0f
                }
                z.y = feet
            }
            z.x = z.x.coerceIn(30f, rm.width - 30f)
            if (f.ranged) {
                f.spitCd -= dt
                if (f.spitCd <= 0f && abs(dx) < 800f) {
                    f.spitCd = 2.6f
                    val sx = z.x
                    val sy = z.y - z.type.h * 0.7f
                    val t = 0.9f
                    rm.shots.add(RoomShot(sx, sy, (d.x - sx) / t, (d.y - 50f - sy) / t - Game.G * 0.5f * t, 0, 6f + run.stage * 0.6f))
                }
            }
            val touch = abs(dx) < z.type.w / 2 + 18f && d.y > z.y - z.type.h - 10f && d.y - 90f < z.y
            if (touch) {
                if (z.type.flying) {
                    hurt(run, f, 99f, 0f)
                    g.hurtDriver(bite * 0.6f)
                } else {
                    g.hurtDriver(bite * dt)
                }
            }
        }
        rm.foes.removeAll { !it.z.alive }
    }

    private fun updateBoss(run: RoomRun, dt: Float) {
        val b = run.room.roomBoss ?: return
        if (!b.alive) return
        val d = g.driver
        b.anim += dt
        if (b.flash > 0f) b.flash -= dt
        val dx = d.x - b.x
        b.dir = if (dx > 0f) 1f else -1f
        b.look.anim = b.anim
        b.fireCd -= dt
        when (b.kind) {
            0 -> { // cannon turret: fans of fireballs
                if (b.fireCd <= 0f) {
                    b.fireCd = 2.2f
                    for (k in -1..1) {
                        val a = atan2(d.y - 60f - (-b.h + 30f), dx) + k * 0.18f
                        run.room.shots.add(RoomShot(b.x, -b.h + 30f, cos(a) * 620f, sin(a) * 620f, 1, 10f + run.stage))
                    }
                    g.sfx.play(Sfx.S.CATAPULT, 0.6f)
                }
            }
            1 -> { // brute: lumbers over, then charges
                if (b.chargeT > 0f) {
                    b.chargeT -= dt
                    b.x += b.dir * 620f * dt
                } else {
                    b.x += b.dir * 70f * dt
                    if (b.fireCd <= 0f && abs(dx) < 700f) {
                        b.fireCd = 3.5f
                        b.chargeT = 0.9f
                        g.sfx.play(Sfx.S.ROAR, 0.6f)
                    }
                }
            }
            else -> { // big boss: closes in and hurls fire
                b.x += b.dir * 55f * dt
                if (b.fireCd <= 0f) {
                    b.fireCd = 1.8f
                    val a = atan2(d.y - 60f - (-b.h * 0.6f), dx)
                    run.room.shots.add(RoomShot(b.x, -b.h * 0.6f, cos(a) * 540f, sin(a) * 540f, 1, 12f + run.stage))
                }
            }
        }
        b.x = b.x.coerceIn(150f, run.room.width - 150f)
        b.look.x = b.x
        if (abs(dx) < b.w / 2 + 20f && d.y > -b.h) g.hurtDriver((if (b.chargeT > 0f) 40f else 18f) * dt)
    }

    private fun stats(run: RoomRun): Triple<Float, Float, Int> {
        val w = g.save.sideWeapon
        val dmg = w.damage * (1f + 0.25f * run.lv(Power.DAMAGE))
        val rate = (1f + 0.2f * run.lv(Power.RATE)) * (if (run.adrenT > 0f) 1.5f else 1f)
        return Triple(dmg, w.interval / rate, w.pellets + (if (w.melee) 0 else run.lv(Power.DUAL)))
    }

    private fun attack(run: RoomRun, dt: Float) {
        val d = g.driver
        val rm = run.room
        val w = g.save.sideWeapon
        val (dmg, interval, pellets) = stats(run)
        d.fireCd -= dt
        val gx = d.x
        val gy = d.y - 58f
        val reach = if (w.melee) w.range + 30f else w.range + 150f
        var best = reach
        var tx = 0f
        var ty = 0f
        for (f in rm.foes) {
            val z = f.z
            val dd = hypot(z.x - gx, z.y - z.type.h * 0.55f - gy)
            if (dd < best) {
                best = dd; tx = z.x; ty = z.y - z.type.h * 0.55f
            }
        }
        rm.roomBoss?.let { b -> if (b.alive) { val dd = hypot(b.x - gx, -b.h / 2 - gy) - b.w / 2; if (dd < best) { best = dd; tx = b.x; ty = -b.h / 2 } } }
        if (best >= reach) {
            if (d.atkT <= 0f) d.aim = if (d.face > 0f) 0f else PI.toFloat()
            return
        }
        d.aim = atan2(ty - gy, tx - gx)
        if (d.dir == 0f) d.face = if (tx >= d.x) 1f else -1f
        if (d.fireCd > 0f) return
        d.fireCd = interval
        if (w.melee) {
            d.atkT = 0.25f
            val f = if (tx >= d.x) 1f else -1f
            g.sfx.play(Sfx.S.HIT, 0.5f)
            for (fo in rm.foes) {
                val z = fo.z
                val dx = (z.x - d.x) * f
                if (dx < -20f || dx > w.range + z.type.w / 2 || abs(z.y - d.y) > 140f) continue
                hurt(run, fo, dmg, f * w.knock)
            }
            rm.roomBoss?.let { b -> if (b.alive && (b.x - d.x) * f in -20f..(w.range + b.w / 2)) hurtBoss(run, b, dmg) }
            return
        }
        val dist = hypot(tx - gx, ty - gy)
        val lob = if (w.gravity > 0f) w.gravity * dist / (w.speed * w.speed) * 0.5f else 0f
        for (k in 0 until pellets) {
            val spread = if (w.pellets > 1) w.spread else 0.09f
            val off = if (pellets > 1) spread * (k - (pellets - 1) / 2f) / max(1f, (pellets - 1) / 2f) else 0f
            var a = d.aim + off + rnd(-0.03f, 0.03f)
            a -= lob * (if (cos(a) >= 0f) 1f else -1f)
            g.roomProjectiles.add(Projectile(WeaponType.PISTOL, gx, gy, cos(a) * w.speed, sin(a) * w.speed, dmg, w.pierce, 0f).also {
                it.life = (w.range + 200f) / w.speed + 0.15f
                it.side = w
                it.grav = w.gravity
            })
        }
        g.particles.add(Particle(gx + cos(d.aim) * 26f, gy + sin(d.aim) * 26f, 0f, 0f, 0.06f, 0.06f, 9f, 0xFFFFE082.toInt()))
        if (w != SideWeapon.BOW && w != SideWeapon.DART && w != SideWeapon.SPEAR) d.muzzleT = 0.07f
        g.sfx.play(if (w == SideWeapon.BOW || w == SideWeapon.DART) Sfx.S.BOW else Sfx.S.SHOOT, 0.35f)
    }

    private fun powersTick(run: RoomRun, dt: Float) {
        val d = g.driver
        val rm = run.room
        // dynamite at the nearest monster
        val dyn = run.lv(Power.DYNAMITE)
        if (dyn > 0) {
            run.dynCd -= dt
            if (run.dynCd <= 0f) {
                val t = rm.foes.minByOrNull { abs(it.z.x - d.x) }
                if (t != null && abs(t.z.x - d.x) < 900f) {
                    run.dynCd = 3.4f / (1f + 0.15f * dyn)
                    val time = 0.8f
                    rm.shots.add(RoomShot(d.x, d.y - 70f, (t.z.x - d.x) / time, (t.z.y - 30f - (d.y - 70f)) / time - Game.G * 0.5f * time, 2, 4f + 2f * dyn).also { it.life = time })
                }
            }
        }
        // burning aura
        val match = run.lv(Power.MATCH)
        if (match > 0) {
            val r = 120f + 20f * match
            run.matchT += dt
            if (Random.nextFloat() < dt * 14f) {
                val a = Random.nextFloat() * 6.28f
                g.particles.add(Particle(d.x + cos(a) * r * 0.8f, d.y - 50f + sin(a) * r * 0.5f, 0f, -60f, 0.4f, 0.4f, 7f, 0xFFFF9100.toInt()))
            }
            for (f in rm.foes) if (hypot(f.z.x - d.x, f.z.y - d.y) < r) f.burnT = max(f.burnT, 0.6f + 0.1f * match)
        }
        // orbiting junk
        val orb = run.lv(Power.ORBIT)
        if (orb > 0) {
            run.orbitA += dt * 3.2f
            for (k in 0 until orb) {
                val a = run.orbitA + k * (2f * PI.toFloat() / orb)
                val ox = d.x + cos(a) * 115f
                val oy = d.y - 50f + sin(a) * 115f
                for (f in rm.foes) if (f.orbitCd <= 0f && abs(f.z.x - ox) < f.z.type.w / 2 + 22f && oy > f.z.y - f.z.type.h - 20f && oy < f.z.y + 20f) {
                    f.orbitCd = 0.45f
                    hurt(run, f, 2f + orb, sign(f.z.x - d.x) * 120f)
                }
                rm.roomBoss?.let { b -> if (b.alive && abs(b.x - ox) < b.w / 2 + 20f && oy > -b.h && run.orbitA % 0.5f < dt * 3.2f) hurtBoss(run, b, 1f + orb * 0.5f) }
            }
        }
        // car battery: chain lightning
        val bat = run.lv(Power.BATTERY)
        if (bat > 0) {
            run.batCd -= dt
            if (run.batCd <= 0f && rm.foes.isNotEmpty()) {
                run.batCd = 2.5f
                val pts = ArrayList<Float>()
                pts.add(d.x); pts.add(d.y - 60f)
                var px = d.x
                var py = d.y - 60f
                val hit = HashSet<Foe>()
                repeat(2 + bat) {
                    val n = rm.foes.filter { it !in hit && it.z.alive && hypot(it.z.x - px, it.z.y - it.z.type.h / 2 - py) < 420f }.minByOrNull { hypot(it.z.x - px, it.z.y - py) } ?: return@repeat
                    hit.add(n)
                    px = n.z.x; py = n.z.y - n.z.type.h / 2
                    pts.add(px); pts.add(py)
                    hurt(run, n, 2f + 1.2f * bat, 0f)
                }
                if (pts.size > 2) {
                    run.zaps.add(Zap(pts.toFloatArray()))
                    g.sfx.play(Sfx.S.HIT, 0.6f)
                }
            }
        }
    }

    private fun updateShots(run: RoomRun, dt: Float) {
        val d = g.driver
        val rm = run.room
        for (s in g.roomProjectiles) {
            if (!s.alive) continue
            s.life -= dt
            s.age += dt
            s.vy += s.grav * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            if (s.life <= 0f || s.y > 0f || s.y < -600f || s.x < 0f || s.x > rm.width) {
                s.alive = false
                continue
            }
            for (f in rm.foes) {
                val z = f.z
                if (!z.alive || z in s.hit) continue
                if (abs(z.x - s.x) < z.type.w / 2 + 8f && s.y > z.y - z.type.h - 8f && s.y < z.y + 8f) {
                    s.hit.add(z)
                    hurt(run, f, s.damage, s.vx * 0.06f)
                    if (run.lv(Power.NITRO) > 0) f.slowT = 1.6f
                    if (--s.pierce <= 0) {
                        s.alive = false; break
                    }
                }
            }
            if (!s.alive) continue
            rm.roomBoss?.let { b ->
                if (b.alive && b !in s.hit && abs(b.x - s.x) < b.w / 2 && s.y > -b.h - 20f) {
                    s.hit.add(b)
                    hurtBoss(run, b, s.damage)
                    if (--s.pierce <= 0) s.alive = false
                }
            }
        }
        g.roomProjectiles.removeAll { !it.alive }
        for (s in rm.shots) {
            if (!s.alive) continue
            s.life -= dt
            if (s.kind != 1) s.vy += Game.G * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            if (s.kind == 2) {
                // the player's dynamite: goes off where it lands
                if (s.life <= 0f || s.y > 0f) {
                    s.alive = false
                    blast(run, s.x, min(s.y, 0f), 150f + 15f * run.lv(Power.DYNAMITE), s.dmg)
                }
                continue
            }
            if (s.life <= 0f || s.y > 0f || s.x < 0f || s.x > rm.width) {
                s.alive = false
                if (s.kind == 1) g.sparksAt(s.x, min(s.y, 0f))
                continue
            }
            if (abs(s.x - d.x) < 26f && s.y > d.y - 100f && s.y < d.y + 6f) {
                s.alive = false
                g.hurtDriver(s.dmg)
                g.bleedAt(d.x, d.y - 50f, 4)
            }
        }
        rm.shots.removeAll { !it.alive }
    }

    private fun updateCash(run: RoomRun, dt: Float) {
        val d = g.driver
        val rm = run.room
        val reach = 170f * (1f + run.lv(Power.MAGNET))
        for (c in rm.cash) {
            c.t += dt
            val dx = d.x - c.x
            val dy = d.y - 50f - c.y
            val dist = hypot(dx, dy)
            if (c.t > 0.35f && dist < reach) {
                val sp = 900f
                c.vx = dx / max(1f, dist) * sp
                c.vy = dy / max(1f, dist) * sp
            } else {
                c.vy += Game.G * dt
                c.vx *= 1f - min(1f, dt * 2f)
            }
            c.x += c.vx * dt
            c.y += c.vy * dt
            val gnd = rm.support(c.x, c.y)
            if (c.y > gnd - 8f && dist >= reach) {
                c.y = gnd - 8f
                c.vy = 0f
            }
            if (dist < 40f && c.t > 0.2f) {
                c.alive = false
                if (c.heart) {
                    d.hp = min(d.maxHp, d.hp + 15f)
                    g.texts.add(FloatText("+15♥", d.x, d.y - 130f, 0xFF69F0AE.toInt()).also { it.size = 30f })
                } else {
                    g.giveMoneyQuiet(c.value)
                }
                g.sfx.play(Sfx.S.COIN, 0.5f)
            }
        }
        rm.cash.removeAll { !it.alive }
    }

    fun hurt(run: RoomRun, f: Foe, dmg: Float, push: Float, fx: Boolean = true) {
        val z = f.z
        if (!z.alive) return
        z.hp -= dmg
        f.hitT = 0.1f
        if (push != 0f && !z.type.flying) z.x += push * 0.25f
        if (fx) g.bleedAt(z.x, z.y - z.type.h * 0.6f, 3)
        if (z.hp <= 0f) kill(run, f)
    }

    private fun kill(run: RoomRun, f: Foe) {
        val z = f.z
        z.alive = false
        g.kills++
        g.bleedAt(z.x, z.y - z.type.h / 2, 14)
        g.sfx.play(Sfx.S.SPLAT, 0.7f)
        val rm = run.room
        val bills = 1 + (if (z.type == ZombieType.FAT || z.type == ZombieType.BLOB) 2 else 0)
        val value = max(1, (z.type.reward * g.monsterRewardMul * 0.5f / bills).toInt())
        repeat(bills) { rm.cash.add(Cash(z.x, z.y - z.type.h / 2, rnd(-160f, 160f), -rnd(300f, 520f), value)) }
        if (Random.nextFloat() < 0.05f) rm.cash.add(Cash(z.x, z.y - z.type.h / 2, rnd(-100f, 100f), -400f, 0, heart = true))
        if (run.lv(Power.ADREN) > 0) run.adrenT = 2f + run.lv(Power.ADREN)
        if (run.lv(Power.VAMP) > 0) g.driver.hp = min(g.driver.maxHp, g.driver.hp + 1.5f * run.lv(Power.VAMP))
        gainXp(run, if (z.type == ZombieType.FAT || z.type == ZombieType.BLOB) 2 else 1)
        if (run.lv(Power.BOOM) > 0) blast(run, z.x, z.y - z.type.h / 2, 120f, 2f + 1.5f * run.lv(Power.BOOM), skip = f)
    }

    private fun hurtBoss(run: RoomRun, b: RoomBoss, dmg: Float) {
        if (!b.alive) return
        b.hp -= dmg
        b.flash = 0.08f
        if (b.hp <= 0f) {
            b.alive = false
            g.kills++
            g.explodeAt(b.x, -b.h / 2, 60)
            g.sfx.play(Sfx.S.EXPLODE)
            g.shake = 0.5f
            repeat(10) { run.room.cash.add(Cash(b.x, -b.h / 2, rnd(-300f, 300f), -rnd(300f, 700f), (20 * g.monsterRewardMul).toInt())) }
            g.texts.add(FloatText("HẠ TRÙM!", b.x, -b.h - 60f, 0xFFFFD740.toInt()).also { it.size = 60f })
            gainXp(run, 10)
            if (run.index < run.total) run.upgradeQueued++ // Damage+ or Heal+ after the fight
        }
    }

    private fun blast(run: RoomRun, x: Float, y: Float, r: Float, dmg: Float, skip: Foe? = null) {
        g.explodeAt(x, y, 22)
        g.sfx.play(Sfx.S.BOOM, 0.5f)
        g.shake = max(g.shake, 0.12f)
        for (f in run.room.foes.toList()) if (f !== skip && f.z.alive && hypot(f.z.x - x, f.z.y - f.z.type.h / 2 - y) < r) hurt(run, f, dmg, sign(f.z.x - x) * 200f)
        run.room.roomBoss?.let { b -> if (b.alive && hypot(b.x - x, -b.h / 2 - y) < r + b.w / 2) hurtBoss(run, b, dmg) }
    }

    private fun gainXp(run: RoomRun, n: Int) {
        run.xp += n
        while (run.xp >= run.xpNeed) {
            run.xp -= run.xpNeed
            run.level++
            run.levelUps++
        }
    }
}
