package io.github.hatake716.bossrush

import kotlin.math.*
import kotlin.random.Random

enum class Screen { TITLE, JOBS, INTRO, BATTLE, CUTIN, REWARD, SHOP, PAUSED, GAMEOVER, ENDING, CODEX, HELP }
data class Run(
    val job: Job, var stage: Int = 0, val levels: IntArray = intArrayOf(1,1,1,1),
    var gold: Int = 0, val inventory: MutableList<Item> = mutableListOf(Item.POTION, Item.POTION),
    var score: Int = 0, var totalTime: Double = 0.0, var totalDamage: Double = 0.0,
    var kills: Int = 0, var previousHp: Double = 0.0, var checkpoint: String = "INTRO"
) {
    fun copyForTrial() = Run(job, stage, levels.copyOf(), inventory = mutableListOf())
}
data class Actor(var x: Double, var y: Double, var hp: Double, var maxHp: Double, var facing: Double = -PI/2)
data class Summon(val kind: Int, var x: Double, var y: Double, var life: Double, var timer: Double = .1)
data class Projectile(var x: Double, var y: Double, val vx: Double, val vy: Double, val power: Double, val radius: Double, val kind: String, var life: Double = 3.0)
data class IceMark(var x: Double, var y: Double, val power: Double, val radius: Double, var time: Double = .75)
data class Particle(var x: Double, var y: Double, val text: String, var life: Double = .85, val good: Boolean = false)
data class Hazard(
    val shape: String, var x: Double, var y: Double, val a: Double, val b: Double = 0.0,
    val angle: Double = 0.0, var delay: Double = 1.4, val duration: Double = .28*BossTiming.SCALE,
    val multiplier: Double = 1.0, var time: Double = 0.0, var resolved: Boolean = false,
    val sourceX: Double = x, val sourceY: Double = y, val label: String = "",
    val ultimate: Boolean = false
) {
    fun contains(px: Double, py: Double, radius: Double = 7.0): Boolean {
        val dx = px-x; val dy = py-y; val d = hypot(dx,dy)
        val along = dx*cos(angle)+dy*sin(angle)
        val across = -dx*sin(angle)+dy*cos(angle)
        return when(shape) {
            "circle" -> d < a + radius
            "ring" -> d > a-radius && d < b+radius
            "line" -> abs(across) < b/2+radius && abs(along) < a/2+radius
            "cone" -> d < a+radius && abs(atan2(sin(atan2(dy,dx)-angle), cos(atan2(dy,dx)-angle))) < b/2
            "safe", "tower" -> d > a-radius
            "knock" -> true
            else -> false
        }
    }
    fun dangerousAt(px: Double,py: Double,radius: Double=12.0): Boolean {
        if(shape!="knock") return contains(px,py,radius)
        val direction=atan2(py-y,px-x)
        val xx=px+cos(direction)*a; val yy=py+sin(direction)*a
        return xx<16 || xx>584 || yy<20 || yy>318
    }
}
data class Cue(var at: Double, val pattern: Pattern, val ordinal: Int)

class GameEngine(random: Random=Random.Default) {
    private val director=BossAttackDirector(random)
    var screen = Screen.TITLE
    var run: Run? = null
    var player = Actor(300.0,270.0,150.0,150.0)
    var boss = Actor(300.0,125.0,1000.0,1000.0)
    val bossInfo get() = Bosses.all[(run?.stage ?: 0).coerceIn(0,31)]
    val job get() = run?.job ?: Job.WARRIOR
    val levels get() = run?.levels ?: intArrayOf(1,1,1,1)
    var selectedJob = Job.WARRIOR
    var gauge = 100.0
    val cooldowns = DoubleArray(4)
    val buffs = mutableMapOf<String,Double>()
    val summons = mutableListOf<Summon>()
    val projectiles = mutableListOf<Projectile>()
    val iceMarks = mutableListOf<IceMark>()
    val hazards = mutableListOf<Hazard>()
    val impacts = mutableListOf<BattleImpact>()
    val particles = mutableListOf<Particle>()
    val cues = mutableListOf<Cue>()
    val normalCues = mutableListOf<NormalCue>()
    val sounds = mutableListOf<String>()
    var moveX = 0.0; var moveY = 0.0
    var heldSkill = -1
    var elapsed = 0.0
    var damageTaken = 0.0
    var damageDone = 0.0
    var restTime = 0.0
    var invulnerability = 0.0
    var ultimateUsed = false
    var ultimateCount = 0
        private set
    val ultimateActive get()=cutinTime>0 || cues.isNotEmpty() || hazards.any { it.ultimate && !it.resolved } || impacts.any { it.hazard.ultimate }
    val enraged get() = ultimateUsed && boss.hp > 0 && boss.hp <= boss.maxHp*.25
    var cutinTime = 0.0
    var patternNumber = 0
    var nextPattern = 1.5*BossTiming.SCALE
    var castName = ""
    var castHint = "斜線の予兆から離れよう"
    var castEnd = 0.0
    var castDuration = 0.0
    var message = ""
    var messageTime = 0.0
    var lastScore = 0
    var lastGold = 0
    var lastTime = 0.0
    var lastDamage = 0.0
    var rewardChosen = false
    var lootChosen = false
    var stolen = false
    var pendingLoot: Item? = null
    var fortune = false
    var screenAge = 0.0
    var selectedItem = -1
    var selectedBoss = 0
    var resultRecorded = false
    var onCheckpoint: (() -> Unit)? = null
    var onResult: ((Int, Boolean) -> Unit)? = null
    private var trial = false
    private var finalEnding = false

