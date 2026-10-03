package com.peterpork.liuyao.preview

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiPolishTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(300)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        if (name.endsWith("six_bottom")) {
            val headerBottom = (90 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
            var bronzePixels = 0
            for (y in 0 until headerBottom step 4) for (x in 0 until bitmap.width step 4) {
                val p = bitmap.getPixel(x,y)
                val r = android.graphics.Color.red(p); val g = android.graphics.Color.green(p); val b = android.graphics.Color.blue(p)
                if (r in 90..235 && g in 70..210 && r > g*1.04 && g > b*1.35) bronzePixels++
            }
            assertTrue("Scrolled coins must not paint over the header: $bronzePixels", bronzePixels < 5)
        }
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
    private fun waitText(text: String) = compose.waitUntil(90_000) {
        try { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        catch (_: IllegalStateException) { false }
    }
    private fun end(s: ActivityScenario<CastingVerificationActivity>) = compose.waitUntil(45_000) {
        var ended = false
        s.onActivity { a ->
            a.session.renderer.active?.snapshot()?.let {
                ended = it.optBoolean("ready") && it.optString("castId").isNotBlank() &&
                    it.optString("castId") == a.business.castingState.value.castsBottomUp.lastOrNull()?.castId &&
                    it.optDouble("duration") > 0 && it.optDouble("seconds") >= it.optDouble("duration")
            }
        }
        ended
    }
    @Test fun captureEightStableFinals() {
        val records = org.json.JSONArray()
        val codes = InstrumentationRegistry.getArguments().getString("captureCodes")?.split(",")
        for (result in PreviewResult.entries.filter { codes == null || it.code in codes }) {
            ActivityScenario.launch<CastingVerificationActivity>(Intent(ApplicationProvider.getApplicationContext(), CastingVerificationActivity::class.java)
                .putExtra("fixedResults", result.code)).use { s ->
                waitText("开始起卦")
                compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("内部固定样片验收")
                s.onActivity {
                    (it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                        .hideSoftInputFromWindow(it.window.decorView.windowToken,0)
                    it.currentFocus?.clearFocus()
                }
                compose.onNodeWithText("开始起卦").performClick()
                compose.waitUntil(90_000) {
                    try { compose.onNodeWithText("掷铜钱").assertIsEnabled(); true } catch (_: AssertionError) { false }
                }
                compose.onNodeWithText("掷铜钱").performClick()
                end(s)
                var accepted: Bitmap? = null
                compose.waitUntil(15_000) {
                    var focused=false
                    s.onActivity {
                        focused=it.window.decorView.hasWindowFocus()
                        (it.session.renderer.active!!.container.getChildAt(0) as io.github.sceneview.SceneView).engine.flushAndWait()
                    }
                    if (!focused) false else {
                        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                        var bronze=0; var white=0; var count=0
                        for(y in bitmap.height/8 until bitmap.height*7/8 step 16) for(x in 0 until bitmap.width step 16) {
                            val p=bitmap.getPixel(x,y); val r=android.graphics.Color.red(p); val g=android.graphics.Color.green(p); val b=android.graphics.Color.blue(p)
                            count++; if(r>250 && g>250 && b>250) white++
                            if(r in 61..230 && r>g*1.05 && g>b*1.2) bronze++
                        }
                        if(bronze>15 && white<count/2) { accepted=bitmap; true }
                        else { bitmap.recycle(); false }
                    }
                }
                val name="polish_${result.code}_settled.png"
                File(instrumentation.targetContext.getExternalFilesDir(null),name).outputStream().use {
                    assertTrue(accepted!!.compress(Bitmap.CompressFormat.PNG,100,it))
                }
                accepted!!.recycle()
                s.onActivity {
                    val snapshot=it.session.renderer.active!!.snapshot()
                    assertEquals(result.code,snapshot.getString("result"))
                    records.put(snapshot.put("capture",name).put("windowFocused",true))
                }
            }
        }
        File(instrumentation.targetContext.getExternalFilesDir(null),"polish-stable-eight.json").writeText(records.toString(2))
    }
    @Test fun replayTouchScrollAndSixLinesRemainUsable() {
        val suffix = InstrumentationRegistry.getArguments().getString("captureSuffix") ?: "default"
        ActivityScenario.launch<CastingVerificationActivity>(Intent(ApplicationProvider.getApplicationContext(), CastingVerificationActivity::class.java)
            .putExtra("fixedResults", "233,222,223,232,332,333")).use { s ->
            waitText("开始起卦")
            val question = if (suffix == "large") "内部布局验收：这是一段用于检查大字号和小屏幕自然滚动的较长测试问题，不含个人内容。" else "内部固定样片验收"
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(question)
            s.onActivity {
                (it.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(it.window.decorView.windowToken, 0)
                it.currentFocus?.clearFocus()
            }
            compose.onNodeWithText("开始起卦").performClick()
            compose.waitUntil(90_000) {
                try { compose.onNodeWithText("掷铜钱").assertIsEnabled(); true } catch (_: AssertionError) { false }
            }
            val recorder = if (suffix == "default") {
                check(shell("pidof screenrecord").isBlank()) { "Another recorder is active" }
                instrumentation.uiAutomation.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 120 /sdcard/Android/data/com.peterpork.liuyao.debug/files/polish-replay.mp4")
            } else null
            var recorderPid = ""
            try {
            if (recorder != null) {
                SystemClock.sleep(300)
                recorderPid = shell("pidof screenrecord")
                check(recorderPid.matches(Regex("[0-9]+"))) { "Recorder did not start" }
            }
            compose.onNodeWithText("掷铜钱").performTouchInput { click() }
            end(s)
            var castId = ""
            var matrices = ""
            s.onActivity {
                val host = it.session.renderer.active!!
                val scene = host.container.getChildAt(0) as io.github.sceneview.SceneView
                assertNull(scene.skybox); assertNotNull(scene.indirectLight)
                assertEquals(com.google.android.filament.View.BlendMode.TRANSLUCENT, scene.view.blendMode)
                castId = host.snapshot().getString("castId")
                matrices = host.snapshot().getJSONArray("coinTransforms").toString()
            }
            capture("polish_${suffix}_natural")
            if (suffix == "large") {
                var stageY = 0; var x = 0f; var startY = 0f
                s.onActivity {
                    val host = it.session.renderer.active!!
                    stageY = IntArray(2).also(host.container::getLocationOnScreen)[1]
                    val surface = host.container.getChildAt(0)
                    val origin = IntArray(2).also(surface::getLocationOnScreen)
                    x = origin[0] + surface.width / 2f
                    startY = origin[1] + surface.height * .75f
                }
                val down = SystemClock.uptimeMillis()
                val distance = 160f * instrumentation.targetContext.resources.displayMetrics.density
                for (step in 0..12) {
                    val action = when(step) { 0 -> android.view.MotionEvent.ACTION_DOWN; 12 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
                    val event = android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,startY-distance*step/12,0)
                    instrumentation.sendPointerSync(event); event.recycle(); SystemClock.sleep(16)
                }
                compose.waitForIdle()
                compose.waitUntil(10_000) {
                    var moved = false
                    s.onActivity { moved = IntArray(2).also(it.session.renderer.active!!.container::getLocationOnScreen)[1] < stageY - 40 }
                    moved
                }
                s.onActivity { assertEquals(8,it.session.renderer.active!!.snapshot().getInt("assetReads")); assertEquals(1,it.session.castCalls) }
            }
            val replay = compose.onNodeWithText("重播").performScrollTo()
            val bounds = replay.fetchSemanticsNode().boundsInRoot
            val density = instrumentation.targetContext.resources.displayMetrics.density
            assertTrue(bounds.height / density >= 48f)
            assertTrue(bounds.width / density >= 48f)
            replay.performTouchInput { click() }
            waitText("跳过动画"); end(s)
            s.onActivity {
                val snapshot = it.session.renderer.active!!.snapshot()
                assertEquals(castId, snapshot.getString("castId"))
                assertEquals(matrices, snapshot.getJSONArray("coinTransforms").toString())
                assertEquals(1, it.session.castCalls); assertEquals(0, it.session.dao.saveCalls); assertEquals(0, it.session.analysisCalls)
            }
            capture("polish_${suffix}_replay")
            repeat(5) { index ->
                compose.onNodeWithText("掷铜钱").performTouchInput { click() }
                compose.waitUntil(15_000) { var count=0; s.onActivity { count=it.session.castCalls }; count==index+2 }
                end(s)
            }
            compose.onNodeWithText("初爻").performScrollTo().assertIsDisplayed()
            val last = compose.onNodeWithText("初爻").fetchSemanticsNode().boundsInRoot
            val action = compose.onNodeWithText("生成卦象").fetchSemanticsNode().boundsInRoot
            assertTrue("Last line must scroll fully above action", last.bottom < action.top)
            s.onActivity {
                File(instrumentation.targetContext.getExternalFilesDir(null),"polish_${suffix}_scrolled.json")
                    .writeText(it.session.renderer.active!!.snapshot().toString(2))
            }
            capture("polish_${suffix}_six_bottom")
            s.onActivity { assertEquals(6,it.session.castCalls); assertEquals(0,it.session.dao.saveCalls); assertEquals(0,it.session.analysisCalls) }
            compose.onNodeWithText("生成卦象").performTouchInput { click() }
            waitText("卦象结果")
            s.onActivity { assertEquals(1,it.session.dao.saveCalls); assertEquals(0,it.session.analysisCalls) }
            } finally {
                s.onActivity {
                    File(instrumentation.targetContext.getExternalFilesDir(null), "polish_${suffix}_state.json").writeText(
                        org.json.JSONObject().put("casts",it.session.castCalls).put("saves",it.session.dao.saveCalls)
                            .put("requests",it.session.analysisCalls).put("renderer",it.session.renderer.active?.snapshot()).toString(2))
                }
                if (recorderPid.matches(Regex("[0-9]+"))) shell("kill -2 $recorderPid")
                if (recorder != null) {
                    val deadline = SystemClock.elapsedRealtime() + 10_000
                    while (shell("pidof screenrecord").isNotBlank() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
                    check(shell("pidof screenrecord").isBlank()) { "Recorder did not finalize" }
                    shell("chmod 644 /sdcard/Android/data/com.peterpork.liuyao.debug/files/polish-replay.mp4")
                }
                recorder?.close()
                if (recorder != null) SystemClock.sleep(500)
            }
        }
    }
}
