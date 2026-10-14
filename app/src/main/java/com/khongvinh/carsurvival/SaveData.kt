package com.khongvinh.carsurvival

import android.content.Context

/** Persistent wallet, stage progress, garage and upgrade levels. */
class SaveData(context: Context) {
    private val prefs = context.getSharedPreferences("car_survival", Context.MODE_PRIVATE)

    var money = prefs.getInt("money", 0)

    /** Number of stages (ải) cleared, 0..[Game.STAGES]. */
    var bestStage = prefs.getInt("bestStage", 0)

    /** Stage chosen in the menu to start a run from. */
    var stageSel = prefs.getInt("stageSel", 1)
    val upgrades = IntArray(Upgrade.entries.size) { prefs.getInt("up$it", 0) }
    var ownedCars = prefs.getInt("ownedCars", 1)
    var ownedWeapons = prefs.getInt("ownedWeapons", 1)
    var car = prefs.getInt("car", 0)
    var weapon = prefs.getInt("weapon", 0)
    var sound = prefs.getBoolean("sound", true)

    fun level(u: Upgrade) = upgrades[u.ordinal]

    val carType get() = CarType.entries[car]
    val weaponType get() = WeaponType.entries[weapon]
    val garageUnlocked get() = bestStage >= 1
    val cloneUnlocked get() = bestStage >= 5

    fun ownsCar(i: Int) = ownedCars and (1 shl i) != 0
    fun ownsWeapon(i: Int) = ownedWeapons and (1 shl i) != 0

    fun save() {
        val e = prefs.edit()
        e.putInt("money", money)
        e.putInt("bestStage", bestStage)
        e.putInt("stageSel", stageSel)
        upgrades.forEachIndexed { i, v -> e.putInt("up$i", v) }
        e.putInt("ownedCars", ownedCars)
        e.putInt("ownedWeapons", ownedWeapons)
        e.putInt("car", car)
        e.putInt("weapon", weapon)
        e.putBoolean("sound", sound)
        e.apply()
    }
}

enum class Upgrade(val title: String, val desc: String) {
    HP("Máu tối đa", "+25 máu mỗi cấp"),
    ARMOR("Giáp xe", "-10% sát thương mỗi cấp"),
    WEAPON("Vũ khí", "Bắn lâu và nhanh hơn"),
    SHIELD("Lá chắn", "+1.5 giây mỗi cấp");

    fun cost(level: Int) = 120 * (level + 1)

    companion object {
        const val MAX_LEVEL = 5
    }
}
