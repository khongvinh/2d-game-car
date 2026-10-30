package com.khongvinh.carsurvival

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Defences built on the camp's slots. [ground] = only in front of the wall,
 * [blocks] = monsters must break it to pass. [cost] is scrap.
 */
enum class DefType(
    val title: String,
    val desc: String,
    val icon: String,
    val cost: Int,
    val hp: Float,
    val range: Float = 0f,
    val damage: Float = 0f,
    val interval: Float = 1f,
    val splash: Float = 0f,
    val ground: Boolean = false,
    val blocks: Boolean = false,
) {
    WOOD("Rào gỗ", "Chặn đường, zombie phải phá mới qua", "🪵", 25, 160f, ground = true, blocks = true),
    MG("Súng máy", "Bắn nhanh, tầm vừa", "🔫", 60, 90f, 760f, 1.3f, 0.16f),
    SPIKE("Rào gai", "Chặn đường và cứa zombie đứng sát", "⛓", 45, 220f, 60f, 7f, 1f, ground = true, blocks = true),
    CANNON("Pháo", "Đạn nổ lan, chậm mà mạnh", "💣", 120, 120f, 980f, 9f, 1.6f, 120f),
    FLAME("Súng lửa", "Phun lửa đốt cả đám trước mặt", "🔥", 110, 110f, 420f, 0.8f, 0.09f),
    TESLA("Tesla", "Sét nhảy qua 3 con", "⚡", 140, 110f, 500f, 4.5f, 1.1f),
    MORTAR("Cối", "Bắn vòng cung rất xa, nổ to", "🎯", 130, 110f, 1600f, 12f, 2.6f, 170f),
    MINE("Mìn", "Nổ 1 lần khi zombie giẫm lên", "💥", 30, 1f, 0f, 30f, 0f, 160f, ground = true),
}

/** Something to unlock by collecting enough parts: a vehicle, a defence or a wall level. */
class Blueprint(val id: Int, val kind: Int, val ref: Int, val title: String, val parts: Int, val cost: Int) {
    companion object {
        const val CAR = 0
        const val DEF = 1
        const val WALL = 2
    }
}

object Blueprints {
    val all: List<Blueprint> = run {
        val l = ArrayList<Blueprint>()
        val cars = arrayOf("Xe con gỉ sét", "Bán tải bọc thép", "Xe bọc thép", "Xe tăng")
        for (k in 1..4) l.add(Blueprint(l.size, Blueprint.CAR, k, cars[k - 1], 4 + k, 300 * k * k))
        for (d in listOf(DefType.SPIKE, DefType.CANNON, DefType.FLAME, DefType.TESLA, DefType.MORTAR, DefType.MINE)) {
            l.add(Blueprint(l.size, Blueprint.DEF, d.ordinal, d.title, if (d == DefType.MINE || d == DefType.SPIKE) 3 else 5, d.cost * 3))
        }
        l.add(Blueprint(l.size, Blueprint.WALL, 1, "Tường sắt", 5, 600))
        l.add(Blueprint(l.size, Blueprint.WALL, 2, "Tường bê tông", 7, 1500))
        l
    }

    fun forDef(d: DefType) = all.firstOrNull { it.kind == Blueprint.DEF && it.ref == d.ordinal }
}

/** A built defence on slot [slot]. */
class Def(val slot: Int, var type: DefType, var level: Int) {
    var maxHp = type.hp * (1f + 0.5f * (level - 1))
    var hp = maxHp
    var cd = 0f
    var aim = PI.toFloat()
    var flash = 0f
    var fireT = 0f
    val dmgMul get() = 1f + 0.5f * (level - 1)
}

/** Camp layout, the night siege and the day actions. Lives next to [Game]. */
class Campaign(private val g: Game) {
    companion object {
        const val WALL_X = 700f
        const val SPAWN_X = 2700f
        const val GOAL_M = 12000
        val WALL_HP = floatArrayOf(320f, 650f, 1100f)
        val WALL_H = floatArrayOf(230f, 260f, 290f)
        val WALL_NAMES = arrayOf("Tường gỗ", "Tường sắt", "Tường bê tông")

        /** Slot positions: 0 on the wall, 1 on the watchtower, 2..6 on the ground in front. */
        val SLOT_X = floatArrayOf(WALL_X + 20f, 470f, 920f, 1110f, 1300f, 1490f, 1680f)
        const val SLOTS = 7
    }

