package com.khongvinh.carsurvival

import android.graphics.Canvas
import kotlin.math.PI
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
    val beams = ArrayList<Beam>()
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

    /** HP taken from pillbox fire this run (balance checks). */
    var bunkerDamage = 0f

    /** Red gems picked up this run (banked when the run ends). */
    var runGems = 0

    /** Asks before wiping all progress from the menu. */
    var confirmReset = false

    // story screen
    var storyPages: List<StoryPage> = emptyList()
    var storyIndex = 0
    private var afterStory: () -> Unit = {}

    /** Place of the last run on the distance leaderboard (0 = not on it). */
    var boardRank = 0

    // car stats for this run
    private var topSpeed = 700f
    private var engineAcc = 520f
    private var mu = 1f
    private var armor = 0f
    var weapon = WeaponType.PISTOL
        private set
    var wstats = WeaponType.PISTOL.stats(0)
        private set
    var vehicle = Vehicles.of(0)
        private set
    private var ramMul = 1f

    /** Money from monsters grows with the stage, so farming later stages pays more. */
    private val rewardMul get() = 1f + 0.12f * (stage - 1)

    // garage browsing
    var garageTab = 0
    var garageClass = VehicleClass.MOTO
    var garageVariant = 0
    var garageWeapon = WeaponType.PISTOL

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
        if (save.storySeen and (1 shl Story.PROLOGUE_BIT) == 0) {
            showStory(Story.prologue) {
                save.storySeen = save.storySeen or (1 shl Story.PROLOGUE_BIT)
                save.save()
                setState(State.MENU)
            }
        }
    }

    /** Shows [pages]; tapping through (or SKIP) runs [then]. */
    private fun showStory(pages: List<StoryPage>, then: () -> Unit) {
        storyPages = pages
        storyIndex = 0
        afterStory = then
        setState(State.STORY)
    }

    private fun nextStoryPage() {
        if (storyIndex < storyPages.size - 1) {
            storyIndex++
            stateTime = 0f
        } else {
            afterStory()
        }
    }

    /** Starts a run, after the region's story chapter the first time. */
    private fun play() {
        val r = Themes.of(save.stageSel.coerceIn(1, maxStartStage())).region
        val bit = 1 shl (r.ordinal + 1)
        if (save.storySeen and bit == 0) {
            showStory(listOf(Story.chapter(r))) {
                save.storySeen = save.storySeen or bit
                save.save()
                startRun()
            }
        } else {
            startRun()
        }
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

    /** Map (look, region, monsters) of the stage being played or previewed. */
    val theme get() = Themes.of(stage)
    val region get() = theme.region

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
        val ct = save.vehicleType
        vehicle = ct
        val up = { u: Upgrade -> save.level(u).toFloat() }
        topSpeed = ct.topSpeed * (1f + 0.025f * up(Upgrade.ENGINE))
        engineAcc = ct.engineAcc * (1f + 0.03f * up(Upgrade.ENGINE))
        mu = ct.traction * (1f + 0.02f * up(Upgrade.GRIP))
        armor = min(0.75f, ct.totalArmor + 0.01f * up(Upgrade.ARMOR))
        ramMul = ct.ram
        weapon = save.weaponType
        wstats = weapon.stats(save.weaponLevel(weapon))

        val c = car
        c.vx = 0f; c.vy = 0f; c.spin = 0f; c.angle = 0f
        c.throttle = 0f; c.leanIn = 0f; c.squat = 0f; c.slashT = 0f
        stuckX = 0f; stuckGasT = 0f
        c.maxHp = ct.maxHp * (1f + 0.04f * up(Upgrade.ARMOR))
        c.hp = c.maxHp
        c.maxFuel = ct.fuelTime * (1f + 0.05f * up(Upgrade.FUEL))
        c.fuel = c.maxFuel
        c.maxBoost = ct.boostTime + 0.15f * up(Upgrade.BOOST)
        c.boost = c.maxBoost
        c.ammo = ct.ammo + 6f * up(Upgrade.AMMO)
        c.shieldT = 0f; c.cloneT = 0f; c.hurtT = 0f; c.flipT = 0f; c.stuckT = 0f; c.fireCd = 0f
        c.aim = 0f

        zombies.clear(); obstacles.clear(); debris.clear(); items.clear()
        shots.clear(); particles.clear(); texts.clear(); beams.clear()
        bossRocks.clear()
        bossKilled = false
        boss = if (stage % 5 == 0) Boss(stage / 5, goalX - 520f, terrain.heightAt(goalX - 520f)) else null
        populate()
        placeBunkers()

        stormX = -1500f
        phase = 0
        phaseLeft = PHASE_TIME[0]
        lastBeep = 0
        kills = 0; smashed = 0; runMoney = 0; distMoney = 0; stageBonus = 0; runGems = 0; bunkerDamage = 0f
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
                    // each region has its own monsters
                    val type = when (region) {
                        Region.DESERT -> when {
                            stage >= 2 && tRoll < 0.15f -> ZombieType.FAT
                            tRoll < 0.4f -> ZombieType.RUNNER
                            else -> ZombieType.WALKER
                        }
                        Region.VOLCANO -> when {
                            tRoll < 0.15f -> ZombieType.FAT
                            tRoll < 0.5f -> ZombieType.LIZARD
                            else -> ZombieType.WALKER
                        }
                        Region.NIGHT -> when {
                            tRoll < 0.12f -> ZombieType.FAT
                            tRoll < 0.5f -> ZombieType.BAT
                            else -> ZombieType.WALKER
                        }
                        Region.ALIEN -> if (tRoll < 0.2f) ZombieType.BLOB else ZombieType.ALIEN
                    }
                    zombies.add(Zombie(type, x, terrain.heightAt(x)))
                    // monsters like company
                    if (rnd.nextFloat() < 0.35f) {
                        val x2 = x + 50f + rnd.nextFloat() * 60f
                        val buddy = when (type) {
                            ZombieType.FAT -> ZombieType.WALKER
                            ZombieType.BLOB -> ZombieType.ALIEN
                            else -> type
                        }
                        zombies.add(Zombie(buddy, x2, terrain.heightAt(x2)))
                    }
                } else if (terrain.awkward(x) || terrain.onBridge(x) || terrain.onBridge(x + 200f)) {
                    // never block a dip, a steep face or a bridge: a monster stands there instead
                    zombies.add(Zombie(if (region == Region.ALIEN) ZombieType.ALIEN else ZombieType.WALKER, x, terrain.heightAt(x)))
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
            // red gems: more of them on later stages, some hanging over the road to jump for
            val gemsHere = 1 + stage / 6 + rnd.nextInt(2)
            for (k in 0 until gemsHere) {
                val x = start + rnd.nextFloat() * seg
                if (x > goalX - 600f) continue
                val roof = terrain.ceilingAt(x)
                val lift = if (roof.isNaN() && rnd.nextFloat() < 0.4f) 170f + rnd.nextFloat() * 120f else 70f
                items.add(Item(ItemType.GEM, x, terrain.heightAt(x) - lift))
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

    /** Pillboxes that shoot back: 1 on stage 1, one more every 3 stages. */
    private fun placeBunkers() {
        val rnd = Random(stage * 31 + save.day * 7)
        val n = 1 + stage / 3
        val from = 1500f
        val to = goalX - (if (boss != null) 3300f else 700f)
        if (to <= from) return
        val span = (to - from) / n
        for (k in 0 until n) {
            var tries = 0
            while (tries++ < 12) {
                val x = from + span * k + rnd.nextFloat() * span * 0.8f
                if (terrain.awkward(x) || terrain.onBridge(x) || terrain.onBridge(x + 160f) || terrain.covered(x, 200f)) continue
                if (obstacles.any { abs(it.x - x) < 220f }) continue
                val o = Obstacle(ObstacleType.BUNKER, x, terrain.heightAt(x))
                o.maxHp = 8f + stage
                o.hp = o.maxHp
                o.fireCd = 1f + rnd.nextFloat()
                obstacles.add(o)
                break
            }
        }
    }

    private fun endRun(reason: String) {
        if (state != State.PLAYING && state != State.PAUSED) return
        endReason = reason
        val m = maxDist.toInt()
        distMoney = (m * 0.25f).toInt()
        save.money += distMoney + runMoney
        save.gems += runGems
        if (m > save.best[stage]) {
            newRecord = save.best[stage] > 0
            save.best[stage] = m
        }
        boardRank = save.addRecord(m, stage, vehicle.id, save.day)
        finishStats(false)
        save.day++
        save.save()
        sfx.play(Sfx.S.LOSE, 0.8f)
        setState(State.RESULT)
    }

    private fun stageComplete() {
        val m = goalM.toInt()
        distMoney = (m * 0.25f).toInt()
        stageBonus = 120 * stage
        save.money += distMoney + runMoney + stageBonus
        save.gems += runGems
        save.best[stage] = max(save.best[stage], m)
        boardRank = save.addRecord(m, stage, vehicle.id, save.day)
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
        if (boardRank in 1..3) achievements.add("🏅 Hạng $boardRank bảng xếp hạng")
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
        shots.clear(); particles.clear(); texts.clear(); beams.clear()
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
            State.MENU, State.MAPS, State.GARAGE, State.STORY -> music.play(Music.Track.CHILL, 1f)
            State.PLAYING -> music.play(if (intense) Music.Track.INTENSE else Music.Track.CHILL, 1f)
            State.PAUSED -> music.play(if (intense) Music.Track.INTENSE else Music.Track.CHILL, 0.3f)
            State.RESULT -> music.play(Music.Track.CHILL, 0.6f)
            State.STAGE_DONE, State.VICTORY -> music.play(Music.Track.VICTORY, 1f)
        }
        music.update(dt)
        updateEngineSound(dt)
        when (state) {
            State.PLAYING -> updatePlay(dt)
            State.PAUSED -> {}
            else -> {
                updateEffects(dt)
                updateDebris(dt)
            }
        }
    }

    private var engineRev = 0f

    /** Engine note: pitch follows road speed (revs up in the air), loudness the throttle. */
    private fun updateEngineSound(dt: Float) {
        val c = car
        if (state != State.PLAYING || dyingT > 0f || wrecked) {
            sfx.engine(false)
            engineRev = 0f
            return
        }
        val airborne = !c.grounded[0] && !c.grounded[1]
        var want = (abs(c.forwardSpeed) / topSpeed).coerceIn(0f, 1.2f)
        if (airborne && c.throttle > 0.2f) want = max(want, 1f)
        want = want * 0.8f + max(0f, c.throttle) * 0.3f
        engineRev += (want - engineRev) * min(1f, dt * 5f)
        val pitch = when (vehicle.cls) {
            VehicleClass.MOTO -> 1.35f
            VehicleClass.CAR -> 1.05f
            VehicleClass.PICKUP -> 0.92f
            VehicleClass.ARMORED -> 0.8f
            VehicleClass.TANK -> 0.68f
        }
        val vol = 0.14f + 0.2f * max(0f, c.throttle) + (if (c.boosting) 0.08f else 0f)
        sfx.engine(true, pitch * (0.62f + engineRev * 0.85f), vol)
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
        // the body squats under throttle and dips under the brakes (drawing only)
        val wantSquat = if (c.grounded[0] || c.grounded[1]) -c.throttle * 0.045f else 0f
        c.squat += (wantSquat - c.squat) * min(1f, dt * 7f)
        if (c.slashT > 0f) c.slashT -= dt

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
        val b0 = boss
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
        // wedged against crates / wrecks with the gas held: smash through after a moment
        if (gas && c.fuel > 0f && (b0 == null || !b0.alive || c.x < goalX - 600f)) {
            if (abs(c.x - stuckX) < 40f) stuckGasT += dt else {
                stuckX = c.x; stuckGasT = 0f
            }
            if (stuckGasT > 2.2f) {
                unstick()
                stuckX = c.x; stuckGasT = 0f
            }
        } else {
            stuckX = c.x; stuckGasT = 0f
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

    private var stuckX = 0f
    private var stuckGasT = 0f

    /** Clears the crates / wrecks pinning the car down. */
    private fun unstick() {
        val c = car
        var freed = false
        for (o in obstacles) {
            if (!o.alive || abs(o.x - c.x) > Car.HALF_LEN + o.type.w / 2 + 90f || abs(o.y - c.y) > 260f) continue
            if (o.type == ObstacleType.BARREL) {
                o.alive = false
                smashed++
                explode(o.x, o.y - o.type.h / 2, 0xFFFF5722.toInt(), 20)
            } else {
                breakObstacle(o, c.fwdX * 300f)
            }
            freed = true
        }
        if (freed) {
            // a small nudge forward once the way is clear (only when roughly level)
            if (abs(c.angle) < 0.5f) {
                c.vx += c.fwdX * 160f
                c.vy -= 160f
            }
            shake = max(shake, 0.25f)
            sfx.play(Sfx.S.HIT, 0.8f)
            texts.add(FloatText("PHÁ TAN!", c.x + 60f, c.y - 120f, 0xFFFFAB00.toInt()).also { it.size = 52f })
        }
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
            debris.add(Debris(Debris.METAL, c.x + rnd(-60f, 60f), c.y - 20f, c.vx * 0.5f + rnd(-350f, 350f), -rnd(350f, 800f), 0f, rnd(-14f, 14f), rnd(24f, 50f), rnd(10f, 20f), vehicle.color))
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
        // inputs ramp in and out over a fraction of a second instead of snapping on/off
        val wantT = when {
            dyingT > 0f -> 0f
            gas && c.fuel > 0f -> 1f
            brake -> -1f
            else -> 0f
        }
        c.throttle += (wantT - c.throttle) * min(1f, h * (if (wantT == 0f) 14f else 8f))
        val wantLean = if (dyingT > 0f) 0f else (if (leanFwd) 1f else 0f) - (if (leanBack) 1f else 0f)
        c.leanIn += (wantLean - c.leanIn) * min(1f, h * 10f)
        val landed = phys.step(
            terrain, c, h,
            throttle = c.throttle,
            lean = c.leanIn,
            topSpeed = topSpeed, engineAcc = engineAcc, mu = mu,
        )
        if (landed) {
            sfx.play(Sfx.S.LAND, 0.6f)
            shake = max(shake, 0.12f)
            // sparks fly where the chassis scrapes the road
            val gy = terrain.heightAt(c.x)
            repeat(10) {
                particles.add(Particle(c.x + rnd(-70f, 70f), gy - 4f, -c.vx * 0.3f + rnd(-260f, 260f), -rnd(120f, 380f), 0.35f, 0.35f, rnd(2.5f, 4.5f), if (Random.nextBoolean()) 0xFFFFD54F.toInt() else 0xFFFFF8E1.toInt(), G * 0.6f))
            }
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
            z.phase += dt * (when (z.type) {
                ZombieType.RUNNER, ZombieType.LIZARD -> 9f
                ZombieType.BAT -> 14f
                else -> 4f
            })
            if (abs(dx) < 1300f) {
                z.x += sign(dx) * z.type.speed * dt
            }
            if (z.burnT > 0f) {
                // set alight by the flamethrower
                z.burnT -= dt
                z.hp -= 1.2f * dt
                if (Random.nextFloat() < dt * 25f) {
                    particles.add(Particle(z.x + rnd(-12f, 12f), z.y - z.fly - rnd(10f, z.type.h), rnd(-20f, 20f), -rnd(60f, 140f), 0.4f, 0.4f, rnd(5f, 10f), if (Random.nextBoolean()) 0xFFFF6D00.toInt() else 0xFFFFD740.toInt()))
                }
                if (z.hp <= 0f) {
                    smashZombie(z, rnd(-80f, 80f), -200f)
                    continue
                }
            }
            val ground = terrain.heightAt(z.x)
            if (z.type.flying) {
                // hover, then swoop down at the car when close
                var target = if (abs(dx) < 320f) max(10f, ground - c.y - 10f) else 150f + 35f * sin(z.phase * 0.3f)
                val roof = terrain.ceilingAt(z.x)
                if (!roof.isNaN()) target = min(target, ground - roof - z.type.h - 20f)
                z.fly += (target - z.fly) * min(1f, dt * 3f)
                z.y = ground - z.fly
            } else {
                z.y = ground
            }
            toLocal(z.x, z.y - z.type.h / 2, loc)
            if (abs(loc[0]) < Car.HALF_LEN + z.type.w / 2 && abs(loc[1]) < Car.HALF_H + 14f + z.type.h / 2) {
                if (z.type.flying) {
                    // bats burst on impact but bite first
                    smashZombie(z, c.vx * 0.8f + rnd(-80f, 80f), -200f)
                    damageCar(4f * (1f - armor))
                } else if (speed > 120f || c.shieldT > 0f) {
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
        val kind = when (z.type) {
            ZombieType.FAT -> Debris.FAT_ZOMBIE
            ZombieType.WALKER, ZombieType.RUNNER -> Debris.ZOMBIE
            else -> Debris.CREATURE + z.type.ordinal
        }
        debris.add(Debris(kind, z.x, z.y - z.type.h / 2, vx, vy, 0f, rnd(-12f, 12f), z.type.w, z.type.h, z.shirt))
        bleed(z.x, z.y - z.type.h / 2, 14)
        addMoney((z.type.reward * rewardMul).toInt(), z.x, z.y - z.type.h - 20f)
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
            if (o.type == ObstacleType.BUNKER) bunkerGun(o, dt)
            if (dx > 400f || dx < -400f) continue
            toLocal(o.x, o.y - o.type.h / 2, loc)
            val ox = Car.HALF_LEN + o.type.w / 2
            val oy = Car.HALF_H + 12f + o.type.h / 2
            if (abs(loc[0]) >= ox || abs(loc[1]) >= oy) continue
            when {
                o.type == ObstacleType.BARREL -> barrelBlast(o)
                abs(fs) > o.type.breakSpeed / max(1f, ramMul) || (c.shieldT > 0f && o.type != ObstacleType.WRECK && o.type != ObstacleType.BUNKER) ||
                    (c.shieldT > 0f && abs(fs) > 300f && o.type != ObstacleType.BUNKER) -> {
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
                        // pushing against a wall must not lever the car onto its tail
                        c.spin *= 0.6f
                        if (into > 60f) o.barT = 4f
                        if (into > Obstacle.RAM_SPEED && o.ramCd <= 0f) {
                            // a solid ram wears the obstacle down by 25%; four rams break it
                            o.ramCd = 0.35f
                            o.ramHp -= Obstacle.RAM_HIT * (if (ramMul >= 1.4f) 2f else 1f)
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

    /** A pillbox swings its gun at the car and fires bursts while it is in range. */
    private fun bunkerGun(o: Obstacle, dt: Float) {
        val c = car
        if (o.muzzleT > 0f) o.muzzleT -= dt
        val gx = o.x - 10f
        val gy = o.y - 52f
        val dx = c.x - gx
        val dy = c.y - 20f - gy
        if (dx > 400f || dx < -1150f || dyingT > 0f) return
        val want = kotlin.math.atan2(dy, dx)
        o.aim += (want - o.aim) * min(1f, dt * 4f)
        o.fireCd -= dt
        if (o.fireCd > 0f) return
        // three-round burst, then reload
        o.fireCd = if ((anim * 10f).toInt() % 3 == 0) max(0.9f, 1.9f - stage * 0.04f) else 0.16f
        val spread = rnd(-0.05f, 0.05f)
        val sp = 900f + stage * 15f
        val mx = gx + cos(o.aim) * 46f
        val my = gy + sin(o.aim) * 46f
        bossRocks.add(BossRock(mx, my, cos(o.aim + spread) * sp, sin(o.aim + spread) * sp, 3, 4f + stage * 0.5f).also { it.life = 1.6f })
        o.muzzleT = 0.07f
        particles.add(Particle(mx, my, 0f, 0f, 0.08f, 0.08f, 14f, 0xFFFFE082.toInt()))
        sfx.play(Sfx.S.SHOOT, 0.35f)
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
        addMoney((o.type.reward * rewardMul).toInt(), o.x, o.y - o.type.h - 10f)
        val cy = o.y - o.type.h / 2
        if (o.type == ObstacleType.BUNKER) {
            repeat(8) {
                debris.add(Debris(Debris.METAL, o.x + rnd(-60f, 60f), cy, vx * 0.5f + rnd(-250f, 300f), -rnd(250f, 700f), 0f, rnd(-10f, 10f), rnd(26f, 54f), rnd(14f, 26f), 0xFF8D8A83.toInt()))
            }
            explode(o.x, cy, 0xFF9E9E9E.toInt(), 40)
            explode(o.x, cy - 20f, 0xFFFF6D00.toInt(), 30)
            shake = max(shake, 0.3f)
            sfx.play(Sfx.S.EXPLODE)
            runGems += 3
            texts.add(FloatText("+3♦", o.x + 40f, o.y - o.type.h - 60f, 0xFFFF5252.toInt()).also { it.size = 44f })
        } else if (o.type == ObstacleType.WRECK) {
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
        blastArea(cx, cy, 220f, 5f)
        val c = car
        val d = hypot(c.x - cx, c.y - cy)
        if (d < 260f) {
            damageCar(o.type.damage * (1f - armor))
            c.vy -= 380f
            c.spin += rnd(-1.5f, 1.5f)
        }
    }

    /** Damages / kills everything in a radius (barrels, catapult). */
    private fun blastArea(cx: Float, cy: Float, r: Float, dmg: Float) {
        boss?.let { bb ->
            if (bb.alive && abs(bb.x - cx) < r + bb.w / 2 && abs(bb.cy - cy) < r + bb.h / 2) hurtBoss(bb, dmg * 2f)
        }
        for (z in zombies) {
            if (!z.alive) continue
            val d = hypot(z.x - cx, z.y - z.type.h / 2 - cy)
            if (d < r) {
                z.hp -= dmg
                if (z.hp <= 0f) smashZombie(z, (z.x - cx) * 3f, -400f - rnd(0f, 200f))
            }
        }
        for (o in obstacles) {
            if (!o.alive) continue
            val d = hypot(o.x - cx, o.y - o.type.h / 2 - cy)
            if (d < r + o.type.w * 0.3f) {
                if (o.type == ObstacleType.BARREL) {
                    o.hp = 0f
                } else {
                    o.hp -= dmg
                    o.flash = 0.1f
                    if (o.hp <= 0f) breakObstacle(o, (o.x - cx) * 2f)
                }
            }
        }
        // chain reaction
        val chain = obstacles.filter { it.alive && it.type == ObstacleType.BARREL && it.hp <= 0f }
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
        val tier = b.tier
        if (b.diveT > 0f) b.diveT -= dt
        if (b.breathT > 0f) b.breathT -= dt

        // movement: walk/fly towards the car but guard the station
        val chase = if (tier == 3 && b.diveT > 0f) b.speed * 5f else b.speed
        if (abs(dx) > (if (tier == 3 && b.diveT > 0f) 20f else 330f)) b.x += dir * chase * dt
        b.x = b.x.coerceIn(goalX - 1150f, goalX - 260f)
        if (b.flying) {
            val carLift = (terrain.heightAt(b.x) - c.y).coerceAtLeast(0f)
            val target = when {
                b.diveT > 0f && tier == 3 -> max(20f, carLift - b.h * 0.3f) // bat swoops at the car
                b.diveT > 0f -> 30f // dragon lands for a moment
                else -> (if (tier == 2) 230f else 260f) + 35f * sin(b.anim * 1.6f)
            }
            b.lift += (target - b.lift) * min(1f, dt * (if (b.diveT > 0f) 4f else 2f))
        }
        val mouthX = b.x + dir * b.w * 0.45f
        val mouthY = b.cy - (if (tier == 2) b.h * 0.3f else b.h * 0.4f)

        // ranged attacks
        b.throwCd -= dt
        if (b.throwCd <= 0f && abs(dx) in 280f..1400f && b.diveT <= 0f && tier != 3) {
            b.throwCd = max(1.4f, 3f - 0.35f * tier) * (0.8f + Random.nextFloat() * 0.4f)
            b.throwAnim = 0.45f
            when (tier) {
                4 -> { // three plasma orbs fanned at the car
                    val ox = b.x + dir * b.w * 0.4f
                    val oy = b.y - b.h * 0.75f
                    val a = atan2(c.y - oy, c.x - ox)
                    for (k in -1..1) {
                        val aa = a + k * 0.2f
                        bossRocks.add(BossRock(ox, oy, cos(aa) * 620f, sin(aa) * 620f, 2))
                    }
                    sfx.play(Sfx.S.BOOST, 0.6f)
                }
                else -> { // boulder (giant) or fireball (dragon), lobbed ahead of the car
                    val x0 = if (tier == 2) mouthX else b.x + dir * b.w * 0.3f
                    val y0 = if (tier == 2) mouthY else b.y - b.h * 1.02f
                    val t = (abs(c.x - x0) / 750f).coerceIn(0.6f, 1.5f)
                    val tx = c.x + c.vx * t * 0.6f
                    bossRocks.add(BossRock(x0, y0, (tx - x0) / t, (c.y - 10f - y0) / t - 0.5f * G * t, if (tier == 2) 1 else 0))
                    sfx.play(Sfx.S.CATAPULT, 0.9f)
                }
            }
        }
        when (tier) {
            2 -> { // dragon: fire breath and landing
                b.breathCd -= dt
                if (b.breathCd <= 0f && abs(dx) < 750f && b.diveT <= 0f) {
                    b.breathCd = 6f
                    b.breathT = 1.6f
                    sfx.play(Sfx.S.BOOST, 1f)
                }
                if (b.breathT > 0f) {
                    val a = atan2(c.y - mouthY, c.x - mouthX)
                    repeat(3) {
                        val aa = a + rnd(-0.18f, 0.18f)
                        val sp = rnd(500f, 800f)
                        particles.add(Particle(mouthX, mouthY, cos(aa) * sp, sin(aa) * sp, 0.6f, 0.6f, rnd(8f, 16f), if (Random.nextBoolean()) 0xFFFF6D00.toInt() else 0xFFFFC400.toInt(), 0f, 1.4f))
                    }
                    if (abs(dx) < 550f) damageCar(14f * (1f - armor) * dt, instant = false)
                }
                b.slamCd -= dt
                if (b.slamCd <= 0f && b.breathT <= 0f) {
                    b.slamCd = 7f
                    b.diveT = 2.4f
                }
            }
            3 -> { // vampire bat: swoop-bite and summon bats
                b.slamCd -= dt
                if (b.slamCd <= 0f && abs(dx) < 1000f) {
                    b.slamCd = max(3f, 4.5f)
                    b.diveT = 1.8f
                    sfx.play(Sfx.S.ROAR, 0.5f)
                }
                b.summonCd -= dt
                if (b.summonCd <= 0f) {
                    b.summonCd = 8f
                    repeat(2) {
                        zombies.add(Zombie(ZombieType.BAT, b.x + rnd(-80f, 80f), terrain.heightAt(b.x)).also { z -> z.fly = b.lift + rnd(-30f, 30f) })
                    }
                }
                if (b.diveT > 0f && b.ramCd <= 0f && abs(c.x - b.x) < b.w * 0.45f + Car.HALF_LEN && abs(c.y - b.cy) < b.h) {
                    // bite: drains the car and heals the bat
                    damageCar((10f + 3f * tier) * (1f - armor))
                    b.hp = min(b.maxHp, b.hp + 6f)
                    texts.add(FloatText("+6", b.x, b.cy - b.h, 0xFF69F0AE.toInt()))
                    bleed(c.x, c.y - 20f, 12)
                    c.vx -= dir * -260f
                    b.diveT = min(b.diveT, 0.25f)
                    b.ramCd = 0.8f
                }
            }
            else -> { // giant zombie / alien: ground slam when close
                b.slamCd -= dt
                if (b.slamCd <= 0f && abs(dx) < 300f) {
                    b.slamCd = max(1.6f, 2.8f - 0.25f * tier)
                    b.slamAnim = 0.5f
                    shake = max(shake, 0.45f)
                    sfx.play(Sfx.S.BOOM, 0.9f)
                    repeat(26) {
                        val a = Random.nextFloat() * 3.1416f
                        particles.add(Particle(b.x + rnd(-80f, 80f), b.y - 5f, cos(a) * rnd(150f, 450f), -sin(a) * rnd(100f, 320f), 0.7f, 0.7f, rnd(8f, 16f), if (tier == 4) 0xFF80DEEA.toInt() else 0xFFC9A27E.toInt(), 300f, 1.2f))
                    }
                    if (c.grounded[0] || c.grounded[1] || c.y > terrain.heightAt(c.x) - 160f) {
                        damageCar((10f + 4f * tier) * (1f - armor))
                        c.vx = dir * (420f + 60f * tier)
                        c.vy = -420f
                        c.spin += dir * 1.5f
                    }
                }
            }
        }

        // body contact (flying bosses only when they come low): rams hurt the boss
        val ox = Car.HALF_LEN + b.w / 2
        val low = !b.flying || b.lift < 140f
        if (low && abs(dx) < ox && abs(c.y - b.cy) < b.h / 2 + 60f) {
            val toward = -dir
            val fs = c.forwardSpeed
            val into = fs * toward * sign(c.fwdX)
            c.x = b.x + dir * ox
            if (into > 0f) {
                if (into > Obstacle.RAM_SPEED && b.ramCd <= 0f) {
                    b.ramCd = 0.45f
                    val dmg = into / 35f * (if (c.boosting) 1.5f else 1f) * ramMul
                    hurtBoss(b, dmg)
                    damageCar((5f + 2f * tier) * (1f - armor))
                    shake = max(shake, 0.3f)
                    sfx.play(Sfx.S.HIT, 0.9f)
                    bleed(b.x - dir * b.w / 2, b.cy, 10)
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
            runGems += 10 * b.tier
            texts.add(FloatText("+${10 * b.tier}♦", b.x, b.cy - b.h, 0xFFFF5252.toInt()).also { it.size = 60f; it.life = 2.2f })
            val main = when (b.tier) {
                2 -> 0xFFC62828.toInt()
                3 -> 0xFF4A148C.toInt()
                4 -> 0xFF80DEEA.toInt()
                else -> 0xFF7CB342.toInt()
            }
            explode(b.x, b.cy, main, 90)
            explode(b.x, b.cy - b.h * 0.3f, 0xFFFF6D00.toInt(), 60)
            bleed(b.x, b.cy, 40)
            if (b.tier == 1) {
                debris.add(Debris(Debris.FAT_ZOMBIE, b.x, b.y - b.h / 2, car.vx * 0.3f + 250f, -650f, 0f, 3f, b.w, b.h, 0xFF4E342E.toInt()).also { it.life = 8f })
            } else {
                repeat(8) {
                    debris.add(Debris(Debris.METAL, b.x + rnd(-60f, 60f), b.cy, rnd(-400f, 400f), -rnd(300f, 800f), 0f, rnd(-12f, 12f), rnd(30f, 60f), rnd(14f, 26f), main))
                }
            }
            addMoney(300 * b.tier, b.x, b.cy - b.h / 2 - 60f)
            texts.add(FloatText("${b.name} ĐÃ GỤC!", b.x, b.cy, 0xFFFFD740.toInt()).also { it.size = 60f; it.life = 2.5f })
            shake = 1f
            sfx.play(Sfx.S.ROAR, 0.7f)
            sfx.play(Sfx.S.BOOM)
        }
    }

    private fun flushBossDamage(b: Boss) {
        if (b.pendingDmg >= 1f) {
            texts.add(FloatText("-${b.pendingDmg.toInt()}", b.x + rnd(-40f, 40f), b.cy - b.h / 2 - 30f, 0xFFFFEA00.toInt()).also { it.size = 46f })
        }
        b.pendingDmg = 0f
        b.dmgT = 0f
    }

    private fun updateBossRocks(dt: Float) {
        val c = car
        val tier = boss?.tier ?: 1
        for (r in bossRocks) {
            if (!r.alive) continue
            if (r.kind < 2) r.vy += G * dt
            r.x += r.vx * dt
            r.y += r.vy * dt
            r.rot += dt * 6f
            r.life -= dt
            if (r.kind == 1 && Random.nextFloat() < 0.6f) {
                particles.add(Particle(r.x, r.y, rnd(-40f, 40f), rnd(-60f, 0f), 0.35f, 0.35f, rnd(6f, 12f), if (Random.nextBoolean()) 0xFFFF6D00.toInt() else 0xFFFFC400.toInt(), 0f, 1f))
            }
            if (r.kind == 3) {
                // bunker bullet
                if (hypot(r.x - c.x, r.y - c.y) < 70f) {
                    r.alive = false
                    if (c.shieldT <= 0f) bunkerDamage += r.dmg * (1f - armor)
                    damageCar(r.dmg * (1f - armor))
                    sparks(r.x, r.y)
                    sfx.play(Sfx.S.HIT, 0.35f)
                } else if (r.y > terrain.heightAt(r.x) || r.life <= 0f) {
                    r.alive = false
                    sparks(r.x, r.y)
                }
                continue
            }
            if (hypot(r.x - c.x, r.y - c.y) < 75f) {
                r.alive = false
                damageCar(((if (r.kind == 0) 14f else 10f) + (if (r.kind == 2) 2f else 3f) * tier) * (1f - armor))
                c.vx -= 200f
                c.vy -= 150f
                explode(r.x, r.y, when (r.kind) { 1 -> 0xFFFF6D00.toInt(); 2 -> 0xFF64FFDA.toInt(); else -> 0xFF795548.toInt() }, 24)
                shake = max(shake, 0.35f)
                sfx.play(Sfx.S.EXPLODE, 0.8f)
            } else if (r.y > terrain.heightAt(r.x) || r.life <= 0f) {
                r.alive = false
                explode(r.x, r.y, when (r.kind) { 1 -> 0xFFFF9100.toInt(); 2 -> 0xFF64FFDA.toInt(); else -> 0xFF8D6E63.toInt() }, 14)
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
                c.ammo += 15f
                text("+15 ĐẠN", it, 0xFFFFCC80.toInt())
            }
            ItemType.TIME -> {
                if (phase < PHASE_TIME.size) phaseLeft += 5f
                stormX -= 400f
                text("+5 GIÂY, BÃO LÙI!", it, 0xFFEA80FC.toInt())
            }
            ItemType.GEM -> {
                runGems++
                texts.add(FloatText("+1♦", it.x, it.y - 30f, 0xFFFF5252.toInt()).also { t -> t.size = 36f })
                sfx.play(Sfx.S.COIN, 0.9f)
                return
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

    /** World position of the weapon mount on the current vehicle. */
    private fun mountPos(out: FloatArray) {
        val c = car
        val cs = cos(c.angle)
        val sn = sin(c.angle)
        val mx = vehicle.cls.mountX
        val my = vehicle.cls.mountY
        out[0] = c.x + mx * cs - my * sn
        out[1] = c.y + mx * sn + my * cs
    }

    private val mount = FloatArray(2)

    private fun updateWeapon(dt: Float) {
        val c = car
        c.fireCd -= dt
        if (weapon == WeaponType.KNIFE) {
            updateKnife()
            return
        }
        val st = wstats
        mountPos(mount)
        val ox = mount[0]
        val oy = mount[1]
        var tx = 0f
        var ty = 0f
        var best = Float.MAX_VALUE
        // a pillbox shooting at us comes first
        for (o in obstacles) {
            if (!o.alive || o.type != ObstacleType.BUNKER) continue
            val dx = o.x - c.x
            if (dx < -150f || dx > st.range || abs(dx) > best) continue
            best = abs(dx); tx = o.x; ty = o.y - o.type.h * 0.55f
        }
        if (best == Float.MAX_VALUE) for (z in zombies) {
            val dx = z.x - c.x
            if (dx < 60f || dx > st.range || dx > best) continue
            best = dx; tx = z.x; ty = z.y - z.type.h * 0.6f
        }
        boss?.let { bb ->
            val dx = bb.x - c.x
            // the turret swings round: the boss is the priority target, in front or behind
            if (bb.alive && bb.engaged && abs(dx) < max(st.range, 500f) + bb.w / 2) {
                best = abs(dx); tx = bb.x; ty = bb.cy
            }
        }
        if (best == Float.MAX_VALUE) {
            val weak = st.damage < 2f && st.splash == 0f && weapon != WeaponType.FLAMER
            for (o in obstacles) {
                if (o.type == ObstacleType.WRECK && weak) continue
                val dx = o.x - c.x
                if (dx < 100f || dx > min(st.range, 850f) || dx > best) continue
                best = dx; tx = o.x; ty = o.y - o.type.h / 2
            }
        }
        val targetAim = if (best == Float.MAX_VALUE) c.angle else atan2(ty - oy, tx - ox)
        c.aim += (targetAim - c.aim) * min(1f, dt * 12f)
        if (best == Float.MAX_VALUE || c.ammo < weapon.ammoUse || c.fireCd > 0f) return
        c.fireCd = st.interval
        c.ammo -= weapon.ammoUse
        val dx = tx - ox
        val dy = ty - oy
        val d = max(1f, hypot(dx, dy))
        val ang = atan2(dy, dx)
        fun shot(a: Float, speed: Float, life: Float) = Projectile(weapon, ox, oy, cos(a) * speed + c.vx, sin(a) * speed + c.vy, st.damage, st.pierce, st.splash).also { it.life = life }
        when (weapon) {
            WeaponType.PISTOL, WeaponType.SMG, WeaponType.MINIGUN -> {
                val jitter = if (weapon == WeaponType.MINIGUN) rnd(-0.05f, 0.05f) else 0f
                shots.add(shot(ang + jitter, weapon.speed, st.range / weapon.speed * 1.3f))
                sfx.play(Sfx.S.SHOOT, if (weapon == WeaponType.PISTOL) 0.4f else 0.3f)
            }
            WeaponType.SHOTGUN -> {
                val n = weapon.pellets
                for (k in 0 until n) {
                    val a = ang + (k - (n - 1) / 2f) / ((n - 1) / 2f) * weapon.spread + rnd(-0.03f, 0.03f)
                    shots.add(shot(a, weapon.speed * rnd(0.9f, 1.1f), st.range / weapon.speed * 1.2f))
                }
                sfx.play(Sfx.S.BOOM, 0.35f)
                shake = max(shake, 0.06f)
            }
            WeaponType.CROSSBOW -> {
                shots.add(shot(ang, weapon.speed, st.range / weapon.speed * 1.3f))
                sfx.play(Sfx.S.BOW, 0.6f)
            }
            WeaponType.CATAPULT -> {
                val t = (d / 900f).coerceIn(0.35f, 1.1f)
                shots.add(Projectile(weapon, ox, oy, dx / t, dy / t - 0.5f * G * t, st.damage, 1, st.splash).also { it.life = 3f })
                sfx.play(Sfx.S.CATAPULT, 0.7f)
            }
            WeaponType.FLAMER -> {
                val sp = weapon.speed * rnd(0.85f, 1.1f)
                shots.add(shot(ang + rnd(-0.12f, 0.12f), sp, st.range / sp * 1.25f))
                if (Random.nextFloat() < 0.25f) sfx.play(Sfx.S.BOOST, 0.25f)
            }
            WeaponType.ROCKET -> {
                shots.add(shot(ang - 0.25f, weapon.speed, 3f))
                sfx.play(Sfx.S.BOOST, 0.6f)
            }
            WeaponType.LASER -> fireLaser(ox, oy, ang, st)
            WeaponType.KNIFE -> {}
        }
    }

    /** Front blades: slash whatever is just ahead of the bumper. No ammo needed. */
    private fun updateKnife() {
        val c = car
        val st = wstats
        c.aim = c.angle
        if (c.fireCd > 0f) return
        val reach = Car.HALF_LEN + st.range
        var hitAny = false
        for (z in zombies) {
            if (!z.alive) continue
            toLocal(z.x, z.y - z.type.h / 2, loc)
            if (loc[0] > Car.HALF_LEN - 30f && loc[0] < reach + z.type.w / 2 && abs(loc[1]) < 60f + z.type.h / 2) {
                z.hp -= st.damage
                bleed(z.x, z.y - z.type.h / 2, 6)
                if (z.hp <= 0f) smashZombie(z, c.vx * 0.8f + 200f * c.fwdX, -300f)
                hitAny = true
            }
        }
        for (o in obstacles) {
            if (!o.alive || o.type == ObstacleType.BARREL) continue
            toLocal(o.x, o.y - o.type.h / 2, loc)
            if (loc[0] > 0f && loc[0] < reach + o.type.w / 2 && abs(loc[1]) < 50f + o.type.h / 2) {
                o.hp -= st.damage
                o.flash = 0.1f
                o.barT = 3f
                sparks(o.x - o.type.w / 2, o.y - o.type.h / 2)
                if (o.hp <= 0f) breakObstacle(o, c.vx)
                hitAny = true
            }
        }
        boss?.let { bb ->
            if (bb.alive && abs(bb.x - c.x) < reach + bb.w / 2 && abs(bb.cy - c.y) < bb.h / 2 + 70f) {
                hurtBoss(bb, st.damage)
                bleed(bb.x - bb.w / 2, bb.cy, 5)
                hitAny = true
            }
        }
        if (hitAny) {
            c.fireCd = st.interval
            c.slashT = 0.2f
            sfx.play(Sfx.S.HIT, 0.5f)
        }
    }

    /** Instant beam that pierces everything along its line. */
    private fun fireLaser(ox: Float, oy: Float, ang: Float, st: WeaponStats) {
        val dx = cos(ang)
        val dy = sin(ang)
        var len = st.range
        // stop at the ground
        var k = 40f
        while (k < len) {
            val roof = terrain.ceilingAt(ox + dx * k)
            if (oy + dy * k > terrain.heightAt(ox + dx * k) || (!roof.isNaN() && oy + dy * k < roof)) {
                len = k; break
            }
            k += 30f
        }
        fun along(px: Float, py: Float, rad: Float): Boolean {
            val t = (px - ox) * dx + (py - oy) * dy
            if (t < 0f || t > len) return false
            val ex = ox + dx * t - px
            val ey = oy + dy * t - py
            return ex * ex + ey * ey < rad * rad
        }
        for (z in zombies) {
            if (!z.alive || !along(z.x, z.y - z.type.h / 2, z.type.h / 2 + 10f)) continue
            z.hp -= st.damage
            bleed(z.x, z.y - z.type.h / 2, 4)
            if (z.hp <= 0f) smashZombie(z, dx * 300f, -250f)
        }
        for (o in obstacles) {
            if (!o.alive || !along(o.x, o.y - o.type.h / 2, o.type.h / 2 + 10f)) continue
            if (o.type == ObstacleType.BARREL) {
                barrelBlast(o); continue
            }
            o.hp -= st.damage
            o.flash = 0.1f
            o.barT = 3f
            if (o.hp <= 0f) breakObstacle(o, dx * 200f)
        }
        boss?.let { bb -> if (bb.alive && along(bb.x, bb.cy, max(bb.w, bb.h) / 2 + 20f)) hurtBoss(bb, st.damage) }
        beams.add(Beam(ox, oy, ox + dx * len, oy + dy * len))
        sparks(ox + dx * len, oy + dy * len)
        sfx.play(Sfx.S.BEEP, 0.5f)
    }

    private fun updateShots(dt: Float) {
        for (s in shots) {
            if (!s.alive) continue
            s.life -= dt
            s.age += dt
            when (s.kind) {
                WeaponType.CATAPULT -> s.vy += G * dt
                WeaponType.FLAMER -> {
                    s.vx *= 1f - 1.2f * dt
                    s.vy = s.vy * (1f - 1.2f * dt) - 90f * dt
                }
                WeaponType.ROCKET -> steerRocket(s, dt)
                else -> {}
            }
            s.x += s.vx * dt
            s.y += s.vy * dt
            val blasts = s.splash > 0f
            if (s.life <= 0f) {
                s.alive = false
                if (blasts) blast(s)
                continue
            }
            val roof = terrain.ceilingAt(s.x)
            if (s.y > terrain.heightAt(s.x) || (!roof.isNaN() && s.y < roof)) {
                s.alive = false
                if (blasts) blast(s) else if (s.kind != WeaponType.FLAMER) sparks(s.x, s.y)
                continue
            }
            val pad = if (s.kind == WeaponType.FLAMER) 10f + s.age * 60f else 6f
            val bb = boss
            if (bb != null && bb.alive && bb !in s.hit && abs(s.x - bb.x) < bb.w / 2 + pad && abs(s.y - bb.cy) < bb.h / 2 + (if (bb.flying) 40f else 10f)) {
                if (blasts) {
                    s.alive = false
                    blast(s)
                    continue
                }
                s.hit.add(bb)
                hurtBoss(bb, s.damage)
                if (s.kind != WeaponType.FLAMER) bleed(s.x, s.y, 3)
                if (--s.pierce <= 0) {
                    s.alive = false; continue
                }
            }
            if (blasts) {
                // explode on direct contact too
                val hitZ = zombies.any { it.alive && abs(it.x - s.x) < it.type.w && s.y > it.y - it.type.h - it.fly && s.y < it.y + 10f }
                val hitO = obstacles.any { it.alive && abs(it.x - s.x) < it.type.w / 2 && s.y > it.y - it.type.h }
                if (hitZ || hitO) {
                    s.alive = false
                    blast(s)
                }
                continue
            }
            for (z in zombies) {
                if (!z.alive || z in s.hit) continue
                if (abs(z.x - s.x) < z.type.w / 2 + pad && s.y > z.y - z.type.h - pad && s.y < z.y + pad) {
                    s.hit.add(z)
                    z.hp -= s.damage
                    if (s.kind == WeaponType.FLAMER) z.burnT = 2f else bleed(s.x, s.y, 4)
                    if (z.hp <= 0f) smashZombie(z, s.vx * 0.12f, -220f)
                    if (--s.pierce <= 0) {
                        s.alive = false; break
                    }
                }
            }
            if (!s.alive) continue
            for (o in obstacles) {
                if (!o.alive || o in s.hit) continue
                if (abs(o.x - s.x) < o.type.w / 2 + pad && s.y > o.y - o.type.h - pad && s.y < o.y) {
                    s.hit.add(o)
                    if (o.type == ObstacleType.BARREL) {
                        barrelBlast(o)
                    } else {
                        o.hp -= s.damage
                        o.flash = 0.08f
                        o.barT = 3f
                        if (s.kind != WeaponType.FLAMER) sparks(s.x, s.y)
                        if (o.hp <= 0f) breakObstacle(o, s.vx * 0.2f)
                    }
                    if (--s.pierce <= 0) {
                        s.alive = false; break
                    }
                }
            }
        }
        shots.removeAll { !it.alive }
    }

    /** Rockets turn towards the nearest monster (or the boss) and speed up. */
    private fun steerRocket(s: Projectile, dt: Float) {
        var tx = Float.NaN
        var ty = 0f
        var best = 900f
        boss?.let { bb ->
            if (bb.alive) {
                val d = hypot(bb.x - s.x, bb.cy - s.y)
                if (d < 1400f) {
                    best = 0f; tx = bb.x; ty = bb.cy
                }
            }
        }
        if (best > 0f) for (z in zombies) {
            if (!z.alive) continue
            val d = hypot(z.x - s.x, z.y - z.type.h / 2 - s.y)
            if (d < best && z.x > s.x - 100f) {
                best = d; tx = z.x; ty = z.y - z.type.h / 2
            }
        }
        val sp = min(1500f, hypot(s.vx, s.vy) + 1400f * dt)
        var a = atan2(s.vy, s.vx)
        if (!tx.isNaN() && s.age > 0.12f) {
            var da = atan2(ty - s.y, tx - s.x) - a
            while (da > PI) da -= (2 * PI).toFloat()
            while (da < -PI) da += (2 * PI).toFloat()
            a += da.coerceIn(-5f * dt, 5f * dt)
        }
        s.vx = cos(a) * sp
        s.vy = sin(a) * sp
        if (Random.nextFloat() < 0.7f) {
            particles.add(Particle(s.x - cos(a) * 20f, s.y - sin(a) * 20f, rnd(-30f, 30f), rnd(-40f, 0f), 0.5f, 0.5f, rnd(6f, 10f), 0xFF9E9E9E.toInt(), 0f, 2f))
        }
    }

    private fun blast(s: Projectile) {
        val big = s.kind == WeaponType.ROCKET
        explode(s.x, s.y, if (big) 0xFFFF6D00.toInt() else 0xFF8D6E63.toInt(), if (big) 40 else 30)
        sfx.play(if (big) Sfx.S.EXPLODE else Sfx.S.BOOM, 0.7f)
        shake = max(shake, if (big) 0.22f else 0.15f)
        blastArea(s.x, s.y - 30f, s.splash, s.damage)
    }

    // ------------------------------------------------------------- effects

    private fun updateEffects(dt: Float) {
        for (b in beams) b.life -= dt
        beams.removeAll { it.life <= 0f }
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
        // a couple of coins pop out
        repeat(2) {
            particles.add(Particle(x + rnd(-20f, 20f), y + 30f, rnd(-120f, 120f), -rnd(300f, 480f), 0.8f, 0.8f, 7f, 0xFFFFC107.toInt(), G))
        }
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
            State.MENU -> if (confirmReset) listOf(
                Button("resetNo", "HỦY", W / 2 - 330f, H * 0.62f, W / 2 - 30f, H * 0.62f + 86f, grey),
                Button("resetYes", "XÓA HẾT", W / 2 + 30f, H * 0.62f, W / 2 + 330f, H * 0.62f + 86f, 0xFFC62828.toInt()),
            ) else listOf(
                Button("story", "📖 CỐT TRUYỆN", W - 400f, 20f, W - 206f, 96f, 0xFF5D4037.toInt()),
                Button("reset", "↺ CHƠI LẠI", W - 590f, 20f, W - 410f, 96f, 0xFF4E342E.toInt()),
                Button("maps", "🗺  ẢI ${save.stageSel}: ${Themes.of(save.stageSel).name.uppercase()}", cx - 230f, 128f, cx + 230f, 214f, 0xFF6D4C41.toInt()),
                b("play", "CHẠY!", cx, 262f, 460f, green),
                b("garage", "GARA", cx, 360f, 460f, purple),
                Button("sound", if (save.sound) "🔊" else "🔇", W - 100f, 20f, W - 24f, 96f, 0x66000000),
                Button("music", "🎵", W - 190f, 20f, W - 114f, 96f, if (save.music) 0x66000000 else 0x66B71C1C),
            )
            State.MAPS -> listOf(b("menu", "QUAY LẠI", W / 2, H - 84f, 360f, grey))
            State.GARAGE -> garageButtons(green, grey)
            State.PAUSED -> listOf(
                b("resume", "TIẾP TỤC", W / 2, H * 0.32f, 420f, green),
                b("quit", "BỎ CUỘC", W / 2, H * 0.32f + 104f, 420f, grey),
                Button("sound", if (save.sound) "🔊 HIỆU ỨNG: BẬT" else "🔇 HIỆU ỨNG: TẮT", W / 2 - 330f, H * 0.32f + 214f, W / 2 - 10f, H * 0.32f + 290f, if (save.sound) 0xFF00897B.toInt() else grey),
                Button("music", if (save.music) "🎵 NHẠC: BẬT" else "🎵 NHẠC: TẮT", W / 2 + 10f, H * 0.32f + 214f, W / 2 + 330f, H * 0.32f + 290f, if (save.music) 0xFF00897B.toInt() else grey),
            )
            State.STORY -> listOf(Button("skip", "BỎ QUA ›", W - 290f, H - 96f, W - 40f, H - 24f, grey))
            State.RESULT -> {
                val l = ArrayList<Button>()
                val ids = listOf("menu" to "MENU", "garage" to "GARA", "play" to "CHẠY TIẾP")
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
                b("garage", "GARA", W / 2, H - 120f, 260f, blue),
                b("play", "ẢI TIẾP", W / 2 + 300f, H - 120f, 260f, green),
            )
            State.VICTORY -> listOf(b("menu", "MENU", W / 2, H - 130f, 360f, green))
            State.PLAYING -> emptyList()
        }
    }

    private fun garageButtons(green: Int, grey: Int): List<Button> {
        val l = ArrayList<Button>()
        val gold = 0xFFF9A825.toInt()
        l.add(Button("menu", "MENU", W / 2 - 400f, H - 100f, W / 2 - 100f, H - 14f, grey))
        l.add(Button("play", "CHẠY!", W / 2 + 100f, H - 100f, W / 2 + 400f, H - 14f, green))
        if (garageTab == 2) {
            // upgrade cards are tapped directly
        } else if (garageTab == 0) {
            val v = Vehicles.of(garageClass, garageVariant)
            val pr = previewRect()
            val (label, color, on) = when {
                !save.classUnlocked(v.cls) -> Triple("🔒 QUA ẢI ${v.cls.unlockStage}", grey, false)
                save.vehicle == v.id -> Triple("ĐANG DÙNG", green, false)
                save.ownsVehicle(v.id) -> Triple("CHỌN XE", 0xFF1E88E5.toInt(), true)
                else -> Triple("MUA ${v.cost}$", gold, save.money >= v.cost)
            }
            l.add(Button("vehAct", label, pr[0] + 24f, pr[3] - 78f, pr[0] + 360f, pr[3] - 14f, color, on))
        } else {
            val w = garageWeapon
            val pr = weaponPanelRect()
            val owned = save.ownsWeapon(w.ordinal)
            val (label, color, on) = when {
                !save.weaponUnlocked(w) -> Triple("🔒 QUA ẢI ${w.unlockStage}", grey, false)
                save.weapon == w.ordinal -> Triple("ĐANG DÙNG", green, false)
                owned -> Triple("TRANG BỊ", 0xFF1E88E5.toInt(), true)
                else -> Triple("MUA ${w.cost}$", gold, save.money >= w.cost)
            }
            l.add(Button("wpnAct", label, pr[2] - 330f, pr[1] + 30f, pr[2] - 24f, pr[1] + 102f, color, on))
            if (owned) {
                val lv = save.weaponLevel(w)
                if (lv >= WeaponType.MAX_LEVEL) {
                    l.add(Button("wpnUp", "CẤP TỐI ĐA", pr[2] - 330f, pr[1] + 118f, pr[2] - 24f, pr[1] + 190f, grey, false))
                } else {
                    val cost = w.upgradeCost(lv)
                    l.add(Button("wpnUp", "NÂNG CẤP ${cost}$", pr[2] - 330f, pr[1] + 118f, pr[2] - 24f, pr[1] + 190f, 0xFF8E24AA.toInt(), save.money >= cost))
                }
            }
        }
        return l
    }

    /** Gem upgrade cards: 3 x 2 grid. */
    fun upgradeCard(i: Int): FloatArray {
        val cw = (W - 120f) / 3f
        val col = i % 3
        val row = i / 3
        val l = 40f + col * (cw + 20f)
        val t = 104f + row * 248f
        return floatArrayOf(l, t, l + cw, t + 230f)
    }

    private fun buyUpgrade(u: Upgrade) {
        val lv = save.level(u)
        if (lv >= Upgrade.MAX_LEVEL) return
        val cost = u.cost(lv)
        if (save.gems < cost) return
        save.gems -= cost
        save.upgrades[u.ordinal]++
        save.save()
        sfx.play(Sfx.S.POWER)
    }

    /** Map cards: 5 per row, one row per region. */
    fun mapCard(i: Int): FloatArray {
        val cw = (W - 80f - 4 * 16f) / 5f
        val col = i % 5
        val row = i / 5
        val l = 40f + col * (cw + 16f)
        val t = 92f + row * 134f
        return floatArrayOf(l, t, l + cw, t + 122f)
    }

    // garage layout (shared by the renderer and touch handling)

    fun garageTabRect(i: Int): FloatArray {
        val l = 40f + i * 250f
        return floatArrayOf(l, 22f, l + 230f, 86f)
    }

    fun classRect(i: Int): FloatArray {
        val t = 104f + i * 100f
        return floatArrayOf(40f, t, 310f, t + 88f)
    }

    fun previewRect() = floatArrayOf(330f, 104f, W - 40f, 392f)

    fun variantRect(i: Int): FloatArray {
        val cw = (W - 370f - 4 * 14f) / 5f
        val l = 330f + i * (cw + 14f)
        return floatArrayOf(l, 404f, l + cw, 600f)
    }

    fun weaponRect(i: Int): FloatArray {
        val cw = (W - 80f - 4 * 14f) / 5f
        val col = i % 5
        val row = i / 5
        val l = 40f + col * (cw + 14f)
        val t = 104f + row * 138f
        return floatArrayOf(l, t, l + cw, t + 126f)
    }

    fun weaponPanelRect() = floatArrayOf(40f, 384f, W - 40f, 604f)

    /** Opens the garage on the vehicle and weapon in use. */
    private fun openGarage() {
        val v = save.vehicleType
        garageClass = v.cls
        garageVariant = v.variant
        garageWeapon = save.weaponType
        setState(State.GARAGE)
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
        if (state == State.STORY) {
            val skip = buttons().first()
            if (!skip.contains(x, y) && stateTime > 0.35f) {
                sfx.play(Sfx.S.CLICK)
                nextStoryPage()
                return
            }
        }
        if (state == State.MAPS) {
            for (i in 0 until STAGES) if (inRect(mapCard(i), x, y)) {
                if (i + 1 <= maxStartStage()) {
                    sfx.play(Sfx.S.CLICK)
                    save.stageSel = i + 1
                    save.save()
                    loadStage(save.stageSel)
                    setState(State.MENU)
                }
                return
            }
        }
        if (state == State.GARAGE) {
            if (garageTab == 2) for (i in Upgrade.entries.indices) if (inRect(upgradeCard(i), x, y)) {
                buyUpgrade(Upgrade.entries[i]); return
            }
            for (i in 0..2) if (inRect(garageTabRect(i), x, y)) {
                sfx.play(Sfx.S.CLICK)
                garageTab = i
                return
            }
            if (garageTab == 0) {
                for (cl in VehicleClass.entries) if (inRect(classRect(cl.ordinal), x, y)) {
                    sfx.play(Sfx.S.CLICK)
                    garageClass = cl
                    // show the best version owned, or the next one to buy
                    garageVariant = (0 until Vehicles.PER_CLASS).firstOrNull { !save.ownsVehicle(Vehicles.of(cl, it).id) } ?: 0
                    if (save.vehicleType.cls == cl) garageVariant = save.vehicleType.variant
                    return
                }
                for (i in 0 until Vehicles.PER_CLASS) if (inRect(variantRect(i), x, y)) {
                    sfx.play(Sfx.S.CLICK)
                    garageVariant = i
                    return
                }
            } else {
                for (w in WeaponType.entries) if (inRect(weaponRect(w.ordinal), x, y)) {
                    sfx.play(Sfx.S.CLICK)
                    garageWeapon = w
                    return
                }
            }
        }
        val btn = buttons().firstOrNull { it.contains(x, y) } ?: return
        if (!btn.enabled) return
        sfx.play(Sfx.S.CLICK)
        when (btn.id) {
            "play" -> play()
            "garage" -> openGarage()
            "vehAct" -> pickVehicle(Vehicles.of(garageClass, garageVariant))
            "wpnAct" -> pickWeapon(garageWeapon)
            "wpnUp" -> upgradeWeapon(garageWeapon)
            "menu" -> if (state == State.VICTORY && save.storySeen and (1 shl Story.ENDING_BIT) == 0) {
                showStory(listOf(Story.ending)) {
                    save.storySeen = save.storySeen or (1 shl Story.ENDING_BIT)
                    goMenu()
                }
            } else {
                goMenu()
            }
            "story" -> showStory(Story.unlocked(save.storySeen, save.bestStage)) { setState(State.MENU) }
            "skip" -> afterStory()
            "reset" -> confirmReset = true
            "resetNo" -> confirmReset = false
            "resetYes" -> {
                confirmReset = false
                save.resetAll()
                save.stageSel = 1
                loadStage(1)
                showStory(Story.prologue) {
                    save.storySeen = save.storySeen or (1 shl Story.PROLOGUE_BIT)
                    save.save()
                    setState(State.MENU)
                }
            }
            "resume" -> setState(State.PLAYING)
            "quit" -> endRun("Bỏ cuộc")
            "maps" -> setState(State.MAPS)
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

    private fun pickVehicle(v: Vehicle) {
        if (!save.classUnlocked(v.cls)) return
        if (!save.ownsVehicle(v.id)) {
            if (save.money < v.cost) return
            save.money -= v.cost
            save.ownedVehicles = save.ownedVehicles or (1 shl v.id)
        }
        save.vehicle = v.id
        sfx.play(Sfx.S.COIN)
        save.save()
    }

    private fun pickWeapon(w: WeaponType) {
        if (!save.weaponUnlocked(w)) return
        if (!save.ownsWeapon(w.ordinal)) {
            if (save.money < w.cost) return
            save.money -= w.cost
            save.ownedWeapons = save.ownedWeapons or (1 shl w.ordinal)
        }
        save.weapon = w.ordinal
        sfx.play(Sfx.S.COIN)
        save.save()
    }

    private fun upgradeWeapon(w: WeaponType) {
        val lv = save.weaponLevel(w)
        if (!save.ownsWeapon(w.ordinal) || lv >= WeaponType.MAX_LEVEL) return
        val cost = w.upgradeCost(lv)
        if (save.money < cost) return
        save.money -= cost
        save.weaponLevels[w.ordinal]++
        sfx.play(Sfx.S.COIN)
        save.save()
    }

    // -------------------------------------------------------------- render

    fun render(c: Canvas) = renderer.render(c)
}
