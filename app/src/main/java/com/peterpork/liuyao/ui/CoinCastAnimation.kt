package com.peterpork.liuyao.ui

sealed interface RevealState {
    data object Idle : RevealState
    data class Animating(val castId: String, val progress: Float, val runId: Int) : RevealState
    data class Revealed(val castId: String, val runId: Int) : RevealState
}

internal fun RevealState.castIdOrNull(): String? = when (this) {
    RevealState.Idle -> null
    is RevealState.Animating -> castId
    is RevealState.Revealed -> castId
}

internal fun RevealState.replay(castId: String): RevealState {
    val nextRun = when (this) {
        is RevealState.Animating -> runId + 1
        is RevealState.Revealed -> runId + 1
        RevealState.Idle -> 1
    }
    return RevealState.Animating(castId, 0f, nextRun)
}

internal fun RevealState.skip(): RevealState = when (this) {
    is RevealState.Animating -> RevealState.Revealed(castId, runId)
    else -> this
}

internal fun RevealState.cancel(): RevealState = RevealState.Idle
