package com.khongvinh.carsurvival

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Hosts the game loop on a background thread and forwards touches to [Game]. */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private val game = Game(SaveData(context))

    @Volatile
    private var running = false
    private var thread: Thread? = null

    /** Screen pixels per virtual unit; the game is laid out on a 720-unit-wide canvas. */
    @Volatile
    private var scale = 1f

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "GameLoop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(game) {
            scale = width / Game.VIRTUAL_WIDTH
            game.resize(Game.VIRTUAL_WIDTH, height / scale)
            val cutoutTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rootWindowInsets?.displayCutout?.safeInsetTop ?: 0
            } else {
                0
            }
            game.topInset = cutoutTop / scale
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        var retry = true
        while (retry) {
            try {
                thread?.join()
                retry = false
            } catch (_: InterruptedException) {
            }
        }
        thread = null
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now

            val canvas: Canvas? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) holder.lockHardwareCanvas() else holder.lockCanvas()
            } catch (_: Exception) {
                null
            }
            if (canvas == null) {
                Thread.sleep(16)
                continue
            }
            try {
                synchronized(game) {
                    game.update(dt)
                    canvas.save()
                    canvas.scale(scale, scale)
                    game.render(canvas)
                    canvas.restore()
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x / scale
        val y = event.y / scale
        synchronized(game) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> game.touchDown(x, y)
                MotionEvent.ACTION_MOVE -> game.touchMove(x, y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> game.touchUp()
            }
        }
        return true
    }

    fun onHostPause() {
        synchronized(game) { game.pause() }
    }

    /** Returns true when the back press was consumed by the game. */
    fun onBack(): Boolean = synchronized(game) { game.back() }
}
