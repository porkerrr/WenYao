package com.peterpork.liuyao.domain

enum class FiveElement { WOOD, FIRE, EARTH, METAL, WATER }

enum class HeavenlyStem(val glyph: Char) {
    JIA('甲'), YI('乙'), BING('丙'), DING('丁'), WU('戊'), JI('己'), GENG('庚'), XIN('辛'), REN('壬'), GUI('癸');

    companion object {
        fun parse(glyph: Char): HeavenlyStem = entries.firstOrNull { it.glyph == glyph }
            ?: throw IllegalArgumentException("未知天干：$glyph")
    }
}

enum class EarthlyBranch(val glyph: Char, val element: FiveElement) {
    ZI('子', FiveElement.WATER), CHOU('丑', FiveElement.EARTH), YIN('寅', FiveElement.WOOD),
    MAO('卯', FiveElement.WOOD), CHEN('辰', FiveElement.EARTH), SI('巳', FiveElement.FIRE),
    WU('午', FiveElement.FIRE), WEI('未', FiveElement.EARTH), SHEN('申', FiveElement.METAL),
    YOU('酉', FiveElement.METAL), XU('戌', FiveElement.EARTH), HAI('亥', FiveElement.WATER);

    companion object {
        fun parse(glyph: Char): EarthlyBranch = entries.firstOrNull { it.glyph == glyph }
            ?: throw IllegalArgumentException("未知地支：$glyph")
    }
}

data class GanZhi(val stem: HeavenlyStem, val branch: EarthlyBranch) {
    override fun toString(): String = "${stem.glyph}${branch.glyph}"

    companion object {
        fun parse(value: String): GanZhi {
            require(value.length == 2) { "干支必须包含一个天干和一个地支" }
            return GanZhi(HeavenlyStem.parse(value[0]), EarthlyBranch.parse(value[1]))
        }
    }
}

enum class HexagramClass(val shiPosition: Int) {
    PURE(6), FIRST(1), SECOND(2), THIRD(3), FOURTH(4), FIFTH(5), WANDERING_SOUL(4), RETURNING_SOUL(3)
}

enum class SixRelation { SIBLING, PARENT, OFFICIAL, WEALTH, CHILD }
enum class SixSpirit { AZURE_DRAGON, VERMILION_BIRD, HOOK_SNAKE, FLYING_SERPENT, WHITE_TIGER, BLACK_TORTOISE }

/** Three lines encoded bottom-up: bit0 is the bottom line and bit2 is the top line. */
@JvmInline
value class TrigramPattern private constructor(val key: Int) {
    val trigram: Trigram get() = TrigramRules.fromKey(key)
    fun opposite(): TrigramPattern = fromKey(key xor 0b111)

    companion object {
        fun fromKey(key: Int): TrigramPattern {
            require(key in 0..7) { "单卦编码必须在 0..7" }
            return TrigramPattern(key)
        }
    }
}

/** Six lines encoded bottom-up: bit0 is 初爻 and bit5 is 上爻. */
data class HexagramPattern(val lower: TrigramPattern, val upper: TrigramPattern) {
    val key: Int get() = lower.key or (upper.key shl 3)

    companion object {
        fun fromKey(key: Int): HexagramPattern {
            require(key in 0..63) { "六十四卦编码必须在 0..63" }
            return HexagramPattern(TrigramPattern.fromKey(key and 0b111), TrigramPattern.fromKey(key shr 3))
        }

        fun fromBitsBottomUp(bits: List<Boolean>): HexagramPattern {
            require(bits.size == 6) { "必须按初爻到上爻提供六个爻" }
            val key = bits.mapIndexed { index, yang -> if (yang) 1 shl index else 0 }.sum()
            return fromKey(key)
        }
    }
}

data class PalaceResult(
    val palace: Trigram,
    val palaceElement: FiveElement,
    val hexagramClass: HexagramClass,
    val shiPosition: Int,
    val yingPosition: Int
)

data class Najia(val ganZhi: GanZhi) {
    val element: FiveElement get() = ganZhi.branch.element
    override fun toString(): String = ganZhi.toString()
}

data class StaticProfessionalLine(
    val position: Int,
    val najia: Najia,
    val relation: SixRelation
)

/** Time-independent mechanical result; calendar fields deliberately live outside this model. */
data class StaticProfessionalChart(
    val pattern: HexagramPattern,
    val palace: PalaceResult,
    val referencePalaceElement: FiveElement,
    val lines: List<StaticProfessionalLine>
)

