package com.peterpork.liuyao.domain

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exhaustive static-core comparison against yaomancy/liuyao-engine at
 * 53291663a4c733c4cbdfa174a8d3075475c07fd3. The digest covers 64 hexagrams
 * and all ten day stems. See docs/third_party/oracles/yaomancy.md.
 */
class ProfessionalStaticOracleTest {
    @Test fun `all static charts match pinned oracle digest`() {
        val rows = buildList {
            for (key in 0 until 64) {
                val pattern = HexagramPattern.fromKey(key)
                val basic = LiuyaoEngine.calculate(List(6) { position ->
                    if (key and (1 shl position) != 0) LineValue.YOUNG_YANG else LineValue.YOUNG_YIN
                })
                val chart = StaticChartEngine.calculateOriginal(pattern)
                for (dayStem in HeavenlyStem.entries) {
                    val spirits = LiushenEngine.forDayStem(dayStem)
                    val lines = chart.lines.joinToString(",") { line ->
                        val role = when (line.position) {
                            chart.palace.shiPosition -> "世"
                            chart.palace.yingPosition -> "应"
                            else -> "-"
                        }
                        "${line.najia}:${line.najia.element.glyph()}:${line.relation.glyph()}:${spirits[line.position - 1].glyph()}:$role"
                    }
                    add("$key|${dayStem.ordinal}|${basic.original.displayName}|${chart.palace.palace.name}|${chart.palace.palaceElement.glyph()}|${chart.palace.hexagramClass.glyph()}|${chart.palace.shiPosition}|${chart.palace.yingPosition}|$lines")
                }
            }
        }
        assertEquals(640, rows.size)
        assertEquals(ORACLE_SHA256, MessageDigest.getInstance("SHA-256").digest(rows.joinToString("\n").toByteArray()).toHex())
    }

    private fun FiveElement.glyph() = mapOf(
        FiveElement.WOOD to "木", FiveElement.FIRE to "火", FiveElement.EARTH to "土",
        FiveElement.METAL to "金", FiveElement.WATER to "水",
    ).getValue(this)
    private fun SixRelation.glyph() = mapOf(SixRelation.SIBLING to "兄弟", SixRelation.PARENT to "父母", SixRelation.OFFICIAL to "官鬼", SixRelation.WEALTH to "妻财", SixRelation.CHILD to "子孙").getValue(this)
    private fun SixSpirit.glyph() = mapOf(SixSpirit.AZURE_DRAGON to "青龙", SixSpirit.VERMILION_BIRD to "朱雀", SixSpirit.HOOK_SNAKE to "勾陈", SixSpirit.FLYING_SERPENT to "螣蛇", SixSpirit.WHITE_TIGER to "白虎", SixSpirit.BLACK_TORTOISE to "玄武").getValue(this)
    private fun HexagramClass.glyph() = mapOf(HexagramClass.PURE to "本宫", HexagramClass.FIRST to "一世", HexagramClass.SECOND to "二世", HexagramClass.THIRD to "三世", HexagramClass.FOURTH to "四世", HexagramClass.FIFTH to "五世", HexagramClass.WANDERING_SOUL to "游魂", HexagramClass.RETURNING_SOUL to "归魂").getValue(this)
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object { private const val ORACLE_SHA256 = "141f099beae1537bb0611ef4e12a7a3044979bb61430421a0261db916e66985a" }
}
