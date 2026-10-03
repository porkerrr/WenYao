package com.peterpork.liuyao.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.peterpork.liuyao.data.AnalysisPayload
import com.peterpork.liuyao.data.AnalysisRevisionEntity
import com.peterpork.liuyao.data.AnalysisErrorCode

internal object AnalysisMotion {
    const val StateMillis = 200
    const val ExpandMillis = 220
}

/** Initial/restored content is already readable. Only a different real state earns a transition. */
internal class AnalysisChange(initial: Any?) {
    private var previous = initial
    fun next(value: Any?): InkReveal? {
        if (previous == value) return null
        previous = value
        return InkReveal()
    }
}

@Composable
internal fun rememberAnalysisMotionActive(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return rememberDecorativeMotionEnabled() && state == Lifecycle.State.RESUMED
}

/** No duplicate outgoing subtree, delayed callback, or content-dependent animation identity. */
@Composable
internal fun AnalysisStateFade(recordId: String, stateKey: Any?, content: @Composable () -> Unit) {
    val changes = remember(recordId) { AnalysisChange(stateKey) }
    val reveal = remember(recordId, stateKey) { changes.next(stateKey) }
    val frame = rememberInkReveal(reveal, true, AnalysisMotion.StateMillis)
    // Keep incoming content readable immediately; retry/configuration actions use a separate layer.
    Box(Modifier.graphicsLayer { alpha = .4f + .6f * frame.fraction }) { content() }
}

@Composable
internal fun AnalysisWaiting() {
    val active = rememberAnalysisMotionActive()
    Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
        if (active) CircularProgressIndicator(Modifier.size(WenYaoLayout.ProgressSize),
            color = LocalContentColor.current, strokeWidth = WenYaoShapeTokens.FocusOutlineWidth)
        else Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, modifier = Modifier.size(WenYaoLayout.ProgressSize))
        Text("正在生成 AI 解读…", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Reset the measured animation node on pause, including a pause/resume between compositions. */
@Composable
internal fun AnalysisSizedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val active = rememberAnalysisMotionActive()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var paused by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) paused++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    key(active, paused) {
        WenYaoSurfaceCard(modifier.then(if (active) Modifier.animateContentSize(tween(AnalysisMotion.ExpandMillis)) else Modifier), content)
    }
}

@Composable
internal fun AnalysisReading(recordId: String, analysis: AnalysisPayload, expandedValue: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null) {
    // Default open preserves the previous full-text presentation; only this long section folds.
    var localExpanded by rememberSaveable(recordId) { mutableStateOf(true) }
    val expanded = expandedValue ?: localExpanded
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm)) {
            WenYaoSection("核心判断", analysis.summary)
            WenYaoSection("趋势", analysis.trend)
            WenYaoSection("关键因素", analysis.keyFactors.joinToString("\n• ", prefix = "• ") { "${it.title}：${it.explanation}" })
            AnalysisSizedCard(Modifier.fillMaxWidth()) {
                TextButton(onClick = { if (onExpandedChange != null) onExpandedChange(!expanded) else localExpanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics {
                    stateDescription = if (expanded) "已展开" else "已收起"
                }) { Text(if (expanded) "收起详细分析" else "展开详细分析") }
                if (expanded) Text(analysis.analysis, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WenYaoSection("建议", analysis.suggestion)
            WenYaoSurfaceCard {
                Text(analysis.limitationsNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (analysis.safetyNote.isNotBlank() && analysis.safetyNote != analysis.limitationsNote) {
                    Text(analysis.safetyNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

/** Shared by freshly saved results and history through the existing ResultScreen route. */
@Composable
internal fun AnalysisRegion(recordId: String, generation: AnalysisGenerationState, analysis: AnalysisPayload?,
    latestAttempt: AnalysisRevisionEntity?, onConfigure: () -> Unit, onRetry: () -> Unit,
    expanded: Boolean? = null, onExpandedChange: ((Boolean) -> Unit)? = null) {
    AnalysisStateFade(recordId, generation) {
        when (generation) {
            AnalysisGenerationState.Running -> WenYaoSurfaceCard(Modifier.fillMaxWidth()) { AnalysisWaiting() }
            AnalysisGenerationState.NotConfigured -> WenYaoSurfaceCard(Modifier.fillMaxWidth()) {
                Text("尚未配置当前 AI 供应商的 API Key。请前往设置保存。", style = MaterialTheme.typography.bodyMedium)
            }
            is AnalysisGenerationState.Failed -> WenYaoErrorBanner("最新一次解读失败：${analysisErrorText(generation.errorCode)}")
            else -> Unit
        }
    }
    if (generation == AnalysisGenerationState.NotConfigured) {
        WenYaoPrimaryButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) { Text("配置 AI") }
    }
    latestAttempt?.takeIf { it.status == "FAILED" && generation !is AnalysisGenerationState.Failed }?.let {
        WenYaoErrorBanner("最新一次解读失败：${it.errorCode?.let { code -> runCatching { analysisErrorText(AnalysisErrorCode.valueOf(code)) }.getOrNull() } ?: "未知错误"}")
    }
    // The latest successful body remains in place through retry/failure. Text itself is not a key.
    var successIdentity by remember(recordId) { mutableStateOf(latestAttempt?.takeIf { it.status == "SUCCESS" }?.id) }
    val incomingSuccess = latestAttempt?.takeIf { it.status == "SUCCESS" }?.id
    SideEffect { if (incomingSuccess != null) successIdentity = incomingSuccess }
    AnalysisStateFade(recordId, incomingSuccess ?: successIdentity) {
        analysis?.let { AnalysisReading(recordId, it, expanded, onExpandedChange) }
    }
    if (generation !is AnalysisGenerationState.Running && generation !is AnalysisGenerationState.NotConfigured) {
        WenYaoOutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth(), pressFeedback = true) {
            Text(if (analysis == null) "生成 AI 解读" else "重新解读（可能再次产生费用）")
        }
    }
}