data class ChangedLineDetail(
    val position: Int,
    val najia: Najia,
    val relation: SixRelation
)

data class ChangedHexagramDetail(
    val name: String,
    val pattern: HexagramPattern,
    val changedLines: List<ChangedLineDetail>
)

object TrigramRules {
    private val names = mapOf(7 to "乾", 3 to "兑", 5 to "离", 1 to "震", 6 to "巽", 2 to "坎", 4 to "艮", 0 to "坤")
    private val natures = mapOf(7 to "天", 3 to "泽", 5 to "火", 1 to "雷", 6 to "风", 2 to "水", 4 to "山", 0 to "地")
    private val elements = mapOf(
        7 to FiveElement.METAL, 3 to FiveElement.METAL, 5 to FiveElement.FIRE, 1 to FiveElement.WOOD,
        6 to FiveElement.WOOD, 2 to FiveElement.WATER, 4 to FiveElement.EARTH, 0 to FiveElement.EARTH
    )

    fun fromKey(key: Int): Trigram {
        require(key in 0..7) { "单卦编码必须在 0..7" }
        return Trigram(key, names.getValue(key), natures.getValue(key))
    }

    fun element(trigram: Trigram): FiveElement = elements.getValue(trigram.key)
}

object PalaceEngine {
    fun calculate(pattern: HexagramPattern): PalaceResult {
        val inner = pattern.lower.key
        val outer = pattern.upper.key
        fun same(bit: Int) = (inner and (1 shl bit)) == (outer and (1 shl bit))
        val sameMask = (if (same(2)) 0b100 else 0) or (if (same(1)) 0b010 else 0) or (if (same(0)) 0b001 else 0)
        val hexagramClass = when (sameMask) {
            0b111 -> HexagramClass.PURE
            0b110 -> HexagramClass.FIRST
            0b100 -> HexagramClass.SECOND
            0b000 -> HexagramClass.THIRD
            0b001 -> HexagramClass.FOURTH
            0b011 -> HexagramClass.FIFTH
            0b010 -> HexagramClass.WANDERING_SOUL
            0b101 -> HexagramClass.RETURNING_SOUL
            else -> error("三位同异关系不完整")
        }
        val palacePattern = when (hexagramClass) {
            HexagramClass.RETURNING_SOUL -> pattern.lower
            HexagramClass.FOURTH, HexagramClass.FIFTH, HexagramClass.WANDERING_SOUL -> pattern.lower.opposite()
            else -> pattern.upper
        }
        val palace = palacePattern.trigram
        val shi = hexagramClass.shiPosition
        return PalaceResult(palace, TrigramRules.element(palace), hexagramClass, shi, if (shi > 3) shi - 3 else shi + 3)
    }
}

object NajiaEngine {
    private data class InnerOuter(val inner: List<String>, val outer: List<String>)
    private val table = mapOf(
        7 to InnerOuter(listOf("甲子", "甲寅", "甲辰"), listOf("壬午", "壬申", "壬戌")),
        3 to InnerOuter(listOf("丁巳", "丁卯", "丁丑"), listOf("丁亥", "丁酉", "丁未")),
        5 to InnerOuter(listOf("己卯", "己丑", "己亥"), listOf("己酉", "己未", "己巳")),
        1 to InnerOuter(listOf("庚子", "庚寅", "庚辰"), listOf("庚午", "庚申", "庚戌")),
        6 to InnerOuter(listOf("辛丑", "辛亥", "辛酉"), listOf("辛未", "辛巳", "辛卯")),
        2 to InnerOuter(listOf("戊寅", "戊辰", "戊午"), listOf("戊申", "戊戌", "戊子")),
        4 to InnerOuter(listOf("丙辰", "丙午", "丙申"), listOf("丙戌", "丙子", "丙寅")),
        0 to InnerOuter(listOf("乙未", "乙巳", "乙卯"), listOf("癸丑", "癸亥", "癸酉"))
    )

    fun forTrigram(pattern: TrigramPattern, isOuter: Boolean): List<Najia> {
        val pair = table.getValue(pattern.key)
        return (if (isOuter) pair.outer else pair.inner).map { Najia(GanZhi.parse(it)) }
    }

    fun forHexagram(pattern: HexagramPattern): List<Najia> = forTrigram(pattern.lower, false) + forTrigram(pattern.upper, true)
}

