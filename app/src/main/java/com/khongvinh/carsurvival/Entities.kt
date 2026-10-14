package com.khongvinh.carsurvival

import android.graphics.RectF

enum class State { MENU, SHOP, GARAGE, PLAYING, PAUSED, LEVEL_DONE, STAGE_DONE, VICTORY, GAME_OVER }

enum class ObstacleType(
    val w: Float,
    val h: Float,
    val damage: Int,
    val reward: Int,
    val hp: Int,
    val destructible: Boolean,
) {
    CAR(78f, 136f, 25, 8, 2, true),
    TRUCK(92f, 230f, 35, 15, 4, true),
    BARREL(58f, 58f, 15, 3, 1, true),
    ROCK(84f, 72f, 30, 2, 0, false),
    CONE(40f, 46f, 8, 1, 1, true),
    OIL(116f, 84f, 0, 0, 0, false),
}

class Obstacle(
    val type: ObstacleType,
    var x: Float,
    var y: Float,
    /** Forward speed in world units/s (0 for static obstacles). */
    val speed: Float,
    val color: Int,
) {
    var hp = type.hp
    var alive = true
    var used = false
    var flash = 0f

    fun rect(out: RectF, shrink: Float = 0.82f): RectF {
        val hw = type.w * shrink / 2
        val hh = type.h * shrink / 2
        out.set(x - hw, y - hh, x + hw, y + hh)
        return out
    }
}

enum class ItemType(val icon: String, val color: Int, val weight: Int) {
    COIN("$", 0xFFFFC107.toInt(), 40),
    HEALTH("✚", 0xFF43A047.toInt(), 18),
    SHIELD("🛡", 0xFF1E88E5.toInt(), 15),
    WEAPON("🚀", 0xFFE53935.toInt(), 17),
    TIME("⏱", 0xFF8E24AA.toInt(), 10),

    /** Unlocked after clearing stage 5: a twin car that acts as an extra life for 10 s. */
    CLONE("👥", 0xFF00BFA5.toInt(), 9),
}

class Item(val type: ItemType, var x: Float, var y: Float) {
    var alive = true
}

/** Selectable cars (garage unlocks after clearing stage 1). */
enum class CarType(
    val title: String,
    val color: Int,
    val hpMul: Float,
    val speedMul: Float,
    val handling: Float,
    val armor: Float,
    val cost: Int,
    val w: Float,
    val h: Float,
) {
    RACER("Xe đua", 0xFFE53935.toInt(), 1.0f, 1.0f, 12f, 0f, 0, 76f, 132f),
    PICKUP("Bán tải", 0xFF43A047.toInt(), 1.4f, 0.96f, 11f, 0.05f, 800, 82f, 140f),
    SUPER("Siêu xe", 0xFFFFD600.toInt(), 0.85f, 1.12f, 16f, 0f, 1500, 72f, 128f),
    TANK("Bọc thép", 0xFF546E7A.toInt(), 1.8f, 0.92f, 9f, 0.2f, 3000, 86f, 146f),
}

/** Weapon fired while a 🚀 pickup is active. */
enum class WeaponType(val title: String, val icon: String, val desc: String, val cost: Int, val interval: Float) {
    GUN("Súng máy", "🔫", "2 nòng, bắn nhanh", 0, 0.26f),
    CROSSBOW("Nỏ", "🏹", "Tên xuyên 3 mục tiêu", 1000, 0.5f),
    CATAPULT("Máy ném đá", "☄", "Nổ diện rộng, phá đá", 2000, 0.9f),
}

class Projectile(val kind: WeaponType, var x: Float, var y: Float) {
    var alive = true
    var pierce = if (kind == WeaponType.CROSSBOW) 3 else 1
    val damage = when (kind) {
        WeaponType.GUN -> 1
        WeaponType.CROSSBOW -> 3
        WeaponType.CATAPULT -> 4
    }

    /** Catapult only: distance flown so far (it lands and explodes at [CATAPULT_RANGE]). */
    var travel = 0f
    val hit = HashSet<Obstacle>()

    companion object {
        const val CATAPULT_RANGE = 520f
        const val CATAPULT_RADIUS = 140f
    }
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
)

class FloatText(val text: String, var x: Float, var y: Float, val color: Int) {
    var life = 1.2f
}

class Tree(var x: Float, var y: Float, val size: Float, val dark: Boolean)

class Button(val id: String, val label: String, val rect: RectF, val color: Int, val enabled: Boolean = true)

class Player {
    var x = 360f
    var y = 1000f
    var w = 76f
    var h = 132f
    var color = 0xFFE53935.toInt()
    var handling = 12f
    var armor = 0f
    var targetX = 360f
    var speed = 0f
    var maxSpeed = 700f
    var hp = 100f
    var maxHp = 100f
    var shieldT = 0f
    var weaponT = 0f
    var fireCd = 0f
    var skidT = 0f
    var skidDir = 1f
    var hurtT = 0f
    var tilt = 0f
    var cloneT = 0f
    var cloneX = 360f

    fun rect(out: RectF, shrink: Float = 0.8f): RectF {
        val hw = w * shrink / 2
        val hh = h * shrink / 2
        out.set(x - hw, y - hh, x + hw, y + hh)
        return out
    }

    fun cloneRect(out: RectF, shrink: Float = 0.8f): RectF {
        val hw = w * shrink / 2
        val hh = h * shrink / 2
        out.set(cloneX - hw, y - hh, cloneX + hw, y + hh)
        return out
    }
}