    fun changeScreen(value: Screen) { screen=value; screenAge=0.0; heldSkill=-1; moveX=0.0; moveY=0.0 }
    fun newRun() {
        run = Run(selectedJob); resultRecorded=false; finalEnding=false
        changeScreen(Screen.INTRO); onCheckpoint?.invoke()
    }
    fun resumeRun() {
        val r = run ?: return
        rewardChosen = r.checkpoint == "SHOP"
        lootChosen = true
        changeScreen(if (r.checkpoint == "SHOP") Screen.SHOP else Screen.INTRO)
    }
    fun beginBattle(reference: Boolean = false) {
        val r=run ?: return
        trial=reference
        player=Actor(300.0,265.0,job.hp,job.hp)
        boss=Actor(300.0,125.0,1e12,1e12)
        gauge=100.0; cooldowns.fill(0.0); buffs.clear(); summons.clear(); projectiles.clear(); iceMarks.clear()
        hazards.clear(); impacts.clear(); particles.clear(); cues.clear(); normalCues.clear(); sounds.clear(); director.reset()
        elapsed=0.0; damageTaken=0.0; damageDone=0.0; restTime=0.0; invulnerability=0.0
        ultimateUsed=false; ultimateCount=0; cutinTime=0.0; patternNumber=0; nextPattern=1.6*BossTiming.SCALE
        message="予兆の外へ移動。技を押して攻撃！"; messageTime=4.0
        rewardChosen=false; lootChosen=false; stolen=false; pendingLoot=null; fortune=false
        castName=""; castEnd=0.0; castDuration=0.0; selectedItem=-1
        if (!reference) {
            val hp = max(estimateHp(r), r.previousHp * 1.025).roundToInt().toDouble()
            boss.hp=hp; boss.maxHp=hp
            r.checkpoint="INTRO"
        }
        changeScreen(Screen.BATTLE)
    }
    fun estimateHp(r: Run): Double {
        val sim=GameEngine(); sim.run=r.copyForTrial(); sim.beginBattle(true)
        sim.player.x=300.0; sim.player.y=166.0
        repeat(1800) {
            sim.idealActions()
            sim.update(1.0/60)
        }
        return max(100.0, sim.damageDone)
    }
    fun idealActions() {
        when(job) {
            Job.WARRIOR -> { useSkill(0); useSkill(2) }
            Job.MAGE -> { if ((buffs["focus"] ?: 0.0)<=0) useSkill(2); useSkill(0); useSkill(1) }
            Job.SUMMONER -> { useSkill(0) }
            Job.THIEF -> useSkill(0)
        }
    }
    fun notify(text: String) { if(!trial) { message=text; messageTime=2.4 } }
    fun power(slot: Int): Double = Skills.power(job,slot,levels[slot]) * (1 + .10*(run?.stage ?: 0)) *
        (if((buffs["focus"] ?: 0.0)>0) 1+.5*Skills.fraction(levels[2]) else 1.0) *
        (if((buffs["power"] ?: 0.0)>0) 1.5 else 1.0) *
        (if((buffs["clones"] ?: 0.0)>0) 2.0 else 1.0)

