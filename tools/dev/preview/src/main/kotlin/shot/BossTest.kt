package shot

import android.content.Context
import com.khongvinh.carsurvival.*
import java.io.File

fun bossMain(args: Array<String>) {
    out = File(args[0]).also { it.mkdirs() }
    for ((run, tier) in listOf(0 to 1, 1 to 1, 2 to 2, 3 to 3, 4 to 4)) {
        val weak = run == 0
        val ctx = Context()
        val save = SaveData(ctx)
        save.storySeen = 63
        save.bestStage = tier * 5 - 1
        save.stageSel = tier * 5
        // a realistic loadout for that point of the game
        val veh = if (weak) Vehicles.of(VehicleClass.MOTO, 1) else listOf(Vehicles.of(VehicleClass.MOTO, 4), Vehicles.of(VehicleClass.CAR, 3), Vehicles.of(VehicleClass.PICKUP, 2), Vehicles.of(VehicleClass.ARMORED, 2))[tier - 1]
        val wpn = if (weak) WeaponType.PISTOL else listOf(WeaponType.SMG, WeaponType.CROSSBOW, WeaponType.FLAMER, WeaponType.ROCKET)[tier - 1]
        save.ownedVehicles = -1; save.vehicle = veh.id
        save.ownedWeapons = -1; save.weapon = wpn.ordinal; save.weaponLevels[wpn.ordinal] = if (weak) 1 else tier
        println("loadout: ${veh.title} + ${wpn.title} cấp ${tier + 1}")
        val g = Game(save, Sfx(ctx), Music())
        g.resize(W.toFloat(), H.toFloat())
        tick(g, 1f)
        tap0(g)
        val supplies = g.items.count { it.x > g.goalX * 0.72f && it.type != ItemType.COIN }
        println("tier $tier stage ${g.stage}: boss=${g.boss?.name} hp=${g.boss?.hp} supplies in last stretch=$supplies, total specials=${g.items.count { it.type != ItemType.COIN }}")
        val c = g.car
        c.x = g.goalX - 2600f; c.y = g.terrain.heightAt(c.x) - 60f
        c.maxHp = 400f; c.hp = 400f; c.maxFuel = 999f; c.fuel = 999f; c.ammo = 80f
        g.zombies.removeAll { it.x > c.x - 200 }; g.obstacles.removeAll { it.x > c.x - 200 }
        val gas = g.controls().first { it.id == "gas" }
        val brake = g.controls().first { it.id == "brake" }
        var t = 0f; var n = 0; var lastHp = g.boss!!.hp
        while (t < 90f && g.state == State.PLAYING) {
            g.touchDown(1, gas.cx, gas.cy); tick(g, 1.6f); t += 1.6f
            val b = g.boss!!
            if (b.engaged && n < 3 && (b.throwAnim > 0f || b.breathT > 0f || b.diveT > 0f || g.bossRocks.isNotEmpty() || b.hp < lastHp)) { shot(g, "boss${tier}_$n"); n++ }
            lastHp = b.hp
            g.touchUp(1)
            if (b.alive && kotlin.math.abs(g.car.x - b.x) < 500f) { g.touchDown(2, brake.cx, brake.cy); tick(g, 0.8f); t += 0.8f; g.touchUp(2) }
            if (!b.alive && n < 9) { shot(g, "boss${tier}_dead"); n = 9 }
        }
        val b = g.boss!!
        println("  reason=${g.endReason} t=$t alive=${b.alive} bossHp=${b.hp.toInt()} car.hp=${g.car.hp.toInt()} ammo=${g.car.ammo} state=${g.state} ach=${g.achievements}")
        tick(g, 1f)
        if (g.state != State.PLAYING) shot(g, "boss${tier}_result")
    }
}
