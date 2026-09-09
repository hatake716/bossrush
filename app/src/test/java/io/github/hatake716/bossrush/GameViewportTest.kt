package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test

class GameViewportTest {
    @Test fun widePhonesUseAllSafeWidthWithoutStretchingCombatCircles() {
        for(width in listOf(1920,2400,2424,2640)) {
            val v=GameViewport.fit(width,1080)
            assertEquals(0f,v.screenX(0f),.001f)
            assertEquals(width.toFloat(),v.screenX(v.width),.001f)
            assertEquals(1080f,v.screenY(540f),.001f)
            assertEquals(v.screenX(107f)-v.screenX(100f),v.screenY(107f)-v.screenY(100f),.001f)
            assertEquals(940f,v.logicalX(v.screenX(940f)),.001f)
        }
    }
    @Test fun eitherCutoutSideProtectsControlsButKeepsTheBackdropAtPhysicalEdges() {
        for((left,right) in listOf(96 to 0,0 to 96,60 to 60)) {
            val v=GameViewport.fit(2424,1080,left=left,right=right)
            assertEquals(left.toFloat(),v.screenX(0f),.001f)
            assertEquals((2424-right).toFloat(),v.screenX(v.width),.001f)
            assertEquals(0f,v.screenX(v.fullLeft),.001f)
            assertEquals(2424f,v.screenX(v.fullLeft+v.fullWidth),.001f)
            assertTrue(v.screenX(937+v.extra)<2424-right)
        }
    }
    @Test fun tabletWindowKeepsEveryControlVisibleAndBackdropFullHeight() {
        val v=GameViewport.fit(1600,1200,top=20,bottom=20)
        assertEquals(960f,v.width,.001f)
        assertTrue(v.screenY(0f)>=20)
        assertTrue(v.screenY(540f)<=1180)
        assertEquals(0f,v.screenY(v.fullTop),.001f)
        assertEquals(1200f,v.screenY(v.fullTop+v.fullHeight),.001f)
    }
}
