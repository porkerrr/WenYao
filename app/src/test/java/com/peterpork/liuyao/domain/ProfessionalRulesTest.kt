package com.peterpork.liuyao.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalRulesTest {
    private val expectedNajia = mapOf(
        7 to listOf("甲子", "甲寅", "甲辰", "壬午", "壬申", "壬戌"),
        3 to listOf("丁巳", "丁卯", "丁丑", "丁亥", "丁酉", "丁未"),
        5 to listOf("己卯", "己丑", "己亥", "己酉", "己未", "己巳"),
        1 to listOf("庚子", "庚寅", "庚辰", "庚午", "庚申", "庚戌"),
        6 to listOf("辛丑", "辛亥", "辛酉", "辛未", "辛巳", "辛卯"),
        2 to listOf("戊寅", "戊辰", "戊午", "戊申", "戊戌", "戊子"),
        4 to listOf("丙辰", "丙午", "丙申", "丙戌", "丙子", "丙寅"),
        0 to listOf("乙未", "乙巳", "乙卯", "癸丑", "癸亥", "癸酉")
    )

    @Test
    fun `frozen 64 row golden fixture matches palace results and names`() {
        val rows = javaClass.getResourceAsStream("/professional/palace_golden.csv")!!
            .bufferedReader().readLines().drop(1)
        assertEquals(64, rows.size)
        rows.forEach { row ->
            val columns = row.split(",")
            assertEquals(8, columns.size)
            val key = columns[0]
            val name = columns[1]
            val lower = columns[2]
            val upper = columns[3]
            val palace = columns[4]
            val type = columns[5]
            val shi = columns[6]
            val ying = columns[7]
            val pattern = HexagramPattern.fromKey(key.toInt())
            val result = PalaceEngine.calculate(pattern)
            val hexagram = LiuyaoEngine.calculate(bits(key.toInt()).map { if (it) LineValue.YOUNG_YANG else LineValue.YOUNG_YIN }).original
            assertEquals(name, hexagram.displayName)
            assertEquals(lower, pattern.lower.trigram.name)
            assertEquals(upper, pattern.upper.trigram.name)
            assertEquals(palace, result.palace.name)
            assertEquals(HexagramClass.valueOf(type), result.hexagramClass)
            assertEquals(shi.toInt(), result.shiPosition)
            assertEquals(ying.toInt(), result.yingPosition)
        }
    }

    @Test
    fun `bit and trigram orientation contract is fixed by named anchors`() {
        assertPattern(63, "乾", "乾", "乾为天")
        assertPattern(0, "坤", "坤", "坤为地")
        assertPattern(56, "坤", "乾", "天地否")
        assertPattern(7, "乾", "坤", "地天泰")
        assertPattern(62, "巽", "乾", "天风姤")
        assertPattern(3, "兑", "坤", "地泽临")
    }

    @Test
    fun `all palace and class set invariants hold`() {
        val results = (0 until 64).map { PalaceEngine.calculate(HexagramPattern.fromKey(it)) }
        assertEquals(64, results.size)
        assertEquals(8, results.groupingBy { it.palace.name }.eachCount().size)
        assertTrue(results.groupingBy { it.palace.name }.eachCount().values.all { it == 8 })
        assertTrue(results.groupingBy { it.hexagramClass }.eachCount().values.all { it == 8 })
        results.groupBy { it.palace.name }.values.forEach { palace ->
            assertEquals(HexagramClass.entries.toSet(), palace.map { it.hexagramClass }.toSet())
        }
    }

    @Test
    fun `all 48 base najia slots match the reviewed table`() {
        expectedNajia.forEach { (key, values) ->
            val trigram = TrigramPattern.fromKey(key)
            val actual = NajiaEngine.forTrigram(trigram, false) + NajiaEngine.forTrigram(trigram, true)
            assertEquals(values, actual.map(Najia::toString))
        }
    }

    @Test
    fun `all 384 composed najia lines use their own inner and outer trigram`() {
        (0 until 64).forEach { key ->
            val innerKey = key and 0b111
            val outerKey = key shr 3
            val expected = expectedNajia.getValue(innerKey).take(3) + expectedNajia.getValue(outerKey).drop(3)
            assertEquals(expected, NajiaEngine.forHexagram(HexagramPattern.fromKey(key)).map(Najia::toString))
        }
    }

    @Test
    fun `earthly branches have literal five element mapping`() {
        val expected = mapOf(
            '子' to FiveElement.WATER, '丑' to FiveElement.EARTH, '寅' to FiveElement.WOOD,
            '卯' to FiveElement.WOOD, '辰' to FiveElement.EARTH, '巳' to FiveElement.FIRE,
            '午' to FiveElement.FIRE, '未' to FiveElement.EARTH, '申' to FiveElement.METAL,
            '酉' to FiveElement.METAL, '戌' to FiveElement.EARTH, '亥' to FiveElement.WATER
        )
        expected.forEach { (glyph, element) -> assertEquals(element, EarthlyBranch.parse(glyph).element) }
    }

    @Test
    fun `all 25 five element pairs match literal relation matrix`() {
        val matrix = mapOf(
            FiveElement.WOOD to listOf(SixRelation.SIBLING, SixRelation.CHILD, SixRelation.WEALTH, SixRelation.OFFICIAL, SixRelation.PARENT),
            FiveElement.FIRE to listOf(SixRelation.PARENT, SixRelation.SIBLING, SixRelation.CHILD, SixRelation.WEALTH, SixRelation.OFFICIAL),
            FiveElement.EARTH to listOf(SixRelation.OFFICIAL, SixRelation.PARENT, SixRelation.SIBLING, SixRelation.CHILD, SixRelation.WEALTH),
            FiveElement.METAL to listOf(SixRelation.WEALTH, SixRelation.OFFICIAL, SixRelation.PARENT, SixRelation.SIBLING, SixRelation.CHILD),
            FiveElement.WATER to listOf(SixRelation.CHILD, SixRelation.WEALTH, SixRelation.OFFICIAL, SixRelation.PARENT, SixRelation.SIBLING)
        )
        FiveElement.entries.forEach { reference ->
            FiveElement.entries.forEachIndexed { index, line ->
                assertEquals(matrix.getValue(reference)[index], LiuqinEngine.calculate(reference, line))
            }
        }
    }

    @Test
    fun `changed chart relations keep original palace element as reference`() {
        val original = StaticChartEngine.calculateOriginal(HexagramPattern.fromKey(3)) // 地泽临，坤宫土
        val basic = LiuyaoEngine.calculate(
            listOf(LineValue.YOUNG_YANG, LineValue.OLD_YANG, LineValue.YOUNG_YIN,
                LineValue.YOUNG_YIN, LineValue.YOUNG_YIN, LineValue.YOUNG_YIN)
        )
        val changed = ChangedLineEngine.calculate(basic.changed, basic.movingLines, original.palace.palaceElement)
        assertEquals("地雷复", changed.name)
        assertEquals(listOf(2), changed.changedLines.map { it.position })
        assertEquals("庚寅", changed.changedLines.single().najia.toString())
        assertEquals(SixRelation.OFFICIAL, changed.changedLines.single().relation)
    }

    @Test
    fun `ten day stems match literal six spirit sequences`() {
        val q = SixSpirit.AZURE_DRAGON
        val z = SixSpirit.VERMILION_BIRD
        val g = SixSpirit.HOOK_SNAKE
        val t = SixSpirit.FLYING_SERPENT
        val b = SixSpirit.WHITE_TIGER
        val x = SixSpirit.BLACK_TORTOISE
        val expected = mapOf(
            HeavenlyStem.JIA to listOf(q,z,g,t,b,x), HeavenlyStem.YI to listOf(q,z,g,t,b,x),
            HeavenlyStem.BING to listOf(z,g,t,b,x,q), HeavenlyStem.DING to listOf(z,g,t,b,x,q),
            HeavenlyStem.WU to listOf(g,t,b,x,q,z), HeavenlyStem.JI to listOf(t,b,x,q,z,g),
            HeavenlyStem.GENG to listOf(b,x,q,z,g,t), HeavenlyStem.XIN to listOf(b,x,q,z,g,t),
            HeavenlyStem.REN to listOf(x,q,z,g,t,b), HeavenlyStem.GUI to listOf(x,q,z,g,t,b)
        )
        expected.forEach { (stem, spirits) -> assertEquals(spirits, LiushenEngine.forDayStem(stem)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid stem is rejected only at parser boundary`() {
        HeavenlyStem.parse('A')
    }

    private fun assertPattern(key: Int, lower: String, upper: String, name: String) {
        val pattern = HexagramPattern.fromKey(key)
        assertEquals(lower, pattern.lower.trigram.name)
        assertEquals(upper, pattern.upper.trigram.name)
        val lines = bits(key).map { if (it) LineValue.YOUNG_YANG else LineValue.YOUNG_YIN }
        assertEquals(name, LiuyaoEngine.calculate(lines).original.displayName)
    }

    private fun bits(value: Int): List<Boolean> = List(6) { index -> value and (1 shl index) != 0 }
}