    fun useSkill(slot: Int): Boolean {
        if(screen!=Screen.BATTLE || slot !in 0..3 || restTime>0 || cooldowns[slot]>.001) return false
        val skill=Skills.all.getValue(job)[slot]
        val haniwa=job==Job.SUMMONER && slot==2 && summons.any { it.kind==2 }
        val cost=if(haniwa) 10.0 else skill.cost
        if(gauge<cost) { notify("ゲージの回復を待とう"); return false }
        val d=hypot(player.x-boss.x,player.y-boss.y)
        val range=Skills.range(job,slot,levels[slot])
        if ((job==Job.WARRIOR&&slot==0 || job==Job.THIEF&&slot<2 || haniwa) && d>range+22) {
            notify("もう少しボスに近づこう"); return false
        }
        if (job==Job.SUMMONER && slot<3 && !haniwa && summons.size>=2) {
            notify("召喚は2体まで。仲間が帰るのを待とう"); return false
        }
        if (job==Job.THIEF && slot==1 && (stolen || (run?.inventory?.size ?: 5)>=5)) {
            notify(if(stolen) "このボスからは盗み済み" else "アイテムが満杯。使ってから盗もう"); return false
        }
        if (slot==3 && player.hp>=player.maxHp) { notify("HPは満タン"); return false }
        gauge-=cost; cooldowns[slot]=Skills.cooldown(job,slot,levels[slot])
        if(slot==3) { restTime=2.0; sounds.add("rest"); return true }
        player.facing=atan2(boss.y-player.y,boss.x-player.x)
        when(job) {
            Job.WARRIOR -> when(slot) {
                0 -> { damageBoss(power(0)); slash("SLASH","sword") }
                1 -> { buffs["shield"]=4.0+4*Skills.progress(levels[1]); sounds.add("shield") }
                2 -> shoot(power(2),6.0,"arrow",370.0)
            }
            Job.MAGE -> when(slot) {
                0 -> shoot(power(0),range,"fire",290.0)
                1 -> { iceMarks.add(IceMark(boss.x,boss.y,power(1),range)); sounds.add("ice") }
                2 -> { buffs["focus"]=5.0+4*Skills.progress(levels[2]); sounds.add("focus") }
            }
            Job.SUMMONER -> if(haniwa) { damageBoss(power(2)); slash("HANIWA","haniwa") } else {
                summons.add(Summon(slot,player.x+(if(summons.isEmpty()) -27 else 27),player.y-20,12.0+8*Skills.progress(levels[slot])))
                sounds.add(when(slot) { 0 -> "summon-giant"; 1 -> "summon-rabbit"; else -> "summon-haniwa" })
            }
            Job.THIEF -> when(slot) {
                0 -> { damageBoss(power(0)); slash("SLASH","knife") }
                1 -> {
                    val item=Item.entries.filter { it.special }[(run!!.stage + levels[1]-1)%4]
                    run!!.inventory.add(item); stolen=true; notify("${item.title}を盗んだ！"); sounds.add("coin")
                }
                2 -> { buffs["speed"]=5.0+4*Skills.progress(levels[2]); sounds.add("speed") }
            }
        }
        return true
    }
    private fun slash(text: String,sound: String) { if(!trial) { particles.add(Particle((player.x+boss.x)/2,(player.y+boss.y)/2,text,.22)); sounds.add(sound) } }
    private fun shoot(power: Double, radius: Double, kind: String, speed: Double) {
        val a=atan2(boss.y-player.y,boss.x-player.x)
        projectiles.add(Projectile(player.x,player.y,cos(a)*speed,sin(a)*speed,power,radius,kind))
        if(!trial) sounds.add(kind)
    }
    fun damageBoss(amount: Double) {
        if(screen!=Screen.BATTLE) return
        val applied=if(!trial && !ultimateUsed && boss.hp-amount<=boss.maxHp/2) max(0.0,boss.hp-boss.maxHp/2) else min(boss.hp,amount)
        val wasEnraged=enraged
        boss.hp=max(0.0,boss.hp-applied); damageDone+=applied
        if(!trial && !wasEnraged && enraged) notify("HP1/4：二重詠唱！ 重なる予兆の隙間へ")
        if(!trial && applied>0) particles.add(Particle(boss.x+sin(elapsed*7)*25,boss.y-30,"${applied.roundToInt()}"))
        if(trial) return
        if(!ultimateUsed && boss.hp<=boss.maxHp/2+.001) {
            startUltimate()
        } else if(boss.hp<=0.0) victory()
    }
    private fun startUltimate() {
        ultimateUsed=true; ultimateCount++; director.ultimateStarted()
        hazards.clear(); impacts.clear(); cues.clear(); normalCues.clear()
        castName=bossInfo.ultimate; castHint=bossInfo.hint; sounds.add("ultimate")
        if(ultimateCount==1) {
            changeScreen(Screen.CUTIN); cutinTime=3.0
        } else beginUltimateSequence()
    }
    private fun beginUltimateSequence() {
        // Repeated ultimates keep combat, movement and held attacks running.
        bossInfo.sequence.forEachIndexed { i,p -> cues.add(Cue(elapsed+i*2.2*BossTiming.SCALE,p,i)) }
        nextPattern=elapsed+(bossInfo.sequence.size*2.2+1.3)*BossTiming.SCALE
        castHint=bossInfo.hint
    }
    fun heal(value: Double) {
        val actual=min(player.maxHp-player.hp,value); player.hp+=actual
        if(!trial && actual>.1) { particles.add(Particle(player.x,player.y-20,"+${actual.roundToInt()}",good=true)); sounds.add("heal") }
    }
    fun hurt(amount: Double, sourceX: Double=boss.x, sourceY: Double=boss.y, frontal: Boolean=false) {
        if(invulnerability>0 || (buffs["invisible"] ?: 0.0)>0 || screen!=Screen.BATTLE) return
        var reduction=if((buffs["armor"] ?: 0.0)>0) .5 else 1.0
        if((buffs["shield"] ?: 0.0)>0) reduction*=1-.75*Skills.fraction(levels[1])
        val haniwa=summons.firstOrNull { it.kind==2 }
        if(frontal && haniwa!=null) {
            val sourceAngle=atan2(sourceY-player.y,sourceX-player.x)
            val guardAngle=atan2(haniwa.y-player.y,haniwa.x-player.x)
            if(cos(sourceAngle-guardAngle)>.55) { reduction*=.25; notify("はにわが正面を守った！") }
        }
        val actual=min(player.hp, amount*reduction)
        player.hp-=actual; damageTaken+=actual; invulnerability=.5
        particles.add(Particle(player.x,player.y-25,"−${actual.roundToInt()}")); sounds.add("hurt")
        if(player.hp<=0) {
            val r=run ?: return
            r.totalDamage+=damageTaken; r.totalTime+=elapsed
            r.score+=(3000*(1-boss.hp/boss.maxHp)).roundToInt()
            changeScreen(Screen.GAMEOVER); recordResult(false)
        }
    }
    private fun recordResult(clear: Boolean) {
        if(!resultRecorded) { resultRecorded=true; onResult?.invoke(run?.score ?: 0,clear) }
    }
    fun useItem(index: Int): Boolean {
        val r=run ?: return false
        if(screen!=Screen.BATTLE || index !in r.inventory.indices) return false
        val item=r.inventory[index]
        if(item==Item.POTION && player.hp>=player.maxHp) { notify("HPは満タン"); return false }
        if(item==Item.FORTUNE && fortune) { notify("黄金の印は使用済み"); return false }
        when(item) {
            Item.POTION -> heal(player.maxHp*.45)
            Item.POWER -> buffs["power"]=8.0
            Item.ARMOR -> buffs["armor"]=8.0
            Item.HASTE -> buffs["haste"]=8.0
            Item.INVISIBLE -> buffs["invisible"]=6.0
            Item.CLONES -> buffs["clones"]=8.0
            Item.FORTUNE -> fortune=true
            Item.HOURGLASS -> { cooldowns.fill(0.0); gauge=100.0 }
        }
        r.inventory.removeAt(index); selectedItem=-1; sounds.add("buff"); notify("${item.title}を使った")
        return true
    }
    fun update(rawDt: Double) {
        val dt=rawDt.coerceIn(0.0,.05)
        screenAge+=dt
        if(screen==Screen.CUTIN) {
            cutinTime-=dt
            if(cutinTime<=0) {
                cutinTime=0.0
                changeScreen(Screen.BATTLE)
                beginUltimateSequence()
            }
            return
        }
        if(screen!=Screen.BATTLE) return
        impacts.forEach { it.age+=dt }; impacts.removeAll { it.age>=it.lifetime }
        elapsed+=dt; messageTime=max(0.0,messageTime-dt); invulnerability=max(0.0,invulnerability-dt)
        for(i in cooldowns.indices) cooldowns[i]=max(0.0,cooldowns[i]-dt)
        buffs.keys.toList().forEach { buffs[it]=max(0.0,buffs.getValue(it)-dt) }
        gauge=min(100.0,gauge+dt*(if(job==Job.SUMMONER) 10.0 else 20.0))
        if(restTime>0) { restTime-=dt; if(restTime<=0) heal(Skills.power(job,3,levels[3])) }
        else {
            val len=hypot(moveX,moveY).coerceAtLeast(1.0)
            val speed=job.speed*(if((buffs["speed"] ?: 0.0)>0) 1+.8*Skills.fraction(levels[2]) else 1.0)*(if((buffs["haste"] ?: 0.0)>0) 1.5 else 1.0)
            player.x=(player.x+moveX/len*speed*dt).coerceIn(16.0,584.0)
            player.y=(player.y+moveY/len*speed*dt).coerceIn(20.0,318.0)
            if(abs(moveX)+abs(moveY)>.05) player.facing=atan2(moveY,moveX)
            if(heldSkill>=0) useSkill(heldSkill)
        }
        if(screen!=Screen.BATTLE) return
        if(!trial) {
            // Small, readable movement. A lock-on spell can miss after its tracking window.
            boss.x=300+sin(elapsed*.47+(run?.stage ?: 0)*.3)*32
            boss.y=122+sin(elapsed*.33)*15
        }
        for(s in summons.toList()) {
            s.life-=dt; s.timer-=dt
            val angle=atan2(boss.y-player.y,boss.x-player.x)
            val tx=when(s.kind) { 0 -> boss.x+(if(summons.indexOf(s)%2==0) -36 else 36); 2 -> player.x+cos(angle)*24; else -> player.x-27 }
            val ty=when(s.kind) { 0 -> boss.y+25; 2 -> player.y+sin(angle)*24; else -> player.y+18 }
            s.x+=(tx-s.x)*min(1.0,dt*6); s.y+=(ty-s.y)*min(1.0,dt*6)
            if(s.timer<=0) {
                s.timer+=if(s.kind==1) 2.0 else 1.0
                if(s.kind==0 && hypot(s.x-boss.x,s.y-boss.y)<Skills.range(job,0,levels[0])+25) {
                    damageBoss(power(0))
                    if(!trial) { sounds.add("giant-hit"); particles.add(Particle(boss.x,boss.y,"HANIWA",.22)) }
                }
                if(s.kind==1) heal(Skills.power(job,1,levels[1]))
            }
        }
        summons.removeAll { it.life<=0 }
        if(screen!=Screen.BATTLE) return
        for(p in projectiles.toList()) {
            p.life-=dt; p.x+=p.vx*dt; p.y+=p.vy*dt
            if(hypot(p.x-boss.x,p.y-boss.y)<28+p.radius*.3) {
                damageBoss(p.power); p.life=0.0
                if(!trial) { particles.add(Particle(p.x,p.y,if(p.kind=="fire") "BURST" else "HIT",.25)); sounds.add("${p.kind}-hit") }
            }
        }
        projectiles.removeAll { it.life<=0 }
        for(mark in iceMarks.toList()) {
            if(mark.time>.35) { mark.x=boss.x; mark.y=boss.y }
            mark.time-=dt
            if(mark.time<=0) {
                if(hypot(mark.x-boss.x,mark.y-boss.y)<mark.radius+25) damageBoss(mark.power)
                if(!trial) { particles.add(Particle(mark.x,mark.y,"ICE",.3)); sounds.add("ice-hit") }
            }
        }
        iceMarks.removeAll { it.time<=0 }
        if(screen!=Screen.BATTLE) return
        for(p in particles) { p.life-=dt; p.y-=dt*19 }
        particles.removeAll { it.life<=0 }
        if(trial) return
        val due=cues.filter { it.at<=elapsed }
        cues.removeAll(due.toSet())
        due.forEach {
            cast(it.pattern,true,it.ordinal)
            val finish=elapsed+(hazards.maxOfOrNull { h -> h.delay-h.time } ?: .0)+.55*BossTiming.SCALE
            if(cues.isNotEmpty() && cues.first().at<finish) {
                val shift=finish-cues.first().at
                cues.forEach { cue -> cue.at+=shift }
            }
            nextPattern=max(nextPattern,(cues.lastOrNull()?.at ?: elapsed)+(finish-elapsed)+.8*BossTiming.SCALE)
        }
        val normalDue=normalCues.filter { it.at<=elapsed }
        normalCues.removeAll(normalDue.toSet())
        normalDue.forEach { castNormalWave(it) }
        if(elapsed>=nextPattern && cues.isEmpty() && normalCues.isEmpty() && hazards.none { !it.resolved }) {
            val i=director.next(BossCombat.forBoss(bossInfo.id).size,ultimateUsed,BossDifficulty(run!!.stage).ultimateChance)
            if(i<0) { startUltimate(); return }
            castNormal(i,patternNumber++)
        }
        for(h in hazards) {
            h.time+=dt
            if(!h.resolved && h.time>=h.delay) {
                h.resolved=true
                if(impacts.size>=24) impacts.removeAt(0)
                impacts.add(BattleImpact(h.copy()))
                if(h.contains(player.x,player.y)) {
                    if(h.shape=="knock") {
                        val angle=atan2(player.y-h.y,player.x-h.x)
                        val nx=player.x+cos(angle)*h.a; val ny=player.y+sin(angle)*h.a
                        if(nx<16 || nx>584 || ny<20 || ny>318) hurt(bossDamage()*h.multiplier*1.5)
                        player.x=nx.coerceIn(16.0,584.0); player.y=ny.coerceIn(20.0,318.0)
                    } else hurt(bossDamage()*h.multiplier,h.sourceX,h.sourceY,h.shape=="cone" || h.shape=="line")
                } else if(h.shape=="tower") { notify("ルーンを受け止めた！"); sounds.add("buff") }
            }
        }
        hazards.removeAll { it.time>=it.delay+it.duration }
    }
    fun bossDamage() = BossDifficulty(run?.stage ?: 0).damage

