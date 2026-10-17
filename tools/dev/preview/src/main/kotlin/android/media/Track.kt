package android.media

class AudioFormat {
    companion object { const val ENCODING_PCM_16BIT = 2; const val CHANNEL_OUT_MONO = 4 }
    class Builder {
        fun setEncoding(@Suppress("UNUSED_PARAMETER") e: Int) = this
        fun setSampleRate(@Suppress("UNUSED_PARAMETER") r: Int) = this
        fun setChannelMask(@Suppress("UNUSED_PARAMETER") m: Int) = this
        fun build() = AudioFormat()
    }
}

class AudioTrack {
    companion object {
        const val MODE_STATIC = 0
        const val PLAYSTATE_PLAYING = 3
        val written = ArrayList<ShortArray>()
    }
    var playState = 1
    var playbackHeadPosition = 0
    class Builder {
        fun setAudioAttributes(@Suppress("UNUSED_PARAMETER") a: AudioAttributes) = this
        fun setAudioFormat(@Suppress("UNUSED_PARAMETER") f: AudioFormat) = this
        fun setTransferMode(@Suppress("UNUSED_PARAMETER") m: Int) = this
        fun setBufferSizeInBytes(@Suppress("UNUSED_PARAMETER") n: Int) = this
        fun build() = AudioTrack()
    }
    fun write(d: ShortArray, o: Int, n: Int): Int { written.add(d.copyOfRange(o, o + n)); return n }
    fun setLoopPoints(@Suppress("UNUSED_PARAMETER") a: Int, @Suppress("UNUSED_PARAMETER") b: Int, @Suppress("UNUSED_PARAMETER") c: Int) = 0
    fun setVolume(@Suppress("UNUSED_PARAMETER") v: Float) = 0
    fun play() { playState = PLAYSTATE_PLAYING }
    fun pause() { playState = 2 }
    fun release() {}
}
