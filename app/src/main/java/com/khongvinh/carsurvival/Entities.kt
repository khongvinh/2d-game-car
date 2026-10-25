package com.khongvinh.carsurvival

import kotlin.math.min

enum class State { MENU, MAPS, GARAGE, PLAYING, PAUSED, RESULT, STAGE_DONE, VICTORY }

enum class Upgrade(val title: String, val desc: String, val base: Int) {
    ENGINE("Động cơ", "+tốc độ & lực kéo", 150),
    FUEL("Bình xăng", "+25% xăng", 120),
    BOOST("Tên lửa đẩy", "+bình tên lửa", 180),
    ARMOR("Giáp & máu", "+máu, -sát thương", 140),
    AMMO("Đạn dược", "+20 viên mỗi cấp", 100),
    GRIP("Lốp xe", "+bám đường", 110);

    /** Upgrades are a long grind: each level costs much more than the last. */
    fun cost(level: Int) = (base * (level + 1) * 1.5f).toInt() + 100 * level * level

    companion object {
        const val MAX_LEVEL = 5
    }
}

enum class ItemType(val icon: String, val color: Int) {
    COIN("$", 0xFFFFC107.toInt()),
    FUEL("⛽", 0xFFE53935.toInt()),
    REPAIR("✚", 0xFF43A047.toInt()),
    SHIELD("🛡", 0xFF1E88E5.toInt()),
    AMMO("🎯", 0xFF6D4C41.toInt()),
    TIME("⏱", 0xFF8E24AA.toInt()),

    /** Unlocked after clearing stage 5: one free revive for 10 s. */
    CLONE("👥", 0xFF00BFA5.toInt()),
}

class Item(val type: ItemType, val x: Float, val y: Float) {
    var alive = true
}

enum class ZombieType(
    val w: Float,
    val h: Float,
    val mass: Float,
    val hp: Int,
    val speed: Float,
    val reward: Int,
    val flying: Boolean = false,
) {
    WALKER(34f, 92f, 0.07f, 2, 35f, 5),
    RUNNER(30f, 88f, 0.06f, 1, 110f, 6),
    FAT(58f, 98f, 0.2f, 5, 22f, 12),

    /** Volcano: low, fast fire lizard. */
    LIZARD(74f, 40f, 0.08f, 2, 140f, 7),

    /** Night forest: bat that swoops at the car. */
    BAT(48f, 30f, 0.03f, 1, 170f, 6, true),

    /** Alien world: little grey invader. */
    ALIEN(32f, 80f, 0.06f, 2, 75f, 7),

    /** Alien world: heavy slime blob. */
    BLOB(72f, 62f, 0.18f, 5, 25f, 12),
}

class Zombie(val type: ZombieType, var x: Float, var y: Float) {
    var hp = type.hp.toFloat()

    /** Seconds left burning (flamethrower). */
    var burnT = 0f

    /** Height above the ground for flying monsters. */
    var fly = if (type.flying) 150f + (x % 60f) else 0f
    var alive = true
    var phase = (x * 0.37f) % 6.28f
    val shirt = SHIRTS[(x.toInt() / 7 and 0x7fffffff) % SHIRTS.size]

    companion object {
        val SHIRTS = intArrayOf(
            0xFF5D4037.toInt(), 0xFF455A64.toInt(), 0xFF7B1FA2.toInt(),
            0xFF1565C0.toInt(), 0xFF827717.toInt(), 0xFFAD1457.toInt(),
        )
    }
}

enum class ObstacleType(val w: Float, val h: Float, val mass: Float, val breakSpeed: Float, val hp: Int, val damage: Float, val reward: Int) {
    CRATE(62f, 62f, 0.22f, 260f, 2, 4f, 2),
    BARREL(46f, 64f, 0.15f, 0f, 1, 10f, 3),
    WRECK(170f, 70f, 0.9f, 720f, 8, 18f, 15),
}

class Obstacle(val type: ObstacleType, var x: Float, var y: Float) {
    var hp = type.hp.toFloat()
    var alive = true
    var flash = 0f
    var vy = 0f

    /** Durability against ramming (1 = intact); each solid ram removes [RAM_HIT]. */
    var ramHp = 1f
    var ramCd = 0f

    /** Seconds the durability bar stays visible after a failed attempt. */
    var barT = 0f

    /** Bar fraction: the weaker of ramming durability and weapon damage. */
    val barFrac get() = min(ramHp, if (type.hp > 0) hp / type.hp else 1f).coerceIn(0f, 1f)

    companion object {
        const val RAM_HIT = 0.25f

        /** Minimum speed into the obstacle (units/s) for a ram to count. */
        const val RAM_SPEED = 150f
    }
}

