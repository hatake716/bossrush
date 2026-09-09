package io.github.hatake716.bossrush

import android.graphics.*
import kotlin.math.*

/** Purely visual aftermath, independent of damage timing and collision geometry. */
data class BattleImpact(val hazard: Hazard,var age: Double=0.0) {
    val lifetime get()=if(hazard.ultimate) 1.05 else .60
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
    fun telegraph(c: Canvas,h: Hazard,bossId: String) {
        val colors=palette(h.ultimate,bossId)
        if(h.resolved) return
        val x=h.x.toFloat(); val y=h.y.toFloat(); val a=h.a.toFloat()
        val progress=(h.time/h.delay).coerceIn(.0,1.0).toFloat()
        if(h.shape=="knock") {
            stroke(colors.core,2f)
            for(i in 0..3) {
                val radius=20f+((progress*70+i*27)%108)
                c.drawCircle(x,y,radius,p)
                for(k in 0..7) {
                    val angle=k*PI/4; val xx=x+cos(angle)*radius; val yy=y+sin(angle)*radius
                    line(c,xx,yy,xx-cos(angle-.5)*9,yy-sin(angle-.5)*9)
                    line(c,xx,yy,xx-cos(angle+.5)*9,yy-sin(angle+.5)*9)
                }
            }
            PixelFont.draw(c,"CENTER",x,y-9,1.5f,colors.core,true)
            return
        }
        val area=shape(h)
        c.save(); c.clipPath(area)
        fill(colors.shadow,125); c.drawPath(area,p)
        stroke(colors.energy,2f)
        val offset=(h.time*18%14).toFloat()
        for(i in -350..600 step 14) c.drawLine(i+offset,0f,i+340f+offset,340f,p)
        // The approaching cast is a moving inset; the outer edge remains fixed and exact.
        stroke(colors.core,1f,125)
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
        stroke(colors.shadow,5f); c.drawPath(area,p)
        stroke(colors.core,if(progress>.8f) 3f else 2f); c.drawPath(area,p)
        if(h.shape in arrayOf("circle","ring","safe","tower")) {
            stroke(colors.core,4f)
            c.drawArc(x-a+5,y-a+5,x+a-5,y+a-5,-90f,progress*360,false,p)
            // Four small exterior brackets show the exact circle even over another effect.
            for(i in 0..3) c.drawArc(x-a-4,y-a-4,x+a+4,y+a+4,i*90f-6,12f,false,p)
        }
        if(h.shape=="safe" || h.shape=="tower") {
            fill(Ink.deep); c.drawCircle(x,y,a-9,p)
            stroke(colors.core,2f); c.drawCircle(x,y,20f+sin(progress*PI).toFloat()*4,p)
            c.drawLine(x-12,y,x,y-12,p); c.drawLine(x,y-12,x+12,y,p)
            c.drawLine(x+12,y,x,y+12,p); c.drawLine(x,y+12,x-12,y,p)
            PixelFont.draw(c,if(h.shape=="tower") "IN" else "SAFE",x,y+26,1.2f,colors.core,true)
        }
    }
    fun impact(c: Canvas,impact: BattleImpact,bossId: String) {
        val h=impact.hazard; val colors=palette(h.ultimate,bossId); val u=impact.progress; val fade=(1-u).pow(1.3)
        val x=h.x; val y=h.y
        val area=shape(h)
        c.save(); c.clipPath(area)
        // No full-screen white strobe: energy stays inside this attack's damaging footprint.
        fill(if(h.ultimate) colors.energy else colors.core,((if(h.ultimate) 170 else 95)*fade).toInt()); c.drawPath(area,p)
        stroke(colors.core,(7*(1-u)+1).toFloat(),(210*fade).toInt()); c.drawPath(area,p)
        if(h.shape=="line") {
            c.save(); c.rotate((h.angle*180/PI).toFloat(),x.toFloat(),y.toFloat())
            for(i in -1..1) {
                stroke(if(i==0) colors.core else colors.energy,((if(i==0) 12 else 4)*(1-u)+1).toFloat(),(255*fade).toInt())
                c.drawLine((x-h.a/2).toFloat(),(y+i*h.b*.3).toFloat(),(x+h.a/2).toFloat(),(y+i*h.b*.3).toFloat(),p)
            }
            c.restore()
        }
        if(h.shape=="cone") {
            stroke(colors.core,3f,(255*fade).toInt())
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
                stroke(if(i==1) colors.shadow else colors.core,(5-i).toFloat(),(245*fade).toInt())
                c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),p)
            }
        }
        if(h.ultimate) {
            // Saturated energy columns and orbiting runes remain clipped to danger.
            for(i in 0..11) {
                val xx=(i*137+abs(x).toInt()*3)%600
                val yy=(i*73+abs(y).toInt()*5)%334
                val height=(42+80*(1-u)).toFloat()
                stroke(if(i%2==0) colors.energy else colors.accent,9f,(190*fade).toInt())
                c.drawLine(xx.toFloat(),yy.toFloat(),xx.toFloat(),yy-height,p)
                stroke(colors.core,2f,(250*fade).toInt())
                c.drawLine(xx.toFloat(),yy.toFloat(),xx.toFloat(),yy-height*.8f,p)
                stroke(colors.accent,2f,(230*fade).toInt())
                c.drawCircle(xx.toFloat(),yy-height*.65f,9f+u.toFloat()*17,p)
            }
            stroke(colors.accent,9f,(190*fade).toInt()); c.drawPath(area,p)
            stroke(colors.core,2f,(255*fade).toInt()); c.drawPath(area,p)
        }
        val element=element(bossId)
        // Deterministic sparks distributed through the real footprint, capped per impact.
        val count=if(h.ultimate) 48 else 22
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
                    stroke(colors.accent,7f,(240*fade).toInt()); lightning(c,xx,yy,u,i)
                    stroke(colors.core,2f,(255*fade).toInt()); lightning(c,xx,yy,u,i)
                }
                "ice" -> {
                    fill(if(h.ultimate) colors.energy else colors.core,(240*fade).toInt()); bolt.reset()
                    bolt.moveTo(xx.toFloat(),(yy-size*5).toFloat()); bolt.lineTo((xx+size).toFloat(),yy.toFloat())
                    bolt.lineTo(xx.toFloat(),(yy+size).toFloat()); bolt.lineTo((xx-size).toFloat(),yy.toFloat()); bolt.close(); c.drawPath(bolt,p)
                    stroke(colors.shadow,1f,(220*fade).toInt()); line(c,xx,yy-size*4,xx,yy)
                }
                "sea" -> {
                    stroke(if(h.ultimate) colors.energy else colors.core,3f,(245*fade).toInt())
                    c.drawArc((xx-size*3).toFloat(),(yy-size*2).toFloat(),(xx+size*3).toFloat(),(yy+size*2).toFloat(),190f,150f,false,p)
                    fill(colors.core,(200*fade).toInt()); c.drawRect(xx.toFloat(),(yy-size*3).toFloat(),(xx+3).toFloat(),(yy-size*3+3).toFloat(),p)
                }
                "fire" -> {
                    fill(if(i%2==0) colors.energy else colors.accent,(240*fade).toInt())
                    c.drawRect((xx-size).toFloat(),(yy-size*(3+i%3)).toFloat(),(xx+size).toFloat(),yy.toFloat(),p)
                    fill(colors.core,(220*fade).toInt()); c.drawRect(xx.toFloat(),(yy-size*5-9).toFloat(),(xx+3).toFloat(),(yy-size*5-6).toFloat(),p)
                }
                "wind" -> {
                    stroke(colors.core,2f,(245*fade).toInt()); line(c,xx-size*4,yy+size*2,xx+size*4,yy-size*2)
                    line(c,xx-size*2,yy+size*3,xx+size*4,yy)
                }
                "dark" -> {
                    stroke(if(h.ultimate) colors.accent else colors.shadow,6f,(235*fade).toInt()); c.drawCircle(xx.toFloat(),yy.toFloat(),(size*2).toFloat(),p)
                    stroke(colors.core,2f,(230*fade).toInt()); c.drawArc((xx-size*2).toFloat(),(yy-size*2).toFloat(),(xx+size*2).toFloat(),(yy+size*2).toFloat(),(i*43).toFloat(),235f,false,p)
                }
                "rune" -> {
                    stroke(colors.core,2f,(240*fade).toInt()); line(c,xx,yy-size*3,xx,yy+size*2)
                    line(c,xx,yy-size*3,xx+size*2,yy-size); line(c,xx+size*2,yy-size,xx,yy)
                }
                else -> {
                    c.save(); c.rotate((i*39+u*80).toFloat(),xx.toFloat(),yy.toFloat())
                    fill(colors.shadow,(220*fade).toInt()); c.drawRect((xx-size-2).toFloat(),(yy-size-2).toFloat(),(xx+size+2).toFloat(),(yy+size+2).toFloat(),p)
                    fill(colors.core,(245*fade).toInt()); c.drawRect((xx-size).toFloat(),(yy-size).toFloat(),(xx+size).toFloat(),(yy+size).toFloat(),p); c.restore()
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
    fun charge(c: Canvas,x: Double,y: Double,progress: Double,strong: Boolean,bossId: String) {
        val colors=palette(strong,bossId)
        val u=progress.coerceIn(.0,1.0)
        stroke(if(strong) colors.energy else Ink.light,if(strong) 3f else 2f,(100+u*130).toInt())
        c.drawOval((x-42-u*9).toFloat(),(y-14).toFloat(),(x+42+u*9).toFloat(),(y+17).toFloat(),p)
        for(i in 0..7) {
            val a=i*PI/4+u*2; val radius=70-u*36
            val xx=x+cos(a)*radius; val yy=y-26+sin(a)*radius*.6
            line(c,xx,yy,xx+cos(a)*8,yy+sin(a)*8)
        }
    }
    private fun palette(ultimate: Boolean,bossId: String)=if(ultimate) UltimateColors.forBoss(bossId)
        else CombatPalette(Ink.mid,Ink.light,Ink.light,Ink.dark)

    fun cutin(c: Canvas,bossId: String,left: Float,top: Float,width: Float,height: Float,age: Double) {
        val colors=UltimateColors.forBoss(bossId)
        p.style=Paint.Style.FILL; p.alpha=255
        p.shader=LinearGradient(left,top,left+width,top+height,
            intArrayOf(colors.shadow,colors.energy,colors.accent,colors.shadow),floatArrayOf(0f,.28f,.76f,1f),Shader.TileMode.CLAMP)
        c.drawRect(left,top,left+width,top+height,p); p.shader=null
        c.save(); c.clipRect(left,top,left+width,top+height)
        val cx=left+width*.31f; val cy=top+height*.52f
        for(i in 0..27) {
            val angle=i*PI/14+age*.20
            val near=65+sin(age*1.7+i)*13
            stroke(if(i%3==0) colors.core else colors.accent,if(i%3==0) 3f else 6f,185)
            line(c,cx+cos(angle)*near,cy+sin(angle)*near,cx+cos(angle)*width,cy+sin(angle)*width)
        }
        for(i in 0..4) {
            val r=43f+i*23f+(age*23%23).toFloat()
            stroke(if(i%2==0) colors.core else colors.energy,2f,185)
            c.drawCircle(cx,cy,r,p)
        }
        for(i in 0..35) {
            val xx=left+((i*113+age*80)%width).toFloat(); val yy=top+(i*67%height)
            fill(if(i%2==0) colors.core else colors.energy,205)
            c.drawRect(xx,yy,xx+3+i%4*2,yy+3,p)
        }
        c.restore(); p.alpha=255
    }

}
