package io.github.hatake716.bossrush

import kotlin.math.*
import kotlin.random.Random

/** Encounter pacing is independent from player damage and the 30-second HP budget. */
data class BossDifficulty(val stage: Int) {
    private val t=stage.coerceIn(0,31)/31.0
    val warning get()=1.85-.60*t
    val reaction get()=.55-.25*t
    val recovery get()=1.05-.50*t
    val comboGap get()=.65-.30*t
    val damage get()=15.0+stage*1.35+stage*stage*.010
    val ultimateChance get()=.22+.18*t
}

/** A random mix after awakening, with a normal cast between successive ultimates. */
class BossAttackDirector(private val random: Random) {
    private var lastNormal=-1
    private var normalsSinceUltimate=0
    private var lastWasUltimate=false
    fun reset() { lastNormal=-1; normalsSinceUltimate=0; lastWasUltimate=false }
    fun ultimateStarted() { normalsSinceUltimate=0; lastWasUltimate=true }
    fun next(count: Int,awakened: Boolean,chance: Double): Int {
        if(awakened && !lastWasUltimate && (normalsSinceUltimate>=3 || random.nextDouble()<chance)) return -1
        val choices=(0 until count).filter { it!=lastNormal }
        val choice=if(awakened) choices[random.nextInt(choices.size)] else (lastNormal+1)%count
        lastNormal=choice; lastWasUltimate=false; normalsSinceUltimate++
        return choice
    }
}

data class NormalCue(val at: Double,val slot: Int,val wave: Int,val ordinal: Int,val memoryX: Double,val memoryY: Double)
data class NormalAttack(val hint: String,val waves: Int=1,val draw: NormalArena.(Int)->Unit)

/** Each wave locks its geometry when its visible warning starts. */
class NormalArena(val e: GameEngine,val memoryX: Double,val memoryY: Double,val ordinal: Int) {
    val hazards=mutableListOf<Hazard>()
    val bx get()=e.boss.x; val by get()=e.boss.y
    val px get()=e.player.x; val py get()=e.player.y
    val aim get()=atan2(py-by,px-bx)
    val oldAim get()=atan2(memoryY-by,memoryX-bx)
    val side get()=if(ordinal%2==0) 1 else -1
    fun mirror(x: Double)=if(side==1) x else 600-x
    private fun add(shape: String,x: Double,y: Double,a: Double,b: Double=0.0,angle: Double=0.0,label: String="") {
        hazards.add(Hazard(shape,x,y,a,b,angle,delay=BossDifficulty(e.run!!.stage).warning,
            sourceX=bx,sourceY=by,label=label))
    }
    fun circle(x: Double,y: Double,r: Double)=add("circle",x.coerceIn(25.0,575.0),y.coerceIn(25.0,309.0),r)
    fun line(x: Double,y: Double,length: Double,width: Double,angle: Double=0.0)=add("line",x,y,length,width,angle)
    fun ray(angle: Double,width: Double,length: Double=660.0)=line(bx,by,length,width,angle)
    fun cone(angle: Double,width: Double,reach: Double=540.0)=add("cone",bx,by,reach,width,angle)
    fun ring(x: Double,y: Double,inner: Double,outer: Double=720.0)=add("ring",x,y,inner,outer)
    fun safe(x: Double,y: Double,r: Double,label: String="入る")=add("tower",x,y,r,label=label)
    fun knock(distance: Double)=add("knock",300.0,170.0,distance,label="中央へ")
}

