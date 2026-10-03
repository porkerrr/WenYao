package com.peterpork.liuyao.domain

import java.security.SecureRandom
import java.util.UUID

enum class LineValue(val value: Int, val label: String, val isYang: Boolean, val isMoving: Boolean) {
    OLD_YIN(6, "老阴", false, true),
    YOUNG_YANG(7, "少阳", true, false),
    YOUNG_YIN(8, "少阴", false, false),
    OLD_YANG(9, "老阳", true, true);

    companion object {
        fun from(value: Int): LineValue = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("爻值只能是 6、7、8、9")
    }
}

data class Trigram(val key: Int, val name: String, val nature: String)

data class Hexagram(
    val name: String,
    val upper: Trigram,
    val lower: Trigram,
    val yangBottomUp: List<Boolean>
) {
    val displayName: String = if (upper == lower) "${upper.name}为${upper.nature}" else "${upper.nature}${lower.nature}$name"
}

data class PaipanResult(
    val original: Hexagram,
    val changed: Hexagram,
    val movingLines: List<Int>,
    val linesBottomUp: List<LineValue>,
    val schemaVersion: Int = 1,
    val engineVersion: String = "0.1.0",
    val rulesetId: String = "hexagram-core-v1"
)

enum class CoinSide(val points: Int, val label: String) {
    HEADS(3, "正"),
    TAILS(2, "反")
}

data class CoinCastResult(
    val castId: String,
    val coins: List<CoinSide>
) {
    init {
        require(castId.isNotBlank()) { "投掷标识不能为空" }
        require(coins.size == 3) { "每次起爻必须投掷三枚铜钱" }
    }

    val total: Int = coins.sumOf(CoinSide::points)
    val lineValue: LineValue = LineValue.from(total)
}

fun interface CoinCaster { fun cast(): CoinCastResult }

class SecureCoinCaster(private val random: SecureRandom = SecureRandom()) : CoinCaster {
    override fun cast() = CoinCastResult(
        castId = UUID.randomUUID().toString(),
        coins = List(3) { if (random.nextBoolean()) CoinSide.HEADS else CoinSide.TAILS }
    )
}

object LiuyaoEngine {
    private val trigrams = mapOf(
        0 to Trigram(0, "坤", "地"), 1 to Trigram(1, "震", "雷"),
        2 to Trigram(2, "坎", "水"), 3 to Trigram(3, "兑", "泽"),
        4 to Trigram(4, "艮", "山"), 5 to Trigram(5, "离", "火"),
        6 to Trigram(6, "巽", "风"), 7 to Trigram(7, "乾", "天")
    )

    private val order = listOf(7, 3, 5, 1, 6, 2, 4, 0)
    private val names = listOf(
        listOf("乾", "履", "同人", "无妄", "姤", "讼", "遁", "否"),
        listOf("夬", "兑", "革", "随", "大过", "困", "咸", "萃"),
        listOf("大有", "睽", "离", "噬嗑", "鼎", "未济", "旅", "晋"),
        listOf("大壮", "归妹", "丰", "震", "恒", "解", "小过", "豫"),
        listOf("小畜", "中孚", "家人", "益", "巽", "涣", "渐", "观"),
        listOf("需", "节", "既济", "屯", "井", "坎", "蹇", "比"),
        listOf("大畜", "损", "贲", "颐", "蛊", "蒙", "艮", "剥"),
        listOf("泰", "临", "明夷", "复", "升", "师", "谦", "坤")
    )

    fun calculate(values: List<LineValue>): PaipanResult {
        require(values.size == 6) { "必须按初爻到上爻提供六个爻值" }
        val originalBits = values.map { it.isYang }
        val changedBits = values.map { if (it.isMoving) !it.isYang else it.isYang }
        return PaipanResult(
            original = identify(originalBits),
            changed = identify(changedBits),
            movingLines = values.mapIndexedNotNull { index, line -> if (line.isMoving) index + 1 else null },
            linesBottomUp = values.toList()
        )
    }

    private fun identify(bits: List<Boolean>): Hexagram {
        require(bits.size == 6)
        fun key(offset: Int) = (0..2).sumOf { i -> if (bits[offset + i]) 1 shl i else 0 }
        val lowerKey = key(0)
        val upperKey = key(3)
        val upperIndex = order.indexOf(upperKey)
        val lowerIndex = order.indexOf(lowerKey)
        return Hexagram(
            name = names[upperIndex][lowerIndex],
            upper = trigrams.getValue(upperKey),
            lower = trigrams.getValue(lowerKey),
            yangBottomUp = bits.toList()
        )
    }
}
