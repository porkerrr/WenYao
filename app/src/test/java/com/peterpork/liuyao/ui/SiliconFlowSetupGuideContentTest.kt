package com.peterpork.liuyao.ui

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiliconFlowSetupGuideContentTest {
    @Test fun `guide urls use expected secure SiliconFlow destinations`() {
        assertEquals("https", URI(SiliconFlowSetupGuideContent.REGISTRATION_URL).scheme)
        assertEquals("cloud.siliconflow.cn", URI(SiliconFlowSetupGuideContent.REGISTRATION_URL).host)
        assertEquals("/i/cxwSu8y4", URI(SiliconFlowSetupGuideContent.REGISTRATION_URL).path)

        assertEquals("https", URI(SiliconFlowSetupGuideContent.API_KEY_URL).scheme)
        assertEquals("cloud.siliconflow.cn", URI(SiliconFlowSetupGuideContent.API_KEY_URL).host)
        assertEquals("/account/ak", URI(SiliconFlowSetupGuideContent.API_KEY_URL).path)
    }

    @Test fun `guide contains five ordered steps and a key safety warning`() {
        assertEquals(5, SiliconFlowSetupGuideContent.steps.size)
        SiliconFlowSetupGuideContent.steps.forEachIndexed { index, step ->
            assertTrue(step.startsWith("${index + 1}."))
        }
        assertTrue(SiliconFlowSetupGuideContent.SECURITY_NOTICE.contains("只粘贴到本页"))
        assertTrue(SiliconFlowSetupGuideContent.SECURITY_NOTICE.contains("不要公开"))
    }
}