    fun castNormal(slot: Int,ordinal: Int) {
        castNormalWave(NormalCue(elapsed,slot,0,ordinal,player.x,player.y))
    }
    private fun castNormalWave(cue: NormalCue) {
        val attack=BossCombat.forBoss(bossInfo.id)[cue.slot]
        val difficulty=BossDifficulty(run!!.stage)
        val arena=NormalArena(this,cue.memoryX,cue.memoryY,cue.ordinal)
        attack.draw(arena,cue.wave)
        val partner=if(enraged) combineNormal(arena.hazards,cue.slot,cue.ordinal+cue.wave) else null
        ensureReachable(arena.hazards,difficulty.reaction)
        hazards.addAll(arena.hazards)
        castName=bossInfo.attackNames[cue.slot]+if(attack.waves>1) "  ${cue.wave+1}/${attack.waves}" else ""
        castHint=if(partner!=null) "二重詠唱：${bossInfo.attackNames[cue.slot]} ＋ $partner" else attack.hint
        castDuration=arena.hazards.maxOf { it.delay }; castEnd=elapsed+castDuration
        val finish=elapsed+arena.hazards.maxOf { it.delay+it.duration }
        if(cue.wave+1<attack.waves) normalCues.add(cue.copy(at=finish+difficulty.comboGap,wave=cue.wave+1))
        nextPattern=finish+difficulty.recovery
        sounds.add("cast")
    }

