package shot
import com.khongvinh.carsurvival.*
import kotlin.math.*

fun run(stage: Int, seconds: Float, gas: Boolean, lean: Float = 0f, top: Float = 700f, acc: Float = 520f, boostAt: Float = -1f, verbose: Boolean = true): String {
    val goal = (700f + (stage - 1) * 100f) * 30f
    val t = Terrain(stage, goal, stage * 7919)
    val c = Car()
    c.x = 0f; c.y = t.heightAt(0f) - Car.WHEEL_Y - Car.WHEEL_R + 4f
    val p = CarPhysics()
    var time = 0f
    val h = 1f / 240f
    var flips = 0; var wasFlip = false; var minClear = 1e9f; var maxSpin = 0f; var air = 0f; var landings = 0
    var next = 0f
    while (time < seconds) {
        c.boosting = boostAt >= 0f && time > boostAt && time < boostAt + 1.5f
        val air0 = !c.grounded[0] && !c.grounded[1]
        val want = kotlin.math.atan(t.slopeAt(c.x + 60f))
        val l = if (lean != 0f) lean else if (air0 || cos(c.angle) < 0.7f) ((want - c.angle) * 3f).coerceIn(-1f, 1f) else 0f
        if (p.step(t, c, h, if (gas) 1f else 0f, l, top, acc, 1f)) landings++
        time += h
        val flip = cos(c.angle) < -0.3f
        if (flip && !wasFlip) flips++
        wasFlip = flip
        // clearance of body centre above ground
        minClear = min(minClear, t.heightAt(c.x) - c.y)
        maxSpin = max(maxSpin, abs(c.spin))
        if (!c.grounded[0] && !c.grounded[1]) air += h
        if (verbose && time >= next) {
            next += 2f
            println(String.format("  t=%5.1f x=%7.0f (%4.0fm) v=%6.0f ang=%6.1f° clear=%5.1f g=%s%s", time, c.x, c.x/30f, hypot(c.vx,c.vy), Math.toDegrees(c.angle.toDouble()), t.heightAt(c.x)-c.y, if (c.grounded[0]) "R" else "-", if (c.grounded[1]) "F" else "-"))
        }
        if (!c.x.isFinite() || !c.y.isFinite()) return "NaN!"
    }
    return String.format("stage %d: dist=%.0fm flips=%d minClear=%.1f maxSpin=%.1f air=%.1fs landings=%d", stage, c.x/30f, flips, minClear, maxSpin, air, landings)
}

fun simMain() {
    for (st in listOf(1, 5, 10, 20)) {
        val goal = (700f + (st - 1) * 100f) * 30f
        val t = Terrain(st, goal, st * 7919)
        var maxUp = 0f; var flat = 0; var x = 0f; var cnt = 0
        while (x < goal) { val s = -t.slopeAt(x); maxUp = maxOf(maxUp, s); if (kotlin.math.abs(s) < 0.03f) flat++; cnt++; x += 40f }
        println("stage $st: max uphill slope=${"%.2f".format(maxUp)} flat=${100 * flat / cnt}% ramps=${t.rampEnds.size}")
    }
    for (s in listOf(1, 3, 5, 10, 15, 20)) println("base   " + run(s, 40f, true, verbose = false))
    for (s in listOf(1, 3, 5, 10, 15, 20)) println("mid    " + run(s, 60f, true, top = 1000f, acc = 760f, verbose = false))
    for (s in listOf(5, 10, 15, 20)) println("maxed  " + run(s, 80f, true, top = 1475f, acc = 1060f, verbose = false))
}
