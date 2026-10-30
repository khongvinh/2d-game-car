package shot

import android.content.Context
import com.khongvinh.carsurvival.*

/** Into a roadside building: fight, search every chest, leave. Plus the new screens. */
fun main() {
    out = java.io.File("../out/shots").also { it.mkdirs() }
    for (st in listOf(1, 7, 12, 18)) {
        val ctx = Context()
        val save = SaveData(ctx)
        save.storySeen = 63
        save.bestStage = 20; save.stageSel = st
        save.side = if (st == 1) 0 else (st / 4).coerceAtMost(SideWeapon.entries.size - 1)
        val g = Game(save, Sfx(ctx), Music())
        g.resize(W.toFloat(), H.toFloat())
        tick(g, 0.5f)
        tap0(g)
        val b = g.buildings.firstOrNull()
        if (b == null) { println("stage $st: no building"); continue }
        // drop the car right by the door
        g.car.x = b.x - 40f; g.car.y = g.terrain.heightAt(g.car.x) - 60f
        tick(g, 1.0f)
        fun press(id: String) { val c = g.controls().first { it.id == id }; g.touchDown(7, c.cx, c.cy); g.touchUp(7) }
        press("exit")
        tick(g, 0.6f)
        g.driver.x = b.x
        tick(g, 0.2f)
        println("stage $st action=${g.driverAction()} kind=${b.kind}")
        press("act")
        tick(g, 0.3f)
        val inn = g.interior!!
        println("  inside: rooms=${inn.rooms} chests=${inn.chests.size} zombies=${inn.zombiesLeft} weapon=${save.sideWeapon}")
        shot(g, "in_${st}_a")
        fun walk(dir: Float) { g.touchUp(2); g.touchDown(2, 190f, H - 150f); g.touchMove(2, 190f + dir * 80f, H - 150f) }
        val jump = g.controls().first { it.id == "jump" }
        var t = 0f
        var shots = 0
        val hp0 = g.driver.hp
        // bot: walk to the next unopened chest, jump if it sits high, search it
        while (t < 90f && g.state == State.PLAYING && g.interior != null) {
            g.driver.hp = kotlin.math.max(g.driver.hp, 30f)
            val target = inn.chests.filter { !it.opened }.minByOrNull { kotlin.math.abs(it.x - g.driver.x) }
            g.touchUp(2); g.touchUp(3)
            if (target == null) {
                if (g.driverAction() == "leave") { press("act"); break }
                walk(-1f)
            } else if (g.driverAction() == "chest") {
                press("act")
            } else if (g.driverAction() != "busy") {
                if (target.y > 0f && kotlin.math.abs(target.x - g.driver.x) < 160f) g.touchDown(3, jump.cx, jump.cy)
                if (target.x > g.driver.x) walk(1f) else walk(-1f)
            }
            tick(g, 0.1f); t += 0.1f
            if (shots < 3 && (t * 10).toInt() % 60 == 30) { shot(g, "in_${st}_${"bcd"[shots]}"); shots++ }
        }
        println("  done t=${"%.1f".format(t)} opened=${inn.chests.count { it.opened }}/${inn.chests.size} zombiesLeft=${inn.zombiesLeft} kills=${g.kills} money=${g.runMoney} gems=${g.runGems} parts=${g.runParts} hpLost~${"%.0f".format(hp0 - g.driver.hp)} out=${g.interior == null} looted=${b.looted} state=${g.state}")
        tick(g, 0.3f)
        if (st == 1) {
            shot(g, "in_out")
            // shield on foot
            g.driver.shieldT = 5f
            tick(g, 0.2f)
            shot(g, "in_shield")
        }
    }
    // sound screen and the hand-weapon tab
    val ctx = Context()
    val save = SaveData(ctx)
    save.storySeen = 63; save.bestStage = 5; save.money = 3000
    val g = Game(save, Sfx(ctx), Music())
    g.resize(W.toFloat(), H.toFloat())
    tick(g, 0.6f)
    shot(g, "menu_new")
    tap(g, "soundMenu"); tick(g, 0.5f)
    shot(g, "sound")
    tap(g, "soundDone"); tick(g, 0.5f)
    tap(g, "garage"); tick(g, 0.5f)
    val nr = g.navRect(4)
    g.touchDown(9, (nr[0] + nr[2]) / 2, (nr[1] + nr[3]) / 2); g.touchUp(9)
    tick(g, 0.2f)
    shot(g, "garage_side")
}