    /** A simultaneous volley must offer one route outside both attacks, including knockback landing. */
    private fun distanceToSafety(created: List<Hazard>): Double {
        var distance=Double.POSITIVE_INFINITY
        for(x in 20..580 step 10) for(y in 20..310 step 10) {
            var xx=x.toDouble(); var yy=y.toDouble()
            var safe=created.none { it.dangerousAt(xx,yy) }
            if(safe) for(h in created) if(h.shape=="knock") {
                val a=atan2(yy-h.y,xx-h.x); xx+=cos(a)*h.a; yy+=sin(a)*h.a
                if(xx<16 || xx>584 || yy<20 || yy>318 || created.any { it.shape!="knock" && it.dangerousAt(xx,yy) }) safe=false
            }
            if(safe) distance=min(distance,hypot(x-player.x,y-player.y))
        }
        return distance
    }

    /** Pair a boss's own normal motif with this wave. Try its authored variants to avoid contradictory safe zones. */
    private fun combineNormal(primary: MutableList<Hazard>,exclude: Int,ordinal: Int): String {
        val attacks=BossCombat.forBoss(bossInfo.id)
        val delay=primary.minOf { it.delay }
        val first=primary.filter { abs(it.delay-delay)<.01 }
        for(offset in attacks.indices) {
            val slot=(ordinal+offset).mod(attacks.size)
            if(slot==exclude) continue
            val attack=attacks[slot]
            for(w in 0 until attack.waves) for(side in 0..1) {
                val arena=NormalArena(this,player.x,player.y,ordinal+side)
                attack.draw(arena,(ordinal+w).mod(attack.waves))
                arena.hazards.forEach { it.delay=delay }
                if(distanceToSafety(first+arena.hazards).isFinite()) {
                    primary.addAll(arena.hazards)
                    return bossInfo.attackNames[slot]
                }
            }
        }
        // Some inward/outward pairs have mutually exclusive safe regions. Mirroring
        // the secondary formation preserves its geometry while opening a shared gap.
        for(slot in attacks.indices) if(slot!=exclude) {
            val attack=attacks[slot]
            for(w in 0 until attack.waves) {
                val arena=NormalArena(this,player.x,player.y,ordinal)
                attack.draw(arena,w)
                val mirrored=arena.hazards.map { it.copy(x=600-it.x,y=340-it.y,angle=it.angle+PI,delay=delay) }
                if(distanceToSafety(first+mirrored).isFinite()) {
                    primary.addAll(mirrored)
                    return bossInfo.attackNames[slot]
                }
            }
        }
        error("No compatible double cast for ${bossInfo.id}: $exclude / $ordinal")
    }

