package com.khongvinh.carsurvival

import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Side-scrolling survival driving in the style of "Earn to Die": drive as far as you
 * can over hilly desert roads full of zombies, earn money, upgrade, and try again
 * until you reach the evacuation point at the end of each stage. A toxic storm
 * ("vòng bo") chases you from behind in three phases.
 *
 * The UI uses a virtual canvas [VIRTUAL_HEIGHT] units tall; width follows the screen.
 */
class Game(val save: SaveData, val sfx: Sfx, val music: Music) {
    companion object {
        const val VIRTUAL_HEIGHT = 720f
        const val METER = 30f
        const val G = 900f
        const val STAGES = 20

        /** Each stage's road is split into 5 levels (màn), marked by checkpoints. */
        const val LEVELS_PER_STAGE = 5
        const val MAX_PER_HUNDRED = 20
        const val ZOOM = 0.8f

        /** Storm phases: Bo 1 (60 s), Bo 2 (20 s), Bo 3 (5 s), then fully closed. */
        val PHASE_TIME = floatArrayOf(60f, 20f, 5f)
        val STORM_SPEED = floatArrayOf(150f, 450f, 1100f, 2600f)

        private const val SUBSTEP = 1f / 240f
    }

    var W = 1500f
    var H = VIRTUAL_HEIGHT

    var state = State.MENU
        private set
    var stateTime = 0f
    var anim = 0f

    // ---------------------------------------------------------------- run state
    var stage = 1
    lateinit var terrain: Terrain
    var goalX = 1f
    val car = Car()
    val zombies = ArrayList<Zombie>()
    val obstacles = ArrayList<Obstacle>()
    val debris = ArrayList<Debris>()
    val items = ArrayList<Item>()
    val shots = ArrayList<Projectile>()
    val particles = ArrayList<Particle>()
    val texts = ArrayList<FloatText>()

    /** End boss of every 5th stage (null on normal stages). */
    var boss: Boss? = null
    val bossRocks = ArrayList<BossRock>()
    var bossKilled = false

    var camX = 0f
    var camY = 0f
    val viewW get() = W / ZOOM
    val viewH get() = H / ZOOM

    var stormX = 0f
    var phase = 0
    var phaseLeft = PHASE_TIME[0]
    private var lastBeep = 0

    var kills = 0
    var smashed = 0
    var runMoney = 0
    var distMoney = 0
    var stageBonus = 0
    var maxDist = 0f
    var endReason = ""
    var newRecord = false

    // death sequence, damage feedback and run stats
    var dyingT = 0f
    private var dyingReason = ""
    var wrecked = false
    var hurtFlash = 0f
    private var pendingDmg = 0f
    private var dmgTimer = 0f
    private var airT = 0f
    var maxAir = 0f
    var maxKmh = 0f
    var minStormGap = Float.MAX_VALUE
    var intense = false
    var score = 0
    var newBestScore = false
    val achievements = ArrayList<String>()
    var shake = 0f
    var stormHurt = 0f
    var revived = false

    // car stats for this run
    private var topSpeed = 700f
    private var engineAcc = 520f
    private var mu = 1f
    private var armor = 0f
    var weapon = WeaponType.GUN
        private set

    // controls
    private val pointers = HashMap<Int, String>()
    private var gas = false
    private var brake = false
    private var leanBack = false
    private var leanFwd = false
    private var boostHeld = false
    private var wasBoosting = false

    private val renderer = Renderer(this)
    private val phys = CarPhysics()

    val distanceM get() = max(0f, car.x / METER)
    val goalM get() = goalX / METER

    init {
        sfx.enabled = save.sound
        save.stageSel = save.stageSel.coerceIn(1, maxStartStage())
        loadStage(save.stageSel)
    }

    fun maxStartStage() = min(STAGES, save.bestStage + 1)

    fun resize(w: Float, h: Float) {
        W = w
        H = h
    }

    private fun setState(s: State) {
        state = s
        stateTime = 0f
        pointers.clear()
        updateControls()
    }

    // --------------------------------------------------------------- flow

    fun stageGoalM(s: Int) = 700f + (s - 1) * 100f

    /** Builds the stage's terrain (used by the menu background too). */
    private fun loadStage(s: Int) {
        stage = s
        goalX = stageGoalM(s) * METER
        terrain = Terrain(s, goalX, s * 7919)
        car.x = 0f
        car.angle = 0f
        car.y = terrain.heightAt(0f) - Car.WHEEL_Y - Car.WHEEL_R + 4f
        camX = car.x - viewW * 0.3f
        camY = car.y - viewH * 0.6f
    }

    fun startRun() {
        loadStage(save.stageSel.coerceIn(1, maxStartStage()))
        val ct = save.carType
        val lv = { u: Upgrade -> save.level(u) }
        topSpeed = (700f + 110f * lv(Upgrade.ENGINE)) * ct.speedMul
        engineAcc = (520f + 80f * lv(Upgrade.ENGINE)) * ct.powerMul
        mu = 1.0f + 0.15f * lv(Upgrade.GRIP)
        armor = min(0.7f, ct.armor + 0.06f * lv(Upgrade.ARMOR))
        weapon = save.weaponType

        val c = car
        c.vx = 0f; c.vy = 0f; c.spin = 0f; c.angle = 0f
        c.maxHp = (100f + 30f * lv(Upgrade.ARMOR)) * ct.hpMul
        c.hp = c.maxHp
        c.maxFuel = (22f + 6f * lv(Upgrade.FUEL)) * ct.fuelMul
        c.fuel = c.maxFuel
        c.maxBoost = 1.5f + 1.2f * lv(Upgrade.BOOST)
        c.boost = c.maxBoost
        c.ammo = 20 + 20 * lv(Upgrade.AMMO)
        c.shieldT = 0f; c.cloneT = 0f; c.hurtT = 0f; c.flipT = 0f; c.stuckT = 0f; c.fireCd = 0f
        c.aim = 0f

        zombies.clear(); obstacles.clear(); debris.clear(); items.clear()
        shots.clear(); particles.clear(); texts.clear()
        bossRocks.clear()
        bossKilled = false
        boss = if (stage % 5 == 0) Boss(stage / 5, goalX - 520f, terrain.heightAt(goalX - 520f)) else null
        populate()

        stormX = -1500f
        phase = 0
        phaseLeft = PHASE_TIME[0]
        lastBeep = 0
        kills = 0; smashed = 0; runMoney = 0; distMoney = 0; stageBonus = 0
        maxDist = 0f
        lastSection = 0
        newRecord = false
        revived = false
        dyingT = 0f; wrecked = false; hurtFlash = 0f; pendingDmg = 0f; dmgTimer = 0f
        airT = 0f; maxAir = 0f; maxKmh = 0f; minStormGap = Float.MAX_VALUE; intense = false
        score = 0; newBestScore = false; achievements.clear()
        shake = 0f
        texts.add(FloatText("NGÀY ${save.day} — ẢI $stage", 0f, car.y - 160f, 0xFFFFD740.toInt()).also { it.life = 2.5f })
        sfx.play(Sfx.S.GO)
        setState(State.PLAYING)
    }

    /**
     * Places zombies, obstacles and pickups. Every 100 m of level n holds 3 + (n - 1)
     * zombies/obstacles (arithmetic progression), capped at [MAX_PER_HUNDRED].
     */
    /** Global level number (màn) of a position on the current stage, 1-based. */
    fun levelAt(x: Float): Int {
        val section = (x / (goalX / LEVELS_PER_STAGE)).toInt().coerceIn(0, LEVELS_PER_STAGE - 1)
        return (stage - 1) * LEVELS_PER_STAGE + section + 1
    }

    val levelInStage get() = levelAt(car.x) - (stage - 1) * LEVELS_PER_STAGE

    /** Zombies/obstacles per 100 m: 3 on level 1, +1 per level (arithmetic), capped. */
    fun perHundredForLevel(level: Int) = min(MAX_PER_HUNDRED, 3 + (level - 1))
    fun perHundredFor(s: Int) = perHundredForLevel((s - 1) * LEVELS_PER_STAGE + 1)
    private var lastSection = 0

