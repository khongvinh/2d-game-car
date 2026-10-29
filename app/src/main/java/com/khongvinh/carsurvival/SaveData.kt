package com.khongvinh.carsurvival

import android.content.Context

/** Persistent wallet, stage progress, garage and upgrade levels. */
class SaveData(context: Context) {
    companion object {
        const val MAX_RECORDS = 10
        const val PARTS_NEEDED = 5
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

    /** Volume sliders 0..100: music, sound effects, engine. Old on/off switches carry over. */
    var volMusic = prefs.getInt("volMusic", if (music) 50 else 0)
    var volSfx = prefs.getInt("volSfx", if (sound) 65 else 0)
    var volEngine = prefs.getInt("volEngine", if (sound) 100 else 0)

    /** Hand weapons for the driver on foot: bit per [SideWeapon] ordinal (pistol free), and the one carried. */
    var ownedSide = prefs.getInt("ownedSide", 1)
    var side = prefs.getInt("side", 0)

    /** Red gems for tuning upgrades, and the level of each upgrade. */
    var gems = prefs.getInt("gems", 0)

    /** Bought skill-tree nodes (bit per [Skills.nodes] id). */
    var skills = prefs.getInt("skills", 0)

    /** Experience for the player level shown in the garage. */
    var xp = prefs.getInt("xp", 0)

    /** Parts found for each vehicle (5 needed before it can be built). */
    val parts = IntArray(Vehicles.all.size) { prefs.getInt("pt$it", 0) }

    /**
     * Custom control positions (centre, in 1/1000 of the screen) for the slots
     * brake, leanBack, leanFwd, boost, gas, exit; -1 = default place.
     */
    val ctlX = IntArray(6) { prefs.getInt("cx$it", -1) }
    val ctlY = IntArray(6) { prefs.getInt("cy$it", -1) }

    /** Story pages already shown: bit 0 prologue, 1..4 region chapters, 5 ending. */
    var storySeen = prefs.getInt("story", 0)

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
        if (v >= 4) return
        if (v == 3) {
            // v4.8: the 6 x 20 gem upgrades became the skill tree; give the gems back
            val e = prefs.edit()
            for (i in 0 until 6) {
                val lv = prefs.getInt("ug$i", 0)
                for (l in 0 until lv) gems += Math.round(5.0 * Math.pow(1.2, l.toDouble())).toInt()
                e.remove("ug$i")
            }
            e.putInt("garageV", 4).apply()
            save()
            return
        }
        // v4.6: the upgrade shop is gone (vehicles carry their own tuning): refund it
        val upBase = intArrayOf(150, 120, 180, 140, 100, 110)
        val e0 = prefs.edit()
        for (i in upBase.indices) {
            val lv = prefs.getInt("up$i", 0)
            for (l in 0 until lv) money += (upBase[i] * (l + 1) * 1.5f).toInt() + 100 * l * l
            e0.remove("up$i")
        }
        e0.putInt("garageV", 4).apply()
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
        prefs.edit().putInt("garageV", 4).remove("ownedCars").remove("car").remove("ownedWeapons").remove("weapon").apply()
        save()
    }

    /** The vehicle that looted parts go to: the cheapest unfinished one of an open family. */
    fun partTarget(): Vehicle? = Vehicles.all
        .filter { it.id != 0 && !ownsVehicle(it.id) && parts[it.id] < PARTS_NEEDED && classUnlocked(it.cls) }
        .minByOrNull { it.cost }

    fun hasAllParts(v: Vehicle) = v.id == 0 || parts[v.id] >= PARTS_NEEDED

    /** Wipes all progress (sound settings stay) so the player can start over. */
    fun resetAll() {
        money = 0
        bestStage = 0
        stageSel = 1
        day = 1
        best.fill(0)
        ownedVehicles = 1
        vehicle = 0
        ownedWeapons = 1
        weapon = 0
        weaponLevels.fill(0)
        ownedSide = 1
        side = 0
        bestScore = 0
        records.clear()
        gems = 0
        skills = 0
        xp = 0
        parts.fill(0)
        storySeen = 0
        save()
    }

    val vehicleType get() = Vehicles.of(vehicle)
    val weaponType get() = WeaponType.entries[weapon.coerceIn(0, WeaponType.entries.size - 1)]
    fun weaponLevel(w: WeaponType) = weaponLevels[w.ordinal]
    val cloneUnlocked get() = bestStage >= 5

    val sideWeapon get() = SideWeapon.entries[side.coerceIn(0, SideWeapon.entries.size - 1)]
    fun ownsSide(w: SideWeapon) = ownedSide and (1 shl w.ordinal) != 0
    fun sideUnlocked(w: SideWeapon) = bestStage >= w.unlockStage

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
        e.putInt("volMusic", volMusic)
        e.putInt("volSfx", volSfx)
        e.putInt("volEngine", volEngine)
        e.putInt("ownedSide", ownedSide)
        e.putInt("side", side)
        e.putString("runs", records.joinToString(";") { it.joinToString(",") })
        e.putInt("gems", gems)
        e.putInt("skills", skills)
        e.putInt("xp", xp)
        parts.forEachIndexed { i, v -> e.putInt("pt$i", v) }
        e.putInt("story", storySeen)
        for (i in 0 until 6) {
            e.putInt("cx$i", ctlX[i])
            e.putInt("cy$i", ctlY[i])
        }
        e.apply()
    }
}
