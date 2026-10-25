package android.content

import java.io.File

open class Context {
    companion object { const val MODE_PRIVATE = 0 }
    val cacheDir: File = File(System.getProperty("java.io.tmpdir"))
    private val prefs = HashMap<String, SharedPreferences>()
    fun getSharedPreferences(name: String, @Suppress("UNUSED_PARAMETER") mode: Int) = prefs.getOrPut(name) { SharedPreferences() }
}

class SharedPreferences {
    val map = HashMap<String, Any>()
    fun getInt(k: String, d: Int) = map[k] as? Int ?: d
    fun getBoolean(k: String, d: Boolean) = map[k] as? Boolean ?: d
    fun getString(k: String, d: String?) = map[k] as? String ?: d
    fun contains(k: String) = map.containsKey(k)
    fun edit() = Editor(this)
    class Editor(val p: SharedPreferences) {
        fun putInt(k: String, v: Int) = this.also { p.map[k] = v }
        fun putBoolean(k: String, v: Boolean) = this.also { p.map[k] = v }
        fun putString(k: String, v: String) = this.also { p.map[k] = v }
        fun remove(k: String) = this.also { p.map.remove(k) }
        fun apply() {}
    }
}