object WuxingEngine {
    fun fromBranch(branch: EarthlyBranch): FiveElement = branch.element
}

object LiuqinEngine {
    private val generating = mapOf(
        FiveElement.WOOD to FiveElement.FIRE, FiveElement.FIRE to FiveElement.EARTH,
        FiveElement.EARTH to FiveElement.METAL, FiveElement.METAL to FiveElement.WATER,
        FiveElement.WATER to FiveElement.WOOD
    )
    private val controlling = mapOf(
        FiveElement.WOOD to FiveElement.EARTH, FiveElement.EARTH to FiveElement.WATER,
        FiveElement.WATER to FiveElement.FIRE, FiveElement.FIRE to FiveElement.METAL,
        FiveElement.METAL to FiveElement.WOOD
    )

    /** Changed lines still use the original hexagram's palace element as the reference. */
    fun calculate(referencePalaceElement: FiveElement, lineElement: FiveElement): SixRelation = when {
        referencePalaceElement == lineElement -> SixRelation.SIBLING
        generating.getValue(lineElement) == referencePalaceElement -> SixRelation.PARENT
        generating.getValue(referencePalaceElement) == lineElement -> SixRelation.CHILD
        controlling.getValue(lineElement) == referencePalaceElement -> SixRelation.OFFICIAL
        controlling.getValue(referencePalaceElement) == lineElement -> SixRelation.WEALTH
        else -> error("五行关系不完整")
    }
}

object StaticChartEngine {
    fun calculateOriginal(pattern: HexagramPattern): StaticProfessionalChart {
        val palace = PalaceEngine.calculate(pattern)
        return assemble(pattern, palace, palace.palaceElement)
    }

    private fun assemble(
        pattern: HexagramPattern,
        palace: PalaceResult,
        referencePalaceElement: FiveElement
    ): StaticProfessionalChart {
        val lines = NajiaEngine.forHexagram(pattern).mapIndexed { index, najia ->
            StaticProfessionalLine(index + 1, najia, LiuqinEngine.calculate(referencePalaceElement, najia.element))
        }
        return StaticProfessionalChart(pattern, palace, referencePalaceElement, lines)
    }
}

object ChangedLineEngine {
    fun calculate(
        changedHexagram: Hexagram,
        movingPositions: List<Int>,
        originalPalaceElement: FiveElement
    ): ChangedHexagramDetail {
        require(movingPositions.distinct().size == movingPositions.size && movingPositions.all { it in 1..6 })
        val pattern = HexagramPattern.fromBitsBottomUp(changedHexagram.yangBottomUp)
        val najia = NajiaEngine.forHexagram(pattern)
        val lines = movingPositions.sorted().map { position ->
            val changedNajia = najia[position - 1]
            ChangedLineDetail(
                position,
                changedNajia,
                LiuqinEngine.calculate(originalPalaceElement, changedNajia.element)
            )
        }
        return ChangedHexagramDetail(changedHexagram.displayName, pattern, lines)
    }
}

object LiushenEngine {
    private val cycle = listOf(
        SixSpirit.AZURE_DRAGON, SixSpirit.VERMILION_BIRD, SixSpirit.HOOK_SNAKE,
        SixSpirit.FLYING_SERPENT, SixSpirit.WHITE_TIGER, SixSpirit.BLACK_TORTOISE
    )
    private val firstByStem = mapOf(
        HeavenlyStem.JIA to SixSpirit.AZURE_DRAGON, HeavenlyStem.YI to SixSpirit.AZURE_DRAGON,
        HeavenlyStem.BING to SixSpirit.VERMILION_BIRD, HeavenlyStem.DING to SixSpirit.VERMILION_BIRD,
        HeavenlyStem.WU to SixSpirit.HOOK_SNAKE, HeavenlyStem.JI to SixSpirit.FLYING_SERPENT,
        HeavenlyStem.GENG to SixSpirit.WHITE_TIGER, HeavenlyStem.XIN to SixSpirit.WHITE_TIGER,
        HeavenlyStem.REN to SixSpirit.BLACK_TORTOISE, HeavenlyStem.GUI to SixSpirit.BLACK_TORTOISE
    )

    fun forDayStem(dayStem: HeavenlyStem): List<SixSpirit> {
        val offset = cycle.indexOf(firstByStem.getValue(dayStem))
        return List(6) { cycle[(offset + it) % cycle.size] }
    }
}
