package com.peterpork.liuyao.ui

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekSetupGuideContentTest {
    @Test fun `guide uses the official secure API key destination`() {
        assertEquals("https", URI(DeepSeekSetupGuideContent.API_KEY_URL).scheme)
        assertEquals("platform.deepseek.com", URI(DeepSeekSetupGuideContent.API_KEY_URL).host)
        assertEquals("/api_keys", URI(DeepSeekSetupGuideContent.API_KEY_URL).path)
    }

    @Test fun `guide explains API key workflow and safety`() {
        assertEquals(5, DeepSeekSetupGuideContent.steps.size)
        DeepSeekSetupGuideContent.steps.forEachIndexed { index, step -> assertTrue(step.startsWith("${index + 1}.")) }
        assertTrue(DeepSeekSetupGuideContent.SECURITY_NOTICE.contains("不要公开"))
    }
}
