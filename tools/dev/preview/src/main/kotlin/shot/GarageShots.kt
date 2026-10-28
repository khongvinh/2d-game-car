package shot

import android.content.Context
import com.khongvinh.carsurvival.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private fun shotAt(g: Game, name: String, w: Int) {
    val img = BufferedImage(w, H, BufferedImage.TYPE_INT_ARGB)
    val gr = img.createGraphics()
    g.render(android.graphics.Canvas(gr))
    gr.dispose()
    ImageIO.write(img, "png", File(out, "$name.png"))
}

private fun tapAt(g: Game, x: Float, y: Float) { g.touchDown(98, x, y); g.touchUp(98) }

/** All 25 vehicles, each with a different weapon, on one sheet. */
private fun lineup() {
    val cw = 340; val ch = 200
    val img = BufferedImage(cw * 5, ch * 5, BufferedImage.TYPE_INT_ARGB)
    val gr = img.createGraphics()
    gr.color = java.awt.Color(0x5D6D7E); gr.fillRect(0, 0, img.width, img.height)
    val c = android.graphics.Canvas(gr)
    val art = CarArt()
    art.time = 1.3f
    val p = android.graphics.Paint().apply { color = 0xFFFFFFFF.toInt(); textSize = 20f; textAlign = android.graphics.Paint.Align.CENTER }
    for (v in Vehicles.all) {
        val col = v.variant; val row = v.cls.ordinal
        val cx = col * cw + cw / 2f; val cy = row * ch + 95f
        p.color = 0x33000000; c.drawRect(cx - 160f, cy + 54f, cx + 160f, cy + 60f, p)
        art.draw(c, v, cx, cy, 0f, 0.5f, -0.2f, col == 4, WeaponType.entries[(row * 5 + col) % 10], false)
        p.color = 0xFFFFFFFF.toInt()
        c.drawText("${v.title} — ${v.cost}$", cx, row * ch + 190f, p)
    }
    gr.dispose()
    ImageIO.write(img, "png", File(out, "g_lineup.png"))
}

fun garageMain(args: Array<String>) {
    out = File(args[0]).also { it.mkdirs() }
    lineup()
    for (w in listOf(W, 1280)) {
        val ctx = Context()
        val save = SaveData(ctx)
        save.storySeen = 63
        save.bestStage = 16
        save.money = 60000
        save.ownedVehicles = 0b1111 or (0b11 shl 5) or (1 shl 10)
        save.vehicle = 6
        save.ownedWeapons = 0b10111
        save.weapon = 4
        save.weaponLevels[4] = 2
        val g = Game(save, Sfx(ctx), Music())
        g.resize(w.toFloat(), H.toFloat())
        tick(g, 1f)
        tap(g, "garage"); tick(g, 0.5f)
        shotAt(g, "g_${w}_veh_car", w)
        for (cl in listOf(VehicleClass.MOTO, VehicleClass.ARMORED, VehicleClass.TANK)) {
            val rc = g.classRect(cl.ordinal)
            tapAt(g, (rc[0] + rc[2]) / 2, (rc[1] + rc[3]) / 2); tick(g, 0.2f)
            shotAt(g, "g_${w}_veh_${cl.name.lowercase()}", w)
            if (w != W) break
        }
        val tr = g.garageTabRect(1)
        tapAt(g, (tr[0] + tr[2]) / 2, (tr[1] + tr[3]) / 2); tick(g, 0.2f)
        shotAt(g, "g_${w}_wpn", w)
        val rr = g.weaponRect(WeaponType.ROCKET.ordinal)
        tapAt(g, (rr[0] + rr[2]) / 2, (rr[1] + rr[3]) / 2); tick(g, 0.2f)
        shotAt(g, "g_${w}_wpn_rocket", w)
        save.gems = 420; save.upgrades[0] = 12; save.upgrades[3] = 17; save.upgrades[4] = 20; save.upgrades[1] = 3
        val ur = g.garageTabRect(2)
        tapAt(g, (ur[0] + ur[2]) / 2, (ur[1] + ur[3]) / 2); tick(g, 0.2f)
        shotAt(g, "g_${w}_upg", w)
    }
    // vehicles and weapons in action
    val combos = listOf(
        Triple(4, WeaponType.KNIFE, 3), Triple(7, WeaponType.SHOTGUN, 6), Triple(13, WeaponType.FLAMER, 8),
        Triple(17, WeaponType.LASER, 12), Triple(24, WeaponType.ROCKET, 17), Triple(9, WeaponType.MINIGUN, 18),
    )
    for ((vid, wpn, st) in combos) {
        val ctx = Context()
        val save = SaveData(ctx)
        save.storySeen = 63
        save.bestStage = 20; save.stageSel = st
        save.ownedVehicles = -1; save.vehicle = vid
        save.ownedWeapons = -1; save.weapon = wpn.ordinal
        val g = Game(save, Sfx(ctx), Music())
        g.resize(W.toFloat(), H.toFloat())
        tick(g, 1f)
        tap0(g)
        g.car.ammo = 300f; g.car.maxFuel = 999f; g.car.fuel = 999f
        val gas = g.controls().first { it.id == "gas" }
        g.touchDown(1, gas.cx, gas.cy)
        var t = 0f
        var taken = false
        while (t < 25f && g.state == State.PLAYING) {
            tick(g, 0.1f); t += 0.1f
            val busy = g.shots.size >= 2 || g.beams.isNotEmpty() || g.car.slashT > 0f
            if (t > 2.5f && busy) { shotAt(g, "g_act_${wpn.name.lowercase()}", W); taken = true; break }
        }
        if (!taken) shotAt(g, "g_act_${wpn.name.lowercase()}", W)
        println("${Vehicles.of(vid).title} + ${wpn.title}: t=$t kills=${g.kills} hp=${g.car.hp.toInt()}/${g.car.maxHp.toInt()} state=${g.state}")
    }
}