    private fun populate() {
        val rnd = Random(stage * 1000 + save.day)
        val seg = 100f * METER
        var start = 1200f
        while (start < goalX - 500f) {
            val count = perHundredForLevel(levelAt(start))
            // dense levels are mostly zombies so the road stays drivable
            val obstacleShare = if (count > 10) 0.3f else 0.38f
            repeat(count) {
                val x = start + rnd.nextFloat() * seg
                if (x > goalX - (if (boss != null) 1300f else 400f)) return@repeat
                val roll = rnd.nextFloat()
                if (roll >= obstacleShare) {
                    val tRoll = rnd.nextFloat()
                    val type = when {
                        stage >= 2 && tRoll < 0.15f -> ZombieType.FAT
                        tRoll < 0.4f -> ZombieType.RUNNER
                        else -> ZombieType.WALKER
                    }
                    zombies.add(Zombie(type, x, terrain.heightAt(x)))
                    // zombies like company
                    if (rnd.nextFloat() < 0.35f) {
                        val x2 = x + 50f + rnd.nextFloat() * 60f
                        zombies.add(Zombie(ZombieType.WALKER, x2, terrain.heightAt(x2)))
                    }
                } else {
                    val oRoll = rnd.nextFloat()
                    when {
                        oRoll < 0.55f -> {
                            val cols = 1 + rnd.nextInt(if (stage >= 3) 3 else 2)
                            val maxRows = 1 + min(3, stage / 3)
                            for (col in 0 until cols) {
                                val cx = x + col * 64f
                                val rows = 1 + rnd.nextInt(maxRows)
                                val gy = terrain.heightAt(cx)
                                for (r in 0 until rows) obstacles.add(Obstacle(ObstacleType.CRATE, cx, gy - r * 62f))
                            }
                        }
                        oRoll < 0.8f || stage < 2 -> obstacles.add(Obstacle(ObstacleType.BARREL, x, terrain.heightAt(x)))
                        else -> obstacles.add(Obstacle(ObstacleType.WRECK, x, terrain.heightAt(x)))
                    }
                }
            }
            // pickups
            val coins = 2 + rnd.nextInt(3)
            val cx = start + rnd.nextFloat() * (seg - 300f)
            for (k in 0 until coins) {
                val x = cx + k * 70f
                items.add(Item(ItemType.COIN, x, terrain.heightAt(x) - 80f))
            }
            val pRoll = rnd.nextFloat()
            val special = when {
                pRoll < 0.26f -> ItemType.FUEL
                pRoll < 0.44f -> ItemType.AMMO
                pRoll < 0.58f -> ItemType.REPAIR
                pRoll < 0.67f -> ItemType.TIME
                pRoll < 0.76f -> ItemType.SHIELD
                pRoll < 0.84f && save.cloneUnlocked -> ItemType.CLONE
                else -> null
            }
            if (special != null) {
                val x = start + rnd.nextFloat() * seg
                if (x < goalX - 400f) items.add(Item(special, x, terrain.heightAt(x) - 90f))
            }
            // stocking up before the boss: the last stretch of a boss stage is rich in supplies
            if (boss != null && start > goalX * 0.72f) {
                val extra = listOf(ItemType.AMMO, ItemType.AMMO, if (rnd.nextBoolean()) ItemType.REPAIR else ItemType.FUEL,
                    if (rnd.nextFloat() < 0.35f) ItemType.SHIELD else ItemType.AMMO)
                for ((k, t) in extra.withIndex()) {
                    val x = start + seg * (0.15f + 0.22f * k) + rnd.nextFloat() * 120f
                    if (x < goalX - 1400f) items.add(Item(t, x, terrain.heightAt(x) - 90f))
                }
            }
            start += seg
        }
    }

    private fun endRun(reason: String) {
        if (state != State.PLAYING && state != State.PAUSED) return
        endReason = reason
        val m = maxDist.toInt()
        distMoney = (m * 0.4f).toInt()
        save.money += distMoney + runMoney
        if (m > save.best[stage]) {
            newRecord = save.best[stage] > 0
            save.best[stage] = m
        }
        finishStats(false)
        save.day++
        save.save()
        sfx.play(Sfx.S.LOSE, 0.8f)
        setState(State.RESULT)
    }

    private fun stageComplete() {
        val m = goalM.toInt()
        distMoney = (m * 0.4f).toInt()
        stageBonus = 200 * stage
        save.money += distMoney + runMoney + stageBonus
        save.best[stage] = max(save.best[stage], m)
        finishStats(true)
        save.bestStage = max(save.bestStage, stage)
        save.stageSel = min(STAGES, max(save.stageSel, stage + 1))
        save.day++
        save.save()
        sfx.play(Sfx.S.STAGE)
        setState(if (stage >= STAGES) State.VICTORY else State.STAGE_DONE)
    }

    /** Score and achievements (🏆) for the result screens. */
    private fun finishStats(finished: Boolean) {
        score = (maxDist * 10f).toInt() + kills * 100 + smashed * 50 + (if (finished) 5000 * stage else 0) +
            (if (bossKilled) 3000 * (stage / 5) else 0)
        if (score > save.bestScore) {
            newBestScore = save.bestScore > 0
            save.bestScore = score
        }
        achievements.clear()
        if (finished) achievements.add("🏁 Tới trạm cứu hộ")
        if (bossKilled) boss?.let { achievements.add("👹 Hạ ${it.name.lowercase().replaceFirstChar { ch -> ch.uppercase() }}") }
        if (finished && car.hp >= car.maxHp * 0.9f) achievements.add("✨ Không trầy xước")
        if (newRecord) achievements.add("📏 Kỷ lục quãng đường")
        if (newBestScore) achievements.add("⭐ Điểm cao nhất mới")
        if (kills >= 50) achievements.add("🧟 Đồ tể zombie") else if (kills >= 20) achievements.add("🧟 Sát thủ zombie")
        if (smashed >= 15) achievements.add("💥 Xe ủi")
        if (maxAir >= 2f) achievements.add("🦅 Phi công") else if (maxAir >= 1f) achievements.add("🛫 Bay cao")
        if (maxKmh >= 150f) achievements.add("⚡ Tốc độ bàn thờ")
        if (minStormGap < 8f && (finished || maxDist > 50f)) achievements.add("🌪 Thoát bão trong gang tấc")
    }

    fun pause() {
        if (state == State.PLAYING) setState(State.PAUSED)
        save.save()
    }

    fun back(): Boolean = when (state) {
        State.PLAYING -> {
            pause(); true
        }
        State.MENU -> false
        State.PAUSED -> {
            endRun("Bỏ cuộc"); true
        }
        else -> {
            goMenu(); true
        }
    }

    private fun goMenu() {
        save.save()
        boss = null
        bossRocks.clear()
        zombies.clear(); obstacles.clear(); debris.clear(); items.clear()
        shots.clear(); particles.clear(); texts.clear()
        loadStage(save.stageSel.coerceIn(1, maxStartStage()))
        setState(State.MENU)
    }

    // ------------------------------------------------------------- update

    fun update(dt: Float) {
        anim += dt
        stateTime += dt
        if (shake > 0f) shake -= dt
        if (hurtFlash > 0f) hurtFlash -= dt
        music.enabled = save.music
        when (state) {
            State.MENU, State.SHOP, State.GARAGE -> music.play(Music.Track.CHILL, 1f)
            State.PLAYING -> music.play(if (intense) Music.Track.INTENSE else Music.Track.CHILL, 1f)
            State.PAUSED -> music.play(if (intense) Music.Track.INTENSE else Music.Track.CHILL, 0.3f)
            State.RESULT -> music.play(Music.Track.CHILL, 0.6f)
            State.STAGE_DONE, State.VICTORY -> music.play(Music.Track.VICTORY, 1f)
        }
        music.update(dt)
        when (state) {
            State.PLAYING -> updatePlay(dt)
            State.PAUSED -> {}
            else -> {
                updateEffects(dt)
                updateDebris(dt)
            }
        }
    }

