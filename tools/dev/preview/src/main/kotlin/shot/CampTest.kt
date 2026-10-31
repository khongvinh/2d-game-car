package shot

import android.content.Context
import com.khongvinh.carsurvival.*
import kotlin.math.abs

/** The v7 loop: camp, build, a night, an expedition, a drive, the workshop. */
fun main() {
    out = java.io.File("../out/shots").also { it.mkdirs() }
    Sprites.open = { p -> java.io.File("../../../app/src/main/assets/$p").takeIf { it.exists() }?.inputStream() }
    val ctx = Context()
    val save = SaveData(ctx)
    save.storySeen = 63
    val g = Game(save, Sfx(ctx), Music())
    g.resize(W.toFloat(), H.toFloat())
    tick(g, 0.6f)
    shot(g, "v7_menu")
    tap(g, "play"); tick(g, 0.5f)
    println("state=${g.state} scrap=${save.scrap} fuel=${save.fuel}")
    shot(g, "v7_camp")
    fun tapAt(r: FloatArray) { g.touchDown(9, (r[0] + r[2]) / 2, (r[1] + r[3]) / 2); g.touchUp(9); tick(g, 0.1f) }
    // build a machine gun on the watchtower
    tapAt(g.slotHit(1)); tick(g, 0.5f)
    shot(g, "v7_build")
    tap(g, "bld${DefType.MG.ordinal}"); tick(g, 0.5f)
    println("after build scrap=${save.scrap} defs=${g.campaign.defs.map { it?.type?.name }}")
    // nights: survive a few, log how it goes
    for (n in 1..3) {
        tap(g, "night"); tick(g, 0.5f)
        var t = 0f
        var shots = 0
        while (g.state == State.SIEGE && t < 400f) {
            tick(g, 0.25f); t += 0.25f
            if (n == 1 && shots < 2 && g.campaign.foes.size > 6) { shot(g, "v7_siege$shots"); shots++; tick(g, 3f) }
        }
        println("night $n: t=${t.toInt()}s state=${g.state} nights=${save.nights} wall=${g.campaign.wallHp.toInt()}/${g.campaign.wallMax.toInt()} banner=${g.campaign.banner} ${g.campaign.bannerSub} defs=${g.campaign.defs.map { it?.let { d -> "${d.type.name}${d.hp.toInt()}" } }}")
        tick(g, 4.5f)
        // spend scrap: fill empty ground slots with barricades / guns
        for (i in 0 until Campaign.SLOTS) if (g.campaign.defs[i] == null) {
            val t2 = if (i >= 2 && i % 2 == 0) DefType.WOOD else DefType.MG
            if (save.scrap >= t2.cost) g.campaign.build(i, t2)
        }
    }
    // expedition (god mode bot)
    tap(g, "expedition"); tick(g, 0.6f)
    val run = g.rooms
    println("expedition: rooms=${run?.total} state=${g.state}")
    var t = 0f
    var died = false
    while (g.rooms != null && t < 400f) {
        // endless rooms: stay alive for 3 rooms, then let the driver fall (run ends, loot kept)
        val idx = g.rooms?.index ?: 0
        if (idx <= 3) g.driver.hp = g.driver.maxHp else if (!died) { g.driver.hp = 0f; died = true }
        if (idx == 3 && t > 0f && g.state == State.PLAYING && g.rooms?.room?.foes?.size ?: 0 > 3) shot(g, "v7_room3")
        if (g.state == State.PICK) { tick(g, 0.45f); tap(g, "pick0"); t += 0.5f; continue }
        val r = g.rooms!!
        val rm = r.room
        val d = g.driver
        val crate = rm.crates.firstOrNull { it[2] == 0f && it[1] == 0f }
        val target = if (crate != null) crate[0] else if (rm.open) rm.exitX else (rm.foes.minByOrNull { abs(it.z.x - d.x) }?.z?.x ?: rm.roomBoss?.x ?: rm.exitX)
        val want = if (crate != null) 20f else if (rm.open) 0f else 260f
        g.touchUp(5)
        val dx = target - d.x
        if (abs(dx) > want) { g.touchDown(5, 190f, H - 150f); g.touchMove(5, 190f + (if (dx > 0) 80f else -80f), H - 150f) }
        val j = g.controls().first { it.id == "jump" }
        g.touchUp(6)
        if (crate != null && crate[1] > 0f && abs(dx) < 200f) g.touchDown(6, j.cx, j.cy)
        tick(g, 0.1f); t += 0.1f
        if (t > 5f && t < 5.11f) shot(g, "v7_loot")
        if (g.state == State.PICK && t < 3f) shot(g, "v7_pick")
    }
    g.touchUp(5); g.touchUp(6)
    println("after expedition t=${t.toInt()} state=${g.state} scrap=${save.scrap} fuel=${save.fuel} parts=${save.bpParts.toList()} banner=${g.campaign.banner} ${g.campaign.bannerSub}")
    tick(g, 0.5f)
    shot(g, "v7_back")
    // next day: drive
    tap(g, "night"); tick(g, 0.5f)
    while (g.state == State.SIEGE) tick(g, 0.5f)
    println("night 4: nights=${save.nights} wall=${g.campaign.wallHp.toInt()} ${g.campaign.banner}")
    tick(g, 4.5f)
    println("day ${save.day} acted=${save.dayActed} fuel=${save.fuel}")
    tap(g, "drive"); tick(g, 0.5f)
    val gas = g.controls().first { it.id == "gas" }
    g.touchDown(1, gas.cx, gas.cy)
    t = 0f
    while (g.state == State.PLAYING && t < 120f) { tick(g, 0.2f); t += 0.2f; if (t > 6f && t < 6.21f) shot(g, "v7_drive") }
    g.touchUp(1)
    println("after drive t=${t.toInt()} state=${g.state} distM=${save.distM} fuel=${save.fuel} banner=${g.campaign.banner} ${g.campaign.bannerSub}")
    tick(g, 0.5f)
    shot(g, "v7_newcamp")
    tap(g, "workshop"); tick(g, 0.5f)
    shot(g, "v7_workshop")
}
