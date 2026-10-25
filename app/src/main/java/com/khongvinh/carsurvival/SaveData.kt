package com.khongvinh.carsurvival

import android.content.Context

/** Persistent wallet, stage progress, garage and upgrade levels. */
class SaveData(context: Context) {
    companion object {
        const val MAX_RECORDS = 10
    }

    private val prefs = context.getSharedPreferences("car_survival_v3", Context.MODE_PRIVATE)

    var money = prefs.getInt("money", 0)

    /** Number of stages (ải) cleared, 0..[Game.STAGES]. */
    var bestStage = prefs.getInt("bestStage", 0)

    /** Stage chosen in the menu. */
    var stageSel = prefs.getInt("stageSel", 1)

    /** Days survived (one run = one day), like Earn to Die. */
    var day = prefs.getInt("day", 1)

    /** Best distance (m) reached on each stage, to draw the "record" flag. */
    val best = IntArray(Game.STAGES + 1) { prefs.getInt("best$it", 0) }
    /** Bit per [Vehicles.all] id; the first motorbike is free. */
    var ownedVehicles = prefs.getInt("ownedVeh", 1)
    var vehicle = prefs.getInt("veh", 0)

    /** Bit per [WeaponType] ordinal; the pistol is free. */
    var ownedWeapons = prefs.getInt("ownedWpn", 1)
    var weapon = prefs.getInt("wpn", 0)
    val weaponLevels = IntArray(WeaponType.entries.size) { prefs.getInt("wl$it", 0) }
    var sound = prefs.getBoolean("sound", true)
    var music = prefs.getBoolean("music", true)
    var bestScore = prefs.getInt("bestScore", 0)

    /** Leaderboard of the longest runs: metres, stage, vehicle id, day; best first. */
    val records = ArrayList<IntArray>().also { list ->
        for (row in prefs.getString("runs", "")?.split(";") ?: emptyList()) {
            val v = row.split(",").mapNotNull { it.toIntOrNull() }
            if (v.size == 4) list.add(v.toIntArray())
        }
    }

    /** Adds a run to the leaderboard; returns its rank (1-based) or 0 if it did not make it. */
    fun addRecord(m: Int, stage: Int, vehicle: Int, day: Int): Int {
        if (m <= 0) return 0
        val row = intArrayOf(m, stage, vehicle, day)
        var at = records.indexOfFirst { m > it[0] }
        if (at < 0) at = records.size
        if (at >= MAX_RECORDS) return 0
        records.add(at, row)
        while (records.size > MAX_RECORDS) records.removeAt(records.size - 1)
        return at + 1
    }

    init {
        migrateGarage()
    }

    /**
     * Saves from before the big garage (v4.4) had 4 cars and 3 weapons: bought cars
     * are refunded, the machine gun / crossbow / catapult carry over.
     */
    private fun migrateGarage() {
        val v = prefs.getInt("garageV", 0)
        if (v >= 3) return
        // v4.6: the upgrade shop is gone (vehicles carry their own tuning): refund it
        val upBase = intArrayOf(150, 120, 180, 140, 100, 110)
        val e0 = prefs.edit()
        for (i in upBase.indices) {
            val lv = prefs.getInt("up$i", 0)
            for (l in 0 until lv) money += (upBase[i] * (l + 1) * 1.5f).toInt() + 100 * l * l
            e0.remove("up$i")
        }
        e0.putInt("garageV", 3).apply()
        if (v >= 2) {
            save()
            return
        }
        if (prefs.contains("ownedCars")) {
            val oldCars = prefs.getInt("ownedCars", 1)
            val refund = intArrayOf(0, 1500, 3000, 6000)
            for (i in 1..3) if (oldCars and (1 shl i) != 0) money += refund[i]
            val oldW = prefs.getInt("ownedWeapons", 1)
            val map = intArrayOf(WeaponType.SMG.ordinal, WeaponType.CROSSBOW.ordinal, WeaponType.CATAPULT.ordinal)
            ownedWeapons = 1
            for (i in 0..2) if (oldW and (1 shl i) != 0) ownedWeapons = ownedWeapons or (1 shl map[i])
            weapon = map[prefs.getInt("weapon", 0).coerceIn(0, 2)]
        }
        prefs.edit().putInt("garageV", 3).remove("ownedCars").remove("car").remove("ownedWeapons").remove("weapon").apply()
        save()
    }

    val vehicleType get() = Vehicles.of(vehicle)
    val weaponType get() = WeaponType.entries[weapon.coerceIn(0, WeaponType.entries.size - 1)]
    fun weaponLevel(w: WeaponType) = weaponLevels[w.ordinal]
    val cloneUnlocked get() = bestStage >= 5

    fun ownsVehicle(id: Int) = ownedVehicles and (1 shl id) != 0
    fun ownsWeapon(i: Int) = ownedWeapons and (1 shl i) != 0
    fun classUnlocked(c: VehicleClass) = bestStage >= c.unlockStage
    fun weaponUnlocked(w: WeaponType) = bestStage >= w.unlockStage

    fun save() {
        val e = prefs.edit()
        e.putInt("money", money)
        e.putInt("bestStage", bestStage)
        e.putInt("stageSel", stageSel)
        e.putInt("day", day)
        best.forEachIndexed { i, v -> e.putInt("best$i", v) }
        e.putInt("ownedVeh", ownedVehicles)
        e.putInt("veh", vehicle)
        e.putInt("ownedWpn", ownedWeapons)
        e.putInt("wpn", weapon)
        weaponLevels.forEachIndexed { i, v -> e.putInt("wl$i", v) }
        e.putBoolean("sound", sound)
        e.putBoolean("music", music)
        e.putInt("bestScore", bestScore)
        e.putString("runs", records.joinToString(";") { it.joinToString(",") })
        e.apply()
    }
}