    private val save get() = g.save

    // ------------------------------------------------------------ state

    val defs = arrayOfNulls<Def>(SLOTS)
    var wallHp = 0f
    val wallMax get() = WALL_HP[save.wallLevel.coerceIn(0, 2)]

    // siege
    val foes = ArrayList<Foe>()
    val shots = ArrayList<RoomShot>()
    val zaps = ArrayList<Zap>()
    val tracers = ArrayList<FloatArray>()
    var wave = 0
    var waves = 0
    private var toSpawn = 0
    private var spawnT = 0f
    var breakT = 0f
    var bossLeft = 0
    var nightKills = 0
    var nightCash = 0
    var nightScrap = 0

    /** Slot chosen for the build panel, -1 = none. */
    var selSlot = -1

    /** Message shown over the camp (night survived, camp fallen, arrived). */
    var banner = ""
    var bannerSub = ""
    var bannerT = 0f

    /** Loot carried back by the current expedition. */
    var lootScrap = 0
    var lootFuel = 0
    val lootParts = HashMap<Int, Int>()

    val region: Region get() = Region.entries[(save.distM / 3000).coerceIn(0, 3)]

    /** Stage number used for terrain, monsters and themes (1..20 along the 20 km). */
    val zone get() = (1 + save.distM / 600).coerceIn(1, Game.STAGES)

    fun load() {
        for (i in 0 until SLOTS) {
            val t = save.slotType[i]
            defs[i] = if (t >= 0) Def(i, DefType.entries[t], save.slotLevel[i].coerceAtLeast(1)) else null
            defs[i]?.let { d -> if (save.slotHp[i] > 0) d.hp = min(d.maxHp, save.slotHp[i].toFloat()) }
        }
        wallHp = if (save.wallHpSaved > 0) min(wallMax, save.wallHpSaved.toFloat()) else wallMax
    }

    fun store() {
        for (i in 0 until SLOTS) {
            val d = defs[i]
            save.slotType[i] = d?.type?.ordinal ?: -1
            save.slotLevel[i] = d?.level ?: 0
            save.slotHp[i] = d?.hp?.toInt() ?: 0
        }
        save.wallHpSaved = wallHp.toInt()
        save.save()
    }

    fun unlocked(d: DefType) = Blueprints.forDef(d)?.let { save.bpOwned and (1 shl it.id) != 0 } ?: true
    fun owned(bp: Blueprint) = save.bpOwned and (1 shl bp.id) != 0

    fun slotY(i: Int) = when (i) {
        0 -> -WALL_H[save.wallLevel.coerceIn(0, 2)]
        1 -> -200f
        else -> 0f
    }

    fun allowed(i: Int, d: DefType) = unlocked(d) && (if (i < 2) !d.ground else true)

    // ------------------------------------------------------------ building

    fun buildCost(d: DefType) = d.cost
    fun upgradeCost(df: Def) = df.type.cost * (df.level + 1) / 2 + 20
    fun repairCost(df: Def) = ((df.maxHp - df.hp) / df.maxHp * df.type.cost * 0.6f).toInt().coerceAtLeast(0)

    fun build(i: Int, d: DefType) {
        if (defs[i] != null || !allowed(i, d) || save.scrap < d.cost) return
        save.scrap -= d.cost
        defs[i] = Def(i, d, 1)
        g.sfx.play(Sfx.S.WOOD, 0.8f)
        g.burstAt(SLOT_X[i], slotY(i) - 40f, 0xFFFFD740.toInt(), 14)
        store()
    }

    fun upgrade(i: Int) {
        val df = defs[i] ?: return
        if (df.level >= 3 || df.type == DefType.MINE) return
        val cost = upgradeCost(df)
        if (save.scrap < cost) return
        save.scrap -= cost
        df.level++
        df.maxHp = df.type.hp * (1f + 0.5f * (df.level - 1))
        df.hp = df.maxHp
        g.sfx.play(Sfx.S.POWER, 0.8f)
        store()
    }

    fun repair(i: Int) {
        val df = defs[i] ?: return
        val cost = repairCost(df)
        if (cost <= 0 || save.scrap < cost) return
        save.scrap -= cost
        df.hp = df.maxHp
        g.sfx.play(Sfx.S.WOOD, 0.6f)
        store()
    }

