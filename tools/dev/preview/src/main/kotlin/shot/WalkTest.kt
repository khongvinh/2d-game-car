package shot

import android.content.Context
import com.khongvinh.carsurvival.*

/** On foot: does the storm hurt the driver as it rolls past? */
fun main() {
    out = java.io.File("../out/shots").also { it.mkdirs() }
    val ctx = Context()
    val save = SaveData(ctx)
    save.storySeen = 63
    val g = Game(save, Sfx(ctx), Music())
    g.resize(W.toFloat(), H.toFloat())
    tick(g, 0.5f)
    tap0(g)
    tick(g, 0.5f)
    val ex = g.controls().first { it.id == "exit" }
    g.touchDown(1, ex.cx, ex.cy); g.touchUp(1)
    var t = 0f
    val fwd = g.controls().first { it.id == "leanFwd" }
    if (System.getenv("WALK") != null) g.touchDown(2, fwd.cx, fwd.cy)
    while (t < 90f && g.state == State.PLAYING) {
        tick(g, 0.25f); t += 0.25f
        if (g.driver.x - g.stormX < 200f || (t * 4).toInt() % 20 == 0) println("t=$t out=${g.driver.out} dx=${g.driver.x.toInt()} storm=${g.stormX.toInt()} hp=${g.driver.hp.toInt()} car=${g.car.hp.toInt()} dying=${g.dyingT}")
    }
    println("end state=${g.state} reason=${g.endReason} t=$t")
}
