package io.github.hatake716.bossrush

import kotlin.math.*

enum class BossMoveKind(val label: String) { CHARGE("突進"), LEAP("飛び込み"), FLANK("回り込み"), BLINK("瞬間移動") }
data class BossMoveProfile(val kind: BossMoveKind,val slot: Int,val reach: Double,val width: Double,val range: Double,val tempo: Double)

/** One physical boss, one locked route. The anchor shares its clock with the visible warning. */
data class BossMove(
    val kind: BossMoveKind,val fromX: Double,val fromY: Double,val toX: Double,val toY: Double,
    val anchor: Hazard,val travel: Double,var hitPlayer: Boolean=false
) {
    val start get()=when(kind) {
        BossMoveKind.CHARGE -> anchor.delay
        BossMoveKind.FLANK,BossMoveKind.BLINK -> (anchor.delay-travel-.10).coerceAtLeast(0.0)
        BossMoveKind.LEAP -> (anchor.delay-travel).coerceAtLeast(0.0)
    }
    val progress get()=((anchor.time-start)/travel).coerceIn(0.0,1.0)
    val finished get()=anchor.time>=start+travel
    val lift get()=if(kind==BossMoveKind.LEAP) sin(progress*PI)*48 else if(kind==BossMoveKind.FLANK) sin(progress*PI)*10 else 0.0
    val opacity get()=if(kind==BossMoveKind.BLINK && progress>0 && progress<1) (50+205*abs(progress*2-1)).toInt() else 255
    fun point(u: Double): Pair<Double,Double> {
        val t=if(kind==BossMoveKind.BLINK) { if(u<.5) 0.0 else 1.0 } else u*u*(3-2*u)
        val bend=if(kind==BossMoveKind.FLANK) sin(u*PI)*22 else 0.0
        val a=atan2(toY-fromY,toX-fromX)
        return (fromX+(toX-fromX)*t-sin(a)*bend).coerceIn(84.0,516.0) to
            (fromY+(toY-fromY)*t+cos(a)*bend).coerceIn(112.0,276.0)
    }
}

