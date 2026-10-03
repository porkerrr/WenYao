package com.peterpork.liuyao.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** All timings are at system animator scale 1x. One Compose clock also scales the stagger. */
internal object HomeEntranceMotion {
    const val TotalMillis = 2200
    const val SpreadEnd = 800
    const val WritingEnd = 1600
    const val SkipMillis = 150
    const val SubtitleMillis = 200
    const val InkInitialScale = 0.12f
    const val InkContraction = 0.72f
    const val InkRotation = 11f
    const val PaperTextureAlpha = 1f
    val InkWidth = 280.dp
    val InkHeight = 210.dp
    val Rise = 16.dp
    val SpreadEase = androidx.compose.animation.core.CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
    val WritingEase = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val MoveEase = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)
    fun enabled(scale: Float, platformEnabled: Boolean = true): Boolean =
        platformEnabled && scale.isFinite() && scale > 0f
    fun fraction(elapsed: Float, delay: Int, duration: Int): Float =
        ((elapsed - delay) / duration).coerceIn(0f, 1f)
    fun skipDuration(elapsed: Float, scale: Float): Int =
        minOf(SkipMillis, ((TotalMillis - elapsed).coerceAtLeast(0f) * scale).toInt())
}

internal data class SplashValues(
    val ink: Float = 0f, val spread: Float = 0f, val writing: Float = 0f,
    val subtitle: Float = 0f, val move: Float = 0f, val body: Float = 0f,
) {
    fun towardsEnd(t: Float) = if (t >= 1f) Final else SplashValues(
        ink * (1f-t), spread, writing + (1f-writing)*t,
        subtitle + (1f-subtitle)*t, move + (1f-move)*t, body + (1f-body)*t,
    )
    companion object {
        val Final = SplashValues(0f, 1f, 1f, 1f, 1f, 1f)
        fun at(ms: Float): SplashValues {
            if (ms >= HomeEntranceMotion.TotalMillis) return Final
            val spreadEnd = HomeEntranceMotion.SpreadEnd
            val writingEnd = HomeEntranceMotion.WritingEnd
            val spread = HomeEntranceMotion.SpreadEase.transform(HomeEntranceMotion.fraction(ms, 0, spreadEnd))
            val writing = HomeEntranceMotion.WritingEase.transform(HomeEntranceMotion.fraction(ms, spreadEnd, writingEnd-spreadEnd))
            val move = HomeEntranceMotion.MoveEase.transform(HomeEntranceMotion.fraction(ms, writingEnd, HomeEntranceMotion.TotalMillis-writingEnd))
            return SplashValues(
                ink = if (ms <= spreadEnd) spread else 1f-writing,
                spread = spread * (1f - writing * HomeEntranceMotion.InkContraction), writing = writing,
                subtitle = HomeEntranceMotion.fraction(ms, writingEnd-HomeEntranceMotion.SubtitleMillis,
                    HomeEntranceMotion.SubtitleMillis), move = move, body = move,
            )
        }
    }
}

/** Synchronous first read avoids a transparent first frame when animations are disabled. */
@Composable
internal fun rememberDecorativeMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val composeScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    fun readEnabled() = HomeEntranceMotion.enabled(
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        ValueAnimator.areAnimatorsEnabled(),
    )
    val enabled = remember(resolver) { mutableStateOf(readEnabled()) }
    DisposableEffect(resolver, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled.value = readEnabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled.value = readEnabled()
        }
        lifecycle.addObserver(lifecycleObserver)
        enabled.value = readEnabled()
        onDispose {
            resolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return enabled.value && HomeEntranceMotion.enabled(composeScale?.scaleFactor ?: 1f)
}

internal class HomeEntranceFrame(val animationsEnabled: Boolean = true, private val values: () -> SplashValues) {
    val value get() = values()
    val title get() = value.writing
    val question get() = value.body
    val action get() = value.body
}

@Composable
internal fun rememberHomeEntrance(session: HomeEntranceSession, ready: Boolean = true): HomeEntranceFrame {
    val enabled = rememberDecorativeMotionEnabled()
    val clock = remember(session) { Animatable(0f) }
    val finishClock = remember(session) { Animatable(0f) }
    val snapshot = remember(session) { mutableStateOf(SplashValues()) }
    val scope = rememberCoroutineScope()
    val scale = scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val phase = session.phase
    val visible = session.contentVisible
    val resolver = LocalContext.current.contentResolver
    DisposableEffect(session, resolver) {
        // Settings IPC must also terminate the session when the Compose test/display clock is
        // suspended. Do not leave a visually-final but still-blocking session until another frame.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f)
                    session.finish()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(session, enabled, ready, visible,
        phase == HomeEntranceSession.Phase.Finishing, phase == HomeEntranceSession.Phase.Finished) {
        if (session.previewMillis != null) return@LaunchedEffect
        if (!enabled || phase == HomeEntranceSession.Phase.Finished) {
            session.finish()
            return@LaunchedEffect
        }
        if (phase == HomeEntranceSession.Phase.Finishing) {
            snapshot.value = SplashValues.at(clock.value)
            // Bound skip by wall time, including nonstandard system animation scales.
            val duration = HomeEntranceMotion.skipDuration(clock.value, scale)
            val unscaled = if (scale > 0f) (duration / scale).toInt() else 0
            finishClock.animateTo(1f, tween(unscaled, easing = HomeEntranceMotion.MoveEase))
            session.finish()
        } else if (ready && visible) {
            // The frame after removal is the first frame actually available to the app.
            androidx.compose.runtime.withFrameNanos { }
            if (session.tryStart() || session.phase == HomeEntranceSession.Phase.Running) {
                clock.animateTo(HomeEntranceMotion.TotalMillis.toFloat(),
                    tween((HomeEntranceMotion.TotalMillis-clock.value).toInt(), easing = LinearEasing))
                session.finish()
            }
        }
    }
    DisposableEffect(session) { onDispose { session.finish() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) session.finish()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return remember(session, enabled, phase) {
        HomeEntranceFrame(enabled) {
            val preview = session.previewMillis
            when {
                preview != null -> SplashValues.at(preview)
                !enabled || session.phase == HomeEntranceSession.Phase.Finished -> SplashValues.Final
                session.phase == HomeEntranceSession.Phase.Finishing -> snapshot.value.towardsEnd(finishClock.value)
                else -> SplashValues.at(clock.value)
            }
        }
    }
}

/** Draw-layer transforms preserve final measurement and do not recompose the home every frame. */
internal fun Modifier.inkEntrance(progress: () -> Float, rise: Boolean = false): Modifier = graphicsLayer {
    val value = progress()
    alpha = value
    translationY = if (rise) (1f - value) * HomeEntranceMotion.Rise.toPx() else 0f
}

/** Capture the entire gesture, including UP even if completion happens while the finger is held. */
internal fun Modifier.finishEntranceOnTouch(session: HomeEntranceSession): Modifier = pointerInput(session) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (session.phase != HomeEntranceSession.Phase.Finished) {
            down.consume()
            session.skip()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
