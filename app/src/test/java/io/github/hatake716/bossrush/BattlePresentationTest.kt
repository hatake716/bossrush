package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test

internal fun GameEngine.finishDefeatAnimation() { repeat(37) { update(.05) } }

class BattlePresentationTest {
    @Test fun cutinWaitsForAcknowledgementAndPreservesCombatStateAcrossPause() {
        val e=GameEngine().apply { newRun(); beginBattle(); cooldowns[0]=2.0; buffs["power"]=5.0 }
        e.damageBoss(e.boss.maxHp)
        val hp=e.player.hp; val elapsed=e.elapsed; val gauge=e.gauge
        repeat(1200) { e.update(.05) }
        assertEquals(Screen.CUTIN,e.screen); assertTrue(e.awaitingCutin)
        assertEquals(elapsed,e.elapsed,0.0); assertEquals(hp,e.player.hp,0.0)
        assertEquals(gauge,e.gauge,0.0); assertEquals(2.0,e.cooldowns[0],0.0)
        assertEquals(5.0,e.buffs.getValue("power"),0.0); assertTrue(e.cues.isEmpty())
        e.pause(); assertFalse(e.dismissCutin()); e.unpause()
        assertEquals(Screen.CUTIN,e.screen); assertTrue(e.dismissCutin())
        val count=e.cues.size
        assertTrue(count>0); assertFalse(e.dismissCutin()); assertEquals(count,e.cues.size)
        e.update(.05); assertTrue(e.elapsed>elapsed); assertFalse(e.awaitingCutin)
    }
    @Test fun allBossesExplodeBeforeRewardWithoutChangingScoreOrGrantingTwice() {
        for(stage in 0..31) {
            val e=GameEngine().apply { newRun(); run!!.stage=stage; beginBattle(); ultimateUsed=true }
            e.elapsed=30.0; e.damageTaken=12.0; e.moveX=1.0; e.heldSkill=0
            e.castNormal(0,0)
            e.damageBoss(e.boss.maxHp)
            assertEquals(Screen.DEFEAT,e.screen); assertEquals(0.0,e.boss.hp,0.0)
            assertNull(e.bossMove); assertTrue(e.hazards.isEmpty()); assertTrue(e.cues.isEmpty()); assertTrue(e.normalCues.isEmpty())
            assertEquals(-1,e.heldSkill); assertEquals(0.0,e.moveX,0.0)
            val gold=e.run!!.gold; val score=e.run!!.score; val hp=e.player.hp
            repeat(20) { e.update(.05); e.victory(); e.hurt(10000.0); assertFalse(e.useSkill(0)); assertFalse(e.selectUpgrade(0)) }
            assertEquals(Screen.DEFEAT,e.screen); assertEquals(30.0,e.elapsed,0.0); assertEquals(hp,e.player.hp,0.0)
            e.finishDefeatAnimation(); assertEquals(Screen.REWARD,e.screen)
            e.victory(); assertEquals(1,e.run!!.kills); assertEquals(gold,e.run!!.gold); assertEquals(score,e.run!!.score)
            assertEquals(GameEngine.scoreFor(30.0,12.0),score); assertEquals(30.0,e.run!!.totalTime,0.0)
            assertEquals(-1,e.pendingUpgrade); assertEquals(1,e.sounds.count { it=="boss-defeat" }); assertEquals(1,e.sounds.count { it=="victory" })
        }
    }
}