    fun sell(i: Int) {
        val df = defs[i] ?: return
        save.scrap += df.type.cost * df.level / 2
        defs[i] = null
        g.sfx.play(Sfx.S.COIN)
        store()
    }

    fun wallRepairCost() = ((wallMax - wallHp) / wallMax * 80f).toInt()

    fun repairWall() {
        val cost = wallRepairCost()
        if (cost <= 0 || save.scrap < cost) return
        save.scrap -= cost
        wallHp = wallMax
        g.sfx.play(Sfx.S.WOOD, 0.6f)
        store()
    }

    /** Workshop: assemble a blueprint once all its parts are in. */
    fun assemble(bp: Blueprint) {
        if (owned(bp) || save.bpParts[bp.id] < bp.parts || save.money < bp.cost) return
        save.money -= bp.cost
        save.bpOwned = save.bpOwned or (1 shl bp.id)
        when (bp.kind) {
            Blueprint.CAR -> save.carTier = max(save.carTier, bp.ref)
            Blueprint.WALL -> {
                save.wallLevel = max(save.wallLevel, bp.ref)
                wallHp = wallMax
            }
        }
        g.sfx.play(Sfx.S.STAGE)
        store()
    }

    /** The blueprint that loot parts should go to: a random one still missing parts. */
    fun partTarget(rnd: Random): Blueprint? {
        val open = Blueprints.all.filter { !owned(it) && save.bpParts[it.id] + (lootParts[it.id] ?: 0) < it.parts }
        if (open.isEmpty()) return null
        // the next car is wanted a little more often
        val car = open.firstOrNull { it.kind == Blueprint.CAR }
        return if (car != null && rnd.nextFloat() < 0.35f) car else open.random(rnd)
    }

    // ------------------------------------------------------------ the night

    fun startNight() {
        selSlot = -1
        val n = save.nights + 1
        waves = min(6, 3 + n / 3)
        wave = 0
        foes.clear(); shots.clear(); zaps.clear(); tracers.clear()
        nightKills = 0; nightCash = 0; nightScrap = 0
        bossLeft = if (n % 5 == 0) 1 else 0
        breakT = 2.5f
        // mines are re-armed at nightfall only if built; nothing else to do
        banner = "ĐÊM $n"
        bannerSub = "${region.title} • $waves đợt zombie"
        bannerT = 2.5f
        g.sfx.play(Sfx.S.STORM, 0.8f)
    }

    private fun waveSize(n: Int, w: Int) = 5 + n * 2 + w * 2 + region.ordinal * 3

    private fun spawnOne(n: Int) {
        val r = Random.nextFloat()
        val t = when (region) {
            Region.DESERT -> if (r < 0.15f && n > 2) ZombieType.FAT else if (r < 0.45f) ZombieType.RUNNER else ZombieType.WALKER
            Region.VOLCANO -> if (r < 0.3f) ZombieType.LIZARD else if (r < 0.5f) ZombieType.FAT else ZombieType.WALKER
            Region.NIGHT -> if (r < 0.25f) ZombieType.BAT else if (r < 0.55f) ZombieType.RUNNER else ZombieType.WALKER
            Region.ALIEN -> if (r < 0.3f) ZombieType.BLOB else ZombieType.ALIEN
        }
        val z = Zombie(t, SPAWN_X + Random.nextFloat() * 200f, if (t.flying) -220f - Random.nextFloat() * 80f else 0f)
        z.hp = t.hp * 7f * (1f + 0.2f * n + 0.3f * region.ordinal)
        z.fly = 0f
        foes.add(Foe(z, !t.flying && t != ZombieType.FAT && Random.nextFloat() < 0.18f))
    }

    private fun spawnBoss(n: Int) {
        val z = Zombie(ZombieType.FAT, SPAWN_X + 100f, 0f)
        z.hp = 300f * (1f + 0.25f * n)
        z.fly = 1f // marks the boss (drawn big)
        foes.add(Foe(z, false))
        g.sfx.play(Sfx.S.ROAR)
        banner = "TRÙM TỚI!"
        bannerSub = ""
        bannerT = 1.6f
    }

