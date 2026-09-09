package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class ControllerMathTest {
    @Test fun stickRejectsDriftHonorsDeviceFlatAndPreservesAnalogSpeed() {
        assertEquals(ControllerMath.Point(0f,0f),ControllerMath.stick(.12f,-.10f,0f))
        assertEquals(ControllerMath.Point(0f,0f),ControllerMath.stick(.29f,0f,.30f))
        val half=ControllerMath.stick(.6f,0f,.2f)
        assertEquals(.5f,half.x,.001f); assertEquals(0f,half.y,0f)
        val diagonal=ControllerMath.stick(1f,-1f,0f)
        assertEquals(1f,hypot(diagonal.x,diagonal.y),.001f); assertTrue(diagonal.y<0)
    }
    @Test fun menusFollowRowsColumnsAndLeaveEdgeSelectionInPlace() {
        val p=listOf(ControllerMath.Point(800f,30f),ControllerMath.Point(100f,200f),ControllerMath.Point(300f,200f),
            ControllerMath.Point(500f,200f),ControllerMath.Point(100f,400f),ControllerMath.Point(500f,400f))
        assertEquals(2,ControllerMath.neighbor(p,1,1f,0f))
        assertEquals(4,ControllerMath.neighbor(p,1,0f,1f))
        assertEquals(0,ControllerMath.neighbor(p,3,0f,-1f))
        assertEquals(1,ControllerMath.neighbor(p,1,-1f,0f))
        assertEquals(-1,ControllerMath.neighbor(emptyList(),0,1f,0f))
        assertEquals(ControllerMath.Point(0f,-1f),ControllerMath.direction(.2f,-.8f))
    }
    @Test fun allCutinBackdropsUseTheirAttackEnergyHue() {
        assertEquals(32,UltimateColors.all.values.map { it.energy to it.cutinEdge }.toSet().size)
        UltimateColors.all.values.forEach { palette ->
            for(shift in listOf(0,8,16)) assertEquals(((palette.energy shr shift and 255)*.22).toInt(),palette.cutinEdge shr shift and 255)
        }
    }
}
