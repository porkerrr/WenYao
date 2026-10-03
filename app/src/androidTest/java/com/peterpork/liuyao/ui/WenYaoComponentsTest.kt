package com.peterpork.liuyao.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.peterpork.liuyao.domain.LineValue
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic UI only: no production ViewModel, database, credentials, network or settings writes. */
class WenYaoComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun paperBackdropHasSubtleVisibleTextureWithoutTransparentGaps() {
        rule.setContent { WenYaoTheme { PaperBackdrop {} } }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val colors = buildSet {
            for (x in 1..8) for (y in 1..8) {
                val pixel = bitmap.getPixel(bitmap.width * x / 9, bitmap.height * y / 9)
                assertEquals(255, android.graphics.Color.alpha(pixel))
                add(pixel)
            }
        }
        assertTrue("Paper fibers and grain should be visible across the surface", colors.size > 2)
    }

    @Test fun largeFontLongActionGrowsAndDisabledActionDoesNotDispatch() {
        var clicks = 0
        var overflow = true
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                WenYaoTheme {
                    Column(Modifier.width(220.dp)) {
                        WenYaoPrimaryButton(onClick = { clicks++ }, modifier = Modifier.testTag("enabled")) {
                            Text("重新解读（可能再次产生费用）", onTextLayout = { overflow = it.hasVisualOverflow })
                        }
                        WenYaoOutlinedButton(onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("disabled")) { Text("测试连接") }
                    }
                }
            }
        }
        rule.onNodeWithTag("enabled").assertHeightIsAtLeast(52.dp).performClick()
        rule.onNodeWithTag("disabled").assertIsNotEnabled().performClick()
        rule.runOnIdle { assertEquals(1, clicks); assertFalse("Large-font action clipped", overflow) }
    }

    @Test fun questionCounterTracksEditingWithoutOverlappingTheInput() {
        rule.setContent {
            WenYaoTheme {
                var question by remember { mutableStateOf("") }
                WenYaoQuestionField(question, { if (it.length <= 200) question = it }, "请输入 2～200 个字符的问题")
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("示例问题")
        rule.onNodeWithText("4/200", useUnmergedTree = true).assertExists()
        rule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "请输入 2～200 个字符的问题"))
        rule.onNode(hasSetTextAction()).performTextReplacement("字".repeat(201))
        rule.onNodeWithText("4/200", useUnmergedTree = true).assertExists()
    }

    @Test fun largeFontDivinationFixtureRendersAllLineValues() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                WenYaoTheme {
                    Surface {
                        Column(Modifier.width(360.dp).verticalScroll(rememberScrollState()).padding(WenYaoSpacing.Lg),
                            verticalArrangement = Arrangement.spacedBy(WenYaoSpacing.Md)) {
                            Text("组件检查 · 合成示例", style = MaterialTheme.typography.headlineSmall)
                            WenYaoSurfaceCard {
                                Text("本卦与变卦", style = MaterialTheme.typography.titleLarge)
                                listOf(6, 7, 8, 9, 7, 8).map(LineValue::from).asReversed().forEachIndexed { i, line ->
                                    LineRow(6 - i, line, changedIsYang = !line.isYang)
                                }
                            }
                            WenYaoOutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("重新解读（可能再次产生费用）") }
                            WenYaoErrorBanner("合成错误示例：请检查连接后重试")
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("9 老阳").assertIsDisplayed()
        rule.onNodeWithText("6 老阴").assertIsDisplayed()
        rule.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-review")
        directory.mkdirs()
        File(directory, "components-font-1.5.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun castingProgressRetainsSixPositionsBeforeAndAfterACast() {
        var add: (() -> Unit)? = null
        rule.setContent {
            WenYaoTheme {
                var lines by remember { mutableStateOf(emptyList<LineValue>()) }
                add = { lines = listOf(LineValue.OLD_YANG) }
                CastingLineProgress(lines)
            }
        }
        rule.onAllNodesWithText("待生成").assertCountEquals(6)
        rule.runOnIdle { add!!() }
        rule.onAllNodesWithText("待生成").assertCountEquals(5)
        rule.onNodeWithText("9 老阳").assertIsDisplayed()
        rule.onNodeWithText("初爻").assertIsDisplayed()
        rule.onNodeWithText("上爻").assertIsDisplayed()
    }
}