    fun isBoss(f: Foe) = f.z.fly == 1f

    /** Runs the night; returns true when it is over (won or the camp fell). */
    fun updateSiege(dt: Float) {
        val n = save.nights + 1
        if (bannerT > 0f) bannerT -= dt
        for (z in zaps) z.life -= dt
        zaps.removeAll { it.life <= 0f }
        tracers.removeAll { it[4] -= dt; it[4] <= 0f }
        // waves
        if (toSpawn > 0) {
            spawnT -= dt
            if (spawnT <= 0f) {
                spawnT = max(0.35f, 1.1f - n * 0.03f)
                spawnOne(n)
                toSpawn--
            }
        } else if (foes.isEmpty()) {
            if (wave >= waves && bossLeft == 0) {
                nightWon()
                return
            }
            breakT -= dt
            if (breakT <= 0f) {
                if (wave < waves) {
                    wave++
                    toSpawn = waveSize(n, wave)
                    spawnT = 0f
                    breakT = 5f
                    banner = "ĐỢT $wave / $waves"
                    bannerSub = ""
                    bannerT = 1.5f
                    if (wave > 1) g.sfx.play(Sfx.S.BEEP)
                } else if (bossLeft > 0) {
                    bossLeft--
                    spawnBoss(n)
                    breakT = 5f
                }
            }
        }
        updateFoes(dt, n)
        updateDefs(dt)
        updateShots(dt)
        if (wallHp <= 0f) campFell()
    }

    /** What stops a monster at [x]: the nearest ground blocker or the wall. Returns its x (front edge). */
    private fun blockerAt(x: Float): Pair<Float, Def?> {
        var bx = WALL_X + 40f
        var bd: Def? = null
        for (i in 2 until SLOTS) {
            val d = defs[i] ?: continue
            if (d.hp <= 0f || d.type == DefType.MINE) continue
            val front = SLOT_X[i] + 40f
            if (front <= x + 1f && front > bx) {
                bx = front
                bd = d
            }
        }
        return bx to bd
    }

    private fun updateFoes(dt: Float, n: Int) {
        val bite = 7f + n * 0.9f
        for (f in foes) {
            val z = f.z
            if (!z.alive) continue
            if (f.slowT > 0f) f.slowT -= dt
            if (f.hitT > 0f) f.hitT -= dt
            if (f.burnT > 0f) {
                f.burnT -= dt
                hurt(f, 1.5f * dt, false)
                if (Random.nextFloat() < dt * 15f) g.particles.add(Particle(z.x + rnd(-10f, 10f), z.y - rnd(10f, z.type.h), rnd(-20f, 20f), -rnd(60f, 120f), 0.4f, 0.4f, rnd(5f, 9f), 0xFFFF6D00.toInt()))
                if (!z.alive) continue
            }
            val boss = isBoss(f)
            z.phase += dt * (if (z.type == ZombieType.RUNNER || z.type == ZombieType.LIZARD) 9f else if (z.type == ZombieType.BAT) 14f else 4f)
            val speed = max(40f, z.type.speed * (if (boss) 0.8f else 1.25f)) * (if (f.slowT > 0f) 0.5f else 1f)
            if (z.type.flying) {
                // bats fly over the barricades straight at the wall
                val tx = WALL_X + 50f
                if (z.x > tx) z.x -= speed * dt else {
                    wallHp -= bite * 0.6f * dt
                    if (Random.nextFloat() < dt * 2f) g.sparksAt(WALL_X + 30f, z.y)
                }
                z.y = -200f + sin(z.phase * 0.3f) * 30f
                continue
            }
            // mines go off underfoot
            for (i in 2 until SLOTS) {
                val d = defs[i] ?: continue
                if (d.type == DefType.MINE && d.hp > 0f && abs(z.x - SLOT_X[i]) < 30f) {
                    d.hp = 0f
                    defs[i] = null
                    blast(SLOT_X[i], -20f, d.type.splash, d.type.damage * d.dmgMul)
                }
            }
            if (!z.alive) continue
            val (bx, bd) = blockerAt(z.x)
            val stopAt = bx + z.type.w / 2 + (if (f.ranged) 380f else 4f)
            if (z.x > stopAt) {
                z.x -= speed * dt
            } else if (f.ranged) {
                f.spitCd -= dt
                if (f.spitCd <= 0f) {
                    f.spitCd = 2.4f
                    val t = 0.9f
                    val sx = z.x
                    val sy = -z.type.h * 0.7f
                    val tx = bx - 10f
                    val ty = -60f
                    shots.add(RoomShot(sx, sy, (tx - sx) / t, (ty - sy) / t - Game.G * 0.5f * t, 0, 6f + n * 0.5f))
                }
            } else {
                val dmg = bite * (if (boss) 4f else if (z.type == ZombieType.FAT || z.type == ZombieType.BLOB) 1.8f else 1f) * dt
                if (bd != null) {
                    bd.hp -= dmg
                    bd.flash = 0.08f
                    if (bd.type == DefType.SPIKE) hurt(f, bd.type.damage * bd.dmgMul * dt, false)
                    if (bd.hp <= 0f) {
                        defs[bd.slot] = null
                        g.explodeAt(SLOT_X[bd.slot], -40f, 18)
                        g.sfx.play(Sfx.S.WOOD)
                    }
                } else {
                    wallHp -= dmg
                }
                if (Random.nextFloat() < dt * 2f) g.sparksAt(bx, -40f - Random.nextFloat() * 60f)
            }
        }
        foes.removeAll { !it.z.alive }
    }

