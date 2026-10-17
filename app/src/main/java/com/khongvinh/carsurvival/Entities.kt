package com.khongvinh.carsurvival

import kotlin.math.min

enum class State { MENU, SHOP, GARAGE, PLAYING, PAUSED, RESULT, STAGE_DONE, VICTORY }

/** Selectable cars (garage unlocks after clearing stage 1). */
enum class CarType(
    val title: String,
    val color: Int,
    val hpMul: Float,
    val speedMul: Float,
    val powerMul: Float,
    val armor: Float,
    val fuelMul: Float,
    val cost: Int,
) {
    PICKUP("Bán tải cũ", 0xFFD84315.toInt(), 1.0f, 1.0f, 1.0f, 0f, 1.0f, 0),
    MUSCLE("Xe cơ bắp", 0xFF1565C0.toInt(), 0.9f, 1.18f, 1.15f, 0f, 0.95f, 1500),
    VAN("Xe tải thùng", 0xFFECEFF1.toInt(), 1.5f, 0.95f, 1.1f, 0.1f, 1.4f, 3000),
    TANK("Xe bọc thép", 0xFF4E5B31.toInt(), 2.0f, 1.05f, 1.35f, 0.3f, 1.2f, 6000),
}

/** Roof-mounted weapon; fires automatically at zombies and obstacles ahead. */
enum class WeaponType(val title: String, val icon: String, val desc: String, val cost: Int, val interval: Float) {
    GUN("Súng máy", "🔫", "Bắn nhanh", 0, 0.16f),
    CROSSBOW("Nỏ", "🏹", "Xuyên 3 mục tiêu", 1200, 0.45f),
    CATAPULT("Máy ném đá", "☄", "Nổ diện rộng", 2500, 0.8f),
}

enum class Upgrade(val title: String, val desc: String, val base: Int) {
    ENGINE("Động cơ", "+tốc độ & lực kéo", 150),
    FUEL("Bình xăng", "+25% xăng", 120),
    BOOST("Tên lửa đẩy", "+bình tên lửa", 180),
    ARMOR("Giáp & máu", "+máu, -sát thương", 140),
    AMMO("Đạn dược", "+20 viên mỗi cấp", 100),
    GRIP("Lốp xe", "+bám đường", 110);

    fun cost(level: Int) = base * (level + 1) + 50 * level * level

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

enum class ZombieType(val w: Float, val h: Float, val mass: Float, val hp: Int, val speed: Float, val reward: Int) {
    WALKER(34f, 92f, 0.07f, 2, 35f, 5),
    RUNNER(30f, 88f, 0.06f, 1, 110f, 6),
    FAT(58f, 98f, 0.2f, 5, 22f, 12),
}

class Zombie(val type: ZombieType, var x: Float, var y: Float) {
    var hp = type.hp
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
    var hp = type.hp
    var alive = true
    var flash = 0f
    var vy = 0f

    /** Durability against ramming (1 = intact); each solid ram removes [RAM_HIT]. */
    var ramHp = 1f
    var ramCd = 0f

    /** Seconds the durability bar stays visible after a failed attempt. */
    var barT = 0f

    /** Bar fraction: the weaker of ramming durability and weapon damage. */
    val barFrac get() = min(ramHp, if (type.hp > 0) hp.toFloat() / type.hp else 1f).coerceIn(0f, 1f)

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
    }
}

class Projectile(val kind: WeaponType, var x: Float, var y: Float, var vx: Float, var vy: Float) {
    var alive = true
    var pierce = if (kind == WeaponType.CROSSBOW) 3 else 1
    var life = 1.4f
    val damage = when (kind) {
        WeaponType.GUN -> 1
        WeaponType.CROSSBOW -> 3
        WeaponType.CATAPULT -> 4
    }
    val hit = HashSet<Any>()
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
    var ammo = 0
    var shieldT = 0f
    var cloneT = 0f
    var hurtT = 0f
    var flipT = 0f
    var stuckT = 0f
    var fireCd = 0f
    var aim = 0f
    var boosting = false

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

/** End-of-stage boss on every 5th stage; [tier] 1..4. */
class Boss(val tier: Int, var x: Float, var y: Float) {
    val name = NAMES[(tier - 1).coerceIn(0, NAMES.size - 1)]
    val maxHp = HP[(tier - 1).coerceIn(0, HP.size - 1)]
    var hp = maxHp
    val w = 90f + tier * 10f
    val h = 250f + tier * 20f
    val speed = 45f + tier * 12f
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
        val NAMES = arrayOf("GÃ KHỔNG LỒ", "BẠO CHÚA", "QUÁI VẬT DUNG NHAM", "VUA ZOMBIE")
        val HP = floatArrayOf(50f, 90f, 140f, 200f)
    }
}

/** Boulder thrown by a boss. */
class BossRock(var x: Float, var y: Float, var vx: Float, var vy: Float) {
    var alive = true
    var rot = 0f
}
