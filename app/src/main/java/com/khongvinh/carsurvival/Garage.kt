package com.khongvinh.carsurvival

import kotlin.math.min
import kotlin.math.roundToInt

/** Prices of vehicles and weapons are this much above the base table (+30%). */
const val PRICE_MUL = 1.3f

private fun price(base: Int) = ((base * PRICE_MUL) / 10f).roundToInt() * 10

/**
 * Vehicle family. A family unlocks once [unlockStage] stages are cleared and has
 * five versions ([Vehicle.variant] 0..4), each stronger and pricier than the last.
 * [mountX]/[mountY] is where the roof weapon sits in car space.
 */
enum class VehicleClass(val title: String, val icon: String, val unlockStage: Int, val mountX: Float, val mountY: Float) {
    MOTO("Xe máy", "🏍", 0, -46f, -40f),
    CAR("Xe hơi", "🚗", 5, -8f, -46f),
    PICKUP("Bán tải", "🛻", 10, -46f, -40f),
    ARMORED("Bọc thép", "🚙", 15, -6f, -64f),
    TANK("Xe tăng", "🪖", 20, -14f, -80f),
}

/** One buyable vehicle. Multipliers apply on top of the upgrade levels. */
class Vehicle(
    val id: Int,
    val cls: VehicleClass,
    val variant: Int,
    val title: String,
    val color: Int,
    val accent: Int,
    val hpMul: Float,
    val speedMul: Float,
    val powerMul: Float,
    val armor: Float,
    val fuelMul: Float,
    val grip: Float,
    /** How hard it rams: obstacle break speed and boss ram damage. */
    val ram: Float,
    baseCost: Int,
) {
    val cost = price(baseCost)

    /**
     * Built-in tuning level (0..5) that replaces the old upgrade shop: later
     * versions and heavier families come with a stronger engine, a bigger tank,
     * more rocket fuel and a fuller ammo box.
     */
    val tune = min(5f, (cls.ordinal * Vehicles.PER_CLASS + variant) * 0.25f)

    val maxHp get() = (100f + 30f * tune) * hpMul
    val topSpeed get() = (700f + 110f * tune) * speedMul
    val engineAcc get() = (520f + 80f * tune) * powerMul
    val traction get() = (1.0f + 0.15f * tune) * grip
    val totalArmor get() = min(0.7f, armor + 0.06f * tune)

    /** Seconds of throttle on a full tank. */
    val fuelTime get() = (22f + 6f * tune) * fuelMul

    /** Seconds of rocket boost. */
    val boostTime get() = 1.5f + 1.2f * tune
    val ammo get() = 20 + (20f * tune).toInt()
}

object Vehicles {
    const val PER_CLASS = 5

    private class Base(val hp: Float, val speed: Float, val power: Float, val armor: Float, val fuel: Float, val grip: Float, val ram: Float)

    private val bases = mapOf(
        VehicleClass.MOTO to Base(0.75f, 1.04f, 0.95f, 0f, 0.85f, 1.05f, 0.6f),
        VehicleClass.CAR to Base(1.0f, 1.10f, 1.05f, 0.05f, 1.0f, 1.0f, 0.9f),
        VehicleClass.PICKUP to Base(1.3f, 1.02f, 1.15f, 0.1f, 1.2f, 1.08f, 1.15f),
        VehicleClass.ARMORED to Base(1.8f, 1.0f, 1.3f, 0.22f, 1.3f, 1.1f, 1.45f),
        VehicleClass.TANK to Base(2.4f, 0.96f, 1.5f, 0.32f, 1.45f, 1.25f, 2.0f),
    )

    private val names = mapOf(
        VehicleClass.MOTO to listOf("Xe Cub Cũ", "Cào Cào Địa Hình", "Mô Tô Phân Khối Lớn", "Chopper Đinh Tán", "Mô Tô Chiến Binh"),
        VehicleClass.CAR to listOf("Xe Con Gỉ Sét", "Xe Cơ Bắp", "Xe Đua Rally", "Siêu Xe Gai Nhọn", "Chiến Xa Tử Thần"),
        VehicleClass.PICKUP to listOf("Bán Tải Cũ", "Bán Tải Săn Zombie", "Bán Tải Lưới Thép", "Bán Tải Off-road", "Bán Tải Quái Thú"),
        VehicleClass.ARMORED to listOf("Xe Chở Tiền", "Xe Đặc Nhiệm SWAT", "Xe Bọc Thép Lội Nước", "Xe Ủi Thép", "Pháo Đài Di Động"),
        VehicleClass.TANK to listOf("Tăng Hạng Nhẹ", "Tăng Sa Mạc", "Tăng Rằn Ri", "Tăng Hạng Nặng", "Tăng Hủy Diệt"),
    )

    /** Base prices before [PRICE_MUL]. */
    private val costs = mapOf(
        VehicleClass.MOTO to intArrayOf(0, 600, 1200, 2000, 3200),
        VehicleClass.CAR to intArrayOf(2000, 3000, 4200, 5600, 7500),
        VehicleClass.PICKUP to intArrayOf(5000, 6500, 8500, 11000, 14000),
        VehicleClass.ARMORED to intArrayOf(10000, 13000, 16500, 20500, 25000),
        VehicleClass.TANK to intArrayOf(18000, 23000, 29000, 36000, 45000),
    )

