package com.peterpork.liuyao.preview

/** PixelCopy may contain full RGB dither at alpha 0/1 even when nothing was rendered. */
internal fun hasVisibleCoinPixels(pixels: IntArray): Boolean {
    var opaque = 0
    var low = 765
    var high = 0
    for (pixel in pixels) {
        if ((pixel ushr 24) < 192) continue // Exclude transparent clear and soft shadows.
        opaque++
        val light = (pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255)
        low = minOf(low, light)
        high = maxOf(high, light)
    }
    return opaque >= 4 && high - low > 48
}
