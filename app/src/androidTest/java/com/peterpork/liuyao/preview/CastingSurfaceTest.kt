package com.peterpork.liuyao.preview

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sceneview.SceneView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Checks composed screen pixels, not only the off-screen readiness buffer. No real records/keys. */
@RunWith(AndroidJUnit4::class)
class CastingSurfaceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val output get() = instrumentation.targetContext.getExternalFilesDir(null)!!
    private fun save(bitmap: Bitmap, name: String) {
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun bronze(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Int {
        var count = 0
        for (y in top.coerceAtLeast(0) until (top + height).coerceAtMost(bitmap.height) step 2)
            for (x in left.coerceAtLeast(0) until (left + width).coerceAtMost(bitmap.width) step 2) {
                val p = bitmap.getPixel(x, y)
                val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                if (Color.alpha(p) > 100 && r in 61..230 && r > g * 1.05 && g > b * 1.2) count++
            }
        return count
    }
    @Test fun coinsReachCompositedScreenAfterPreparationAndReplay() {
        ActivityScenario.launch<CastingVerificationActivity>(Intent(ApplicationProvider.getApplicationContext(), CastingVerificationActivity::class.java)
            .putExtra("fixedResults", "233")).use { scenario ->
            compose.waitUntil(30_000) { compose.onAllNodesWithText("开始起卦").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("内部渲染回归验收")
            scenario.onActivity {
                (it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(it.window.decorView.windowToken, 0)
                it.currentFocus?.clearFocus()
            }
            compose.onNodeWithText("开始起卦").performClick()
            compose.waitUntil(90_000) {
                try { compose.onNodeWithText("掷铜钱").assertIsEnabled(); true } catch (_: AssertionError) { false }
            }
            compose.onNodeWithText("掷铜钱").performClick()
            repeat(2) { run ->
                compose.waitUntil(30_000) {
                    var ended = false
                    scenario.onActivity { it.session.renderer.active!!.snapshot().let { s ->
                        ended = s.optDouble("duration") > 0 && s.optDouble("seconds") >= s.optDouble("duration")
                    } }
                    ended
                }
                SystemClock.sleep(300)
                lateinit var scene: SceneView
                val origin = IntArray(2)
                scenario.onActivity {
                    val host = it.session.renderer.active!!
                    scene = host.container.getChildAt(0) as SceneView
                    scene.getLocationOnScreen(origin)
                    File(output, "surface-$run.json").writeText(host.snapshot().toString(2))
                    scene.engine.flushAndWait()
                    val culling = scene.cameraNode.camera.getCullingProjectionMatrix(DoubleArray(16))
                    // The far plane normal is row 4 - row 3. It must not degenerate to zero.
                    val farNormal = (0..2).sumOf { column ->
                        val n = culling[column * 4 + 3] - culling[column * 4 + 2]; n * n
                    }
                    assertTrue("Finite culling far plane is required", farNormal > 1e-12)
                    assertEquals(1, it.session.castCalls)
                    assertEquals(0, it.session.dao.saveCalls)
                    assertEquals(0, it.session.analysisCalls)
                }
                SystemClock.sleep(300)
                val buffer = Bitmap.createBitmap(scene.width, scene.height, Bitmap.Config.ARGB_8888)
                val latch = CountDownLatch(1)
                var result = -1
                instrumentation.runOnMainSync {
                    PixelCopy.request(scene, buffer, { result = it; latch.countDown() }, Handler(Looper.getMainLooper()))
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS)); assertEquals(PixelCopy.SUCCESS, result)
                save(buffer, "surface-$run-buffer")
                val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                save(screen, "surface-$run-screen")
                val bufferCoins = bronze(buffer, 0, 0, buffer.width, buffer.height)
                val screenCoins = bronze(screen, origin[0], origin[1], scene.width, scene.height)
                File(output, "surface-$run-pixels.txt").writeText("buffer=$bufferCoins screen=$screenCoins")
                buffer.recycle(); screen.recycle()
                assertTrue("Coins missing from Surface buffer: $bufferCoins", bufferCoins > 100)
                assertTrue("Coins missing from composed screen: $screenCoins (buffer=$bufferCoins)", screenCoins > 100)
                if (run == 0) compose.onNodeWithText("重播").performScrollTo().performClick()
            }
        }
    }
}
