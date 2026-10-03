package com.peterpork.liuyao.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiuyaoEngineTest {
    @Test fun `all three coin combinations map to traditional line values`() {
        val expected = mapOf(
            listOf(CoinSide.TAILS, CoinSide.TAILS, CoinSide.TAILS) to LineValue.OLD_YIN,
            listOf(CoinSide.HEADS, CoinSide.TAILS, CoinSide.TAILS) to LineValue.YOUNG_YANG,
            listOf(CoinSide.TAILS, CoinSide.HEADS, CoinSide.TAILS) to LineValue.YOUNG_YANG,
            listOf(CoinSide.TAILS, CoinSide.TAILS, CoinSide.HEADS) to LineValue.YOUNG_YANG,
            listOf(CoinSide.HEADS, CoinSide.HEADS, CoinSide.TAILS) to LineValue.YOUNG_YIN,
            listOf(CoinSide.HEADS, CoinSide.TAILS, CoinSide.HEADS) to LineValue.YOUNG_YIN,
            listOf(CoinSide.TAILS, CoinSide.HEADS, CoinSide.HEADS) to LineValue.YOUNG_YIN,
            listOf(CoinSide.HEADS, CoinSide.HEADS, CoinSide.HEADS) to LineValue.OLD_YANG
        )
        expected.forEach { (coins, line) ->
            val cast = CoinCastResult("fixture-${coins.joinToString()}", coins)
            assertEquals(coins.sumOf(CoinSide::points), cast.total)
            assertEquals(line, cast.lineValue)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cast rejects anything other than three coins`() {
        CoinCastResult("invalid", listOf(CoinSide.HEADS, CoinSide.TAILS))
    }

    @Test fun `line values expose correct yin yang and movement`() {
        assertFalse(LineValue.OLD_YIN.isYang)
        assertTrue(LineValue.OLD_YIN.isMoving)
        assertTrue(LineValue.YOUNG_YANG.isYang)
        assertFalse(LineValue.YOUNG_YANG.isMoving)
        assertFalse(LineValue.YOUNG_YIN.isYang)
        assertFalse(LineValue.YOUNG_YIN.isMoving)
        assertTrue(LineValue.OLD_YANG.isYang)
        assertTrue(LineValue.OLD_YANG.isMoving)
    }

    @Test fun `all yang is qian and all yin is kun`() {
        assertEquals("乾为天", LiuyaoEngine.calculate(List(6) { LineValue.YOUNG_YANG }).original.displayName)
        assertEquals("坤为地", LiuyaoEngine.calculate(List(6) { LineValue.YOUNG_YIN }).original.displayName)
    }

    @Test fun `earth over lake is lin`() {
        val lines = listOf(7, 7, 8, 8, 8, 8).map(LineValue::from)
        assertEquals("地泽临", LiuyaoEngine.calculate(lines).original.displayName)
    }

    @Test fun `all 64 static hexagrams have unique names`() {
        val names = (0 until 64).map { encoded ->
            val lines = List(6) { index -> if (encoded and (1 shl index) != 0) LineValue.YOUNG_YANG else LineValue.YOUNG_YIN }
            LiuyaoEngine.calculate(lines).original.displayName
        }
        assertEquals(64, names.toSet().size)
        assertTrue("地雷复" in names)
        assertTrue("水火既济" in names)
        assertTrue("火水未济" in names)
    }

    @Test fun `directional fixtures distinguish upper lower and bit order`() {
        fun name(bitsBottomUp: String): String = LiuyaoEngine.calculate(bitsBottomUp.map {
            if (it == '1') LineValue.YOUNG_YANG else LineValue.YOUNG_YIN
        }).original.displayName
        assertEquals("乾为天", name("111111"))
        assertEquals("坤为地", name("000000"))
        assertEquals("地天泰", name("111000"))
        assertEquals("天地否", name("000111"))
        assertEquals("地泽临", name("110000"))
        assertEquals("水火既济", name("101010"))
        assertEquals("火水未济", name("010101"))
    }

    @Test fun `moving lines flip and static lines remain`() {
        val result = LiuyaoEngine.calculate(listOf(6, 7, 8, 9, 7, 8).map(LineValue::from))
        assertEquals(listOf(1, 4), result.movingLines)
        result.linesBottomUp.indices.forEach { index ->
            if (result.linesBottomUp[index].isMoving) assertNotEquals(result.original.yangBottomUp[index], result.changed.yangBottomUp[index])
            else assertEquals(result.original.yangBottomUp[index], result.changed.yangBottomUp[index])
        }
    }

    @Test fun `all 4096 line combinations preserve invariants`() {
        val values = LineValue.entries
        repeat(4096) { encoded ->
            var cursor = encoded
            val lines = List(6) { values[(cursor % 4).also { cursor /= 4 }] }
            val result = LiuyaoEngine.calculate(lines)
            assertEquals(6, result.original.yangBottomUp.size)
            assertEquals(6, result.changed.yangBottomUp.size)
            assertEquals(lines.count { it.isMoving }, result.movingLines.size)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `engine rejects incomplete casts`() { LiuyaoEngine.calculate(List(5) { LineValue.YOUNG_YANG }) }
}