    private fun target(x: Float, y: Float, range: Float, minRange: Float = 0f): Foe? {
        var best: Foe? = null
        var bx = Float.MAX_VALUE
        for (f in foes) {
            val d = hypot(f.z.x - x, f.z.y - f.z.type.h / 2 - y)
            if (d > range || d < minRange) continue
            // the one closest to the wall first
            if (f.z.x < bx) {
                bx = f.z.x
                best = f
            }
        }
        return best
    }

    private fun updateDefs(dt: Float) {
        for (i in 0 until SLOTS) {
            val d = defs[i] ?: continue
            if (d.flash > 0f) d.flash -= dt
            if (d.fireT > 0f) d.fireT -= dt
            if (d.type.range <= 0f || d.type.blocks) {
                continue
            }
            val x = SLOT_X[i]
            val y = slotY(i) - 50f
            d.cd -= dt
            val minR = if (d.type == DefType.MORTAR) 300f else 0f
            val t = target(x, y, d.type.range, minR) ?: continue
            val tx = t.z.x
            val ty = t.z.y - t.z.type.h * 0.55f
            d.aim = atan2(ty - y, tx - x)
            if (d.cd > 0f) continue
            d.cd = d.type.interval
            d.fireT = 0.08f
            val dmg = d.type.damage * d.dmgMul
            when (d.type) {
                DefType.MG -> {
                    hurt(t, dmg, true)
                    tracers.add(floatArrayOf(x + cos(d.aim) * 40f, y + sin(d.aim) * 40f, tx, ty, 0.06f))
                    g.sfx.play(Sfx.S.SHOOT, 0.25f)
                }
                DefType.CANNON -> {
                    shots.add(RoomShot(x + cos(d.aim) * 50f, y + sin(d.aim) * 50f, cos(d.aim) * 900f, sin(d.aim) * 900f, 3, dmg).also { it.life = 2f })
                    g.sfx.play(Sfx.S.CATAPULT, 0.5f)
                }
                DefType.MORTAR -> {
                    val time = 1.4f
                    shots.add(RoomShot(x, y - 20f, (tx - x) / time, (ty - y) / time - Game.G * 0.5f * time, 4, dmg).also { it.life = time })
                    g.sfx.play(Sfx.S.CATAPULT, 0.6f)
                }
                DefType.FLAME -> {
                    for (f in foes) if (f.z.x > x && f.z.x - x < d.type.range && abs(f.z.y - f.z.type.h / 2 - y) < 160f) {
                        hurt(f, dmg, false)
                        f.burnT = 2f
                    }
                    if (Random.nextFloat() < 0.8f) g.particles.add(Particle(x + 40f, y, 700f + rnd(-80f, 80f), rnd(-60f, 60f), 0.45f, 0.45f, rnd(10f, 18f), if (Random.nextBoolean()) 0xFFFF9100.toInt() else 0xFFFFEB3B.toInt(), 0f, 1.5f))
                }
                DefType.TESLA -> {
                    val pts = arrayListOf(x, y - 40f)
                    var px = x
                    var py = y - 40f
                    val hit = HashSet<Foe>()
                    repeat(3) {
                        val nx = foes.filter { it !in hit && it.z.alive && hypot(it.z.x - px, it.z.y - py) < 420f }.minByOrNull { hypot(it.z.x - px, it.z.y - py) } ?: return@repeat
                        hit.add(nx)
                        px = nx.z.x; py = nx.z.y - nx.z.type.h / 2
                        pts.add(px); pts.add(py)
                        hurt(nx, dmg, true)
                        nx.slowT = 0.8f
                    }
                    if (pts.size > 2) zaps.add(Zap(pts.toFloatArray()))
                    g.sfx.play(Sfx.S.HIT, 0.5f)
                }
                else -> {}
            }
        }
    }

