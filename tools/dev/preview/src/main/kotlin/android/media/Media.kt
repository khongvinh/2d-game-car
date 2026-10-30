package android.media

class AudioAttributes {
    companion object { const val USAGE_GAME = 14; const val CONTENT_TYPE_SONIFICATION = 4; const val CONTENT_TYPE_MUSIC = 2 }
    class Builder {
        fun setUsage(@Suppress("UNUSED_PARAMETER") u: Int) = this
        fun setContentType(@Suppress("UNUSED_PARAMETER") c: Int) = this
        fun build() = AudioAttributes()
    }
}

class SoundPool {
    class Builder {
        fun setMaxStreams(@Suppress("UNUSED_PARAMETER") n: Int) = this
        fun setAudioAttributes(@Suppress("UNUSED_PARAMETER") a: AudioAttributes) = this
        fun build() = SoundPool()
    }
    fun load(@Suppress("UNUSED_PARAMETER") p: String, @Suppress("UNUSED_PARAMETER") pr: Int): Int { val id = next++; listener?.invoke(this, id, 0); return id }
    fun play(id: Int, l: Float, r: Float, p: Int, loop: Int, rate: Float) = id + (l + r + p + loop + rate).toInt() * 0
    fun release() {}
    fun setRate(@Suppress("UNUSED_PARAMETER") id: Int, @Suppress("UNUSED_PARAMETER") r: Float) {}
    fun setVolume(@Suppress("UNUSED_PARAMETER") id: Int, @Suppress("UNUSED_PARAMETER") l: Float, @Suppress("UNUSED_PARAMETER") r: Float) {}
    fun stop(@Suppress("UNUSED_PARAMETER") id: Int) {}
    fun setOnLoadCompleteListener(l: (SoundPool, Int, Int) -> Unit) { listener = l }
    private var listener: ((SoundPool, Int, Int) -> Unit)? = null
    private var next = 1
}

/** Stand-in for android.media.MediaPlayer: silent in the preview. */
class MediaPlayer {
    var isLooping = false
    val isPlaying = false
    fun setDataSource(@Suppress("UNUSED_PARAMETER") path: String) {}
    fun prepare() {}
    fun start() {}
    fun pause() {}
    fun setVolume(@Suppress("UNUSED_PARAMETER") l: Float, @Suppress("UNUSED_PARAMETER") r: Float) {}
    fun release() {}
}
