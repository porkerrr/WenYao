package com.peterpork.liuyao.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** Decorative control feedback only. Durations are defined at system animator scale 1x. */
internal object ControlMotion {
    const val PressedScale = 0.97f
    const val PressMillis = 90
    const val ReleaseMillis = 160
    const val FocusMillis = 160
}

@Composable
internal fun Modifier.actionPressFeedback(source: InteractionSource, enabled: Boolean): Modifier {
    val motionEnabled = rememberDecorativeMotionEnabled()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    var pauseGeneration by remember(lifecycle) { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            // A rapid pause/resume can be conflated into RESUMED before the next composition.
            if (event == Lifecycle.Event.ON_PAUSE) pauseGeneration++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val active = enabled && motionEnabled && lifecycleState == Lifecycle.State.RESUMED
    // A fresh active period never inherits an outstanding press from before pause/disable.
    var pressed by remember(source, active, pauseGeneration) { mutableStateOf(false) }
    val scale = remember(source, active, pauseGeneration) { Animatable(1f) }
    LaunchedEffect(source, active, pauseGeneration) {
        if (active) {
            val presses = mutableSetOf<PressInteraction.Press>()
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                }
                pressed = presses.isNotEmpty()
            }
        }
    }
    LaunchedEffect(scale, pressed) {
        scale.animateTo(
            if (pressed) ControlMotion.PressedScale else 1f,
            tween(if (pressed) ControlMotion.PressMillis else ControlMotion.ReleaseMillis,
                easing = LinearOutSlowInEasing),
        )
    }
    // Draw only: keep the original hit region, semantics bounds and measured size even while pressed.
    return drawWithContent {
        scale(if (active) scale.value else 1f) { this@drawWithContent.drawContent() }
    }
}