    private fun ensureReachable(created: List<Hazard>,reaction: Double) {
        if(created.isEmpty()) return
        val delay=created.minOf { it.delay }
        val first=created.filter { abs(it.delay-delay)<.01 }
        val distance=distanceToSafety(first)
        check(distance.isFinite()) { "No safe space for ${bossInfo.id}: $castName" }
        val extra=max(.0,distance/job.speed+reaction-delay)
        created.forEach { it.delay+=extra }
    }

    fun cast(pattern: Pattern, ultimate: Boolean, ordinal: Int) {
        val start=hazards.size
        val strong=if(ultimate) 1.65 else 1.0
        val delay=(if(ultimate) 1.75 else max(1.1,1.65-(run?.stage ?: 0)*.012))*BossTiming.SCALE
        val angle=atan2(player.y-boss.y,player.x-boss.x)
        fun add(shape: String,x: Double,y: Double,a: Double,b: Double=0.0,ang: Double=0.0,wait: Double=delay,label: String="") {
            hazards.add(Hazard(shape,x,y,a,b,ang,wait,multiplier=strong,sourceX=boss.x,sourceY=boss.y,label=label,ultimate=ultimate))
        }
        if(ultimate) castName="${bossInfo.ultimate}  ${ordinal+1}/${bossInfo.sequence.size}"
        castEnd=elapsed+delay
        castHint=when(pattern) {
            Pattern.CIRCLE, Pattern.CHASE -> "足元の円から外へ"
            Pattern.DONUT -> "輪の内側が安全"
            Pattern.CROSS -> "十字の斜線を避ける"
            Pattern.LANES -> "縦の帯の隙間へ"
            Pattern.CONE -> "扇の外・ボスの横へ"
            Pattern.METEORS -> "落下地点から離れる"
            Pattern.SWEEP -> "斜線のない半面へ"
            Pattern.KNOCKBACK -> "中央に寄って吹き飛ばしに備える"
            Pattern.TOWERS -> "白いルーンの円に入る！"
            Pattern.SPIRAL -> "回る光の間に入る"
            Pattern.SIDES -> "中央の横帯が安全"
            Pattern.WAVE -> "横の波の隙間へ"
            Pattern.FRONTBACK -> "正面の次は背面！ 切り返す"
            Pattern.GRID -> "格子の小さな隙間へ"
            Pattern.ECLIPSE -> "月印の円に入る！"
        }
        when(pattern) {
            Pattern.CIRCLE -> add("circle",player.x,player.y,66.0)
            Pattern.DONUT -> add("ring",boss.x,boss.y,82.0,720.0)
            Pattern.CROSS -> { add("line",boss.x,boss.y,1100.0,55.0); add("line",boss.x,boss.y,1100.0,55.0,PI/2) }
            Pattern.LANES -> { val shift=(ordinal%2)*60.0; for(i in 0..3) add("line",85.0+i*145+shift,170.0,400.0,55.0,PI/2) }
            Pattern.CONE -> add("cone",boss.x,boss.y,310.0,PI*.64,angle)
            Pattern.METEORS -> { add("circle",player.x,player.y,52.0); for(i in 0..3) add("circle",70.0+((i*137+ordinal*51)%460),65.0+((i*83+ordinal*29)%215),42.0) }
            Pattern.SWEEP -> add("line",if(ordinal%2==0) 150.0 else 450.0,170.0,370.0,300.0,PI/2)
            Pattern.KNOCKBACK -> add("knock",300.0,170.0,100.0,label="中央へ")
            Pattern.TOWERS -> add("tower",if(ordinal%2==0) 210.0 else 390.0,215.0,48.0,label="入る")
            Pattern.SPIRAL -> for(i in 0..3) add("line",300.0,170.0,750.0,32.0,ordinal*.42+i*PI/4)
            Pattern.CHASE -> for(i in 0..2) {
                val x=(player.x+moveX*i*48).coerceIn(30.0,570.0); val y=(player.y+moveY*i*48).coerceIn(30.0,305.0)
                add("circle",x,y,44.0,wait=delay+i*.22*BossTiming.SCALE)
            }
            Pattern.SIDES -> { add("line",300.0,38.0,650.0,116.0); add("line",300.0,303.0,650.0,116.0) }
            Pattern.WAVE -> { val gap=ordinal%3; for(i in 0..2) if(i!=gap) add("line",300.0,58.0+i*111,650.0,67.0) }
            Pattern.FRONTBACK -> { add("cone",boss.x,boss.y,600.0,PI*.98,angle); add("cone",boss.x,boss.y,600.0,PI*.98,angle+PI,delay+.8*BossTiming.SCALE) }
            Pattern.GRID -> {
                for(i in 0..3) add("line",75.0+i*150,170.0,400.0,32.0,PI/2)
                for(i in 0..2) add("line",300.0,55.0+i*115,650.0,28.0)
            }
            Pattern.ECLIPSE -> add("safe",if(ordinal%2==0) 180.0 else 420.0,210.0,58.0,label="月印")
        }
        // Fairness: even an unbuffed character at an edge must be able to reach a safe
        // point after seeing the telegraph. Sequential casts wait for this resolution.
        val created=hazards.drop(start).toMutableList()
        val primaryCount=created.size
        if(ultimate && enraged) {
            val partner=combineNormal(created,-1,ordinal+patternNumber++)
            hazards.addAll(created.drop(primaryCount))
            castHint="二重詠唱：$partner ＋ $castHint"
        }
        ensureReachable(created,.4*BossTiming.SCALE)
        castDuration=created.minOf { it.delay }; castEnd=elapsed+castDuration
        sounds.add("cast")
    }

