package com.peterpork.liuyao.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.peterpork.liuyao.domain.CoinCastResult
import java.time.Instant

internal sealed interface PreparationState {
    data class Preparing(val completed: Int = 0, val total: Int = 8) : PreparationState
    data object Ready : PreparationState
    data object Failed : PreparationState
}

/** Presentation only; production uses 3D while results remain domain-owned. */
internal interface CastPresentation {
    @Composable
    fun Content(session: Instant?, cast: CoinCastResult?, state: RevealState, attempt: Int,
                onStateChange: (RevealState) -> Unit, onPreparation: (PreparationState) -> Unit, modifier: Modifier)
}

internal fun RevealState.runIdOrNull(): Int? = when (this) {
    RevealState.Idle -> null
    is RevealState.Animating -> runId
    is RevealState.Revealed -> runId
}

/** A skipped/finished run is terminal; an old renderer cannot revive or replace it. */
internal fun acceptPresentationUpdate(current: RevealState, incoming: RevealState): Boolean =
    current is RevealState.Animating && incoming !is RevealState.Idle &&
        current.castId == incoming.castIdOrNull() && current.runId == incoming.runIdOrNull()
