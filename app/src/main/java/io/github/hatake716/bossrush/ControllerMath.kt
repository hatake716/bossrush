package io.github.hatake716.bossrush

import kotlin.math.*

/** Shared by Android stick input and deterministic navigation tests. */
object ControllerMath {
    data class Point(val x: Float,val y: Float)
    fun stick(x: Float,y: Float,flat: Float): Point {
        val radius=hypot(x,y)
        val dead=flat.coerceIn(.18f,.5f)
        if(!radius.isFinite() || radius<=dead) return Point(0f,0f)
        val strength=((radius-dead)/(1-dead)).coerceAtMost(1f)
        return Point(x/radius*strength,y/radius*strength)
    }
    fun direction(x: Float,y: Float): Point = when {
        max(abs(x),abs(y))<.55f -> Point(0f,0f)
        abs(x)>abs(y) -> Point(sign(x),0f)
        else -> Point(0f,sign(y))
    }
    fun neighbor(points: List<Point>,current: Int,dx: Float,dy: Float): Int {
        if(points.isEmpty()) return -1
        val origin=points.getOrNull(current) ?: return 0
        // Prefer the same row/column; still allow access to headers and uneven grids.
        return points.indices.filter { it!=current }.mapNotNull { i ->
            val x=points[i].x-origin.x; val y=points[i].y-origin.y
            val forward=x*dx+y*dy
            if(forward<=1f) null else i to (forward+abs(x*dy-y*dx)*3)
        }.minByOrNull { it.second }?.first ?: current
    }
}