    private fun updatePlay(dt: Float) {
        val c = car
        if (dyingT > 0f) {
            // the wreck keeps tumbling and burning for a moment before the result screen
            dyingT -= dt
            c.boosting = false
            var left = dt
            while (left > 1e-5f) {
                val h = min(SUBSTEP, left)
                physics(h)
                left -= h
            }
            stormX += STORM_SPEED[phase] * dt * 0.3f
            burn(dt, true)
            updateDebris(dt)
            updateEffects(dt)
            updateCamera(dt)
            if (dyingT <= 0f) endRun(dyingReason)
            return
        }
        updateStorm(dt)

        // physics in fixed substeps
        var left = dt
        while (left > 1e-5f) {
            val h = min(SUBSTEP, left)
            physics(h)
            left -= h
        }
        c.wheelRot += c.forwardSpeed / Car.WHEEL_R * dt

        // fuel & boost
        if (gas && c.fuel > 0f) c.fuel -= dt else c.fuel -= 0.03f * dt
        if (c.fuel < 0f) c.fuel = 0f
        c.boosting = boostHeld && c.boost > 0f
        if (c.boosting) {
            c.boost = max(0f, c.boost - dt)
            if (!wasBoosting) sfx.play(Sfx.S.BOOST, 0.7f)
            val bx = c.x - c.fwdX * Car.HALF_LEN
            val by = c.y - c.fwdY * Car.HALF_LEN
            repeat(2) {
                particles.add(Particle(bx, by, -c.fwdX * 500f + rnd(-60f, 60f), -c.fwdY * 500f + rnd(-60f, 60f), 0.3f, 0.3f, rnd(6f, 12f), if (Random.nextBoolean()) 0xFFFFAB40.toInt() else 0xFFFFEB3B.toInt()))
            }
        }
        wasBoosting = c.boosting

        if (c.shieldT > 0f) c.shieldT -= dt
        if (c.cloneT > 0f) c.cloneT -= dt
        if (c.hurtT > 0f) c.hurtT -= dt
        if (stormHurt > 0f) stormHurt -= dt

        maxDist = max(maxDist, distanceM)
        val section = levelInStage - 1
        if (section > lastSection) {
            lastSection = section
            val bonus = 10 * stage * section
            runMoney += bonus
            texts.add(FloatText("MÀN ${section + 1}/$LEVELS_PER_STAGE  +$bonus$", c.x, c.y - 200f, 0xFF69F0AE.toInt()).also { it.life = 2f })
            sfx.play(Sfx.S.WIN, 0.8f)
        }

        updateZombies(dt)
        updateObstacles(dt)
        updateDebris(dt)
        updateItems()
        updateWeapon(dt)
        updateShots(dt)
        updateBoss(dt)
        updateEffects(dt)

        // run stats: air time (with a small stunt bonus), top speed, storm distance
        val kmh = abs(c.forwardSpeed) / METER * 3.6f
        maxKmh = max(maxKmh, kmh)
        val gapM = (c.x - Car.HALF_LEN - stormX) / METER
        if (stormX > 0f) minStormGap = min(minStormGap, gapM)
        if (!c.grounded[0] && !c.grounded[1]) {
            airT += dt
        } else {
            if (airT > 1.2f) {
                val bonus = (airT * 10f).toInt()
                runMoney += bonus
                texts.add(FloatText("BAY ${"%.1f".format(airT)}s  +$bonus$", c.x, c.y - 160f, 0xFF80D8FF.toInt()))
            }
            maxAir = max(maxAir, airT)
            airT = 0f
        }
        if (!intense && (distanceM > goalM * 0.5f || phase >= 1 || gapM < 40f || c.hp < c.maxHp * 0.25f)) intense = true

        // dust kicked up by the wheels
        if (abs(c.forwardSpeed) > 220f && Random.nextFloat() < dt * 30f) {
            for (i in 0..1) if (c.grounded[i]) {
                val wx = c.x + Car.WHEEL_X[i] * cos(c.angle)
                particles.add(Particle(wx - 20f, terrain.heightAt(wx) - 6f, -c.vx * 0.15f + rnd(-30f, 30f), rnd(-90f, -30f), 0.7f, 0.7f, rnd(8f, 14f), 0xFFD7B48A.toInt(), 0f, 1.6f))
            }
        }
        dmgTimer += dt
        if (pendingDmg > 0f && dmgTimer > 0.5f) flushDamage()
        burn(dt, false)

        // storm damage
        if (c.x - Car.HALF_LEN < stormX) {
            damageCar(80f * dt, instant = false, ignoreShield = true)
            stormHurt = 0.2f
            if (Random.nextFloat() < 0.5f) particles.add(Particle(c.x + rnd(-60f, 60f), c.y + rnd(-30f, 30f), 0f, -100f, 0.5f, 0.5f, 7f, 0xFFE040FB.toInt()))
            if (c.hp <= 0f && !tryRevive(true)) {
                startDying("Bị vòng bo tiêu diệt!"); return
            }
        }

        // flipped / out of fuel
        val speed = hypot(c.vx, c.vy)
        // upside down, or standing on its nose/tail and not moving
        val wheelsOff = !c.grounded[0] && !c.grounded[1]
        if ((cos(c.angle) < 0.3f || (wheelsOff && cos(c.angle) < 0.6f)) && speed < 120f) {
            c.flipT += dt
            if (c.flipT > 3f && !tryRevive(false)) {
                endRun("Xe bị lật!"); return
            }
        } else {
            c.flipT = 0f
        }
        if (c.fuel <= 0f && speed < 25f) {
            c.stuckT += dt
            if (c.stuckT > 2f) {
                endRun("Hết xăng!"); return
            }
        } else {
            c.stuckT = 0f
        }
        if (c.hp <= 0f && !tryRevive(false)) {
            startDying("Xe bị phá hủy!"); return
        }
        if (c.y > terrain.heightAt(c.x) + 400f && !tryRevive(false)) {
            endRun("Rơi khỏi đường!"); return
        }

        val b = boss
        if (b != null && b.alive && c.x > goalX - 140f) {
            // the station gate stays shut until the boss is down
            c.x = goalX - 140f
            if (c.vx > 0f) c.vx = -c.vx * 0.3f
        }
        if (c.x >= goalX) {
            stageComplete(); return
        }

        updateCamera(dt)
    }

    private fun updateCamera(dt: Float) {
        val c = car
        val lookAhead = (c.vx * 0.3f).coerceIn(-100f, 320f)
        val tx = c.x - viewW * 0.32f + lookAhead
        val ty = c.y - viewH * 0.58f
        camX += (tx - camX) * min(1f, dt * 5f)
        camY += (ty - camY) * min(1f, dt * 4f)
    }

    /** Applies damage to the car and shows the lost HP floating above it. */
    private fun damageCar(amount: Float, instant: Boolean = true, ignoreShield: Boolean = false) {
        val c = car
        if (amount <= 0f || dyingT > 0f || (c.shieldT > 0f && !ignoreShield)) return
        c.hp -= amount
        hurtFlash = max(hurtFlash, if (instant) 0.35f else 0.12f)
        pendingDmg += amount
        if (instant) flushDamage()
    }

    private fun flushDamage() {
        if (pendingDmg >= 1f) {
            texts.add(
                FloatText("-${pendingDmg.toInt()}", car.x + rnd(-40f, 40f), car.y - 100f, 0xFFFF1744.toInt())
                    .also { it.size = 58f; it.life = 1f },
            )
        }
        pendingDmg = 0f
        dmgTimer = 0f
    }

