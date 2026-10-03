package com.peterpork.liuyao.ui

import org.junit.Assert.*
import org.junit.Test
import com.peterpork.liuyao.ui.NavigationMotion.Kind

class NavigationMotionTest {
    @Test fun peerRoutesFadeInBothDirections() {
        for (target in listOf("history", "settings")) {
            assertEquals(Kind.Peer, kind("home", target))
            assertEquals(Kind.Peer, kind(target, "home", true))
        }
    }
    @Test fun flowAndDetailForwardAndBackIncludeExistingToolbarHomeNavigation() {
        for ((from, to) in listOf("home" to "casting", "casting" to "result/{id}",
            "history" to "result/{id}", "result/{id}" to "settings")) {
            assertEquals(Kind.Forward, kind(from, to))
            assertEquals(Kind.Back, kind(to, from, true))
        }
        assertEquals(Kind.Back, kind("result/{id}", "home"))
    }
    @Test fun firstCompositionRestorationAndDisabledMotionHaveNoHostEntrance() {
        assertEquals(Kind.None, NavigationMotion.kind("home", "home", false, true, true))
        assertEquals(Kind.None, NavigationMotion.kind("result/{id}", "result/{id}", false, true, true))
        assertEquals(Kind.None, NavigationMotion.kind("home", "casting", false, false, false))
        assertEquals(Kind.None, NavigationMotion.kind(null, "home", false, false, true))
        assertEquals(0, NavigationMotion.duration(Kind.None))
    }
    @Test fun directionsMirrorInRtlAndDistancesStaySmall() {
        assertEquals(1, NavigationMotion.direction(Kind.Forward, false))
        assertEquals(-1, NavigationMotion.direction(Kind.Back, false))
        assertEquals(-1, NavigationMotion.direction(Kind.Forward, true))
        assertEquals(1, NavigationMotion.direction(Kind.Back, true))
        assertTrue(NavigationMotion.IncomingDp in 12..16)
        assertTrue(NavigationMotion.OutgoingDp in 0..8)
        assertTrue(NavigationMotion.ForwardMillis in 220..260)
        assertTrue(NavigationMotion.BackMillis in 180..220)
    }
    private fun kind(from: String, to: String, pop: Boolean = false) =
        NavigationMotion.kind(from, to, pop, sameEntry = false, enabled = true)
}
