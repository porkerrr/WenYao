package com.peterpork.liuyao.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.peterpork.liuyao.domain.LineValue

internal fun hexagramDescription(original: String, changed: String, lines: List<LineValue>): String =
    "卦象展示：本卦$original，变卦$changed，动爻：${movingLineNames(lines.mapIndexedNotNull { i, line -> if (line.isMoving) i + 1 else null })}。" +
        lines.mapIndexed { i, line -> "${lineName(i + 1)} ${line.value} ${line.label}" }.joinToString("，")

@Composable
internal fun LineRow(position: Int, line: LineValue, modifier: Modifier = Modifier, changedIsYang: Boolean? = null,
    inkProgress: () -> Float = { 1f }) {
    val color = if (line.isMoving) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth().padding(vertical = WenYaoSpacing.Sm).semantics(mergeDescendants = true) {
        stateDescription = "${if (line.isYang) "阳爻" else "阴爻"}，${if (line.isMoving) "动爻" else "静爻"}"
    },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm)) {
        Text(lineName(position), Modifier.widthIn(min = WenYaoLayout.LineLabelWidth), style = MaterialTheme.typography.labelMedium, color = color)
        DivinationGlyph(line.isYang, line.isMoving, Modifier.weight(1f), color, inkProgress)
        if (changedIsYang != null) DivinationGlyph(changedIsYang, line.isMoving, Modifier.weight(1f), color, inkProgress)
        Text("${line.value} ${line.label}", Modifier.widthIn(min = WenYaoLayout.LineValueWidth),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = color, textAlign = TextAlign.End)
    }
}

@Composable
internal fun DivinationGlyph(isYang: Boolean, isMoving: Boolean, modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = if (isMoving) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
    inkProgress: () -> Float = { 1f }) {
    Row(modifier.height(WenYaoSpacing.Xs), horizontalArrangement = Arrangement.spacedBy(WenYaoSpacing.Md)) {
        // Each yin segment has its own reveal; the central gap is never painted over.
        val segment = Modifier.weight(1f).fillMaxHeight().drawWithContent {
            clipRect(right = size.width * inkProgress().coerceIn(0f, 1f)) { this@drawWithContent.drawContent() }
        }.background(color, RoundedCornerShape(WenYaoShapeTokens.DivinationLine))
        Box(segment)
        if (!isYang) Box(segment)
    }
}

/** Empty positions remain visible; domain values are never inferred from animation state. */
@Composable
internal fun CastingLineProgress(lines: List<LineValue>, newPosition: Int? = null, inkProgress: () -> Float = { 1f }) {
    WenYaoSurfaceCard(Modifier.fillMaxWidth()) {
      Column {
        (6 downTo 1).forEach { position ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm)) {
                if (lineIsMoving(lines, position)) {
                    val marker = MaterialTheme.colorScheme.tertiary
                    Canvas(Modifier.size(WenYaoSpacing.Sm)) { drawCircle(marker) }
                } else Spacer(Modifier.size(WenYaoSpacing.Sm))
                val line = lines.getOrNull(position - 1)
                if (line != null) LineRow(position, line, inkProgress = { if (position == newPosition) inkProgress() else 1f })
                else Row(Modifier.fillMaxWidth().padding(vertical = WenYaoSpacing.Sm), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(lineName(position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("待生成", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
      }
    }
}

internal fun lineIsMoving(lines: List<LineValue>, position: Int): Boolean =
    lines.getOrNull(position - 1)?.isMoving == true

@Composable
internal fun EmptyCoinStage(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("准备掷铜钱", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun HistoryEmptyState() {
    val color = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().padding(vertical = WenYaoSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(WenYaoSpacing.Md), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(WenYaoLayout.EmptyStateSize)) {
            val stroke = WenYaoShapeTokens.OutlineWidth.toPx()
            drawRect(color, Offset(stroke, stroke), Size(size.width - stroke * 2, size.height - stroke * 2), style = Stroke(stroke))
            repeat(3) { row ->
                val y = size.height * (0.3f + row * 0.2f)
                drawLine(color, Offset(size.width * 0.2f, y), Offset(size.width * 0.8f, y), stroke)
            }
        }
        Text("暂无历史卦例", style = MaterialTheme.typography.headlineSmall)
        Text("起卦后的推演记录将保存在此处。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun MiniHexagram(rawValues: String) {
    val lines = remember(rawValues) { runCatching { parseLineValues(rawValues) }.getOrDefault(emptyList()) }
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.size(WenYaoLayout.MiniGlyphSize).clearAndSetSemantics {}) {
        lines.asReversed().forEachIndexed { index, line ->
            val y = size.height * (index + 0.5f) / 6
            val color = if (line.isMoving) accent else ink
            val stroke = WenYaoShapeTokens.OutlineWidth.toPx()
            if (line.isYang) drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
            else {
                drawLine(color, Offset(0f, y), Offset(size.width * 0.4f, y), stroke)
                drawLine(color, Offset(size.width * 0.6f, y), Offset(size.width, y), stroke)
            }
        }
    }
}