    /** Flames and smoke from the engine when HP is low (and from the wreck). */
    private fun burn(dt: Float, wreck: Boolean) {
        val c = car
        val critical = c.hp < c.maxHp * 0.15f
        if (!critical && !wreck) return
        val cs = cos(c.angle)
        val sn = sin(c.angle)
        val lx = if (wreck) rnd(-60f, 60f) else 55f
        val ly = -30f
        val fx = c.x + lx * cs - ly * sn
        val fy = c.y + lx * sn + ly * cs
        val rate = if (wreck) 3 else 2
        repeat(rate) {
            if (Random.nextFloat() < dt * 40f) {
                particles.add(Particle(fx + rnd(-10f, 10f), fy, rnd(-40f, 40f) - c.vx * 0.1f, rnd(-220f, -120f), 0.45f, 0.45f, rnd(7f, 14f), if (Random.nextBoolean()) 0xFFFF6D00.toInt() else 0xFFFFC400.toInt(), -150f))
            }
            if (Random.nextFloat() < dt * 25f) {
                particles.add(Particle(fx, fy - 10f, rnd(-30f, 30f) - c.vx * 0.2f, rnd(-120f, -70f), 1.3f, 1.3f, rnd(12f, 20f), 0xFF424242.toInt(), -40f, 1.8f))
            }
        }
    }

    /** Car is destroyed: big explosion, the wreck flies up, then the result screen. */
    private fun startDying(reason: String) {
        val c = car
        flushDamage()
        dyingT = 2f
        dyingReason = reason
        wrecked = true
        c.hp = 0f
        c.boosting = false
        pointers.clear()
        updateControls()
        explode(c.x, c.y - 10f, 0xFFFF6D00.toInt(), 80)
        explode(c.x + 40f, c.y - 30f, 0xFFFFEA00.toInt(), 40)
        repeat(7) {
            debris.add(Debris(Debris.METAL, c.x + rnd(-60f, 60f), c.y - 20f, c.vx * 0.5f + rnd(-350f, 350f), -rnd(350f, 800f), 0f, rnd(-14f, 14f), rnd(24f, 50f), rnd(10f, 20f), save.carType.color))
        }
        c.vy -= 520f
        c.vx *= 0.4f
        c.spin += rnd(-5f, 5f)
        shake = 0.9f
        hurtFlash = 0.6f
        texts.add(FloatText("BÙMMM!", c.x, c.y - 170f, 0xFFFFAB00.toInt()).also { it.size = 80f; it.life = 1.6f })
        sfx.play(Sfx.S.BOOM)
        sfx.play(Sfx.S.EXPLODE)
    }

    /** Clone pickup: one free revive while active. */
    private fun tryRevive(storm: Boolean): Boolean {
        val c = car
        if (c.cloneT <= 0f) return false
        c.cloneT = 0f
        c.hp = max(c.hp, c.maxHp * 0.4f)
        c.angle = 0f
        c.spin = 0f
        c.flipT = 0f
        if (storm || c.x < stormX + 300f) c.x = max(c.x, stormX + 700f)
        c.y = terrain.heightAt(c.x) - Car.WHEEL_Y - Car.WHEEL_R - 30f
        c.vx = max(c.vx, 300f)
        c.vy = 0f
        burst(c.x, c.y, ItemType.CLONE.color, 24)
        texts.add(FloatText("BẢN SAO HỒI SINH!", c.x, c.y - 120f, 0xFF64FFDA.toInt()))
        sfx.play(Sfx.S.CLONE)
        return true
    }

    private fun updateStorm(dt: Float) {
        if (phase < PHASE_TIME.size) {
            phaseLeft -= dt
            if (phase == 2) {
                val sec = ceil(phaseLeft).toInt()
                if (sec != lastBeep && sec > 0) {
                    lastBeep = sec
                    sfx.play(Sfx.S.BEEP)
                }
            }
            if (phaseLeft <= 0f) {
                phase++
                if (phase < PHASE_TIME.size) {
                    phaseLeft += PHASE_TIME[phase]
                    texts.add(FloatText("BO ${phase + 1}: BÃO TĂNG TỐC!", car.x, car.y - 180f, 0xFFE040FB.toInt()))
                } else {
                    phaseLeft = 0f
                    texts.add(FloatText("VÒNG BO ĐÃ KHÉP!", car.x, car.y - 180f, 0xFFE040FB.toInt()))
                }
                sfx.play(Sfx.S.STORM)
                shake = 0.3f
            }
        }
        stormX += STORM_SPEED[phase] * dt
    }

    // ------------------------------------------------------------ physics

    private fun physics(h: Float) {
        val c = car
        val landed = phys.step(
            terrain, c, h,
            gas = gas && c.fuel > 0f && dyingT <= 0f, brake = brake && dyingT <= 0f,
            lean = if (dyingT > 0f) 0f else (if (leanFwd) 1f else 0f) - (if (leanBack) 1f else 0f),
            topSpeed = topSpeed, engineAcc = engineAcc, mu = mu,
        )
        if (landed) {
            sfx.play(Sfx.S.LAND, 0.6f)
            shake = max(shake, 0.12f)
        }
    }

    // ----------------------------------------------------------- entities

    private fun toLocal(px: Float, py: Float, out: FloatArray) {
        val dx = px - car.x
        val dy = py - car.y
        val cs = cos(car.angle)
        val sn = sin(car.angle)
        out[0] = dx * cs + dy * sn
        out[1] = -dx * sn + dy * cs
    }

    private val loc = FloatArray(2)

    private fun updateZombies(dt: Float) {
        val c = car
        val speed = abs(c.forwardSpeed)
        for (z in zombies) {
            if (!z.alive) continue
            val dx = c.x - z.x
            if (dx > 1600f || dx < -1600f) continue
            z.phase += dt * (if (z.type == ZombieType.RUNNER) 9f else 4f)
            if (abs(dx) < 1300f) {
                z.x += sign(dx) * z.type.speed * dt
            }
            z.y = terrain.heightAt(z.x)
            toLocal(z.x, z.y - z.type.h / 2, loc)
            if (abs(loc[0]) < Car.HALF_LEN + z.type.w / 2 && abs(loc[1]) < Car.HALF_H + 14f + z.type.h / 2) {
                if (speed > 120f || c.shieldT > 0f) {
                    smashZombie(z, c.vx * 1.1f + rnd(-80f, 80f), -250f - abs(c.vx) * 0.35f - rnd(0f, 200f))
                    val keep = 1f - z.type.mass * 0.7f * (1f - armor * 0.5f)
                    c.vx *= keep
                    c.vy *= keep
                    damageCar(z.type.mass * 35f * (1f - armor))
                } else {
                    // slow contact: zombie claws at the car
                    val side = sign(z.x - c.x)
                    z.x = c.x + side * (Car.HALF_LEN + z.type.w / 2 + 1f)
                    damageCar(10f * (1f - armor) * dt, instant = false)
                    if (Random.nextFloat() < dt * 3f) bleed(z.x, z.y - z.type.h * 0.6f, 3)
                }
            }
        }
        zombies.removeAll { !it.alive }
    }

    private fun smashZombie(z: Zombie, vx: Float, vy: Float) {
        z.alive = false
        kills++
        val kind = if (z.type == ZombieType.FAT) Debris.FAT_ZOMBIE else Debris.ZOMBIE
        debris.add(Debris(kind, z.x, z.y - z.type.h / 2, vx, vy, 0f, rnd(-12f, 12f), z.type.w, z.type.h, z.shirt))
        bleed(z.x, z.y - z.type.h / 2, 14)
        addMoney(z.type.reward, z.x, z.y - z.type.h - 20f)
        sfx.play(Sfx.S.SPLAT, 0.8f)
    }

