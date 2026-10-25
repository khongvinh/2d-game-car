package shot

import android.content.Context
import com.khongvinh.carsurvival.*
import java.io.File

/** One picture per stage at its most typical road feature (tunnel, cave, bridge). */
fun roadMain(args: Array<String>) {
    out = File(args[0]).also { it.mkdirs() }
    val stages = args.getOrNull(1)?.split(",")?.map { it.toInt() } ?: (1..20).toList()
    for (st in stages) {
        val ctx = Context()
        val save = SaveData(ctx)
        save.bestStage = 20; save.stageSel = st
        val g = Game(save, Sfx(ctx), Music())
        g.resize(W.toFloat(), H.toFloat())
        tick(g, 0.5f)
        tap0(g)
        val t = g.terrain
        val x = when {
            t.bridges.isNotEmpty() -> t.bridges[0][0] - 250f
            t.tunnels.isNotEmpty() -> if (t.style.cave) t.tunnels[0][0] + 900f else t.tunnels[0][0] - 150f
            else -> 1500f
        }
        val c = g.car
        c.x = x; c.y = t.heightAt(x) - 60f; c.vx = 300f; c.maxHp = 9999f; c.hp = 9999f; c.fuel = 999f
        g.zombies.removeAll { kotlin.math.abs(it.x - x) < 300f }
        g.obstacles.removeAll { kotlin.math.abs(it.x - x) < 300f }
        val gas = g.controls().first { it.id == "gas" }
        g.touchDown(1, gas.cx, gas.cy)
        tick(g, 1.2f)
        g.touchUp(1)
        shot(g, "r_stage$st")
        println("stage $st ${t.style.name}: tunnels=${t.tunnels.map { it.map { v -> v.toInt() } }} bridges=${t.bridges.map { it.map { v -> v.toInt() } }} car=${g.car.x.toInt()} state=${g.state}")
    }
}
