package io.github.hatake716.bossrush

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.*

/** Bounded, deterministic pixel fragments. Presentation never deals damage or awards rewards. */
internal class BossDefeatEffects {
    private val paint=Paint().apply { isAntiAlias=false; isFilterBitmap=false }
    fun shake(age: Double): Pair<Float,Float> {
        val strength=12.0*(1-age/1.35).coerceIn(0.0,1.0).pow(2)
        return (sin(age*91)*strength).roundToInt().toFloat() to (cos(age*117)*strength*.75).roundToInt().toFloat()
    }
    fun flash(age: Double,id: String): Int {
        val alpha=(110*(1-age/.32).coerceIn(0.0,1.0)).toInt()
        return (UltimateColors.forBoss(id).core and 0x00ffffff) or (alpha shl 24)
    }
    private fun block(c: Canvas,x: Double,y: Double,size: Float,color: Int,alpha: Int=255) {
        paint.color=color; paint.alpha=alpha.coerceIn(0,255)
        val xx=(x/2).roundToInt()*2f; val yy=(y/2).roundToInt()*2f
        c.drawRect(xx,yy,xx+size,yy+size,paint)
    }
    fun draw(c: Canvas,art: PixelArt,id: String,x: Float,feet: Float,age: Double) {
        val colors=UltimateColors.forBoss(id)
        val t=(age-.10).coerceAtLeast(0.0)
        val centerY=feet-50f
        // The actual boss portrait breaks into 48 pieces, preserving its silhouette at impact.
        for(row in 0..5) for(col in 0..7) {
            val i=row*8+col
            val left=x-80+col*20f; val top=feet-110+row*(110f/6)
            val angle=atan2((row-2.5)*1.1,col-3.5)
            val speed=75+(i*37%115)
            val dx=(cos(angle)*speed*t).toFloat()
            val dy=(sin(angle)*speed*t+100*t*t).toFloat()
            val alpha=(255*(1-t/1.2).coerceIn(0.0,1.0)).toInt()
            if(alpha==0) continue
            c.save(); c.translate(dx.roundToInt().toFloat(),dy.roundToInt().toFloat())
            c.clipRect(left,top,left+20,top+110f/6)
            art.boss(c,id,x,feet,160f,110f,alpha)
            c.restore()
        }
        // Two expanding rings and long radial sparks retain hard pixel edges.
        repeat(2) { wave ->
            val u=t-wave*.14
            if(u>=0 && u<1.2) {
                val radius=14+u*250
                val alpha=(220*(1-u/1.2)).toInt()
                for(i in 0 until 96) {
                    val a=i*PI/48
                    block(c,x+cos(a)*radius,centerY+sin(a)*radius*.70,if(wave==0) 6f else 4f,
                        if(wave==0) colors.energy else colors.accent,alpha)
                }
            }
        }
        for(i in 0 until 88) {
            val a=i*2.399963+(id.hashCode() and 31)*.1
            val speed=65+i*43%260
            val radius=8+speed*t
            val fade=(1-t/(.7+(i%7)*.13)).coerceIn(0.0,1.0)
            if(fade<=0) continue
            val color=when(i%3) { 0 -> colors.core; 1 -> colors.energy; else -> colors.accent }
            for(tail in 2 downTo 0) {
                val r=(radius-tail*9).coerceAtLeast(0.0)
                block(c,x+cos(a)*r,centerY+sin(a)*r*.8+45*t*t,
                    (if(tail==0) 4+i%3*2 else 2).toFloat(),color,(255*fade/(tail+1)).toInt())
            }
        }
        // A single expanding white core fades away; no repeating full-screen strobe.
        if(t<.4) {
            val size=(12+80*t).toFloat()
            val alpha=(240*(1-t/.4)).toInt()
            block(c,(x-size).toDouble(),(centerY-4).toDouble(),8f,colors.core,alpha)
            paint.color=colors.core; paint.alpha=alpha
            c.drawRect(x-size,centerY-3,x+size,centerY+3,paint)
            c.drawRect(x-3,centerY-size,x+3,centerY+size,paint)
        }
        paint.alpha=255
    }
}