    private fun updateObstacles(dt: Float) {
        val c = car
        val fs = c.forwardSpeed
        for (o in obstacles) {
            if (!o.alive) continue
            if (o.flash > 0f) o.flash -= dt
            if (o.ramCd > 0f) o.ramCd -= dt
            if (o.barT > 0f) o.barT -= dt
            val dx = o.x - c.x
            if (o.type == ObstacleType.CRATE && dx < 1500f && dx > -800f) settleCrate(o)
            if (dx > 400f || dx < -400f) continue
            toLocal(o.x, o.y - o.type.h / 2, loc)
            val ox = Car.HALF_LEN + o.type.w / 2
            val oy = Car.HALF_H + 12f + o.type.h / 2
            if (abs(loc[0]) >= ox || abs(loc[1]) >= oy) continue
            when {
                o.type == ObstacleType.BARREL -> barrelBlast(o)
                abs(fs) > o.type.breakSpeed || (c.shieldT > 0f && o.type != ObstacleType.WRECK) ||
                    (c.shieldT > 0f && abs(fs) > 300f) -> {
                    breakObstacle(o, c.vx)
                    val keep = 1f - o.type.mass * 0.6f * (1f - armor * 0.5f)
                    c.vx *= keep
                    c.vy *= keep
                    if (c.shieldT <= 0f) {
                        damageCar(o.type.damage * (1f - armor))
                        shake = max(shake, 0.15f)
                    }
                }
                else -> {
                    // too slow to smash through: the car bounces off
                    val dir = sign(loc[0]).let { if (it == 0f) 1f else it }
                    val shift = ox - abs(loc[0])
                    c.x -= c.fwdX * shift * dir
                    c.y -= c.fwdY * shift * dir
                    val into = fs * dir
                    if (into > 0f) {
                        c.vx -= c.fwdX * fs * 1.25f
                        c.vy -= c.fwdY * fs * 1.25f
                        if (into > 60f) o.barT = 4f
                        if (into > Obstacle.RAM_SPEED && o.ramCd <= 0f) {
                            // a solid ram wears the obstacle down by 25%; four rams break it
                            o.ramCd = 0.35f
                            o.ramHp -= Obstacle.RAM_HIT
                            o.flash = 0.12f
                            damageCar(into * 0.012f * (1f - armor))
                            shake = max(shake, 0.2f)
                            sfx.play(Sfx.S.LAND, 0.7f)
                            sfx.play(if (o.type == ObstacleType.CRATE) Sfx.S.WOOD else Sfx.S.HIT, 0.6f)
                            sparks(o.x - dir * o.type.w / 2, o.y - o.type.h / 2)
                            if (o.ramHp <= 0.001f) {
                                breakObstacle(o, c.vx + c.fwdX * dir * 300f)
                                texts.add(FloatText("PHÁ TAN!", o.x, o.y - o.type.h - 40f, 0xFFFFAB00.toInt()).also { it.size = 56f })
                            } else {
                                texts.add(FloatText("-25%", o.x, o.y - o.type.h - 40f, 0xFFFFD740.toInt()).also { it.size = 46f })
                            }
                        }
                    }
                }
            }
        }
        obstacles.removeAll { !it.alive }
    }

    /** Crates stacked on a crate that was smashed fall down. */
    private fun settleCrate(o: Obstacle) {
        val ground = terrain.heightAt(o.x)
        var floor = ground
        for (b in obstacles) {
            if (b === o || !b.alive || abs(b.x - o.x) > o.type.w * 0.8f) continue
            val top = b.y - b.type.h
            if (top >= o.y - 2f && top < floor) floor = top
        }
        if (o.y < floor - 1f) {
            o.vy += G * 0.016f
            o.y = min(floor, o.y + o.vy * 0.016f)
        } else {
            o.y = floor
            o.vy = 0f
        }
    }

    private fun breakObstacle(o: Obstacle, vx: Float) {
        o.alive = false
        smashed++
        addMoney(o.type.reward, o.x, o.y - o.type.h - 10f)
        val cy = o.y - o.type.h / 2
        if (o.type == ObstacleType.WRECK) {
            repeat(6) {
                debris.add(Debris(Debris.METAL, o.x + rnd(-60f, 60f), cy, vx * 0.8f + rnd(-150f, 250f), -rnd(200f, 600f), 0f, rnd(-10f, 10f), rnd(30f, 60f), rnd(12f, 24f), 0xFF8D6E63.toInt()))
            }
            explode(o.x, cy, 0xFF795548.toInt(), 20)
            sfx.play(Sfx.S.EXPLODE, 0.8f)
        } else {
            repeat(5) {
                debris.add(Debris(Debris.PLANK, o.x + rnd(-20f, 20f), cy + rnd(-20f, 20f), vx * 0.9f + rnd(-120f, 220f), -rnd(150f, 500f), 0f, rnd(-14f, 14f), rnd(30f, 56f), 10f, 0xFFA1887F.toInt()))
            }
            sfx.play(Sfx.S.WOOD, 0.8f)
        }
    }

    private fun barrelBlast(o: Obstacle) {
        o.alive = false
        smashed++
        val cx = o.x
        val cy = o.y - o.type.h / 2
        explode(cx, cy, 0xFFFF5722.toInt(), 40)
        sfx.play(Sfx.S.BOOM)
        shake = 0.35f
        blastArea(cx, cy, 220f, 5)
        val c = car
        val d = hypot(c.x - cx, c.y - cy)
        if (d < 260f) {
            damageCar(o.type.damage * (1f - armor))
            c.vy -= 380f
            c.spin += rnd(-1.5f, 1.5f)
        }
    }

    /** Damages / kills everything in a radius (barrels, catapult). */
    private fun blastArea(cx: Float, cy: Float, r: Float, dmg: Int) {
        boss?.let { bb ->
            if (bb.alive && abs(bb.x - cx) < r + bb.w / 2 && cy > bb.y - bb.h - r) hurtBoss(bb, dmg * 2f)
        }
        for (z in zombies) {
            if (!z.alive) continue
            val d = hypot(z.x - cx, z.y - z.type.h / 2 - cy)
            if (d < r) {
                z.hp -= dmg
                if (z.hp <= 0) smashZombie(z, (z.x - cx) * 3f, -400f - rnd(0f, 200f))
            }
        }
        for (o in obstacles) {
            if (!o.alive) continue
            val d = hypot(o.x - cx, o.y - o.type.h / 2 - cy)
            if (d < r + o.type.w * 0.3f) {
                if (o.type == ObstacleType.BARREL) {
                    o.hp = 0
                } else {
                    o.hp -= dmg
                    o.flash = 0.1f
                    if (o.hp <= 0) breakObstacle(o, (o.x - cx) * 2f)
                }
            }
        }
        // chain reaction
        val chain = obstacles.filter { it.alive && it.type == ObstacleType.BARREL && it.hp <= 0 }
        for (b in chain) barrelBlast(b)
    }

