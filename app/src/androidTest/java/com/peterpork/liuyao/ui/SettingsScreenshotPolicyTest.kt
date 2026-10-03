package com.peterpork.liuyao.ui

import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.peterpork.liuyao.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenshotPolicyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun settingsScreenAllowsScreenshots() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.waitForIdle()

        val flags = composeRule.activity.window.attributes.flags
        assertEquals(0, flags and WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Test fun settingsCanSwitchToDeepSeekOfficial() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("DeepSeek 官方").performScrollTo().performClick()
        composeRule.onNodeWithText("AI 解读 · DeepSeek 官方").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("固定模型：deepseek-flash").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("获取 DeepSeek API Key").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("DeepSeek 请求固定关闭 thinking，不发送 reasoning_effort。").performScrollTo().assertIsDisplayed()
    }
}
