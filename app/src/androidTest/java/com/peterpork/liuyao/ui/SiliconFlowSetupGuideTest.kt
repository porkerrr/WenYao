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

class SiliconFlowSetupGuideTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun guideDisplaysInstructionsAndOpensExpectedUrls() {
        val openedUrls = mutableListOf<String>()
        composeRule.setContent {
            WenYaoTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SiliconFlowSetupGuide(onOpenUrl = openedUrls::add)
                }
            }
        }

        composeRule.onNodeWithText("获取 SiliconFlow API Key").assertIsDisplayed()
        SiliconFlowSetupGuideContent.steps.forEach { step ->
            composeRule.onNodeWithText(step).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText(SiliconFlowSetupGuideContent.SECURITY_NOTICE).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("新用户注册硅基流动").performScrollTo().performClick()
        composeRule.onNodeWithText("已有账号：创建 API Key").performScrollTo().performClick()

        assertEquals(
            listOf(
                SiliconFlowSetupGuideContent.REGISTRATION_URL,
                SiliconFlowSetupGuideContent.API_KEY_URL,
            ),
            openedUrls,
        )
    }
}