object BossMobility {
    private fun p(kind: BossMoveKind,slot: Int=0,reach: Double=230.0,width: Double=52.0,range: Double=100.0,tempo: Double=.44)=BossMoveProfile(kind,slot,reach,width,range,tempo)
    val profiles=mapOf(
        "ratatoskr" to p(BossMoveKind.LEAP,reach=205.0,width=49.0,tempo=.38),
        "dainn" to p(BossMoveKind.CHARGE,reach=226.0,width=54.0),
        "gullinbursti" to p(BossMoveKind.CHARGE,reach=310.0,width=68.0,tempo=.42),
        "hugin" to p(BossMoveKind.FLANK,reach=235.0,range=140.0,tempo=.40),
        "munin" to p(BossMoveKind.BLINK,reach=210.0,range=110.0,tempo=.28),
        "tanngrisnir" to p(BossMoveKind.CHARGE,slot=1,reach=255.0,width=58.0),
        "skoll" to p(BossMoveKind.CHARGE,reach=275.0,width=62.0,tempo=.37),
        "hati" to p(BossMoveKind.LEAP,slot=1,reach=247.0,width=55.0,tempo=.42),
        "hraesvelgr" to p(BossMoveKind.FLANK,reach=270.0,range=165.0,tempo=.48),
        "thjazi" to p(BossMoveKind.LEAP,reach=280.0,width=61.0,tempo=.43),
        "skadi" to p(BossMoveKind.FLANK,reach=205.0,range=185.0,tempo=.35),
        "ullr" to p(BossMoveKind.FLANK,reach=220.0,range=170.0,tempo=.39),
        "aegir" to p(BossMoveKind.FLANK,reach=250.0,range=130.0,tempo=.52),
        "ran" to p(BossMoveKind.BLINK,reach=245.0,range=155.0,tempo=.30),
        "njord" to p(BossMoveKind.FLANK,reach=285.0,range=145.0,tempo=.46),
        "jormungandr" to p(BossMoveKind.CHARGE,slot=1,reach=320.0,width=72.0,tempo=.55),
        "garmr" to p(BossMoveKind.CHARGE,reach=242.0,width=56.0,tempo=.36),
        "hel" to p(BossMoveKind.BLINK,reach=235.0,range=125.0,tempo=.32),
        "nidhogg" to p(BossMoveKind.LEAP,reach=275.0,width=68.0,tempo=.50),
        "fenrir" to p(BossMoveKind.CHARGE,reach=305.0,width=70.0,tempo=.38),
        "hrungnir" to p(BossMoveKind.LEAP,reach=220.0,width=76.0,tempo=.54),
        "thrym" to p(BossMoveKind.LEAP,reach=240.0,width=70.0,tempo=.50),
        "utgardloki" to p(BossMoveKind.BLINK,reach=260.0,range=170.0,tempo=.26),
        "surtr" to p(BossMoveKind.CHARGE,reach=265.0,width=78.0,tempo=.55),
        "idunn" to p(BossMoveKind.FLANK,reach=200.0,range=115.0,tempo=.46),
        "freyr" to p(BossMoveKind.CHARGE,reach=250.0,width=50.0,tempo=.38),
        "freyja" to p(BossMoveKind.LEAP,reach=255.0,width=57.0,tempo=.36),
        "tyr" to p(BossMoveKind.CHARGE,reach=228.0,width=46.0,tempo=.34),
        "heimdall" to p(BossMoveKind.FLANK,reach=300.0,range=150.0,tempo=.32),
        "loki" to p(BossMoveKind.BLINK,reach=280.0,range=140.0,tempo=.24),
        "thor" to p(BossMoveKind.LEAP,reach=265.0,width=74.0,tempo=.46),
        "odin" to p(BossMoveKind.CHARGE,reach=330.0,width=60.0,tempo=.36)
    )
    fun forBoss(id: String)=profiles.getValue(id)
    fun target(e: GameEngine,p: BossMoveProfile,ordinal: Int): Pair<Double,Double> {
        val aim=atan2(e.player.y-e.boss.y,e.player.x-e.boss.x)
        val side=if(ordinal%2==0) 1 else -1
        val tx: Double; val ty: Double
        if(p.kind==BossMoveKind.FLANK || p.kind==BossMoveKind.BLINK) {
            tx=e.player.x+cos(aim+side*PI*.68)*p.range
            ty=e.player.y+sin(aim+side*PI*.68)*p.range
        } else {
            val overshoot=if(p.kind==BossMoveKind.CHARGE) 70.0 else 0.0
            tx=e.player.x+cos(aim)*overshoot; ty=e.player.y+sin(aim)*overshoot
        }
        return boundedTarget(e.boss.x,e.boss.y,tx,ty,p.reach,ordinal)
    }
    fun boundedTarget(x: Double,y: Double,tx: Double,ty: Double,reach: Double,ordinal: Int): Pair<Double,Double> {
        fun limit(xx: Double,yy: Double): Pair<Double,Double> {
            val d=hypot(xx-x,yy-y).coerceAtLeast(1.0); val f=min(1.0,reach/d)
            return (x+(xx-x)*f).coerceIn(84.0,516.0) to (y+(yy-y)*f).coerceIn(112.0,276.0)
        }
        var target=limit(tx,ty)
        if(hypot(target.first-x,target.second-y)<60) {
            target=limit(if(x<300) 465.0 else 135.0,if(ordinal%2==0) 238.0 else 130.0)
        }
        return target
    }
    fun charge(e: GameEngine,p: BossMoveProfile,ordinal: Int,delay: Double,ultimate: Boolean=false): BossMove {
        val (x,y)=target(e,p.copy(kind=BossMoveKind.CHARGE),ordinal)
        val distance=hypot(x-e.boss.x,y-e.boss.y)
        // Include the body's radius at both ends of the route in the warning.
        val h=Hazard("line",(x+e.boss.x)/2,(y+e.boss.y)/2,distance+p.width,p.width,
            atan2(y-e.boss.y,x-e.boss.x),delay,p.tempo,multiplier=if(ultimate) 1.65 else 1.0,
            sourceX=e.boss.x,sourceY=e.boss.y,label="突進",ultimate=ultimate,swept=true)
        return BossMove(BossMoveKind.CHARGE,e.boss.x,e.boss.y,x,y,h,p.tempo)
    }
    /** Distance to a swept segment. Relative coordinates also catch fast projectile/boss crossings. */
    fun segmentDistance(x0: Double,y0: Double,x1: Double,y1: Double): Double {
        val dx=x1-x0; val dy=y1-y0; val length=dx*dx+dy*dy
        val t=if(length<1e-12) 0.0 else (-(x0*dx+y0*dy)/length).coerceIn(0.0,1.0)
        return hypot(x0+dx*t,y0+dy*t)
    }
}
