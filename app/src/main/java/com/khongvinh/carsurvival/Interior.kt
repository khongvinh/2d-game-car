package com.khongvinh.carsurvival

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Furniture the driver can jump onto (one-way: stood on from above, walked
 * through below). [top] is the height of its surface above the floor.
 * [kind]: 0 table, 1 shelf, 2 sofa, 3 counter, 4 car lift, 5 fridge, 6 bed.
 */
class Platform(val l: Float, val r: Float, val top: Float, val kind: Int)

/**
 * Something to search. [y] is the height (above the floor) it stands on.
 * [kind]: 0 cupboard, 1 crate, 2 toolbox, 3 fridge, 4 medicine cabinet, 5 safe.
 */
class Chest(val x: Float, val y: Float, val kind: Int) {
    var opened = false
    var openT = 0f
}

/**
 * The inside of a roadside building: a row of rooms (floor at y = 0, up is
 * negative) with furniture, chests to search and monsters waiting. The driver
 * comes in at the door on the left and leaves the same way.
 */
class Interior(val b: Building, stage: Int, region: Region) {
    companion object {
        const val ROOM_W = 900f
        const val CEIL = 560f
        const val OPEN_TIME = 1.1f
        val ROOMS = arrayOf(
            arrayOf("PHÒNG KHÁCH", "BẾP", "PHÒNG NGỦ", "KHO"),
            arrayOf("QUẦY HÀNG", "KỆ HÀNG", "KHO HÀNG", "VĂN PHÒNG"),
            arrayOf("CỬA HÀNG", "PHÒNG MÁY", "KHO DẦU", "VĂN PHÒNG"),
            arrayOf("XƯỞNG SỬA", "KHO PHỤ TÙNG", "PHÒNG SƠN", "VĂN PHÒNG"),
            arrayOf("QUẦY THUỐC", "PHÒNG KHÁM", "KHO THUỐC", "PHÒNG NGHỈ"),
        )
    }

    private val rnd = Random(b.x.toInt() * 13 + stage * 7)
    val rooms = 2 + (if (b.kind == 1 || b.kind == 3) 1 else 0) + rnd.nextInt(2)
    val width = rooms * ROOM_W
    val exitX = 110f
    val platforms = ArrayList<Platform>()
    val chests = ArrayList<Chest>()
    val zombies = ArrayList<Zombie>()
    val shots = ArrayList<Projectile>()
    var camX = 0f

    /** Monsters that crawl out when a chest is opened. */
    var ambushes = 1 + stage / 6

    val cleared get() = chests.all { it.opened }
    val zombiesLeft get() = zombies.count { it.alive }

    init {
        for (room in 0 until rooms) {
            val l = room * ROOM_W
            // two pieces of furniture per room, fitting the kind of building
            val kinds = when (b.kind) {
                1 -> intArrayOf(3, 1)
                3 -> intArrayOf(4, 1)
                4 -> intArrayOf(3, 6)
                2 -> intArrayOf(3, 5)
                else -> intArrayOf(if (room % 2 == 0) 2 else 0, if (room == rooms - 1) 6 else 1)
            }
            val a = l + 220f + rnd.nextFloat() * 80f
            platforms.add(furniture(kinds[0], a))
            platforms.add(furniture(kinds[1], l + 560f + rnd.nextFloat() * 120f))
        }
        // chests: one per room, plus a bonus one on top of a shelf (jump for it)
        val chestKind = when (b.kind) {
            1 -> 1
            2 -> 1
            3 -> 2
            4 -> 4
            else -> 0
        }
        for (room in 0 until rooms) {
            val x = room * ROOM_W + (if (room == 0) 470f else 120f + rnd.nextFloat() * 120f)
            chests.add(Chest(x, 0f, if (room == rooms - 1 && rnd.nextFloat() < 0.5f) 5 else if (b.kind == 0 && room == 1) 3 else chestKind))
        }
        platforms.filter { it.kind == 1 || it.kind == 5 }.firstOrNull()?.let { p ->
            chests.add(Chest((p.l + p.r) / 2, p.top, 1))
        }
        // monsters waiting inside
        val n = min(9, 1 + rooms + stage / 4)
        for (k in 0 until n) {
            val x = 700f + (width - 900f) * (k + rnd.nextFloat() * 0.6f) / n
            zombies.add(spawn(region, x))
        }
    }

