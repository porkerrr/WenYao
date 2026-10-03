package com.peterpork.liuyao.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/** Bind toolbar actions to their owning entry, not whichever page is on top when clicked. */
@Composable
internal fun guardedPageBack(
    nav: NavController,
    entry: NavBackStackEntry,
    returnHome: Boolean = false,
): () -> Unit = dropUnlessResumed(entry) {
    // An outgoing page can still receive clicks before its exit animation is disposed.
    // Entry identity also protects against a new instance of the same route.
    if (nav.currentBackStackEntry?.id == entry.id) {
        when {
            returnHome -> nav.navigate("home") { popUpTo("home") { inclusive = true } }
            nav.previousBackStackEntry != null -> {
                if (!nav.popBackStack() && nav.currentBackStackEntry == null) nav.restoreHome()
            }
            entry.destination.route != "home" -> nav.restoreHome()
            // Never pop the root page from an in-app toolbar. System Back keeps native behavior.
        }
    }
}

private fun NavController.restoreHome() {
    navigate("home") {
        popUpTo(graph.id) { inclusive = false }
        launchSingleTop = true
    }
}
