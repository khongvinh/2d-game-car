package com.khongvinh.carsurvival

import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Game rules and state. Everything is laid out on a virtual canvas that is
 * [VIRTUAL_WIDTH] units wide; height follows the screen's aspect ratio.
 */
class Game(val save: SaveData, val sfx: Sfx) {
    companion object {
        const val VIRTUAL_WIDTH = 720f
        const val ROAD_LEFT = 60f
        const val ROAD_RIGHT = 660f
        const val LANES = 4
        const val BULLET_SPEED = 1500f
        const val BOLT_SPEED = 1300f
        const val BOULDER_SPEED = 750f

        /** Levels (màn) per stage (ải), and number of stages. */
        const val LEVELS_PER_STAGE = 5
        const val STAGES = 20

        /** Storm phases: Bo 1, Bo 2, Bo 3 (seconds). */
        val PHASE_TIME = floatArrayOf(60f, 20f, 5f)

        /** Storm damage per second outside the safe zone during Bo 1..3 and after it closed. */
        val PHASE_DAMAGE = floatArrayOf(15f, 25f, 45f, 150f)

        /** Distance one wave of obstacles is spread over. */
        const val WAVE_LENGTH = 1800f
        const val WAVE_BREAK = 520f
    }

    var W = VIRTUAL_WIDTH
    var H = 1280f

    /** Space reserved at the top for a display cutout, in virtual units. */
    var topInset = 0f

    var state = State.MENU
        private set
    var stateTime = 0f
    var anim = 0f

    // Current run
    var level = 1
    val stage get() = (level - 1) / LEVELS_PER_STAGE + 1
    val levelInStage get() = (level - 1) % LEVELS_PER_STAGE + 1
    var distance = 0f
    var goal = 1f
    var countdown = 0f
    var nextSpawn = 0f
    var waveLeft = 0
    var levelCoins = 0
    var levelKills = 0
    var runCoins = 0
    var timeBonus = 0
    var stageBonus = 0
    var deathByStorm = false
    var shake = 0f
    var stormHurt = 0f
    var menuScroll = 0f

    /** Storm phase: 0..2 = Bo 1..3 counting down, 3 = fully closed. */
    var phase = 0
    var phaseLeft = PHASE_TIME[0]
    private var lastBeep = 0

    val player = Player()
    val obstacles = ArrayList<Obstacle>()
    val items = ArrayList<Item>()
    val shots = ArrayList<Projectile>()
    val particles = ArrayList<Particle>()
    val texts = ArrayList<FloatText>()
    val trees = ArrayList<Tree>()

    private val renderer = Renderer(this)
    private val tmpA = RectF()
    private val tmpB = RectF()
    private val tmpC = RectF()
    private var lastTouchX = 0f
    private var touching = false

    val laneW get() = (ROAD_RIGHT - ROAD_LEFT) / LANES
    fun laneX(i: Int) = ROAD_LEFT + laneW * (i + 0.5f)
    val progress get() = (distance / goal).coerceIn(0f, 1f)
    val roadHalf get() = (ROAD_RIGHT - ROAD_LEFT) / 2f

    init {
        sfx.enabled = save.sound
        save.stageSel = save.stageSel.coerceIn(1, maxStartStage())
        resize(W, H)
    }

    fun maxStartStage() = min(STAGES, save.bestStage + 1)

    fun resize(w: Float, h: Float) {
        W = w
        H = h
        player.y = H * 0.78f
        trees.clear()
        var y = -100f
        while (y < H + 200f) {
            spawnTree(y)
            y += 110f + Random.nextFloat() * 90f
        }
    }

    private fun spawnTree(y: Float) {
        val left = Random.nextBoolean()
        val x = if (left) Random.nextFloat() * (ROAD_LEFT - 30f) + 8f else ROAD_RIGHT + 22f + Random.nextFloat() * (W - ROAD_RIGHT - 30f)
        trees.add(Tree(x, y, 22f + Random.nextFloat() * 16f, Random.nextBoolean()))
    }

    private fun setState(s: State) {
        state = s
        stateTime = 0f
    }

    // ------------------------------------------------------------------ flow

