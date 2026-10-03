package com.peterpork.liuyao.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect

internal val LocalCastingViewport = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.geometry.Rect?> { null }

/** Static, cached low-contrast washes; no bitmaps, particles or moving decoration. */
internal fun Modifier.castingInk() = drawWithCache {
    val washes = listOf(
        Brush.radialGradient(listOf(Color(0x0A514A3E), Color.Transparent), Offset(size.width*.43f,size.height*.61f),size.minDimension*.48f),
        Brush.radialGradient(listOf(Color(0x07514A3E), Color.Transparent), Offset(size.width*.61f,size.height*.67f),size.minDimension*.37f),
        Brush.radialGradient(listOf(Color(0x05514A3E), Color.Transparent), Offset(size.width*.55f,size.height*.40f),size.minDimension*.31f)
    )
    onDrawBehind { clipRect { washes.forEach { drawRect(it) } } }
}