    private fun updateShots(dt: Float) {
        for (s in shots) {
            if (!s.alive) continue
            s.life -= dt
            if (s.kind != 3) s.vy += Game.G * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            when (s.kind) {
                0 -> { // spit at the blocker or the wall
                    if (s.y >= 0f || s.life <= 0f || s.x <= WALL_X + 40f) {
                        s.alive = false
                        val (_, bd) = blockerAt(s.x + 60f)
                        if (bd != null && abs(SLOT_X[bd.slot] - s.x) < 120f) bd.hp -= s.dmg else if (s.x < WALL_X + 140f) wallHp -= s.dmg
                        g.particles.add(Particle(s.x, min(s.y, 0f), 0f, -40f, 0.4f, 0.4f, 12f, 0xFF76FF03.toInt()))
                    }
                }
                3 -> { // cannon ball: bursts on the first monster or the ground
                    val hit = foes.firstOrNull { abs(it.z.x - s.x) < it.z.type.w / 2 + 10f && s.y > it.z.y - it.z.type.h - 10f && s.y < it.z.y + 10f }
                    if (hit != null || s.y >= 0f || s.life <= 0f) {
                        s.alive = false
                        blast(s.x, min(s.y, 0f), DefType.CANNON.splash, s.dmg)
                    }
                }
                4 -> if (s.life <= 0f || s.y >= 0f) {
                    s.alive = false
                    blast(s.x, min(s.y, 0f), DefType.MORTAR.splash, s.dmg)
                }
            }
        }
        shots.removeAll { !it.alive }
    }

    private fun blast(x: Float, y: Float, r: Float, dmg: Float) {
        g.explodeAt(x, y, 24)
        g.sfx.play(Sfx.S.BOOM, 0.6f)
        g.shake = max(g.shake, 0.12f)
        for (f in foes.toList()) if (hypot(f.z.x - x, f.z.y - f.z.type.h / 2 - y) < r + f.z.type.w / 2) hurt(f, dmg, true)
    }

    private fun hurt(f: Foe, dmg: Float, fx: Boolean) {
        val z = f.z
        if (!z.alive) return
        z.hp -= dmg
        f.hitT = 0.08f
        if (fx) g.bleedAt(z.x, z.y - z.type.h * 0.6f, 2)
        if (z.hp <= 0f) {
            z.alive = false
            nightKills++
            g.kills++
            val cash = ((z.type.reward + (if (isBoss(f)) 200 else 0)) * (1f + 0.1f * region.ordinal)).toInt()
            nightCash += cash
            if (Random.nextFloat() < 0.3f || isBoss(f)) nightScrap += if (isBoss(f)) 40 else 2
            g.bleedAt(z.x, z.y - z.type.h / 2, 12)
            g.texts.add(FloatText("+$cash$", z.x, z.y - z.type.h - 30f, 0xFF69F0AE.toInt()).also { it.size = 30f; it.life = 0.9f })
            g.sfx.play(Sfx.S.SPLAT, 0.6f)
        }
    }

    private fun clearField() {
        foes.clear(); shots.clear(); zaps.clear(); tracers.clear()
    }

