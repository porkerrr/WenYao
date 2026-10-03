package com.peterpork.liuyao.ui

import android.animation.ValueAnimator
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue

/** Real native NavHost with synthetic destinations, no DAO/network or second page state machine. */
class NavigationMotionHostTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController
    private var entries = 0
    private val rtl = mutableStateOf(false)

    private fun fixture() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            WenYaoTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    nav = rememberNavController()
                    WenYaoNavHost(nav, "home") {
                        for (route in listOf("home", "history", "casting", "result/{id}", "settings")) {
                            composable(route) {
                                LaunchedEffect(Unit) { entries++ }
                                var count by rememberSaveable { mutableIntStateOf(0) }
                                Box(Modifier.fillMaxSize().background(Paper).testTag(route)) {
                                    Text("$route:$count", Modifier.padding(start = 60.dp, top = 60.dp).testTag("marker-$route"))
                                    WenYaoPrimaryButton({ count++ }, Modifier.padding(top = 120.dp).testTag("edit-$route")) { Text("edit") }
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(32)
    }
    private fun settle() { rule.mainClock.advanceTimeBy(320); rule.waitForIdle() }
    private fun go(route: String) { rule.runOnUiThread { nav.navigate(route) }; rule.mainClock.advanceTimeBy(32) }

    @Test fun fixedDistanceForwardBackAndRtlKeepFinalGeometry() {
        assumeTrue(ValueAnimator.areAnimatorsEnabled())
        fixture()
        val homeLeft = rule.onNodeWithTag("marker-home").fetchSemanticsNode().boundsInRoot.left
        go("casting")
        val incoming = rule.onNodeWithTag("marker-casting").fetchSemanticsNode().boundsInRoot.left
        assertTrue("LTR enters from logical end", incoming > homeLeft)
        assertTrue("Never a whole-screen slide", incoming - homeLeft <= with(rule.density) { 16.dp.toPx() } + 1)
        // Both entries coexist during the same native transition.
        rule.onNodeWithTag("home").assertExists()
        settle()
        assertEquals(homeLeft, rule.onNodeWithTag("marker-casting").fetchSemanticsNode().boundsInRoot.left, 1f)
        rule.runOnUiThread { nav.popBackStack() }
        rule.mainClock.advanceTimeBy(32)
        assertTrue(rule.onNodeWithTag("marker-home").fetchSemanticsNode().boundsInRoot.left < homeLeft)
        settle()
        rule.runOnUiThread { rtl.value = true }
        settle()
        val rtlLeft = rule.onNodeWithTag("marker-home").fetchSemanticsNode().boundsInRoot.left
        go("casting")
        // Compare the same marker after settling because text widths differ between routes in RTL.
        val rtlIncoming = rule.onNodeWithTag("marker-casting").fetchSemanticsNode().boundsInRoot.left
        settle()
        val rtlFinal = rule.onNodeWithTag("marker-casting").fetchSemanticsNode().boundsInRoot.left
        assertTrue(rtlIncoming < rtlFinal)
        assertTrue(rtlFinal - rtlIncoming <= with(rule.density) { 16.dp.toPx() } + 1)
        assertTrue(rtlLeft > homeLeft)
    }

    @Test fun peersDoNotSlideAndNativeBackStackRetainsEntryState() {
        fixture()
        rule.onNodeWithTag("edit-home").performClick()
        go("history")
        val during = rule.onNodeWithTag("marker-history").fetchSemanticsNode().boundsInRoot.left
        settle()
        assertEquals(during, rule.onNodeWithTag("marker-history").fetchSemanticsNode().boundsInRoot.left, 0f)
        go("result/sample")
        settle()
        rule.runOnUiThread { assertEquals("sample", nav.currentBackStackEntry?.arguments?.getString("id")); nav.popBackStack() }
        settle()
        rule.runOnUiThread { assertEquals("history", nav.currentDestination?.route); nav.popBackStack() }
        settle()
        rule.onNodeWithText("home:1").assertExists()
        rule.runOnUiThread { assertNull(nav.previousBackStackEntry) }
    }

    @Test fun immediateBackAndRecompositionDoNotDuplicateNavigationOrEntryEffects() {
        fixture()
        go("casting")
        rule.runOnUiThread { nav.popBackStack() }
        settle()
        rule.onNodeWithText("home:0").assertExists()
        assertEquals(2, entries)
        rule.runOnUiThread { rtl.value = true }
        settle()
        assertEquals(2, entries)
        rule.runOnUiThread { assertEquals("home", nav.currentDestination?.route); assertNull(nav.previousBackStackEntry) }
    }

    @Test fun zeroScaleCompletesWithoutWaitingForAnimationDuration() {
        assumeTrue("Run with animator_duration_scale=0", !ValueAnimator.areAnimatorsEnabled())
        fixture()
        go("casting")
        rule.onNodeWithTag("casting").assertIsDisplayed()
        // The native host disposes outgoing entries on subsequent composition frames even for None.
        // 96ms remains below every nonzero transition duration; this cannot pass by waiting it out.
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithTag("home").assertDoesNotExist()
        rule.runOnUiThread { assertEquals("casting", nav.currentDestination?.route); nav.popBackStack() }
        rule.mainClock.advanceTimeBy(96)
        rule.onNodeWithTag("home").assertIsDisplayed()
        rule.onNodeWithTag("casting").assertDoesNotExist()
        rule.runOnUiThread { assertNull(nav.previousBackStackEntry) }
    }
}
