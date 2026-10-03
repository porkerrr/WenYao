package com.peterpork.liuyao.preview

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File

@RunWith(AndroidJUnit4::class)
class CoinPreviewTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun awaitText(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertFinalPose(scenario: ActivityScenario<CoinPreviewActivity>) {
        scenario.onActivity { activity ->
            val manifest = activity.assets.open("${activity.previewAssetDirectory}/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
            val positions = manifest.getJSONArray("finalTranslations")
            val scores = manifest.getJSONArray("scores")
            activity.snapshotCoinTransforms().forEachIndexed { i, matrix ->
                for (axis in 0..2) assertEquals(positions.getJSONArray(i).getDouble(axis), matrix[12+axis].toDouble(), 0.0001)
                // glTF local +Y is Blender local +Z (the 3-point face).
                assertTrue(kotlin.math.abs(matrix[5]) > 0.98f)
                assertEquals(scores.getInt(i), if (matrix[5] > 0) 3 else 2)
            }
        }
    }

    private fun capturePreview(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    private fun exercisePreview(round2: Boolean) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CoinPreviewActivity::class.java)
            .putExtra("round2", round2)
        val label = if (round2) "round2" else "round1"
        ActivityScenario.launch<CoinPreviewActivity>(intent).use { scenario ->
            awaitText("加载完成 · 等待播放")
            scenario.onActivity {
                assertEquals(if (round2) "coin_preview_round2" else "coin_preview", it.previewAssetDirectory)
            }
            compose.onNodeWithText("播放一次 / 重播同一段").performScrollTo().performClick()
            awaitText("正在播放同一段样片")
            SystemClock.sleep(300)
            capturePreview("${label}_flight")
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitText("播放完成 · 保持最终姿态")
            assertFinalPose(scenario)
            capturePreview("${label}_final")
            scenario.recreate()
            awaitText("已到末帧 · 保持最终姿态")
            assertFinalPose(scenario)
            compose.onNodeWithText("播放一次 / 重播同一段").performScrollTo().performClick()
            awaitText("正在播放同一段样片")
            compose.onNodeWithText("跳到末帧").performScrollTo().performClick()
            awaitText("已到末帧 · 保持最终姿态")
            assertFinalPose(scenario)
        }
        // Re-entering creates fresh resources and waits for an explicit play action.
        ActivityScenario.launch<CoinPreviewActivity>(intent).use {
            awaitText("加载完成 · 等待播放")
        }
    }

    @Test fun loadReplaySkipBackgroundAndRecreate() = exercisePreview(round2 = false)

    @Test fun candidateLoadReplaySkipBackgroundAndRecreate() = exercisePreview(round2 = true)
}
