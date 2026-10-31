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

    private val frameCount = HashMap<String, Int>()

    /** Number of animation frames name_0, name_1, ... shipped for [name] (0 = none). */
    fun frames(name: String): Int = synchronized(frameCount) {
        frameCount.getOrPut(name) {
            var n = 0
            while (n < 16 && has("${name}_$n")) n++
            n
        }
    }

    fun zombie(t: ZombieType) = when (t) {
        ZombieType.WALKER -> "zombie_walker"
        ZombieType.RUNNER -> "zombie_runner"
        ZombieType.FAT -> "zombie_fat"
        ZombieType.BAT -> "zombie_bat"
        ZombieType.LIZARD -> "zombie_dog"
        ZombieType.ALIEN -> "zombie_armored"
        ZombieType.BLOB -> "zombie_crawler"
    }

    /** Upright monsters whose legs can step (see [Rig]). */
    fun walks(t: ZombieType) = t == ZombieType.WALKER || t == ZombieType.RUNNER || t == ZombieType.FAT

    fun stride(t: ZombieType) = when (t) {
        ZombieType.RUNNER -> 34f
        ZombieType.FAT -> 14f
        else -> 22f
    }

    /** Road/room region boss by tier (1 brute, 2 serpent, 3 bat, 4 hulk); null = drawn in code. */
    fun boss(tier: Int) = when (tier) {
        1 -> "boss_brute"
        2 -> "boss_mutant_worm" // flying serpent of the volcano
        3 -> "zombie_bat" // the giant bat
        else -> "boss_tank_zombie"
    }.takeIf { has(it) }

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

/**
 * A still picture cut into body + two legs so it can walk: the legs swing
 * about the hip while the body bobs. Cut once per sprite and cached.
 */
class Rig(val body: Bitmap, val legA: Bitmap, val legB: Bitmap, val hip: Int, val aX: Int, val bX: Int, val w: Int, val h: Int) {
    companion object {
        private val cache = HashMap<String, Rig?>()

        /** Where the hip is, as a share of the picture height. */
        private fun hipOf(name: String) = when {
            name.startsWith("boss") -> 0.62f
            name == "zombie_fat" -> 0.7f
            name.startsWith("survivor") -> 0.56f
            else -> 0.58f
        }

        fun of(name: String): Rig? = synchronized(cache) {
            cache.getOrPut(name) { Sprites.get(name)?.let { cut(it, hipOf(name)) } }
        }

        private fun cut(b: Bitmap, hipF: Float): Rig? {
            val w = b.width
            val h = b.height
            val hip = (h * hipF).toInt()
            if (hip <= 4 || h - hip < 8 || w < 16) return null
            // split the legs at the emptiest column in the middle of the leg area
            val px = IntArray(w * (h - hip))
            b.getPixels(px, 0, w, 0, hip, w, h - hip)
            val col = IntArray(w)
            for (y in 0 until h - hip) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 40) col[x]++
            // ignore thin bits (a gun or hand hanging low): only columns with real leg mass count
            val thick = maxOf(1, (col.maxOrNull() ?: 0) / 4)
            var l = 0
            while (l < w && col[l] < thick) l++
            var r = w - 1
            while (r > l && col[r] < thick) r--
            if (r - l < 8) return null
            var split = (l + r) / 2
            var best = Int.MAX_VALUE
            for (x in l + (r - l) * 3 / 10..l + (r - l) * 7 / 10) if (col[x] < best) { best = col[x]; split = x }
            // centre of each leg = where it hangs from
            fun centre(a: Int, z: Int): Int {
                var s = 0L
                var n = 0L
                for (x in a until z) { s += x.toLong() * col[x]; n += col[x] }
                return if (n > 0) (s / n).toInt() else (a + z) / 2
            }
            val overlap = (h * 0.05f).toInt()
            val body = Bitmap.createBitmap(b, 0, 0, w, minOf(h, hip + overlap))
            val legA = Bitmap.createBitmap(b, 0, hip, split, h - hip)
            val legB = Bitmap.createBitmap(b, split, hip, w - split, h - hip)
            return Rig(body, legA, legB, hip, centre(0, split), centre(split, w) - split, w, h)
        }
    }
}