    fun victory() {
        val r=run ?: return
        lastTime=elapsed; lastDamage=damageTaken
        lastScore=scoreFor(elapsed,damageTaken)
        lastGold=((60+r.stage*8)*(if(job==Job.THIEF) 1.6 else 1.0)*(if(fortune) 3 else 1)).roundToInt()
        r.score+=lastScore; r.gold+=lastGold; r.kills++; r.totalTime+=elapsed; r.totalDamage+=damageTaken; r.previousHp=boss.maxHp
        rewardChosen=false; lootChosen=job!=Job.THIEF; finalEnding=r.stage==31
        // Rewards remain an atomic checkpoint: reloading before choosing restarts this battle.
        changeScreen(Screen.REWARD); sounds.add("victory")
    }
    fun upgrade(slot: Int): Boolean {
        if(screen!=Screen.REWARD || rewardChosen || slot !in 0..3 || levels[slot]>=16) return false
        levels[slot]++; rewardChosen=true; sounds.add("buff"); return true
    }
    fun chooseLoot(item: Item): Boolean {
        val r=run ?: return false
        if(screen!=Screen.REWARD || job!=Job.THIEF || lootChosen || !item.special) return false
        if(r.inventory.size>=5) { pendingLoot=item; notify("所持品を1つ選ぶと交換できます"); return false }
        r.inventory.add(item); lootChosen=true; sounds.add("coin"); return true
    }
    fun replaceLoot(index: Int): Boolean {
        val r=run ?: return false; val item=pendingLoot ?: return false
        if(index !in r.inventory.indices || screen!=Screen.REWARD || lootChosen) return false
        r.inventory[index]=item; pendingLoot=null; lootChosen=true; return true
    }
    fun finishReward() {
        if(!rewardChosen || !lootChosen) return
        if(finalEnding) { changeScreen(Screen.ENDING); recordResult(true) }
        else {
            run!!.stage++; run!!.checkpoint="SHOP"; changeScreen(Screen.SHOP); onCheckpoint?.invoke()
        }
    }
    fun buy(item: Item): Boolean {
        val r=run ?: return false
        if(screen!=Screen.SHOP || item.special) return false
        if(r.inventory.size>=5) { notify("ストックは5個まで"); return false }
        if(r.gold<item.price) { notify("お金が足りない"); return false }
        r.gold-=item.price; r.inventory.add(item); sounds.add("coin"); onCheckpoint?.invoke(); return true
    }
    fun discardItem(index: Int) {
        if(screen==Screen.SHOP && index in run!!.inventory.indices) { run!!.inventory.removeAt(index); selectedItem=-1; onCheckpoint?.invoke() }
    }
    fun leaveShop() { run?.checkpoint="INTRO"; changeScreen(Screen.INTRO); onCheckpoint?.invoke() }
    fun pause() { if(screen==Screen.BATTLE || screen==Screen.CUTIN) changeScreen(Screen.PAUSED) }
    fun unpause() { selectedItem=-1; changeScreen(if(cutinTime>0) Screen.CUTIN else Screen.BATTLE) }
    companion object {
        fun scoreFor(time: Double, damage: Double): Int = 10000 + max(0.0,9000-time*150).roundToInt() + max(0.0,6000-damage*40).roundToInt()
    }
}