    /** Body colour, accent colour for each version. */
    private val colors = mapOf(
        VehicleClass.MOTO to longArrayOf(0xFF8D6E63, 0xFFFFCC80, 0xFF43A047, 0xFFFFEB3B, 0xFFE53935, 0xFFFFFFFF, 0xFF212121, 0xFFFF9800, 0xFF37474F, 0xFFFF1744),
        VehicleClass.CAR to longArrayOf(0xFF8D6E63, 0xFFBF360C, 0xFF1565C0, 0xFFFFFFFF, 0xFFFDD835, 0xFF1A237E, 0xFF212121, 0xFFFF3D00, 0xFF4A148C, 0xFFFF1744),
        VehicleClass.PICKUP to longArrayOf(0xFFD84315, 0xFF5D4037, 0xFF2E7D32, 0xFFFFC107, 0xFF546E7A, 0xFFB0BEC5, 0xFFF9A825, 0xFF212121, 0xFF263238, 0xFF76FF03),
        VehicleClass.ARMORED to longArrayOf(0xFF78909C, 0xFFFFD54F, 0xFF263238, 0xFF1E88E5, 0xFF4E6B3A, 0xFFC5E1A5, 0xFFFFB300, 0xFF212121, 0xFF3E2723, 0xFFFF5722),
        VehicleClass.TANK to longArrayOf(0xFF556B2F, 0xFF8BC34A, 0xFFC8A165, 0xFF6D4C41, 0xFF4E5B31, 0xFF2E3B1F, 0xFF37474F, 0xFF90A4AE, 0xFF212121, 0xFFFF1744),
    )

    val all: List<Vehicle> = VehicleClass.entries.flatMap { cls ->
        val b = bases.getValue(cls)
        (0 until PER_CLASS).map { k ->
            val col = colors.getValue(cls)
            Vehicle(
                id = cls.ordinal * PER_CLASS + k,
                cls = cls,
                variant = k,
                title = names.getValue(cls)[k],
                color = col[k * 2].toInt(),
                accent = col[k * 2 + 1].toInt(),
                hpMul = b.hp * (1f + 0.1f * k),
                speedMul = b.speed * (1f + 0.03f * k),
                powerMul = b.power * (1f + 0.05f * k),
                armor = b.armor + 0.025f * k,
                fuelMul = b.fuel * (1f + 0.06f * k),
                grip = b.grip * (1f + 0.03f * k),
                ram = b.ram * (1f + 0.1f * k),
                baseCost = costs.getValue(cls)[k],
            )
        }
    }

    fun of(id: Int) = all[id.coerceIn(0, all.size - 1)]
    fun of(cls: VehicleClass, variant: Int) = all[cls.ordinal * PER_CLASS + variant.coerceIn(0, PER_CLASS - 1)]
}

/**
 * Weapon mounted on the vehicle; fires automatically. Stats are for level 0;
 * each weapon level adds damage, fire rate and range (see [Game.weaponStats]).
 */
enum class WeaponType(
    val title: String,
    val icon: String,
    val desc: String,
    private val baseCost: Int,
    val unlockStage: Int,
    val damage: Float,
    /** Seconds between shots. */
    val interval: Float,
    val range: Float,
    val pierce: Int,
    /** Blast radius, 0 = none. */
    val splash: Float,
    val speed: Float,
    val pellets: Int = 1,
    val spread: Float = 0f,
    /** Ammo used per shot (fast weapons use less). */
    val ammoUse: Float = 1f,
) {
    PISTOL("Súng lục", "🔫", "Rẻ, bền, bắn chậm", 0, 0, 1f, 0.34f, 760f, 1, 0f, 1900f),
    KNIFE("Dao chém", "🔪", "Lưỡi dao trước mũi xe, không tốn đạn", 500, 1, 2f, 0.3f, 190f, 3, 0f, 0f),
    SMG("Súng máy", "🔫", "Bắn nhanh", 1500, 1, 1f, 0.14f, 900f, 1, 0f, 2100f),
    CROSSBOW("Nỏ", "🏹", "Mũi tên xuyên 3 mục tiêu", 2400, 2, 3f, 0.45f, 950f, 3, 0f, 1600f),
    SHOTGUN("Súng hoa cải", "💥", "5 viên đạn chùm, tầm gần", 3200, 3, 1.2f, 0.7f, 560f, 1, 0f, 1700f, 5, 0.2f),
    CATAPULT("Máy ném đá", "☄", "Đá nổ diện rộng", 4500, 5, 4f, 0.8f, 900f, 1, 160f, 0f),
    FLAMER("Súng phun lửa", "🔥", "Lửa xuyên mọi thứ, tầm ngắn", 7000, 7, 0.55f, 0.07f, 400f, 99, 0f, 950f, ammoUse = 0.3f),
    ROCKET("Tên lửa", "🚀", "Tự dẫn đường, nổ cực mạnh", 11000, 10, 7f, 1.3f, 1100f, 1, 210f, 700f),
    LASER("Súng laser", "⚡", "Tia xuyên thấu tức thì", 16000, 13, 2.4f, 0.5f, 1150f, 99, 0f, 0f),
    MINIGUN("Súng nòng xoay", "⚙", "Mưa đạn không ngừng", 22000, 16, 1.1f, 0.055f, 1000f, 1, 0f, 2300f, ammoUse = 0.5f);

    val cost = price(baseCost)

    /** Price of the next weapon level (0-based current level). */
    fun upgradeCost(level: Int) = price(((if (baseCost == 0) 400 else baseCost) * 0.35f * (level + 1)).toInt() + 150 * level * level)

    companion object {
        const val MAX_LEVEL = 5
    }
}

/** Weapon stats after levels. */
class WeaponStats(val damage: Float, val interval: Float, val range: Float, val pierce: Int, val splash: Float)

fun WeaponType.stats(level: Int) = WeaponStats(
    damage = damage * (1f + 0.18f * level),
    interval = interval * (1f - 0.06f * level),
    range = range * (1f + 0.05f * level),
    pierce = pierce + (if (pierce in 2..10) level / 2 else 0),
    splash = splash * (1f + 0.06f * level),
)
