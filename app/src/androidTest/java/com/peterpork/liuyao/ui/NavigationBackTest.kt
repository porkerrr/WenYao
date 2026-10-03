package com.peterpork.liuyao.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.peterpork.liuyao.WenYaoApplication
import com.peterpork.liuyao.domain.CoinCastResult
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real navigation stack and entry lifecycles; never opens the user's database or credentials. */
class NavigationBackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController
    private val backByEntry = mutableMapOf<String, () -> Unit>()

    private fun fixture(start: String = "home", animations: Boolean = true) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            WenYaoTheme {
                nav = rememberNavController()
                val destinations: NavGraphBuilder.() -> Unit = {
                    for (route in listOf("home", "history", "settings", "casting", "result/{id}")) {
                        composable(route) { entry ->
                            backByEntry[entry.id] = guardedPageBack(nav, entry, returnHome = route == "result/{id}")
                            Text(route)
                        }
                    }
                }
                if (animations) {
                    WenYaoNavHost(nav, start, destinations)
                } else {
                    NavHost(nav, start, enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None }, popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }, builder = destinations)
                }
            }
        }
        settle()
    }

    private fun settle() { rule.mainClock.advanceTimeBy(400); rule.waitForIdle() }
    private fun go(route: String) { rule.runOnUiThread { nav.navigate(route) }; settle() }
    private fun currentBack(): () -> Unit {
        var back: (() -> Unit)? = null
        rule.runOnUiThread {
            val entry = checkNotNull(nav.currentBackStackEntry)
            assertEquals(Lifecycle.State.RESUMED, entry.lifecycle.currentState)
            back = backByEntry.getValue(entry.id)
        }
        return checkNotNull(back)
    }
    private fun assertHome() {
        rule.onNodeWithText("home").assertIsDisplayed()
        rule.runOnUiThread { assertEquals("home", nav.currentDestination?.route); assertNull(nav.previousBackStackEntry) }
    }

    @Test fun sameFrameTripleBackPreservesHomeForEveryPopPage() {
        fixture()
        for (route in listOf("history", "settings", "casting")) {
            go(route)
            val back = currentBack()
            rule.runOnUiThread { repeat(3) { back() } }
            settle()
            assertHome()
        }
    }

    @Test fun delayedClickDuringExitCannotPopHome() {
        fixture()
        go("history")
        val back = currentBack()
        rule.runOnUiThread { back() }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnUiThread { repeat(2) { back() } }
        settle()
        assertHome()
    }

    @Test fun settingBackRetainsExactResultEntryAndDoesNotSkipIt() {
        fixture()
        go("history")
        go("result/sample")
        val result = nav.currentBackStackEntry
        go("settings")
        val back = currentBack()
        rule.runOnUiThread { repeat(3) { back() } }
        settle()
        rule.onNodeWithText("result/{id}").assertIsDisplayed()
        rule.runOnUiThread {
            assertSame(result, nav.currentBackStackEntry)
            assertEquals("history", nav.previousBackStackEntry?.destination?.route)
        }
    }

    @Test fun oldCallbackCannotPopNewInstanceOfSameRoute() {
        fixture()
        go("settings")
        val oldBack = currentBack()
        rule.runOnUiThread { oldBack() }
        settle()
        go("settings")
        val newEntry = nav.currentBackStackEntry
        rule.runOnUiThread { oldBack(); assertSame(newEntry, nav.currentBackStackEntry) }
        val newBack = currentBack()
        rule.runOnUiThread { newBack() }
        settle()
        assertHome()
    }

    @Test fun resultToolbarReplacesHomeOnlyOnce() {
        fixture()
        go("history")
        go("result/sample")
        val back = currentBack()
        rule.runOnUiThread {
            back()
            val home = nav.currentBackStackEntry
            repeat(2) { back() }
            assertSame(home, nav.currentBackStackEntry)
        }
        settle()
        assertHome()
    }

    @Test fun rootToolbarDoesNotRemoveStartDestination() {
        fixture()
        val back = currentBack()
        rule.runOnUiThread { repeat(3) { back() } }
        settle()
        assertHome()
    }

    @Test fun orphanPageRecoversToHomeInsteadOfPoppingEmptyStack() {
        fixture(start = "settings")
        val back = currentBack()
        rule.runOnUiThread { repeat(3) { back() } }
        settle()
        assertHome()
    }

    @Test fun noAnimationStillRejectsRepeatedBack() {
        fixture(animations = false)
        go("settings")
        val back = currentBack()
        rule.runOnUiThread { repeat(3) { back() } }
        settle()
        assertHome()
    }

    @Test fun castingPreparationFailureBottomBackIgnoresTripleClick() {
        check(isMotionTestPackage(rule.activity.packageName))
        val app = rule.activity.application as WenYaoApplication
        val entrance = HomeEntranceSession(restored = true, normalLaunch = false, visible = true)
        val failure = object : CastPresentation {
            @Composable
            override fun Content(session: Instant?, cast: CoinCastResult?, state: RevealState, attempt: Int,
                onStateChange: (RevealState) -> Unit, onPreparation: (PreparationState) -> Unit, modifier: Modifier) {
                LaunchedEffect(session) { onPreparation(PreparationState.Failed) }
            }
        }
        rule.setContent {
            WenYaoTheme {
                WenYaoApp(app.database.dao(), app.analysisRepository, app.credentialStore,
                    app.settingsRepository, entrance, presentation = failure)
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("导航验收：准备失败后返回")
        rule.onNodeWithText("开始起卦").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("重试准备").assertExists()
        val click = checkNotNull(rule.onNodeWithText("返回").fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        rule.runOnUiThread { repeat(3) { click() } }
        rule.onNodeWithText("开始起卦").assertExists()
    }
}
