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
class Game(val save: SaveData) {
    companion object {
        const val VIRTUAL_WIDTH = 720f
        const val ROAD_LEFT = 60f
        const val ROAD_RIGHT = 660f
        const val LANES = 4
        const val ACCEL = 260f
        const val BULLET_SPEED = 1500f

        /** Time it takes the storm to collapse completely once the timer hits zero. */
        const val COLLAPSE_TIME = 4f

        /** Fraction of the level time after which the storm starts closing in. */
        const val STORM_START = 0.55f
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
    var distance = 0f
    var goal = 1f
    var timeLimit = 1f
    var timeLeft = 1f
    var collapseT = 0f
    var countdown = 0f
    var nextSpawn = 0f
    var levelCoins = 0
    var levelKills = 0
    var runCoins = 0
    var timeBonus = 0
    var deathByStorm = false
    var shake = 0f
    var stormHurt = 0f
    var menuScroll = 0f

    val player = Player()
    val obstacles = ArrayList<Obstacle>()
    val items = ArrayList<Item>()
    val bullets = ArrayList<Bullet>()
    val particles = ArrayList<Particle>()
    val texts = ArrayList<FloatText>()
    val trees = ArrayList<Tree>()

    private val renderer = Renderer(this)
    private val tmpA = RectF()
    private val tmpB = RectF()
    private var lastTouchX = 0f
    private var touching = false

    val laneW get() = (ROAD_RIGHT - ROAD_LEFT) / LANES
    fun laneX(i: Int) = ROAD_LEFT + laneW * (i + 0.5f)
    val progress get() = (distance / goal).coerceIn(0f, 1f)

    init {
        resize(W, H)
    }

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
        level = 1
        runCoins = 0
        player.maxHp = 100f + 25f * save.level(Upgrade.HP)
        player.hp = player.maxHp
        setupLevel()
    }

    private fun nextLevel() {
        level++
        player.hp = min(player.maxHp, player.hp + player.maxHp * 0.3f)
        setupLevel()
    }

    private fun setupLevel() {
        obstacles.clear()
        items.clear()
        bullets.clear()
        particles.clear()
        texts.clear()
        distance = 0f
        nextSpawn = 700f
        levelCoins = 0
        levelKills = 0
        timeBonus = 0
        deathByStorm = false
        collapseT = 0f
        shake = 0f
        stormHurt = 0f

        val p = player
        p.maxSpeed = 620f + level * 45f
        p.speed = 0f
        p.x = W / 2
        p.targetX = p.x
        p.y = H * 0.78f
        p.shieldT = 0f
        p.weaponT = 0f
        p.skidT = 0f
        p.hurtT = 0f
        p.tilt = 0f

        // Pure driving time at full speed; the time limit leaves only a small margin
        // for crashes, which slow the car down.
        val cruise = 40f + level * 3f
        goal = p.maxSpeed * cruise
        val efficiency = min(0.95f, 0.90f + level * 0.01f)
        timeLimit = ceil(cruise / efficiency + 1.5f)
        timeLeft = timeLimit
        countdown = 3f
        setState(State.PLAYING)
    }

    private fun levelComplete() {
        timeBonus = (timeLeft * 3f).toInt()
        save.money += timeBonus
        runCoins += timeBonus
        save.bestLevel = max(save.bestLevel, level)
        save.save()
        setState(State.LEVEL_DONE)
    }

    private fun gameOver(byStorm: Boolean) {
        deathByStorm = byStorm
        player.hp = 0f
        explode(player.x, player.y, 0xFFFF7043.toInt(), 50)
        shake = 0.6f
        save.bestLevel = max(save.bestLevel, level - 1)
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
        State.PAUSED, State.GAME_OVER, State.LEVEL_DONE, State.SHOP -> {
            save.save(); setState(State.MENU); true
        }
        State.MENU -> false
    }

    // ---------------------------------------------------------------- update

