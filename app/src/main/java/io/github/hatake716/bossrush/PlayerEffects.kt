package io.github.hatake716.bossrush

import android.graphics.*
import kotlin.math.*

/** Green pixel effects. Ground effects precede enemy warnings and the player's foot marker. */
class PlayerEffects {
    private val p=Paint().apply { isAntiAlias=false; strokeCap=Paint.Cap.SQUARE }
    private val path=Path()
    private fun fill(color: Int=Ink.light,alpha: Int=255) { p.color=color; p.alpha=alpha.coerceIn(0,255); p.style=Paint.Style.FILL }
    private fun stroke(width: Double=2.0,alpha: Int=255,color: Int=Ink.light) { fill(color,alpha); p.style=Paint.Style.STROKE; p.strokeWidth=width.toFloat() }
    private fun line(c: Canvas,x: Double,y: Double,xx: Double,yy: Double)=c.drawLine(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),p)
    private fun square(c: Canvas,x: Double,y: Double,size: Double) {
        val xx=floor(x/2)*2; val yy=floor(y/2)*2; val s=max(2.0,round(size/2)*2)
        c.drawRect(xx.toFloat(),yy.toFloat(),(xx+s).toFloat(),(yy+s).toFloat(),p)
    }
    private fun ring(c: Canvas,x: Double,y: Double,r: Double,flat: Double=1.0) =
        c.drawOval((x-r).toFloat(),(y-r*flat).toFloat(),(x+r).toFloat(),(y+r*flat).toFloat(),p)
    private fun plus(c: Canvas,x: Double,y: Double,r: Double) { line(c,x-r,y,x+r,y); line(c,x,y-r,x,y+r) }
    private fun diamond(c: Canvas,x: Double,y: Double,w: Double,h: Double) {
        path.reset(); path.moveTo(x.toFloat(),(y-h).toFloat()); path.lineTo((x+w).toFloat(),y.toFloat())
        path.lineTo(x.toFloat(),(y+h*.3).toFloat()); path.lineTo((x-w).toFloat(),y.toFloat()); path.close(); c.drawPath(path,p)
    }
    private fun polygon(c: Canvas,x: Double,y: Double,r: Double,n: Int,angle: Double,flat: Double=1.0) {
        path.reset()
        repeat(n) { i ->
            val a=angle+i*2*PI/n; val xx=(x+cos(a)*r).toFloat(); val yy=(y+sin(a)*r*flat).toFloat()
            if(i==0) path.moveTo(xx,yy) else path.lineTo(xx,yy)
        }
        path.close(); c.drawPath(path,p)
    }

    fun ground(c: Canvas,e: GameEngine) {
        val x=e.player.x; val y=e.player.y; val time=e.elapsed
        if((e.buffs["shield"] ?: 0.0)>0) aura(c,PlayerEffectKind.SHIELD,x,y,e.levels[1],time)
        if((e.buffs["focus"] ?: 0.0)>0) aura(c,PlayerEffectKind.FOCUS,x,y,e.levels[2],time)
        if((e.buffs["speed"] ?: 0.0)>0) wind(c,x,y,e.levels[2],time,e.player.facing)
        if((e.buffs["vanish"] ?: 0.0)>0) {
            wind(c,x,y,e.levels[3],time,e.player.facing,100)
            stroke(1.0+Skills.progress(e.levels[3]),130,Color.rgb(168,225,222)); ring(c,x,y+3,22.0+12*Skills.progress(e.levels[3]),.4)
        }
        for(s in e.summons) {
            val t=Skills.progress(s.level); val r=13+9*t
            stroke(1.0+t,110+(t*70).toInt()); ring(c,s.x,s.y+2,r,.42)
            repeat(2+(6*t).roundToInt()) { i ->
                val a=time*.7+i*2.39996
                fill(Ink.paper,100+(t*90).toInt()); square(c,s.x+cos(a)*r,s.y+sin(a)*r*.42,2+2*t)
            }
        }
        e.iceMarks.forEach { target(c,it) }
        e.playerEffects.forEach { impact(c,it) }
        p.alpha=255
    }

    fun target(c: Canvas,m: IceMark) {
        val t=Skills.progress(m.level); val u=(1-m.time/.75).coerceIn(0.0,1.0)
        stroke(1.0+t,170)
        // Eight separated frost brackets cannot be confused with a safe-zone circle.
        repeat(8) { i ->
            val a=i*PI/4; val r=m.radius
            line(c,m.x+cos(a)*(r-5),m.y+sin(a)*(r-5),m.x+cos(a)*r,m.y+sin(a)*r)
        }
        stroke(1.0+t,150)
        polygon(c,m.x,m.y,m.radius*(.8-.45*u),4,u*PI/2)
        repeat(1+(4*t).roundToInt()) { i ->
            val a=i*2.39996; val r=if(i==0) 0.0 else m.radius*.5
            fill(Ink.paper,180); diamond(c,m.x+cos(a)*r,m.y+sin(a)*r,3+3*t,8+u*(8+14*t))
        }
    }

    fun impact(c: Canvas,e: PlayerEffect) {
        val t=e.strength; val u=e.progress; val fade=(1-u).pow(.65); val alpha=(245*fade).toInt()
        val x=e.x; val y=e.y; val r=e.radius
        when(e.kind) {
            PlayerEffectKind.LIMIT_SLASH -> {
                val gold=Color.rgb(250,226,147)
                val length=r*(.4+.6*u)
                stroke(6.0+5*t,alpha/3,Color.rgb(149,128,74))
                line(c,x-cos(e.angle)*length,y-sin(e.angle)*length*.7,x+cos(e.angle)*length,y+sin(e.angle)*length*.7)
                stroke(2.0+3*t,alpha,gold)
                line(c,x-cos(e.angle)*length,y-sin(e.angle)*length*.7,x+cos(e.angle)*length,y+sin(e.angle)*length*.7)
                repeat(5+(13*t).roundToInt()) { i ->
                    val a=e.angle+i*2.39996; val rr=length*(.35+(i%4)*.2)
                    fill(if(i%2==0) gold else Ink.paper,alpha); square(c,x+cos(a)*rr,y+sin(a)*rr*.7,2+4*t)
                }
                stroke(2.0+2*t,alpha,gold); diamond(c,x,y,8+14*t,18+14*t)
            }
            PlayerEffectKind.LIMIT_FLARE -> {
                val gold=Color.rgb(255,221,144); val fire=Color.rgb(199,116,80)
                fill(fire,(16*fade).toInt()); c.drawRect(0f,0f,600f,334f,p)
                val count=9+(16*t).roundToInt()
                repeat(count) { i ->
                    val xx=25.0+((i*131+e.angle.toInt()*43)%550)
                    val yy=28.0+((i*73+e.angle.toInt()*29)%275)
                    val rr=(23+28*t)*(.3+.7*u)
                    stroke(2.0+3*t,alpha/2,fire); polygon(c,xx,yy,rr*1.5,8,u)
                    stroke(2.0+2*t,alpha,gold); polygon(c,xx,yy,rr,8,-u)
                    fill(gold,alpha); diamond(c,xx,yy,rr*.35,rr*1.2*(1-u*.7))
                    repeat(4) { j ->
                        val a=j*PI/2+i; fill(Ink.paper,alpha); square(c,xx+cos(a)*rr*1.5,yy+sin(a)*rr*1.5,3+3*t)
                    }
                }
                stroke(2.0+3*t,alpha/2,gold); ring(c,300.0,167.0,65+u*300,.56)
            }
            PlayerEffectKind.LIMIT_SUMMON -> {
                val green=Color.rgb(199,237,163); val rr=r*(.65+.35*u)
                stroke(2.0+2*t,alpha,green); polygon(c,x,y,rr,5,e.angle,.5)
                stroke(1.0+2*t,alpha/2,green); ring(c,x,y,rr*1.2,.5)
                repeat(5+(10*t).roundToInt()) { i ->
                    val a=i*2.39996; val yy=y+sin(a)*rr*.45-u*(25+20*t)
                    fill(green,alpha); square(c,x+cos(a)*rr,yy,3+4*t)
                    stroke(1.0+t,alpha/2,green); line(c,x+cos(a)*rr,yy+12,x+cos(a)*rr,yy)
                }
            }
            PlayerEffectKind.LIMIT_VANISH -> {
                val mist=Color.rgb(162,224,224); val rr=r*(.6+u*.6)
                repeat(6+(18*t).roundToInt()) { i ->
                    val a=i*2.39996+u*2; val distance=rr*(.25+(i%5)*.18)
                    fill(if(i%2==0) mist else Ink.mid,alpha/2); square(c,x+cos(a)*distance,y-15+sin(a)*distance*.7-u*18,4+6*t)
                }
                stroke(1.0+2*t,alpha,mist); polygon(c,x,y-15,rr*.8,6,u)
            }
            PlayerEffectKind.SWORD,PlayerEffectKind.KNIFE -> {
                val span=if(e.kind==PlayerEffectKind.SWORD) 125.0 else 78.0
                val direction=e.angle*180/PI
                val layers=1+(3*t).roundToInt()
                repeat(layers) { i ->
                    val rr=r*(1-i*.095)*(.9+.1*sin(u*PI))
                    val start=direction-span/2+u*span*.35+i*7
                    stroke(5.0+4*t,alpha/2,Ink.deep)
                    c.drawArc((x-rr).toFloat(),(y-rr).toFloat(),(x+rr).toFloat(),(y+rr).toFloat(),start.toFloat(),(span*(1-u*.35)).toFloat(),false,p)
                    stroke((2.0+3*t)*(1-i*.13),alpha,if(i%2==0) Ink.light else Ink.paper)
                    c.drawArc((x-rr).toFloat(),(y-rr).toFloat(),(x+rr).toFloat(),(y+rr).toFloat(),start.toFloat(),(span*(1-u*.35)).toFloat(),false,p)
                }
                repeat(4+(14*t).roundToInt()) { i ->
                    val a=e.angle+(i%7-3)*.14+u*.5; val rr=r*(.58+.4*((i*17%23)/23.0))
                    fill(if(i%2==0) Ink.light else Ink.mid,alpha)
                    square(c,x+cos(a)*rr,y+sin(a)*rr,2+3*t)
                }
            }
            PlayerEffectKind.HANIWA,PlayerEffectKind.GIANT -> {
                val xx=x+cos(e.angle)*r*.46; val yy=y+sin(e.angle)*r*.46
                val wave=r*.52*(.25+.75*u)
                repeat(1+(2*t).roundToInt()) { i ->
                    stroke(3.0+2*t,alpha/(i+1)); ring(c,xx,yy,max(2.0,wave-i*7),.65)
                }
                val count=6+(14*t).roundToInt()
                repeat(count) { i ->
                    val a=i*2.39996; val distance=wave*(.4+(i%4)*.13)
                    val px=xx+cos(a)*distance; val py=yy+sin(a)*distance*.7-u*(10+12*t)
                    fill(if(i%2==0) Ink.light else Ink.mid,alpha); square(c,px,py,3+5*t)
                    if(e.kind==PlayerEffectKind.GIANT) { stroke(1.0+t,alpha); line(c,xx+cos(a)*wave*.5,yy+sin(a)*wave*.5,xx+cos(a)*wave,yy+sin(a)*wave) }
                }
                stroke(3.0+3*t,alpha); plus(c,xx,yy-8,r*.18*(1-u))
            }
            PlayerEffectKind.FIRE -> {
                fill(Ink.mid,(50*fade).toInt()); ring(c,x,y,r)
                stroke(2.0+3*t,alpha); ring(c,x,y,r*(.65+.35*u))
                val count=5+(12*t).roundToInt()
                repeat(count) { i ->
                    val a=i*2.39996; val distance=r*.8*sqrt((i+.5)/count)
                    val xx=x+cos(a)*distance; val yy=y+sin(a)*distance*.8
                    val height=(14+31*t)*(1-u)*(.7+(i%3)*.3)
                    val width=3+4*t
                    fill(Ink.mid,alpha); square(c,xx-width,yy-height,width*2)
                    c.drawRect((xx-width).toFloat(),(yy-height).toFloat(),(xx+width).toFloat(),yy.toFloat(),p)
                    fill(Ink.light,alpha); c.drawRect(xx.toFloat(),(yy-height*.72).toFloat(),(xx+width*.6).toFloat(),yy.toFloat(),p)
                    square(c,xx+sin(i+u*4)*4,yy-height-6-u*15,2+2*t)
                }
            }
            PlayerEffectKind.ICE -> {
                stroke(2.0+2*t,alpha); polygon(c,x,y,r*(.8+.2*u),6,PI/6)
                val count=3+(8*t).roundToInt()
                repeat(count) { i ->
                    val a=i*2.39996; val distance=if(i==0) 0.0 else r*.75*sqrt(i.toDouble()/count)
                    val xx=x+cos(a)*distance; val yy=y+sin(a)*distance*.7
                    val height=(20+43*t)*(1-u*.65)*(if(i==0) 1.25 else .65+(i%3)*.13)
                    fill(Ink.mid,alpha); diamond(c,xx,yy,5+6*t,height)
                    stroke(1.0+1.5*t,alpha); diamond(c,xx,yy,5+6*t,height)
                    line(c,xx,yy-height,xx,yy+height*.15)
                    fill(Ink.light,alpha); square(c,xx+9*sin(a),yy-height-u*14-5,2+3*t)
                }
            }
            PlayerEffectKind.ARROW -> {
                val length=(12+22*t)*(1-u*.6)
                stroke(1.5+2*t,alpha)
                repeat(4+(6*t).roundToInt()) { i ->
                    val a=i*2.39996; line(c,x+cos(a)*3,y+sin(a)*3,x+cos(a)*length,y+sin(a)*length)
                }
            }
            PlayerEffectKind.STEAL -> {
                repeat(3+(9*t).roundToInt()) { i ->
                    val v=(u-i*.045).coerceIn(0.0,1.0)
                    val xx=x+cos(e.angle)*r*v; val yy=y+sin(e.angle)*r*v-sin(v*PI)*(16+16*t)
                    stroke(1.0+t,alpha); diamond(c,xx,yy,3+3*t,5+4*t)
                }
            }
            PlayerEffectKind.SPEED -> wind(c,x,y,e.level,e.age*3,e.angle,alpha)
            else -> {
                val expanded=r*(.55+.45*u)
                stroke(1.5+2*t,alpha); ring(c,x,y,expanded,.6)
                if(t>0) { stroke(1.0+t,(alpha*.7*t).toInt()); polygon(c,x,y,expanded*.8,6,u, .6) }
                repeat(3+(9*t).roundToInt()) { i ->
                    val a=i*2.39996+u*.5; val xx=x+cos(a)*expanded; val yy=y+sin(a)*expanded*.6-u*(14+17*t)
                    stroke(1.0+t,alpha)
                    when(e.kind) {
                        PlayerEffectKind.HEAL,PlayerEffectKind.SUMMON_RABBIT -> plus(c,xx,yy,2+3*t)
                        PlayerEffectKind.SUMMON_GIANT,PlayerEffectKind.SUMMON_HANIWA -> { fill(Ink.paper,alpha); square(c,xx,yy,3+4*t) }
                        else -> diamond(c,xx,yy,2+3*t,5+5*t)
                    }
                }
            }
        }
        p.alpha=255; p.style=Paint.Style.FILL
    }

    private fun aura(c: Canvas,kind: PlayerEffectKind,x: Double,y: Double,level: Int,time: Double) {
        val t=Skills.progress(level); val r=Skills.auraRadius(level)
        if(kind==PlayerEffectKind.SHIELD) {
            stroke(1.5+1.5*t,150); polygon(c,x,y-13,r,6,-PI/2)
            if(t>0) { stroke(1.0+t,(150*t).toInt()); polygon(c,x,y-13,r*.82,6,-PI/2) }
            repeat(2+(4*t).roundToInt()) { i ->
                val a=i*PI/3+time*.22
                stroke(1.0+t,170); diamond(c,x+cos(a)*r,y-13+sin(a)*r,2+2*t,5+5*t)
            }
        } else {
            stroke(1.0+t,125); ring(c,x,y+3,r,.4)
            repeat(3+(7*t).roundToInt()) { i ->
                val a=i*2.39996+time*.9; val yy=y+sin(a)*r*.4-((time*12+i*8)%30)
                stroke(1.0+t,165)
                diamond(c,x+cos(a)*r,yy,2+2*t,4+5*t)
            }
        }
    }

    private fun wind(c: Canvas,x: Double,y: Double,level: Int,time: Double,angle: Double,alpha: Int=165) {
        val t=Skills.progress(level)
        c.save(); c.translate(x.toFloat(),(y-12).toFloat()); c.rotate((angle*180/PI).toFloat())
        repeat(2+(4*t).roundToInt()) { i ->
            val yy=(i-(1+2*t))*6; val offset=(time*40+i*7)%16
            stroke(1.0+1.5*t,alpha); line(c,-10-offset,yy,-30-offset-23*t,yy+3)
            fill(Ink.mid,alpha); square(c,-39-offset-23*t,yy+4,2+2*t)
        }
        c.restore()
    }

    fun projectile(c: Canvas,pr: Projectile) {
        val t=Skills.progress(pr.level); val a=atan2(pr.vy,pr.vx)
        c.save(); c.translate(pr.x.toFloat(),pr.y.toFloat()); c.rotate((a*180/PI).toFloat())
        if(pr.kind=="arrow") {
            val r=pr.radius; val length=16+13*t
            stroke(1.0+3*t,100,Ink.mid); line(c,-length-16-16*t,0.0,-length,0.0)
            stroke(2.0+2*t); line(c,-length,0.0,0.0,0.0)
            fill(); path.reset(); path.moveTo(r.toFloat(),0f); path.lineTo((-r*.45).toFloat(),(-r).toFloat()); path.lineTo((-r*.45).toFloat(),r.toFloat()); path.close(); c.drawPath(path,p)
            stroke(1.0+t); line(c,-length,-r*.7,-length+5,0.0); line(c,-length,r*.7,-length+5,0.0)
            repeat((5*t).roundToInt()) { i -> fill(Ink.paper,170-i*18); square(c,-length-i*8-5,if(i%2==0) r*.8 else -r,2+2*t) }
        } else {
            val r=pr.radius*(.20+.06*t)
            repeat(3+(5*t).roundToInt()) { i ->
                val rr=r*(1-i*.08); fill(if(i%2==0) Ink.mid else Ink.paper,220-i*20)
                diamond(c,-i*(4+2*t),sin(pr.x*.08+i)*r*.22,rr,rr)
            }
            fill(Ink.light); diamond(c,0.0,0.0,r*.65,r*.75)
            repeat(2+(6*t).roundToInt()) { i -> fill(Ink.light,150); square(c,-14.0-i*5,sin(pr.x*.1+i)*r*(.6+t*.5),2+2*t) }
        }
        c.restore(); p.alpha=255; p.style=Paint.Style.FILL
    }
}
