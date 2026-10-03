package com.peterpork.liuyao.preview

import org.junit.Assert.*
import org.junit.Test

class CastingFrameReadinessTest {
    @Test fun transparentDitherAndShadowsDoNotCountAsPreparedCoins() {
        assertFalse(hasVisibleCoinPixels(IntArray(2304) { if (it % 2 == 0) 0 else 0x01ffffff }))
        assertFalse(hasVisibleCoinPixels(IntArray(2304) { if (it % 2 == 0) 0x40231b14 else 0 }))
        assertFalse(hasVisibleCoinPixels(intArrayOf(0xff302010.toInt(), 0xffb09050.toInt())))
    }
    @Test fun visibleOpaqueCoinDetailCountsButUniformClearDoesNot() {
        assertFalse(hasVisibleCoinPixels(IntArray(2304) { 0xffffffff.toInt() }))
        assertTrue(hasVisibleCoinPixels(IntArray(2304) {
            when (it) { 50, 51 -> 0xff302010.toInt(); 52, 53 -> 0xffb09050.toInt(); else -> 0 }
        }))
    }
}
