package com.peterpork.liuyao.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import kotlin.math.pow
import com.peterpork.liuyao.domain.LineValue

class WenYaoDesignTokenTest {
    @org.junit.Test fun onlyMovingLinesReceiveCastingMarker() {
        val lines = listOf(8, 7, 6, 9).map(com.peterpork.liuyao.domain.LineValue::from)
        org.junit.Assert.assertFalse(lineIsMoving(lines, 1))
        org.junit.Assert.assertFalse(lineIsMoving(lines, 2))
        org.junit.Assert.assertTrue(lineIsMoving(lines, 3))
        org.junit.Assert.assertTrue(lineIsMoving(lines, 4))
        org.junit.Assert.assertFalse(lineIsMoving(lines, 5))
    }
    @Test
    fun `paper and ink scheme maps design specification to material roles`() {
        assertEquals(Color(0xFFFAF8F3), WenYaoColorScheme.background)
        assertEquals(Color(0xFF1C1B1A), WenYaoColorScheme.primary)
        assertEquals(Color(0xFFF3EFE6), WenYaoColorScheme.surfaceVariant)
        assertEquals(Color(0xFFBA1A1A), WenYaoColorScheme.error)
    }

    @Test
    fun `spacing scale stays on the four dp grid`() {
        assertEquals(4.dp, WenYaoSpacing.Xs)
        assertEquals(8.dp, WenYaoSpacing.Sm)
        assertEquals(12.dp, WenYaoSpacing.Md)
        assertEquals(16.dp, WenYaoSpacing.Lg)
        assertEquals(24.dp, WenYaoSpacing.Xl)
        assertEquals(32.dp, WenYaoSpacing.Xxl)
    }

    @Test
    fun `primary action height follows the design system`() {
        assertEquals(52.dp, WenYaoSpacing.ActionHeight)
        assertEquals(48.dp, WenYaoSpacing.SecondaryActionHeight)
    }

    @Test
    fun `moving line labels retain their display order`() {
        assertEquals("初爻、三爻、上爻", movingLineNames(listOf(1, 3, 6)))
        assertEquals("无", movingLineNames(emptyList()))
    }
    @Test
    fun `readable text and moving line accents meet contrast on both paper surfaces`() {
        listOf(WenYaoColorScheme.surface, WenYaoColorScheme.surfaceVariant).forEach { background ->
            listOf(WenYaoColorScheme.onSurface, WenYaoColorScheme.onSurfaceVariant, WenYaoColorScheme.tertiary).forEach { foreground ->
                val ratio = contrast(foreground, background)
                assertTrue("Contrast $ratio for $foreground on $background", ratio >= 4.5)
            }
        }
    }

    @Test
    fun `modal and menu surfaces do not inherit material purple defaults`() {
        assertEquals(WenYaoColorScheme.surfaceVariant, WenYaoColorScheme.surfaceContainerHigh)
        assertEquals(WenYaoColorScheme.surfaceVariant, WenYaoColorScheme.surfaceContainer)
        assertEquals(WenYaoColorScheme.onSurface, WenYaoColorScheme.surfaceTint)
    }

    @Test
    fun `grouped hexagram semantics include every value and moving position`() {
        val lines = listOf(6, 7, 8, 9, 7, 8).map(LineValue::from)
        val description = hexagramDescription("本卦示例", "变卦示例", lines)
        assertTrue(description.contains("动爻：初爻、四爻"))
        lines.forEachIndexed { index, line -> assertTrue(description.contains("${lineName(index + 1)} ${line.value} ${line.label}")) }
    }

    private fun contrast(a: Color, b: Color): Double {
        fun luminance(c: Color): Double {
            fun linear(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            return 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)
        }
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