    private fun updateBoss(dt: Float) {
        updateBossRocks(dt)
        val b = boss ?: return
        if (!b.alive) return
        val c = car
        b.anim += dt
        if (b.flash > 0f) b.flash -= dt
        if (b.throwAnim > 0f) b.throwAnim -= dt
        if (b.slamAnim > 0f) b.slamAnim -= dt
        if (b.ramCd > 0f) b.ramCd -= dt
        b.dmgT += dt
        if (b.pendingDmg > 0f && b.dmgT > 0.5f) flushBossDamage(b)
        b.y = terrain.heightAt(b.x)
        val dx = c.x - b.x
        if (!b.engaged) {
            if (dx > -1500f) {
                b.engaged = true
                intense = true
                shake = 0.5f
                sfx.play(Sfx.S.ROAR)
            }
            return
        }
        val dir = if (dx >= 0f) 1f else -1f
        // lumber towards the car, but guard the station
        if (abs(dx) > 330f) b.x += dir * b.speed * dt
        b.x = b.x.coerceIn(goalX - 1150f, goalX - 260f)

        // boulder throw
        b.throwCd -= dt
        if (b.throwCd <= 0f && abs(dx) in 280f..1400f) {
            b.throwCd = max(1.4f, 3f - 0.35f * b.tier) * (0.8f + Random.nextFloat() * 0.4f)
            b.throwAnim = 0.45f
            val x0 = b.x + dir * b.w * 0.3f
            val y0 = b.y - b.h * 1.02f
            val t = (abs(dx) / 750f).coerceIn(0.6f, 1.5f)
            val tx = c.x + c.vx * t * 0.6f
            val ty = c.y - 10f
            bossRocks.add(BossRock(x0, y0, (tx - x0) / t, (ty - y0) / t - 0.5f * G * t))
            sfx.play(Sfx.S.CATAPULT, 0.9f)
        }
        // ground slam when the car gets close
        b.slamCd -= dt
        if (b.slamCd <= 0f && abs(dx) < 300f) {
            b.slamCd = max(1.6f, 2.8f - 0.25f * b.tier)
            b.slamAnim = 0.5f
            shake = max(shake, 0.45f)
            sfx.play(Sfx.S.BOOM, 0.9f)
            repeat(26) {
                val a = Random.nextFloat() * 3.1416f
                particles.add(Particle(b.x + rnd(-80f, 80f), b.y - 5f, cos(a) * rnd(150f, 450f), -sin(a) * rnd(100f, 320f), 0.7f, 0.7f, rnd(8f, 16f), 0xFFC9A27E.toInt(), 300f, 1.2f))
            }
            if (c.grounded[0] || c.grounded[1] || c.y > terrain.heightAt(c.x) - 160f) {
                damageCar((10f + 4f * b.tier) * (1f - armor))
                c.vx = dir * (420f + 60f * b.tier)
                c.vy = -420f
                c.spin += dir * 1.5f
            }
        }
        // body contact: the car is stopped; a hard ram hurts the boss
        val ox = Car.HALF_LEN + b.w / 2
        if (abs(dx) < ox && c.y > b.y - b.h - 30f) {
            val toward = -dir // direction from car to boss
            val fs = c.forwardSpeed
            val into = fs * toward * sign(c.fwdX)
            c.x = b.x + dir * ox
            if (into > 0f) {
                if (into > Obstacle.RAM_SPEED && b.ramCd <= 0f) {
                    b.ramCd = 0.45f
                    val dmg = into / 35f * (if (c.boosting) 1.5f else 1f)
                    hurtBoss(b, dmg)
                    damageCar((5f + 2f * b.tier) * (1f - armor))
                    shake = max(shake, 0.3f)
                    sfx.play(Sfx.S.HIT, 0.9f)
                    bleed(b.x - dir * b.w / 2, b.y - b.h * 0.5f, 10)
                }
                c.vx -= c.fwdX * fs * 1.3f
                c.vy -= c.fwdY * fs * 1.3f
            }
        }
    }

    private fun hurtBoss(b: Boss, dmg: Float) {
        if (!b.alive || dmg <= 0f) return
        b.hp -= dmg
        b.flash = 0.12f
        b.pendingDmg += dmg
        if (b.pendingDmg >= 5f || b.hp <= 0f) flushBossDamage(b)
        if (b.hp <= 0f) {
            b.hp = 0f
            b.alive = false
            bossKilled = true
            kills++
            explode(b.x, b.y - b.h * 0.5f, 0xFF7CB342.toInt(), 90)
            explode(b.x, b.y - b.h * 0.8f, 0xFFFF6D00.toInt(), 60)
            bleed(b.x, b.y - b.h * 0.5f, 40)
            debris.add(Debris(Debris.FAT_ZOMBIE, b.x, b.y - b.h / 2, car.vx * 0.3f + 250f, -650f, 0f, 3f, b.w, b.h, 0xFF4E342E.toInt()).also { it.life = 8f })
            addMoney(300 * b.tier, b.x, b.y - b.h - 60f)
            texts.add(FloatText("${b.name} ĐÃ GỤC!", b.x, b.y - b.h * 0.55f, 0xFFFFD740.toInt()).also { it.size = 60f; it.life = 2.5f })
            shake = 1f
            sfx.play(Sfx.S.ROAR, 0.7f)
            sfx.play(Sfx.S.BOOM)
        }
    }

    private fun flushBossDamage(b: Boss) {
        if (b.pendingDmg >= 1f) {
            texts.add(FloatText("-${b.pendingDmg.toInt()}", b.x + rnd(-40f, 40f), b.y - b.h - 30f, 0xFFFFEA00.toInt()).also { it.size = 46f })
        }
        b.pendingDmg = 0f
        b.dmgT = 0f
    }

    private fun updateBossRocks(dt: Float) {
        val c = car
        for (r in bossRocks) {
            if (!r.alive) continue
            r.vy += G * dt
            r.x += r.vx * dt
            r.y += r.vy * dt
            r.rot += dt * 6f
            if (hypot(r.x - c.x, r.y - c.y) < 75f) {
                r.alive = false
                damageCar((14f + 3f * (boss?.tier ?: 1)) * (1f - armor))
                c.vx -= 200f
                c.vy -= 150f
                explode(r.x, r.y, 0xFF795548.toInt(), 24)
                shake = max(shake, 0.35f)
                sfx.play(Sfx.S.EXPLODE, 0.8f)
            } else if (r.y > terrain.heightAt(r.x)) {
                r.alive = false
                explode(r.x, r.y, 0xFF8D6E63.toInt(), 14)
                sfx.play(Sfx.S.LAND, 0.6f)
            }
        }
        bossRocks.removeAll { !it.alive }
    }

    private fun updateDebris(dt: Float) {
        for (d in debris) {
            d.life -= dt
            if (d.grounded) {
                d.vx *= (1f - 3f * dt)
                d.x += d.vx * dt
                d.y = terrain.heightAt(d.x) - d.h * 0.25f
                continue
            }
            d.vy += G * dt
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.rot += d.spin * dt
            val gy = terrain.heightAt(d.x)
            if (d.y + d.h * 0.25f > gy) {
                d.y = gy - d.h * 0.25f
                d.vy = -d.vy * 0.3f
                d.vx *= 0.6f
                d.spin *= 0.5f
                if (abs(d.vy) < 80f) {
                    d.grounded = true
                    if (d.kind == Debris.ZOMBIE || d.kind == Debris.FAT_ZOMBIE) d.rot = if (d.rot % 6.28f > 0f) 1.57f else -1.57f
                }
            }
        }
        debris.removeAll { it.life <= 0f || it.x < camX - 1500f }
    }

    private fun updateItems() {
        val c = car
        for (it in items) {
            if (!it.alive) continue
            if (abs(it.x - c.x) > 200f) continue
            if (hypot(it.x - c.x, it.y - c.y) < 95f) {
                it.alive = false
                collect(it)
            }
        }
        items.removeAll { !it.alive || it.x < camX - 600f }
    }

    private fun collect(it: Item) {
        val c = car
        burst(it.x, it.y, it.type.color, 12)
        when (it.type) {
            ItemType.COIN -> {
                addMoney(5 + stage, it.x, it.y)
                sfx.play(Sfx.S.COIN, 0.7f)
                return
            }
            ItemType.FUEL -> {
                c.fuel = min(c.maxFuel, c.fuel + c.maxFuel * 0.25f)
                text("+XĂNG", it, 0xFFFF8A80.toInt())
            }
            ItemType.REPAIR -> {
                c.hp = min(c.maxHp, c.hp + c.maxHp * 0.3f)
                text("+MÁU", it, 0xFF69F0AE.toInt())
            }
            ItemType.SHIELD -> {
                c.shieldT = 6f
                text("LÁ CHẮN!", it, 0xFF82B1FF.toInt())
            }
            ItemType.AMMO -> {
                c.ammo += 15
                text("+15 ĐẠN", it, 0xFFFFCC80.toInt())
            }
            ItemType.TIME -> {
                if (phase < PHASE_TIME.size) phaseLeft += 5f
                stormX -= 400f
                text("+5 GIÂY, BÃO LÙI!", it, 0xFFEA80FC.toInt())
            }
            ItemType.CLONE -> {
                c.cloneT = 10f
                text("NHÂN BẢN: x2 MẠNG!", it, 0xFF64FFDA.toInt())
                sfx.play(Sfx.S.CLONE)
                return
            }
        }
        sfx.play(Sfx.S.POWER, 0.8f)
    }

    private fun text(s: String, it: Item, color: Int) = texts.add(FloatText(s, it.x, it.y - 40f, color))

