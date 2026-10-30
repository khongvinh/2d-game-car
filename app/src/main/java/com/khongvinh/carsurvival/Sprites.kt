package com.khongvinh.carsurvival

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

/**
 * Pictures and sounds made by tools/assets/tao_asset_colab.py, shipped in
 * app/src/main/assets (img/, sfx/, music/). Everything is looked up by name;
 * when a file is missing the game keeps drawing that thing in code.
 */
object Sprites {
    /** Opens a file from the app's assets; set by [GameView] (and the preview tool). */
    @Volatile
    var open: ((String) -> InputStream?)? = null

    private val cache = HashMap<String, Bitmap?>()

    fun get(name: String): Bitmap? = synchronized(cache) {
        cache.getOrPut(name) {
            val o = open ?: return@getOrPut null
            for (ext in arrayOf(".png", ".jpg")) {
                try {
                    o("img/$name$ext")?.use { s -> BitmapFactory.decodeStream(s)?.let { return@getOrPut it } }
                } catch (_: Throwable) {
                }
            }
            null
        }
    }

    fun has(name: String) = get(name) != null

    fun zombie(t: ZombieType) = when (t) {
        ZombieType.WALKER -> "zombie_walker"
        ZombieType.RUNNER -> "zombie_runner"
        ZombieType.FAT -> "zombie_fat"
        ZombieType.BAT -> "zombie_bat"
        ZombieType.LIZARD -> "zombie_dog"
        ZombieType.ALIEN -> "zombie_armored"
        ZombieType.BLOB -> "zombie_crawler"
    }

    /** Siege monster picture: night bosses have their own (by region). */
    fun foe(f: Foe, boss: Boolean, r: Region) = when {
        boss -> when (r) {
            Region.VOLCANO -> "boss_tank_zombie"
            Region.ALIEN -> "boss_mutant_worm"
            else -> "boss_brute"
        }
        else -> zombie(f.z.type)
    }

    /** Car-mounted weapons. */
    fun weapon(w: WeaponType) = when (w) {
        WeaponType.PISTOL -> "wpn_pistol"
        WeaponType.KNIFE -> "wpn_axe"
        WeaponType.SMG -> "wpn_rifle"
        WeaponType.CROSSBOW -> "wpn_bow"
        WeaponType.SHOTGUN -> "wpn_shotgun"
        WeaponType.CATAPULT -> "wpn_dynamite"
        WeaponType.FLAMER -> "wpn_flamethrower"
        WeaponType.ROCKET -> "wpn_rocket"
        WeaponType.LASER -> "wpn_laser"
        WeaponType.MINIGUN -> "wpn_minigun"
    }

    /** Round buttons: home, car, weapons, skills. */
    val NAV = arrayOf("ui_home", "ui_garage", "wpn_rifle", "ui_skills")

    fun def(t: DefType) = when (t) {
        DefType.WOOD -> "def_barricade_wood"
        DefType.SPIKE -> "def_barricade_spikes"
        DefType.MG -> "def_turret_mg"
        DefType.CANNON -> "def_turret_cannon"
        DefType.FLAME -> "def_turret_flame"
        DefType.TESLA -> "def_turret_tesla"
        DefType.MORTAR -> "def_turret_mortar"
        DefType.MINE -> "def_landmine"
    }

    fun power(p: Power) = when (p) {
        Power.DUAL -> "pw_dual_magazine"
        Power.DYNAMITE -> "pw_dynamite"
        Power.MATCH -> "pw_matchstick"
        Power.NITRO -> "pw_nitrogen"
        Power.BOOM -> "pw_explode_corpse"
        Power.SHOES -> "pw_shoes"
        Power.ORBIT -> "pw_orbit_junk"
        Power.ADREN -> "pw_adrenaline"
        Power.BATTERY -> "pw_battery"
        Power.DAMAGE -> "pw_damage"
        Power.RATE -> "pw_fire_rate"
        Power.VITAL -> "pw_health"
        Power.MAGNET -> "pw_magnet"
        Power.JUMP2 -> "pw_double_jump"
        Power.VAMP -> "pw_vampire"
    }

    fun side(w: SideWeapon) = when (w) {
        SideWeapon.PISTOL -> "wpn_pistol"
        SideWeapon.DART -> "wpn_shuriken"
        SideWeapon.SPEAR -> "wpn_spear"
        SideWeapon.BOW -> "wpn_bow"
        SideWeapon.AXE -> "wpn_axe"
        SideWeapon.SHOTGUN -> "wpn_shotgun"
        SideWeapon.SMG -> "wpn_smg"
    }

    val CARS = arrayOf("car_motorbike", "car_sedan", "car_pickup", "car_bus", "car_tank")

    fun background(r: Region) = when (r) {
        Region.DESERT -> "bg_desert_far"
        Region.VOLCANO -> "bg_city_ruins"
        Region.NIGHT -> "bg_night_forest"
        Region.ALIEN -> "bg_fortress"
    }
}
