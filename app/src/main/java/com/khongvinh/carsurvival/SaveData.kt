package com.khongvinh.carsurvival

import android.content.Context

/** Persistent wallet, best level and upgrade levels. */
class SaveData(context: Context) {
    private val prefs = context.getSharedPreferences("car_survival", Context.MODE_PRIVATE)

    var money = prefs.getInt("money", 0)
    var bestLevel = prefs.getInt("bestLevel", 0)
    val upgrades = IntArray(Upgrade.entries.size) { prefs.getInt("up$it", 0) }

    fun level(u: Upgrade) = upgrades[u.ordinal]

    fun save() {
        val e = prefs.edit()
        e.putInt("money", money)
        e.putInt("bestLevel", bestLevel)
        upgrades.forEachIndexed { i, v -> e.putInt("up$i", v) }
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