    private fun furniture(kind: Int, cx: Float): Platform {
        val (w, top) = when (kind) {
            0 -> 170f to 78f
            1 -> 200f to 190f
            2 -> 220f to 62f
            3 -> 260f to 96f
            4 -> 240f to 150f
            5 -> 90f to 180f
            else -> 230f to 70f
        }
        return Platform(cx - w / 2, cx + w / 2, top, kind)
    }

    fun spawn(region: Region, x: Float): Zombie {
        val r = rnd.nextFloat()
        val t = when (region) {
            Region.DESERT -> if (r < 0.2f) ZombieType.FAT else if (r < 0.45f) ZombieType.RUNNER else ZombieType.WALKER
            Region.VOLCANO -> if (r < 0.35f) ZombieType.LIZARD else if (r < 0.55f) ZombieType.FAT else ZombieType.WALKER
            Region.NIGHT -> if (r < 0.4f) ZombieType.RUNNER else if (r < 0.6f) ZombieType.FAT else ZombieType.WALKER
            Region.ALIEN -> if (r < 0.3f) ZombieType.BLOB else ZombieType.ALIEN
        }
        return Zombie(t, x, 0f)
    }

    /** Surface height under [x] for feet at height [feet] (one-way platforms). */
    fun support(x: Float, feet: Float): Float {
        var top = 0f
        for (p in platforms) if (x > p.l + 4f && x < p.r - 4f && feet <= -p.top + 8f) top = min(top, -p.top)
        return top
    }

    fun chestNear(x: Float, y: Float) = chests.firstOrNull { !it.opened && abs(it.x - x) < 80f && abs(-it.y - y) < 60f }

    fun roomName(x: Float) = ROOMS[b.kind.coerceIn(0, 4)][(x / ROOM_W).toInt().coerceIn(0, 3)]

    // ------------------------------------------------------------ update

    /** Driver movement, the monsters and the hand weapon, inside. */
    fun update(g: Game, dt: Float, left: Boolean, right: Boolean, jump: Boolean) {
        val d = g.driver
        if (d.hurtT > 0f) d.hurtT -= dt
        if (d.atkT > 0f) d.atkT -= dt
        d.dir = (if (right) 1f else 0f) - (if (left) 1f else 0f)
        val opening = chests.firstOrNull { it.openT > 0f && !it.opened }
        if (opening != null) d.dir = 0f
        if (d.dir != 0f) {
            d.face = d.dir
            d.x = (d.x + d.dir * 260f * dt).coerceIn(40f, width - 40f)
            d.walk += dt * 10f
        }
        if (jump && d.air <= 0.5f && d.vy >= 0f) {
            d.vy = -680f
            g.sfx.play(Sfx.S.CLICK, 0.4f)
        }
        var feet = d.y + d.vy * dt
        d.vy += Game.G * dt
        val ground = support(d.x, d.y)
        if (feet >= ground) {
            feet = ground
            d.vy = 0f
        }
        if (feet < -CEIL + 110f) {
            feet = -CEIL + 110f
            d.vy = max(0f, d.vy)
        }
        d.y = feet
        d.air = support(d.x, d.y) - d.y

        // searching
        opening?.let { ch ->
            ch.openT += dt * (if (g.scavengerPerk) 2f else 1f)
            if (Random.nextFloat() < dt * 8f) g.particles.add(Particle(ch.x + g.rnd(-30f, 30f), -ch.y - g.rnd(20f, 70f), g.rnd(-40f, 40f), -g.rnd(30f, 90f), 0.5f, 0.5f, 5f, 0xFFBCAAA4.toInt(), 0f, 1f))
            if (ch.openT >= OPEN_TIME) {
                ch.opened = true
                g.openChest(this, ch)
                if (ambushes > 0 && Random.nextFloat() < 0.45f) {
                    ambushes--
                    val side = if (d.x > width / 2) -1f else 1f
                    val zx = (d.x + side * 520f).coerceIn(80f, width - 80f)
                    zombies.add(spawn(g.region, zx))
                    g.texts.add(FloatText("ZOMBIE PHỤC KÍCH!", d.x, d.y - 200f, 0xFFFF5252.toInt()).also { it.size = 40f })
                    g.sfx.play(Sfx.S.ROAR, 0.5f)
                }
            }
        }

        updateZombies(g, dt)
        g.driverWeapon(dt, zombies, shots, inside = true)
        updateShots(g, dt)
        camX += ((d.x - g.viewW * 0.45f).coerceIn(-260f, max(-260f, width - g.viewW + 260f)) - camX) * min(1f, dt * 6f)
    }

