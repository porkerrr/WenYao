package com.peterpork.liuyao.domain

import com.peterpork.liuyao.ui.parseLineValues
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySnapshotTest {
    @Test fun `six valid line values parse`() {
        assertEquals(listOf(6, 7, 8, 9, 7, 8), parseLineValues("6,7,8,9,7,8").map { it.value })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `snapshot rejects fewer than six lines`() { parseLineValues("6,7,8") }

    @Test(expected = IllegalArgumentException::class)
    fun `snapshot rejects non line value`() { parseLineValues("6,7,8,9,7,5") }

    @Test(expected = IllegalArgumentException::class)
    fun `snapshot rejects extra characters`() { parseLineValues("6,7,8,9,7,08") }
}
