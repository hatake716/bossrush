package io.github.hatake716.bossrush

import kotlin.math.*

enum class BulletShape { SEED, LEAF, FANG, FEATHER, NEEDLE, SUN, MOON, SHARD, DROP, RUNE, CHAIN, FLAME, APPLE, BLADE, HAMMER, SPEAR }
enum class BarragePattern { FAN, RING, SPIRAL, PAIRS, WAVE, CROSS, FLOWER, STREAM }
data class BarrageProfile(val name: String,val hint: String,val shape: BulletShape,val pattern: BarragePattern,
    val count: Int,val waves: Int,val spread: Double,val rotation: Double=0.0,val turn: Double=0.0,
    val weave: Double=0.0,val rhythm: Double=.25,val remembers: Boolean=false)

data class EnemyBullet(var x: Double,var y: Double,val heading: Double,val speed: Double,val radius: Double,
    val shape: BulletShape,val power: Double,val turn: Double=0.0,val weave: Double=0.0,val phase: Double=0.0,
    var age: Double=0.0,var life: Double=3.6*BossTiming.INTERVAL_SCALE) {
    fun angleAt(time: Double)=heading+turn*time+weave*sin(time*5*BossTiming.RATE+phase)
    val angle get()=angleAt(age)
    val armed get()=age>=ARM_TIME
    companion object { const val ARM_TIME=.20*BossTiming.INTERVAL_SCALE }
}

/** Target and emitter are fixed during the visible charge; fired bullets never home. */
data class EnemyVolley(val profile: BarrageProfile,val stage: Int,val readyAt: Double,val ordinal: Int,
    val memoryX: Double,val memoryY: Double,var started: Boolean=false,var x: Double=0.0,var y: Double=0.0,
    var aim: Double=0.0,var emitted: Int=0) {
    val warning get()=(.75-.20*stage.coerceIn(0,31)/31.0)*BossTiming.INTERVAL_SCALE
    val firstShot get()=readyAt+warning
    val speed get()=(68.0+40.0*stage.coerceIn(0,31)/31.0)*BossTiming.RATE
    val interval get()=profile.rhythm*BossTiming.INTERVAL_SCALE
    // Preserve the authored number of waves so higher density does not slow the cycle.
    val count: Int get() {
        val density=when { stage<8 -> 1.35; stage<20 -> 1.45; else -> 1.55 }
        val group=when(profile.pattern) { BarragePattern.CROSS -> 4; BarragePattern.PAIRS -> 2; else -> 1 }
        return ceil(profile.count*density/group).toInt()*group
    }
    val power get()=.55+.15*stage.coerceIn(0,31)/31.0
    val side get()=if(ordinal%2==0) 1 else -1
    fun bullets(wave: Int): List<EnemyBullet> {
        val p=profile; val n=count
        val stagger=if(wave%2==1) min(.16,PI/n) else .0
        val center=aim+(p.rotation*wave+stagger)*side
        return (0 until n).map { i ->
            val u=if(n>1) i.toDouble()/(n-1)-.5 else .0
            val angle=when(p.pattern) {
                BarragePattern.FAN,BarragePattern.STREAM -> center+u*p.spread
                BarragePattern.RING,BarragePattern.SPIRAL -> center+2*PI*i/n
                BarragePattern.PAIRS -> center+(if(i%2==0) -1 else 1)*(.18+(i/2)*p.spread)
                BarragePattern.WAVE -> center+u*p.spread+.15*sin(i*1.4+wave)
                BarragePattern.CROSS -> center+(i%4)*PI/2+(i/4-(n/4-1)/2.0)*p.spread
                BarragePattern.FLOWER -> center+2*PI*i/n+.12*sin(i*3.0+wave)
            }
            val offset=if(p.pattern==BarragePattern.STREAM) u*45 else .0
            val speedScale=when(p.pattern) {
                BarragePattern.FLOWER -> .80+.20*(1+cos(i*3.0))/2
                BarragePattern.WAVE -> .85+.15*(i%2)
                else -> 1.0
            }
            EnemyBullet(x-sin(aim)*offset,y+cos(aim)*offset,angle,speed*speedScale,3.0+(stage/12)*.4,
                p.shape,power,p.turn*side*BossTiming.RATE,p.weave,i*.7)
        }
    }
}

