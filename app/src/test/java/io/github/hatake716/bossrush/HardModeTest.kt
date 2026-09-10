package io.github.hatake716.bossrush

import kotlin.math.min
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class HardModeTest {
    private fun arena(mode: GameMode,stage: Int=0,job: Job=Job.WARRIOR)=GameEngine(Random(5)).apply {
        run=Run(job,stage=stage,mode=mode); screen=Screen.BATTLE; nextPattern=1e6
        player=Actor(400.0,170.0,100000.0,100000.0)
        boss=Actor(100.0,170.0,9000.0,9000.0)
    }
    @Test fun everyBossHasTripleDamageAndTheSameHpAndPlayerStrength() {
        for(stage in 0..31) {
            val normal=arena(GameMode.NORMAL,stage); val hard=arena(GameMode.HARD,stage)
            assertEquals(normal.bossDamage()*3,hard.bossDamage(),1e-9)
        }
        for(job in Job.entries) {
            val normal=arena(GameMode.NORMAL,job=job); val hard=arena(GameMode.HARD,job=job)
            normal.beginBattle(); hard.beginBattle()
            assertEquals(normal.boss.maxHp,hard.boss.maxHp,0.0)
            assertEquals(normal.player.maxHp,hard.player.maxHp,0.0)
            for(slot in 0..3) assertEquals(normal.power(slot),hard.power(slot),0.0)
        }
    }
    @Test fun regularUltimateAndKnockbackHitsAllApplyTheMultiplierBeforeDefense() {
        for(shape in listOf("circle","knock")) for(ultimate in listOf(false,true)) for(defended in listOf(false,true)) {
            fun hit(mode: GameMode)=arena(mode).apply {
                if(defended) { buffs["armor"]=10.0; buffs["shield"]=10.0 }
                hazards.add(Hazard(shape,player.x-20,player.y,1000.0,delay=0.0,multiplier=1.4,ultimate=ultimate))
                update(.01)
            }.damageTaken
            val normal=hit(GameMode.NORMAL); assertTrue(normal>0)
            assertEquals(normal*3,hit(GameMode.HARD),1e-8)
        }
    }
    @Test fun actualChargeContactDoesTripleDamageExactlyOnce() {
        fun hit(mode: GameMode)=arena(mode,2).apply {
            castNormal(0,0); val move=bossMove!!; val end=move.start+move.travel+.2
            while(elapsed<end) update(min(.01,end-elapsed))
            assertTrue(move.hitPlayer)
            assertEquals(bossDamage(),damageTaken,1e-8)
        }.damageTaken
        assertEquals(hit(GameMode.NORMAL)*3,hit(GameMode.HARD),1e-8)
    }
    @Test fun hardModeStillHonorsInvincibilityAndHaniwaHitCharges() {
        val guarded=arena(GameMode.HARD,job=Job.SUMMONER)
        guarded.summons.add(Summon(2,400.0,170.0,20.0,level=16))
        repeat(4) { guarded.hurt(guarded.bossDamage()) }
        assertEquals(0.0,guarded.damageTaken,0.0); assertTrue(guarded.summons.isEmpty())
        guarded.hurt(guarded.bossDamage()); assertEquals(guarded.bossDamage(),guarded.damageTaken,0.0)
        val invisible=arena(GameMode.HARD); invisible.buffs["vanish"]=10.0
        invisible.hurt(invisible.bossDamage()); assertEquals(0.0,invisible.damageTaken,0.0)
    }
    @Test fun eachRewardAndGameOverContributionIsTripledWithoutMultiplyingExistingPointsAgain() {
        for(mode in GameMode.entries) {
            val e=arena(mode); val r=e.run!!; r.score=123
            e.elapsed=27.25; e.damageTaken=18.0; e.victory()
            val expected=GameEngine.scoreFor(27.25,18.0)*mode.scoreMultiplier
            assertEquals(expected,e.lastScore); assertEquals(123+expected,r.score); assertEquals(60,e.lastGold)
            e.victory(); assertEquals(123+expected,r.score)
            e.changeScreen(Screen.BATTLE); e.boss.hp=e.boss.maxHp*.61; e.player.hp=1.0
            e.hurt(100.0)
            assertEquals(123+expected+1170*mode.scoreMultiplier,r.score)
            e.hurt(100.0); assertEquals(123+expected+1170*mode.scoreMultiplier,r.score)
        }
    }
    @Test fun newRunAndResumePreserveModeAndNormalSelectionResetsIt() {
        val e=GameEngine(); e.selectedMode=GameMode.HARD; e.newRun(story=true)
        assertEquals(GameMode.HARD,e.run!!.mode); assertEquals(GameMode.HARD,e.run!!.copyForTrial().mode)
        e.selectedMode=GameMode.NORMAL; e.resumeRun(); assertEquals(GameMode.HARD,e.selectedMode)
        e.selectedMode=GameMode.NORMAL; e.newRun(); assertEquals(GameMode.NORMAL,e.mode)
    }
}
