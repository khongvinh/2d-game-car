package shot

import android.content.Context
import com.khongvinh.carsurvival.*
import java.io.File

fun ramMain(args: Array<String>) {
    out = File(args[0]).also { it.mkdirs() }
    val ctx = Context()
    val save = SaveData(ctx)
    val g = Game(save, Sfx(ctx), Music())
    g.resize(W.toFloat(), H.toFloat())
    tick(g, 1f)
    tap0(g)
    g.obstacles.clear(); g.zombies.clear()
    val wx = 900f
    g.obstacles.add(Obstacle(ObstacleType.WRECK, wx, g.terrain.heightAt(wx)))
    g.car.ammo = 0
    val gas = g.controls().first { it.id == "gas" }
    val brake = g.controls().first { it.id == "brake" }
    var t = 0f; var shots = 0; var last = 1f
    // drive in, back off when bounced, drive in again (like a player taking a run-up)
    while (t < 40f && g.state == State.PLAYING) {
        g.touchDown(1, gas.cx, gas.cy)
        tick(g, 2.2f); t += 2.2f
        val o = g.obstacles.firstOrNull { it.type == ObstacleType.WRECK }
        val f = o?.ramHp ?: 0f
        println("t=$t car.x=${g.car.x.toInt()} speed=${g.car.forwardSpeed.toInt()} wreck=${o?.let { "%.2f".format(it.ramHp) } ?: "BROKEN"} hp=${g.car.hp.toInt()}")
        if (f < last && shots < 2) { shot(g, "ram_$shots"); shots++ }
        last = f
        if (o == null) { tick(g, 0.3f); shot(g, "ram_broken"); break }
        g.touchUp(1)
        g.touchDown(2, brake.cx, brake.cy); tick(g, 1.2f); t += 1.2f; g.touchUp(2)
    }
}

fun tap0(g: Game) { val b = g.buttons().first { it.id == "play" }; g.touchDown(99, b.cx, b.cy); g.touchUp(99) }
