package com.peterpork.liuyao.preview

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** Fault machinery exists only in the isolated Debug verification path. */
internal class DebugCastingPresentation private constructor(private val injection: Injection) :
    CastingScenePresentation(
        beforeRenderer = { check(injection.faultFor("") != Fault.RENDERER) },
        beforeLoad = {
            delay(injection.delayMillis)
            if (injection.faultFor("") == Fault.TIMEOUT) awaitCancellation()
            check(injection.faultFor("") != Fault.LOAD)
        },
    ) {
    constructor() : this(Injection())
    enum class Fault { NONE, LOAD, TIMEOUT, RENDERER }
    private class Injection {
        var faultFor: (String) -> Fault = { Fault.NONE }
        var delayMillis = 0L
    }
    var loadDelayMillis: Long
        get() = injection.delayMillis
        set(value) { require(value >= 0); injection.delayMillis = value }
    var faultFor: (String) -> Fault
        get() = injection.faultFor
        set(value) { injection.faultFor = value }
}
