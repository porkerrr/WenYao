package com.peterpork.liuyao.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Activity-owned presentation state. Never saved to the database or tied to business callbacks. */
internal class HomeEntranceSession(restored: Boolean, normalLaunch: Boolean, visible: Boolean = true) {
    enum class Phase { Pending, Running, Finishing, Finished }

    var contentVisible by mutableStateOf(visible)
    var previewMillis: Float? = null // Only assigned by the debug-only preview Activity.
    var completionCount = 0
        private set

    var phase by mutableStateOf(if (!restored && normalLaunch) Phase.Pending else Phase.Finished)
        private set

    /** Consumed before the first animated frame, not when the animation completes. */
    fun tryStart(): Boolean {
        if (phase != Phase.Pending) return false
        phase = Phase.Running
        return true
    }

    fun skip() {
        if (phase == Phase.Pending || phase == Phase.Running) phase = Phase.Finishing
    }

    fun finish() {
        if (phase == Phase.Finished) return
        completionCount++
        phase = Phase.Finished
    }

    fun onDestination(route: String?) {
        if (route != null && route != "home") finish()
    }
}
