package com.khongvinh.carsurvival

/** Region of five stages; decides skyline, decor and the kinds of monsters. */
enum class Region(val title: String) {
    DESERT("Sa mạc"),
    VOLCANO("Núi lửa"),
    NIGHT("Rừng đêm"),
    ALIEN("Hành tinh lạ"),
}

/** Look of one stage's map. Colours are ARGB ints. */
class MapTheme(
    val stage: Int,
    val name: String,
    val region: Region,
    /** Sky gradient: top, middle, horizon. */
    val sky: IntArray,
    val sunColor: Int,
    /** Sun/moon height as a fraction of the screen height. */
    val sunY: Float,
    val moon: Boolean,
    val stars: Boolean,
    val cloud: Int,
    val far: IntArray,
    val mid: IntArray,
    val skyline: Int,
    val haze: Int,
    /** Ground gradient: surface, middle, deep. */
    val ground: IntArray,
    /** Road crust: dark edge, top band, bright rim. */
    val crust: IntArray,
    val grass: IntArray,
)

object Themes {
    private fun lerp(a: Int, b: Int, t: Float): Int {
        val ia = 1f - t
        val aa = (a ushr 24) and 255
        val ab = (b ushr 24) and 255
        return ((aa * ia + ab * t).toInt() shl 24) or
            ((((a shr 16) and 255) * ia + ((b shr 16) and 255) * t).toInt() shl 16) or
            ((((a shr 8) and 255) * ia + ((b shr 8) and 255) * t).toInt() shl 8) or
            (((a and 255) * ia + (b and 255) * t).toInt())
    }

    private fun mix(a: IntArray, b: IntArray, t: Float) = IntArray(a.size) { lerp(a[it], b[it], t) }

    private val NAMES = arrayOf(
        "Sa Mạc Bình Minh", "Hẻm Núi Đỏ", "Thị Trấn Hoang", "Đồi Cát Hoàng Hôn", "Nghĩa Địa Sa Mạc",
        "Chân Núi Lửa", "Sông Dung Nham", "Hẻm Tro Tàn", "Miệng Núi Lửa", "Hang Rồng",
        "Rừng Sương Mù", "Đầm Lầy Ma", "Làng Bỏ Hoang", "Nghĩa Trang Trăng Máu", "Lâu Đài Ma Cà Rồng",
        "Vùng Thiên Thạch", "Rừng Pha Lê", "Căn Cứ Thất Thủ", "Thung Lũng Tím", "Tàu Mẹ Ngoài Hành Tinh",
    )

    val all: List<MapTheme> = (1..Game.STAGES).map { build(it) }

    fun of(stage: Int) = all[(stage - 1).coerceIn(0, all.size - 1)]

    private fun c(vararg v: Long) = IntArray(v.size) { v[it].toInt() }

    private fun build(stage: Int): MapTheme {
        val region = Region.entries[((stage - 1) / 5).coerceIn(0, 3)]
        val k = (stage - 1) % 5
        val t = k / 4f // 0 = first map of the region, 1 = boss map
        return when (region) {
            Region.DESERT -> {
                // dawn -> day -> afternoon -> sunset -> dusk
                val skies = arrayOf(
                    c(0xFF6D7FB8, 0xFFF0A3A0, 0xFFFFE0B2),
                    c(0xFF4F8FD1, 0xFF9BC7E8, 0xFFF3E2C0),
                    c(0xFFC7663E, 0xFFE89A5B, 0xFFF8D8A4),
                    c(0xFF8E3B46, 0xFFD9714A, 0xFFFBE3B0),
                    c(0xFF2E2448, 0xFF7B3F63, 0xFFE0805A),
                )
                MapTheme(
                    stage, NAMES[stage - 1], region, skies[k],
                    if (k == 4) 0xFFFFCCBC.toInt() else 0xFFFFF6E0.toInt(), 0.3f - 0.05f * k + 0.1f * (if (k >= 3) 1f else 0f), false, k == 4,
                    0xCCFFF1DC.toInt(),
                    mix(c(0xFFC98F7A, 0xFFE7B892), c(0xFF6A4258, 0xFF9C5E5E), t),
                    mix(c(0xFFA35E3F, 0xFFC4855A), c(0xFF4E2C36, 0xFF7A4038), t),
                    if (k == 2 || k == 4) 1 else 0, 0x33FBE3B0,
                    mix(c(0xFFC99059, 0xFF9C653B, 0xFF5A361E), c(0xFF9C6B4E, 0xFF6E4433, 0xFF3A2218), t),
                    c(0xFF4A2B17, 0xFFB27A47, 0xFFE9BC85),
                    c(0xFFA1A33A, 0xFF7F7A2A),
                )
            }
            Region.VOLCANO -> MapTheme(
                stage, NAMES[stage - 1], region,
                mix(c(0xFF3B1F1F, 0xFF8A3A1E, 0xFFE0702F), c(0xFF1A0B0B, 0xFF5E1810, 0xFFC2410C), t),
                0xFFFFAB40.toInt(), 0.35f, false, false,
                0xAA3E2F2F.toInt(),
                c(0xFF4A2A26, 0xFF7A3E2A), c(0xFF2B1A18, 0xFF4E2A22),
                2, 0x33FF7043,
                c(0xFF4E4342, 0xFF2E2827, 0xFF161212),
                c(0xFF120E0D, 0xFF5D4037, 0xFFFF7043),
                c(0xFF6D4C41, 0xFF4E342E),
            )
            Region.NIGHT -> MapTheme(
                stage, NAMES[stage - 1], region,
                mix(c(0xFF0B1030, 0xFF27305E, 0xFF4F5B8C), c(0xFF140716, 0xFF3D0F1E, 0xFF7A1F2A), t),
                if (k >= 3) 0xFFFF8A80.toInt() else 0xFFF5F5DC.toInt(), 0.22f, true, true,
                0x66B0BEC5,
                c(0xFF26304F, 0xFF3A4670), c(0xFF151B30, 0xFF222B48),
                3, 0x40B0BEC5,
                c(0xFF3B4A33, 0xFF263021, 0xFF121810),
                c(0xFF0E120C, 0xFF4A5A3A, 0xFF8CA27A),
                c(0xFF6B8E5A, 0xFF4E6B3F),
            )
            Region.ALIEN -> MapTheme(
                stage, NAMES[stage - 1], region,
                mix(c(0xFF0B2A3A, 0xFF1E6B78, 0xFF7FE0C8), c(0xFF1A0730, 0xFF5B1A7A, 0xFFE07AD8), t),
                0xFFB2FF59.toInt(), 0.24f, true, true,
                0x6680DEEA,
                c(0xFF3A2A6A, 0xFF6A4A9A), c(0xFF241848, 0xFF41306E),
                4, 0x3380DEEA,
                c(0xFF6A3A7A, 0xFF46245A, 0xFF22102E),
                c(0xFF1A0A24, 0xFF8E4FA8, 0xFF64FFDA),
                c(0xFF64FFDA, 0xFF00BFA5),
            )
        }
    }
}
