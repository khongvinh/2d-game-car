package shot

import android.content.Context
import com.khongvinh.carsurvival.*
import kotlin.math.abs

/** Bot through the 30 warehouse rooms: picks the first card, walks to monsters, then the exit. */
fun main(args: Array<String>) {
    out = java.io.File("../out/shots").also { it.mkdirs() }
    val god = args.contains("god")
    for (st in listOf(1, 6, 10)) {
        val ctx = Context()
        val save = SaveData(ctx)
        save.storySeen = 63
        save.bestStage = 20; save.stageSel = st
        save.side = if (st == 1) 0 else 3
        val g = Game(save, Sfx(ctx), Music())
        g.resize(W.toFloat(), H.toFloat())
        tick(g, 0.5f)
        tap0(g)
        tick(g, 0.3f)
        g.boss?.let { it.hp = 0f; it.alive = false }
        g.car.x = g.goalX + 5f
        tick(g, 0.2f)
        val run0 = g.rooms
        if (run0 == null) { println("stage $st: rooms did not start, state=${g.state}"); continue }
        val run = run0
        var t = 0f
        var shots = 0
        val seen = HashSet<Int>()
        val jump = { g.controls().first { it.id == "jump" } }
        var lastRoom = 0
        var roomT = 0f
        var worstRoom = 0f
        while (t < 1500f && (g.state == State.PLAYING || g.state == State.PICK)) {
            if (god) g.driver.hp = g.driver.maxHp
            if (g.state == State.PICK) {
                if (run.pickKind !in seen && shots < 12) { seen.add(run.pickKind); shot(g, "rm_${st}_pick${run.pickKind}"); shots++ }
                tick(g, 0.45f); t += 0.45f
                tap(g, "pick0")
                continue
            }
            val d = g.driver
            val rm = run.room
            if (run.index != lastRoom) { lastRoom = run.index; worstRoom = maxOf(worstRoom, roomT); roomT = 0f
                if (run.index in listOf(2, 10, 20) && shots < 12) { tick(g, 1.2f); shot(g, "rm_${st}_r${run.index}"); shots++ } }
            val target = if (rm.open) rm.exitX else (rm.foes.minByOrNull { abs(it.z.x - d.x) + abs(it.z.y - d.y) * 0.5f }?.z?.x ?: rm.roomBoss?.x ?: rm.exitX)
            val ty = if (rm.open) 0f else (rm.foes.minByOrNull { abs(it.z.x - d.x) }?.z?.y ?: 0f)
            val want = if (rm.open) 0f else if (save.sideWeapon.melee) 60f else 260f
            g.touchUp(5)
            val dx = target - d.x
            val dir = if (abs(dx) > want) (if (dx > 0) 1f else -1f) else 0f
            if (dir != 0f) { g.touchDown(5, 190f, H - 150f); g.touchMove(5, 190f + dir * 80f, H - 150f) }
            val j = jump()
            g.touchUp(6)
            if (ty < d.y - 100f && abs(dx) < 300f) g.touchDown(6, j.cx, j.cy)
            tick(g, 0.1f); t += 0.1f; roomT += 0.1f
            if (roomT > 60f) { // stuck: open the room to keep going
                println("  stuck in room ${run.index}: foes=${rm.foes.size} at ${rm.foes.map { "${it.z.x.toInt()},${it.z.y.toInt()}" }} boss=${rm.roomBoss?.alive} me=${d.x.toInt()},${d.y.toInt()}")
                shot(g, "rm_${st}_stuck${run.index}")
                rm.foes.clear(); rm.roomBoss?.alive = false; roomT = 0f
            }
        }
        println("stage $st: state=${g.state} reason=${g.endReason} t=${t.toInt()}s room=${run.index}/30 level=${run.level} powers=${Power.entries.filter { run.lv(it) > 0 }.map { "${it.name}${run.lv(it)}" }} kills=${g.kills} money=${g.runMoney} hp=${g.driver.hp.toInt()}/${g.driver.maxHp.toInt()} worstRoom=${worstRoom.toInt()}s")
    }
}
