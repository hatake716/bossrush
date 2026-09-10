package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class FeedbackTest {
    private fun battle(job: Job)=GameEngine().apply {
        selectedJob=job; newRun(); beginBattle(); nextPattern=1000.0
        player.x=boss.x; player.y=boss.y+40
    }
    @Test fun everySuccessfulSkillHasItsOwnAudibleEventAndRejectedActionsStaySilent() {
        val names=mapOf(
            Job.WARRIOR to listOf("sword","shield","arrow","limit-slash"),
            Job.MAGE to listOf("fire","ice","focus","limit-flare"),
            Job.SUMMONER to listOf("summon-giant","summon-rabbit","summon-haniwa","limit-summon"),
            Job.THIEF to listOf("knife","coin","speed","limit-vanish")
        )
        for(job in Job.entries) for(slot in 0..3) {
            val e=battle(job); if(slot==3) e.player.hp=e.player.maxHp/3
            assertTrue(e.useSkill(slot)); assertTrue(e.sounds.contains(names.getValue(job)[slot]))
            e.sounds.clear(); assertFalse(e.useSkill(slot)); assertTrue(e.sounds.isEmpty())
        }
        val far=battle(Job.WARRIOR); far.player.y=300.0
        assertFalse(far.useSkill(0)); assertTrue(far.sounds.isEmpty())
    }
    @Test fun projectileImpactsGiantAndHaniwaEmitDistinctSounds() {
        for((job,slot,event) in listOf(Triple(Job.WARRIOR,2,"arrow-hit"),Triple(Job.MAGE,0,"fire-hit"),Triple(Job.MAGE,1,"ice-hit"),Triple(Job.SUMMONER,0,"giant-hit"))) {
            val e=battle(job); e.useSkill(slot); e.sounds.clear()
            repeat(60) { e.update(1.0/60) }
            assertTrue("$event must play on impact",e.sounds.contains(event))
        }
        val e=battle(Job.SUMMONER); e.useSkill(2); repeat(80) { e.update(1.0/60) }
        // Approach again after the boss repositions; this assertion tests the melee sound.
        e.player.x=e.boss.x; e.player.y=e.boss.y+30
        e.gauge=100.0; e.sounds.clear(); assertTrue(e.useSkill(2)); assertTrue(e.sounds.contains("haniwa"))
    }
    @Test fun simultaneousEffectsAreMixedToCompletionAndVoiceCountIsBounded() {
        val mixer=EffectMixer(); mixer.add("sword"); mixer.add("fire")
        assertEquals(2,mixer.voiceCount)
        repeat(1000) { i ->
            assertEquals(SoundEffects.clip("sword")!![i].toDouble()+SoundEffects.clip("fire")!![i],mixer.next(),1e-7)
        }
        repeat(20000) { mixer.next() }; assertEquals(0,mixer.voiceCount)
        repeat(20) { mixer.add("ice") }; assertEquals(8,mixer.voiceCount)
        mixer.clear(); assertEquals(.0,mixer.next(),.0)
    }
    @Test fun allEffectsAreAudibleDistinctFiniteAndHaveASilentTail() {
        val hashes=mutableSetOf<Int>()
        for((name,duration) in SoundEffects.durations) {
            val clip=SoundEffects.clip(name)!!
            assertTrue(name,clip.sumOf { it.toDouble()*it }>1.0)
            assertTrue(name,clip.all { it.isFinite() && abs(it)<=.95 })
            assertEquals(.0,SoundEffects.sample(duration+.1,name),.0)
            assertEquals(.0,SoundEffects.sample(-.1,name),.0)
            assertTrue(name,abs(clip.last())<.003)
            hashes.add(clip.contentHashCode())
        }
        assertEquals(SoundEffects.durations.size,hashes.size)
    }
    @Test fun everyPatternGetsAftermathAtResolutionWithoutExtraDamageAndPauseFreezesIt() {
        for(pattern in Pattern.entries) {
            val e=battle(Job.WARRIOR); e.player.hp=10000.0; e.player.maxHp=10000.0
            e.cast(pattern,true,0)
            val initial=e.hazards.map { it.copy() }; assertTrue(initial.isNotEmpty())
            assertTrue(e.impacts.isEmpty())
            var erupted=false
            repeat(1200) {
                e.update(1.0/60)
                if(e.impacts.isNotEmpty()) {
                    erupted=true
                    val impact=e.impacts.first(); assertTrue(initial.any { it.shape==impact.hazard.shape && it.x==impact.hazard.x })
                }
            }
            assertTrue("$pattern must emit an impact",erupted)
            assertTrue(e.hazards.isEmpty()); assertTrue(e.impacts.isEmpty())
            assertTrue(e.damageTaken<=initial.size*e.bossDamage()*1.65+e.bossDamage()*1.5)
        }
        val e=battle(Job.WARRIOR)
        e.hazards.add(Hazard("circle",e.player.x,e.player.y,45.0,delay=.1))
        repeat(7) { e.update(1.0/60) }
        assertEquals(1,e.impacts.size); val age=e.impacts.single().age
        val damage=e.damageTaken; e.pause(); repeat(120) { e.update(1.0/60) }
        assertEquals(age,e.impacts.single().age,.0)
        e.unpause(); repeat(70) { e.update(1.0/60) }
        assertTrue(e.impacts.isEmpty()); assertEquals(damage,e.damageTaken,.0)
        e.beginBattle(); assertTrue(e.impacts.isEmpty())
    }
}
