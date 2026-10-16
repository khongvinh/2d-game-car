package com.khongvinh.carsurvival

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Hosts the game loop on a background thread and forwards (multi-)touches to [Game]. */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private val sfx = Sfx(context)
    private val music = Music()
    private val game = Game(SaveData(context), sfx, music)

    @Volatile
    private var running = false
    private var thread: Thread? = null

    /** Screen pixels per virtual unit; the game is laid out on a 720-unit-tall canvas. */
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
            scale = height / Game.VIRTUAL_HEIGHT
            game.resize(width / scale, Game.VIRTUAL_HEIGHT)
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
        synchronized(game) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = event.actionIndex
                    game.touchDown(event.getPointerId(i), event.getX(i) / scale, event.getY(i) / scale)
                }
                MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                    game.touchMove(event.getPointerId(i), event.getX(i) / scale, event.getY(i) / scale)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> game.touchUp(event.getPointerId(event.actionIndex))
                MotionEvent.ACTION_CANCEL -> for (i in 0 until event.pointerCount) game.touchUp(event.getPointerId(i))
            }
        }
        return true
    }

    fun onHostPause() {
        synchronized(game) { game.pause() }
        music.suspend()
    }

    fun onHostResume() {
        music.suspended = false
    }

    fun release() {
        sfx.release()
        music.release()
    }

    /** Returns true when the back press was consumed by the game. */
    fun onBack(): Boolean = synchronized(game) { game.back() }
}