    private fun nightWon() {
        clearField()
        val n = save.nights + 1
        save.nights = n
        val bonusScrap = 15 + 4 * n
        val gems = 2 + n / 3
        save.money += nightCash
        save.scrap += nightScrap + bonusScrap
        save.gems += gems
        save.addStat(Stat.KILLS, nightKills)
        save.addStat(Stat.MONEY, nightCash)
        save.addStat(Stat.STAGES, 1)
        save.addStat(Stat.GEMS, gems)
        save.day++
        save.dayActed = false
        // mines that were not stepped on stay; everything keeps its damage
        store()
        banner = "SỐNG SÓT ĐÊM $n!"
        bannerSub = "+$nightCash$  +${nightScrap + bonusScrap}⚙  +$gems♦"
        bannerT = 4f
        g.sfx.play(Sfx.S.WIN)
        g.nightOver()
    }

    private fun campFell() {
        clearField()
        // the survivors flee: barricades and half the scrap are lost, the wall is patched up
        for (i in 0 until SLOTS) if (defs[i]?.type?.ground == true) defs[i] = null
        val lost = save.scrap / 2
        save.scrap -= lost
        save.money += nightCash / 2
        wallHp = wallMax * 0.5f
        save.day++
        save.dayActed = false
        store()
        banner = "TRẠI THẤT THỦ!"
        bannerSub = "Mất rào chắn và $lost⚙ — dựng lại rồi chiến tiếp"
        bannerT = 4f
        g.sfx.play(Sfx.S.LOSE)
        g.nightOver()
    }

    // ------------------------------------------------------------ day actions

    /** Back from an expedition: loot goes in (half of it if the driver fell). */
    fun expeditionDone(ok: Boolean) {
        val k = if (ok) 1f else 0.5f
        val sc = (lootScrap * k).toInt()
        val fu = (lootFuel * k).toInt()
        save.scrap += sc
        save.fuel += fu
        val got = ArrayList<String>()
        if (ok) for ((id, n) in lootParts) {
            save.bpParts[id] = min(Blueprints.all[id].parts, save.bpParts[id] + n)
            got.add("🔧 ${Blueprints.all[id].title} ${save.bpParts[id]}/${Blueprints.all[id].parts}")
        }
        lootScrap = 0; lootFuel = 0; lootParts.clear()
        if (ok) save.addStat(Stat.HOUSES, 1)
        save.dayActed = true
        store()
        banner = if (ok) "VỀ TRẠI AN TOÀN!" else "BỊ HẠ GỤC — MẤT NỬA ĐỒ"
        bannerSub = "+$sc⚙  +${fu}L xăng" + (if (got.isNotEmpty()) "  •  " + got.joinToString("  ") else if (!ok) "  •  mảnh ghép bị mất" else "")
        bannerT = 5f
    }

    /** The drive ended [metres] further on: a new camp there; ground defences stay behind. */
    fun driveDone(metres: Int, fuelUsed: Int) {
        save.fuel = max(0, save.fuel - fuelUsed)
        save.distM += metres
        save.addStat(Stat.METERS, metres)
        save.dayActed = true
        for (i in 0 until SLOTS) if (defs[i]?.type?.ground == true) defs[i] = null
        wallHp = wallMax
        store()
        banner = "DỰNG TRẠI MỚI!"
        bannerSub = "Đi thêm ${metres}m • Tổng ${save.distM}m / ${GOAL_M}m • ${region.title}"
        bannerT = 4f
    }

    /** A crate broken open on an expedition: scrap, maybe fuel or a part. */
    fun lootCrate(x: Float, y: Float) {
        var line = 0
        fun note(t: String, color: Int) {
            g.texts.add(FloatText(t, x, y - line * 44f, color).also { it.size = 30f; it.life = 1.8f })
            line++
        }
        val sc = 4 + Random.nextInt(8) + save.nights / 2
        lootScrap += sc
        note("+$sc⚙", 0xFFCFD8DC.toInt())
        if (Random.nextFloat() < 0.4f) {
            val f = 2 + Random.nextInt(3)
            lootFuel += f
            note("+${f}L XĂNG", 0xFFFF8A80.toInt())
        }
        if (Random.nextFloat() < 0.35f) partTarget(Random.Default)?.let { bp ->
            lootParts[bp.id] = (lootParts[bp.id] ?: 0) + 1
            note("🔧 MẢNH ${bp.title.uppercase()}", 0xFF80D8FF.toInt())
        }
        g.sfx.play(Sfx.S.WOOD, 0.8f)
        g.burstAt(x, y, 0xFFFFD740.toInt(), 14)
    }

    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
}