    // -------------------------------------------------------------- weapon

    private fun updateWeapon(dt: Float) {
        val c = car
        c.fireCd -= dt
        // gun origin on the roof
        val cs = cos(c.angle)
        val sn = sin(c.angle)
        val ox = c.x + (-10f) * cs - (-Car.HALF_H - 22f) * sn
        val oy = c.y + (-10f) * sn + (-Car.HALF_H - 22f) * cs
        var tx = 0f
        var ty = 0f
        var best = Float.MAX_VALUE
        for (z in zombies) {
            val dx = z.x - c.x
            if (dx < 60f || dx > 950f || dx > best) continue
            best = dx; tx = z.x; ty = z.y - z.type.h * 0.6f
        }
        boss?.let { bb ->
            val dx = bb.x - c.x
            if (bb.alive && bb.engaged && dx > 60f && dx < 1100f && dx < best) {
                best = dx; tx = bb.x; ty = bb.y - bb.h * 0.6f
            }
        }
        if (best == Float.MAX_VALUE) {
            for (o in obstacles) {
                if (o.type == ObstacleType.WRECK && weapon == WeaponType.GUN) continue
                val dx = o.x - c.x
                if (dx < 100f || dx > 800f || dx > best) continue
                best = dx; tx = o.x; ty = o.y - o.type.h / 2
            }
        }
        val targetAim = if (best == Float.MAX_VALUE) c.angle else atan2(ty - oy, tx - ox)
        c.aim += (targetAim - c.aim) * min(1f, dt * 12f)
        if (best == Float.MAX_VALUE || c.ammo <= 0 || c.fireCd > 0f) return
        c.fireCd = weapon.interval
        c.ammo--
        val dx = tx - ox
        val dy = ty - oy
        val d = max(1f, hypot(dx, dy))
        when (weapon) {
            WeaponType.GUN -> {
                shots.add(Projectile(weapon, ox, oy, dx / d * 2000f + c.vx, dy / d * 2000f + c.vy))
                sfx.play(Sfx.S.SHOOT, 0.3f)
            }
            WeaponType.CROSSBOW -> {
                shots.add(Projectile(weapon, ox, oy, dx / d * 1600f + c.vx, dy / d * 1600f + c.vy))
                sfx.play(Sfx.S.BOW, 0.6f)
            }
            WeaponType.CATAPULT -> {
                val t = (d / 900f).coerceIn(0.35f, 1.1f)
                shots.add(Projectile(weapon, ox, oy, dx / t, dy / t - 0.5f * G * t).also { it.life = 3f })
                sfx.play(Sfx.S.CATAPULT, 0.7f)
            }
        }
    }

    private fun updateShots(dt: Float) {
        for (s in shots) {
            if (!s.alive) continue
            s.life -= dt
            if (s.kind == WeaponType.CATAPULT) s.vy += G * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            if (s.life <= 0f) {
                s.alive = false; continue
            }
            if (s.y > terrain.heightAt(s.x)) {
                s.alive = false
                if (s.kind == WeaponType.CATAPULT) catapultBlast(s) else sparks(s.x, s.y)
                continue
            }
            val bb = boss
            if (bb != null && bb.alive && abs(s.x - bb.x) < bb.w / 2 && s.y > bb.y - bb.h && s.y < bb.y) {
                s.alive = false
                if (s.kind == WeaponType.CATAPULT) {
                    catapultBlast(s)
                } else {
                    hurtBoss(bb, s.damage.toFloat())
                    bleed(s.x, s.y, 3)
                }
                continue
            }
            if (s.kind == WeaponType.CATAPULT) {
                // explode on direct contact too
                val hitZ = zombies.any { it.alive && abs(it.x - s.x) < it.type.w && s.y > it.y - it.type.h }
                val hitO = obstacles.any { it.alive && abs(it.x - s.x) < it.type.w / 2 && s.y > it.y - it.type.h }
                if (hitZ || hitO) {
                    s.alive = false
                    catapultBlast(s)
                }
                continue
            }
            for (z in zombies) {
                if (!z.alive || z in s.hit) continue
                if (abs(z.x - s.x) < z.type.w / 2 + 6f && s.y > z.y - z.type.h && s.y < z.y) {
                    s.hit.add(z)
                    z.hp -= s.damage
                    bleed(s.x, s.y, 4)
                    if (z.hp <= 0) smashZombie(z, s.vx * 0.12f, -220f)
                    if (--s.pierce <= 0) {
                        s.alive = false; break
                    }
                }
            }
            if (!s.alive) continue
            for (o in obstacles) {
                if (!o.alive || o in s.hit) continue
                if (abs(o.x - s.x) < o.type.w / 2 && s.y > o.y - o.type.h && s.y < o.y) {
                    s.hit.add(o)
                    if (o.type == ObstacleType.BARREL) {
                        barrelBlast(o)
                    } else {
                        o.hp -= s.damage
                        o.flash = 0.08f
                        o.barT = 3f
                        sparks(s.x, s.y)
                        if (o.hp <= 0) breakObstacle(o, s.vx * 0.2f)
                    }
                    if (--s.pierce <= 0) {
                        s.alive = false; break
                    }
                }
            }
        }
        shots.removeAll { !it.alive }
    }

    private fun catapultBlast(s: Projectile) {
        explode(s.x, s.y, 0xFF8D6E63.toInt(), 30)
        sfx.play(Sfx.S.BOOM, 0.7f)
        shake = max(shake, 0.15f)
        blastArea(s.x, s.y - 30f, 160f, s.damage)
    }

    // ------------------------------------------------------------- effects

