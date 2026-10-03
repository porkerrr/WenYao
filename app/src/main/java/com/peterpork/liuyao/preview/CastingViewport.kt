package com.peterpork.liuyao.preview

/** Crop the rendered buffer itself: a transparent SurfaceView is above the app window. */
internal data class CastingViewport(val left: Int, val bottom: Int, val width: Int, val height: Int) {
    fun projection(full: DoubleArray, fullWidth: Int, fullHeight: Int): DoubleArray = full.copyOf().also {
        val sx = fullWidth.toDouble() / width.coerceAtLeast(1)
        val sy = fullHeight.toDouble() / height.coerceAtLeast(1)
        val ox = (fullWidth - 2.0 * left - width) / width.coerceAtLeast(1)
        val oy = (fullHeight - 2.0 * bottom - height) / height.coerceAtLeast(1)
        for (column in 0..3) {
            val i = column * 4
            it[i] = sx * full[i] + ox * full[i + 3]
            it[i + 1] = sy * full[i + 1] + oy * full[i + 3]
        }
    }
}
