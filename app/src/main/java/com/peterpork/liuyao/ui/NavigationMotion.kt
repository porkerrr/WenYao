package com.peterpork.liuyao.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

/** Presentation only. Route classification does not navigate or change the back stack. */
internal object NavigationMotion {
    const val ForwardMillis = 240
    const val BackMillis = 200
    const val PeerMillis = 200
    const val IncomingDp = 16
    const val OutgoingDp = 8
    enum class Kind { None, Peer, Forward, Back }

    fun kind(from: String?, to: String?, pop: Boolean, sameEntry: Boolean, enabled: Boolean): Kind {
        if (!enabled || sameEntry || from == null || to == null) return Kind.None
        val peers = setOf("home", "history", "settings")
        if (from in peers && to in peers) return Kind.Peer
        // The existing result toolbar returns home with navigate/popUpTo, rather than popBackStack.
        return if (pop || to == "home") Kind.Back else Kind.Forward
    }

    fun duration(kind: Kind): Int = when (kind) {
        Kind.None -> 0
        Kind.Peer -> PeerMillis
        Kind.Forward -> ForwardMillis
        Kind.Back -> BackMillis
    }

    fun direction(kind: Kind, rtl: Boolean): Int =
        (if (kind == Kind.Back) -1 else 1) * (if (rtl) -1 else 1)

    fun enter(kind: Kind, incomingPx: Int, rtl: Boolean): EnterTransition {
        if (kind == Kind.None) return EnterTransition.None
        val fade = fadeIn(tween(duration(kind)))
        return if (kind == Kind.Peer) fade else fade + slideInHorizontally(tween(duration(kind))) {
            incomingPx * direction(kind, rtl)
        }
    }

    fun exit(kind: Kind, outgoingPx: Int, rtl: Boolean): ExitTransition {
        if (kind == Kind.None) return ExitTransition.None
        val fade = fadeOut(tween(duration(kind)))
        return if (kind == Kind.Peer) fade else fade + slideOutOfBounds(outgoingPx, kind, rtl)
    }

    private fun slideOutOfBounds(px: Int, kind: Kind, rtl: Boolean) =
        slideOutHorizontally(tween(duration(kind))) { -px * direction(kind, rtl) }
}

/** Uses Navigation Compose's single host, including its native saved state and predictive back. */
@Composable
internal fun WenYaoNavHost(
    navController: NavHostController,
    startDestination: String,
    builder: NavGraphBuilder.() -> Unit,
) {
    val enabled = rememberDecorativeMotionEnabled()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val incoming = with(LocalDensity.current) { NavigationMotion.IncomingDp.dp.roundToPx() }
    val outgoing = with(LocalDensity.current) { NavigationMotion.OutgoingDp.dp.roundToPx() }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // Opaque theme backing also covers the narrow edges exposed by the two moving pages.
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        enterTransition = {
            NavigationMotion.enter(NavigationMotion.kind(initialState.destination.route, targetState.destination.route,
                false, initialState.id == targetState.id, enabled), incoming, rtl)
        },
        exitTransition = {
            NavigationMotion.exit(NavigationMotion.kind(initialState.destination.route, targetState.destination.route,
                false, initialState.id == targetState.id, enabled), outgoing, rtl)
        },
        popEnterTransition = {
            NavigationMotion.enter(NavigationMotion.kind(initialState.destination.route, targetState.destination.route,
                true, initialState.id == targetState.id, enabled), incoming, rtl)
        },
        popExitTransition = {
            NavigationMotion.exit(NavigationMotion.kind(initialState.destination.route, targetState.destination.route,
                true, initialState.id == targetState.id, enabled), outgoing, rtl)
        },
        builder = builder,
    )
}
