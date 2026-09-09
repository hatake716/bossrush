package io.github.hatake716.bossrush

import android.graphics.*
import kotlin.math.*

/** Purely visual aftermath, independent of damage timing and collision geometry. */
data class BattleImpact(val hazard: Hazard,var age: Double=0.0) {
    val lifetime get()=if(hazard.multiplier>1) .78 else .60
    val progress get()=(age/lifetime).coerceIn(.0,1.0)
}

class BattleEffects {
    private val p=Paint().apply { isAntiAlias=false }
    private val path=Path()
    private val bolt=Path()
    private fun shape(h: Hazard): Path {
        path.reset()
        val x=h.x.toFloat(); val y=h.y.toFloat(); val a=h.a.toFloat(); val b=h.b.toFloat()
        when(h.shape) {
            "circle" -> path.addCircle(x,y,a,Path.Direction.CW)
            "ring","safe","tower" -> {
                path.fillType=Path.FillType.EVEN_ODD
                path.addRect(0f,0f,600f,334f,Path.Direction.CW)
                path.addCircle(x,y,a,Path.Direction.CW)
            }
            "line" -> {
                val dx=cos(h.angle).toFloat(); val dy=sin(h.angle).toFloat()
                path.moveTo(x-dx*a/2+dy*b/2,y-dy*a/2-dx*b/2); path.lineTo(x+dx*a/2+dy*b/2,y+dy*a/2-dx*b/2)
                path.lineTo(x+dx*a/2-dy*b/2,y+dy*a/2+dx*b/2); path.lineTo(x-dx*a/2-dy*b/2,y-dy*a/2+dx*b/2); path.close()
            }
            "cone" -> { path.moveTo(x,y); path.arcTo(x-a,y-a,x+a,y+a,((h.angle-h.b/2)*180/PI).toFloat(),(h.b*180/PI).toFloat(),false); path.close() }
            "knock" -> path.addRect(0f,0f,600f,334f,Path.Direction.CW)
        }
        return path
    }
    private fun stroke(color: Int,width: Float,alpha: Int=255) { p.color=color; p.alpha=alpha; p.style=Paint.Style.STROKE; p.strokeWidth=width }
    private fun fill(color: Int,alpha: Int=255) { p.color=color; p.alpha=alpha; p.style=Paint.Style.FILL }
    private fun line(c: Canvas,x: Double,y: Double,xx: Double,yy: Double)=c.drawLine(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),p)
    private fun element(id: String)=when(id) {
        "thor","tanngrisnir" -> "thunder"
        "skadi","ullr","thrym","thjazi" -> "ice"
        "aegir","ran","njord","jormungandr" -> "sea"
        "surtr","skoll" -> "fire"
        "hugin","munin","hraesvelgr" -> "wind"
        "hel","garmr","nidhogg","fenrir","hati" -> "dark"
        "hrungnir","ratatoskr","dainn" -> "stone"
        else -> "rune"
    }
    fun telegraph(c: Canvas,h: Hazard) {
        if(h.resolved) return
        val x=h.x.toFloat(); val y=h.y.toFloat(); val a=h.a.toFloat()
        val progress=(h.time/h.delay).coerceIn(.0,1.0).toFloat()
        if(h.shape=="knock") {
            stroke(Ink.light,2f)
            for(i in 0..3) {
                val radius=20f+((progress*70+i*27)%108)
                c.drawCircle(x,y,radius,p)
                for(k in 0..7) {
                    val angle=k*PI/4; val xx=x+cos(angle)*radius; val yy=y+sin(angle)*radius
                    line(c,xx,yy,xx-cos(angle-.5)*9,yy-sin(angle-.5)*9)
                    line(c,xx,yy,xx-cos(angle+.5)*9,yy-sin(angle+.5)*9)
                }
            }
            PixelFont.draw(c,"CENTER",x,y-9,1.5f,Ink.light,true)
            return
        }
        val area=shape(h)
        c.save(); c.clipPath(area)
        fill(Ink.dark,125); c.drawPath(area,p)
        stroke(Ink.mid,2f)
        val offset=(h.time*18%14).toFloat()
        for(i in -350..600 step 14) c.drawLine(i+offset,0f,i+340f+offset,340f,p)
        // The approaching cast is a moving inset; the outer edge remains fixed and exact.
        stroke(Ink.light,1f,125)
        if(h.shape=="circle") c.drawCircle(x,y,a*(1-progress),p)
        if(h.shape=="line") {
            c.save(); c.rotate((h.angle*180/PI).toFloat(),x,y)
            val half=h.b.toFloat()/2
            for(i in -300..300 step 45) {
                val xx=x+i+(progress*40)
                c.drawLine(xx-9,y-half*.5f,xx,y,p); c.drawLine(xx,y,xx-9,y+half*.5f,p)
            }
            c.restore()
        }
        c.restore()
        stroke(Ink.dark,5f); c.drawPath(area,p)
        stroke(Ink.light,if(progress>.8f) 3f else 2f); c.drawPath(area,p)
        if(h.shape in arrayOf("circle","ring","safe","tower")) {
            stroke(Ink.light,4f)
            c.drawArc(x-a+5,y-a+5,x+a-5,y+a-5,-90f,progress*360,false,p)
            // Four small exterior brackets show the exact circle even over another effect.
            for(i in 0..3) c.drawArc(x-a-4,y-a-4,x+a+4,y+a+4,i*90f-6,12f,false,p)
        }
        if(h.shape=="safe" || h.shape=="tower") {
            fill(Ink.deep); c.drawCircle(x,y,a-9,p)
            stroke(Ink.light,2f); c.drawCircle(x,y,20f+sin(progress*PI).toFloat()*4,p)
            c.drawLine(x-12,y,x,y-12,p); c.drawLine(x,y-12,x+12,y,p)
            c.drawLine(x+12,y,x,y+12,p); c.drawLine(x,y+12,x-12,y,p)
            PixelFont.draw(c,if(h.shape=="tower") "IN" else "SAFE",x,y+26,1.2f,Ink.light,true)
        }
    }
    fun impact(c: Canvas,impact: BattleImpact,bossId: String) {
        val h=impact.hazard; val u=impact.progress; val fade=(1-u).pow(1.3)
        val x=h.x; val y=h.y
        val area=shape(h)
        c.save(); c.clipPath(area)
        // No full-screen white strobe: energy stays inside this attack's damaging footprint.
        fill(Ink.light,((if(h.multiplier>1) 125 else 95)*fade).toInt()); c.drawPath(area,p)
        stroke(Ink.light,(7*(1-u)+1).toFloat(),(210*fade).toInt()); c.drawPath(area,p)
        if(h.shape=="line") {
            c.save(); c.rotate((h.angle*180/PI).toFloat(),x.toFloat(),y.toFloat())
            for(i in -1..1) {
                stroke(if(i==0) Ink.light else Ink.mid,((if(i==0) 12 else 4)*(1-u)+1).toFloat(),(255*fade).toInt())
                c.drawLine((x-h.a/2).toFloat(),(y+i*h.b*.3).toFloat(),(x+h.a/2).toFloat(),(y+i*h.b*.3).toFloat(),p)
            }
            c.restore()
        }
        if(h.shape=="cone") {
            stroke(Ink.light,3f,(255*fade).toInt())
            for(i in 0..10) {
                val angle=h.angle-h.b/2+h.b*i/10
                val near=15+u*70; val far=min(h.a,near+90+u*240)
                line(c,x+cos(angle)*near,y+sin(angle)*near,x+cos(angle)*far,y+sin(angle)*far)
            }
        }
        if(h.shape in arrayOf("circle","knock","ring","safe","tower")) {
            val radius=if(h.shape=="circle") h.a else 390.0
            for(i in 0..2) {
                val r=(u*radius*1.4-i*21).coerceAtLeast(1.0)
                stroke(if(i==1) Ink.dark else Ink.light,(5-i).toFloat(),(245*fade).toInt())
                c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),p)
            }
        }
        val element=element(bossId)
        // Deterministic sparks distributed through the real footprint, capped per impact.
        val count=if(h.multiplier>1) 32 else 22
        for(i in 0 until count) {
            var xx=((i*137+abs(x).toInt()*3)%600).toDouble()
            var yy=((i*83+abs(y).toInt()*5)%334).toDouble()
            if(h.shape=="circle") {
                val angle=i*2.39996; val radius=h.a*sqrt((i+.5)/count)
                xx=x+cos(angle)*radius; yy=y+sin(angle)*radius
            }
            if(!h.contains(xx,yy,0.0)) continue
            val angle=i*2.39996
            xx+=cos(angle)*u*27; yy+=sin(angle)*u*18-u*15
            val size=(3+(i%4)*2)*(1-u)+1
            when(element) {
                "thunder" -> {
                    stroke(Ink.dark,6f,(200*fade).toInt()); lightning(c,xx,yy,u,i)
                    stroke(Ink.light,2f,(255*fade).toInt()); lightning(c,xx,yy,u,i)
                }
                "ice" -> {
                    fill(Ink.light,(240*fade).toInt()); bolt.reset()
                    bolt.moveTo(xx.toFloat(),(yy-size*5).toFloat()); bolt.lineTo((xx+size).toFloat(),yy.toFloat())
                    bolt.lineTo(xx.toFloat(),(yy+size).toFloat()); bolt.lineTo((xx-size).toFloat(),yy.toFloat()); bolt.close(); c.drawPath(bolt,p)
                    stroke(Ink.dark,1f,(220*fade).toInt()); line(c,xx,yy-size*4,xx,yy)
                }
                "sea" -> {
                    stroke(Ink.light,3f,(245*fade).toInt())
                    c.drawArc((xx-size*3).toFloat(),(yy-size*2).toFloat(),(xx+size*3).toFloat(),(yy+size*2).toFloat(),190f,150f,false,p)
                    fill(Ink.light,(200*fade).toInt()); c.drawRect(xx.toFloat(),(yy-size*3).toFloat(),(xx+3).toFloat(),(yy-size*3+3).toFloat(),p)
                }
                "fire" -> {
                    fill(if(i%2==0) Ink.light else Ink.dark,(240*fade).toInt())
                    c.drawRect((xx-size).toFloat(),(yy-size*(3+i%3)).toFloat(),(xx+size).toFloat(),yy.toFloat(),p)
                    fill(Ink.light,(220*fade).toInt()); c.drawRect(xx.toFloat(),(yy-size*5-9).toFloat(),(xx+3).toFloat(),(yy-size*5-6).toFloat(),p)
                }
                "wind" -> {
                    stroke(Ink.light,2f,(245*fade).toInt()); line(c,xx-size*4,yy+size*2,xx+size*4,yy-size*2)
                    line(c,xx-size*2,yy+size*3,xx+size*4,yy)
                }
                "dark" -> {
                    stroke(Ink.dark,6f,(235*fade).toInt()); c.drawCircle(xx.toFloat(),yy.toFloat(),(size*2).toFloat(),p)
                    stroke(Ink.light,2f,(230*fade).toInt()); c.drawArc((xx-size*2).toFloat(),(yy-size*2).toFloat(),(xx+size*2).toFloat(),(yy+size*2).toFloat(),(i*43).toFloat(),235f,false,p)
                }
                "rune" -> {
                    stroke(Ink.light,2f,(240*fade).toInt()); line(c,xx,yy-size*3,xx,yy+size*2)
                    line(c,xx,yy-size*3,xx+size*2,yy-size); line(c,xx+size*2,yy-size,xx,yy)
                }
                else -> {
                    c.save(); c.rotate((i*39+u*80).toFloat(),xx.toFloat(),yy.toFloat())
                    fill(Ink.dark,(220*fade).toInt()); c.drawRect((xx-size-2).toFloat(),(yy-size-2).toFloat(),(xx+size+2).toFloat(),(yy+size+2).toFloat(),p)
                    fill(Ink.light,(245*fade).toInt()); c.drawRect((xx-size).toFloat(),(yy-size).toFloat(),(xx+size).toFloat(),(yy+size).toFloat(),p); c.restore()
                }
            }
        }
        c.restore()
        p.alpha=255
    }
    private fun lightning(c: Canvas,x: Double,y: Double,u: Double,seed: Int) {
        bolt.reset(); bolt.moveTo(x.toFloat(),(y-110*(1-u)-20).toFloat())
        for(j in 1..5) bolt.lineTo((x+sin(seed*3.0+j*4.0)*12*(1-u)).toFloat(),(y-(5-j)*22*(1-u)).toFloat())
        c.drawPath(bolt,p)
    }
    fun charge(c: Canvas,x: Double,y: Double,progress: Double,strong: Boolean) {
        val u=progress.coerceIn(.0,1.0)
        stroke(Ink.light,if(strong) 3f else 2f,(100+u*130).toInt())
        c.drawOval((x-42-u*9).toFloat(),(y-14).toFloat(),(x+42+u*9).toFloat(),(y+17).toFloat(),p)
        for(i in 0..7) {
            val a=i*PI/4+u*2; val radius=70-u*36
            val xx=x+cos(a)*radius; val yy=y-26+sin(a)*radius*.6
            line(c,xx,yy,xx+cos(a)*8,yy+sin(a)*8)
        }
    }
}
