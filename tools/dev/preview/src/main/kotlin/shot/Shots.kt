package shot

import android.content.Context
import com.khongvinh.carsurvival.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

const val W = 1600
const val H = 720
lateinit var out: File

fun shot(g: Game, name: String) {
    val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
    val gr = img.createGraphics()
    g.render(android.graphics.Canvas(gr))
    gr.dispose()
    ImageIO.write(img, "png", File(out, "$name.png"))
}

fun tick(g: Game, s: Float) { var t = 0f; while (t < s) { g.update(1f / 60f); t += 1f / 60f } }
fun tap(g: Game, id: String) { val b = g.buttons().first { it.id == id }; g.touchDown(99, b.cx, b.cy); g.touchUp(99) }

fun wav(f: File, pcm: ShortArray) {
    val bb = java.nio.ByteBuffer.allocate(44 + pcm.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    bb.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
    bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(22050).putInt(44100).putShort(2).putShort(16)
    bb.put("data".toByteArray()).putInt(pcm.size * 2)
    for (v in pcm) bb.putShort(v)
    f.writeBytes(bb.array())
}

fun main(args: Array<String>) {
    out = File(args[0]).also { it.mkdirs() }
    val ctx = Context()
    val save = SaveData(ctx)
    save.money = 5000
    save.bestStage = 5
    save.stageSel = 1
    val music = Music()
    val g = Game(save, Sfx(ctx), music)
    g.resize(W.toFloat(), H.toFloat())
    Thread.sleep(3000)
    android.media.AudioTrack.written.forEachIndexed { i, pcm ->
        val name = listOf("chill", "intense", "victory")[i]
        wav(File(out, "music_$name.wav"), pcm)
        var peak = 0; var sum = 0.0
        for (v in pcm) { peak = maxOf(peak, kotlin.math.abs(v.toInt())); sum += v.toDouble() * v }
        println("music $name: ${pcm.size / 22050f}s peak=$peak rms=${kotlin.math.sqrt(sum / pcm.size).toInt()}")
    }
    tick(g, 1f)
    shot(g, "01_menu")
    tap(g, "play")
    g.car.ammo = 100f
    val gas = g.controls().first { it.id == "gas" }
    g.touchDown(1, gas.cx, gas.cy)
    tick(g, 3f); shot(g, "02_play")
    var n = 0
    for (k in 0 until 20) {
        tick(g, 0.4f)
        if (g.state != State.PLAYING) break
        if (n < 3 && g.zombies.count { it.x - g.car.x in 100f..900f } >= 2 && k % 3 == 0) { shot(g, "03_action_$n"); n++ }
    }
    // low HP: fire
    println("before fire: state=${g.state} dist=${g.distanceM}")
    g.car.hp = g.car.maxHp * 0.1f
    tick(g, 1f); shot(g, "04_fire")
    // destroyed: explosion
    g.car.hp = -1f
    tick(g, 0.15f); shot(g, "05_explode")
    tick(g, 0.8f); shot(g, "06_wreck")
    tick(g, 2f)
    g.touchUp(1)
    println("state=${g.state} reason=${g.endReason} score=${g.score} ach=${g.achievements}")
    tick(g, 1f); shot(g, "07_result")
    tap(g, "menu"); tick(g, 1f)
    // a stage-clear screen
    save.stageSel = 1
    tap(g, "play"); g.car.maxFuel = 999f; g.car.fuel = 999f; g.car.maxHp = 9999f; g.car.hp = 9999f
    g.touchDown(1, gas.cx, gas.cy)
    var t = 0f
    while (g.state == State.PLAYING && t < 120f) { tick(g, 0.5f); t += 0.5f }
    g.touchUp(1)
    println("stage1: state=${g.state} t=$t score=${g.score} ach=${g.achievements}")
    tick(g, 1f); shot(g, "08_stage_done")
}
