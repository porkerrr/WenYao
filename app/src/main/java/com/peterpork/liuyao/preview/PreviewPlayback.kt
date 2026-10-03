package com.peterpork.liuyao.preview

/** Render-thread clock, used on the main thread. Never holds Compose frame state or domain data. */
internal class PreviewPlayback(val speed: Float = 1f) {
    init { require(speed.isFinite() && speed > 0f) }
    var activeSeconds = 0.0
        private set
    var frameDeltaSeconds = 0.0
        private set
    var duration = 0f
        private set
    var seconds = 0f
        private set
    var playing = false
        private set
    private var lastFrame: Long? = null

    fun configure(duration: Float) {
        require(duration.isFinite() && duration > 0f)
        this.duration = duration
        seconds = seconds.coerceIn(0f, duration)
        suspendFrames()
    }

    fun replay() { seconds = 0f; activeSeconds = 0.0; playing = true; suspendFrames() }
    fun finish() { seconds = duration; playing = false; suspendFrames() }
    fun suspendFrames() { lastFrame = null; frameDeltaSeconds = 0.0 }

    // Filament 1.52 applies fmod(time, duration): duration itself wraps to frame zero.
    // The preceding representable float samples the stable last pose without wrapping.
    fun filamentSeconds(): Float = if (seconds >= duration && duration > 0f) Math.nextDown(duration) else seconds

    /** Returns true only on the transition to the held final pose. */
    fun frame(nanos: Long): Boolean {
        val previous = lastFrame
        lastFrame = nanos
        if (previous == null) return false
        frameDeltaSeconds = (nanos - previous).coerceAtLeast(0L) / 1_000_000_000.0
        activeSeconds += frameDeltaSeconds
        if (!playing) return false
        seconds = (activeSeconds * speed).toFloat().coerceAtMost(duration)
        if (seconds >= duration) { playing = false; return true }
        return false
    }
}