    fun update(dt: Float) {
        anim += dt
        stateTime += dt
        if (shake > 0) shake -= dt
        when (state) {
            State.PLAYING -> updatePlay(dt)
            State.MENU, State.SHOP -> menuScroll += dt * 420f
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

    private fun updatePlay(dt: Float) {
        val p = player
        if (countdown > 0f) {
            countdown -= dt
            return
        }

        // Speed and distance
        p.speed = min(p.maxSpeed, p.speed + ACCEL * dt)
        val move = p.speed * dt
        distance += move

        // Timer and storm
        if (timeLeft > 0f) {
            timeLeft -= dt
            if (timeLeft <= 0f) {
                timeLeft = 0f
                texts.add(FloatText("HẾT GIỜ!", W / 2, H * 0.45f, 0xFFE040FB.toInt()))
            }
        } else {
            collapseT += dt
        }

        // Steering
        if (p.skidT > 0f) {
            p.skidT -= dt
            p.targetX += p.skidDir * 300f * dt
        }
        val minX = ROAD_LEFT + p.w / 2 + 4f
        val maxX = ROAD_RIGHT - p.w / 2 - 4f
        p.targetX = p.targetX.coerceIn(minX, maxX)
        val prevX = p.x
        p.x += (p.targetX - p.x) * min(1f, dt * 12f)
        val vx = (p.x - prevX) / max(dt, 0.001f)
        p.tilt += ((vx * 0.025f).coerceIn(-16f, 16f) - p.tilt) * min(1f, dt * 10f)

        if (p.shieldT > 0f) p.shieldT -= dt
        if (p.hurtT > 0f) p.hurtT -= dt
        if (stormHurt > 0f) stormHurt -= dt

        // Weapon: auto fire
        if (p.weaponT > 0f) {
            p.weaponT -= dt
            p.fireCd -= dt
            if (p.fireCd <= 0f) {
                p.fireCd = 0.26f - 0.025f * save.level(Upgrade.WEAPON)
                bullets.add(Bullet(p.x - 20f, p.y - p.h / 2))
                bullets.add(Bullet(p.x + 20f, p.y - p.h / 2))
            }
        }

        // World movement
        for (o in obstacles) {
            o.y += (p.speed - o.speed) * dt
            if (o.flash > 0) o.flash -= dt
        }
        for (it in items) it.y += p.speed * dt
        for (b in bullets) b.y -= BULLET_SPEED * dt
        for (t in trees) t.y += p.speed * dt
        val ti = trees.iterator()
        var respawn = 0
        while (ti.hasNext()) if (ti.next().y > H + 80f) {
            ti.remove(); respawn++
        }
        repeat(respawn) { spawnTree(-60f - Random.nextFloat() * 150f) }

        // Bullets vs obstacles
        for (b in bullets) {
            if (!b.alive) continue
            if (b.y < -40f) {
                b.alive = false; continue
            }
            for (o in obstacles) {
                if (!o.alive || o.type == ObstacleType.OIL) continue
                o.rect(tmpA, 1f)
                if (tmpA.contains(b.x, b.y)) {
                    b.alive = false
                    if (o.type.destructible) {
                        o.hp--
                        o.flash = 0.08f
                        if (o.hp <= 0) {
                            o.alive = false
                            levelKills++
                            explode(o.x, o.y, o.color, 26)
                            addCoins(o.type.reward, o.x, o.y)
                        }
                    } else {
                        sparks(b.x, b.y)
                    }
                    break
                }
            }
        }

        // Player vs obstacles
        p.rect(tmpA)
        for (o in obstacles) {
            if (!o.alive) continue
            if (!RectF.intersects(tmpA, o.rect(tmpB))) continue
            if (o.type == ObstacleType.OIL) {
                if (!o.used && p.shieldT <= 0f) {
                    o.used = true
                    p.skidT = 0.9f
                    p.skidDir = if (Random.nextBoolean()) 1f else -1f
                    p.speed *= 0.7f
                    texts.add(FloatText("TRƯỢT!", p.x, p.y - 90f, 0xFFFFFFFF.toInt()))
                }
                continue
            }
            if (p.shieldT > 0f) {
                o.alive = false
                explode(o.x, o.y, o.color, 22)
                addCoins(max(1, o.type.reward / 2), o.x, o.y)
                p.speed *= 0.9f
                shake = 0.15f
            } else if (p.hurtT <= 0f) {
                val dmg = o.type.damage * (1f - 0.1f * save.level(Upgrade.ARMOR))
                p.hp -= dmg
                p.hurtT = 0.7f
                p.speed *= 0.3f
                o.alive = false
                explode(o.x, (o.y + p.y) / 2, o.color, 30)
                texts.add(FloatText("-${dmg.toInt()}", p.x, p.y - 90f, 0xFFFF5252.toInt()))
                shake = 0.35f
            }
        }

        // Player vs items
        for (it in items) {
            if (!it.alive) continue
            if (hypot(it.x - p.x, it.y - p.y) < 72f) {
                it.alive = false
                collect(it)
            }
        }

        // Spawning
        while (distance >= nextSpawn) spawnRow()

        // Storm damage
        if (abs(p.x - W / 2) > stormRadius()) {
            p.hp -= (35f + 25f * collapseT) * dt
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
        bullets.removeAll { !it.alive }

        if (p.hp <= 0f) {
            gameOver(false); return
        }
        if (distance >= goal) levelComplete()
    }

    /**
     * Radius of the safe zone around the road's centre line at the player's height.
     * The zone covers the whole screen at the start, shrinks to just the road when the
     * timer reaches zero, and then collapses to nothing over [COLLAPSE_TIME] seconds.
     */
    fun stormRadius(): Float {
        val roadZone = (ROAD_RIGHT - ROAD_LEFT) / 2f + 30f
        if (timeLeft <= 0f) return max(0f, roadZone * (1f - collapseT / COLLAPSE_TIME))
        val full = hypot(W / 2, max(player.y, H - player.y)) + 60f
        val frac = 1f - timeLeft / timeLimit
        if (frac < STORM_START) return full
        val t = ((frac - STORM_START) / (1f - STORM_START)).coerceIn(0f, 1f)
        val e = t * t * (3f - 2f * t)
        return full + (roadZone - full) * e
    }

    val stormWarning get() = timeLeft <= 0f || 1f - timeLeft / timeLimit >= STORM_START

    private fun spawnRow() {
        val gap = max(290f, 420f + Random.nextFloat() * 260f - level * 18f)
        nextSpawn += gap
        val y = -280f
        val maxBlocked = min(3, 1 + (level + 1) / 2)
        val count = Random.nextInt(1, maxBlocked + 1)
        val lanes = (0 until LANES).shuffled()
        for (i in 0 until count) obstacles.add(makeObstacle(lanes[i], y + Random.nextFloat() * 50f))
        if (Random.nextFloat() < 0.5f) {
            val free = lanes[count + Random.nextInt(LANES - count)]
            items.add(Item(randomItem(), laneX(free), y - 30f))
        }
    }

    private val carColors = intArrayOf(
        0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt(), 0xFFFB8C00.toInt(),
        0xFF8E24AA.toInt(), 0xFFECEFF1.toInt(), 0xFF00ACC1.toInt(), 0xFF6D4C41.toInt(),
    )

    private fun makeObstacle(lane: Int, y: Float): Obstacle {
        val roll = Random.nextInt(100)
        val type = when {
            roll < 34 -> ObstacleType.CAR
            roll < 44 -> if (level >= 2) ObstacleType.TRUCK else ObstacleType.CAR
            roll < 63 -> ObstacleType.BARREL
            roll < 75 -> ObstacleType.ROCK
            roll < 91 -> ObstacleType.CONE
            else -> ObstacleType.OIL
        }
        val x = laneX(lane) + (Random.nextFloat() - 0.5f) * 20f
        return when (type) {
            ObstacleType.CAR -> Obstacle(type, x, y, 180f + Random.nextFloat() * 160f, carColors.random())
            ObstacleType.TRUCK -> Obstacle(type, x, y - 50f, 140f + Random.nextFloat() * 90f, carColors.random())
            ObstacleType.BARREL -> Obstacle(type, x, y, 0f, 0xFFFF6F00.toInt())
            ObstacleType.ROCK -> Obstacle(type, x, y, 0f, 0xFF757575.toInt())
            ObstacleType.CONE -> Obstacle(type, x, y, 0f, 0xFFFF5722.toInt())
            ObstacleType.OIL -> Obstacle(type, x, y, 0f, 0xFF111111.toInt())
        }
    }

    private fun randomItem(): ItemType {
        val total = ItemType.entries.sumOf { it.weight }
        var r = Random.nextInt(total)
        for (t in ItemType.entries) {
            if (r < t.weight) return t
            r -= t.weight
        }
        return ItemType.COIN
    }

    private fun collect(it: Item) {
        val p = player
        burst(it.x, it.y, it.type.color)
        when (it.type) {
            ItemType.COIN -> addCoins(10 + level * 2, it.x, it.y)
            ItemType.HEALTH -> {
                p.hp = min(p.maxHp, p.hp + 30f)
                texts.add(FloatText("+30 MÁU", it.x, it.y, 0xFF69F0AE.toInt()))
            }
            ItemType.SHIELD -> {
                p.shieldT = 6f + 1.5f * save.level(Upgrade.SHIELD)
                texts.add(FloatText("LÁ CHẮN!", it.x, it.y, 0xFF82B1FF.toInt()))
            }
            ItemType.WEAPON -> {
                p.weaponT = 7f + 2f * save.level(Upgrade.WEAPON)
                p.fireCd = 0f
                texts.add(FloatText("VŨ KHÍ!", it.x, it.y, 0xFFFF8A80.toInt()))
            }
            ItemType.TIME -> if (timeLeft > 0f) {
                timeLeft += 5f
                texts.add(FloatText("+5 GIÂY", it.x, it.y, 0xFFEA80FC.toInt()))
            } else {
                addCoins(15, it.x, it.y)
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
        return when (state) {
            State.MENU -> listOf(
                b("play", "CHƠI", H * 0.55f, 0xFF43A047.toInt()),
                b("shop", "NÂNG CẤP", H * 0.55f + 130f, 0xFF1E88E5.toInt()),
            )
            State.SHOP -> {
                val list = ArrayList<Button>()
                val top = 300f
                Upgrade.entries.forEachIndexed { i, u ->
                    val lv = save.level(u)
                    val maxed = lv >= Upgrade.MAX_LEVEL
                    val cost = u.cost(lv)
                    val y = top + i * 190f + 70f
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
                list.add(b("menu", "QUAY LẠI", H - 170f, 0xFF546E7A.toInt()))
                list
            }
            State.PAUSED -> listOf(
                b("resume", "TIẾP TỤC", H * 0.45f, 0xFF43A047.toInt()),
                b("menu", "MENU", H * 0.45f + 130f, 0xFF546E7A.toInt()),
            )
            State.LEVEL_DONE -> listOf(
                b("next", "MÀN TIẾP", H * 0.62f, 0xFF43A047.toInt()),
                b("menu", "MENU", H * 0.62f + 130f, 0xFF546E7A.toInt()),
            )
            State.GAME_OVER -> listOf(
                b("play", "CHƠI LẠI", H * 0.62f, 0xFF43A047.toInt()),
                b("menu", "MENU", H * 0.62f + 130f, 0xFF546E7A.toInt()),
            )
            State.PLAYING -> emptyList()
        }
    }

    val pauseRect get() = RectF(W - 92f, 24f + topInset, W - 20f, 96f + topInset)

    fun touchDown(x: Float, y: Float) {
        if (state == State.PLAYING) {
            if (pauseRect.contains(x, y)) {
                pause(); return
            }
            touching = true
            lastTouchX = x
            return
        }
        if (stateTime < 0.5f) return
        val btn = buttons().firstOrNull { it.rect.contains(x, y) } ?: return
        if (!btn.enabled) return
        when (btn.id) {
            "play" -> startRun()
            "shop" -> setState(State.SHOP)
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
                }
            }
        }
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
