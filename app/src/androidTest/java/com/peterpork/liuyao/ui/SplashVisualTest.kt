package com.peterpork.liuyao.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.airbnb.lottie.LottieCompositionFactory
import com.peterpork.liuyao.R
import com.peterpork.liuyao.preview.SplashPreviewActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SplashVisualTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun assetParsesAndActualHomeKeyframesHaveStableGeometry() {
        val result = LottieCompositionFactory.fromRawResSync(context, R.raw.wenyao_ink)
        assertNull(result.exception)
        assertEquals(7, result.value!!.layers.size)
        assertNotNull(LottieCompositionFactory.fromRawResSync(context, R.raw.wenyao_brush_mask).value)
        var central: androidx.compose.ui.geometry.Rect? = null
        var landed: androidx.compose.ui.geometry.Rect? = null
        for (ms in listOf(0, 400, 800, 1100, 1200, 1600, 1900, 2200)) {
            ActivityScenario.launch<SplashPreviewActivity>(Intent(context, SplashPreviewActivity::class.java)
                .putExtra("splash_ms", ms)).use {
                rule.waitForIdle()
                val bounds = rule.onNodeWithTag("splash-title").fetchSemanticsNode().boundsInRoot
                if (ms == HomeEntranceMotion.WritingEnd) central = bounds
                if (ms == HomeEntranceMotion.TotalMillis) landed = bounds
                if (ms < HomeEntranceMotion.TotalMillis) {
                    rule.onNodeWithTag("splash-skip").assertExists()
                    rule.onNode(hasSetTextAction()).assertDoesNotExist()
                    rule.onNodeWithText("历史记录").assertDoesNotExist()
                    rule.onNodeWithText("设置").assertDoesNotExist()
                }
                // Geometry/semantics use the controlled Compose environment. Pixel evidence is
                // captured separately via capture_splash_frames.py after the OS window is visible.
                rule.onNodeWithTag("splash-title").assertIsDisplayed()
            }
        }
        assertNotNull(central); assertNotNull(landed)
        assertTrue(central!!.top > landed!!.top)
        assertEquals(central!!.width, landed!!.width, 1f)
        assertEquals(central!!.height, landed!!.height, 1f)
    }

    @Test fun lottieMaskRevealsRegionsAndCoversOriginalWordmarkBeforeHandoff() {
        val source = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.wenyao_wordmark)
        val drawable = com.airbnb.lottie.LottieDrawable().apply {
            setComposition(LottieCompositionFactory.fromRawResSync(context, R.raw.wenyao_brush_mask).value!!)
            setBounds(0, 0, source.width, source.height)
        }
        fun covered(progress: Float): Double {
            val bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            drawable.progress = progress
            drawable.draw(android.graphics.Canvas(bitmap))
            var ink = 0; var revealed = 0
            for (y in 0 until source.height step 2) for (x in 0 until source.width step 2) {
                if (android.graphics.Color.alpha(source.getPixel(x, y)) > 32) {
                    ink++
                    if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 128) revealed++
                }
            }
            bitmap.recycle()
            return revealed.toDouble() / ink
        }
        assertTrue("Middle mask must reveal regions, not the entire image", covered(.6f) in .1.. .8)
        assertTrue("Mask must cover the original brush edges before completion", covered(.9999f) > .98)
        source.recycle()
    }

    @Test fun completedPreviewHasRealInputAndNavigation() {
        ActivityScenario.launch<SplashPreviewActivity>(Intent(context, SplashPreviewActivity::class.java)
            .putExtra("splash_ms", HomeEntranceMotion.TotalMillis)).use {
            rule.onNodeWithTag("splash-skip").assertDoesNotExist()
            rule.onNode(hasSetTextAction()).performTextInput("开屏隔离验收")
            rule.onNodeWithText("历史记录").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onNodeWithText("历史卦例").isDisplayed() }
            rule.onNodeWithText("历史卦例").assertExists()
            rule.onNodeWithContentDescription("返回").performClick()
            rule.onNodeWithText("开屏隔离验收").assertExists()
            rule.onNodeWithText("设置").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onNodeWithText("尚未配置 API Key").isDisplayed() }
            rule.onNodeWithContentDescription("返回").performClick()
            rule.onNodeWithText("开屏隔离验收").assertExists()
        }
    }

    @Test fun invalidLocalAssetFailsOpenWithoutBlockingHome() {
        ActivityScenario.launch<SplashPreviewActivity>(Intent(context, SplashPreviewActivity::class.java)
            .putExtra("fail_asset", true)).use { scenario ->
            rule.waitForIdle()
            scenario.onActivity { assertEquals(HomeEntranceSession.Phase.Finished, it.entrance.phase) }
            rule.onNodeWithTag("splash-skip").assertDoesNotExist()
            rule.onNode(hasSetTextAction()).assertIsEnabled()
        }
    }
}
