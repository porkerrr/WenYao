package com.peterpork.liuyao.preview

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.peterpork.liuyao.DebugWenYaoApplication
import com.peterpork.liuyao.MainActivity
import com.peterpork.liuyao.ui.WenYaoViewModel
import com.peterpork.liuyao.domain.LiuyaoEngine
import com.peterpork.liuyao.ui.hexagramDescription
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CastingIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val output get() = checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
    private fun launch(codes: String, enabled: Boolean = true) = ActivityScenario.launch<CastingVerificationActivity>(
        Intent(ApplicationProvider.getApplicationContext(), CastingVerificationActivity::class.java)
            .putExtra("fixedResults", codes).putExtra("cast3d", enabled))
    private fun waitText(text: String) = compose.waitUntil(45_000) {
        try { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        catch (e: IllegalStateException) { if (e.message?.startsWith("No compose hierarchies found") == true) false else throw e }
    }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (text == "重播" || text == "跳过动画") node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }; compose.waitForIdle()
    }
    private fun <T : androidx.activity.ComponentActivity> begin(s: ActivityScenario<T>, awaitPreparation: Boolean = true) {
        waitText("开始起卦")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("内部固定样片验收")
        s.onActivity { a ->
            (a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(a.window.decorView.windowToken, 0)
            a.currentFocus?.clearFocus()
        }
        click("开始起卦")
        s.onActivity { if (it is CastingVerificationActivity) assertNotNull("Start action must create session", it.business.castingState.value.castAt) }
        if (awaitPreparation) compose.waitUntil(90_000) {
            try { compose.onNodeWithText("掷铜钱").assertIsEnabled(); true }
            catch (_: AssertionError) { false }
        }
    }
    private fun waitReady(s: ActivityScenario<CastingVerificationActivity>) = compose.waitUntil(90_000) {
        var ready = false
        s.onActivity {
            val snap = it.session.renderer.active?.snapshot()
            val expected = it.business.castingState.value.castsBottomUp.lastOrNull()?.castId
            ready = snap?.optBoolean("ready") == true && (expected == null || snap.optString("castId") == expected)
        }
        ready
    }
    private fun waitEnd() = compose.waitUntil(45_000) {
        compose.onAllNodesWithText("跳过动画").fetchSemanticsNodes().isEmpty()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(300) // Let the already-asserted UI state reach a physical window frame.
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
    private fun final(s: ActivityScenario<CastingVerificationActivity>, code: String): JSONObject {
        var snapshot = JSONObject()
        compose.waitUntil(15000) {
            var stable = false
            s.onActivity { val v = it.session.renderer.active!!.snapshot(); stable = v.getDouble("seconds") >= v.getDouble("settledSourceSeconds") }
            stable
        }
        s.onActivity { a ->
            val cast = a.business.castingState.value.castsBottomUp.last()
            assertEquals(code, cast.coins.joinToString("") { it.points.toString() })
            assertEquals(code.sumOf { it.digitToInt() }, cast.total)
            snapshot = checkNotNull(a.session.renderer.active).snapshot()
            assertTrue(snapshot.getBoolean("ready"))
            assertEquals(8, snapshot.getInt("poolSize"))
            assertEquals(8, snapshot.getInt("assetReads"))
            assertEquals(8, snapshot.getInt("modelCreates"))
            assertEquals(1, snapshot.getInt("sceneCount"))
            assertTrue(snapshot.getDouble("seconds") >= snapshot.getDouble("settledSourceSeconds") - .375)
            val matrices = snapshot.getJSONArray("coinTransforms")
            val manifest = a.assets.open("coin_preview_eight/$code/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
            for (i in 0..2) {
                val m = matrices.getJSONArray(i)
                assertEquals(code[i].digitToInt(), if (m.getDouble(5) > 0) 3 else 2)
                assertTrue(kotlin.math.abs(m.getDouble(5)) > .98)
                for (axis in 0..2) assertEquals(manifest.getJSONArray("finalTranslations").getJSONArray(i).getDouble(axis), m.getDouble(12 + axis), .0001)
            }
            assertEquals(0, a.session.analysisCalls)
        }
        return snapshot
    }

    @Test fun allEightFrozenResultsReachRealPageAndHold() {
        val results = JSONArray()
        for (result in PreviewResult.entries) launch(result.code).use { s ->
            begin(s); click("掷铜钱"); waitReady(s)
            capture("casting_${result.code}_flight")
            s.moveToState(Lifecycle.State.CREATED)
            var paused = 0.0
            s.onActivity { paused = it.session.renderer.active!!.snapshot().getDouble("seconds") }
            SystemClock.sleep(250)
            s.onActivity { assertEquals(paused, it.session.renderer.active!!.snapshot().getDouble("seconds"), .0001) }
            s.moveToState(Lifecycle.State.RESUMED)
            waitEnd()
            val ended = final(s, result.code)
            assertEquals(1.25, ended.getDouble("playbackSpeed"), .000001)
            assertEquals(1, ended.getInt("revealCount"))
            val latency = ended.getDouble("revealedAt") - ended.getDouble("stableExpectedAt")
            assertTrue("Result too early: $latency", latency >= -.300001)
            assertTrue("Result too late: $latency", latency <= -.300 + ended.getDouble("revealFrameDelta") + .00001)
            assertTrue(ended.getDouble("revealSourceSeconds") < ended.getDouble("duration"))
            capture("casting_${result.code}_final")
            compose.waitUntil(15_000) {
                var complete = false
                s.onActivity { val snap = it.session.renderer.active!!.snapshot(); complete = snap.getDouble("seconds") >= snap.getDouble("duration") }
                complete
            }
            val held = final(s, result.code)
            click("重播"); waitText("跳过动画"); waitEnd(); final(s, result.code)
            click("重播"); waitText("跳过动画"); click("跳过动画"); waitEnd()
            assertEquals(held.getJSONArray("coinTransforms").toString(), final(s, result.code).getJSONArray("coinTransforms").toString())
            s.recreate(); waitReady(s)
            final(s, result.code)
            s.onActivity { assertEquals(1, it.session.castCalls); assertEquals(1, it.business.castingState.value.castsBottomUp.size) }
            results.put(ended)
            File(output, "casting-eight.json").writeText(JSONObject().put("model", Build.MODEL).put("device", Build.DEVICE)
                .put("results", results).put("renderFps", "not measured").toString(2))
        }
    }

    @Test fun sixNaturalCastsSaveExactlyOnceAndShowCorrectResult() {
        val codes = listOf("222", "223", "232", "233", "332", "333")
        launch(codes.joinToString(",")).use { s ->
            begin(s)
            val snapshots = JSONArray()
            codes.forEachIndexed { i, code ->
                click("掷铜钱"); waitReady(s)
                if (i == 5) compose.onNodeWithText("生成卦象").assertIsNotEnabled()
                waitEnd()
                snapshots.put(final(s, code))
                s.onActivity { assertEquals(i + 1, it.session.castCalls); assertEquals(i + 1, it.business.castingState.value.castsBottomUp.size) }
                capture("casting_six_${i + 1}")
                SystemClock.sleep(450)
            }
            click("生成卦象"); waitText("卦象结果")
            var expected = ""
            var expectedDescription = ""
            s.onActivity { a ->
                val state = a.business.castingState.value
                assertEquals(codes, state.castsBottomUp.map { c -> c.coins.joinToString("") { it.points.toString() } })
                val computed = LiuyaoEngine.calculate(state.linesBottomUp)
                expected = "${computed.original.displayName}  →  ${computed.changed.displayName}"
                expectedDescription = hexagramDescription(computed.original.displayName, computed.changed.displayName, state.linesBottomUp)
                assertEquals(1, a.session.dao.saveCalls); assertEquals(6, a.session.castCalls)
                assertEquals(0, a.session.analysisCalls); assertNotNull(state.completedRecordId)
                val saved = kotlinx.coroutines.runBlocking { a.session.dao.get(state.completedRecordId!!) }!!
                assertEquals(codes.map { c -> c.sumOf { it.digitToInt() } }.joinToString(","), saved.lineValues)
                a.business.finish { fail("Repeated save navigated") }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(expectedDescription).fetchSemanticsNodes().isNotEmpty() }
            capture("casting_six_result")
            File(output, "casting-six.json").writeText(JSONObject().put("codes", JSONArray(codes)).put("snapshots", snapshots)
                .put("exactlySix", true).put("saves", 1).put("aiRequests", 0).put("resultTitle", expected).toString(2))
            SystemClock.sleep(1500)
        }
    }

    @Test fun preparationFailureTimeoutAndRetryNeverCast() {
        val app = ApplicationProvider.getApplicationContext<DebugWenYaoApplication>()
        assertNotNull(app.castPresentation(Intent()))
        for (fault in listOf(DebugCastingPresentation.Fault.LOAD, DebugCastingPresentation.Fault.RENDERER, DebugCastingPresentation.Fault.TIMEOUT)) {
            launch("332").use { s ->
                s.onActivity { it.session.renderer.faultFor = { fault } }
                begin(s, awaitPreparation = false)
                if (fault == DebugCastingPresentation.Fault.TIMEOUT) {
                    compose.onNodeWithText("掷铜钱").assertIsNotEnabled()
                    compose.onNodeWithText("自动完成剩余爻").assertIsNotEnabled()
                    capture("pool_preparing")
                    s.moveToState(Lifecycle.State.CREATED)
                    var before = 0L
                    s.onActivity { before = it.session.renderer.active!!.snapshot().getLong("prepareForegroundMillis") }
                    SystemClock.sleep(250)
                    s.onActivity { assertEquals(before, it.session.renderer.active!!.snapshot().getLong("prepareForegroundMillis")) }
                    s.moveToState(Lifecycle.State.RESUMED)
                }
                compose.waitUntil(90_000) { compose.onAllNodesWithText("重试准备").fetchSemanticsNodes().isNotEmpty() }
                capture("pool_failure_$fault")
                var previous: CastingSceneHost? = null
                s.onActivity {
                    previous = it.session.renderer.active
                    assertEquals(0, it.session.castCalls)
                    assertEquals(0, it.session.dao.saveCalls)
                    assertTrue(previous!!.snapshot().getBoolean("closed"))
                    assertEquals(0, previous!!.snapshot().getInt("poolSize"))
                    it.session.renderer.faultFor = { DebugCastingPresentation.Fault.NONE }
                }
                click("重试准备"); waitReady(s)
                s.onActivity {
                    assertNotSame(previous, it.session.renderer.active)
                    assertEquals(8, it.session.renderer.active!!.snapshot().getInt("poolSize"))
                    assertEquals(0, it.session.castCalls)
                }
                click("掷铜钱"); waitEnd(); final(s, "332")
                s.onActivity { assertEquals(1, it.session.castCalls) }
            }
        }
    }

    @Test fun leavingWhilePreparingCancelsPoolAndReentryStartsClean() {
        launch("222").use { s ->
          try {
            s.onActivity { it.session.renderer.loadDelayMillis = 500 }
            begin(s, awaitPreparation = false)
            compose.waitUntil(20_000) {
                var count = 0
                s.onActivity { count = it.session.renderer.active?.snapshot()?.optInt("poolSize") ?: 0 }
                count in 1..7
            }
            capture("pool_real_progress")
            var previous: CastingSceneHost? = null
            s.onActivity { previous = it.session.renderer.active }
            compose.onNodeWithContentDescription("返回").performClick()
            waitText("开始起卦")
            s.onActivity {
                assertTrue(previous!!.snapshot().getBoolean("closed"))
                assertEquals(0, previous!!.snapshot().getInt("sceneCount"))
                assertEquals(0, it.session.castCalls)
                it.session.renderer.loadDelayMillis = 0
            }
            click("开始起卦"); waitReady(s)
            s.onActivity { assertNotSame(previous, it.session.renderer.active); assertEquals(0, it.session.castCalls) }
          } catch (error: Throwable) {
              android.util.Log.e("PreloadTest", "Preparation cancellation failure before Activity cleanup", error)
              throw error
          }
        }
    }

    @Test fun destroyedRendererRequiresPreparationWithoutReroll() {
        launch("223").use { s ->
            begin(s); click("掷铜钱"); waitEnd(); final(s, "223")
            s.onActivity { a ->
                val scene = a.session.renderer.active!!.container.getChildAt(0) as io.github.sceneview.SceneView
                scene.destroy()
            }
            waitText("重试准备")
            s.onActivity { assertEquals(1, it.session.castCalls); assertEquals(0, it.session.dao.saveCalls) }
            click("重试准备"); waitReady(s); final(s, "223")
            s.onActivity { assertEquals(1, it.session.castCalls) }
        }
    }

    @Test fun earlyActionsReplaceHostWithoutLateCompletion() {
        launch("222,333").use { s ->
            begin(s); click("掷铜钱"); waitReady(s); waitEnd()
            compose.onNodeWithText("掷铜钱").assertIsEnabled()
            var previous: CastingSceneHost? = null
            var before = JSONObject()
            s.onActivity {
                previous = it.session.renderer.active
                before = previous!!.snapshot()
                assertEquals(1, before.getInt("revealCount"))
                assertTrue(before.getDouble("revealSourceSeconds") < before.getDouble("settledSourceSeconds"))
            }
            compose.onNodeWithText("掷铜钱").performTouchInput { repeat(12) { click() } }
            waitReady(s)
            s.onActivity {
                assertSame(previous, it.session.renderer.active)
                assertEquals(8, previous!!.snapshot().getInt("assetReads"))
                assertEquals(8, previous!!.snapshot().getInt("modelCreates"))
                previous!!.deliverCompletionForTest(1, "fixed-0")
                assertEquals(2, it.session.castCalls)
                assertEquals(0, it.session.dao.saveCalls)
                assertEquals(0, it.session.analysisCalls)
            }
            compose.onNodeWithText("跳过动画").assertExists()
            click("跳过动画"); waitEnd(); final(s, "333")
            File(output, "early-actions.json").writeText(before.put("nextCastCount", 2)
                .put("staleCompletionRejected", true).toString(2))
        }
    }

    @Test fun rapidTapsSkipReentryAndLateCompletionDoNotAdvance() {
        launch("222,333").use { s ->
            begin(s)
            compose.onNodeWithText("掷铜钱").performTouchInput { repeat(12) { click() } }
            waitReady(s)
            var previous: CastingSceneHost? = null
            s.onActivity { previous = it.session.renderer.active; assertEquals(1, it.session.castCalls) }
            click("跳过动画"); waitEnd(); final(s, "222")
            click("掷铜钱"); waitReady(s)
            s.onActivity { previous!!.deliverCompletionForTest(1, "fixed-0"); assertEquals(2, it.session.castCalls) }
            compose.onNodeWithText("跳过动画").assertExists()
            click("跳过动画"); waitEnd(); final(s, "333")
            compose.onNodeWithContentDescription("返回").performClick()
            waitText("开始起卦"); click("开始起卦"); waitReady(s); final(s, "333")
            s.recreate(); waitReady(s); final(s, "333")
            s.onActivity { assertEquals(2, it.session.castCalls); assertEquals(2, it.business.castingState.value.castsBottomUp.size) }
            File(output, "casting-race-checks.json").writeText(JSONObject().put("rapidTaps", 12)
                .put("casts", 2).put("staleCompletionRejected", true).put("recreateRetainsResults", true).toString(2))
        }
    }

    @Test fun actualMainActivityDefaultOn() {
        val app = ApplicationProvider.getApplicationContext<DebugWenYaoApplication>()
        val renderer = app.castPresentation(Intent().putExtra("cast3d", true)) as CastingScenePresentation
        val records = JSONArray()
        for (enabled in listOf(true)) {
            val intent = Intent(app, MainActivity::class.java)
            // Normal launch deliberately omits the legacy flag.
            ActivityScenario.launch<MainActivity>(intent).use { s ->
                begin(s); click("掷铜钱")
                if (enabled) compose.waitUntil(45_000) {
                    var ready = false
                    s.onActivity { ready = renderer.active?.snapshot()?.optBoolean("ready") == true }
                    ready
                }
                waitEnd()
                s.onActivity { a ->
                    val vm = androidx.lifecycle.ViewModelProvider(a)[WenYaoViewModel::class.java]
                    val state = vm.castingState.value
                    val cast = state.castsBottomUp.single()
                    assertNull(state.completedRecordId)
                    val row = JSONObject().put("enabled", enabled).put("casts", 1).put("saved", false)
                        .put("frozenCode", cast.coins.joinToString("") { it.points.toString() })
                    if (enabled) {
                        val snapshot = checkNotNull(renderer.active).snapshot()
                        assertTrue(snapshot.getDouble("seconds") >= snapshot.getDouble("settledSourceSeconds") - .375)
                        val matrices = snapshot.getJSONArray("coinTransforms")
                        assertEquals(cast.coins.map { it.points }, (0..2).map { if (matrices.getJSONArray(it).getDouble(5) > 0) 3 else 2 })
                        row.put("actualRenderer", snapshot)
                    } else assertNull(renderer.active)
                    records.put(row)
                }
                capture(if (enabled) "main_activity_on" else "main_activity_off")
            }
        }
        File(output, "main-activity-checks.json").writeText(records.toString(2))
    }
}