    fun startRun() {
        val stageNo = save.stageSel.coerceIn(1, maxStartStage())
        level = (stageNo - 1) * LEVELS_PER_STAGE + 1
        runCoins = 0
        val car = save.carType
        val p = player
        p.w = car.w
        p.h = car.h
        p.color = car.color
        p.handling = car.handling
        p.armor = car.armor + 0.1f * save.level(Upgrade.ARMOR)
        p.maxHp = (100f + 25f * save.level(Upgrade.HP)) * car.hpMul
        p.hp = p.maxHp
        setupLevel()
    }

    private fun nextLevel() {
        level++
        player.hp = min(player.maxHp, player.hp + player.maxHp * 0.3f)
        setupLevel()
    }

    /** Obstacles per wave: 3 on level 1, +1 every level (arithmetic progression). */
    fun obstaclesPerWave() = 3 + (level - 1)

    /** Max lanes blocked at once: 1 early on, up to 3 (one lane always stays open). */
    private fun perRow() = min(3, 1 + (level - 1) / 6)

    private fun setupLevel() {
        obstacles.clear()
        items.clear()
        shots.clear()
        particles.clear()
        texts.clear()
        distance = 0f
        nextSpawn = 700f
        waveLeft = obstaclesPerWave()
        levelCoins = 0
        levelKills = 0
        timeBonus = 0
        stageBonus = 0
        deathByStorm = false
        shake = 0f
        stormHurt = 0f
        phase = 0
        phaseLeft = PHASE_TIME[0]
        lastBeep = 0

        val p = player
        p.maxSpeed = min(1250f, 640f + level * 9f) * save.carType.speedMul
        p.speed = 0f
        p.x = W / 2
        p.targetX = p.x
        p.y = H * 0.78f
        p.shieldT = 0f
        p.weaponT = 0f
        p.skidT = 0f
        p.hurtT = 0f
        p.tilt = 0f
        p.cloneT = 0f

        // Pure driving time at full speed. The three storm phases give 85 s in total,
        // so the margin for crashes shrinks as levels get longer.
        val cruise = min(76f, 60f + level * 0.4f)
        goal = p.maxSpeed * cruise
        countdown = 3f
        lastCountdownBeep = 4
        setState(State.PLAYING)
    }

    private var lastCountdownBeep = 4

    /** Seconds left before the storm closes completely. */
    fun totalTimeLeft(): Float {
        if (phase >= PHASE_TIME.size) return 0f
        var t = phaseLeft
        for (i in phase + 1 until PHASE_TIME.size) t += PHASE_TIME[i]
        return t
    }

    private fun levelComplete() {
        timeBonus = (totalTimeLeft() * 2f).toInt()
        save.money += timeBonus
        runCoins += timeBonus
        if (levelInStage == LEVELS_PER_STAGE) {
            val cleared = stage
            stageBonus = 100 * cleared
            save.money += stageBonus
            runCoins += stageBonus
            save.bestStage = max(save.bestStage, cleared)
            save.stageSel = min(STAGES, max(save.stageSel, cleared + 1))
            save.save()
            sfx.play(Sfx.S.STAGE)
            setState(if (cleared >= STAGES) State.VICTORY else State.STAGE_DONE)
        } else {
            save.save()
            sfx.play(Sfx.S.WIN)
            setState(State.LEVEL_DONE)
        }
    }

    private fun gameOver(byStorm: Boolean) {
        deathByStorm = byStorm
        player.hp = 0f
        explode(player.x, player.y, 0xFFFF7043.toInt(), 50)
        shake = 0.6f
        sfx.play(Sfx.S.BOOM)
        sfx.play(Sfx.S.LOSE, 0.8f)
        save.save()
        setState(State.GAME_OVER)
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
        else -> {
            save.save(); setState(State.MENU); true
        }
    }

    // ---------------------------------------------------------------- update

    fun update(dt: Float) {
        anim += dt
        stateTime += dt
        if (shake > 0) shake -= dt
        when (state) {
            State.PLAYING -> updatePlay(dt)
            State.MENU, State.SHOP, State.GARAGE -> menuScroll += dt * 420f
            else -> {}
        }
        if (state != State.PAUSED) updateEffects(dt)
    }

