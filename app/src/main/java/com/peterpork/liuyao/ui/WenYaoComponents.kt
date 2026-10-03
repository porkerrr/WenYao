package com.peterpork.liuyao.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.peterpork.liuyao.R
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun WenYaoPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressFeedback: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = WenYaoSpacing.ActionHeight)
            .then(if (pressFeedback) Modifier.actionPressFeedback(interaction, enabled) else Modifier),
        enabled = enabled,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(disabledContainerColor = DisabledPaper, disabledContentColor = InkQuiet),
        content = content,
    )
}

@Composable
internal fun WenYaoOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressFeedback: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = WenYaoSpacing.SecondaryActionHeight)
            .then(if (pressFeedback) Modifier.actionPressFeedback(interaction, enabled) else Modifier),
        enabled = enabled,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(WenYaoShapeTokens.OutlineWidth, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, disabledContentColor = InkQuiet),
        content = content,
    )
}

@Composable
internal fun WenYaoSurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(WenYaoShapeTokens.OutlineWidth, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = WenYaoShapeTokens.FlatElevation),
    ) {
        Column(
            modifier = Modifier.padding(WenYaoSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm),
            content = content,
        )
    }
}

@Composable
internal fun WenYaoErrorBanner(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = BorderStroke(WenYaoShapeTokens.OutlineWidth, MaterialTheme.colorScheme.error.copy(alpha = 0.36f)),
        elevation = CardDefaults.cardElevation(defaultElevation = WenYaoShapeTokens.FlatElevation),
    ) {
        Text(message, Modifier.padding(WenYaoSpacing.Md), MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun WenYaoInfoBanner(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(WenYaoShapeTokens.OutlineWidth, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = WenYaoShapeTokens.FlatElevation),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(WenYaoSpacing.Md),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun WenYaoSection(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = WenYaoSpacing.Md)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(WenYaoSpacing.Sm))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A single input policy; editing callbacks and masking remain caller-owned. */
@Composable
internal fun WenYaoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = true,
        visualTransformation = visualTransformation, trailingIcon = trailingIcon,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/** Counter has its own decoration row and cannot overlay editable text. */
@Composable
internal fun WenYaoQuestionField(value: String, onValueChange: (String) -> Unit, errorMessage: String?, modifier: Modifier = Modifier, enabled: Boolean = true,
    compact: Boolean = false, minHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    contentPadding: androidx.compose.ui.unit.Dp = WenYaoSpacing.Lg, contentSpacing: androidx.compose.ui.unit.Dp = WenYaoSpacing.Sm) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val targetBorderColor = when { errorMessage != null -> colors.error; focused -> colors.primary; else -> colors.outline }
    val animateFocus = rememberDecorativeMotionEnabled() && enabled && errorMessage == null
    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = if (animateFocus) tween(ControlMotion.FocusMillis) else snap(),
        label = "questionFocusBorder",
    )
    val borderColor = if (animateFocus) animatedBorderColor else targetBorderColor
    val borderWidth = when { errorMessage != null -> WenYaoShapeTokens.ErrorOutlineWidth; focused -> WenYaoShapeTokens.FocusOutlineWidth; else -> WenYaoShapeTokens.OutlineWidth }
    BasicTextField(
        value = value, onValueChange = onValueChange, minLines = if (compact) 2 else 4,
        maxLines = if (compact) 2 else Int.MAX_VALUE, enabled = enabled,
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = "你想问什么？"
            if (errorMessage != null) error(errorMessage)
        },
        decorationBox = { innerTextField ->
            Column(
                Modifier.heightIn(min = minHeight).background(colors.surfaceVariant, MaterialTheme.shapes.small)
                    .border(borderWidth, borderColor, MaterialTheme.shapes.small).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
            ) {
                Box {
                    if (value.isEmpty()) Text("例如：最近找工作的进展怎么样？", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    innerTextField()
                }
                Text("${value.length}/200", Modifier.align(Alignment.End), style = MaterialTheme.typography.bodySmall,
                    color = if (value.length >= 190) colors.tertiary else colors.onSurfaceVariant)
            }
        },
    )
}

/** Readable width, keyboard clearance and gesture-area padding for pinned actions. */
@Composable
internal fun WenYaoActionBar(verticalPadding: androidx.compose.ui.unit.Dp = WenYaoSpacing.Md, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = WenYaoLayout.ContentMaxWidth)
                .fillMaxWidth().padding(horizontal = WenYaoSpacing.PageHorizontal, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm), content = content,
        )
    }
}

/** A fixed, decorative paper layer; content and scrolling remain above it. */
@Composable
internal fun PaperBackdrop(color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.background, textureAlpha: Float = 1f, content: @Composable () -> Unit) {
    val paper = remember { PaperSurface() }
    Box(Modifier.fillMaxSize().background(color).onGloballyPositioned { paper.bounds = it.boundsInWindow() }) {
        Image(
            painter = painterResource(R.drawable.paper_fiber_texture),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(color, BlendMode.Multiply),
            alpha = textureAlpha,
        )
        CompositionLocalProvider(LocalPaperSurface provides paper) { content() }
    }
}

@Composable
internal fun WenYaoBusyIndicator(label: String, modifier: Modifier = Modifier) {
    Row(modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(WenYaoLayout.ProgressSize), color = LocalContentColor.current,
            strokeWidth = WenYaoShapeTokens.FocusOutlineWidth)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