/** Individual mythology-inspired volleys, additive to the existing floor and movement attacks. */
object EnemyBarrage {
    const val MAX_BULLETS=192
    val profiles=linkedMapOf(
        "ratatoskr" to BarrageProfile("枝渡りの木の実","木の実の扇状射撃を横へかわす",BulletShape.SEED,BarragePattern.FAN,3,2,.85,rotation=.16),
        "dainn" to BarrageProfile("四枝の若葉","四方へ広がる葉の間を抜ける",BulletShape.LEAF,BarragePattern.CROSS,8,2,.30,rotation=.12),
        "gullinbursti" to BarrageProfile("黄金の剛毛","並んで飛ぶ黄金の針から横へ",BulletShape.NEEDLE,BarragePattern.STREAM,4,3,.16,rhythm=.22),
        "hugin" to BarrageProfile("思考の羽矢","開いてゆく羽の扇を見て移動",BulletShape.FEATHER,BarragePattern.FAN,5,3,1.2,rotation=.19),
        "munin" to BarrageProfile("記憶の返し羽","最初にいた場所から切り返す",BulletShape.FEATHER,BarragePattern.PAIRS,6,3,.23,turn=-.18,remembers=true),
        "tanngrisnir" to BarrageProfile("雷車の双轍","二列の雷針の間に入る",BulletShape.NEEDLE,BarragePattern.PAIRS,6,3,.14,rhythm=.21),
        "skoll" to BarrageProfile("追日の光輪","外へ広がる日輪の隙間へ",BulletShape.SUN,BarragePattern.RING,10,2,0.0,rotation=.14),
        "hati" to BarrageProfile("追月の弧","曲がる月の軌道の外へ",BulletShape.MOON,BarragePattern.FAN,7,3,1.7,turn=.32),
        "hraesvelgr" to BarrageProfile("北風の羽嵐","うねる羽の列を大きく回避",BulletShape.FEATHER,BarragePattern.WAVE,7,3,1.9,weave=.20),
        "thjazi" to BarrageProfile("奪春の氷羽","両翼から開く氷の羽を避ける",BulletShape.SHARD,BarragePattern.PAIRS,8,3,.19,rotation=-.17),
        "skadi" to BarrageProfile("雪山の矢雨","細い矢列を左右にかわす",BulletShape.NEEDLE,BarragePattern.STREAM,5,4,.30,rotation=.10,rhythm=.20),
        "ullr" to BarrageProfile("狩神の連弓","回転する矢の十字の間へ",BulletShape.NEEDLE,BarragePattern.CROSS,12,3,.13,rotation=.18),
        "aegir" to BarrageProfile("海宴の波珠","速度の違う波の列を見分ける",BulletShape.DROP,BarragePattern.WAVE,9,4,2.3,weave=.12,rhythm=.28),
        "ran" to BarrageProfile("溺者の結び網","交差する網の結び目を避ける",BulletShape.RUNE,BarragePattern.CROSS,12,3,.22,rotation=.28,turn=.12),
        "njord" to BarrageProfile("順風の潮弾","風に曲がる潮の扇をかわす",BulletShape.DROP,BarragePattern.FAN,9,4,2.0,rotation=-.14,turn=.22),
        "jormungandr" to BarrageProfile("世界蛇の毒環","うねる毒の輪の隙間へ",BulletShape.DROP,BarragePattern.RING,14,3,0.0,weave=.28,rhythm=.32),
        "garmr" to BarrageProfile("冥門の血牙","三連の牙の扇から離れる",BulletShape.FANG,BarragePattern.FAN,9,3,2.2,rotation=.23,rhythm=.18),
        "hel" to BarrageProfile("生死の魂環","逆向きにずれる二つの魂環",BulletShape.MOON,BarragePattern.RING,14,4,0.0,turn=-.22,rhythm=.29),
        "nidhogg" to BarrageProfile("根喰いの毒流","蛇行する毒の列を横に抜ける",BulletShape.FANG,BarragePattern.WAVE,11,4,2.4,weave=.33,rhythm=.23),
        "fenrir" to BarrageProfile("断鎖の散弾","鎖の破片が螺旋に広がる",BulletShape.CHAIN,BarragePattern.SPIRAL,12,4,0.0,rotation=.25),
        "hrungnir" to BarrageProfile("石心の砥石花","三角の石心から広がる破片",BulletShape.SHARD,BarragePattern.FLOWER,15,3,0.0,rotation=.21),
        "thrym" to BarrageProfile("霜王の氷鎚","回る氷の十字を斜めにかわす",BulletShape.HAMMER,BarragePattern.CROSS,16,4,.15,rotation=.20),
        "utgardloki" to BarrageProfile("幻城の偽星","曲がるルーンの螺旋を読む",BulletShape.RUNE,BarragePattern.SPIRAL,13,4,0.0,rotation=-.31,turn=.24),
        "surtr" to BarrageProfile("終末の火弾雨","火の扇が順に向きを変える",BulletShape.FLAME,BarragePattern.FAN,13,5,2.65,rotation=.19,rhythm=.19),
        "idunn" to BarrageProfile("黄金林檎の種","花のように開く種の隙間へ",BulletShape.APPLE,BarragePattern.FLOWER,16,4,0.0,rotation=.17,weave=.14),
        "freyr" to BarrageProfile("自律剣の輪舞","回りながら広がる剣の輪",BulletShape.BLADE,BarragePattern.SPIRAL,14,5,0.0,rotation=.27,turn=.17),
        "freyja" to BarrageProfile("黄金涙の花","波打つ涙の花をくぐり抜ける",BulletShape.DROP,BarragePattern.FLOWER,18,4,0.0,rotation=-.15,weave=.23),
        "tyr" to BarrageProfile("誓約の剣列","左右に開く剣列の切れ目へ",BulletShape.BLADE,BarragePattern.PAIRS,12,5,.15,rotation=.13,rhythm=.20),
        "heimdall" to BarrageProfile("虹橋の星鐘","星の輪の間を乗り換える",BulletShape.SUN,BarragePattern.RING,18,4,0.0,rotation=.16,rhythm=.26),
        "loki" to BarrageProfile("変幻の火蛇","逆巻く炎の螺旋を見てかわす",BulletShape.FLAME,BarragePattern.SPIRAL,15,5,0.0,rotation=-.28,weave=.30,rhythm=.22),
        "thor" to BarrageProfile("雷鎚の九連環","雷鎚の放射と次の輪の間へ",BulletShape.HAMMER,BarragePattern.RING,18,5,0.0,rotation=.18,rhythm=.19),
        "odin" to BarrageProfile("槍と鴉の星陣","槍の螺旋は間隔を見て抜ける",BulletShape.SPEAR,BarragePattern.SPIRAL,20,5,0.0,rotation=.21,turn=-.12,rhythm=.21)
    )
    fun forBoss(id: String)=profiles.getValue(id)
}
