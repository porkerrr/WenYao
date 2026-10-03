package com.peterpork.liuyao.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/** Window coordinates let embedded native content reproduce the existing paper crop exactly. */
internal class PaperSurface {
    var bounds by mutableStateOf(Rect.Zero)
}
internal val LocalPaperSurface = compositionLocalOf { PaperSurface() }
