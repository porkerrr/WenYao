package com.peterpork.liuyao.preview

/** Source-time gate; loading/background time cannot advance the foreground playback clock. */
internal class SettledReveal(val stableSourceSeconds: Double, val speed: Double = 1.25,
                            val leadSeconds: Double = .300) {
    init {
        require(stableSourceSeconds.isFinite() && stableSourceSeconds >= 0)
        require(speed.isFinite() && speed > 0)
        require(leadSeconds.isFinite() && leadSeconds >= 0)
    }
    val stableActiveSeconds = stableSourceSeconds / speed
    val revealSourceSeconds = (stableSourceSeconds - leadSeconds * speed).coerceAtLeast(0.0)
    var revealedAt: Double? = null
        private set
    fun reset() { revealedAt = null }
    fun frame(sourceSeconds: Float, activeSeconds: Double): Boolean {
        if (revealedAt != null || sourceSeconds.toDouble() + 1e-7 < revealSourceSeconds) return false
        revealedAt = activeSeconds
        return true
    }
}