/** Authored normal attacks: shared primitives, distinct targeting, topology and sequences. */
object BossCombat {
    val attacks: Map<String,List<NormalAttack>> = linkedMapOf(
        "ratatoskr" to listOf(
            NormalAttack("木の実は足元へ。円の外に一歩移動") { circle(px,py,49.0) },
            NormalAttack("枝を渡る順番に、縦の帯が走る",2) { w ->
                line(mirror(155.0+w*260),167.0,390.0,46.0,PI/2)
            }),
        "dainn" to listOf(
            NormalAttack("角の正面を避けて鹿の横へ") { cone(aim,PI*.53,270.0) },
            NormalAttack("四枚の葉が落ちる。中心と葉の隙間へ") {
                for(i in 0..3) { val a=i*PI/2+.4; circle(bx+cos(a)*110,by+sin(a)*110,39.0) }
            }),
        "gullinbursti" to listOf(
            NormalAttack("黄金の突進は直線。横へかわす") { ray(aim,72.0,1100.0) },
            NormalAttack("牙は外から内へ。次の扇も見直す",2) { w -> cone(aim+side*(if(w==0) -.5 else .5),PI*.43,350.0) }),
        "hugin" to listOf(
            NormalAttack("思考の羽が左右から足元を挟む") {
                circle(px-62,py,38.0); circle(px+62,py,38.0)
            },
            NormalAttack("黒翼の斜めの交差。菱形の隙間へ") { ray(PI/4,35.0); ray(-PI/4,35.0) }),
        "munin" to listOf(
            NormalAttack("記憶は最初にいた場所へ戻る",2) { w ->
                circle(memoryX,memoryY,if(w==0) 44.0 else 68.0)
            },
            NormalAttack("最初の向きを記憶する翼。背面へ切り返す",2) { w -> cone(oldAim+w*PI,PI*.72,410.0) }),
        "tanngrisnir" to listOf(
            NormalAttack("二つの蹄から雷の轍が走る") {
                line(bx-65,167.0,400.0,38.0,PI/2); line(bx+65,167.0,400.0,38.0,PI/2)
            },
            NormalAttack("角突きのあと、踏み込んだ場所に雷",2) { w ->
                if(w==0) cone(aim,PI*.38,350.0) else circle(memoryX,memoryY,63.0)
            }),
        "skoll" to listOf(
            NormalAttack("太陽の牙が正面、次に横を喰らう",2) { w -> cone(aim+w*side*PI/2,PI*.68) },
            NormalAttack("日輪が外へ広がる。中心と外周を切り返す",2) { w ->
                if(w==0) circle(bx,by,103.0) else ring(bx,by,103.0)
            }),
        "hati" to listOf(
            NormalAttack("月の輪が閉じた後、月の中心から離れる",2) { w ->
                if(w==0) ring(memoryX,memoryY,79.0) else circle(memoryX,memoryY,79.0)
            },
            NormalAttack("月を追う二度の噛みつき。足を止めない",2) { circle(px,py,55.0) }),
        "hraesvelgr" to listOf(
            NormalAttack("北風の後に左右の風壁。中央へ戻る",2) { w ->
                if(w==0) knock(78.0) else { line(48.0,167.0,400.0,98.0,PI/2); line(552.0,167.0,400.0,98.0,PI/2) }
            },
            NormalAttack("風の裂け目が斜めに交替する",2) { w ->
                for(i in 0..2) line(110.0+i*190,167.0,540.0,36.0,PI/2+side*(if(w==0) .30 else -.30))
            }),
        "thjazi" to listOf(
            NormalAttack("鷲の急襲のあと、最初の足元へ氷の羽",2) { w ->
                if(w==0) ray(aim,68.0,1000.0) else { circle(memoryX-44,memoryY,48.0); circle(memoryX+44,memoryY,48.0) }
            },
            NormalAttack("凍る羽は外側、次に真ん中へ落ちる",2) { w ->
                for(i in 0..2) circle(115.0+i*185,if(w==0) 85.0 else 255.0,57.0)
            }),
        "skadi" to listOf(
            NormalAttack("雪の矢列が交替。横の隙間を乗り換える",2) { w ->
                for(i in 0..3) line(55.0+i*156+w*54,167.0,410.0,43.0,PI/2)
            },
            NormalAttack("二度の狙撃。射線が決まってから横へ",2) { ray(aim,42.0,1100.0) }),
        "ullr" to listOf(
            NormalAttack("交差する矢が回転する。斜めから十字へ",2) { w ->
                ray(PI/4+w*PI/4,39.0); ray(-PI/4+w*PI/4,39.0)
            },
            NormalAttack("盾の前を薙ぎ、背後へ矢を返す",2) { w ->
                if(w==0) cone(oldAim,PI*.9,430.0) else ray(oldAim+PI,66.0,950.0)
            }),
        "aegir" to listOf(
            NormalAttack("宴の三つの波。上から下へ進む",3) { w -> line(300.0,52.0+w*113,650.0,76.0) },
            NormalAttack("渦の中心が移る。次の渦へ泳ぐ",2) { w -> ring(mirror(210.0+w*180),190.0,97.0) }),
        "ran" to listOf(
            NormalAttack("網は縦糸、横糸の順。隙間を渡る",2) { w ->
                if(w==0) for(i in 0..3) line(76.0+i*150,167.0,390.0,36.0,PI/2)
                else for(i in 0..2) line(300.0,52.0+i*115,650.0,41.0)
            },
            NormalAttack("投げ網の輪が閉じ、内側を捕らえる",2) { w ->
                if(w==0) ring(memoryX,memoryY,91.0) else circle(memoryX,memoryY,91.0)
            }),
        "njord" to listOf(
            NormalAttack("順風を受けてから、港の印へ戻る",2) { w ->
                if(w==0) knock(92.0) else safe(mirror(240.0),220.0,66.0,"港")
            },
            NormalAttack("航路を挟む波が横、縦へ切り替わる",2) { w ->
                if(w==0) { line(300.0,40.0,650.0,115.0); line(300.0,304.0,650.0,115.0) }
                else { line(90.0,167.0,390.0,180.0,PI/2); line(510.0,167.0,390.0,180.0,PI/2) }
            }),
        "jormungandr" to listOf(
            NormalAttack("蛇の環が締まり、頭の側へ毒が来る",2) { w ->
                if(w==0) ring(bx,by,98.0) else cone(aim,PI*.83,570.0)
            },
            NormalAttack("毒の息から三つの毒溜まりへ",2) { w ->
                if(w==0) cone(oldAim,PI*.50) else for(i in -1..1) circle(memoryX+i*76,memoryY,48.0)
            }),
        "garmr" to listOf(
            NormalAttack("冥府の咆哮は正面、背面、両脇の順",3) { w ->
                if(w<2) cone(oldAim+w*PI,PI*.93) else { ray(oldAim+PI/2,61.0); circle(bx,by,53.0) }
            },
            NormalAttack("血の足跡は三度追跡。止まると噛まれる",3) { circle(px,py,62.0) }),
        "hel" to listOf(
            NormalAttack("生と死の半面が交替し、最後は中央へ",3) { w ->
                if(w<2) line(mirror(if(w==0) 145.0 else 455.0),167.0,400.0,290.0,PI/2)
                else { line(300.0,38.0,650.0,128.0); line(300.0,302.0,650.0,128.0) }
            },
            NormalAttack("冷たい抱擁は外、内の順に閉じる",2) { w ->
                if(w==0) ring(300.0,205.0,101.0) else circle(300.0,205.0,101.0)
            }),
        "nidhogg" to listOf(
            NormalAttack("根腐れの息、毒の跡、逆向きの息",3) { w ->
                when(w) { 0 -> cone(oldAim,PI*.66); 1 -> circle(memoryX,memoryY,83.0); else -> cone(oldAim+PI,PI*.86) }
            },
            NormalAttack("黒い根が格子状に伸び、次に根元が崩れる",2) { w ->
                if(w==0) { for(i in 0..3) line(65.0+i*156,167.0,400.0,38.0,PI/2); line(300.0,170.0,650.0,35.0) }
                else { circle(155.0,155.0,83.0); circle(445.0,155.0,83.0) }
            }),
        "fenrir" to listOf(
            NormalAttack("双顎が前後を噛み、中央の鎖が砕ける",3) { w ->
                if(w<2) cone(oldAim+w*PI,PI*.96) else circle(bx,by,124.0)
            },
            NormalAttack("鎖の交差を抜け、破片が広がる前に内へ",2) { w ->
                if(w==0) { ray(.35,58.0,950.0); ray(PI/2+.35,58.0,950.0) }
                else ring(bx,by,93.0)
            }),
        "hrungnir" to listOf(
            NormalAttack("砥石が三角に落ち、中央の心臓が砕ける",2) { w ->
                if(w==0) for(i in 0..2) { val a=-PI/2+i*PI*2/3; circle(300+cos(a)*115,174+sin(a)*115,66.0) }
                else circle(300.0,174.0,106.0)
            },
            NormalAttack("三角の石心から三方向へ地割れが走る",3) { w -> ray(PI/6+w*PI/3,70.0,1000.0) }),
        "thrym" to listOf(
            NormalAttack("奪った鎚が十字、斜め、足元へ砕ける",3) { w ->
                if(w<2) { ray(w*PI/4,57.0,1000.0); ray(PI/2+w*PI/4,57.0,1000.0) }
                else circle(px,py,88.0)
            },
            NormalAttack("氷壁が左右から上下へ閉じる",2) { w ->
                if(w==0) { line(105.0,167.0,400.0,211.0,PI/2); line(495.0,167.0,400.0,211.0,PI/2) }
                else { line(300.0,43.0,650.0,137.0); line(300.0,291.0,650.0,137.0) }
            }),
        "utgardloki" to listOf(
            NormalAttack("幻の印は左、右、中央。印を見直す",3) { w -> safe(if(w==2) 300.0 else mirror(170.0+w*260),210.0,56.0,"真実") },
            NormalAttack("虚像の手が前後を交換し、十字へ変わる",3) { w ->
                if(w<2) cone(oldAim+(1-w)*PI,PI*.94) else { ray(PI/4,51.0,1000.0); ray(-PI/4,51.0,1000.0) }
            }),
        "surtr" to listOf(
            NormalAttack("炎剣を左右にかわし、最後の斬線の外へ",3) { w ->
                if(w<2) line(mirror(if(w==0) 147.0 else 453.0),167.0,400.0,294.0,PI/2)
                else ray(oldAim,115.0,1100.0)
            },
            NormalAttack("火の雨は三度。直前の落下地点も見る",3) { w ->
                circle(px,py,64.0)
                for(i in 0..2) circle(70.0+(i*179+w*73)%470,50.0+(i*91+w*57)%235,51.0)
            }),
        "idunn" to listOf(
            NormalAttack("三つの林檎を順に守る。印の中へ",3) { w -> safe(mirror(180.0+w*120),if(w==1) 105.0 else 242.0,54.0,"林檎") },
            NormalAttack("若葉の波紋は内、外、足元へ広がる",3) { w ->
                when(w) { 0 -> circle(300.0,185.0,110.0); 1 -> ring(300.0,185.0,110.0); else -> circle(px,py,72.0) }
            }),
        "freyr" to listOf(
            NormalAttack("鹿角の二連突きから光の剣が飛ぶ",3) { w ->
                if(w<2) cone(aim+side*(w*2-1)*.38,PI*.56) else ray(aim,68.0,1100.0)
            },
            NormalAttack("自ら戦う光の剣が三度回転する",3) { w ->
                for(i in 0..2) ray(ordinal*.31+w*.39+i*PI/3,39.0,950.0)
            }),
        "freyja" to listOf(
            NormalAttack("二匹の猫が左右から迫り、足元へ飛ぶ",3) { w ->
                if(w<2) { circle(px-54,py-36+w*72,57.0); circle(px+54,py+36-w*72,57.0) }
                else circle(px,py,92.0)
            },
            NormalAttack("首飾りの星が交差し、黄金の印へ集まる",3) { w ->
                if(w<2) for(i in 0..2) ray(i*PI/3+w*PI/6,41.0,1000.0)
                else safe(mirror(220.0),220.0,55.0,"黄金")
            }),
        "tyr" to listOf(
            NormalAttack("片腕の剣は前、背面、細い突きの順",3) { w ->
                if(w<2) cone(oldAim+w*PI,PI*.97) else ray(aim,53.0,1100.0)
            },
            NormalAttack("誓いの切先をかわし、誓約の印を守る",3) { w ->
                when(w) { 0 -> ray(aim,91.0,1100.0); 1 -> safe(300.0,230.0,52.0,"誓約"); else -> circle(300.0,230.0,89.0) }
            }),
        "heimdall" to listOf(
            NormalAttack("虹橋の光線は三度交替。橋の隙間を渡る",3) { w ->
                for(i in 0..4) line(36.0+i*127+(w%2)*55,167.0,400.0,45.0,PI/2)
            },
            NormalAttack("角笛の波は内、外、上下の順に響く",3) { w ->
                when(w) { 0 -> circle(bx,by,119.0); 1 -> ring(bx,by,94.0); else -> { line(300.0,65.0,650.0,102.0); line(300.0,277.0,650.0,102.0) } }
            }),
        "loki" to listOf(
            NormalAttack("変わり身は印、偽の足跡、反対側の印",3) { w ->
                when(w) { 0 -> safe(mirror(165.0),202.0,52.0,"真身"); 1 -> circle(mirror(165.0),202.0,104.0); else -> safe(mirror(435.0),202.0,52.0,"真身") }
            },
            NormalAttack("策略は今、過去、未来の足元へ向かう",3) { w ->
                when(w) { 0 -> circle(px,py,65.0); 1 -> { circle(memoryX,memoryY,86.0); ray(oldAim+PI/2,38.0) }; else -> circle(px+e.moveX*65,py+e.moveY*65,75.0) }
            }),
        "thor" to listOf(
            NormalAttack("鎚の十字が斜めに返り、落下地点が爆ぜる",3) { w ->
                if(w<2) { ray(w*PI/4,67.0,1100.0); ray(PI/2+w*PI/4,67.0,1100.0) }
                else circle(memoryX,memoryY,101.0)
            },
            NormalAttack("雷鳴の吹き飛ばしから、鎚の内側へ戻る",2) { w -> if(w==0) knock(104.0) else ring(bx,by,91.0) },
            NormalAttack("三連落雷。最後は隣にも雷が分かれる",3) { w ->
                circle(px,py,68.0); if(w==2) { circle(px-132,py,53.0); circle(px+132,py,53.0) }
            }),
        "odin" to listOf(
            NormalAttack("グングニルは三方向から貫き、足元へ返る",4) { w ->
                if(w<3) { ray(oldAim+(w-1)*PI/3,51.0,1200.0); circle(bx,by,41.0) }
                else ray(aim,81.0,1200.0)
            },
            NormalAttack("思考の鴉は今を、記憶の鴉は過去を狙う",3) { w ->
                if(w==0) { circle(px-58,py,50.0); circle(px+58,py,50.0) }
                else if(w==1) { ray(oldAim,57.0); ray(oldAim+PI/2,42.0) }
                else { circle(memoryX,memoryY,82.0); circle(px,py,61.0) }
            },
            NormalAttack("ルーンを踏み、槍の格子を抜け、次の印へ",3) { w ->
                if(w!=1) safe(mirror(if(w==0) 180.0 else 420.0),210.0,49.0,"ルーン")
                else { for(i in 0..3) line(77.0+i*150,167.0,400.0,42.0,PI/2); for(i in 0..2) line(300.0,53.0+i*115,650.0,34.0) }
            })
    )
    fun forBoss(id: String)=attacks.getValue(id)
}
