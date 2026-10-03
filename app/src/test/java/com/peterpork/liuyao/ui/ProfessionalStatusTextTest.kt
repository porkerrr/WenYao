package com.peterpork.liuyao.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfessionalStatusTextTest {
    @Test fun `unavailable status is explicit`() {
        assertEquals("未开放，规则验证中", professionalStatusText("NOT_AVAILABLE"))
    }

    @Test fun `experimental status discloses missing expert review`() {
        assertEquals("实验性，尚未完成专家校验", professionalStatusText("EXPERIMENTAL"))
    }

    @Test fun `unknown persisted status fails closed`() {
        assertEquals("未开放，规则验证中", professionalStatusText("UNKNOWN"))
    }
    @Test fun `professional enum labels render in Chinese`() {
        assertEquals("木", elementLabel("WOOD"))
        assertEquals("官鬼", relationLabel("OFFICIAL"))
        assertEquals("青龙", spiritLabel("AZURE_DRAGON"))
        assertEquals("世", roleLabel("SHI"))
        assertEquals("游魂", classLabel("WANDERING_SOUL"))
    }

    @Test fun `cast time renders in persisted zone`() {
        assertEquals("2026-09-13 12:00:00 Asia/Shanghai", formatCastTime("2026-09-13T04:00:00Z", "Asia/Shanghai"))
    }
}
