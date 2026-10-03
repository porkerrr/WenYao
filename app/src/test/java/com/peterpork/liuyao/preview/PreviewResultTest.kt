package com.peterpork.liuyao.preview

import org.junit.Assert.*
import org.junit.Test

class PreviewResultTest {
    @Test fun allOrderedTriplesHaveDistinctFixedAssets() {
        val expected = (2..3).flatMap { a -> (2..3).flatMap { b -> (2..3).map { c -> "$a$b$c" } } }
        assertEquals(expected, PreviewResult.entries.map { it.code })
        assertEquals(8, PreviewResult.entries.map { it.assetDirectory }.toSet().size)
        PreviewResult.entries.forEach { assertEquals(it.code.map(Char::digitToInt), it.scores) }
    }
    @Test fun invalidInputCannotBecomeAnAssetPathOrRandomFallback() {
        listOf(null, "", "2233", "123", "../223", "coins_223", " 223").forEach {
            assertNull(PreviewResult.fromCode(it))
        }
        assertEquals(PreviewResult.R232, PreviewResult.fromCode("232"))
    }
}