    private fun updateEffects(dt: Float) {
        val pi = particles.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.life -= dt
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life <= 0f) pi.remove()
        }
        val ti = texts.iterator()
        while (ti.hasNext()) {
            val t = ti.next()
            t.life -= dt
            t.y -= 60f * dt
            if (t.life <= 0f) ti.remove()
        }
    }

    private fun addMoney(n: Int, x: Float, y: Float) {
        runMoney += n
        texts.add(FloatText("+$n$", x, y, 0xFFFFD740.toInt()))
    }

    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    private fun bleed(x: Float, y: Float, n: Int) {
        repeat(n) {
            particles.add(Particle(x, y, rnd(-220f, 220f), rnd(-380f, 40f), 0.7f, 0.7f, rnd(4f, 8f), if (Random.nextBoolean()) 0xFF8E0000.toInt() else 0xFF5D7A1F.toInt(), G))
        }
    }

    private fun explode(x: Float, y: Float, color: Int, n: Int) {
        val colors = intArrayOf(color, 0xFFFFC107.toInt(), 0xFFFF5722.toInt(), 0xFF424242.toInt())
        repeat(n) {
            val a = Random.nextFloat() * 6.2832f
            val s = rnd(80f, 480f)
            val life = rnd(0.4f, 0.9f)
            particles.add(Particle(x, y, cos(a) * s, sin(a) * s, life, life, rnd(6f, 16f), colors.random()))
        }
    }

    private fun sparks(x: Float, y: Float) {
        repeat(4) {
            val a = Random.nextFloat() * 6.2832f
            particles.add(Particle(x, y, cos(a) * 200f, sin(a) * 200f, 0.25f, 0.25f, 4f, 0xFFFFF59D.toInt()))
        }
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int) {
        repeat(n) {
            val a = it.toFloat() / n * 6.2832f
            particles.add(Particle(x, y, cos(a) * 260f, sin(a) * 260f, 0.4f, 0.4f, 6f, color))
        }
    }

    // --------------------------------------------------------------- input

    /** On-screen driving controls (screen coordinates). */
    fun controls(): List<Button> = listOf(
        Button("brake", "PHANH", 24f, H - 360f, 184f, H - 210f, 0x55FF5252),
        Button("leanBack", "↺", 24f, H - 190f, 184f, H - 30f, 0x55FFFFFF),
        Button("leanFwd", "↻", 204f, H - 190f, 364f, H - 30f, 0x55FFFFFF),
        Button("boost", "🚀", W - 384f, H - 190f, W - 224f, H - 30f, 0x55FF9800),
        Button("gas", "GA", W - 204f, H - 260f, W - 24f, H - 30f, 0x5543A047),
    )

    val pauseBtn get() = Button("pause", "II", W - 96f, 16f, W - 20f, 92f, 0x55FFFFFF)

    fun buttons(): List<Button> {
        val green = 0xFF43A047.toInt()
        val grey = 0xFF546E7A.toInt()
        val blue = 0xFF1E88E5.toInt()
        val purple = 0xFF8E24AA.toInt()
        val cx = W * 0.74f
        fun b(id: String, label: String, x: Float, y: Float, w: Float, color: Int, enabled: Boolean = true) =
            Button(id, label, x - w / 2, y, x + w / 2, y + 86f, color, enabled)
        return when (state) {
            State.MENU -> listOf(
                Button("stPrev", "◀", cx - 230f, 130f, cx - 140f, 216f, grey, save.stageSel > 1),
                Button("stNext", "▶", cx + 140f, 130f, cx + 230f, 216f, grey, save.stageSel < maxStartStage()),
                b("play", "CHẠY!", cx, 250f, 460f, green),
                b("shop", "NÂNG CẤP", cx, 356f, 460f, blue),
                b("garage", if (save.garageUnlocked) "GARA" else "GARA 🔒 (qua ải 1)", cx, 462f, 460f, purple, save.garageUnlocked),
                Button("sound", if (save.sound) "🔊" else "🔇", W - 100f, 20f, W - 24f, 96f, 0x66000000),
                Button("music", "🎵", W - 190f, 20f, W - 114f, 96f, if (save.music) 0x66000000 else 0x66B71C1C),
            )
            State.SHOP, State.GARAGE -> listOf(
                b("menu", "MENU", W / 2 - 250f, H - 100f, 300f, grey),
                b("play", "CHẠY!", W / 2 + 250f, H - 100f, 300f, green),
            )
            State.PAUSED -> listOf(
                b("resume", "TIẾP TỤC", W / 2, H * 0.36f, 420f, green),
                b("quit", "BỎ CUỘC", W / 2, H * 0.36f + 110f, 420f, grey),
            )
            State.RESULT -> {
                val l = ArrayList<Button>()
                val ids = mutableListOf("menu" to "MENU", "shop" to "NÂNG CẤP")
                if (save.garageUnlocked) ids.add("garage" to "GARA")
                ids.add("play" to "CHẠY TIẾP")
                val w = 260f
                val gap = 24f
                var x = W / 2 - (ids.size * w + (ids.size - 1) * gap) / 2 + w / 2
                for ((id, label) in ids) {
                    l.add(b(id, label, x, H - 120f, w, if (id == "play") green else if (id == "menu") grey else blue))
                    x += w + gap
                }
                l
            }
            State.STAGE_DONE -> listOf(
                b("menu", "MENU", W / 2 - 300f, H - 120f, 260f, grey),
                b("shop", "NÂNG CẤP", W / 2, H - 120f, 260f, blue),
                b("play", "ẢI TIẾP", W / 2 + 300f, H - 120f, 260f, green),
            )
            State.VICTORY -> listOf(b("menu", "MENU", W / 2, H - 130f, 360f, green))
            State.PLAYING -> emptyList()
        }
    }

    /** Upgrade cards: 3 x 2 grid. */
    fun upgradeCard(i: Int): FloatArray {
        val cw = (W - 120f) / 3f
        val col = i % 3
        val row = i / 3
        val l = 40f + col * (cw + 20f)
        val t = 120f + row * 235f
        return floatArrayOf(l, t, l + cw, t + 215f)
    }

    fun carCard(i: Int): FloatArray {
        val cw = (W - 130f) / 4f
        val l = 40f + i * (cw + 16f)
        return floatArrayOf(l, 110f, l + cw, 360f)
    }

    fun weaponCard(i: Int): FloatArray {
        val cw = (W - 120f) / 3f
        val l = 40f + i * (cw + 20f)
        return floatArrayOf(l, 400f, l + cw, 600f)
    }

    private fun inRect(r: FloatArray, x: Float, y: Float) = x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]

    fun touchDown(id: Int, x: Float, y: Float) {
        if (state == State.PLAYING) {
            if (dyingT > 0f) return
            if (pauseBtn.contains(x, y)) {
                sfx.play(Sfx.S.CLICK)
                pause()
                return
            }
            pointers[id] = controlAt(x, y)
            updateControls()
            return
        }
        if (stateTime < 0.4f) return
        if (state == State.SHOP) {
            for (i in Upgrade.entries.indices) if (inRect(upgradeCard(i), x, y)) {
                buyUpgrade(Upgrade.entries[i]); return
            }
        }
        if (state == State.GARAGE) {
            for (i in CarType.entries.indices) if (inRect(carCard(i), x, y)) {
                pickCar(i); return
            }
            for (i in WeaponType.entries.indices) if (inRect(weaponCard(i), x, y)) {
                pickWeapon(i); return
            }
        }
        val btn = buttons().firstOrNull { it.contains(x, y) } ?: return
        if (!btn.enabled) return
        sfx.play(Sfx.S.CLICK)
        when (btn.id) {
            "play" -> startRun()
            "shop" -> setState(State.SHOP)
            "garage" -> setState(State.GARAGE)
            "menu" -> goMenu()
            "resume" -> setState(State.PLAYING)
            "quit" -> endRun("Bỏ cuộc")
            "stPrev" -> {
                save.stageSel = max(1, save.stageSel - 1); loadStage(save.stageSel)
            }
            "stNext" -> {
                save.stageSel = min(maxStartStage(), save.stageSel + 1); loadStage(save.stageSel)
            }
            "sound" -> {
                save.sound = !save.sound
                sfx.enabled = save.sound
                save.save()
            }
            "music" -> {
                save.music = !save.music
                save.save()
            }
        }
    }

    private fun controlAt(x: Float, y: Float): String {
        controls().firstOrNull { it.contains(x, y) }?.let { return it.id }
        // anywhere else: right half accelerates, left half brakes
        return if (x > W / 2) "gas" else "none"
    }

    fun touchMove(id: Int, x: Float, y: Float) {
        if (state != State.PLAYING || !pointers.containsKey(id)) return
        val c = controlAt(x, y)
        if (c != pointers[id]) {
            pointers[id] = c
            updateControls()
        }
    }

    fun touchUp(id: Int) {
        pointers.remove(id)
        updateControls()
    }

    private fun updateControls() {
        val v = pointers.values
        gas = "gas" in v
        brake = "brake" in v
        leanBack = "leanBack" in v
        leanFwd = "leanFwd" in v
        boostHeld = "boost" in v
    }

    fun isHeld(id: String) = id in pointers.values

    private fun buyUpgrade(u: Upgrade) {
        val lv = save.level(u)
        if (lv >= Upgrade.MAX_LEVEL) return
        val cost = u.cost(lv)
        if (save.money < cost) return
        save.money -= cost
        save.upgrades[u.ordinal]++
        save.save()
        sfx.play(Sfx.S.COIN)
    }

    private fun pickCar(i: Int) {
        val ct = CarType.entries[i]
        if (save.ownsCar(i)) {
            save.car = i
        } else if (save.money >= ct.cost) {
            save.money -= ct.cost
            save.ownedCars = save.ownedCars or (1 shl i)
            save.car = i
        } else {
            return
        }
        sfx.play(Sfx.S.COIN)
        save.save()
    }

    private fun pickWeapon(i: Int) {
        val w = WeaponType.entries[i]
        if (save.ownsWeapon(i)) {
            save.weapon = i
        } else if (save.money >= w.cost) {
            save.money -= w.cost
            save.ownedWeapons = save.ownedWeapons or (1 shl i)
            save.weapon = i
        } else {
            return
        }
        sfx.play(Sfx.S.COIN)
        save.save()
    }

    // -------------------------------------------------------------- render

    fun render(c: Canvas) = renderer.render(c)
}
