package com.peterpork.liuyao.preview

import org.junit.Assert.*
import org.junit.Test

class CastingVisualsTest {
    @Test fun croppedProjectionPreservesFullStagePixelPositions() {
        val full = doubleArrayOf(2.0,0.0,0.0,0.0, 0.0,3.0,0.0,0.0, 0.0,0.0,-1.0,-1.0, 0.0,0.0,-.02,0.0)
        for (crop in listOf(CastingViewport(0,0,1000,500), CastingViewport(50,30,800,300))) {
            val p = crop.projection(full,1000,500)
            for (x in listOf(-.5,0.0,.5)) for (y in listOf(-.3,0.0,.3)) {
                val expectedX = (full[0]*x/2+1)*500
                val expectedY = (full[5]*y/2+1)*250
                val actualX = crop.left+( (p[0]*x+p[8]*-2)/2+1)*crop.width/2
                val actualY = crop.bottom+( (p[5]*y+p[9]*-2)/2+1)*crop.height/2
                assertEquals(expectedX,actualX,1e-8); assertEquals(expectedY,actualY,1e-8)
            }
        }
    }
    @Test fun fullMotionEnvelopeFitsPortraitLandscapeAndNarrowStages() {
        for (aspect in listOf(.65, 1.0, 1.75, 2.5, 4.0)) {
            val tangent = CastingFraming.tangent(aspect)
            assertTrue(tangent > .123155)
            assertTrue(tangent * aspect > .155177)
        }
    }
    @Test fun contactShadowFadesAndSpreadsWithHeightWithoutMovingCoins() {
        assertEquals(7, shadowLevel(.036f))
        assertEquals(0, shadowLevel(3f))
        assertEquals(1f, shadowScale(.036f), .0001f)
        assertEquals(2f, shadowScale(3f), .0001f)
        for (height in 0..199) {
            assertTrue(shadowLevel(height / 100f) >= shadowLevel((height + 1) / 100f))
            assertTrue(shadowScale(height / 100f) <= shadowScale((height + 1) / 100f))
        }
    }
}
