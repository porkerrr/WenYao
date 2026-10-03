package com.peterpork.liuyao.preview

/** Charge the interval using its previous lifecycle state, including the pause boundary. */
internal class ForegroundBudget(private val limitMillis: Long) {
    init { require(limitMillis > 0) }
    var elapsedMillis = 0L
        private set
    private var previous: Long? = null
    private var wasRunning = false
    fun update(nowMillis: Long, running: Boolean): Boolean {
        previous?.let { if (wasRunning) elapsedMillis += (nowMillis - it).coerceAtLeast(0) }
        previous = nowMillis
        wasRunning = running
        return elapsedMillis >= limitMillis
    }
}
