package com.peterpork.liuyao.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant

internal object DivinationMotion {
    const val LineMillis = 220
    const val CompletionMillis = 240
}

/** Identity comes from the committed cast, never its 6/7/8/9 value or a playback run. */
internal data class NewLineInk(val session: Instant?, val castId: String, val position: Int,
    val reveal: InkReveal = InkReveal())

internal data class ResultInk(val recordId: String, val reveal: InkReveal)

/** A disposable presentation ticket, created only by the existing user action/save callback.
 * Existing/restored data has no ticket. This object has no domain callbacks or persistence.
 */
internal class InkReveal(eligible: Boolean = true) {
    enum class Phase { Waiting, Running, Finished }
    var phase by mutableStateOf(if (eligible) Phase.Waiting else Phase.Finished)
        private set

    fun start(): Boolean {
        if (phase != Phase.Waiting) return false
        phase = Phase.Running // Consume before the first animated frame.
        return true
    }

    fun finish() { phase = Phase.Finished }
}

internal class InkFrame(private val progress: State<Float>, private val final: Boolean) {
    val fraction: Float get() = if (final) 1f else progress.value
}

/** Waiting is tied to real readiness (coin Revealed / loaded record), never a guessed delay. */
@Composable
internal fun rememberInkReveal(reveal: InkReveal?, ready: Boolean, duration: Int): InkFrame {
    val enabled = rememberDecorativeMotionEnabled()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val progress = remember(reveal) { Animatable(0f) }
    val finished = reveal == null || reveal.phase == InkReveal.Phase.Finished
    DisposableEffect(reveal, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) reveal?.finish()
        }
        lifecycle.addObserver(observer)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) reveal?.finish()
        onDispose { lifecycle.removeObserver(observer); reveal?.finish() }
    }
    LaunchedEffect(reveal, ready, enabled, finished) {
        if (!enabled) reveal?.finish()
        if (enabled && ready && !finished && reveal?.start() == true) {
            try {
                progress.animateTo(1f, tween(duration, easing = LinearOutSlowInEasing))
            } finally {
                reveal.finish() // Only visual fast-forward, even on cancellation.
            }
        }
    }
    return InkFrame(progress.asState(), final = !enabled || finished)
}
