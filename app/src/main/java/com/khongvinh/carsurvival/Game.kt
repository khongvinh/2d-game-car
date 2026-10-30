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
        /** Shown on the menu so the installed build is easy to check. */
        const val VERSION = "7.4"

        const val VIRTUAL_HEIGHT = 720f
        const val METER = 30f
        const val G = 900f
        const val STAGES = 20

        /** Each stage's road is split into 5 levels (màn), marked by checkpoints. */
        const val LEVELS_PER_STAGE = 5
        const val MAX_PER_HUNDRED = 9
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

    val buildings = ArrayList<Building>()
    val driver = Driver()

    /** The building the driver is inside (its rooms are drawn instead of the road), if any. */
    var inside: Building? = null
        private set
    val interior get() = inside?.interior

    /** The run of rooms after the drive (arrived at the station or the car broke down). */
    var rooms: RoomRun? = null
        private set
    val roomProjectiles = ArrayList<Projectile>()
    private val roomRules = RoomRules(this)

    /** Camp, defences, nights and the day actions (v7). */
    val campaign = Campaign(this)

    /** Fuel in the tank when the drive began (to know how much was burnt). */
    private var driveFuel0 = 0f

    /** Seconds until the rooms start after the car is lost (-1 = not counting). */
    private var roomsIn = -1f

    /** Where the player is: the driver when on foot, else the car. */
    val playerX get() = if (rooms != null) goalX else if (driver.out) inside?.x ?: driver.x else car.x

    /** Parts found this run (for the result screen). */
    var runParts = 0

    // perks of the skill tree for this run
    private var magnet = false
    private var regen = false
    private var scavenger = false

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

    private var harvest = false

    /** Money from monsters grows with the stage, so farming later stages pays more. */
    private val rewardMul get() = (1f + 0.12f * (stage - 1)) * (if (harvest) 1.25f else 1f)

    // garage browsing
    /** 0 vehicles, 1 weapons, 2 skill tree, 3 hand weapons. */
    var garageTab = 0

    /** Vehicle on show in the garage (index into [Vehicles.all]). */
    var showVeh = 0
    var garageWeapon = WeaponType.PISTOL
    var garageSide = SideWeapon.PISTOL
    var skillPage = 0
    var skillSel = 0

    // controls
    private val pointers = HashMap<Int, String>()
    private var gas = false
    private var brake = false
    private var leanBack = false
    private var leanFwd = false
    private var boostHeld = false
    private var jumpHeld = false
    private var wasBoosting = false

    private val renderer = Renderer(this)
    private val phys = CarPhysics()

    val distanceM get() = max(0f, playerX / METER)
    val goalM get() = goalX / METER

    init {
        campaign.load()
        sfx.enabled = true
        save.refreshMissions()
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
        val r = campaign.region
        val bit = 1 shl (r.ordinal + 1)
        if (save.storySeen and bit == 0) {
            showStory(listOf(Story.chapter(r))) {
                save.storySeen = save.storySeen or bit
                save.save()
                openCamp()
            }
        } else {
            openCamp()
        }
    }

    // --------------------------------------------------------------- camp

    /** The camp screen of the current day (also where every run comes back to). */
    fun openCamp() {
        rooms = null
        inside = null
        boss = null
        driver.out = false
        dyingT = 0f
        loadStage(campaign.zone)
        zombies.clear(); obstacles.clear(); items.clear(); debris.clear(); shots.clear()
        particles.clear(); texts.clear(); roomProjectiles.clear(); beams.clear()
        campaign.selSlot = -1
        save.save()
        if (save.distM >= Campaign.GOAL_M) {
            setState(State.VICTORY)
            return
        }
        setState(State.CAMP)
    }

    /** Called by [Campaign] when the night is won or lost. */
    fun nightOver() {
        save.bestStage = max(save.bestStage, min(STAGES, save.nights / 2))
        save.save()
        setState(State.CAMP)
    }

    /** Day action: drive the camp further along the road, as far as the fuel lasts. */
    private fun startDrive() {
        save.stageSel = campaign.zone
        save.vehicle = Vehicles.of(VehicleClass.entries[save.carTier.coerceIn(0, 4)], 0).id
        startRun()
        boss = null
        buildings.clear()
        stormX = -1e9f
        car.fuel = min(car.maxFuel, save.fuel.toFloat())
        driveFuel0 = car.fuel
        texts.clear()
        texts.add(FloatText("LÊN ĐƯỜNG! ⛽ ${car.fuel.toInt()}L", car.x + 300f, car.y - 160f, 0xFFFFD740.toInt()).also { it.life = 2.2f })
    }

    /** The drive is over (tank dry, flipped, wrecked, fell, or the leg's end): camp right here. */
    private fun endDrive() {
        flushDamage()
        val m = (max(car.x, if (driver.out) driver.x else 0f) / METER).toInt().coerceIn(0, goalM.toInt())
        val used = (driveFuel0 - car.fuel).toInt().coerceAtLeast(0)
        save.money += runMoney
        save.gems += runGems
        save.addStat(Stat.KILLS, kills)
        save.addStat(Stat.MONEY, runMoney)
        save.addStat(Stat.GEMS, runGems)
        campaign.driveDone(m, used)
        openCamp()
    }

    /** Leaving a run from the pause menu. */
    private fun quitRun() {
        if (rooms != null) {
            rooms = null
            campaign.expeditionDone(false)
            openCamp()
        } else {
            endDrive()
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

    fun stageGoalM(s: Int) = 1500f

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
        loadStage(save.stageSel.coerceIn(1, STAGES))
        val ct = save.vehicleType
        vehicle = ct
        val sk = save.skills
        val up = { st: SkillStat -> Skills.total(sk, st) }
        topSpeed = ct.topSpeed * (1f + 0.01f * up(SkillStat.ENGINE))
        engineAcc = ct.engineAcc * (1f + 0.012f * up(SkillStat.ENGINE))
        mu = ct.traction * (1f + 0.01f * up(SkillStat.GRIP))
        armor = min(0.75f, ct.totalArmor + 0.01f * up(SkillStat.ARMOR))
        ramMul = ct.ram * (if (Skills.has(sk, Perk.SPIKES)) 1.5f else 1f)
        weapon = save.weaponType
        wstats = weapon.stats(save.weaponLevel(weapon)).let {
            if (Skills.has(sk, Perk.FIRE_ROUNDS)) WeaponStats(it.damage * 1.25f, it.interval, it.range, it.pierce, it.splash) else it
        }
        harvest = Skills.has(sk, Perk.HARVEST)
        magnet = Skills.has(sk, Perk.MAGNET)
        regen = Skills.has(sk, Perk.REGEN)
        scavenger = Skills.has(sk, Perk.SCAVENGER)

        val c = car
        c.vx = 0f; c.vy = 0f; c.spin = 0f; c.angle = 0f
        c.throttle = 0f; c.leanIn = 0f; c.squat = 0f; c.slashT = 0f
        stuckX = 0f; stuckGasT = 0f
        c.maxHp = ct.maxHp + up(SkillStat.HP)
        c.hp = c.maxHp
        c.maxFuel = ct.fuelTime * (1f + 0.01f * up(SkillStat.FUEL))
        c.fuel = c.maxFuel
        c.maxBoost = ct.boostTime + up(SkillStat.BOOST)
        c.boost = c.maxBoost
        c.ammo = ct.ammo + up(SkillStat.AMMO)
        driver.out = false; driver.hp = driver.maxHp; driver.looting = null; driver.hurtT = 0f
        driver.shieldT = 0f; driver.cloneT = 0f; driver.atkT = 0f; driver.vy = 0f; driver.air = 0f
        driver.maxHp = 100f
        inside = null
        rooms = null
        roomsIn = -1f
        roomProjectiles.clear()
        joyId = -1
        c.shieldT = 0f; c.cloneT = 0f; c.hurtT = 0f; c.flipT = 0f; c.stuckT = 0f; c.fireCd = 0f
        c.aim = 0f

        zombies.clear(); obstacles.clear(); debris.clear(); items.clear(); buildings.clear()
        shots.clear(); particles.clear(); texts.clear(); beams.clear()
        bossRocks.clear()
        bossKilled = false
        boss = if (stage % 5 == 0) Boss(stage / 5, goalX - 520f, terrain.heightAt(goalX - 520f)) else null
        populate()
        placeBunkers()
        placeBuildings()

        stormX = -1500f
        phase = 0
        phaseLeft = PHASE_TIME[0]
        lastBeep = 0
        kills = 0; smashed = 0; runMoney = 0; distMoney = 0; stageBonus = 0; runGems = 0; bunkerDamage = 0f; runParts = 0
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

    val levelInStage get() = levelAt(playerX) - (stage - 1) * LEVELS_PER_STAGE

    /** Zombies/obstacles per 100 m: 3 on level 1, +1 per level (arithmetic), capped. */
    /** Kept low on purpose: a few monsters at a time, never a swarm. */
    fun perHundredForLevel(level: Int): Int {
        val st = (level - 1) / LEVELS_PER_STAGE + 1
        val section = (level - 1) % LEVELS_PER_STAGE
        return min(MAX_PER_HUNDRED, 2 + section + st / 5)
    }
    fun perHundredFor(s: Int) = perHundredForLevel((s - 1) * LEVELS_PER_STAGE + 1)
    private var lastSection = 0

    private fun populate() {
        val rnd = Random(stage * 1000 + save.day)
        val seg = 100f * METER
        var start = 1200f
        while (start < goalX - 500f) {
            val count = perHundredForLevel(levelAt(start))
            // dense levels are mostly zombies so the road stays drivable
            val obstacleShare = 0.38f
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
                    if (rnd.nextFloat() < 0.12f) {
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

    /** Buildings to loot: 1 on stage 1 up to 4 on stage 20, on level ground by the road. */
    private fun placeBuildings() {
        val rnd = Random(stage * 97 + save.day * 13)
        val n = 1 + stage / 6
        val from = 1200f
        val to = goalX - (if (boss != null) 3300f else 900f)
        if (to <= from) return
        val span = (to - from) / n
        for (k in 0 until n) {
            var tries = 0
            while (tries++ < 16) {
                val x = from + span * k + rnd.nextFloat() * span * 0.8f
                if (terrain.covered(x, 300f) || terrain.onBridge(x) || terrain.onBridge(x + 200f)) continue
                if (abs(terrain.heightAt(x - 140f) - terrain.heightAt(x + 140f)) > 70f || terrain.awkward(x)) continue
                if (obstacles.any { abs(it.x - x) < 420f }) continue
                // the garage kind is where car parts turn up most
                buildings.add(Building(x, rnd.nextInt(5)))
                break
            }
        }
        // keep the doorstep clear
        for (b in buildings) zombies.removeAll { abs(it.x - b.x) < 250f }
    }

    /** The building whose door the car (or the driver) is at, if it is still unlooted. */
    fun doorNear(x: Float, reach: Float) = buildings.firstOrNull { !it.looted && abs(it.x - x) < reach }

    /** Get out (when parked by a building) or back in. */
    fun canLeaveCar() = !driver.out && !wrecked && dyingT <= 0f && abs(car.forwardSpeed) < 80f

    private fun leaveCar() {
        val d = driver
        d.out = true
        d.x = car.x - 20f
        d.y = terrain.heightAt(d.x)
        d.vy = -300f
        d.face = 1f
        d.looting = null
        pointers.clear()
        updateControls()
        sfx.play(Sfx.S.CLICK)
    }

    private fun enterCar() {
        val c = car
        if (cos(c.angle) < 0.5f) {
            // the driver heaves an overturned car back onto its wheels
            c.angle = 0f
            c.spin = 0f
            c.vx = 0f
            c.vy = 0f
            c.y = terrain.heightAt(c.x) - Car.WHEEL_Y - Car.WHEEL_R
        }
        c.flipT = 0f
        driver.out = false
        driver.looting = null
        pointers.clear()
        updateControls()
        sfx.play(Sfx.S.GO, 0.5f)
    }

    /** What the action button does on foot: go into a building, search a chest, leave, or get back in. */
    fun driverAction(): String? {
        if (!driver.out) return null
        val inn = interior
        if (inn != null) {
            if (inn.chests.any { it.openT > 0f && !it.opened }) return "busy"
            if (inn.chestNear(driver.x, driver.y) != null) return "chest"
            if (abs(driver.x - inn.exitX) < 120f) return "leave"
            return null
        }
        return when {
            doorNear(driver.x, 110f) != null -> "enter"
            !wrecked && abs(driver.x - car.x) < 130f -> "car"
            else -> null
        }
    }

    /** Through the door: the road is left behind and the rooms take the screen. */
    private fun enterBuilding(b: Building) {
        val inn = b.interior ?: Interior(b, stage, region).also { b.interior = it }
        inside = b
        val d = driver
        d.x = inn.exitX + 60f
        d.y = 0f
        d.vy = 0f
        d.face = 1f
        inn.camX = d.x - viewW * 0.45f
        particles.clear()
        texts.clear()
        pointers.clear()
        updateControls()
        texts.add(FloatText(Building.NAMES[b.kind], d.x + 300f, -330f, 0xFFFFD740.toInt()).also { it.life = 1.8f; it.size = 52f })
        sfx.play(Sfx.S.WOOD, 0.7f)
    }

    private fun leaveBuilding() {
        val b = inside ?: return
        inside = null
        val d = driver
        d.x = b.x
        d.y = terrain.heightAt(b.x)
        d.vy = 0f
        particles.clear()
        texts.clear()
        pointers.clear()
        updateControls()
        if (b.interior?.cleared == true) {
            b.looted = true
            save.addStat(Stat.HOUSES, 1)
            texts.add(FloatText("ĐÃ LỤC SẠCH!", b.x, d.y - 200f, 0xFF69F0AE.toInt()).also { it.life = 1.8f })
        }
        sfx.play(Sfx.S.WOOD, 0.7f)
    }

    /** Hurts the driver on foot; a shield bubble blocks everything but the storm. */
    internal fun hurtDriver(amount: Float, storm: Boolean = false) {
        val d = driver
        if (amount <= 0f || dyingT > 0f) return
        if (d.shieldT > 0f && !storm) return
        d.hp -= amount
        d.hurtT = 0.25f
        d.looting = null
        interior?.chests?.forEach { if (!it.opened) it.openT = 0f }
        if (Random.nextFloat() < 0.3f) bleed(d.x, d.y - 50f, 2)
        if (d.hp <= 0f && d.cloneT > 0f) {
            // the clone pickup: back on the feet once
            d.cloneT = 0f
            d.hp = d.maxHp * 0.6f
            if (storm) {
                if (inside != null) leaveBuilding()
                d.x = max(d.x, stormX + 600f)
                d.y = terrain.heightAt(d.x)
            }
            burst(d.x, d.y - 50f, ItemType.CLONE.color, 24)
            texts.add(FloatText("BẢN SAO HỒI SINH!", d.x, d.y - 160f, 0xFF64FFDA.toInt()))
            sfx.play(Sfx.S.CLONE)
        }
    }

    private fun updateDriver(dt: Float) {
        val d = driver
        if (d.hurtT > 0f) d.hurtT -= dt
        if (d.atkT > 0f) d.atkT -= dt
        // walking
        d.dir = (if (leanFwd) 1f else 0f) - (if (leanBack) 1f else 0f)
        if (d.dir != 0f) {
            d.face = d.dir
            d.x += d.dir * 240f * dt
            d.walk += dt * 10f
        }
        val oldX = d.x - d.dir * 240f * dt
        // jumping and falling: the ground is the road or the top of a crate / wreck
        fun support(x: Float): Float {
            var top = terrain.heightAt(x)
            for (o in obstacles) if (o.alive && abs(x - o.x) < o.type.w / 2 + 6f) {
                val ot = o.y - o.type.h
                if (d.y <= ot + 8f) top = min(top, ot)
            }
            return top
        }
        if (jumpHeld && d.air <= 0.5f && d.vy >= 0f) {
            d.vy = -620f
            d.air = 1f
            sfx.play(Sfx.S.CLICK, 0.4f)
        }
        val feet0 = d.y
        var feet = feet0 + d.vy * dt
        d.vy += G * dt
        val ground = support(d.x)
        if (feet >= ground) {
            feet = ground
            d.vy = 0f
        }
        // crates, wrecks and pillboxes block the way unless jumped over
        for (o in obstacles) if (o.alive && abs(d.x - o.x) < o.type.w / 2 + 12f && abs(oldX - o.x) >= o.type.w / 2 + 12f && feet > o.y - o.type.h + 4f) d.x = oldX
        val bb = boss
        if (bb != null && bb.alive && d.x > goalX - 140f) d.x = goalX - 140f
        val g2 = support(d.x)
        if (feet > g2) feet = g2
        d.y = feet
        d.air = max(0f, terrain.heightAt(d.x) - d.y)
        driverWeapon(dt, zombies, shots, inside = false)
        // the storm swallows people on foot too
        if (d.x < stormX) {
            hurtDriver(45f * dt, storm = true)
            stormHurt = 0.2f
            if (Random.nextFloat() < 0.5f) particles.add(Particle(d.x + rnd(-30f, 30f), d.y - rnd(10f, 90f), 0f, -100f, 0.5f, 0.5f, 7f, 0xFFE040FB.toInt()))
        }
        if (d.y > terrain.heightAt(d.x) + 5f) d.y = terrain.heightAt(d.x)
        if (d.hp <= 0f) driverDown(if (d.x < stormX) "Tài xế bị vòng bo nuốt chửng!" else "Tài xế bị quái vật hạ gục!")
    }

    /** Inside a building: only the storm keeps moving outside. */
    private fun updateInside(inn: Interior, dt: Float) {
        val d = driver
        val b = inside ?: return
        updateStorm(dt)
        if (stormHurt > 0f) stormHurt -= dt
        if (d.shieldT > 0f) d.shieldT -= dt
        if (d.cloneT > 0f) d.cloneT -= dt
        // the parked car still burns if the storm rolls over it
        val c = car
        if (!wrecked && c.x - Car.HALF_LEN < stormX) {
            c.hp -= 80f * dt
            if (c.hp <= 0f) {
                c.hp = 0f
                wrecked = true
            }
        }
        inn.update(this, dt, leanBack, leanFwd, jumpHeld)
        val stormIn = stormX > b.x - Building.W / 2
        if (stormIn) {
            hurtDriver(45f * dt, storm = true)
            stormHurt = 0.2f
            if (Random.nextFloat() < 0.6f) particles.add(Particle(d.x + rnd(-200f, 200f), d.y - rnd(10f, 300f), rnd(-40f, 40f), -80f, 0.6f, 0.6f, 8f, 0xFFE040FB.toInt()))
        }
        updateEffects(dt)
        if (d.hp <= 0f) driverDown(if (stormIn) "Tài xế bị vòng bo nuốt chửng trong nhà!" else "Tài xế bị zombie trong nhà hạ gục!")
    }

    /**
     * The hand weapon on foot: aims at the nearest monster (on the road also at
     * blocking obstacles and the boss) and attacks by itself.
     */
    internal fun driverWeapon(dt: Float, zs: List<Zombie>, out: MutableList<Projectile>, inside: Boolean) {
        val d = driver
        val w = save.sideWeapon
        d.fireCd -= dt
        val inn = interior
        val reach = if (w.melee) w.range + 30f else w.range
        var best = reach
        var tx = 0f
        var ty = 0f
        for (z in zs) {
            if (!z.alive) continue
            val dd = abs(z.x - d.x)
            if (dd < best) {
                best = dd
                tx = z.x
                ty = if (inside) -z.type.h * 0.55f else z.y - z.fly - z.type.h * 0.55f
            }
        }
        val bb = boss
        if (!inside && best >= reach) {
            // nothing alive near: hit what blocks the way
            for (o in obstacles) {
                if (!o.alive) continue
                val dd = abs(o.x - d.x) - (if (w.melee) o.type.w / 2 else 0f)
                if (dd < min(reach, 520f) && dd < best && sign(o.x - d.x) == d.face) {
                    best = dd; tx = o.x; ty = o.y - o.type.h * 0.5f
                }
            }
            bb?.let { if (it.alive && it.engaged && abs(it.x - d.x) - (if (w.melee) it.w / 2 else 0f) < (if (w.melee) reach else 900f)) { best = 0f; tx = it.x; ty = it.cy } }
        }
        val gx = d.x
        val gy = d.y - 58f
        if (best >= reach) {
            if (d.atkT <= 0f) d.aim = if (d.face > 0f) 0f else PI.toFloat()
            return
        }
        d.face = if (tx >= d.x) 1f else -1f
        d.aim = atan2(ty - gy, tx - gx)
        val busy = inn != null && inn.chests.any { it.openT > 0f && !it.opened }
        if (d.fireCd > 0f || busy) return
        d.fireCd = w.interval
        if (w.melee) {
            d.atkT = 0.25f
            val f = d.face
            sfx.play(Sfx.S.HIT, 0.5f)
            for (z in zs) {
                if (!z.alive) continue
                val dx = (z.x - d.x) * f
                if (dx < -20f || dx > w.range + z.type.w / 2) continue
                if (inside) {
                    inn?.hit(this, z, w.damage, f * w.knock * 2f)
                } else {
                    z.hp -= w.damage
                    bleed(z.x, z.y - z.fly - z.type.h * 0.5f, 5)
                    if (z.hp <= 0f) smashZombie(z, f * (200f + w.knock), -260f) else z.x += f * w.knock * 0.25f
                }
            }
            if (!inside) {
                for (o in obstacles) {
                    if (!o.alive) continue
                    val dx = (o.x - d.x) * f
                    if (dx < -20f || dx > w.range + o.type.w / 2 || o.y - o.type.h > d.y + 10f) continue
                    if (o.type == ObstacleType.BARREL) {
                        barrelBlast(o)
                    } else {
                        o.hp -= w.damage
                        o.flash = 0.08f
                        o.barT = 3f
                        sparks(o.x - f * o.type.w / 2, d.y - 50f)
                        if (o.hp <= 0f) breakObstacle(o, f * 200f)
                    }
                }
                if (bb != null && bb.alive && (bb.x - d.x) * f in -20f..(w.range + bb.w / 2)) hurtBoss(bb, w.damage)
            }
            return
        }
        // thrown / fired: arcing weapons aim a little high to drop onto the target
        val dist = hypot(tx - gx, ty - gy)
        val lob = if (w.gravity > 0f) w.gravity * dist / (w.speed * w.speed) * 0.5f else 0f
        for (k in 0 until w.pellets) {
            val off = if (w.pellets > 1) w.spread * (k - (w.pellets - 1) / 2f) / ((w.pellets - 1) / 2f) else 0f
            var a = d.aim + off + rnd(-0.03f, 0.03f)
            a -= lob * cos(a).let { if (it >= 0f) 1f else -1f }
            out.add(Projectile(WeaponType.PISTOL, gx, gy, cos(a) * w.speed, sin(a) * w.speed, w.damage, w.pierce, 0f).also {
                it.life = w.range / w.speed + 0.15f
                it.side = w
                it.grav = w.gravity
            })
        }
        particles.add(Particle(gx + cos(d.aim) * 26f, gy + sin(d.aim) * 26f, 0f, 0f, 0.06f, 0.06f, 9f, 0xFFFFE082.toInt()))
        sfx.play(if (w == SideWeapon.BOW || w == SideWeapon.DART) Sfx.S.BOW else Sfx.S.SHOOT, 0.35f)
    }

    /** A chest inside is open: money and a share of what the building holds. */
    internal fun openChest(inn: Interior, ch: Chest) {
        val c = car
        val b = inn.b
        val tx = ch.x
        val ty = -ch.y - 150f
        val cash = ((25 + 10 * stage) * rnd(0.7f, 1.3f) * (if (b.kind == 1) 1.5f else 1f) * (if (ch.kind == 5) 3f else 1f)).toInt()
        addMoney(cash, tx, ty)
        var line = 0
        fun note(s: String, color: Int) {
            texts.add(FloatText(s, tx, ty - 60f - line * 56f, color).also { it.life = 2.4f; it.size = 30f })
            line++
        }
        val partChance = (if (b.kind == 3) 0.45f else 0.22f) + (if (ch.kind == 2) 0.2f else 0f) + (if (scavenger) 0.15f else 0f)
        val target = save.partTarget()
        if (target != null && Random.nextFloat() < partChance) {
            save.parts[target.id]++
            runParts++
            note("🔧 MẢNH ${target.title.uppercase()} (${save.parts[target.id]}/${SaveData.PARTS_NEEDED})", 0xFF80D8FF.toInt())
        }
        if (ch.kind == 5 || Random.nextFloat() < 0.35f) {
            val gm = if (ch.kind == 5) 4 + Random.nextInt(5) else 2 + Random.nextInt(3)
            runGems += gm
            note("+$gm♦", 0xFFFF5252.toInt())
        }
        val firstHere = inn.chests.count { it.opened } == 1
        if ((b.kind == 2 && firstHere) || Random.nextFloat() < 0.15f) {
            c.fuel = min(c.maxFuel, c.fuel + c.maxFuel * 0.35f)
            note("+XĂNG", 0xFFFF8A80.toInt())
        }
        if (ch.kind == 3 || ch.kind == 4 || (b.kind == 4 && firstHere) || Random.nextFloat() < 0.15f) {
            val full = ch.kind == 4 || b.kind == 4
            driver.hp = min(driver.maxHp, driver.hp + driver.maxHp * (if (full) 1f else 0.4f))
            if (!wrecked) c.hp = min(c.maxHp, c.hp + c.maxHp * (if (full) 0.3f else 0.15f))
            note("+MÁU", 0xFF69F0AE.toInt())
        }
        if (Random.nextFloat() < 0.25f && weapon != WeaponType.KNIFE) {
            c.ammo += 15f
            note("+15 ĐẠN", 0xFFFFCC80.toInt())
        }
        // now and then a hand weapon nobody had found yet
        val finds = SideWeapon.entries.filter { !save.ownsSide(it) && save.sideUnlocked(it) }
        if (finds.isNotEmpty() && Random.nextFloat() < (if (ch.kind == 5) 0.25f else 0.06f)) {
            val w = finds.random()
            save.ownedSide = save.ownedSide or (1 shl w.ordinal)
            note("🗡 TÌM ĐƯỢC ${w.title.uppercase()}!", 0xFFFFD740.toInt())
        }
        if (inn.cleared) {
            runGems += 2
            note("ĐÃ LỤC SẠCH! +2♦", 0xFF69F0AE.toInt())
        }
        burst(tx, ty + 100f, 0xFFFFD740.toInt(), 20)
        sfx.play(Sfx.S.WIN, 0.7f)
    }

    // ------------------------------------------------------------- rooms

    /** Day action: on foot through a few warehouse rooms to break crates for parts, scrap and fuel. */
    private fun startExpedition() {
        val arrived = true
        val note: String? = "LỤC SOÁT KHO HÀNG"
        noFuelT = 0f
        inside = null
        loadStage(campaign.zone)
        kills = 0; runMoney = 0; runGems = 0
        val run = RoomRun(campaign.zone, campaign.region, save.day * 977 + save.nights * 31, total = 6, bossLast = save.day % 3 == 0)
        rooms = run
        setState(State.PLAYING)
        val d = driver
        d.out = true
        d.maxHp = 100f + stage * 4f + Skills.total(save.skills, SkillStat.HP) * 0.5f
        d.hp = if (arrived) d.maxHp else max(d.hp / 100f, 0.7f) * d.maxHp
        d.x = run.room.entryX
        d.y = 0f
        d.vy = 0f
        d.face = 1f
        d.shieldT = 0f
        run.camX = d.x - viewW * 0.42f
        roomProjectiles.clear()
        particles.clear()
        texts.clear()
        pointers.clear()
        joyId = -1
        updateControls()
        intense = true
        // the merchant waits at the door; reaching the station earns a second box
        run.merchantQueued = 1
        texts.add(FloatText(note ?: if (arrived) "TỚI TRẠM! VÀO KHO HÀNG" else "XE HỎNG! CHẠY VÀO KHO HÀNG", d.x + 400f, -300f, 0xFFFFD740.toInt()).also { it.size = 46f; it.life = 2.2f })
        sfx.play(Sfx.S.GO)
    }

    private fun updateRooms(run: RoomRun, dt: Float) {
        val d = driver
        roomRules.update(run, dt, leanBack, leanFwd, jumpHeld)
        updateEffects(dt)
        if (d.hp <= 0f) {
            driverDown("Gục ở phòng ${run.index}/${run.total}")
            return
        }
        val rm = run.room
        if (rm.open && d.x > rm.exitX - 40f && d.y > -20f) {
            if (run.index >= run.total) {
                save.addStat(Stat.ROOMS, 1)
                save.money += runMoney
                save.addStat(Stat.MONEY, runMoney)
                save.addStat(Stat.KILLS, kills)
                rooms = null
                campaign.expeditionDone(true)
                openCamp()
                return
            }
            save.addStat(Stat.ROOMS, 1)
            if (run.index in RoomRun.MERCHANT_AFTER) run.merchantQueued++
            run.nextRoom()
            d.x = run.room.entryX
            d.y = 0f
            d.vy = 0f
            run.camX = d.x - viewW * 0.42f
            roomProjectiles.clear()
            particles.clear()
            texts.clear()
            sfx.play(Sfx.S.WOOD, 0.6f)
        }
        checkPicks(run)
    }

    /** Opens the next waiting choice: level-up power, boss upgrade, merchant box. */
    private fun checkPicks(run: RoomRun) {
        if (state != State.PLAYING || dyingT > 0f) return
        when {
            run.benchQueued -> {
                run.benchQueued = false
                run.pickKind = 3
                run.choices = listOf(Power.VITAL, Power.DAMAGE, Power.DUAL)
            }
            run.merchantQueued > 0 -> {
                run.merchantQueued--
                run.pickKind = 1
                run.choices = run.roll()
            }
            run.upgradeQueued > 0 -> {
                run.upgradeQueued--
                run.pickKind = 2
                run.choices = listOf(Power.DAMAGE, Power.VITAL).filter { run.lv(it) < it.max }
            }
            run.levelUps > 0 -> {
                run.levelUps--
                run.pickKind = 0
                run.choices = run.roll()
            }
            else -> return
        }
        if (run.choices.isEmpty()) return
        pointers.clear()
        joyId = -1
        updateControls()
        sfx.play(Sfx.S.POWER, 0.7f)
        setState(State.PICK)
    }

    /** Card [i] of the current choice. */
    fun pickCard(i: Int, n: Int): FloatArray {
        val w = 270f
        val gap = 34f
        val l = W / 2 - (n * w + (n - 1) * gap) / 2 + i * (w + gap)
        return floatArrayOf(l, 200f, l + w, 560f)
    }

    /** Workbench options in a rest room: icon, title, description. */
    fun benchOption(i: Int): Triple<String, String, String> = when (i) {
        0 -> Triple("❤", "Băng bó", "Hồi 50% máu")
        1 -> Triple("🔧", "Mài vũ khí", if ((rooms?.lv(Power.DAMAGE) ?: 0) < Power.DAMAGE.max) "+1 cấp Sát thương+" else "+1 cấp Tốc bắn+")
        else -> nextSide()?.let { Triple("🔄", "Đổi vũ khí", "Cầm ${it.title}") } ?: Triple("➕", "Lắp băng đạn", "+1 cấp Đạn kép")
    }

    /** The next hand weapon the player owns, after the one carried. */
    private fun nextSide(): SideWeapon? {
        val all = SideWeapon.entries
        for (k in 1 until all.size) {
            val w = all[(save.side + k) % all.size]
            if (save.ownsSide(w)) return w
        }
        return null
    }

    private fun chooseBench(run: RoomRun, i: Int) {
        val d = driver
        val t = benchOption(i)
        when (i) {
            0 -> d.hp = min(d.maxHp, d.hp + d.maxHp * 0.5f)
            1 -> if (run.lv(Power.DAMAGE) < Power.DAMAGE.max) run.powers[Power.DAMAGE.ordinal]++ else run.powers[Power.RATE.ordinal]++
            else -> {
                val w = nextSide()
                if (w != null) save.side = w.ordinal else run.powers[Power.DUAL.ordinal]++
            }
        }
        texts.add(FloatText("${t.first} ${t.second.uppercase()}", d.x, d.y - 170f, 0xFF80D8FF.toInt()).also { it.size = 38f; it.life = 1.8f })
        run.pickKind = -1
        sfx.play(Sfx.S.COIN)
        setState(State.PLAYING)
    }

    private fun choosePick(i: Int) {
        val run = rooms ?: return
        if (run.pickKind == 3) {
            chooseBench(run, i)
            return
        }
        val p = run.choices.getOrNull(i) ?: return
        run.powers[p.ordinal]++
        if (p == Power.VITAL) {
            driver.maxHp += 25f
            driver.hp = min(driver.maxHp, driver.hp + driver.maxHp * 0.25f)
        }
        texts.add(FloatText("${p.icon} ${p.title.uppercase()}${if (run.lv(p) > 1) " CẤP ${run.lv(p)}" else ""}", driver.x, driver.y - 170f, 0xFF80D8FF.toInt()).also { it.size = 38f; it.life = 1.8f })
        run.pickKind = -1
        sfx.play(Sfx.S.COIN)
        setState(State.PLAYING)
    }

    // ------------------------------------------------------------ joystick

    /** Floating stick on the left half while on foot. */
    var joyId = -1
        private set
    var joyX = 0f
        private set
    var joyY = 0f
        private set
    var joyDx = 0f
        private set
    var joyDy = 0f
        private set

    // small hooks for the building interiors
    internal val scavengerPerk get() = scavenger
    internal val monsterRewardMul get() = rewardMul
    internal fun giveMoney(n: Int, x: Float, y: Float) = addMoney(n, x, y)
    internal fun bleedAt(x: Float, y: Float, n: Int) = bleed(x, y, n)
    internal fun sparksAt(x: Float, y: Float) = sparks(x, y)
    internal fun burstAt(x: Float, y: Float, color: Int, n: Int) = burst(x, y, color, n)
    internal fun explodeAt(x: Float, y: Float, n: Int) = explode(x, y, 0xFFFF6D00.toInt(), n)
    internal fun giveMoneyQuiet(n: Int) {
        runMoney += n
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

    /** Experience for the player level: distance, kills, a stage clear. Level-ups pay gems. */
    private fun gainXp(finished: Boolean) {
        val before = Rank.of(save.xp).first
        save.xp += (maxDist / 25f).toInt() + kills + (if (finished) 30 * stage else 0)
        val after = Rank.of(save.xp).first
        if (after > before) {
            val bonus = 10 * (after - before)
            save.gems += bonus
            levelUp = after
            levelGems = bonus
        }
    }

    /** Set when the last run raised the player level (for the result screen). */
    var levelUp = 0
    var levelGems = 0

    /** Score and achievements (🏆) for the result screens. */
    private fun finishStats(finished: Boolean) {
        save.addStat(Stat.KILLS, kills)
        save.addStat(Stat.MONEY, distMoney + runMoney + stageBonus)
        save.addStat(Stat.GEMS, runGems)
        save.addStat(Stat.METERS, maxDist.toInt())
        if (finished) save.addStat(Stat.STAGES, 1)
        levelUp = 0
        gainXp(finished)
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
        if (levelUp > 0) achievements.add("⭐ Lên cấp $levelUp (+$levelGems♦)")
        if (runParts > 0) achievements.add("🔧 Tìm được $runParts mảnh xe")
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
        State.PICK, State.SIEGE -> true
        State.WORKSHOP -> {
            setState(State.CAMP); true
        }
        State.SOUND -> {
            save.save(); setState(soundBack); true
        }
        State.PAUSED -> {
            quitRun(); true
        }
        else -> {
            goMenu(); true
        }
    }

    private fun goMenu() {
        save.refreshMissions()
        save.save()
        inside = null
        rooms = null
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
        music.enabled = save.volMusic > 0
        music.master = save.volMusic / 100f
        sfx.fxVol = save.volSfx / 100f
        sfx.engineMaster = save.volEngine / 100f
        when (state) {
            State.MENU, State.MAPS, State.GARAGE, State.STORY, State.LAYOUT, State.MISSIONS, State.CAMP, State.WORKSHOP -> music.play(Music.Track.CHILL, 0.8f)
            State.SIEGE -> music.play(Music.Track.INTENSE, 0.6f)
            State.SOUND -> music.play(if (soundBack == State.PAUSED && intense) Music.Track.INTENSE else Music.Track.CHILL, if (soundBack == State.PAUSED) 0.4f else 1f)
            // kept low in a run so the engine and the action carry the sound
            State.PLAYING -> music.play(if (intense) Music.Track.INTENSE else Music.Track.CHILL, 0.4f)
            State.PAUSED, State.PICK -> music.play(if (intense) Music.Track.INTENSE else Music.Track.CHILL, 0.3f)
            State.RESULT -> music.play(Music.Track.CHILL, 0.6f)
            State.STAGE_DONE, State.VICTORY -> music.play(Music.Track.VICTORY, 1f)
        }
        music.update(dt)
        updateEngineSound(dt)
        when (state) {
            State.PLAYING -> updatePlay(dt)
            State.PAUSED, State.SOUND, State.PICK -> {}
            State.CAMP -> {
                if (campaign.bannerT > 0f) campaign.bannerT -= dt
                updateEffects(dt)
            }
            State.SIEGE -> {
                campaign.updateSiege(dt)
                updateEffects(dt)
            }
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
        if (state == State.SOUND && dragVol == 2) {
            // let the engine slider be heard while it moves
            sfx.engine(true, 1.1f, 0.8f)
            return
        }
        if (state != State.PLAYING || dyingT > 0f || wrecked || driver.out) {
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
        val vol = 0.45f + 0.4f * max(0f, c.throttle) + (if (c.boosting) 0.1f else 0f)
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
            if (inside == null && rooms == null) {
                burn(dt, true)
                updateDebris(dt)
                updateCamera(dt)
            }
            updateEffects(dt)
            if (dyingT <= 0f) {
                if (rooms != null) {
                    // carried back to camp; the money picked up is kept, half the loot is lost
                    save.money += runMoney
                    rooms = null
                    campaign.expeditionDone(false)
                    openCamp()
                } else {
                    endDrive()
                }
            }
            return
        }
        rooms?.let {
            updateRooms(it, dt)
            return
        }
        interior?.let {
            updateInside(it, dt)
            return
        }
        // the car is gone: on foot to the nearest shelter, the rooms begin
        if (wrecked && roomsIn < 0f) roomsIn = 1.8f
        if (roomsIn > 0f) {
            roomsIn -= dt
            if (roomsIn <= 0f) {
                endDrive()
                return
            }
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
        if (driver.shieldT > 0f) driver.shieldT -= dt
        if (driver.cloneT > 0f) driver.cloneT -= dt
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

        if (regen && c.hp > 0f) c.hp = min(c.maxHp, c.hp + 1.5f * dt)
        if (driver.out) updateDriver(dt)
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
            if (c.hp <= 0f && !wrecked && !tryRevive(true)) abandonCar(true)
        }

        // flipped / out of fuel
        val speed = hypot(c.vx, c.vy)
        val b0 = boss
        // upside down, or standing on its nose/tail and not moving
        val wheelsOff = !c.grounded[0] && !c.grounded[1]
        if ((cos(c.angle) < 0.3f || (wheelsOff && cos(c.angle) < 0.6f)) && speed < 120f) {
            c.flipT += dt
            if (c.flipT > 3f && !driver.out && !wrecked) {
                // stuck on its roof: on foot into the warehouse
                c.flipT = 0f
                texts.add(FloatText("LẬT XE!", c.x, c.y - 150f, 0xFFFFAB40.toInt()))
                endDrive()
                return
            }
        } else {
            c.flipT = 0f
        }
        // out of fuel and rolled to a stop: on foot into the warehouse
        if (c.fuel <= 0f && !driver.out && speed < 40f) {
            noFuelT += dt
            if (noFuelT > 1.5f) {
                endDrive()
                return
            }
        } else {
            noFuelT = 0f
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
        if (c.hp <= 0f && !wrecked && !tryRevive(false)) abandonCar(true)
        if (c.y > terrain.heightAt(c.x) + 400f && !tryRevive(false)) {
            endDrive(); return
        }

        val b = boss
        if (b != null && b.alive && c.x > goalX - 140f) {
            // the station gate stays shut until the boss is down
            c.x = goalX - 140f
            if (c.vx > 0f) c.vx = -c.vx * 0.3f
        }
        if ((c.x >= goalX && !driver.out) || (driver.out && inside == null && driver.x >= goalX)) {
            // the end of this leg of the road: make camp here
            endDrive(); return
        }

        updateCamera(dt)
    }

    /**
     * The car is lost (destroyed, or burnt by the storm): it blows up and the
     * driver jumps clear to go on with the pistol.
     */
    private fun abandonCar(explodeIt: Boolean) {
        val c = car
        flushDamage()
        wrecked = true
        c.hp = 0f
        c.boosting = false
        if (explodeIt) {
            explode(c.x, c.y - 10f, 0xFFFF6D00.toInt(), 80)
            explode(c.x + 40f, c.y - 30f, 0xFFFFEA00.toInt(), 40)
            repeat(7) {
                debris.add(Debris(Debris.METAL, c.x + rnd(-60f, 60f), c.y - 20f, c.vx * 0.5f + rnd(-350f, 350f), -rnd(350f, 800f), 0f, rnd(-14f, 14f), rnd(24f, 50f), rnd(10f, 20f), vehicle.color))
            }
            c.vy -= 420f
            c.vx *= 0.4f
            shake = 0.8f
            hurtFlash = 0.5f
            texts.add(FloatText("BÙMMM!", c.x, c.y - 170f, 0xFFFFAB00.toInt()).also { it.size = 80f; it.life = 1.6f })
            sfx.play(Sfx.S.BOOM)
            sfx.play(Sfx.S.EXPLODE)
        }
        if (!driver.out) {
            leaveCar()
            driver.x = c.x - 90f
            texts.add(FloatText("NHẢY KHỎI XE!", c.x - 60f, c.y - 110f, 0xFF80D8FF.toInt()).also { it.life = 1.8f; it.size = 46f })
        }
    }

    /** The driver has fallen: a short beat, then the day ends. */
    private fun driverDown(reason: String) {
        dyingT = 1.6f
        dyingReason = reason
        pointers.clear()
        updateControls()
        bleed(driver.x, driver.y - 50f, 20)
        sfx.play(Sfx.S.LOSE, 0.6f)
    }

    private var noFuelT = 0f
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
        val tx = if (driver.out) driver.x - viewW * 0.45f else c.x - viewW * 0.32f + lookAhead
        val ty = (if (driver.out) driver.y - 40f else c.y) - viewH * 0.58f
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
        if (stormX < -1e8f) return
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
            dyingT > 0f || driver.out -> 0f
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
            val chaseX = if (driver.out) driver.x else c.x
            if (abs(dx) < 1300f) {
                z.x += sign(chaseX - z.x) * z.type.speed * dt
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
                val ty = if (driver.out) driver.y - 40f else c.y
                val tdx = if (driver.out) driver.x - z.x else dx
                var target = if (abs(tdx) < 320f) max(10f, ground - ty - 10f) else 150f + 35f * sin(z.phase * 0.3f)
                val roof = terrain.ceilingAt(z.x)
                if (!roof.isNaN()) target = min(target, ground - roof - z.type.h - 20f)
                z.fly += (target - z.fly) * min(1f, dt * 3f)
                z.y = ground - z.fly
            } else {
                z.y = ground
            }
            if (driver.out && abs(z.x - driver.x) < z.type.w / 2 + 16f && z.y - z.fly * 0.2f > driver.y - 90f) {
                // a monster has reached the driver
                val away = sign(z.x - driver.x).let { if (it == 0f) 1f else it }
                if (driver.shieldT > 0f) {
                    // the bubble throws monsters off and hurts them
                    z.hp -= 1.5f
                    sparks(driver.x + away * 30f, driver.y - 50f)
                    if (z.hp <= 0f || z.type.flying) smashZombie(z, away * 400f, -300f) else z.x = driver.x + away * (z.type.w / 2 + 70f)
                } else if (z.type.flying) {
                    smashZombie(z, rnd(-80f, 80f), -200f)
                    hurtDriver(8f)
                } else {
                    z.x = driver.x - sign(driver.x - z.x).let { if (it == 0f) 1f else it } * (z.type.w / 2 + 15f)
                    hurtDriver(16f * dt)
                }
                continue
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
        val tx = if (driver.out) driver.x else c.x
        val tyy = if (driver.out) driver.y - 40f else c.y - 20f
        val dx = tx - gx
        val dy = tyy - gy
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
        if (driver.out) {
            bossVsDriver(b, dt)
            return
        }
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

    /** With the driver on foot the boss turns on them: closes in, throws, and hits up close. */
    private fun bossVsDriver(b: Boss, dt: Float) {
        val d = driver
        val dx = d.x - b.x
        if (!b.engaged) {
            if (dx > -1500f) {
                b.engaged = true
                intense = true
                sfx.play(Sfx.S.ROAR)
            }
            return
        }
        val dir = if (dx >= 0f) 1f else -1f
        if (abs(dx) > 200f) b.x += dir * b.speed * dt
        b.x = b.x.coerceIn(goalX - 1150f, goalX - 260f)
        if (b.flying) b.lift += (120f - b.lift) * min(1f, dt * 2f)
        b.throwCd -= dt
        if (b.throwCd <= 0f && abs(dx) < 1100f) {
            b.throwCd = 2.4f - b.tier * 0.2f
            b.throwAnim = 0.4f
            val x0 = b.x
            val y0 = b.cy - b.h * 0.3f
            val kind = when (b.tier) { 2 -> 1; 4 -> 2; else -> 0 }
            if (kind == 2) {
                val a = atan2(d.y - 50f - y0, d.x - x0)
                bossRocks.add(BossRock(x0, y0, cos(a) * 560f, sin(a) * 560f, 2))
            } else {
                val t = (abs(d.x - x0) / 750f).coerceIn(0.6f, 1.4f)
                bossRocks.add(BossRock(x0, y0, (d.x - x0) / t, (d.y - 40f - y0) / t - 0.5f * G * t, kind))
            }
        }
        // up close it simply mauls the driver
        if (abs(dx) < b.w / 2 + 24f && (!b.flying || b.lift < 160f)) hurtDriver((14f + 4f * b.tier) * dt)
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
            save.addStat(Stat.BOSSES, 1)
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
                if (driver.out && abs(r.x - driver.x) < 20f && r.y > driver.y - 80f && r.y < driver.y) {
                    r.alive = false
                    hurtDriver(r.dmg * 0.8f)
                    sparks(r.x, r.y)
                } else if (hypot(r.x - c.x, r.y - c.y) < 70f) {
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
            if (driver.out && abs(r.x - driver.x) < 36f && r.y > driver.y - 100f && r.y < driver.y + 10f) {
                r.alive = false
                hurtDriver(12f + 3f * tier)
                explode(r.x, r.y, 0xFF8D6E63.toInt(), 14)
                sfx.play(Sfx.S.HIT, 0.7f)
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
        val px = if (driver.out) driver.x else c.x
        val py = if (driver.out) driver.y - 40f else c.y
        for (it in items) {
            if (!it.alive) continue
            if (magnet && abs(it.x - px) < 300f) {
                // the Magnet perk pulls loot in
                val d = max(1f, hypot(px - it.x, py - it.y))
                if (d < 300f) {
                    it.x += (px - it.x) / d * 520f * 0.016f
                    it.y += (py - it.y) / d * 520f * 0.016f
                }
            }
            if (abs(it.x - px) > 200f) continue
            if (hypot(it.x - px, it.y - py) < 95f) {
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
                // on foot it patches up the driver
                if (driver.out) driver.hp = min(driver.maxHp, driver.hp + driver.maxHp * 0.4f) else c.hp = min(c.maxHp, c.hp + c.maxHp * 0.3f)
                text("+MÁU", it, 0xFF69F0AE.toInt())
            }
            ItemType.SHIELD -> {
                if (driver.out) driver.shieldT = 6f else c.shieldT = 6f
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
                if (driver.out) driver.cloneT = 10f else c.cloneT = 10f
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
        // nobody at the gun while the driver is out (or the car is a wreck)
        if (driver.out || wrecked) return
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
            for (o in obstacles) {
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
            s.vy += s.grav * dt
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

    internal fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

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
    /** Movable control slots: brake, leanBack, leanFwd, boost, gas, exit. */
    private val slotIds = arrayOf("brake", "leanBack", "leanFwd", "boost", "gas", "exit")

    private fun slotDefault(i: Int): FloatArray = when (i) {
        0 -> floatArrayOf(24f, H - 360f, 184f, H - 210f)
        1 -> floatArrayOf(24f, H - 190f, 184f, H - 30f)
        2 -> floatArrayOf(204f, H - 190f, 364f, H - 30f)
        3 -> floatArrayOf(W - 384f, H - 190f, W - 224f, H - 30f)
        4 -> floatArrayOf(W - 204f, H - 260f, W - 24f, H - 30f)
        else -> floatArrayOf(W - 384f, H - 300f, W - 224f, H - 210f)
    }

    /** Where slot [i] sits now: its default rect moved to the saved centre, kept on screen. */
    fun slotRect(i: Int): FloatArray {
        val d = slotDefault(i)
        if (save.ctlX[i] < 0) return d
        val w = d[2] - d[0]
        val h = d[3] - d[1]
        val cx = (save.ctlX[i] / 1000f * W).coerceIn(w / 2 + 4f, W - w / 2 - 4f)
        val cy = (save.ctlY[i] / 1000f * H).coerceIn(h / 2 + 100f, H - h / 2 - 4f)
        return floatArrayOf(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    private fun slotButton(i: Int, id: String, label: String, color: Int): Button {
        val r = slotRect(i)
        return Button(id, label, r[0], r[1], r[2], r[3], color)
    }

    fun controls(): List<Button> = if (driver.out && rooms != null) listOf(
        slotButton(3, "jump", "NHẢY", 0x5529B6F6),
    ) else if (driver.out) listOf(
        slotButton(3, "jump", "NHẢY", 0x5529B6F6),
        slotButton(4, "act", when (driverAction()) {
            "car" -> "LÊN XE"
            "enter" -> if (doorNear(driver.x, 110f)?.interior != null) "VÀO LẠI" else "VÀO NHÀ"
            "chest" -> "LỤC"
            "busy" -> "ĐANG LỤC…"
            "leave" -> "RA NGOÀI"
            else -> "…"
        }, if (driverAction() != null) 0x55FFC107 else 0x33FFFFFF),
    ) else listOf(
        slotButton(0, "brake", "PHANH", 0x55FF5252),
        slotButton(1, "leanBack", "↺", 0x55FFFFFF),
        slotButton(2, "leanFwd", "↻", 0x55FFFFFF),
        slotButton(3, "boost", "🚀", 0x55FF9800),
        slotButton(4, "gas", "GA", 0x5543A047),
    ) + (if (canLeaveCar()) listOf(slotButton(5, "exit", "XUỐNG XE", 0x5500BCD4)) else emptyList())

    // ------------------------------------------------------- control layout

    /** Where to go back after editing the layout. */
    private var layoutBack = State.MENU
    private var dragSlot = -1
    private var dragId = -1

    private fun openLayout() {
        layoutBack = state
        setState(State.LAYOUT)
    }

    /** All six slots, shown with their driving labels while editing. */
    fun layoutButtons(): List<Button> = listOf(
        slotButton(0, "brake", "PHANH", 0x88FF5252.toInt()),
        slotButton(1, "leanBack", "↺ / ◀", 0x88FFFFFF.toInt()),
        slotButton(2, "leanFwd", "↻ / ▶", 0x88FFFFFF.toInt()),
        slotButton(3, "boost", "🚀 / NHẢY", 0x88FF9800.toInt()),
        slotButton(4, "gas", "GA / LỤC", 0x8843A047.toInt()),
        slotButton(5, "exit", "XUỐNG XE", 0x8800BCD4.toInt()),
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
            ) else {
                val mx = menuCenterX()
                val side = listOf(
                    "missions" to "📋 NHIỆM VỤ", "achieve" to "🏆 THÀNH TÍCH", "story" to "📖 CỐT TRUYỆN",
                    "soundMenu" to "🔊 ÂM THANH", "layout" to "🎮 PHÍM", "reset" to "↺ CHƠI LẠI",
                )
                side.mapIndexed { i, (id, label) ->
                    Button(id, label, W - 214f, 84f + i * 82f, W - 24f, 152f + i * 82f, if (id == "reset") 0xFF4E342E.toInt() else 0xFF37474F.toInt())
                } + listOf(
                    Button("info", "🏕 NGÀY ${save.day} • ${campaign.region.title.uppercase()}", mx - 300f, 84f, mx + 300f, 148f, 0xFF3E2723.toInt()),
                    Button("play", "VÀO TRẠI!", mx - 220f, H - 118f, mx + 220f, H - 26f, 0xFFF9A825.toInt()),
                )
            }
            State.MISSIONS -> {
                val l = ArrayList<Button>()
                val tabs = listOf("NHIỆM VỤ NGÀY", "THÀNH TÍCH", "XẾP HẠNG")
                for (i in tabs.indices) l.add(Button("mtab$i", tabs[i], 200f + i * 330f, 20f, 510f + i * 330f, 90f, if (missionsTab == i) 0xFFB71C1C.toInt() else grey))
                l.add(Button("menu", "QUAY LẠI", W - 260f, 20f, W - 24f, 90f, grey))
                when (missionsTab) {
                    0 -> for (i in 0 until 3) {
                        val r = missionRow(i)
                        val claimed = save.missionClaimed and (1 shl i) != 0
                        l.add(Button("claimM$i", if (claimed) "ĐÃ NHẬN" else "NHẬN ${save.missionGems(i)}♦", r[2] - 250f, r[1] + 22f, r[2] - 24f, r[3] - 22f, if (claimed) grey else 0xFFC62828.toInt(), !claimed && save.missionDone(i)))
                    }
                    1 -> for (i in Missions.achievements.indices) {
                        val r = achRow(i)
                        val claimed = save.achClaimed and (1 shl i) != 0
                        l.add(Button("claimA$i", if (claimed) "✔" else "${Missions.achievements[i].gems}♦", r[2] - 120f, r[1] + 8f, r[2] - 10f, r[3] - 8f, if (claimed) grey else 0xFFC62828.toInt(), !claimed && save.achDone(i)))
                    }
                }
                l
            }
            State.MAPS -> listOf(b("menu", "QUAY LẠI", W / 2, H - 84f, 360f, grey))
            State.GARAGE -> garageButtons(green, grey)
            State.PAUSED -> listOf(
                b("resume", "TIẾP TỤC", W / 2, H * 0.32f, 420f, green),
                b("quit", "BỎ CUỘC", W / 2, H * 0.32f + 104f, 420f, grey),
                Button("soundMenu", "🔊 CHỈNH ÂM THANH", W / 2 - 330f, H * 0.32f + 214f, W / 2 + 330f, H * 0.32f + 290f, 0xFF00897B.toInt()),
                Button("layout", "🎮 ĐỔI VỊ TRÍ PHÍM", W / 2 - 330f, H * 0.32f + 304f, W / 2 + 330f, H * 0.32f + 376f, 0xFF37474F.toInt()),
            )
            State.CAMP, State.SIEGE -> campButtons(green, grey)
            State.WORKSHOP -> {
                val l = ArrayList<Button>()
                l.add(Button("camp", "◀ VỀ TRẠI", 24f, 20f, 270f, 90f, grey))
                for (bp in Blueprints.all) {
                    val r = workshopCard(bp.id)
                    val have = save.bpParts[bp.id]
                    val (label, color, on) = when {
                        campaign.owned(bp) -> Triple("ĐÃ CÓ", green, false)
                        have < bp.parts -> Triple("THIẾU MẢNH $have/${bp.parts}", grey, false)
                        else -> Triple("LẮP RÁP ${bp.cost}$", 0xFFF9A825.toInt(), save.money >= bp.cost)
                    }
                    l.add(Button("asm${bp.id}", label, r[0] + 12f, r[3] - 62f, r[2] - 12f, r[3] - 12f, color, on))
                }
                l
            }
            State.PICK -> rooms?.let { run -> run.choices.indices.map { i -> val r = pickCard(i, run.choices.size); Button("pick$i", "", r[0], r[1], r[2], r[3], 0) } } ?: emptyList()
            State.SOUND -> listOf(Button("soundDone", "XONG", W / 2 - 180f, H - 120f, W / 2 + 180f, H - 40f, green))
            State.STORY -> listOf(Button("skip", "BỎ QUA ›", W - 290f, H - 96f, W - 40f, H - 24f, grey))
            State.LAYOUT -> listOf(
                Button("layoutReset", "MẶC ĐỊNH", W / 2 - 330f, 120f, W / 2 - 20f, 196f, grey),
                Button("layoutDone", "XONG", W / 2 + 20f, 120f, W / 2 + 330f, 196f, green),
            )
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
        val blue = 0xFF1E88E5.toInt()
        l.add(Button("play", "CHẠY!", W - 370f, H - 100f, W - 24f, H - 22f, green))
        if (garageTab == 1 || garageTab == 3) {
            // one weapons screen, two lists: mounted on the car / carried by the driver
            val mid = (garageL + 330f + W - 470f) / 2
            l.add(Button("wsub0", "🚗 GẮN TRÊN XE", mid - 300f, 16f, mid - 6f, 74f, if (garageTab == 1) 0xFFB71C1C.toInt() else grey))
            l.add(Button("wsub1", "🧍 TÀI XẾ CẦM", mid + 6f, 16f, mid + 300f, 74f, if (garageTab == 3) 0xFFB71C1C.toInt() else grey))
        }
        when (garageTab) {
            0 -> {
                val v = Vehicles.of(showVeh)
                val (label, color, on) = when {
                    !save.classUnlocked(v.cls) -> Triple("🔒 QUA ẢI ${v.cls.unlockStage}", grey, false)
                    save.vehicle == v.id -> Triple("ĐANG DÙNG", green, false)
                    save.ownsVehicle(v.id) -> Triple("CHỌN XE", blue, true)
                    !save.hasAllParts(v) -> Triple("THIẾU MẢNH ${save.parts[v.id]}/${SaveData.PARTS_NEEDED}", grey, false)
                    else -> Triple("LẮP RÁP ${v.cost}$", gold, save.money >= v.cost)
                }
                l.add(Button("vehAct", label, W - 370f, H - 192f, W - 24f, H - 114f, color, on))
            }
            1 -> {
                val w = garageWeapon
                val pr = weaponPanelRect()
                val owned = save.ownsWeapon(w.ordinal)
                val (label, color, on) = when {
                    !save.weaponUnlocked(w) -> Triple("🔒 QUA ẢI ${w.unlockStage}", grey, false)
                    save.weapon == w.ordinal -> Triple("ĐANG DÙNG", green, false)
                    owned -> Triple("TRANG BỊ", blue, true)
                    else -> Triple("MUA ${w.cost}$", gold, save.money >= w.cost)
                }
                l.add(Button("wpnAct", label, pr[2] - 330f, pr[1] + 24f, pr[2] - 24f, pr[1] + 94f, color, on))
                if (owned) {
                    val lv = save.weaponLevel(w)
                    if (lv >= WeaponType.MAX_LEVEL) {
                        l.add(Button("wpnUp", "CẤP TỐI ĐA", pr[2] - 330f, pr[1] + 106f, pr[2] - 24f, pr[1] + 176f, grey, false))
                    } else {
                        val cost = w.upgradeCost(lv)
                        l.add(Button("wpnUp", "NÂNG CẤP ${cost}$", pr[2] - 330f, pr[1] + 106f, pr[2] - 24f, pr[1] + 176f, 0xFF8E24AA.toInt(), save.money >= cost))
                    }
                }
            }
            3 -> {
                val w = garageSide
                val pr = weaponPanelRect()
                val (label, color, on) = when {
                    save.side == w.ordinal -> Triple("ĐANG DÙNG", green, false)
                    save.ownsSide(w) -> Triple("TRANG BỊ", blue, true)
                    !save.sideUnlocked(w) -> Triple("🔒 QUA ẢI ${w.unlockStage}", grey, false)
                    else -> Triple("MUA ${w.cost}$", gold, save.money >= w.cost)
                }
                l.add(Button("sideAct", label, pr[2] - 330f, pr[1] + 24f, pr[2] - 24f, pr[1] + 94f, color, on))
            }
            else -> {
                val n = Skills.nodes[skillSel]
                val bits = save.skills
                val cost = Skills.cost(Skills.count(bits))
                val (label, color, on) = when {
                    Skills.owned(bits, n.id) -> Triple("ĐÃ MỞ", green, false)
                    !Skills.available(bits, n) -> Triple("🔒 MỞ NÚT TRƯỚC", grey, false)
                    else -> Triple("MỞ  $cost ♦", 0xFFC62828.toInt(), save.gems >= cost)
                }
                l.add(Button("skillBuy", label, W - 370f, H - 192f, W - 24f, H - 114f, color, on))
            }
        }
        return l
    }

    // camp layout: a fixed view of x -150..2450 with the ground at 80% of the screen
    val campZoom get() = W / 2600f
    fun campX(wx: Float) = (wx + 150f) * campZoom
    fun campY(wy: Float) = H * 0.8f + wy * campZoom

    fun slotHit(i: Int): FloatArray {
        val x = campX(Campaign.SLOT_X[i])
        val y = campY(campaign.slotY(i) - 50f)
        return floatArrayOf(x - 70f, y - 80f, x + 70f, y + 60f)
    }

    fun wallHit() = floatArrayOf(campX(Campaign.WALL_X - 10f), campY(-Campaign.WALL_H[save.wallLevel.coerceIn(0, 2)]), campX(Campaign.WALL_X + 70f), campY(0f))

    /** Cell [k] of the build panel grid (2 rows x 4). */
    fun buildCell(k: Int): FloatArray {
        val w = min(360f, (W - 80f) / 4f - 10f)
        val col = k % 4
        val row = k / 4
        val l = 40f + col * (w + 10f)
        val t = H - 176f + row * 84f
        return floatArrayOf(l, t, l + w, t + 74f)
    }

    fun workshopCard(i: Int): FloatArray {
        val w = (W - 80f - 3 * 16f) / 4f
        val col = i % 4
        val row = i / 4
        val l = 40f + col * (w + 16f)
        val t = 106f + row * 200f
        return floatArrayOf(l, t, l + w, t + 188f)
    }

    private fun campButtons(green: Int, grey: Int): List<Button> {
        val l = ArrayList<Button>()
        val sel = campaign.selSlot
        if (sel >= 0) {
            fun cell(k: Int, id: String, label: String, color: Int, on: Boolean) {
                val r = buildCell(k)
                l.add(Button(id, label, r[0], r[1], r[2], r[3], color, on))
            }
            if (sel == 99) {
                val cost = campaign.wallRepairCost()
                cell(0, "fixwall", "🔧 SỬA TƯỜNG $cost⚙", green, cost > 0 && save.scrap >= cost)
                cell(3, "close", "✖ ĐÓNG", grey, true)
                return l
            }
            val d = campaign.defs[sel]
            if (d == null) {
                var k = 0
                for (t in DefType.entries) {
                    if (sel < 2 && t.ground) continue
                    if (k >= 7) break
                    val ok = campaign.unlocked(t)
                    cell(k++, "bld${t.ordinal}", if (ok) "${t.icon} ${t.title} ${t.cost}⚙" else "🔒 ${t.title}", if (ok) 0xFF37474F.toInt() else grey, ok && save.scrap >= t.cost)
                }
                cell(7, "close", "✖ ĐÓNG", grey, true)
            } else {
                val up = campaign.upgradeCost(d)
                val canUp = d.level < 3 && d.type != DefType.MINE
                cell(0, "up", if (canUp) "⬆ NÂNG CẤP ${up}⚙" else "CẤP TỐI ĐA", 0xFF8E24AA.toInt(), canUp && save.scrap >= up)
                val fix = campaign.repairCost(d)
                cell(1, "fix", "🔧 SỬA $fix⚙", green, fix > 0 && save.scrap >= fix)
                cell(2, "sell", "♻ BÁN +${d.type.cost * d.level / 2}⚙", 0xFF6D4C41.toInt(), true)
                cell(3, "close", "✖ ĐÓNG", grey, true)
            }
            return l
        }
        if (state == State.SIEGE) return l
        l.add(Button("menu", "◀ MENU", 24f, 20f, 210f, 90f, grey))
        l.add(Button("workshop", "🔧 XƯỞNG", 224f, 20f, 440f, 90f, 0xFF5D4037.toInt()))
        val acted = save.dayActed
        l.add(Button("expedition", "🔍 LỤC SOÁT", W - 660f, H - 104f, W - 450f, H - 20f, 0xFF1E88E5.toInt(), !acted))
        l.add(Button("drive", "🚗 LÊN ĐƯỜNG", W - 440f, H - 104f, W - 230f, H - 20f, 0xFFF9A825.toInt(), !acted && save.fuel >= 3))
        l.add(Button("night", "🌙 VÀO ĐÊM", W - 220f, H - 104f, W - 20f, H - 20f, 0xFF4527A0.toInt()))
        return l
    }

    /** Menu: the map in the middle, between the round nav buttons and the right column. */
    fun menuCenterX() = (garageL + W - 230f) / 2

    fun menuIsland(): FloatArray {
        val mx = menuCenterX()
        return floatArrayOf(mx - 260f, 170f, mx + 260f, 470f)
    }

    /** Missions screen rows. */
    fun missionRow(i: Int) = floatArrayOf(200f, 130f + i * 150f, W - 200f, 260f + i * 150f)

    fun achRow(i: Int): FloatArray {
        val col = i / 8
        val row = i % 8
        val w = (W - 120f) / 2
        val l = 40f + col * (w + 40f)
        return floatArrayOf(l, 110f + row * 72f, l + w, 174f + row * 72f)
    }

    /** 0 daily missions, 1 achievements, 2 leaderboard. */
    var missionsTab = 0

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

    /** Left of the garage content (the round nav buttons sit before it). */
    val garageL = 168f

    /** Round nav buttons: home, vehicles, weapons, skill tree, hand weapons. */
    fun navRect(i: Int): FloatArray {
        val cy = 128f + i * 114f
        return floatArrayOf(32f, cy - 52f, 136f, cy + 52f)
    }

    /** The garage floor where the vehicle stands (between the nav and the stat panel). */
    fun showroomX() = (garageL + W - 380f) / 2

    fun arrowRect(right: Boolean): FloatArray {
        val cx = if (right) W - 430f else garageL + 46f
        return floatArrayOf(cx - 42f, H * 0.5f - 60f, cx + 42f, H * 0.5f + 40f)
    }

    fun classChip(i: Int): FloatArray {
        val w = (W - 380f - garageL - 20f) / 5f
        val l = garageL + 10f + i * w
        return floatArrayOf(l + 4f, H - 110f, l + w - 4f, H - 24f)
    }

    fun weaponRect(i: Int): FloatArray {
        val cw = (W - garageL - 30f - 4 * 12f) / 5f
        val col = i % 5
        val row = i / 5
        val l = garageL + col * (cw + 12f)
        val t = 104f + row * 132f
        return floatArrayOf(l, t, l + cw, t + 120f)
    }

    fun weaponPanelRect() = floatArrayOf(garageL, 372f, W - 30f, H - 112f)

    /** Skill node squares: main line on top, perks below their parent. */
    fun skillRect(n: SkillNode): FloatArray {
        val span = (W - garageL - 160f) / 6f
        val cx = garageL + 80f + n.slot * span
        val cy = if (n.perk != null) 430f else 240f
        val half = if (n.perk != null) 62f else 54f
        return floatArrayOf(cx - half, cy - half, cx + half, cy + half)
    }

    fun skillArrow(right: Boolean): FloatArray {
        val cx = if (right) W - 70f else garageL + 40f
        return floatArrayOf(cx - 36f, 390f, cx + 36f, 470f)
    }

    /** Opens the garage on the vehicle and weapon in use. */
    private fun openGarage() {
        showVeh = save.vehicle
        garageWeapon = save.weaponType
        garageSide = save.sideWeapon
        // open the skill page where the next node waits
        val next = Skills.nodes.firstOrNull { Skills.available(save.skills, it) && it.stat != null }
        skillSel = next?.id ?: 0
        skillPage = next?.page ?: 0
        setState(State.GARAGE)
    }

    // ------------------------------------------------------- sound sliders

    /** Where to go back after the sound screen. */
    var soundBack = State.MENU
        private set

    /** Slider being dragged: 0 music, 1 effects, 2 engine; -1 none. */
    var dragVol = -1
        private set
    private var dragVolId = -1

    /** Track of slider [i] (screen coordinates). */
    fun volSlider(i: Int): FloatArray {
        val y = 230f + i * 118f
        return floatArrayOf(W / 2 - 300f, y - 12f, W / 2 + 300f, y + 12f)
    }

    fun volValue(i: Int) = when (i) {
        0 -> save.volMusic
        1 -> save.volSfx
        else -> save.volEngine
    }

    private fun setVol(i: Int, x: Float) {
        val r = volSlider(i)
        val v = (((x - r[0]) / (r[2] - r[0])) * 100f).toInt().coerceIn(0, 100)
        when (i) {
            0 -> save.volMusic = v
            1 -> save.volSfx = v
            else -> save.volEngine = v
        }
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
            val ctl = controlAt(x, y)
            if (driver.out && ctl == "none" && x < W * 0.5f && joyId < 0) {
                joyId = id
                joyX = x
                joyY = y
                joyDx = 0f
                joyDy = 0f
                updateControls()
                return
            }
            when (ctl) {
                "exit" -> if (canLeaveCar()) leaveCar()
                "act" -> when (driverAction()) {
                    "car" -> enterCar()
                    "enter" -> doorNear(driver.x, 110f)?.let { enterBuilding(it) }
                    "chest" -> interior?.chestNear(driver.x, driver.y)?.let {
                        it.openT = 0.001f
                        sfx.play(Sfx.S.CLICK)
                    }
                    "leave" -> leaveBuilding()
                }
                else -> {
                    pointers[id] = ctl
                    updateControls()
                }
            }
            return
        }
        if (state == State.LAYOUT) {
            val b = buttons().firstOrNull { it.contains(x, y) }
            if (b == null) {
                for (i in 5 downTo 0) {
                    val r = slotRect(i)
                    if (x in r[0]..r[2] && y in r[1]..r[3]) {
                        dragSlot = i
                        dragId = id
                        return
                    }
                }
                return
            }
        }
        if (state == State.SOUND) {
            for (i in 0..2) {
                val r = volSlider(i)
                if (x > r[0] - 40f && x < r[2] + 40f && y > r[1] - 44f && y < r[3] + 44f) {
                    dragVol = i
                    dragVolId = id
                    setVol(i, x)
                    return
                }
            }
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
        if (state == State.MENU && !confirmReset) {
            for (i in 1..3) if (inRect(navRect(i), x, y)) {
                sfx.play(Sfx.S.CLICK)
                if (i == 1) {
                    setState(State.WORKSHOP)
                } else {
                    openGarage()
                    garageTab = i - 1
                }
                return
            }
            if (inRect(menuIsland(), x, y)) {
                sfx.play(Sfx.S.CLICK)
                play()
                return
            }
        }
        if ((state == State.CAMP || state == State.SIEGE) && buttons().none { it.contains(x, y) }) {
            for (i in 0 until Campaign.SLOTS) if (inRect(slotHit(i), x, y)) {
                sfx.play(Sfx.S.CLICK)
                campaign.selSlot = if (campaign.selSlot == i) -1 else i
                return
            }
            if (inRect(wallHit(), x, y)) {
                sfx.play(Sfx.S.CLICK)
                campaign.selSlot = if (campaign.selSlot == 99) -1 else 99
                return
            }
            campaign.selSlot = -1
            return
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
            for (i in 0..3) if (inRect(navRect(i), x, y)) {
                sfx.play(Sfx.S.CLICK)
                // the weapons button keeps whichever list (car / hand) was open
                if (i == 0) goMenu() else if (i == 2 && garageTab == 3) Unit else garageTab = i - 1
                return
            }
            when (garageTab) {
                0 -> {
                    for (right in listOf(false, true)) if (inRect(arrowRect(right), x, y)) {
                        sfx.play(Sfx.S.CLICK)
                        showVeh = (showVeh + (if (right) 1 else -1) + Vehicles.all.size) % Vehicles.all.size
                        return
                    }
                    for (cl in VehicleClass.entries) if (inRect(classChip(cl.ordinal), x, y)) {
                        sfx.play(Sfx.S.CLICK)
                        val owned = (Vehicles.PER_CLASS - 1 downTo 0).firstOrNull { save.ownsVehicle(Vehicles.of(cl, it).id) }
                        showVeh = Vehicles.of(cl, owned ?: 0).id
                        return
                    }
                }
                1 -> for (w in WeaponType.entries) if (inRect(weaponRect(w.ordinal), x, y)) {
                    sfx.play(Sfx.S.CLICK)
                    garageWeapon = w
                    return
                }
                3 -> for (w in SideWeapon.entries) if (inRect(weaponRect(w.ordinal), x, y)) {
                    sfx.play(Sfx.S.CLICK)
                    garageSide = w
                    return
                }
                else -> {
                    for (right in listOf(false, true)) if (inRect(skillArrow(right), x, y)) {
                        sfx.play(Sfx.S.CLICK)
                        skillPage = (skillPage + (if (right) 1 else -1)).coerceIn(0, Skills.ROUNDS - 1)
                        return
                    }
                    for (n in Skills.nodes) if (n.page == skillPage && inRect(skillRect(n), x, y)) {
                        sfx.play(Sfx.S.CLICK)
                        skillSel = n.id
                        return
                    }
                }
            }
        }
        val btn = buttons().firstOrNull { it.contains(x, y) } ?: return
        if (!btn.enabled) return
        sfx.play(Sfx.S.CLICK)
        when (btn.id) {
            "play" -> play()
            "garage" -> openGarage()
            "vehAct" -> pickVehicle(Vehicles.of(showVeh))
            "skillBuy" -> buySkill(Skills.nodes[skillSel])
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
            "layout" -> openLayout()
            "layoutDone" -> {
                save.save()
                setState(layoutBack)
            }
            "layoutReset" -> {
                save.ctlX.fill(-1)
                save.ctlY.fill(-1)
            }
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
            "quit" -> quitRun()
            "maps" -> setState(State.MAPS)
            "stPrev" -> {
                save.stageSel = max(1, save.stageSel - 1); loadStage(save.stageSel)
            }
            "stNext" -> {
                save.stageSel = min(maxStartStage(), save.stageSel + 1); loadStage(save.stageSel)
            }
            "missions", "achieve" -> {
                save.refreshMissions()
                missionsTab = if (btn.id == "missions") 0 else 1
                setState(State.MISSIONS)
            }
            "mtab0" -> missionsTab = 0
            "mtab1" -> missionsTab = 1
            "mtab2" -> missionsTab = 2
            "soundMenu" -> {
                soundBack = state
                setState(State.SOUND)
            }
            "soundDone" -> {
                save.save()
                setState(soundBack)
            }
            "sideAct" -> pickSide(garageSide)
            "wsub0" -> garageTab = 1
            "wsub1" -> garageTab = 3
            else -> if (btn.id.startsWith("bld")) {
                campaign.build(campaign.selSlot, DefType.entries[btn.id.substring(3).toInt()])
                campaign.selSlot = -1
            } else if (btn.id.startsWith("asm")) campaign.assemble(Blueprints.all[btn.id.substring(3).toInt()])
            else if (btn.id.startsWith("claimM")) claimMission(btn.id.substring(6).toInt())
            else if (btn.id.startsWith("claimA")) claimAchievement(btn.id.substring(6).toInt())
        }
        when (btn.id) {
            "workshop" -> setState(State.WORKSHOP)
            "camp" -> setState(State.CAMP)
            "expedition" -> startExpedition()
            "drive" -> startDrive()
            "night" -> {
                campaign.startNight()
                setState(State.SIEGE)
            }
            "fixwall" -> campaign.repairWall()
            "close" -> campaign.selSlot = -1
            "up" -> campaign.upgrade(campaign.selSlot)
            "fix" -> campaign.repair(campaign.selSlot)
            "sell" -> {
                campaign.sell(campaign.selSlot)
                campaign.selSlot = -1
            }
            "pick0" -> choosePick(0)
            "pick1" -> choosePick(1)
            "pick2" -> choosePick(2)
        }
    }

    private fun controlAt(x: Float, y: Float): String {
        controls().firstOrNull { it.contains(x, y) }?.let { return it.id }
        // anywhere else: right half accelerates (in the car)
        return if (x > W / 2 && !driver.out) "gas" else "none"
    }

    fun touchMove(id: Int, x: Float, y: Float) {
        if (state == State.SOUND && id == dragVolId && dragVol >= 0) {
            setVol(dragVol, x)
            return
        }
        if (state == State.LAYOUT && id == dragId && dragSlot >= 0) {
            save.ctlX[dragSlot] = (x / W * 1000f).toInt().coerceIn(0, 1000)
            save.ctlY[dragSlot] = (y / H * 1000f).toInt().coerceIn(0, 1000)
            return
        }
        if (state == State.PLAYING && id == joyId) {
            joyDx = (x - joyX).coerceIn(-90f, 90f)
            joyDy = (y - joyY).coerceIn(-90f, 90f)
            updateControls()
            return
        }
        if (state != State.PLAYING || !pointers.containsKey(id)) return
        val c = controlAt(x, y)
        if (c != pointers[id]) {
            pointers[id] = c
            updateControls()
        }
    }

    fun touchUp(id: Int) {
        if (id == dragVolId) {
            // a sample of the new effects level
            if (dragVol == 1) sfx.play(Sfx.S.COIN)
            dragVol = -1
            dragVolId = -1
        }
        if (id == dragId) {
            dragId = -1
            dragSlot = -1
        }
        if (id == joyId) joyId = -1
        pointers.remove(id)
        updateControls()
    }

    private fun updateControls() {
        val v = pointers.values
        gas = "gas" in v
        brake = "brake" in v
        val joy = joyId >= 0 && driver.out
        leanBack = "leanBack" in v || (joy && joyDx < -22f)
        leanFwd = "leanFwd" in v || (joy && joyDx > 22f)
        boostHeld = "boost" in v
        jumpHeld = "jump" in v
    }

    fun isHeld(id: String) = id in pointers.values

    private fun buySkill(n: SkillNode) {
        val bits = save.skills
        if (!Skills.available(bits, n)) return
        val cost = Skills.cost(Skills.count(bits))
        if (save.gems < cost) return
        save.gems -= cost
        save.skills = bits or (1 shl n.id)
        save.save()
        sfx.play(Sfx.S.POWER)
        // move on to the next node of the line
        Skills.nodes.firstOrNull { it.stat != null && Skills.available(save.skills, it) }?.let { if (n.stat != null) { skillSel = it.id; skillPage = it.page } }
    }

    private fun pickVehicle(v: Vehicle) {
        if (!save.classUnlocked(v.cls) || !save.hasAllParts(v)) return
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

    private fun claimMission(i: Int) {
        if (!save.missionDone(i) || save.missionClaimed and (1 shl i) != 0) return
        save.missionClaimed = save.missionClaimed or (1 shl i)
        save.gems += save.missionGems(i)
        sfx.play(Sfx.S.WIN, 0.7f)
        save.save()
    }

    private fun claimAchievement(i: Int) {
        if (!save.achDone(i) || save.achClaimed and (1 shl i) != 0) return
        save.achClaimed = save.achClaimed or (1 shl i)
        save.gems += Missions.achievements[i].gems
        sfx.play(Sfx.S.WIN, 0.7f)
        save.save()
    }

    private fun pickSide(w: SideWeapon) {
        if (!save.ownsSide(w)) {
            if (!save.sideUnlocked(w) || save.money < w.cost) return
            save.money -= w.cost
            save.ownedSide = save.ownedSide or (1 shl w.ordinal)
        }
        save.side = w.ordinal
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
