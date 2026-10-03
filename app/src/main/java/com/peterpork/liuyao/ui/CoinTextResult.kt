package com.peterpork.liuyao.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.peterpork.liuyao.domain.CoinCastResult

/** Graphics failure must never generate a replacement result or block the session. */
@Composable
internal fun CoinTextResult(cast: CoinCastResult, state: RevealState,
                            onStateChange: (RevealState) -> Unit, modifier: Modifier) {
    LaunchedEffect(cast.castId, state) {
        if (state is RevealState.Animating && state.castId == cast.castId) {
            onStateChange(RevealState.Revealed(cast.castId, state.runId))
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("铜钱画面暂不可用，已保留本次结果")
    }
}
