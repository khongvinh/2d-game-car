package shot

import android.content.Context
import com.khongvinh.carsurvival.*
import java.io.File

fun themeMain(args: Array<String>) {
    out = File(args[0]).also { it.mkdirs() }
    val ctx = Context()
    val save = SaveData(ctx)
    save.bestStage = 19
    save.stageSel = 7
    save.best[1] = 700; save.best[2] = 450
    save.addRecord(1834, 9, 7, 41); save.addRecord(1520, 8, 12, 39); save.addRecord(990, 5, 3, 20)
    save.addRecord(2410, 12, 13, 55); save.addRecord(700, 1, 0, 2); save.addRecord(640, 4, 1, 9)
    val g = Game(save, Sfx(ctx), Music())
    g.resize(W.toFloat(), H.toFloat())
    tick(g, 1f)
    shot(g, "t_menu")
    val mb = g.buttons().first { it.id == "maps" }
    g.touchDown(9, mb.cx, mb.cy); g.touchUp(9); tick(g, 1f)
    shot(g, "t_maps")
    g.back(); tick(g, 1f)
    for (st in (args.getOrNull(1)?.split(",")?.map { it.toInt() } ?: listOf(1, 4, 7, 9, 12, 14, 17, 20))) {
        save.stageSel = st
        tap0(g)
        g.car.maxFuel = 999f; g.car.fuel = 999f; g.car.maxHp = 9999f; g.car.hp = 9999f
        val gas = g.controls().first { it.id == "gas" }
        g.touchDown(1, gas.cx, gas.cy)
        var t = 0f
        while (t < 30f && g.state == State.PLAYING) {
            tick(g, 0.5f); t += 0.5f
            if (t >= 4f && g.zombies.count { kotlin.math.abs(it.x - g.car.x) < 900f } >= 2) break
        }
        shot(g, "t_stage$st")
        g.touchUp(1)
        g.back(); tick(g, 0.3f); g.back(); tick(g, 1f)
        if (g.state != State.MENU) { g.back(); tick(g, 1f) }
    }
}
