package com.peterpork.liuyao.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeepSeekSetupGuideTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun guideDisplaysInstructionsAndOpensOfficialPlatform() {
        val openedUrls = mutableListOf<String>()
        composeRule.setContent {
            WenYaoTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    DeepSeekSetupGuide(onOpenUrl = openedUrls::add)
                }
            }
        }
        composeRule.onNodeWithText("获取 DeepSeek API Key").assertIsDisplayed()
        DeepSeekSetupGuideContent.steps.forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithText(DeepSeekSetupGuideContent.SECURITY_NOTICE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("打开 DeepSeek API 平台").performScrollTo().performClick()
        assertEquals(listOf(DeepSeekSetupGuideContent.API_KEY_URL), openedUrls)
    }
}
