package com.peterpork.liuyao.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutModePolicyTest {
    @Test fun compactWidthsKeepTheFocusedPhoneLayout() {
        assertEquals(LayoutMode.Compact, layoutModeFor(360))
        assertEquals(LayoutMode.Compact, layoutModeFor(599))
    }

    @Test fun expandedWidthsUseTheRoomierReadingLayout() {
        assertEquals(LayoutMode.Expanded, layoutModeFor(600))
        assertEquals(LayoutMode.Expanded, layoutModeFor(840))
    }
}
