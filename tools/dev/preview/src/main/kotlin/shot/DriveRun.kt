package shot

import android.content.Context
import com.khongvinh.carsurvival.*

/** Full-gas bot through every stage: does any spot trap the car? */
var runN = 0

fun main(args: Array<String>) {
    out = java.io.File("../out/shots").also { it.mkdirs() }
    val only = args.getOrNull(0)?.split(",")?.map { it.toInt() }
    for (st in only ?: (1..20).toList()) {
        var shotN = 0
        runN++
        val ctx = Context()
        val save = SaveData(ctx)
        save.bestStage = 20; save.stageSel = st
        save.ownedVehicles = -1; save.vehicle = Vehicles.of(VehicleClass.CAR, 2).id
        for (u in Upgrade.entries) save.upgrades[u.ordinal] = 2
        val g = Game(save, Sfx(ctx), Music())
        g.resize(W.toFloat(), H.toFloat())
        tick(g, 0.5f)
        tap0(g)
        if (g.boss != null) g.boss!!.hp = 0.01f
        val gas = g.controls().first { it.id == "gas" }
        val back = g.controls().first { it.id == "leanBack" }
        val fwd = g.controls().first { it.id == "leanFwd" }
        g.touchDown(1, gas.cx, gas.cy)
        var t = 0f
        var worstStall = 0f; var lastX = 0f; var stall = 0f; var stallAt = 0
        while (t < 240f && g.state == State.PLAYING) {
            val c = g.car
            c.hp = c.maxHp; c.fuel = c.maxFuel; c.ammo = 50f
            // simple air control like a player: keep the nose level
            val a = c.angle
            g.touchUp(2); g.touchUp(3)
            if (!c.grounded[0] && !c.grounded[1]) {
                if (a < -0.25f) g.touchDown(3, fwd.cx, fwd.cy) else if (a > 0.25f) g.touchDown(2, back.cx, back.cy)
            }
            tick(g, 0.1f); t += 0.1f
            if (only != null && ((stall > 4f && shotN < 2) || (c.flipT > 2.5f && shotN < 3))) { shot(g, "d_${st}_${runN}_$shotN"); shotN++; if (stall > 4f) stall = -20f }
            if (c.x - lastX < 15f) { stall += 0.1f; if (stall > worstStall) { worstStall = stall; stallAt = (c.x / Game.METER).toInt() } } else stall = 0f
            lastX = c.x
            g.boss?.let { if (it.alive && kotlin.math.abs(it.x - c.x) < 900f) { it.hp = 0f; it.alive = false } }
        }
        println("stage $st ${g.terrain.style.name}: state=${g.state} reason=${g.endReason} t=${"%.0f".format(t)}s dist=${g.distanceM.toInt()}/${g.goalM.toInt()} worstStall=${"%.1f".format(worstStall)}s at ${stallAt}m")
    }
}
