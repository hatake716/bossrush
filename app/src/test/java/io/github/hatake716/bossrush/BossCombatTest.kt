package io.github.hatake716.bossrush

import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class BossCombatTest {
    private fun arena(stage: Int=0,seed: Int=7)=GameEngine(Random(seed)).apply {
        run=Run(Job.SUMMONER,stage); screen=Screen.BATTLE
        player=Actor(300.0,265.0,100000.0,100000.0)
        boss=Actor(300.0,125.0,900.0,900.0)
    }
    private fun distanceToSafety(e: GameEngine,hs: List<Hazard>): Double {
        var d=Double.POSITIVE_INFINITY
        for(x in 20..580 step 10) for(y in 20..310 step 10)
            if(hs.none { it.dangerousAt(x.toDouble(),y.toDouble()) }) {
                var xx=x.toDouble(); var yy=y.toDouble(); var valid=true
                for(h in hs.filter { it.shape=="knock" }) {
                    val direction=atan2(yy-h.y,xx-h.x)
                    xx+=cos(direction)*h.a; yy+=sin(direction)*h.a
                    if(xx !in 16.0..584.0 || yy !in 20.0..318.0 || hs.any { it.shape!="knock" && it.dangerousAt(xx,yy) }) valid=false
                }
                if(valid) d=min(d,hypot(x-e.player.x,y-e.player.y))
            }
        return d
    }
    @Test fun everyBossHasAuthoredDistinctNormalGeometryAndAllVolleysLeaveAnEscape() {
        assertEquals(Bosses.all.map { it.id }.toSet(),BossCombat.attacks.keys)
        val identities=mutableSetOf<String>()
        for((stage,boss) in Bosses.all.withIndex()) {
            val attacks=BossCombat.forBoss(boss.id)
            assertEquals(boss.attackNames.size,attacks.size)
            val signature=StringBuilder()
            for((slot,attack) in attacks.withIndex()) for(wave in 0 until attack.waves) for(ordinal in 0..1) {
                val e=arena(stage)
                val a=NormalArena(e,265.0,255.0,ordinal); attack.draw(a,wave)
                assertTrue("${boss.id}/$slot/$wave",a.hazards.isNotEmpty())
                assertTrue("${boss.id}/$slot/$wave has no escape",distanceToSafety(e,a.hazards).isFinite())
                assertTrue(a.hazards.all { !it.ultimate && it.multiplier==1.0 && it.delay.isFinite() && it.a>0 })
                signature.append(a.hazards.map { listOf(it.shape,it.x,it.y,it.a,it.b,it.angle) })
            }
            assertTrue("Duplicated boss moves: ${boss.id}",identities.add(signature.toString()))
        }
        assertEquals(32,identities.size)
    }
    @Test fun normalWarningsAllowUnbuffedTravelFromEveryCornerIncludingKnockbacks() {
        for((stage,boss) in Bosses.all.withIndex()) for(slot in boss.attackNames.indices)
            for(pos in listOf(20 to 20,580 to 20,20 to 310,580 to 310,300 to 265)) for(ordinal in 0..1) {
                val e=arena(stage); e.player.x=pos.first.toDouble(); e.player.y=pos.second.toDouble()
                e.castNormal(slot,ordinal)
                val distance=distanceToSafety(e,e.hazards)
                assertTrue("${boss.id}/$slot $pos",distance/e.job.speed+BossDifficulty(stage).reaction<=e.hazards.minOf { it.delay }+.001)
            }
    }
    @Test fun laterBossesGainDamageShorterWarningsAndMoreComboSteps() {
        for(stage in 1..31) {
            val before=BossDifficulty(stage-1); val now=BossDifficulty(stage)
            assertTrue(now.damage>before.damage); assertTrue(now.warning<before.warning)
            assertTrue(now.reaction<before.reaction); assertTrue(now.recovery<before.recovery)
            assertTrue(now.comboGap<before.comboGap); assertTrue(now.ultimateChance>before.ultimateChance)
        }
        val early=Bosses.all.take(8).flatMap { BossCombat.forBoss(it.id) }.map { it.waves }.average()
        val late=Bosses.all.takeLast(8).flatMap { BossCombat.forBoss(it.id) }.map { it.waves }.average()
        assertTrue(late>early+1)
        assertEquals(15.0,BossDifficulty(0).damage,.001)
        assertEquals(66.46,BossDifficulty(31).damage,.001)
    }
    @Test fun memoryAttacksKeepTheOriginalTargetWhileSnipersRetargetEachVisibleWave() {
        for((stage,slot,remembers) in listOf(Triple(4,0,true),Triple(10,1,false))) {
            val e=arena(stage); e.player.x=175.0; e.player.y=255.0
            e.castNormal(slot,0)
            val secondAt=e.normalCues.single().at
            assertTrue(secondAt>e.hazards.maxOf { it.delay+it.duration })
            e.player.x=470.0; e.player.y=280.0
            while(e.elapsed<secondAt+.04) { e.nextPattern=1e6; e.update(.02) }
            val fresh=e.hazards.filter { !it.resolved }
            assertTrue(fresh.isNotEmpty())
            if(remembers) { assertEquals(175.0,fresh.single().x,.01); assertEquals(255.0,fresh.single().y,.01) }
            else assertTrue(fresh.single().contains(e.player.x,e.player.y))
        }
    }
    @Test fun awakeningCancelsPendingNormalsAndEachNewBattleGetsOneCutin() {
        val e=arena(31); e.castNormal(0,0); assertTrue(e.normalCues.isNotEmpty())
        e.damageBoss(900.0)
        assertEquals(450.0,e.boss.hp,.001); assertEquals(Screen.CUTIN,e.screen)
        assertTrue(e.normalCues.isEmpty()); assertTrue(e.hazards.isEmpty())
        e.pause(); val cutin=e.cutinTime; repeat(100) { e.update(.05) }
        assertEquals(cutin,e.cutinTime,.0)
        e.unpause(); repeat(65) { e.update(.05) }
        assertTrue(e.hazards.all { it.ultimate }); assertTrue(e.ultimateActive)
        val count=e.ultimateCount; e.beginBattle()
        assertTrue(count>0); assertEquals(0,e.ultimateCount); assertFalse(e.ultimateUsed); assertTrue(e.normalCues.isEmpty())
        e.damageBoss(e.boss.maxHp)
        assertEquals(Screen.CUTIN,e.screen); assertEquals(1,e.ultimateCount); assertEquals(3.0,e.cutinTime,.0)
    }
    @Test fun fullEncountersMixRepeatedUltimatesWithNormalsWithoutOverlappingSequences() {
        fun fight(seed: Int): List<String> {
            val e=arena(31,seed); val events=mutableListOf<String>(); var lastName=""; var lastUlt=0
            // No random ultimate can precede the required HP threshold.
            repeat(700) { e.update(.05) }
            assertEquals(0,e.ultimateCount)
            e.damageBoss(700.0)
            repeat(4200) {
                e.update(.05)
                if(e.ultimateCount>lastUlt) {
                    assertTrue(e.hazards.isEmpty()); assertTrue(e.normalCues.isEmpty())
                    assertEquals(if(e.ultimateCount==1) Screen.CUTIN else Screen.BATTLE,e.screen)
                    events.add("ULT"); lastUlt=e.ultimateCount
                    if(lastUlt>1) {
                        assertEquals(.0,e.cutinTime,.0); assertTrue(e.cues.isNotEmpty())
                        e.moveX=1.0; e.player.x=100.0; e.heldSkill=2; e.cooldowns[2]=999.0
                        val time=e.elapsed; val x=e.player.x
                        e.update(.02)
                        assertTrue(e.elapsed>time); assertTrue(e.player.x>x); assertEquals(2,e.heldSkill)
                        e.moveX=.0; e.heldSkill=-1
                        val pauseTime=e.elapsed
                        e.pause(); repeat(4) { e.update(.05) }; e.unpause()
                        assertEquals(pauseTime,e.elapsed,.0); assertEquals(Screen.BATTLE,e.screen)
                    }
                }
                if(e.castName!=lastName) {
                    if(e.hazards.any { !it.ultimate && !it.resolved }) events.add(e.castName)
                    lastName=e.castName
                }
                assertFalse(e.hazards.any { it.ultimate && !it.resolved } && e.hazards.any { !it.ultimate && !it.resolved })
                if(e.cues.isNotEmpty()) assertTrue(e.normalCues.isEmpty())
            }
            assertTrue(e.ultimateCount>=3)
            val ultimatePositions=events.indices.filter { events[it]=="ULT" }
            assertTrue(ultimatePositions.zipWithNext().all { (a,b) -> b-a>1 })
            return events
        }
        assertEquals(fight(71),fight(71))
        assertNotEquals(fight(71),fight(91))
    }
    @Test fun randomDirectorLimitsDroughtsAndEveryBossHasItsOwnColorPair() {
        for(stage in listOf(0,15,31)) {
            val d=BossAttackDirector(Random(stage)); d.ultimateStarted(); var normals=0; var lastUltimate=true
            repeat(200) {
                val choice=d.next(3,true,BossDifficulty(stage).ultimateChance)
                if(choice<0) { assertFalse(lastUltimate); assertTrue(normals in 1..3); d.ultimateStarted(); normals=0; lastUltimate=true }
                else { normals++; lastUltimate=false }
            }
        }
        assertEquals(Bosses.all.map { it.id }.toSet(),UltimateColors.all.keys)
        assertEquals(32,UltimateColors.all.values.map { it.energy to it.accent }.toSet().size)
    }
    @Test fun halfHpAwakensAndQuarterHpEnablesActualDoubleCasts() {
        val e=arena(0)
        e.damageBoss(449.0)
        assertFalse(e.ultimateUsed); assertEquals(Screen.BATTLE,e.screen)
        e.damageBoss(1.0)
        assertEquals(Screen.CUTIN,e.screen); assertEquals(450.0,e.boss.hp,.0)
        repeat(61) { e.update(.05) }
        assertFalse(e.enraged)
        e.damageBoss(224.0); assertFalse(e.enraged)
        e.damageBoss(1.0); assertTrue(e.enraged)
        e.hazards.clear(); e.cues.clear(); e.castNormal(0,0)
        assertTrue(e.hazards.size>=2)
        assertTrue(e.castHint.startsWith("二重詠唱"))
        assertEquals(1,e.ultimateCount)
    }
    @Test fun everyQuarterHpPairLeavesAReachableSharedEscapeAndHitsSimultaneously() {
        for((stage,boss) in Bosses.all.withIndex())
            for(pos in listOf(20 to 20,580 to 20,20 to 310,580 to 310,300 to 265)) for(ordinal in 0..1) {
                fun enraged()=arena(stage).apply {
                    ultimateUsed=true; this.boss.hp=225.0
                    player.x=pos.first.toDouble(); player.y=pos.second.toDouble()
                }
                for(slot in boss.attackNames.indices) {
                    val e=enraged(); e.castNormal(slot,ordinal)
                    val a=NormalArena(e,e.player.x,e.player.y,ordinal)
                    BossCombat.forBoss(boss.id)[slot].draw(a,0)
                    assertTrue("${boss.id}/$slot $pos needs two techniques",e.hazards.size>a.hazards.size)
                    assertEquals(1,e.hazards.map { it.delay }.distinct().size)
                    assertTrue(distanceToSafety(e,e.hazards)/e.job.speed+BossDifficulty(stage).reaction<=e.hazards.first().delay+.001)
                }
                for((wave,pattern) in boss.sequence.withIndex()) {
                    val e=enraged(); e.cast(pattern,true,wave+ordinal)
                    val delay=e.hazards.minOf { it.delay }
                    val first=e.hazards.filter { abs(it.delay-delay)<.001 }
                    assertTrue("${boss.id}/$pattern needs a simultaneous normal",first.any { !it.ultimate })
                    assertTrue(first.any { it.ultimate })
                    assertTrue("${boss.id}/$pattern $pos needs a shared escape",distanceToSafety(e,first)/e.job.speed+.2<=delay+.001)
                }
            }
    }
    @Test fun quarterHpEncountersKeepRandomUltimatesAndOnlyOneCutinForAllBosses() {
        for(stage in 0..31) {
            val e=arena(stage)
            e.damageBoss(900.0); assertEquals(Screen.CUTIN,e.screen)
            repeat(61) { e.update(.05) }; e.damageBoss(225.0)
            var mixed=false; var normal=false
            repeat(2000) {
                e.update(.05)
                assertEquals("stage $stage repeats must not interrupt",Screen.BATTLE,e.screen)
                val active=e.hazards.filter { !it.resolved }
                if(active.any { it.ultimate } && active.any { !it.ultimate }) mixed=true
                if(active.isNotEmpty() && active.none { it.ultimate }) normal=true
            }
            assertTrue("stage $stage",mixed && normal)
            assertTrue(e.ultimateCount>1)
        }
    }
    @Test fun baselineBossTimingIsHalvedAndAllFourJobsMoveTwentyPercentFaster() {
        assertEquals(1.85/2,BossDifficulty(0).warning,.0)
        assertEquals(.65/2,BossDifficulty(0).comboGap,.0)
        assertEquals(1.05/2,BossDifficulty(0).recovery,.0)
        assertEquals((1.85-.60)/2,BossDifficulty(31).warning,.0001)
        for((i,job) in Job.entries.withIndex()) {
            val e=arena().apply { run=Run(job); nextPattern=1e9; moveX=1.0; moveY=1.0 }
            val x=e.player.x; val y=e.player.y
            repeat(10) { e.update(.02) }
            val oldSpeed=listOf(110.0,112.0,108.0,119.0)[i]
            assertEquals(oldSpeed*1.2*.2,hypot(e.player.x-x,e.player.y-y),.00001)
            e.pause(); repeat(10) { e.update(.02) }
            assertEquals(oldSpeed*1.2*.2,hypot(e.player.x-x,e.player.y-y),.00001)
        }
    }

    @Test fun doubleCastsStayValidAcrossRandomPositionsAndEveryNormalWave() {
        val random=Random(107)
        for(stage in 0..31) repeat(24) { ordinal ->
            val e=arena(stage).apply {
                ultimateUsed=true; boss.hp=225.0
                player.x=random.nextDouble(16.0,584.0); player.y=random.nextDouble(20.0,318.0)
            }
            e.castNormal(ordinal % BossCombat.forBoss(e.bossInfo.id).size,ordinal)
            var ticks=0
            while((e.normalCues.isNotEmpty() || e.hazards.any { !it.resolved }) && ticks++<1200) {
                e.nextPattern=1e6; e.update(.05)
            }
            assertTrue("stage $stage must complete every wave",e.normalCues.isEmpty() && e.hazards.none { !it.resolved })
            assertEquals(Screen.BATTLE,e.screen)
            assertTrue(e.player.hp>0)
        }
    }

}
