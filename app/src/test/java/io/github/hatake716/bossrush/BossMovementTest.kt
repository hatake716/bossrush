package io.github.hatake716.bossrush

import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class BossMovementTest {
    private fun arena(stage: Int)=GameEngine(Random(stage)).apply {
        run=Run(Job.WARRIOR,stage); screen=Screen.BATTLE; nextPattern=1e6
        boss=Actor(180.0,140.0,9000.0,9000.0)
        player=Actor(400.0,255.0,100000.0,100000.0)
    }
    private fun until(e: GameEngine,time: Double) {
        while(e.elapsed+1e-7<time) { e.nextPattern=1e6; e.update(min(.01,time-e.elapsed)) }
    }
    @Test fun everyBossTravelsOnALockedRouteAndCastsFromItsNewPosition() {
        assertEquals(Bosses.all.map { it.id }.toSet(),BossMobility.profiles.keys)
        for((stage,boss) in Bosses.all.withIndex()) {
            val e=arena(stage); val profile=BossMobility.forBoss(boss.id)
            e.castNormal(profile.slot,0)
            val move=e.bossMove!!
            assertEquals(profile.kind,move.kind)
            assertTrue(boss.id,hypot(move.toX-move.fromX,move.toY-move.fromY)>=60)
            e.player.x=20.0; e.player.y=20.0
            until(e,move.start+move.travel)
            assertEquals(boss.id,move.toX,e.boss.x,.001); assertEquals(move.toY,e.boss.y,.001)
            for(i in 0..100) {
                val point=move.point(i/100.0)
                assertTrue(point.first in 84.0..516.0 && point.second in 112.0..276.0)
            }
            e.normalCues.clear(); e.hazards.clear()
            e.castNormal((profile.slot+1)%BossCombat.forBoss(boss.id).size,0)
            assertTrue(boss.id,e.hazards.all { it.sourceX==e.boss.x && it.sourceY==e.boss.y })
        }
    }
    @Test fun chargesWarnBeforeMovingThenDamageAtBodyContactOnlyOnce() {
        val e=arena(2); e.boss.x=100.0; e.boss.y=170.0; e.player.y=170.0
        e.castNormal(0,0); val move=e.bossMove!!; val h=move.anchor
        assertTrue(h.swept); assertTrue(h.contains(e.player.x,e.player.y))
        until(e,h.delay-.01)
        assertEquals(100.0,e.boss.x,.001); assertEquals(.0,e.damageTaken,.0)
        until(e,h.delay+move.travel*.25)
        assertTrue(e.boss.x>100); assertEquals("The corridor is not an instantaneous beam",.0,e.damageTaken,.0)
        until(e,h.delay+move.travel)
        assertEquals(e.bossDamage(),e.damageTaken,.001)
        assertTrue(h.resolved); assertTrue(move.hitPlayer)
        until(e,e.elapsed+.5); assertEquals(e.bossDamage(),e.damageTaken,.001)
    }
    @Test fun leavingTheChargeCorridorAvoidsDamageAndWarningIncludesItsEndCaps() {
        for(outside in listOf(false,true)) {
            val e=arena(2); e.boss.x=100.0; e.boss.y=170.0; e.player.y=170.0
            e.castNormal(0,0); val move=e.bossMove!!
            e.player.x=move.toX+20; e.player.y=move.toY+if(outside) 70 else 0
            assertEquals(!outside,move.anchor.contains(e.player.x,e.player.y))
            until(e,move.start+move.travel)
            assertEquals(if(outside) .0 else e.bossDamage(),e.damageTaken,.001)
        }
    }
    @Test fun leapLandsAtTheDamagingCircleAndPausingFreezesTheWholeMotion() {
        val e=arena(30); e.castNormal(0,0); val move=e.bossMove!!
        until(e,move.start+move.travel*.5)
        assertTrue(move.lift>40); assertFalse(move.anchor.resolved)
        val x=e.boss.x; val y=e.boss.y; val time=move.anchor.time
        e.pause(); repeat(100) { e.update(.05) }
        assertEquals(x,e.boss.x,.0); assertEquals(y,e.boss.y,.0); assertEquals(time,move.anchor.time,.0)
        e.unpause(); until(e,move.anchor.delay+.001)
        assertTrue(move.anchor.resolved); assertEquals(.0,move.lift,1e-6)
        assertEquals(move.anchor.x,e.boss.x,.001); assertEquals(move.anchor.y,e.boss.y,.001)
        assertEquals(e.bossDamage(),e.damageTaken,.001)
    }
    @Test fun crossingProjectilesHitMovingBossesButDoNotHitTeleportTransit() {
        val e=arena(2); e.castNormal(0,0); val dash=e.bossMove!!
        until(e,dash.start+dash.travel*.4)
        e.projectiles.add(Projectile(e.boss.x-80,e.boss.y,5000.0,0.0,5.0,0.0,"arrow"))
        e.update(.05); assertEquals(8995.0,e.boss.hp,.001); assertTrue(e.projectiles.isEmpty())
        val teleport=arena(29); teleport.castNormal(0,0); val blink=teleport.bossMove!!
        until(teleport,blink.start+blink.travel*.49)
        teleport.projectiles.add(Projectile((blink.fromX+blink.toX)/2,(blink.fromY+blink.toY)/2,.0,.0,50.0,0.0,"arrow"))
        teleport.update(.02)
        assertEquals(blink.toX,teleport.boss.x,.001)
        assertEquals(9000.0,teleport.boss.hp,.001); assertEquals(1,teleport.projectiles.size)
    }
    @Test fun awakeningVictoryAndNewBattlesClearMotionWithoutSnappingToTheOldRoute() {
        val e=arena(2); e.castNormal(0,0); val move=e.bossMove!!
        until(e,move.start+move.travel*.5); val x=e.boss.x
        e.damageBoss(5000.0)
        assertEquals(Screen.CUTIN,e.screen); assertNull(e.bossMove); assertTrue(e.hazards.isEmpty())
        e.update(.05); assertEquals(x,e.boss.x,.0)
        e.beginBattle(); assertNull(e.bossMove); assertEquals(300.0,e.boss.x,.0)
        e.castNormal(0,0); assertNotNull(e.bossMove); e.victory(); assertNull(e.bossMove)
    }
    @Test fun everyUltimateKeepsItsMotionAnchorThroughDoubleCastWarningExtensions() {
        for(stage in 0..31) for((ordinal,pattern) in Bosses.all[stage].sequence.withIndex()) {
            val e=arena(stage); e.ultimateUsed=true; e.boss.hp=e.boss.maxHp*.25
            e.cast(pattern,true,ordinal); val move=e.bossMove!!
            assertTrue(e.hazards.any { it===move.anchor })
            assertTrue(e.hazards.any { !it.ultimate })
            assertTrue(e.hazards.count { it.swept }<=1)
            assertTrue(move.kind==BossMoveKind.CHARGE || move.start+move.travel<=move.anchor.delay+.001)
            until(e,move.start+move.travel)
            assertEquals("${e.bossInfo.id}/$pattern",move.toX,e.boss.x,.001)
            assertEquals(move.toY,e.boss.y,.001)
        }
    }
    @Test fun mobilityTargetsStayInsideTheArenaEvenWhenBothActorsAreAtItsEdges() {
        for(stage in 0..31) for(x in listOf(84.0,516.0)) for(y in listOf(112.0,276.0)) {
            val e=arena(stage); e.boss.x=x; e.boss.y=y; e.player.x=x; e.player.y=y
            for(ordinal in 0..1) {
                val destination=BossMobility.target(e,BossMobility.forBoss(e.bossInfo.id),ordinal)
                assertTrue(destination.first in 84.0..516.0 && destination.second in 112.0..276.0)
                assertTrue(hypot(destination.first-x,destination.second-y)>=60)
            }
        }
    }
}
