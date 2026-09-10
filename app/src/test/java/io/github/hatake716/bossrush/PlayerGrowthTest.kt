package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PlayerGrowthTest {
    private fun battle(job: Job,level: Int)=GameEngine().apply {
        run=Run(job,levels=IntArray(4) { level }); beginBattle()
        boss=Actor(300.0,125.0,1e7,1e7); player.x=300.0; player.y=165.0
        // An inert remote warning keeps the boss stationary without enabling trial mode.
        hazards.add(Hazard("circle",-1000.0,-1000.0,1.0,delay=1e6)); nextPattern=1e6
    }
    private fun tick(e: GameEngine,seconds: Double) { repeat((seconds*120).roundToInt()) { e.update(1.0/120) } }

    @Test fun allMeleeJobsGainRealReachAndExactlyFourTimesDamage() {
        for((job,slot,kind) in listOf(Triple(Job.WARRIOR,0,PlayerEffectKind.SWORD),Triple(Job.THIEF,0,PlayerEffectKind.KNIFE),Triple(Job.SUMMONER,2,PlayerEffectKind.HANIWA))) {
            val damages=mutableListOf<Double>()
            for(level in listOf(1,8,16)) {
                val e=battle(job,level)
                if(job==Job.SUMMONER) { e.useSkill(2); e.cooldowns[2]=0.0; e.gauge=100.0; e.playerEffects.clear() }
                val lowReach=Skills.range(job,slot,1)+PlayerAttackGeometry.BOSS_RADIUS
                e.player.y=e.boss.y+lowReach+10
                if(level==1) {
                    val gauge=e.gauge; assertFalse(e.useSkill(slot)); assertTrue(e.playerEffects.isEmpty()); assertEquals(gauge,e.gauge,0.0)
                    e.player.y=e.boss.y+40
                }
                assertTrue(e.useSkill(slot)); damages.add(e.damageDone)
                val visual=e.playerEffects.last(); assertEquals(kind,visual.kind); assertEquals(level,visual.level)
                assertEquals(Skills.range(job,slot,level),visual.radius,0.0)
                tick(e,.8); assertEquals("Visual trails must never deal bonus hits",damages.last(),e.damageDone,0.0)
            }
            assertEquals(damages.first()*4,damages.last(),1e-7)
        }
    }

    @Test fun upgradedArrowAndFireCatchGrazesThatLevelOneMissesAndHitOnlyOnce() {
        for((job,slot,offset) in listOf(Triple(Job.WARRIOR,2,33.0),Triple(Job.MAGE,0,74.0))) {
            for(level in listOf(1,16)) {
                val e=battle(job,level); e.player.y=285.0
                assertTrue(e.useSkill(slot)); val projectile=e.projectiles.single()
                assertEquals(level,projectile.level)
                e.boss.x+=offset
                tick(e,1.2)
                if(level==1) assertEquals(0.0,e.damageDone,0.0)
                else {
                    assertEquals(e.power(slot),e.damageDone,1e-7)
                    assertTrue(e.projectiles.isEmpty())
                    tick(e,2.0); assertEquals(e.power(slot),e.damageDone,1e-7)
                }
            }
        }
    }

    @Test fun fireImpactUsesContactPointAndFullBlastRadius() {
        val e=battle(Job.MAGE,16); e.player.y=285.0; e.useSkill(0)
        repeat(120) { if(e.playerEffects.none { it.kind==PlayerEffectKind.FIRE }) e.update(1.0/120) }
        val effect=e.playerEffects.single { it.kind==PlayerEffectKind.FIRE }
        assertEquals(60.8,effect.radius,1e-8)
        assertEquals(effect.radius+PlayerAttackGeometry.BOSS_RADIUS,hypot(effect.x-e.boss.x,effect.y-e.boss.y),1e-6)
        assertEquals(16,effect.level)
    }

    @Test fun upgradedIceCoversMoreGroundAfterItsAimLocks() {
        for(level in listOf(1,16)) {
            val e=battle(Job.MAGE,level); assertTrue(e.useSkill(1)); tick(e,.43)
            val mark=e.iceMarks.single(); assertTrue(mark.time<.35)
            e.boss.x+=74; tick(e,.34)
            assertEquals(if(level==1) 0.0 else e.power(1),e.damageDone,1e-8)
            val fx=e.playerEffects.single { it.kind==PlayerEffectKind.ICE }
            assertEquals(mark.radius,fx.radius,0.0); assertEquals(300.0,fx.x,0.0); assertEquals(level,fx.level)
        }
    }

    @Test fun bothGiantReachAndRabbitHealingRangeGrow() {
        for(level in listOf(1,16)) {
            val e=battle(Job.SUMMONER,level); e.player.hp=10.0
            e.summons.add(Summon(0,e.boss.x-103,e.boss.y,12.0,timer=0.0,level=level))
            e.summons.add(Summon(1,e.player.x-103,e.player.y,12.0,timer=0.0,level=level))
            e.update(.001)
            assertEquals(if(level==1) 0.0 else e.power(0),e.damageDone,1e-8)
            assertEquals(if(level==1) 10.0 else 10.0+16,e.player.hp,1e-8)
            if(level==16) assertEquals(setOf(PlayerEffectKind.GIANT,PlayerEffectKind.HEAL),e.playerEffects.map { it.kind }.toSet())
        }
    }

    @Test fun everySuccessfulSkillCarriesItsOwnLevelAndRejectionDoesNotFlash() {
        for(job in Job.entries) for(slot in 0..3) for(level in listOf(1,8,16)) {
            val e=battle(job,level); if(slot==3) e.player.hp=10.0
            assertTrue(e.useSkill(slot))
            val recorded=e.playerEffects.map { it.level }+e.projectiles.map { it.level }+e.iceMarks.map { it.level }
            assertTrue("$job $slot must show a level-aware cast",recorded.isNotEmpty()); assertTrue(recorded.all { it==level })
            val count=e.playerEffects.size; assertFalse(e.useSkill(slot)); assertEquals(count,e.playerEffects.size)
        }
    }

    @Test fun finisherEffectsUseFourthSkillLevelAndPauseFreezesThem() {
        for(job in Job.entries) {
            val e=battle(job,16); e.player.hp=10.0; e.useSkill(3); tick(e,.1)
            assertTrue(e.playerEffects.isNotEmpty()); assertTrue(e.playerEffects.all { it.level==16 })
            val ages=e.playerEffects.map { it.age }
            e.pause(); tick(e,1.0); assertEquals(ages,e.playerEffects.map { it.age })
            e.unpause(); tick(e,.2); assertEquals(10.0,e.player.hp,0.0)
            e.beginBattle(); assertTrue(e.playerEffects.isEmpty()); assertFalse(e.playerFinisherUsed)
        }
    }
    @Test fun continuousAttacksCannotAccumulateUnboundedEffectsAndTrialsHaveNone() {
        val e=battle(Job.WARRIOR,16)
        repeat(90) { e.gauge=100.0; e.cooldowns[0]=0.0; e.useSkill(0) }
        assertEquals(PlayerEffect.LIMIT,e.playerEffects.size)
        tick(e,1.0); assertTrue(e.playerEffects.isEmpty())
        val trial=GameEngine().apply { run=Run(Job.MAGE,levels=IntArray(4) { 16 }); beginBattle(true) }
        repeat(1800) { trial.idealActions(); trial.update(1.0/60) }
        assertTrue(trial.damageDone>0); assertTrue(trial.playerEffects.isEmpty())
    }

    @Test fun contactGeometryFindsFastCrossingWithoutInventingHitsAcrossTeleports() {
        assertEquals(.375,PlayerAttackGeometry.contactFraction(-100.0,0.0,100.0,0.0,25.0)!!,1e-8)
        assertNull(PlayerAttackGeometry.contactFraction(-100.0,26.0,100.0,26.0,25.0))
        assertNull(PlayerAttackGeometry.contactFraction(-100.0,0.0,-100.0,0.0,25.0))
        assertEquals(0.0,PlayerAttackGeometry.contactFraction(10.0,0.0,10.0,0.0,25.0)!!,0.0)
    }
}
