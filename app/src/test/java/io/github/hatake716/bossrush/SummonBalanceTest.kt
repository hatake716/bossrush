package io.github.hatake716.bossrush

import kotlin.math.PI
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class SummonBalanceTest {
    private fun battle(level: Int=1,stage: Int=0)=GameEngine().apply {
        run=Run(Job.SUMMONER,stage,IntArray(4) { level }); beginBattle()
        boss=Actor(300.0,125.0,1e7,1e7); nextPattern=1e6
        hazards.add(Hazard("circle",-1000.0,-1000.0,1.0,delay=1e6))
    }
    private fun tick(e: GameEngine,seconds: Double) { repeat((seconds*100).roundToInt()) { e.update(.01) } }

    @Test fun guardianAbsorbsAreaRearAndUltimateDamageWithoutAddingToTheScorePenalty() {
        val attacks=listOf(
            Hazard("circle",300.0,270.0,50.0),
            Hazard("line",300.0,270.0,200.0,45.0,angle=PI/2,sourceX=300.0,sourceY=370.0),
            Hazard("cone",300.0,370.0,150.0,PI/2,angle=-PI/2),
            Hazard("ring",220.0,270.0,30.0,120.0),
            Hazard("safe",100.0,270.0,30.0),
            Hazard("tower",100.0,270.0,30.0),
            Hazard("circle",300.0,270.0,50.0,ultimate=true,multiplier=100.0),
            Hazard("knock",295.0,270.0,500.0)
        )
        for(attack in attacks) {
            val e=battle(); e.player.hp=1.0; assertTrue(e.useSkill(2))
            e.hazards.add(attack.copy(delay=.01)); e.update(.01)
            assertEquals(attack.shape,1.0,e.player.hp,0.0)
            assertEquals(0.0,e.damageTaken,0.0); assertEquals(Screen.BATTLE,e.screen)
            assertTrue(e.summons.isEmpty()); assertTrue(e.sounds.contains("haniwa"))
            assertTrue(e.playerEffects.any { it.kind==PlayerEffectKind.HANIWA })
            e.hurt(1.0); assertEquals(Screen.GAMEOVER,e.screen)
            assertEquals(1.0,e.run!!.totalDamage,0.0)
        }
    }

    @Test fun simultaneousAttacksConsumeOneGuardAndTheNextAttackHitsNormally() {
        val e=battle(); e.useSkill(2); e.buffs["armor"]=8.0
        repeat(2) { e.hazards.add(Hazard("circle",e.player.x,e.player.y,50.0,delay=.01)) }
        e.update(.01)
        assertTrue(e.summons.isEmpty())
        assertEquals(e.bossDamage()*.5,e.damageTaken,1e-8)
        assertEquals(e.player.maxHp-e.bossDamage()*.5,e.player.hp,1e-8)
    }

    @Test fun guardianInterceptsTheMovingBossAtContact() {
        val e=battle(stage=2); e.hazards.clear()
        e.boss.x=100.0; e.boss.y=170.0; e.player.x=400.0; e.player.y=170.0
        e.useSkill(2); e.castNormal(0,0)
        val move=e.bossMove!!
        while(e.elapsed<move.start+move.travel+.02) e.update(.01)
        assertTrue(move.hitPlayer); assertTrue(e.summons.isEmpty())
        assertEquals(0.0,e.damageTaken,0.0); assertEquals(e.player.maxHp,e.player.hp,0.0)
    }

    @Test fun guardianLifetimeGrowsOneSecondPerLevelAndPausesWithCombat() {
        for(level in 1..16) {
            val e=battle(level); e.useSkill(2)
            tick(e,level+3.99); assertEquals(1,e.summons.size)
            val life=e.summons.single().life
            e.pause(); tick(e,6.0); assertEquals(life,e.summons.single().life,0.0)
            e.unpause(); e.update(.01); assertTrue(e.summons.isEmpty())
            e.hurt(40.0); assertEquals(40.0,e.damageTaken,0.0)
        }
    }

    @Test fun meleeDoesNotRefreshTheGuardAndItsDisappearanceReleasesOnlyItsOwnSlot() {
        val e=battle(); e.useSkill(2); e.gauge=100.0; e.useSkill(0)
        e.gauge=100.0; assertFalse(e.useSkill(1))
        e.player.y=e.boss.y+40; tick(e,1.3)
        val guard=e.summons.first { it.kind==2 }; val life=guard.life; val before=e.damageDone
        assertTrue(e.useSkill(2)); assertEquals(life,guard.life,0.0)
        assertEquals(e.power(2),e.damageDone-before,1e-8)
        e.hurt(1000.0); assertEquals(1,e.normalSummons); assertEquals(0,e.summons.single().kind)
        e.gauge=100.0; assertTrue(e.useSkill(1)); assertEquals(2,e.normalSummons)
        val other=battle(16); other.useSkill(2); other.player.y=other.boss.y+40
        tick(other,1.3); other.hurt(40.0)
        val guardHits=other.summons.single().guardHits
        other.useSkill(2); assertEquals(guardHits,other.summons.single().guardHits)
        tick(other,18.7)
        assertTrue(other.summons.isEmpty())
    }

    @Test fun dodgedOrAlreadyInvulnerableAttacksDoNotConsumeTheGuard() {
        val e=battle(); e.useSkill(2)
        e.hazards.add(Hazard("circle",20.0,20.0,5.0,delay=.01)); e.update(.01)
        e.hurt(0.0); assertEquals(1,e.summons.size)
        e.invulnerability=.5; e.hurt(40.0); assertEquals(1,e.summons.size)
        e.invulnerability=0.0; e.buffs["invisible"]=6.0; e.hurt(40.0); assertEquals(1,e.summons.size)
        e.buffs.clear(); e.hurt(40.0); assertTrue(e.summons.isEmpty())
        assertEquals(e.player.maxHp,e.player.hp,0.0)
    }

    @Test fun rabbitHealingGrowsFromEightToSixteenOnTheSameTwoSecondInterval() {
        for(level in 1..16) {
            val e=battle(level,31); e.player.hp=1.0; e.buffs["power"]=10.0
            e.useSkill(1)
            val amount=8.0+8.0*(level-1)/15
            tick(e,.11); assertEquals(1.0+amount,e.player.hp,1e-8)
            tick(e,1.98); assertEquals(1.0+amount,e.player.hp,1e-8)
            tick(e,.02); assertEquals(1.0+amount*2,e.player.hp,1e-8)
        }
    }

    @Test fun durabilityGrowsAtLevelsSixElevenAndSixteenAndEachHitConsumesOneCharge() {
        val expected=intArrayOf(1,1,1,1,1,2,2,2,2,2,3,3,3,3,3,4)
        for(level in 1..16) {
            val e=battle(level); e.useSkill(2)
            val guard=e.summons.single(); val life=guard.life
            assertEquals(expected[level-1],guard.guardHits)
            repeat(expected[level-1]) { hit ->
                e.hurt(10000.0)
                assertEquals(expected[level-1]-hit-1,guard.guardHits)
                assertEquals(life,guard.life,0.0)
                assertEquals(e.player.maxHp,e.player.hp,0.0); assertEquals(0.0,e.damageTaken,0.0)
                assertEquals(if(hit==expected[level-1]-1) 0 else 1,e.normalSummons)
            }
            e.hurt(10.0); assertEquals(10.0,e.damageTaken,0.0)
        }
        assertEquals(1,Skills.haniwaDurability(-1)); assertEquals(4,Skills.haniwaDurability(99))
        assertEquals(5.0,Skills.summonDuration(2,-1),0.0); assertEquals(20.0,Skills.summonDuration(2,99),0.0)
    }

    @Test fun maxLevelGuardAbsorbsFourOverlappingAttacksAndTheFifthHurts() {
        val e=battle(16); e.useSkill(2)
        repeat(5) { e.hazards.add(Hazard("circle",e.player.x,e.player.y,50.0,delay=.01)) }
        e.update(.01)
        assertTrue(e.summons.isEmpty()); assertEquals(e.bossDamage(),e.damageTaken,1e-8)
        assertEquals(e.player.maxHp-e.bossDamage(),e.player.hp,1e-8)
    }

    @Test fun recastingAfterExhaustionCreatesFreshDurabilityWithoutReplacingTheOtherSummon() {
        val e=battle(16); e.useSkill(2); e.gauge=100.0; e.useSkill(1)
        tick(e,1.3); repeat(3) { e.hurt(1000.0) }
        assertEquals(2,e.normalSummons); assertEquals(1,e.summons.first { it.kind==2 }.guardHits)
        e.hurt(1000.0); assertEquals(1,e.normalSummons); assertEquals(1,e.summons.single().kind)
        e.gauge=100.0; assertTrue(e.useSkill(2))
        val fresh=e.summons.single { it.kind==2 }
        assertEquals(4,fresh.guardHits); assertEquals(20.0,fresh.life,0.0)
        assertEquals(2,e.normalSummons)
    }
}