    private fun updateZombies(g: Game, dt: Float) {
        val d = g.driver
        val bite = 7f + g.stage * 0.6f
        for (z in zombies) {
            if (!z.alive) continue
            z.phase += dt * (if (z.type == ZombieType.RUNNER || z.type == ZombieType.LIZARD) 9f else 4f)
            if (z.burnT > 0f) z.burnT -= dt
            // asleep until the driver comes into the room
            val dx = d.x - z.x
            if (abs(dx) < 650f) {
                val sp = max(55f, z.type.speed * 1.2f)
                if (abs(dx) > z.type.w / 2 + 14f) z.x += sign(dx) * sp * dt
            }
            // knocked back by heavy hits
            if (z.fly != 0f) {
                z.x += z.fly * dt
                z.fly *= 1f - min(1f, dt * 6f)
                if (abs(z.fly) < 5f) z.fly = 0f
            }
            z.x = z.x.coerceIn(30f, width - 30f)
            z.y = 0f
            val reach = abs(dx) < z.type.w / 2 + 18f && d.y > -z.type.h + 10f
            if (reach) {
                if (d.shieldT > 0f) {
                    // the shield bubble throws monsters off and hurts them
                    z.fly = -sign(dx).let { if (it == 0f) 1f else it } * 700f
                    z.hp -= 1.5f
                    g.sparksAt(d.x - sign(dx) * 30f, d.y - 50f)
                    if (z.hp <= 0f) kill(g, z, -sign(dx) * 300f)
                } else {
                    g.hurtDriver(bite * dt)
                }
            }
        }
        zombies.removeAll { !it.alive }
    }

    private fun updateShots(g: Game, dt: Float) {
        for (s in shots) {
            if (!s.alive) continue
            s.life -= dt
            s.age += dt
            s.vy += s.grav * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            if (s.life <= 0f || s.y > 0f || s.y < -CEIL || s.x < 0f || s.x > width) {
                s.alive = false
                g.sparksAt(s.x.coerceIn(0f, width), s.y.coerceIn(-CEIL, 0f))
                continue
            }
            for (z in zombies) {
                if (!z.alive || z in s.hit) continue
                if (abs(z.x - s.x) < z.type.w / 2 + 6f && s.y > -z.type.h - 6f) {
                    s.hit.add(z)
                    hit(g, z, s.damage, s.vx * 0.15f)
                    if (--s.pierce <= 0) {
                        s.alive = false; break
                    }
                }
            }
        }
        shots.removeAll { !it.alive }
    }

    fun hit(g: Game, z: Zombie, dmg: Float, push: Float) {
        z.hp -= dmg
        if (push != 0f) z.fly = push
        g.bleedAt(z.x, -z.type.h * 0.6f, 4)
        if (z.hp <= 0f) kill(g, z, push)
    }

    private fun kill(g: Game, z: Zombie, push: Float) {
        if (!z.alive) return
        z.alive = false
        g.kills++
        g.bleedAt(z.x, -z.type.h / 2, 16)
        g.giveMoney((z.type.reward * g.monsterRewardMul).toInt(), z.x, -z.type.h - 20f)
        g.sfx.play(Sfx.S.SPLAT, 0.8f)
        // a quick tumble of the body
        repeat(6) {
            val a = Random.nextFloat() * 6.28f
            g.particles.add(Particle(z.x, -z.type.h / 2, cos(a) * 160f + push * 0.3f, sin(a) * 160f - 120f, 0.6f, 0.6f, 9f, z.shirt, Game.G))
        }
    }

    /** Horizontal angle from the driver's hand to a monster's chest. */
    fun aimAt(gx: Float, gy: Float, z: Zombie) = atan2(-z.type.h * 0.55f - gy, z.x - gx)
}