/** Something flying after being hit: a zombie, crate, wreck piece. */
class Debris(
    val kind: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rot: Float,
    var spin: Float,
    val w: Float,
    val h: Float,
    val color: Int,
) {
    var life = 6f
    var grounded = false

    companion object {
        const val ZOMBIE = 0
        const val PLANK = 1
        const val METAL = 2
        const val FAT_ZOMBIE = 3

        /** CREATURE + ZombieType.ordinal for the regional monsters. */
        const val CREATURE = 100
    }
}

class Projectile(
    val kind: WeaponType,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    var pierce: Int,
    val splash: Float,
) {
    var alive = true
    var life = 1.4f
    var age = 0f
    val hit = HashSet<Any>()
}

/** Laser beam drawn for a moment after it fires. */
class Beam(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    var life = 0.18f
}

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val size: Float,
    val color: Int,
    val gravity: Float = 0f,
    /** How much the particle grows over its life (smoke, dust). */
    val grow: Float = 0f,
)

class FloatText(val text: String, var x: Float, var y: Float, val color: Int) {
    var life = 1.3f
    var size = 44f
}

class Decor(val kind: Int, val x: Float, val size: Float)

class Button(val id: String, val label: String, val l: Float, val t: Float, val r: Float, val b: Float, val color: Int, val enabled: Boolean = true) {
    fun contains(x: Float, y: Float) = x in l..r && y in t..b
    val cx get() = (l + r) / 2
    val cy get() = (t + b) / 2
}

/** Rigid car body with two spring wheels. World y points down. */
class Car {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var angle = 0f
    var spin = 0f
    var wheelRot = 0f
    val grounded = BooleanArray(2)
    val compress = FloatArray(2)

    var hp = 100f
    var maxHp = 100f
    var fuel = 1f
    var maxFuel = 1f
    var boost = 1f
    var maxBoost = 1f
    var ammo = 0f
    var shieldT = 0f
    var cloneT = 0f
    var hurtT = 0f
    var flipT = 0f
    var stuckT = 0f
    var fireCd = 0f
    var aim = 0f
    var boosting = false

    /** Smoothed throttle (-1 brake .. 1 gas) and lean, so inputs ramp instead of snapping. */
    var throttle = 0f
    var leanIn = 0f

    /** Visual-only body pitch from acceleration (squat under gas, dive under brakes). */
    var squat = 0f

    /** Knife swing animation timer. */
    var slashT = 0f

    val fwdX get() = kotlin.math.cos(angle)
    val fwdY get() = kotlin.math.sin(angle)
    val forwardSpeed get() = vx * fwdX + vy * fwdY

    companion object {
        const val HALF_LEN = 82f
        const val HALF_H = 26f
        const val WHEEL_R = 24f
        val WHEEL_X = floatArrayOf(-56f, 58f)
        const val WHEEL_Y = 30f
    }
}

/**
 * End-of-stage boss on every 5th stage. [tier] 1 = giant zombie (stage 5),
 * 2 = fire dragon (10), 3 = vampire bat (15), 4 = alien (20).
 */
class Boss(val tier: Int, var x: Float, var y: Float) {
    val name = NAMES[(tier - 1).coerceIn(0, NAMES.size - 1)]
    val maxHp = HP[(tier - 1).coerceIn(0, HP.size - 1)]
    var hp = maxHp
    val w = floatArrayOf(100f, 260f, 240f, 150f)[(tier - 1).coerceIn(0, 3)]
    val h = floatArrayOf(270f, 130f, 110f, 320f)[(tier - 1).coerceIn(0, 3)]
    val speed = 45f + tier * 12f
    val flying = tier == 2 || tier == 3

    /** Current height above the ground (flying bosses). */
    var lift = if (flying) 240f else 0f
    var diveT = 0f
    var breathT = 0f
    var breathCd = 3f
    var summonCd = 6f

    /** Centre of the body in world coordinates. */
    val cy get() = y - lift - h / 2
    var alive = true
    var engaged = false
    var throwCd = 2f
    var slamCd = 1.5f
    var ramCd = 0f
    var flash = 0f
    var throwAnim = 0f
    var slamAnim = 0f
    var anim = 0f

    /** Small hits are summed and shown as one number. */
    var pendingDmg = 0f
    var dmgT = 0f

    companion object {
        val NAMES = arrayOf("GÃ KHỔNG LỒ", "RỒNG LỬA", "DƠI HÚT MÁU", "QUÁI VẬT NGOÀI HÀNH TINH")
        val HP = floatArrayOf(50f, 120f, 180f, 240f)
    }
}

/** Boss projectile: [kind] 0 = boulder, 1 = fireball, 2 = plasma orb (flies straight). */
class BossRock(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int = 0) {
    var alive = true
    var rot = 0f
    var life = 4f
}
