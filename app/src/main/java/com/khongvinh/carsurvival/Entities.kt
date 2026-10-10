package com.khongvinh.carsurvival

import android.graphics.RectF

enum class State { MENU, SHOP, PLAYING, PAUSED, LEVEL_DONE, GAME_OVER }

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
    ROCK(84f, 72f, 30, 0, 0, false),
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
}

class Item(val type: ItemType, var x: Float, var y: Float) {
    var alive = true
}

class Bullet(var x: Float, var y: Float) {
    var alive = true
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
    val w = 76f
    val h = 132f
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

    fun rect(out: RectF, shrink: Float = 0.8f): RectF {
        val hw = w * shrink / 2
        val hh = h * shrink / 2
        out.set(x - hw, y - hh, x + hw, y + hh)
        return out
    }
}
