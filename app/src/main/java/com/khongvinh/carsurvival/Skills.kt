package com.khongvinh.carsurvival

import kotlin.math.pow
import kotlin.math.roundToInt

/** Stat raised by a node on the main line of the skill tree. */
enum class SkillStat(val title: String, val color: Int) {
    BOOST("Tên lửa đẩy", 0xFFE57373.toInt()),
    HP("Máu", 0xFF8BC34A.toInt()),
    ARMOR("Giáp", 0xFF90A4AE.toInt()),
    ENGINE("Động cơ", 0xFFFFB74D.toInt()),
    FUEL("Bình xăng", 0xFFFF8A65.toInt()),
    AMMO("Đạn", 0xFFFFD54F.toInt()),
    GRIP("Lốp xe", 0xFF4DD0E1.toInt()),
}

/** Special abilities hanging off the main line (the flame nodes). */
enum class Perk(val title: String, val desc: String) {
    FIRE_ROUNDS("Đạn lửa", "+25% sát thương mọi vũ khí"),
    HARVEST("Thu hoạch", "+25% tiền từ quái và vật cản"),
    MAGNET("Nam châm", "Hút tiền, ngọc, vật phẩm từ xa"),
    SPIKES("Giáp gai", "Sức húc x1,5"),
    REGEN("Tự sửa chữa", "Xe tự hồi 1,5 máu mỗi giây"),
    SCAVENGER("Lục soát nhanh", "Lục nhà nhanh gấp đôi, dễ ra mảnh xe hơn"),
}

/**
 * One node. Main nodes ([stat] set) form a chain; perk nodes ([perk] set) hang
 * under a main node ([parent]) and open once it is bought.
 */
class SkillNode(val id: Int, val stat: SkillStat?, val amount: Float, val perk: Perk?, val parent: Int, val page: Int, val slot: Int)

object Skills {
    const val ROUNDS = 3
    const val BASE_GEMS = 5.0

    private val perAmount = mapOf(
        SkillStat.BOOST to 0.4f, SkillStat.HP to 12f, SkillStat.ARMOR to 2f, SkillStat.ENGINE to 4f,
        SkillStat.FUEL to 8f, SkillStat.AMMO to 12f, SkillStat.GRIP to 4f,
    )

    val nodes: List<SkillNode>
    val mainCount: Int

    init {
        val list = ArrayList<SkillNode>()
        val stats = SkillStat.entries
        var id = 0
        for (r in 0 until ROUNDS) for ((k, st) in stats.withIndex()) {
            list.add(SkillNode(id++, st, perAmount.getValue(st) * (1f + 0.25f * r), null, id - 2, r, k))
        }
        mainCount = list.size
        // two perks per page, under the 2nd and 5th node of the round
        val perks = Perk.entries
        for ((i, p) in perks.withIndex()) {
            val page = i / 2
            val slot = if (i % 2 == 0) 1 else 4
            list.add(SkillNode(id++, null, 0f, p, page * stats.size + slot, page, slot))
        }
        nodes = list
    }

    /** "+12", "+0.4s", "+4%" … for the label above a node. */
    fun label(n: SkillNode): String {
        val st = n.stat ?: return n.perk!!.title
        val a = n.amount
        return when (st) {
            SkillStat.BOOST -> "+%.1fs".format(a)
            SkillStat.HP, SkillStat.AMMO -> "+${a.roundToInt()}"
            else -> "+${a.roundToInt()}%"
        }
    }

    /** Every node costs 20% more red gems than the one bought before it. */
    fun cost(bought: Int) = (BASE_GEMS * 1.2.pow(bought)).roundToInt()

    fun owned(bits: Int, id: Int) = bits and (1 shl id) != 0
    fun count(bits: Int) = Integer.bitCount(bits)

    fun available(bits: Int, n: SkillNode) = !owned(bits, n.id) && (n.parent < 0 || owned(bits, n.parent))

    /** Sum of a stat over the bought nodes. */
    fun total(bits: Int, st: SkillStat) = nodes.filter { it.stat == st && owned(bits, it.id) }.sumOf { it.amount.toDouble() }.toFloat()

    fun has(bits: Int, p: Perk) = nodes.any { it.perk == p && owned(bits, it.id) }
}

/** Player level from experience earned on runs: each level needs 60 x level XP. */
object Rank {
    fun need(level: Int) = 60 * level

    /** (level, xp into it, xp needed for the next). */
    fun of(xp: Int): Triple<Int, Int, Int> {
        var lv = 1
        var left = xp
        while (left >= need(lv)) {
            left -= need(lv)
            lv++
        }
        return Triple(lv, left, need(lv))
    }
}
