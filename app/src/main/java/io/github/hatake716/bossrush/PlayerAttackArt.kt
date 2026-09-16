package io.github.hatake716.bossrush

import android.graphics.*
import kotlin.math.*

/** Authored pixel geometry: bright attacks remain local so the arena never flashes white. */
class PlayerAttackArt {
    private val paint=Paint().apply { isAntiAlias=false; strokeCap=Paint.Cap.SQUARE }
    private val path=Path()
    private val white=Color.rgb(255,250,218)
    private val fire=Color.rgb(255,91,38)
    private val gold=Color.rgb(255,199,70)
    private val blue=Color.rgb(64,142,255)
    private val ice=Color.rgb(107,239,255)
    private fun fill(color: Int,alpha: Int=255) { paint.color=color; paint.alpha=alpha.coerceIn(0,255); paint.style=Paint.Style.FILL }
    private fun stroke(color: Int,width: Double,alpha: Int=255) { fill(color,alpha); paint.style=Paint.Style.STROKE; paint.strokeWidth=width.toFloat() }
    private fun line(c: Canvas,x: Double,y: Double,xx: Double,yy: Double)=c.drawLine(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),paint)
    private fun block(c: Canvas,x: Double,y: Double,w: Double,h: Double=w) {
        val xx=floor(x/2)*2; val yy=floor(y/2)*2
        c.drawRect(xx.toFloat(),yy.toFloat(),(xx+max(2.0,round(w/2)*2)).toFloat(),(yy+max(2.0,round(h/2)*2)).toFloat(),paint)
    }
    private fun diamond(c: Canvas,x: Double,y: Double,w: Double,h: Double) {
        path.reset(); path.moveTo(x.toFloat(),(y-h).toFloat()); path.lineTo((x+w).toFloat(),y.toFloat())
        path.lineTo(x.toFloat(),(y+h*.32).toFloat()); path.lineTo((x-w).toFloat(),y.toFloat()); path.close(); c.drawPath(path,paint)
    }
    private fun ring(c: Canvas,x: Double,y: Double,r: Double,flat: Double=1.0,n: Int=16,angle: Double=0.0) {
        path.reset()
        repeat(n) { i ->
            val a=angle+i*2*PI/n; val xx=(x+cos(a)*r).toFloat(); val yy=(y+sin(a)*r*flat).toFloat()
            if(i==0) path.moveTo(xx,yy) else path.lineTo(xx,yy)
        }
        path.close(); c.drawPath(path,paint)
    }
    private fun spark(c: Canvas,x: Double,y: Double,r: Double,color: Int,alpha: Int) {
        stroke(color,2.0,alpha); line(c,x-r,y,x+r,y); line(c,x,y-r*1.6,x,y+r*1.6)
        fill(white,alpha); block(c,x-2,y-2,4.0)
    }
    private fun crescent(c: Canvas,r: Double,start: Double,sweep: Double,width: Double) {
        path.reset()
        for(i in 0..24) {
            val a=(start+sweep*i/24)*PI/180
            val x=(cos(a)*r).toFloat(); val y=(sin(a)*r*.76).toFloat()
            if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        for(i in 24 downTo 0) {
            val a=(start+sweep*i/24)*PI/180; val inner=r-width*sin(PI*i/24).pow(.65)
            path.lineTo((cos(a)*inner).toFloat(),(sin(a)*inner*.76).toFloat())
        }
        path.close(); c.drawPath(path,paint)
    }

    fun impact(c: Canvas,e: PlayerEffect) {
        when(e.kind) {
            PlayerEffectKind.SWORD,PlayerEffectKind.KNIFE -> slash(c,e)
            PlayerEffectKind.GIANT,PlayerEffectKind.HANIWA -> punch(c,e)
            PlayerEffectKind.FIRE -> explosion(c,e)
            PlayerEffectKind.ICE -> glacier(c,e)
            PlayerEffectKind.FIRE_CAST,PlayerEffectKind.ICE_CAST -> cast(c,e)
            else -> Unit
        }
        paint.alpha=255; paint.style=Paint.Style.FILL
    }

    private fun slash(c: Canvas,e: PlayerEffect) {
        val t=e.strength; val u=e.progress; val alpha=(255*(1-u).pow(.55)).toInt()
        val warrior=e.kind==PlayerEffectKind.SWORD
        val color=if(warrior) gold else when(e.combo) { 1 -> ice; 2 -> Color.rgb(186,137,255); else -> Color.rgb(255,107,193) }
        val r=e.radius; val x=e.x; val y=e.y-12
        c.save(); c.translate(x.toFloat(),y.toFloat()); c.rotate((e.angle*180/PI).toFloat())
        val layers=1+(2*t).roundToInt()
        repeat(if(e.combo==3) 3 else e.combo) { cut ->
            repeat(layers) { j ->
                val rr=r*(.94-j*.09-cut*.045)
                stroke(color,(if(warrior) 8.0 else 5.0)+t*5,alpha/(2+j))
                if(!warrior && e.combo==2) {
                    val sign=if(cut==0) 1 else -1
                    line(c,8.0,-sign*r*.42,r*.93,sign*r*.38)
                    stroke(white,2+2*t,alpha); line(c,12.0,-sign*r*.4,r*.91,sign*r*.37)
                } else if(warrior) {
                    val span=if(e.combo==3) 270.0 else 125.0
                    val start=when(e.combo) { 2 -> 65-u*70+cut*70; 3 -> -170+u*180+cut*90; else -> -85+u*65 }
                    val sweep=if(e.combo==2) -span else span
                    fill(color,alpha/(1+j)); crescent(c,rr,start,sweep,9.0+12*t)
                    stroke(if(j==0) white else color,1.8+2*t,alpha)
                    c.drawArc((-rr).toFloat(),(-rr*.76).toFloat(),rr.toFloat(),(rr*.76).toFloat(),start.toFloat(),sweep.toFloat(),false,paint)
                } else {
                    // Needle thrust, then three long after-image cuts instead of a sword's broad arc.
                    val across=(cut-1)*16.0; val shift=u*15+j*4
                    line(c,12.0+shift,across+12,r*(.9-u*.12),across-9)
                    stroke(white,2.0+t,alpha); line(c,20.0+shift,across+9,r*(.9-u*.12),across-9)
                }
            }
        }
        if(e.combo==3) {
            stroke(color,2.0+2*t,alpha/2); ring(c,0.0,0.0,r*(.35+.65*u),.7)
        }
        repeat(9+(18*t).roundToInt()+e.combo*3) { i ->
            val a=(i*2.39996+u*1.2); val distance=r*(.28+.7*u)*(.55+(i%5)*.11)
            fill(if(i%3==0) white else color,alpha)
            block(c,cos(a)*distance,sin(a)*distance*.7,2+4*t)
        }
        c.restore()
        spark(c,e.targetX,e.targetY-18,(8+14*t)*(1-u),color,alpha)
    }

    private fun explosion(c: Canvas,e: PlayerEffect) {
        val u=e.progress; val t=e.strength; val a=(245*(1-u).pow(.7)).toInt(); val r=e.radius
        fill(fire,a/7); ring(c,e.x,e.y,r*(.7+u*.3))
        repeat(2+e.combo) { i ->
            stroke(if(i%2==0) fire else gold,3+3*t,a/(1+i/2))
            ring(c,e.x,e.y,r*(.3+u*.8)*(1-i*.14),.72,12,u*.3+i)
        }
        if(e.combo==2) {
            repeat(2) { i ->
                val sign=if(i==0) -1 else 1
                stroke(gold,4+4*t,a/2); line(c,e.x-r*.65,e.y-sign*r*.38,e.x+r*.65,e.y+sign*r*.38)
            }
        } else if(e.combo==3) {
            repeat(8) { i ->
                val angle=i*PI/4+u*.45
                fill(gold,a); diamond(c,e.x+cos(angle)*r*(.45+u*.5),e.y+sin(angle)*r*(.45+u*.5),4+5*t,12+18*t)
            }
        }
        repeat(8+(14*t).roundToInt()+e.combo*2) { i ->
            val angle=i*2.39996; val distance=r*.82*sqrt((i+.5)/(10+14*t+e.combo*2))
            val x=e.x+cos(angle)*distance; val y=e.y+sin(angle)*distance*.7
            val height=(20+40*t)*(1-u)*(.65+(i%4)*.22)
            fill(if(i%3==0) Color.rgb(227,51,85) else fire,a); block(c,x-5-3*t,y-height,10+6*t,height+3)
            fill(gold,a); block(c,x-3,y-height*.78,5+4*t,height*.7)
            fill(white,a); block(c,x,y-height*.38,3+2*t,height*.34)
            fill(gold,a); block(c,x+sin(i+u*5)*8,y-height-u*30-8,2+4*t)
        }
        spark(c,e.x,e.y-15,(13+14*t)*(1-u),white,a)
    }

    private fun glacier(c: Canvas,e: PlayerEffect) {
        val u=e.progress; val t=e.strength; val a=(245*(1-u).pow(.7)).toInt(); val r=e.radius
        stroke(blue,3+3*t,a/2); ring(c,e.x,e.y,r*(.6+u*.4),.75,6,PI/6)
        stroke(ice,1.5+2*t,a); ring(c,e.x,e.y,r*(.8+u*.2),.75,6,PI/6)
        if(e.combo==2) {
            stroke(white,2+2*t,a/2); line(c,e.x-r,e.y,e.x+r,e.y); line(c,e.x,e.y-r,e.x,e.y+r*.45)
        } else if(e.combo==3) {
            stroke(ice,2+2*t,a/2); ring(c,e.x,e.y-14,r*(.75+u*.2),.85,6,-u)
            stroke(white,1.0+t,a/2); ring(c,e.x,e.y-14,r*.55,.85,3,u)
        }
        repeat(5+(10*t).roundToInt()+e.combo) { i ->
            val angle=i*2.39996; val distance=if(i==0) 0.0 else r*.72*sqrt(i.toDouble()/(6+10*t+e.combo))
            val x=e.x+cos(angle)*distance; val y=e.y+sin(angle)*distance*.65
            val height=(26+42*t)*(1-u*.65)*(if(i==0) 1.3 else .6+(i%3)*.18)
            fill(blue,a); diamond(c,x,y,6+8*t,height)
            fill(ice,a); diamond(c,x-2,y-2,3+5*t,height*.88)
            stroke(white,1.5+t,a); line(c,x-2,y-height*.84,x-2,y)
            fill(ice,a); block(c,x+cos(angle)*u*30,y-height-u*18,3+4*t)
        }
        repeat(6) { i ->
            val angle=i*PI/3; val d=r*(.3+u*.65)
            stroke(ice,1.0+t,a); line(c,e.x,e.y,e.x+cos(angle)*d,e.y+sin(angle)*d*.7)
            spark(c,e.x+cos(angle)*d,e.y+sin(angle)*d*.7,3+4*t,white,a)
        }
    }

    private fun cast(c: Canvas,e: PlayerEffect) {
        val t=e.strength; val u=e.progress; val a=(220*(1-u)).toInt()
        val hot=e.kind==PlayerEffectKind.FIRE_CAST; val color=if(hot) fire else ice
        val r=e.radius*(.6+.4*u)
        stroke(color,2+2*t,a); ring(c,e.x,e.y-10,r,.6,if(hot) 8 else 6,u)
        repeat(5+(12*t).roundToInt()+e.combo) { i ->
            val angle=i*2.39996+u*2; val d=r*(.5+u*.5)
            fill(if(i%3==0) white else color,a)
            diamond(c,e.x+cos(angle)*d,e.y-10+sin(angle)*d*.6-u*18,3+3*t,6+7*t)
        }
    }

    private fun punch(c: Canvas,e: PlayerEffect) {
        val t=e.strength; val u=e.progress; val a=(255*(1-u).pow(.6)).toInt()
        val giant=e.kind==PlayerEffectKind.GIANT; val color=if(giant) gold else Color.rgb(236,163,114)
        val extension=SummonPunch.extension(e.age)
        val x=e.x+(e.targetX-e.x)*extension; val y=e.y-22+(e.targetY-e.y)*extension
        // A segmented forearm and a pixel fist visibly travel from the ally to the enemy.
        repeat(4) { i ->
            val f=i/4.0; val xx=e.x+(x-e.x)*f; val yy=e.y-22+(y-(e.y-22))*f
            fill(Ink.deep,a); block(c,xx-7,yy-6,14.0+5*t,12.0+4*t)
            fill(if(i%2==0) Ink.mid else color,a); block(c,xx-5,yy-4,10.0+4*t,8.0+3*t)
        }
        val size=(if(giant) 12.0 else 9.0)+8*t
        fill(Ink.dark,a); block(c,x-size-2,y-size-2,size*2+4,size*1.5+4)
        fill(color,a); block(c,x-size,y-size,size*2,size*1.5)
        fill(white,a); repeat(4) { i -> block(c,x-size+i*size*.5,y-size,3+2*t,6+4*t) }
        fill(Ink.deep,a); block(c,x+size*.25,y+size*.2,size*.7,4+3*t)
        val shock=((u-.12)/.88).coerceIn(0.0,1.0); val r=e.radius*(.22+shock*.5)
        stroke(color,3+3*t,a); ring(c,e.targetX,e.targetY-10,r,.55,12)
        repeat(8+(14*t).roundToInt()) { i ->
            val angle=i*2.39996; val distance=r*(.5+shock*.8)
            fill(if(i%3==0) white else color,a)
            block(c,e.targetX+cos(angle)*distance,e.targetY-10+sin(angle)*distance*.65-shock*18,3+6*t)
        }
        spark(c,e.targetX,e.targetY-16,(14+16*t)*(1-u),white,a)
    }

    fun summon(c: Canvas,art: PixelArt,s: Summon,scale: Double) {
        val f=SummonPunch.extension(s.punchAge)
        val x=s.x+cos(s.punchAngle)*f*12; val y=s.y+sin(s.punchAngle)*f*9
        c.save(); c.translate(x.toFloat(),y.toFloat())
        if(s.kind!=1) c.rotate((cos(s.punchAngle)*f*13).toFloat())
        art.sprite(c,when(s.kind) { 0 -> "giant"; 1 -> "rabbit"; else -> "haniwa" },0f,0f,scale.toFloat(),flip=s.kind!=1 && cos(s.punchAngle)<0)
        c.restore()
    }

    fun projectile(c: Canvas,p: Projectile) {
        if(p.delay>0) return
        val t=Skills.progress(p.level); val hot=p.kind=="fire"; val color=if(hot) fire else blue
        val core=if(hot) gold else ice; val size=if(hot) 7+7*t else 6+5*t
        c.save(); c.translate(p.x.toFloat(),p.y.toFloat()); c.rotate((atan2(p.vy,p.vx)*180/PI).toFloat())
        repeat(5+(4*t).roundToInt()) { i ->
            val r=size*(1-i*.075); val tail=-i*(5+3*t)
            fill(if(i%2==0) color else core,220-i*18)
            diamond(c,tail,sin(p.age*19+i)*size*.23,r,r)
        }
        fill(core); diamond(c,0.0,0.0,size,size*1.3)
        fill(white); diamond(c,2.0,-1.0,size*.45,size*.65)
        repeat(3+(5*t).roundToInt()) { i ->
            fill(core,180-i*12); block(c,-18.0-i*7,sin(p.age*22+i)*(size+5),2+3*t)
        }
        if(p.combo==3) { stroke(core,1.5+t,165); ring(c,-9.0,0.0,size*1.8,.7,if(hot) 8 else 6,p.age*5) }
        c.restore()
    }
}
