package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PlayerComboTest {
    private fun battle(job: Job,level: Int=1)=GameEngine().apply {
        run=Run(job,levels=IntArray(4) { level }); beginBattle()
        boss=Actor(300.0,145.0,1e7,1e7); player.x=300.0; player.y=185.0
        nextPattern=1e6; hazards.add(Hazard("circle",-1000.0,-1000.0,1.0,delay=1e6))
    }
    private fun tick(e: GameEngine,seconds: Double) { repeat((seconds*120).roundToInt()) { e.update(1.0/120) } }
    private fun ready(e: GameEngine,slot: Int) { e.gauge=100.0; e.cooldowns[slot]=0.0 }

    @Test fun successfulMeleeUsesCycleThroughThreeDistinctAttacksAndFailedInputDoesNotAdvance() {
        for(job in listOf(Job.WARRIOR,Job.THIEF)) {
            val e=battle(job,16)
            for(stage in listOf(1,2,3,1)) {
                ready(e,0); val before=e.damageDone
                assertTrue(e.useSkill(0)); assertEquals(stage,e.playerEffects.last().combo)
                assertEquals(e.power(0)*listOf(1.0,1.15,1.35)[stage-1],e.damageDone-before,1e-7)
                val next=e.combos.next(job,0,e.elapsed)
                assertFalse(e.useSkill(0)); assertEquals(next,e.combos.next(job,0,e.elapsed))
                ready(e,0); e.player.y=315.0
                assertFalse(e.useSkill(0)); assertEquals(next,e.combos.next(job,0,e.elapsed)); assertEquals(100.0,e.gauge,0.0)
                e.player.y=185.0
            }
        }
    }

    @Test fun magicTracksTwoIndependentCombosAndPausePreservesTheirWindow() {
        val e=battle(Job.MAGE)
        assertTrue(e.useSkill(0)); assertTrue(e.useSkill(1)); assertTrue(e.useSkill(2))
        assertEquals(2,e.combos.next(e.job,0,e.elapsed)); assertEquals(2,e.combos.next(e.job,1,e.elapsed))
        e.pause(); tick(e,20.0); e.unpause()
        assertEquals(2,e.combos.next(e.job,0,e.elapsed))
        ready(e,0); assertTrue(e.useSkill(0)); assertEquals(3,e.combos.next(e.job,0,e.elapsed))
        assertEquals(2,e.combos.next(e.job,1,e.elapsed))
        tick(e,5.0)
        assertEquals(1,e.combos.next(e.job,0,e.elapsed)); assertEquals(1,e.combos.next(e.job,1,e.elapsed))
        e.beginBattle(); assertEquals(1,e.combos.next(e.job,0,e.elapsed))
    }

    @Test fun magicGainsRealProjectilesAtEveryGrowthStepAndSharesThePowerBudget() {
        for(level in 1..16) for(slot in 0..1) {
            val e=battle(Job.MAGE,level)
            for(stage in 1..3) {
                ready(e,slot); e.projectiles.clear(); e.iceMarks.clear(); e.playerEffects.clear()
                val before=e.damageDone; assertTrue(e.useSkill(slot))
                val expectedCount=1+2*((level-1)/3)+2*(stage-1)
                val total=e.power(slot)*listOf(1.0,1.15,1.35)[stage-1]
                if(slot==0) {
                    assertEquals(expectedCount,e.projectiles.size)
                    assertEquals(total,e.projectiles.sumOf { it.power },1e-7)
                } else {
                    tick(e,.76)
                    assertEquals(expectedCount-1,e.projectiles.size)
                    assertEquals(total,e.damageDone-before+e.projectiles.sumOf { it.power },1e-7)
                }
                tick(e,1.0)
                assertEquals("All rays hit a stationary nearby boss, $slot/$level/$stage",total,e.damageDone-before,1e-6)
                tick(e,2.2); assertEquals(total,e.damageDone-before,1e-6)
                // Finish before the next combo expires; deliberately control game time for this fixture.
                e.elapsed-=3.2+if(slot==1) .76 else 0.0
            }
        }
    }

    @Test fun firedIceCrystalsKeepTheirLockedTargetAndCanMissAMovingBoss() {
        val e=battle(Job.MAGE,16); e.useSkill(1); tick(e,.76)
        assertEquals(10,e.projectiles.size)
        val velocities=e.projectiles.map { it.vx to it.vy }
        val initial=e.damageDone
        e.boss.x=560.0; e.boss.y=285.0
        tick(e,.5)
        assertEquals(initial,e.damageDone,1e-8)
        assertEquals(velocities,e.projectiles.map { it.vx to it.vy })
        tick(e,3.0); assertTrue(e.projectiles.isEmpty())
    }

    @Test fun cutinFreezesUnspentVolleyShotsAndDoesNotConsumeThemInTheBackground() {
        for(slot in 0..1) {
            val e=battle(Job.MAGE,16); e.boss.hp=e.boss.maxHp/2+1
            assertTrue(e.useSkill(slot))
            repeat(200) { if(e.screen==Screen.BATTLE) e.update(1.0/120) }
            assertEquals(Screen.CUTIN,e.screen)
            assertEquals(10,e.projectiles.size)
            val shots=e.projectiles.map { it.copy() }; val age=e.elapsed
            tick(e,3.0); assertEquals(shots,e.projectiles); assertEquals(age,e.elapsed,0.0)
            assertEquals(2,e.combos.next(e.job,slot,e.elapsed))
            e.dismissCutin(); e.hazards.clear(); e.cues.clear(); e.normalCues.clear(); e.nextPattern=1e6
            e.hazards.add(Hazard("circle",-1000.0,-1000.0,1.0,delay=1e6)); tick(e,1.0)
            assertTrue(e.damageDone>1); assertTrue(e.projectiles.isEmpty())
        }
    }

    @Test fun barrageAllocationHasABoundAndRejectionSpendsNothing() {
        val e=battle(Job.MAGE,16)
        repeat(40) {
            ready(e,1); val next=e.combos.next(e.job,1,e.elapsed)
            if(!e.useSkill(1)) {
                assertEquals(100.0,e.gauge,0.0); assertEquals(0.0,e.cooldowns[1],0.0)
                assertEquals(next,e.combos.next(e.job,1,e.elapsed))
            }
            assertTrue(e.projectiles.size+e.iceMarks.sumOf { PlayerMagic.count(it.level,it.combo)-1 }<=128)
        }
        tick(e,.8); assertTrue(e.projectiles.size<=128); assertTrue(e.playerEffects.size<=PlayerEffect.LIMIT)
        e.victory(); assertTrue(e.projectiles.isEmpty()); assertTrue(e.iceMarks.isEmpty()); assertTrue(e.playerEffects.isEmpty())
    }

    @Test fun summonedPunchesAnimateAndFreezeWithoutASecondDamageApplication() {
        val e=battle(Job.SUMMONER,16); e.summons.add(Summon(0,265.0,165.0,20.0,timer=0.0,level=16))
        e.update(.001); val giant=e.summons.single(); val damage=e.damageDone
        assertEquals(0.0,giant.punchAge,0.0)
        tick(e,.13); assertTrue(SummonPunch.extension(giant.punchAge)>.95)
        e.pause(); val age=giant.punchAge; tick(e,3.0); assertEquals(age,giant.punchAge,0.0)
        e.unpause(); tick(e,.4); assertEquals(damage,e.damageDone,0.0); assertEquals(0.0,SummonPunch.extension(giant.punchAge),0.0)
        ready(e,2); assertTrue(e.useSkill(2)); ready(e,2); assertTrue(e.useSkill(2))
        val haniwa=e.summons.first { it.kind==2 }; assertEquals(0.0,haniwa.punchAge,0.0)
        assertEquals(4,haniwa.guardHits); assertEquals(20.0,haniwa.life,0.0)
    }
}
