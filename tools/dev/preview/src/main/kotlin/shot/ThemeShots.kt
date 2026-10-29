package shot

import android.content.Context
import com.khongvinh.carsurvival.*
import java.io.File

fun themeMain(args: Array<String>) {
    out = File(args[0]).also { it.mkdirs() }
    val ctx = Context()
    val save = SaveData(ctx)
    save.storySeen = 63
    save.bestStage = 19
    save.stageSel = 7
    save.best[1] = 700; save.best[2] = 450
    save.addRecord(1834, 9, 7, 41); save.addRecord(1520, 8, 12, 39); save.addRecord(990, 5, 3, 20)
    save.addRecord(2410, 12, 13, 55); save.addRecord(700, 1, 0, 2); save.addRecord(640, 4, 1, 9)
    val g = Game(save, Sfx(ctx), Music())
    g.resize(W.toFloat(), H.toFloat())
    tick(g, 1f)
    shot(g, "t_menu")
    // control layout editor: drag the gas pedal to the middle
    tap(g, "layout"); tick(g, 0.5f)
    val gr = g.slotRect(4)
    g.touchDown(3, (gr[0] + gr[2]) / 2, (gr[1] + gr[3]) / 2); g.touchMove(3, W * 0.55f, H * 0.6f); g.touchUp(3); tick(g, 0.1f)
    shot(g, "t_layout")
    tap(g, "layoutReset"); tap(g, "layoutDone"); tick(g, 0.5f)
    println("layout back to ${g.state}")
    // reset confirmation
    tap(g, "reset"); tick(g, 0.3f); shot(g, "t_reset"); tap(g, "resetNo"); tick(g, 0.5f)
    // story: prologue on a fresh save
    run {
        val s2 = SaveData(Context())
        val g2 = Game(s2, Sfx(Context()), Music())
        g2.resize(W.toFloat(), H.toFloat())
        tick(g2, 1f); shot(g2, "t_story0")
        g2.touchDown(5, 300f, 300f); g2.touchUp(5); tick(g2, 1f); shot(g2, "t_story1")
    }
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
        if (st == 4) {
            // a bunker firing at the car
            val b = g.obstacles.firstOrNull { it.type == ObstacleType.BUNKER }
            if (b != null) {
                g.car.x = b.x - 700f; g.car.y = g.terrain.heightAt(g.car.x) - 60f; g.car.vx = 0f
                g.zombies.removeAll { kotlin.math.abs(it.x - b.x) < 900f }
                g.touchUp(1)
                g.car.ammo = 0f
                tick(g, 2.2f); shot(g, "t_bunker")
            }
            // park by a building, get out and loot it
            run run2@{
            val bld = g.buildings.firstOrNull()
            if (bld != null) {
                g.zombies.removeAll { kotlin.math.abs(it.x - bld.x) < 1200f }
                g.obstacles.removeAll { kotlin.math.abs(it.x - bld.x) < 600f }
                g.zombies.add(Zombie(ZombieType.WALKER, bld.x + 500f, g.terrain.heightAt(bld.x + 500f)))
                g.car.x = bld.x - 160f; g.car.y = g.terrain.heightAt(g.car.x) - 60f; g.car.vx = 0f; g.car.vy = 0f; g.car.angle = 0f
                tick(g, 1.0f)
                val ex = g.controls().firstOrNull { it.id == "exit" }
                println("exit button: ${ex != null}")
                shot(g, "t_park")
                if (ex != null) {
                    g.touchDown(8, ex.cx, ex.cy); g.touchUp(8); tick(g, 0.2f)
                    val right = g.controls().first { it.id == "leanFwd" }
                    g.touchDown(9, right.cx, right.cy)
                    var t = 0f
                    while (g.driverAction() != "loot" && t < 3f) { tick(g, 0.05f); t += 0.05f }
                    g.touchUp(9)
                    // a jump
                    val jb = g.controls().first { it.id == "jump" }
                    g.touchDown(10, jb.cx, jb.cy); tick(g, 0.15f); shot(g, "t_jump"); g.touchUp(10); tick(g, 1f)
                    println("jumped: air back to ${g.driver.air}")
                    val act = g.controls().first { it.id == "act" }
                    g.touchDown(8, act.cx, act.cy); g.touchUp(8); tick(g, 1.0f)
                    shot(g, "t_loot")
                    tick(g, 1.6f)
                    shot(g, "t_loot_done")
                    println("parts=${g.runParts} gems=${g.runGems} driverHp=${g.driver.hp}")
                    // back in, park right on the next door: the button must offer looting
                    val act2 = g.controls().first { it.id == "act" }
                    g.driver.x = g.car.x; g.touchDown(8, act2.cx, act2.cy); g.touchUp(8); tick(g, 0.2f)
                    g.buildings.add(Building(g.car.x + 10f, 0))
                    val ex2 = g.controls().firstOrNull { it.id == "exit" }
                    if (ex2 != null) { g.touchDown(8, ex2.cx, ex2.cy); g.touchUp(8); tick(g, 0.1f) }
                    println("parked on a door -> action=${g.driverAction()}")
                    val act3 = g.controls().first { it.id == "act" }
                    g.touchDown(8, act3.cx, act3.cy); g.touchUp(8); tick(g, 0.2f)
                    g.touchDown(8, act3.cx, act3.cy); g.touchUp(8); tick(g, 0.2f)
                    // the car blows up: the driver jumps clear and fights on
                    g.car.hp = -1f; tick(g, 0.6f)
                    shot(g, "t_bailout")
                    println("after blast: state=${g.state} driverOut=${g.driver.out} wrecked=${g.wrecked}")
                    g.driver.hp = 0f; tick(g, 2.2f)
                    println("driver down: state=${g.state} reason=${g.endReason}")
                    if (g.state != State.PLAYING) { g.back(); tick(g, 0.5f); return@run2 }
                }
            }
            }
            if (g.state != State.PLAYING) continue
            val pb = g.pauseBtn
            g.touchDown(7, pb.cx, pb.cy); g.touchUp(7); tick(g, 0.6f); shot(g, "t_pause")
            tap(g, "resume"); tick(g, 0.2f)
        }
        g.touchUp(1)
        g.back(); tick(g, 0.3f); g.back(); tick(g, 1f)
        if (g.state != State.MENU) { g.back(); tick(g, 1f) }
    }
}
