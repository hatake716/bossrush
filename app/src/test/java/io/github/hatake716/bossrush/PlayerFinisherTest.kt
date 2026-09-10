package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PlayerFinisherTest {
    private fun battle(job: Job, level: Int=1)=GameEngine().apply {
        run=Run(job,levels=intArrayOf(1,1,1,level)); beginBattle()
        nextPattern=1e6; boss=Actor(300.0,145.0,1e6,1e6)
        player.hp=player.maxHp/3
        hazards.add(Hazard("circle",-1000.0,-1000.0,1.0,delay=1e6))
    }
    private fun tick(e: GameEngine, seconds: Double) { repeat((seconds*100).roundToInt()) { e.update(.01) } }
    @Test fun exactHpThresholdOneUseAndNoGaugeOrCooldownRequirementForEveryJob() {
        for(job in Job.entries) {
            val e=battle(job); e.gauge=0.0; e.cooldowns.fill(99.0)
            e.player.hp=e.player.maxHp/3+.00001
            assertFalse(e.canUsePlayerFinisher); assertFalse(e.useSkill(3)); assertFalse(e.playerFinisherUsed)
            assertTrue(e.playerEffects.isEmpty()); assertTrue(e.sounds.isEmpty())
            e.player.hp=e.player.maxHp/3
            assertTrue(e.canUsePlayerFinisher); assertTrue(e.useSkill(3)); assertEquals(0.0,e.gauge,0.0)
            assertTrue(e.playerFinisherUsed); assertFalse(e.canUsePlayerFinisher)
            assertFalse(e.useSkill(3)); val hits=e.playerFinisherHits
            e.run!!.inventory.add(Item.HOURGLASS); e.useItem(e.run!!.inventory.lastIndex)
            assertFalse(e.useSkill(3)); assertEquals(hits,e.playerFinisherHits)
            e.heal(e.player.maxHp); e.invulnerability=0.0; e.buffs.clear(); e.hurt(e.player.maxHp*.8)
            assertFalse(e.useSkill(3))
            e.beginBattle(); e.player.hp=e.player.maxHp/3
            assertTrue(e.useSkill(3))
        }
    }
    @Test fun zeroHpAndNonBattleScreensRejectWithoutSpendingUse() {
        for(job in Job.entries) {
            val e=battle(job); e.player.hp=0.0; assertFalse(e.useSkill(3))
            e.player.hp=1.0
            for(screen in listOf(Screen.PAUSED,Screen.INTRO,Screen.CUTIN,Screen.REWARD,Screen.GAMEOVER)) {
                e.screen=screen; assertFalse(e.useSkill(3)); assertFalse(e.playerFinisherUsed)
            }
        }
    }
    @Test fun warriorDealsExactlyTenStrongHitsInLessThanAQuarterSecondAtAnyDistance() {
        for(level in listOf(1,8,16)) {
            val e=battle(Job.WARRIOR,level); e.player.x=20.0; e.player.y=310.0
            val hit=e.power(3); val hp=e.player.hp
            assertTrue(hit>Skills.power(Job.WARRIOR,0,level)); assertTrue(e.useSkill(3))
            tick(e,.2)
            assertEquals(10,e.playerFinisherHits); assertEquals(10*hit,e.damageDone,1e-7)
            assertFalse(e.playerFinisherActive); assertEquals(hp,e.player.hp,0.0)
            assertEquals(10,e.playerEffects.count { it.kind==PlayerEffectKind.LIMIT_SLASH })
            tick(e,1.0); assertEquals(10*hit,e.damageDone,1e-7)
        }
    }
    @Test fun mageExplodesFiveTimesAcrossEntireArenaAndFreezesWhenPaused() {
        val e=battle(Job.MAGE); e.player.x=20.0; e.player.y=315.0
        val hit=e.power(3); e.useSkill(3); assertEquals(1,e.playerFinisherHits)
        e.pause(); tick(e,3.0); assertEquals(hit,e.damageDone,1e-7)
        e.unpause()
        for(i in 1..4) {
            e.boss.x=if(i%2==0) 580.0 else 20.0; e.boss.y=if(i%2==0) 25.0 else 315.0
            tick(e,.24); assertEquals(i+1,e.playerFinisherHits)
        }
        assertEquals(hit*5,e.damageDone,1e-7); assertFalse(e.playerFinisherActive)
        assertTrue(e.playerEffects.filter { it.kind==PlayerEffectKind.LIMIT_FLARE }.all { it.x==300.0 && it.radius==340.0 })
    }
    @Test fun mandatoryBossCutinPreservesBurstCountAndEveryPointOfDamage() {
        for(job in listOf(Job.WARRIOR,Job.MAGE)) {
            val e=battle(job); e.boss=Actor(300.0,145.0,5010.0,10000.0)
            val total=e.power(3)*(if(job==Job.WARRIOR) 10 else 5)
            e.useSkill(3); assertEquals(Screen.CUTIN,e.screen); assertEquals(10.0,e.damageDone,1e-7)
            tick(e,10.0); assertEquals(Screen.CUTIN,e.screen); e.dismissCutin()
            e.hazards.clear(); e.cues.clear(); e.normalCues.clear(); e.nextPattern=1e6; e.invulnerability=1e6
            tick(e,1.1)
            assertEquals(total,e.damageDone,1e-7)
            assertEquals(if(job==Job.WARRIOR) 10 else 5,e.playerFinisherHits)
            assertEquals(1,e.ultimateCount); assertFalse(e.playerFinisherActive)
        }
    }
    @Test fun defeatAndNewEncounterDiscardRemainingHits() {
        val e=battle(Job.MAGE); e.ultimateUsed=true; e.boss.hp=10.0
        e.useSkill(3); assertEquals(Screen.DEFEAT,e.screen); assertEquals(1,e.run!!.kills)
        assertFalse(e.playerFinisherActive); tick(e,2.0); assertEquals(1,e.run!!.kills)
        e.beginBattle(); tick(e,.1); assertEquals(0.0,e.damageDone,0.0); assertFalse(e.playerFinisherUsed)
        e.player.hp=1.0; e.useSkill(3); e.hurt(10000.0)
        assertEquals(Screen.GAMEOVER,e.screen); assertFalse(e.playerFinisherActive)
    }
    @Test fun fiveGiantsAreIndependentOfTwoNormalSlotsAndUseFourthSkillGrowth() {
        for(level in listOf(1,16)) {
            val e=battle(Job.SUMMONER,level)
            e.useSkill(1); e.gauge=100.0; e.useSkill(2); e.gauge=0.0
            assertTrue(e.useSkill(3)); assertEquals(7,e.summons.size); assertEquals(2,e.normalSummons)
            val army=e.summons.filter { it.finisher }; assertEquals(5,army.size)
            assertEquals((0..4).toSet(),army.map { it.formation }.toSet())
            assertTrue(army.all { it.kind==0 && it.level==level && it.life==Skills.finisherDuration(Job.SUMMONER,level) })
            e.gauge=100.0; assertFalse(e.useSkill(0))
            // Remove normal summons to isolate the army's damage and check the independent quota.
            e.summons.removeAll { !it.finisher }
            tick(e,1.3); assertTrue(e.damageDone>=5*e.power(3))
            assertTrue(e.useSkill(2)); assertEquals(1,e.normalSummons); assertEquals(6,e.summons.size)
            e.pause(); val life=army.map { it.life }; tick(e,1.0); assertEquals(life,army.map { it.life }); e.unpause()
            tick(e,Skills.finisherDuration(Job.SUMMONER,level)); assertTrue(e.summons.none { it.finisher })
        }
    }
    @Test fun thiefIsInvisibleAndInvulnerableForExactlyTenCombatSecondsAtEveryLevel() {
        for(level in listOf(1,8,16)) {
            val e=battle(Job.THIEF,level); val hp=e.player.hp
            e.useSkill(3); assertTrue(e.isInvisible)
            val x=e.player.x; e.moveX=1.0; tick(e,.1); assertTrue(e.player.x>x); e.moveX=0.0
            e.player.x=e.boss.x; e.player.y=e.boss.y+35
            assertTrue(e.useSkill(0)); assertTrue(e.damageDone>0)
            e.hurt(10000.0); assertEquals(hp,e.player.hp,0.0); assertEquals(0.0,e.damageTaken,0.0)
            e.run!!.inventory.add(Item.INVISIBLE); e.useItem(e.run!!.inventory.lastIndex)
            assertEquals(9.9,e.buffs.getValue("vanish"),1e-8)
            e.pause(); tick(e,2.0); assertEquals(9.9,e.buffs.getValue("vanish"),1e-8); e.unpause()
            tick(e,9.89); e.hurt(10000.0); assertEquals(hp,e.player.hp,0.0)
            tick(e,.01); assertFalse(e.isInvisible); e.hurt(1.0); assertEquals(hp-1,e.player.hp,1e-8)
        }
    }
    @Test fun thiefVanishAlsoRejectsBossKnockback() {
        val e=battle(Job.THIEF); e.useSkill(3); val x=e.player.x; val y=e.player.y
        e.hazards.add(Hazard("knock",300.0,125.0,100.0,delay=0.0)); e.update(.01)
        assertEquals(x,e.player.x,0.0); assertEquals(y,e.player.y,0.0)
    }
}