    private fun updateEffects(dt: Float) {
        val pi = particles.iterator()
        while (pi.hasNext()) {
            val pt = pi.next()
            pt.life -= dt
            pt.x += pt.vx * dt
            pt.y += pt.vy * dt
            pt.vx *= 0.96f
            pt.vy *= 0.96f
            if (pt.life <= 0) pi.remove()
        }
        val ti = texts.iterator()
        while (ti.hasNext()) {
            val t = ti.next()
            t.life -= dt
            t.y -= 70f * dt
            if (t.life <= 0) ti.remove()
        }
    }

    private fun updateStorm(dt: Float) {
        if (phase >= PHASE_TIME.size) return
        phaseLeft -= dt
        if (phase == 2) {
            // Bo 3: beep every second of the 5 s countdown
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
                texts.add(FloatText("BO ${phase + 1} BẮT ĐẦU!", W / 2, H * 0.42f, 0xFFE040FB.toInt()))
            } else {
                phaseLeft = 0f
                texts.add(FloatText("VÒNG BO ĐÃ KHÉP!", W / 2, H * 0.42f, 0xFFE040FB.toInt()))
            }
            sfx.play(Sfx.S.STORM)
            shake = 0.3f
        }
    }

    private fun updatePlay(dt: Float) {
        val p = player
        if (countdown > 0f) {
            countdown -= dt
            val sec = ceil(countdown).toInt()
            if (sec != lastCountdownBeep) {
                lastCountdownBeep = sec
                sfx.play(if (sec <= 0) Sfx.S.GO else Sfx.S.BEEP)
            }
            return
        }

        // Speed and distance
        p.speed = min(p.maxSpeed, p.speed + p.maxSpeed * 0.42f * dt)
        distance += p.speed * dt

        updateStorm(dt)

        // Steering
        if (p.skidT > 0f) {
            p.skidT -= dt
            p.targetX += p.skidDir * 300f * dt
        }
        val minX = ROAD_LEFT + p.w / 2 + 4f
        val maxX = ROAD_RIGHT - p.w / 2 - 4f
        p.targetX = p.targetX.coerceIn(minX, maxX)
        val prevX = p.x
        p.x += (p.targetX - p.x) * min(1f, dt * p.handling)
        val vx = (p.x - prevX) / max(dt, 0.001f)
        p.tilt += ((vx * 0.025f).coerceIn(-16f, 16f) - p.tilt) * min(1f, dt * 10f)

        // Clone follows one lane beside the player, on the side with more room
        if (p.cloneT > 0f) {
            p.cloneT -= dt
            val side = if (p.x < W / 2) 1f else -1f
            val want = (p.x + side * laneW * 1.05f).coerceIn(minX, maxX)
            p.cloneX += (want - p.cloneX) * min(1f, dt * 10f)
            if (p.cloneT <= 0f) burst(p.cloneX, p.y, ItemType.CLONE.color)
        }

        if (p.shieldT > 0f) p.shieldT -= dt
        if (p.hurtT > 0f) p.hurtT -= dt
        if (stormHurt > 0f) stormHurt -= dt

        // Weapon: auto fire (clone fires too)
        if (p.weaponT > 0f) {
            p.weaponT -= dt
            p.fireCd -= dt
            if (p.fireCd <= 0f) {
                val w = save.weaponType
                p.fireCd = w.interval * (1f - 0.08f * save.level(Upgrade.WEAPON))
                fireFrom(p.x, w)
                if (p.cloneT > 0f) fireFrom(p.cloneX, w)
            }
        }

        // World movement
        for (o in obstacles) {
            o.y += (p.speed - o.speed) * dt
            if (o.flash > 0) o.flash -= dt
        }
        for (it in items) it.y += p.speed * dt
        for (t in trees) t.y += p.speed * dt
        val ti = trees.iterator()
        var respawn = 0
        while (ti.hasNext()) if (ti.next().y > H + 80f) {
            ti.remove(); respawn++
        }
        repeat(respawn) { spawnTree(-60f - Random.nextFloat() * 150f) }

        updateShots(dt)

        // Player (and clone) vs obstacles
        p.rect(tmpA)
        for (o in obstacles) {
            if (!o.alive) continue
            o.rect(tmpB)
            if (p.cloneT > 0f && o.type != ObstacleType.OIL) {
                val cr = p.cloneRect(tmpC)
                if (RectF.intersects(cr, tmpB)) {
                    // The clone takes the hit for you
                    o.alive = false
                    p.cloneT = 0f
                    explode(o.x, o.y, o.color, 26)
                    texts.add(FloatText("MẤT BẢN SAO!", p.cloneX, p.y - 90f, 0xFF64FFDA.toInt()))
                    sfx.play(Sfx.S.HIT)
                    shake = 0.2f
                    continue
                }
            }
            if (!RectF.intersects(tmpA, tmpB)) continue
            if (o.type == ObstacleType.OIL) {
                if (!o.used && p.shieldT <= 0f) {
                    o.used = true
                    p.skidT = 0.9f
                    p.skidDir = if (Random.nextBoolean()) 1f else -1f
                    p.speed *= 0.7f
                    texts.add(FloatText("TRƯỢT!", p.x, p.y - 90f, 0xFFFFFFFF.toInt()))
                    sfx.play(Sfx.S.CATAPULT, 0.6f)
                }
                continue
            }
            if (p.shieldT > 0f) {
                o.alive = false
                explode(o.x, o.y, o.color, 22)
                addCoins(max(1, o.type.reward / 2), o.x, o.y)
                p.speed *= 0.9f
                shake = 0.15f
                sfx.play(Sfx.S.EXPLODE, 0.7f)
            } else if (p.hurtT <= 0f) {
                o.alive = false
                p.hurtT = 0.7f
                p.speed *= 0.3f
                explode(o.x, (o.y + p.y) / 2, o.color, 30)
                shake = 0.35f
                sfx.play(Sfx.S.HIT)
                if (p.cloneT > 0f) {
                    // Two lives: the clone is sacrificed instead of losing HP
                    p.cloneT = 0f
                    burst(p.cloneX, p.y, ItemType.CLONE.color)
                    texts.add(FloatText("BẢN SAO ĐỠ ĐÒN!", p.x, p.y - 90f, 0xFF64FFDA.toInt()))
                } else {
                    val dmg = o.type.damage * (1f - p.armor).coerceAtLeast(0.2f)
                    p.hp -= dmg
                    texts.add(FloatText("-${dmg.toInt()}", p.x, p.y - 90f, 0xFFFF5252.toInt()))
                }
            }
        }

        // Player / clone vs items
        for (it in items) {
            if (!it.alive) continue
            if (hypot(it.x - p.x, it.y - p.y) < 72f ||
                (p.cloneT > 0f && hypot(it.x - p.cloneX, it.y - p.y) < 72f)
            ) {
                it.alive = false
                collect(it)
            }
        }

        // Spawning
        while (distance >= nextSpawn) spawnNext()

        // Storm damage
        if (abs(p.x - W / 2) > stormRadius()) {
            p.hp -= PHASE_DAMAGE[phase] * dt
            stormHurt = 0.2f
            if (Random.nextFloat() < 0.4f) {
                particles.add(Particle(p.x + Random.nextFloat() * 60f - 30f, p.y, 0f, -120f, 0.5f, 0.5f, 6f, 0xFFE040FB.toInt()))
            }
            if (p.hp <= 0f) {
                gameOver(true); return
            }
        }

        // Cleanup
        obstacles.removeAll { !it.alive || it.y > H + 300f || it.y < -900f }
        items.removeAll { !it.alive || it.y > H + 100f }
        shots.removeAll { !it.alive }

        if (p.hp <= 0f) {
            gameOver(false); return
        }
        if (distance >= goal) levelComplete()
    }

    private fun fireFrom(x: Float, w: WeaponType) {
        val y = player.y - player.h / 2
        when (w) {
            WeaponType.GUN -> {
                shots.add(Projectile(w, x - 20f, y))
                shots.add(Projectile(w, x + 20f, y))
                sfx.play(Sfx.S.SHOOT, 0.35f)
            }
            WeaponType.CROSSBOW -> {
                shots.add(Projectile(w, x, y))
                sfx.play(Sfx.S.BOW, 0.7f)
            }
            WeaponType.CATAPULT -> {
                shots.add(Projectile(w, x, y))
                sfx.play(Sfx.S.CATAPULT, 0.8f)
            }
        }
    }

    private fun updateShots(dt: Float) {
        for (s in shots) {
            if (!s.alive) continue
            when (s.kind) {
                WeaponType.CATAPULT -> {
                    // Lobbed: flies over obstacles and explodes where it lands
                    val step = BOULDER_SPEED * dt
                    s.y -= step
                    s.travel += step
                    if (s.travel >= Projectile.CATAPULT_RANGE) {
                        s.alive = false
                        catapultBlast(s)
                    }
                }
                else -> {
                    s.y -= (if (s.kind == WeaponType.GUN) BULLET_SPEED else BOLT_SPEED) * dt
                    if (s.y < -60f) {
                        s.alive = false; continue
                    }
                    for (o in obstacles) {
                        if (!o.alive || o.type == ObstacleType.OIL || o in s.hit) continue
                        o.rect(tmpA, 1f)
                        if (!tmpA.contains(s.x, s.y)) continue
                        if (!o.type.destructible) {
                            sparks(s.x, s.y)
                            s.alive = false
                            break
                        }
                        s.hit.add(o)
                        damageObstacle(o, s.damage)
                        s.pierce--
                        if (s.pierce <= 0) {
                            s.alive = false
                            break
                        }
                    }
                }
            }
        }
    }

    private fun catapultBlast(s: Projectile) {
        val r = Projectile.CATAPULT_RADIUS
        explode(s.x, s.y, 0xFF8D6E63.toInt(), 34)
        repeat(18) {
            val a = it / 18f * 6.2832f
            particles.add(Particle(s.x, s.y, cos(a) * r * 2.5f, sin(a) * r * 2.5f, 0.35f, 0.35f, 7f, 0xFFFFCC80.toInt()))
        }
        sfx.play(Sfx.S.BOOM, 0.8f)
        shake = max(shake, 0.15f)
        for (o in obstacles) {
            if (!o.alive || o.type == ObstacleType.OIL) continue
            if (hypot(o.x - s.x, o.y - s.y) > r + max(o.type.w, o.type.h) * 0.3f) continue
            if (o.type == ObstacleType.ROCK) {
                o.alive = false
                levelKills++
                explode(o.x, o.y, o.color, 20)
                addCoins(o.type.reward, o.x, o.y)
            } else {
                damageObstacle(o, s.damage)
            }
        }
    }

    private fun damageObstacle(o: Obstacle, dmg: Int) {
        o.hp -= dmg
        o.flash = 0.08f
        if (o.hp <= 0) {
            o.alive = false
            levelKills++
            explode(o.x, o.y, o.color, 26)
            addCoins(o.type.reward, o.x, o.y)
            sfx.play(Sfx.S.EXPLODE, 0.6f)
        }
    }

    /**
     * Radius of the safe zone around the road's centre line at the player's height.
     * Bo 1 closes from the whole screen to the road edges, Bo 2 down to the two middle
     * lanes, Bo 3 to nothing; after that everything is storm.
     */
    fun stormRadius(): Float {
        val full = hypot(W / 2, max(player.y, H - player.y)) + 60f
        val r1 = roadHalf + 30f
        val r2 = roadHalf * 0.5f
        return when (phase) {
            0 -> {
                val t = 1f - phaseLeft / PHASE_TIME[0]
                // hold for the first half of Bo 1, then close in
                val k = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)
                full + (r1 - full) * k
            }
            1 -> r1 + (r2 - r1) * (1f - phaseLeft / PHASE_TIME[1]).coerceIn(0f, 1f)
            2 -> r2 * (phaseLeft / PHASE_TIME[2]).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    private fun spawnNext() {
        if (waveLeft <= 0) {
            // short breather between waves, then a new (bigger) wave
            waveLeft = obstaclesPerWave()
            nextSpawn += WAVE_BREAK
            return
        }
        val per = perRow()
        val n = obstaclesPerWave()
        val rows = ceil(n.toFloat() / per)
        val minGap = max(270f, player.maxSpeed * 0.4f)
        nextSpawn += max(minGap, WAVE_LENGTH / rows) * (0.9f + Random.nextFloat() * 0.2f)

        val count = min(waveLeft, Random.nextInt(max(1, per - 1), per + 1))
        waveLeft -= count
        val y = -280f
        val lanes = (0 until LANES).shuffled()
        for (i in 0 until count) obstacles.add(makeObstacle(lanes[i], y + Random.nextFloat() * 40f, per))
        if (Random.nextFloat() < 0.4f) {
            val free = lanes[count + Random.nextInt(LANES - count)]
            items.add(Item(randomItem(), laneX(free), y - 20f))
        }
    }

    private val carColors = intArrayOf(
        0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt(), 0xFFFB8C00.toInt(),
        0xFF8E24AA.toInt(), 0xFFECEFF1.toInt(), 0xFF00ACC1.toInt(), 0xFF6D4C41.toInt(),
    )

    private fun makeObstacle(lane: Int, y: Float, per: Int): Obstacle {
        val roll = Random.nextInt(100)
        val type = when {
            roll < 32 -> ObstacleType.CAR
            roll < 42 -> if (level >= 3) ObstacleType.TRUCK else ObstacleType.CAR
            roll < 60 -> ObstacleType.BARREL
            roll < 74 -> ObstacleType.ROCK
            roll < 91 -> ObstacleType.CONE
            else -> ObstacleType.OIL
        }
        val x = laneX(lane) + (Random.nextFloat() - 0.5f) * 20f
        // Traffic is slow when rows are dense, so it does not drift into the next row
        // and close the only free lane.
        val traffic = if (per >= 2) 40f + Random.nextFloat() * 80f else 140f + Random.nextFloat() * 160f
        return when (type) {
            ObstacleType.CAR -> Obstacle(type, x, y, traffic, carColors.random())
            ObstacleType.TRUCK -> Obstacle(type, x, y - 50f, traffic * 0.7f, carColors.random())
            ObstacleType.BARREL -> Obstacle(type, x, y, 0f, 0xFFFF6F00.toInt())
            ObstacleType.ROCK -> Obstacle(type, x, y, 0f, 0xFF757575.toInt())
            ObstacleType.CONE -> Obstacle(type, x, y, 0f, 0xFFFF5722.toInt())
            ObstacleType.OIL -> Obstacle(type, x, y, 0f, 0xFF111111.toInt())
        }
    }

    private fun randomItem(): ItemType {
        val pool = ItemType.entries.filter { it != ItemType.CLONE || save.cloneUnlocked }
        val total = pool.sumOf { it.weight }
        var r = Random.nextInt(total)
        for (t in pool) {
            if (r < t.weight) return t
            r -= t.weight
        }
        return ItemType.COIN
    }

    private fun collect(it: Item) {
        val p = player
        burst(it.x, it.y, it.type.color)
        when (it.type) {
            ItemType.COIN -> {
                addCoins(10 + level, it.x, it.y)
                sfx.play(Sfx.S.COIN)
            }
            ItemType.HEALTH -> {
                p.hp = min(p.maxHp, p.hp + 30f)
                texts.add(FloatText("+30 MÁU", it.x, it.y, 0xFF69F0AE.toInt()))
                sfx.play(Sfx.S.POWER)
            }
            ItemType.SHIELD -> {
                p.shieldT = 6f + 1.5f * save.level(Upgrade.SHIELD)
                texts.add(FloatText("LÁ CHẮN!", it.x, it.y, 0xFF82B1FF.toInt()))
                sfx.play(Sfx.S.POWER)
            }
            ItemType.WEAPON -> {
                p.weaponT = 7f + 2f * save.level(Upgrade.WEAPON)
                p.fireCd = 0f
                texts.add(FloatText(save.weaponType.title.uppercase() + "!", it.x, it.y, 0xFFFF8A80.toInt()))
                sfx.play(Sfx.S.POWER)
            }
            ItemType.TIME -> if (phase < PHASE_TIME.size) {
                phaseLeft += 5f
                texts.add(FloatText("+5 GIÂY", it.x, it.y, 0xFFEA80FC.toInt()))
                sfx.play(Sfx.S.POWER)
            } else {
                addCoins(15, it.x, it.y)
                sfx.play(Sfx.S.COIN)
            }
            ItemType.CLONE -> {
                if (p.cloneT <= 0f) p.cloneX = p.x
                p.cloneT = 10f
                texts.add(FloatText("NHÂN BẢN x2!", it.x, it.y, 0xFF64FFDA.toInt()))
                sfx.play(Sfx.S.CLONE)
            }
        }
    }

    private fun addCoins(n: Int, x: Float, y: Float) {
        if (n <= 0) return
        save.money += n
        levelCoins += n
        runCoins += n
        texts.add(FloatText("+$n$", x, y, 0xFFFFD740.toInt()))
    }

    private fun explode(x: Float, y: Float, color: Int, n: Int) {
        val colors = intArrayOf(color, 0xFFFFC107.toInt(), 0xFFFF5722.toInt(), 0xFF424242.toInt())
        repeat(n) {
            val a = Random.nextFloat() * 6.2832f
            val s = 80f + Random.nextFloat() * 380f
            val life = 0.4f + Random.nextFloat() * 0.5f
            particles.add(Particle(x, y, cos(a) * s, sin(a) * s, life, life, 5f + Random.nextFloat() * 9f, colors.random()))
        }
    }

    private fun sparks(x: Float, y: Float) {
        repeat(5) {
            val a = Random.nextFloat() * 6.2832f
            particles.add(Particle(x, y, cos(a) * 200f, sin(a) * 200f, 0.25f, 0.25f, 4f, 0xFFFFF59D.toInt()))
        }
    }

    private fun burst(x: Float, y: Float, color: Int) {
        repeat(14) {
            val a = it / 14f * 6.2832f
            particles.add(Particle(x, y, cos(a) * 260f, sin(a) * 260f, 0.4f, 0.4f, 6f, color))
        }
    }

    // ----------------------------------------------------------------- input

    fun buttons(): List<Button> {
        val cx = W / 2
        fun b(id: String, label: String, y: Float, color: Int, enabled: Boolean = true, w: Float = 420f) =
            Button(id, label, RectF(cx - w / 2, y, cx + w / 2, y + 100f), color, enabled)
        val green = 0xFF43A047.toInt()
        val grey = 0xFF546E7A.toInt()
        return when (state) {
            State.MENU -> {
                val y = H * 0.5f
                listOf(
                    Button("stPrev", "◀", RectF(cx - 260f, y, cx - 160f, y + 100f), grey, save.stageSel > 1),
                    Button("stNext", "▶", RectF(cx + 160f, y, cx + 260f, y + 100f), grey, save.stageSel < maxStartStage()),
                    b("play", "CHƠI", y + 125f, green),
                    b("shop", "NÂNG CẤP", y + 245f, 0xFF1E88E5.toInt()),
                    b("garage", if (save.garageUnlocked) "GARA" else "GARA 🔒", y + 365f, 0xFF8E24AA.toInt(), save.garageUnlocked),
                    Button("sound", if (save.sound) "🔊" else "🔇", RectF(W - 100f, 24f + topInset, W - 20f, 104f + topInset), 0x66000000, true),
                )
            }
            State.SHOP -> {
                val list = ArrayList<Button>()
                Upgrade.entries.forEachIndexed { i, u ->
                    val lv = save.level(u)
                    val maxed = lv >= Upgrade.MAX_LEVEL
                    val cost = u.cost(lv)
                    val y = 300f + i * 190f + 70f
                    list.add(
                        Button(
                            "up${u.ordinal}",
                            if (maxed) "TỐI ĐA" else "$cost$",
                            RectF(W - 250f, y, W - 50f, y + 84f),
                            0xFFFFA000.toInt(),
                            !maxed && save.money >= cost,
                        ),
                    )
                }
                list.add(b("menu", "QUAY LẠI", H - 150f, grey))
                list
            }
            State.GARAGE -> listOf(b("menu", "QUAY LẠI", H - 130f, grey))
            State.PAUSED -> listOf(
                b("resume", "TIẾP TỤC", H * 0.45f, green),
                b("menu", "MENU", H * 0.45f + 130f, grey),
            )
            State.LEVEL_DONE -> listOf(
                b("next", "MÀN TIẾP", H * 0.62f, green),
                b("menu", "MENU", H * 0.62f + 130f, grey),
            )
            State.STAGE_DONE -> listOf(
                b("next", "ẢI TIẾP", H * 0.66f, green),
                b("menu", "MENU", H * 0.66f + 130f, grey),
            )
            State.VICTORY -> listOf(b("menu", "MENU", H * 0.7f, green))
            State.GAME_OVER -> listOf(
                b("play", "CHƠI LẠI", H * 0.62f, green),
                b("menu", "MENU", H * 0.62f + 130f, grey),
            )
            State.PLAYING -> emptyList()
        }
    }

    /** Garage cards: 4 cars in a 2x2 grid, then 3 weapons in a row. */
    fun carCard(i: Int): RectF {
        val col = i % 2
        val row = i / 2
        val x = 30f + col * 340f
        val y = 250f + row * 240f
        return RectF(x, y, x + 320f, y + 225f)
    }

    fun weaponCard(i: Int): RectF {
        val x = 30f + i * 223f
        val y = 790f
        return RectF(x, y, x + 213f, y + 235f)
    }

    val pauseRect get() = RectF(W - 92f, 24f + topInset, W - 20f, 96f + topInset)

    fun touchDown(x: Float, y: Float) {
        if (state == State.PLAYING) {
            if (pauseRect.contains(x, y)) {
                sfx.play(Sfx.S.CLICK)
                pause(); return
            }
            touching = true
            lastTouchX = x
            return
        }
        if (stateTime < 0.5f) return
        if (state == State.GARAGE && garageTouch(x, y)) return
        val btn = buttons().firstOrNull { it.rect.contains(x, y) } ?: return
        if (!btn.enabled) return
        sfx.play(Sfx.S.CLICK)
        when (btn.id) {
            "play" -> startRun()
            "shop" -> setState(State.SHOP)
            "garage" -> setState(State.GARAGE)
            "stPrev" -> save.stageSel = max(1, save.stageSel - 1)
            "stNext" -> save.stageSel = min(maxStartStage(), save.stageSel + 1)
            "sound" -> {
                save.sound = !save.sound
                sfx.enabled = save.sound
                save.save()
            }
            "menu" -> {
                save.save(); setState(State.MENU)
            }
            "resume" -> setState(State.PLAYING)
            "next" -> nextLevel()
            else -> if (btn.id.startsWith("up")) {
                val u = Upgrade.entries[btn.id.substring(2).toInt()]
                val cost = u.cost(save.level(u))
                if (save.money >= cost && save.level(u) < Upgrade.MAX_LEVEL) {
                    save.money -= cost
                    save.upgrades[u.ordinal]++
                    save.save()
                    sfx.play(Sfx.S.COIN)
                }
            }
        }
    }

    /** Select or buy a car / weapon. Returns true if a card was tapped. */
    private fun garageTouch(x: Float, y: Float): Boolean {
        for (i in CarType.entries.indices) {
            if (!carCard(i).contains(x, y)) continue
            val c = CarType.entries[i]
            if (save.ownsCar(i)) {
                save.car = i
                sfx.play(Sfx.S.CLICK)
            } else if (save.money >= c.cost) {
                save.money -= c.cost
                save.ownedCars = save.ownedCars or (1 shl i)
                save.car = i
                sfx.play(Sfx.S.COIN)
            }
            save.save()
            return true
        }
        for (i in WeaponType.entries.indices) {
            if (!weaponCard(i).contains(x, y)) continue
            val w = WeaponType.entries[i]
            if (save.ownsWeapon(i)) {
                save.weapon = i
                sfx.play(Sfx.S.CLICK)
            } else if (save.money >= w.cost) {
                save.money -= w.cost
                save.ownedWeapons = save.ownedWeapons or (1 shl i)
                save.weapon = i
                sfx.play(Sfx.S.COIN)
            }
            save.save()
            return true
        }
        return false
    }

    fun touchMove(x: Float, @Suppress("UNUSED_PARAMETER") y: Float) {
        if (state != State.PLAYING || !touching) return
        player.targetX += (x - lastTouchX) * 1.35f
        lastTouchX = x
    }

    fun touchUp() {
        touching = false
    }

    // ---------------------------------------------------------------- render

    fun render(c: Canvas) = renderer.render(c)
}
