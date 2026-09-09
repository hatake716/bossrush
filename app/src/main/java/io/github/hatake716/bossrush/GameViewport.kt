package io.github.hatake716.bossrush

import kotlin.math.min

/** A uniform UI scale with flexible horizontal space and a safe input origin. */
data class GameViewport(
    val scale: Float, val x: Float, val y: Float, val width: Float,
    val fullLeft: Float, val fullTop: Float, val fullWidth: Float, val fullHeight: Float
) {
    val extra get()=(width-960f).coerceAtLeast(0f)
    fun screenX(logical: Float)=x+logical*scale
    fun screenY(logical: Float)=y+logical*scale
    fun logicalX(screen: Float)=(screen-x)/scale
    fun logicalY(screen: Float)=(screen-y)/scale

    companion object {
        fun fit(width: Int,height: Int,left: Int=0,top: Int=0,right: Int=0,bottom: Int=0): GameViewport {
            val w=(width-left-right).coerceAtLeast(1).toFloat()
            val h=(height-top-bottom).coerceAtLeast(1).toFloat()
            val scale=min(w/960f,h/540f)
            val x=left.toFloat(); val y=top+(h-540*scale)/2
            return GameViewport(scale,x,y,w/scale,-x/scale,-y/scale,width/scale,height/scale)
        }
    }
}
