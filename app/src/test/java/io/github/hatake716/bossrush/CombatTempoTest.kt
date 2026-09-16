package io.github.hatake716.bossrush

import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class CombatTempoTest {
    @Test fun allBossTravelTimesAndBarrageClocksAreEightyPercentOf121() {
        val oldTravel=listOf(.38,.44,.42,.40,.28,.44,.37,.42,.48,.43,.35,.39,.52,.30,.46,.55,
            .36,.32,.50,.38,.54,.50,.26,.55,.46,.38,.36,.34,.32,.24,.46,.36)
        for((stage,boss) in Bosses.all.withIndex()) {
            val profile=BossMobility.forBoss(boss.id)
            assertEquals(boss.id,oldTravel[stage]/1.25,profile.tempo,1e-9)
            val v=EnemyVolley(EnemyBarrage.forBoss(boss.id),stage,0.0,0,300.0,175.0)
            assertEquals((.75-.20*stage/31.0)/1.25,v.warning,1e-9)
            assertEquals(v.profile.rhythm/1.25,v.interval,1e-9)
            assertEquals((68.0+40.0*stage/31.0)*1.25,v.speed,1e-9)
            val end=v.warning+(v.profile.waves-1)*v.interval+v.bullets(0).first().life
            val oldEnd=.75-.20*stage/31.0+(v.profile.waves-1)*v.profile.rhythm+3.6
            assertEquals("A denser barrage must not lengthen the attack cycle",oldEnd/1.25,end,1e-9)
            assertEquals(.16,EnemyBullet.ARM_TIME,1e-9)
        }
    }

    @Test fun everyBossHasMoreBulletsWithShiftedGapsAndAllWavesFitTheBudget() {
        for((stage,boss) in Bosses.all.withIndex()) {
            val p=EnemyBarrage.forBoss(boss.id);val v=EnemyVolley(p,stage,0.0,0,300.0,175.0)
            assertTrue(boss.id,v.count>p.count)
            assertTrue(boss.id,v.count*p.waves<=EnemyBarrage.MAX_BULLETS)
            assertEquals(v.count,v.bullets(0).size)
            val a=v.bullets(0).map { it.heading };val b=v.bullets(1).map { it.heading }
            assertNotEquals(boss.id,a,b)
            if(p.pattern==BarragePattern.CROSS) assertEquals(0,v.count%4)
            if(p.pattern==BarragePattern.PAIRS) assertEquals(0,v.count%2)
        }
        assertEquals(10,EnemyVolley(EnemyBarrage.forBoss("ratatoskr"),0,0.0,0,0.0,0.0).count*2)
        assertEquals(155,EnemyVolley(EnemyBarrage.forBoss("odin"),31,0.0,0,0.0,0.0).count*5)
    }

    @Test fun allDenserBarragesOfferADamageFreeMovingRouteFromMeleeDistance() {
        // Try straight evasions from 50 units away using the slowest job, starting
        // at the visible charge. No invincibility, items, or enlarged HP are needed.
        for((stage,boss) in Bosses.all.withIndex()) {
            val survived=(0 until 48).any { direction ->
                val e=GameEngine(Random(15)).apply {
                    run=Run(Job.SUMMONER,stage); screen=Screen.BATTLE; nextPattern=1e6
                    player=Actor(300.0,175.0,115.0,115.0); this.boss=Actor(300.0,125.0,9000.0,9000.0)
                    moveX=cos(direction*2*PI/48);moveY=sin(direction*2*PI/48)
                }
                val v=EnemyVolley(EnemyBarrage.forBoss(boss.id),stage,0.0,0,300.0,175.0,x=300.0,y=125.0,aim=PI/2)
                var wave=0
                while(e.elapsed<4.8 && e.damageTaken==0.0) {
                    while(wave<v.profile.waves && e.elapsed>=v.firstShot+wave*v.interval) {
                        e.enemyBullets.addAll(v.bullets(wave));wave++
                    }
                    e.update(.01)
                }
                e.damageTaken==0.0 && wave==v.profile.waves && e.enemyBullets.isEmpty()
            }
            assertTrue("${boss.id} needs at least one traversable route",survived)
        }
    }
}
