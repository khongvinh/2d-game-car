package com.khongvinh.carsurvival

/**
 * Hand weapons the driver carries on foot (out on the road and inside
 * buildings). Bought in the garage's "VŨ KHÍ PHỤ" tab; they never use ammo.
 * [melee] weapons hit everything within [range] in front instead of firing.
 */
/** Throws before the arm is spent (then a short rest refills them). */
const val THROW_CHARGES = 3

enum class SideWeapon(
    val title: String,
    val desc: String,
    val cost: Int,
    val unlockStage: Int,
    val damage: Float,
    val interval: Float,
    val range: Float,
    val speed: Float = 0f,
    val gravity: Float = 0f,
    val pierce: Int = 1,
    val melee: Boolean = false,
    val pellets: Int = 1,
    val spread: Float = 0f,
    val knock: Float = 0f,
) {
    PISTOL("Súng lục", "Có sẵn. Bắn đều, tầm vừa.", 0, 0, 1.5f, 0.32f, 700f, 1900f),
    DART("Dao phóng", "Ném dao liên tục, bay hơi cong.", 520, 0, 1.1f, 0.2f, 560f, 1300f, 520f),
    SPEAR("Giáo phóng", "Phóng giáo bay thẳng, ghim xuyên 3 con.", 1100, 1, 3.6f, 0.55f, 820f, 1500f, 240f, pierce = 3, knock = 160f),
    BOW("Cung tên", "Mũi tên mạnh, xuyên 2 con, bay vòng cung.", 1600, 2, 3.6f, 0.62f, 950f, 1500f, 360f, pierce = 2),
    AXE("Rìu phóng", "Ném rìu xoay tròn, cực mạnh, hất văng quái.", 2900, 4, 6.5f, 0.75f, 640f, 1100f, 620f, pierce = 2, knock = 420f),
    SHOTGUN("Súng săn", "Chùm 5 viên, tầm gần cực mạnh.", 4500, 6, 1.15f, 0.8f, 460f, 1700f, pellets = 5, spread = 0.22f),
    SMG("Tiểu liên", "Xả đạn liên tục, tầm xa.", 7800, 9, 1.0f, 0.1f, 700f, 2000f);

    /** Damage per second, for the garage bars. */
    val dps get() = damage * pellets / interval

    /** Rounds in one magazine (guns) before a reload; thrown weapons use [THROW_CHARGES] instead. */
    val magazine get() = when (this) {
        PISTOL -> 12
        SHOTGUN -> 6
        SMG -> 30
        BOW -> 8
        else -> 0
    }

    /** Seconds to reload a full magazine. */
    val reload get() = when (this) {
        SHOTGUN -> 1.6f
        SMG -> 1.5f
        BOW -> 1.1f
        else -> 1.0f
    }

    /** Thrown by hand (wind-up and throw pose) rather than fired. */
    val thrown get() = this == DART || this == SPEAR || this == AXE

    val kind get() = when {
        melee -> "Cận chiến"
        gravity > 0f -> "Ném / bắn cong"
        else -> "Bắn thẳng"
    }
}
