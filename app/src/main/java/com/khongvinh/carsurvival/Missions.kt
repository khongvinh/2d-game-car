package com.khongvinh.carsurvival

import kotlin.random.Random

/** Lifetime counters that daily missions and achievements are measured on. */
enum class Stat(val unit: String) {
    KILLS("zombie"),
    ROOMS("phòng"),
    BOSSES("trùm"),
    MONEY("$"),
    STAGES("ải"),
    GEMS("ngọc"),
    METERS("m"),
    HOUSES("nhà"),
}

class MissionKind(val stat: Stat, val text: String, val targets: IntArray, val gems: IntArray)

class Achievement(val title: String, val stat: Stat?, val target: Int, val gems: Int)

/** Three daily missions (new each day) and the permanent achievement list. */
object Missions {
    val kinds = listOf(
        MissionKind(Stat.KILLS, "Hạ %d zombie", intArrayOf(40, 90, 160), intArrayOf(6, 10, 16)),
        MissionKind(Stat.ROOMS, "Vượt qua %d phòng", intArrayOf(10, 25, 45), intArrayOf(6, 12, 18)),
        MissionKind(Stat.BOSSES, "Hạ %d trùm", intArrayOf(1, 2, 3), intArrayOf(8, 14, 20)),
        MissionKind(Stat.MONEY, "Kiếm %d$", intArrayOf(500, 1500, 3000), intArrayOf(6, 10, 16)),
        MissionKind(Stat.STAGES, "Vượt %d ải", intArrayOf(1, 1, 2), intArrayOf(10, 10, 20)),
        MissionKind(Stat.GEMS, "Nhặt %d ngọc đỏ", intArrayOf(15, 30, 50), intArrayOf(5, 9, 14)),
        MissionKind(Stat.METERS, "Lái xe %dm", intArrayOf(1000, 2500, 5000), intArrayOf(5, 9, 14)),
        MissionKind(Stat.HOUSES, "Lục sạch %d nhà", intArrayOf(1, 2, 4), intArrayOf(6, 10, 16)),
    )

    /** [Achievement.stat] null = stages cleared ([SaveData.bestStage]). */
    val achievements = listOf(
        Achievement("Diệt 100 zombie", Stat.KILLS, 100, 10),
        Achievement("Diệt 1.000 zombie", Stat.KILLS, 1000, 40),
        Achievement("Diệt 5.000 zombie", Stat.KILLS, 5000, 100),
        Achievement("Qua 30 phòng", Stat.ROOMS, 30, 10),
        Achievement("Qua 300 phòng", Stat.ROOMS, 300, 40),
        Achievement("Qua 1.000 phòng", Stat.ROOMS, 1000, 100),
        Achievement("Hạ 3 trùm", Stat.BOSSES, 3, 15),
        Achievement("Hạ 30 trùm", Stat.BOSSES, 30, 60),
        Achievement("Vượt ải 1", null, 1, 10),
        Achievement("Vượt ải 5", null, 5, 30),
        Achievement("Vượt ải 10", null, 10, 60),
        Achievement("Vượt ải 20", null, 20, 150),
        Achievement("Kiếm 10.000$", Stat.MONEY, 10000, 20),
        Achievement("Kiếm 100.000$", Stat.MONEY, 100000, 80),
        Achievement("Lục sạch 10 nhà", Stat.HOUSES, 10, 15),
        Achievement("Lái xe 20km", Stat.METERS, 20000, 20),
    )

    /** Local calendar day (Vietnam time) so missions roll over at midnight. */
    fun today(): Int = ((System.currentTimeMillis() + 7 * 3600_000L) / 86_400_000L).toInt()

    /** Three missions on different stats, tier picked at random, same for the whole day. */
    fun forDay(day: Int): List<Pair<Int, Int>> {
        val rnd = Random(day * 7919)
        return kinds.indices.shuffled(rnd).take(3).map { it to rnd.nextInt(3) }
    }
}
