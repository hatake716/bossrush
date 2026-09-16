package io.github.hatake716.bossrush

import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class EnemyBarrageTest {
    private fun arena(stage: Int=0,mode: GameMode=GameMode.NORMAL,job: Job=Job.WARRIOR)=GameEngine(Random(15)).apply {
        run=Run(job,stage=stage,mode=mode); screen=Screen.BATTLE; nextPattern=1e6
        boss=Actor(300.0,125.0,9000.0,9000.0); player=Actor(300.0,265.0,100000.0,100000.0)
    }
    private fun tick(e: GameEngine,seconds: Double) { repeat(ceil(seconds/.01).toInt()) { e.update(.01) } }
    private fun shot(e: GameEngine,x: Double=250.0,y: Double=265.0,speed: Double=100.0)=EnemyBullet(x,y,0.0,speed,3.5,BulletShape.SEED,.7,age=.3).also { e.enemyBullets.add(it) }
    @Test fun allBossesHaveDistinctFiniteVolleysWithVisibleGapsAndMoreDenseLaterPatterns() {
        assertEquals(Bosses.all.map { it.id }.toSet(),EnemyBarrage.profiles.keys)
        val signatures=mutableSetOf<String>()
        for((stage,boss) in Bosses.all.withIndex()) {
            val p=EnemyBarrage.forBoss(boss.id); val volley=EnemyVolley(p,stage,0.0,0,300.0,265.0,x=300.0,y=125.0,aim=PI/2)
            assertTrue(volley.warning>=.44-1e-9); assertTrue(volley.speed<Job.SUMMONER.speed)
            val bullets=(0 until p.waves).flatMap { volley.bullets(it) }
            assertTrue(bullets.size in 6..EnemyBarrage.MAX_BULLETS)
            assertTrue(bullets.all { it.heading.isFinite() && it.radius in 3.0..4.0 && it.life<=3.6 })
            signatures.add(bullets.map { listOf(it.heading,it.speed,it.turn,it.weave,it.shape) }.toString())
            for(wave in 0 until p.waves) {
                val headings=volley.bullets(wave)
                assertTrue("A gap outside the emitter: ${boss.id}/$wave",(0..359).any { degree ->
                    val a=degree*PI/180
                    headings.all { b -> abs(atan2(sin(a-b.heading),cos(a-b.heading)))*150>b.radius+9 }
                })
            }
        }
        assertEquals(32,signatures.size)
        val profiles=EnemyBarrage.profiles.values.toList()
        assertTrue(profiles.takeLast(8).map { it.count*it.waves }.average()>profiles.take(8).map { it.count*it.waves }.average()*3)
    }
    @Test fun everyNormalSequenceWarnsThenFiresTravellingShotsAndNeverOverlapsTheNextFloorAttack() {
        for(stage in 0..31) {
            val e=arena(stage); val slot=BossCombat.forBoss(e.bossInfo.id).lastIndex
            e.castNormal(slot,0); var sawWarning=false; var sawBullets=false; var finished=false
            repeat(2200) {
                e.nextPattern=1e6; e.update(.01)
                val v=e.enemyVolley
                if(v?.started==true && e.elapsed<v.firstShot) { sawWarning=true; assertTrue(e.enemyBullets.isEmpty()) }
                if(e.enemyBullets.isNotEmpty()) {
                    sawBullets=true; assertTrue(e.hazards.none { !it.resolved }); assertTrue(e.normalCues.isEmpty())
                    assertTrue(e.enemyBullets.size<=EnemyBarrage.MAX_BULLETS)
                }
                if(sawBullets && e.enemyBullets.isEmpty() && v==null) finished=true
            }
            assertTrue("${e.bossInfo.id} $sawWarning/$sawBullets/$finished",sawWarning && sawBullets && finished)
        }
    }
    @Test fun onlyBulletContactDealsDamageAndFastBulletsCannotTunnelThroughThePlayer() {
        val e=arena(); shot(e)
        tick(e,.25); assertEquals(0.0,e.damageTaken,0.0); assertTrue(e.enemyBullets.single().x>250)
        tick(e,.25); assertEquals(e.bossDamage()*.7,e.damageTaken,1e-8); assertTrue(e.enemyBullets.isEmpty())
        val fast=arena(); shot(fast,x=100.0,speed=8000.0); fast.update(.05)
        assertEquals(fast.bossDamage()*.7,fast.damageTaken,1e-8)
        val missed=arena(); shot(missed,y=240.0,speed=8000.0); missed.update(.05)
        assertEquals(0.0,missed.damageTaken,0.0)
    }
    @Test fun newlyFormingBulletsAllowGraceAndCrossingPlayersUseSweptContact() {
        val forming=arena(); forming.enemyBullets.add(EnemyBullet(300.0,265.0,0.0,100.0,3.0,BulletShape.RUNE,1.0))
        forming.update(.05); assertEquals(0.0,forming.damageTaken,0.0)
        val crossing=arena(); crossing.player.x=280.0; crossing.moveX=1.0
        shot(crossing,x=301.0,speed=0.0); tick(crossing,.2)
        assertTrue(crossing.damageTaken>0)
    }
    @Test fun curvedBulletsMoveAndAllBulletsExpireOrLeaveTheArena() {
        val e=arena(); val b=EnemyBullet(100.0,100.0,0.0,80.0,3.0,BulletShape.MOON,.5,turn=.5,weave=.2)
        e.enemyBullets.add(b); tick(e,.6); assertTrue(b.x>130 && b.y>104)
        tick(e,4.0); assertTrue(e.enemyBullets.isEmpty())
        shot(e,x=610.0,y=100.0); tick(e,.3); assertTrue(e.enemyBullets.isEmpty())
    }
    @Test fun pauseFreezesShotsAndAwakeningDefeatGameOverTitleAndNewBattleClearThem() {
        val e=arena(); shot(e); e.pause(); val before=e.enemyBullets.single().copy(); tick(e,2.0)
        assertEquals(before,e.enemyBullets.single()); e.unpause(); e.update(.02); assertTrue(e.enemyBullets.single().x>before.x)
        e.damageBoss(e.boss.maxHp); assertEquals(Screen.CUTIN,e.screen); assertTrue(e.enemyBullets.isEmpty()); assertNull(e.enemyVolley)
        e.dismissCutin(); shot(e); e.damageBoss(e.boss.maxHp); assertEquals(Screen.DEFEAT,e.screen); assertTrue(e.enemyBullets.isEmpty())
        for(screen in listOf(Screen.TITLE,Screen.GAMEOVER)) {
            val other=arena(); shot(other); other.changeScreen(screen); assertTrue(other.enemyBullets.isEmpty())
        }
        val fresh=arena(); fresh.castNormal(0,0); shot(fresh); fresh.beginBattle(); assertNull(fresh.enemyVolley); assertTrue(fresh.enemyBullets.isEmpty())
    }
    @Test fun hardModeDefenseInvincibilityAndHaniwaUseTheSameDamageRules() {
        fun hit(mode: GameMode,armor: Boolean): Double {
            val e=arena(20,mode); if(armor) e.buffs["armor"]=5.0
            shot(e,x=300.0); e.update(.01); return e.damageTaken
        }
        assertEquals(hit(GameMode.NORMAL,false)*3,hit(GameMode.HARD,false),1e-8)
        assertEquals(hit(GameMode.HARD,false)/2,hit(GameMode.HARD,true),1e-8)
        val e=arena(job=Job.SUMMONER); e.summons.add(Summon(2,300.0,260.0,5.0))
        shot(e,x=300.0); e.update(.01); assertTrue(e.summons.isEmpty()); assertEquals(0.0,e.damageTaken,0.0)
        e.buffs["vanish"]=5.0; shot(e,x=300.0); e.update(.01); assertEquals(0.0,e.damageTaken,0.0); assertTrue(e.enemyBullets.isEmpty())
        e.buffs.clear(); e.player.hp=1.0; shot(e,x=300.0); shot(e,x=300.0); e.update(.01)
        assertEquals(Screen.GAMEOVER,e.screen); assertTrue(e.enemyBullets.isEmpty())
    }
}
