package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GameEngineTest {
    private fun battle(job: Job=Job.WARRIOR,stage: Int=0): GameEngine = GameEngine().apply {
        selectedJob=job; newRun(); run!!.stage=stage; beginBattle()
    }
    private fun tick(e: GameEngine,seconds: Double) { repeat((seconds*60).roundToInt()) { e.update(1.0/60) } }
    @Test fun allThirtyTwoBossesHaveDistinctIdentitiesAndSongs() {
        assertEquals(32,Bosses.all.size)
        assertEquals(32,Bosses.all.map { it.id }.toSet().size)
        assertEquals(32,Bosses.all.map { it.ultimate }.toSet().size)
        assertEquals(32,Bosses.all.map { listOf(it.root,it.bpm,it.motif) }.toSet().size)
        assertTrue(Bosses.all.all { it.sequence.size>=2 && it.attacks.size==it.attackNames.size && it.hint.isNotBlank() })
    }
    @Test fun everySkillHasLinearPowerRangeAndCooldownAndA16LevelCap() {
        for(job in Job.entries) for(slot in 0..3) {
            val s=Skills.all.getValue(job)[slot]
            assertEquals(s.power*.25,Skills.power(job,slot,1),1e-9)
            assertEquals(s.power,Skills.power(job,slot,16),1e-9)
            assertEquals(Skills.power(job,slot,16),Skills.power(job,slot,99),1e-9)
            for(l in 2..16) {
                assertEquals(s.power*.05,Skills.power(job,slot,l)-Skills.power(job,slot,l-1),1e-8)
                assertEquals(s.cooldown*.45/15,Skills.cooldown(job,slot,l-1)-Skills.cooldown(job,slot,l),1e-8)
                assertEquals(s.range*.6/15,Skills.range(job,slot,l)-Skills.range(job,slot,l-1),1e-8)
            }
        }
    }
    @Test fun summonGaugeRegeneratesAtExactlyHalfRate() {
        val a=battle(); val b=battle(Job.SUMMONER)
        a.gauge=.0; b.gauge=.0; tick(a,1.0); tick(b,1.0)
        assertEquals(20.0,a.gauge,1e-8); assertEquals(10.0,b.gauge,1e-8)
    }
    @Test fun summonLimitExpiryAndHaniwaReuse() {
        val e=battle(Job.SUMMONER)
        assertTrue(e.useSkill(2)); e.gauge=100.0; assertTrue(e.useSkill(0)); e.gauge=100.0
        assertFalse(e.useSkill(1)); assertEquals(2,e.summons.size)
        e.player.x=e.boss.x; e.player.y=e.boss.y+40; tick(e,1.3)
        assertTrue(e.useSkill(2)); assertEquals(2,e.summons.size)
        e.hazards.clear(); e.nextPattern=1000.0; tick(e,13.0)
        assertTrue(e.summons.isEmpty())
    }
    @Test fun rabbitHealsAndGiantAttacks() {
        val e=battle(Job.SUMMONER); e.player.hp=25.0
        e.useSkill(1); e.gauge=100.0; e.useSkill(0); tick(e,2.0)
        assertTrue(e.player.hp>25); assertTrue(e.damageDone>0)
    }
    @Test fun haniwaOnlyProtectsFrontalAttacks() {
        val e=battle(Job.SUMMONER); e.useSkill(2); tick(e,.5)
        val before=e.player.hp; e.hurt(40.0,e.boss.x,e.boss.y,true)
        assertEquals(10.0,before-e.player.hp,.01)
        e.invulnerability=.0; val hp=e.player.hp
        e.hurt(40.0,e.player.x,e.player.y+100,true)
        assertEquals(40.0,hp-e.player.hp,.01)
    }
    @Test fun restLocksMovementAndHealsOnlyAtCompletion() {
        val e=battle(); e.player.hp=40.0; val x=e.player.x; e.moveX=1.0
        assertTrue(e.useSkill(3)); tick(e,1.0)
        assertEquals(x,e.player.x,.01); assertEquals(40.0,e.player.hp,.01); assertFalse(e.useSkill(2))
        e.nextPattern=1000.0; e.hazards.clear(); tick(e,1.1)
        assertEquals(80.0,e.player.hp,.01); assertTrue(e.player.x>x)
    }
    @Test fun meleeHasRangeWhileBowTravelsToTarget() {
        val e=battle(); assertFalse(e.useSkill(0)); assertTrue(e.useSkill(2))
        assertEquals(.0,e.damageDone,.01); tick(e,.5); assertTrue(e.damageDone>0)
        e.player.x=e.boss.x; e.player.y=e.boss.y+45; assertTrue(e.useSkill(0))
    }
    @Test fun fireAndIceResolveWithTravelAndTracking() {
        val e=battle(Job.MAGE); assertTrue(e.useSkill(0)); assertTrue(e.useSkill(1))
        assertEquals(.0,e.damageDone,.01); tick(e,1.0)
        assertTrue(e.damageDone>=40.0); assertTrue(e.iceMarks.isEmpty())
    }
    @Test fun ultimateTriggersAtOneThirdOnceAndPreventsSkipping() {
        val e=battle(); e.damageBoss(e.boss.maxHp*2)
        assertEquals(Screen.CUTIN,e.screen); assertEquals(e.boss.maxHp/3,e.boss.hp,.001)
        val time=e.elapsed; tick(e,3.1); assertEquals(time,e.elapsed,.2); assertEquals(Screen.BATTLE,e.screen)
        assertTrue(e.ultimateUsed); assertTrue(e.hazards.isNotEmpty()||e.cues.isNotEmpty())
        e.damageBoss(e.boss.maxHp); assertEquals(Screen.REWARD,e.screen)
    }
    @Test fun pauseFreezesAllCombatTimers() {
        val e=battle(); e.useSkill(2); e.pause(); val cd=e.cooldowns[2]; val time=e.elapsed
        tick(e,3.0); assertEquals(cd,e.cooldowns[2],.0); assertEquals(time,e.elapsed,.0)
        e.unpause(); tick(e,.2); assertTrue(e.elapsed>time)
    }
    @Test fun pauseDuringCutinResumesCutin() {
        val e=battle(); e.damageBoss(e.boss.maxHp); e.pause(); tick(e,8.0); e.unpause()
        assertEquals(Screen.CUTIN,e.screen); assertEquals(3.0,e.cutinTime,.0)
    }
    @Test fun stockLimitAppliesToShopAndStolenItems() {
        val e=battle(Job.THIEF); val r=e.run!!; r.inventory.clear(); repeat(5) { r.inventory.add(Item.POTION) }
        e.player.x=e.boss.x; e.player.y=e.boss.y+30
        assertFalse(e.useSkill(1)); assertFalse(e.stolen)
        e.screen=Screen.SHOP; r.gold=100; assertFalse(e.buy(Item.POTION)); assertEquals(100,r.gold)
        e.discardItem(0); assertTrue(e.buy(Item.ARMOR)); assertEquals(60,r.gold); assertEquals(5,r.inventory.size)
    }
    @Test fun thiefStealsExactlyOncePerBoss() {
        val e=battle(Job.THIEF); e.player.x=e.boss.x; e.player.y=e.boss.y+30
        assertTrue(e.useSkill(1)); assertTrue(e.run!!.inventory.last().special)
        e.cooldowns[1]=.0; e.gauge=100.0; assertFalse(e.useSkill(1))
    }
    @Test fun itemEffectsHaveTheirSpecifiedBehavior() {
        val e=battle(); val r=e.run!!; r.inventory.clear(); r.inventory.add(Item.INVISIBLE)
        assertTrue(e.useItem(0)); val hp=e.player.hp; e.hurt(1000.0); assertEquals(hp,e.player.hp,.0)
        r.inventory.add(Item.HOURGLASS); e.cooldowns.fill(8.0); e.gauge=.0; e.useItem(0)
        assertEquals(100.0,e.gauge,.0); assertTrue(e.cooldowns.all { it==.0 })
        r.inventory.add(Item.CLONES); val base=e.power(0); e.useItem(0); assertEquals(base*2,e.power(0),.01)
        assertEquals(8.0,e.buffs["clones"]!!,.0)
    }
    @Test fun fortuneTriplesOnlyThisBossAndThiefGetsMoreGold() {
        val e=battle(Job.THIEF); e.fortune=true; e.victory(); assertEquals(288,e.lastGold)
        assertFalse(e.lootChosen); assertTrue(e.chooseLoot(Item.HOURGLASS))
        assertTrue(e.upgrade(0)); e.finishReward(); e.leaveShop(); e.beginBattle()
        assertFalse(e.fortune); assertFalse(e.stolen)
    }
    @Test fun exactlyOneUpgradeAndFullInventoryReplacementAreRequired() {
        val e=battle(Job.THIEF); e.run!!.inventory.clear(); repeat(5) { e.run!!.inventory.add(Item.POTION) }; e.victory()
        assertTrue(e.upgrade(1)); assertFalse(e.upgrade(0)); assertEquals(2,e.levels[1])
        assertFalse(e.chooseLoot(Item.CLONES)); e.finishReward(); assertEquals(Screen.REWARD,e.screen)
        assertTrue(e.replaceLoot(2)); e.finishReward(); assertEquals(Screen.SHOP,e.screen)
        assertEquals(Item.CLONES,e.run!!.inventory[2]); assertEquals(5,e.run!!.inventory.size)
    }
    @Test fun scoresRewardBothSpeedAndAvoidingDamage() {
        assertTrue(GameEngine.scoreFor(25.0,10.0)>GameEngine.scoreFor(35.0,10.0))
        assertTrue(GameEngine.scoreFor(30.0,.0)>GameEngine.scoreFor(30.0,20.0))
        assertEquals(10000,GameEngine.scoreFor(200.0,2000.0))
    }
    @Test fun everyPatternHasBothDangerAndReachableSafeSpace() {
        val e=battle(); e.player.x=300.0; e.player.y=240.0
        for(pattern in Pattern.entries) {
            e.hazards.clear(); e.cast(pattern,true,0)
            assertTrue(pattern.name,e.hazards.isNotEmpty())
            if(pattern==Pattern.KNOCKBACK) continue
            // FRONTBACK is explicitly sequential, so inspect the first resolution group.
            val first=e.hazards.minOf { it.delay }; val active=e.hazards.filter { abs(it.delay-first)<.01 }
            var safe=0; var danger=0
            for(x in 20..580 step 10) for(y in 20..310 step 10) {
                if(active.any { it.contains(x.toDouble(),y.toDouble()) }) danger++ else safe++
            }
            assertTrue("No safe space: $pattern",safe>0); assertTrue("No hazard: $pattern",danger>0)
        }
    }
    @Test fun circleLineRingAndSafeZonesIncludePlayerRadius() {
        assertTrue(Hazard("circle",100.0,100.0,20.0).contains(126.0,100.0))
        assertFalse(Hazard("circle",100.0,100.0,20.0).contains(128.0,100.0))
        assertFalse(Hazard("safe",100.0,100.0,40.0).contains(100.0,100.0))
        assertTrue(Hazard("ring",100.0,100.0,40.0,300.0).contains(150.0,100.0))
        assertFalse(Hazard("line",100.0,100.0,100.0,20.0,PI/2).contains(140.0,100.0))
    }
    @Test fun aWholeCampaignReachesColorEndingAndSavesExactlyOncePerResult() {
        for(job in Job.entries) {
            val e=GameEngine(); e.selectedJob=job; e.newRun(); var records=0
            e.onResult={ _,clear -> assertTrue(clear); records++ }
            repeat(32) { stage ->
                e.beginBattle(); assertEquals(stage,e.run!!.stage)
                e.elapsed=30.0; e.ultimateUsed=true; e.damageBoss(e.boss.maxHp)
                assertEquals(Screen.REWARD,e.screen)
                val slot=e.levels.indices.first { e.levels[it]<16 }; assertTrue(e.upgrade(slot))
                if(job==Job.THIEF) {
                    if(e.run!!.inventory.size>=5) e.run!!.inventory.removeAt(0)
                    assertTrue(e.chooseLoot(Item.INVISIBLE))
                }
                e.finishReward()
                if(stage<31) { assertEquals(Screen.SHOP,e.screen); e.leaveShop() }
            }
            assertEquals(Screen.ENDING,e.screen); assertEquals(32,e.run!!.kills); assertEquals(1,records)
            assertTrue(e.run!!.score>0)
        }
    }
    @Test fun allClassesAndStagesHaveThirtySecondStationaryDamageBudgetAndIncreasingHp() {
        for(job in Job.entries) {
            val e=GameEngine(); e.selectedJob=job; e.newRun(); var previous=.0
            for(stage in 0..31) {
                e.run!!.stage=stage
                if(stage>0) { val slot=if(stage<=15) 0 else if(stage<=30) 3 else 1; e.levels[slot]=(e.levels[slot]+1).coerceAtMost(16) }
                e.run!!.previousHp=previous; e.beginBattle()
                val reference=e.estimateHp(e.run!!)
                assertTrue("${job.name} stage $stage",e.boss.maxHp>previous)
                assertEquals("30 second budget ${job.name} stage $stage",reference,e.boss.maxHp,reference*.06)
                previous=e.boss.maxHp
            }
        }
    }
    @Test fun defeatedPlayerGetsOneFinalResult() {
        val e=battle(); var records=0; e.onResult={ _,clear -> assertFalse(clear); records++ }
        e.hurt(10000.0); assertEquals(Screen.GAMEOVER,e.screen); tick(e,10.0); assertEquals(1,records)
    }
    @Test fun telegraphsAllowTravelFromEveryCornerAtBaseMovementSpeed() {
        val e=battle(Job.SUMMONER,31)
        for(pattern in Pattern.entries.filter { it!=Pattern.KNOCKBACK }) for(pos in listOf(20 to 20,580 to 20,20 to 310,580 to 310)) {
            e.player.x=pos.first.toDouble(); e.player.y=pos.second.toDouble(); e.hazards.clear(); e.cast(pattern,true,0)
            val delay=e.hazards.minOf { it.delay }; val first=e.hazards.filter { abs(it.delay-delay)<.01 }
            var best=Double.POSITIVE_INFINITY
            for(x in 20..580 step 10) for(y in 20..310 step 10) if(first.none { it.contains(x.toDouble(),y.toDouble(),12.0) }) best=min(best,hypot(x-e.player.x,y-e.player.y))
            assertTrue("${pattern.name} $pos needs $best distance in $delay",best/e.job.speed+.39<=delay)
        }
    }
    @Test fun extendedUltimateTelegraphsDoNotOverlapLaterSteps() {
        val e=battle(Job.SUMMONER,29); e.player.x=20.0; e.player.y=20.0
        e.damageBoss(e.boss.maxHp); tick(e,3.1)
        val remaining=e.hazards.maxOf { it.delay-it.time }
        assertTrue(e.cues.first().at>=e.elapsed+remaining+.5)
    }
}
