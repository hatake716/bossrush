package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test

class MainStoryTest {
    private fun campaign()=GameEngine().apply { newRun(story=true) }
    private fun reload(e: GameEngine)=GameEngine().apply {
        run=e.run!!.copy(levels=e.levels.copyOf(),inventory=e.run!!.inventory.toMutableList())
        selectedJob=run!!.job; resumeRun()
    }
    @Test fun everyEncounterHasItsOwnConflictResolutionColorAndSource() {
        assertEquals(Bosses.all.map { it.id },MainStory.chapters.map { it.id })
        assertEquals(32,MainStory.chapters.map { it.colorName }.toSet().size)
        for(c in MainStory.chapters) {
            assertTrue(c.source.isNotBlank()); assertTrue(c.before.size>=3); assertTrue(c.after.size>=2)
            assertTrue(c.before.any { it.speaker=="旅人" })
            assertTrue((c.before+c.after).all { it.text.isNotBlank() && it.speaker.isNotBlank() })
        }
        assertEquals(32,MainStory.chapters.map { it.before }.toSet().size)
        assertEquals(4,Job.entries.map { MainStory.pages(StoryMoment.PROLOGUE,0,it) }.toSet().size)
    }
    @Test fun campaignStartsWithPrologueAndEncounterBeforeBattle() {
        val e=campaign()
        assertEquals(Screen.STORY,e.screen); assertEquals(StoryMoment.PROLOGUE,e.run!!.storyMoment)
        e.skipStory(); assertEquals(StoryMoment.BEFORE,e.run!!.storyMoment)
        assertEquals(0,e.run!!.stage); assertEquals(0,e.run!!.kills)
        e.skipStory(); assertEquals(Screen.INTRO,e.screen)
        assertEquals(0,e.run!!.storyBeforeStage)
        assertEquals(Screen.INTRO,reload(e).screen)
        e.beginBattle(); assertEquals(Screen.BATTLE,e.screen)
    }
    @Test fun everyPageIsCheckpointedAndBackDoesNotChangeProgress() {
        val e=campaign(); var saves=0; e.onCheckpoint={ saves++ }
        assertFalse(e.previousStoryPage())
        e.advanceStory(); e.advanceStory(); assertEquals(2,saves)
        val r=reload(e); assertEquals(2,r.run!!.storyPage); assertEquals(e.storyLine,r.storyLine)
        assertTrue(e.previousStoryPage()); assertEquals(3,saves)
        assertEquals(1,reload(e).run!!.storyPage)
        assertEquals(0,e.run!!.gold); assertEquals(0,e.run!!.kills)
    }
    @Test fun narrativeNeverAdvancesCombatClockOrAcceptsAttacks() {
        val e=campaign(); e.heldSkill=0; e.moveX=1.0
        val hp=e.player.hp; val x=e.player.x
        repeat(600) { e.update(.05) }
        assertEquals(0.0,e.elapsed,0.0); assertEquals(hp,e.player.hp,0.0); assertEquals(x,e.player.x,0.0)
        assertEquals(0,e.run!!.storyPage); assertFalse(e.useSkill(0)); assertFalse(e.useItem(0))
        e.pause(); assertEquals(Screen.STORY,e.screen)
    }
    @Test fun allThirtyTwoVictoriesPersistAfterStoriesWithoutRepeatingRewards() {
        var e=campaign(); e.skipStory(); e.skipStory()
        for(stage in 0..31) {
            assertEquals(stage,e.run!!.stage); e.beginBattle(); e.elapsed=30.0; e.damageTaken=2.0
            e.victory(); e.finishDefeatAnimation(); assertEquals(Screen.REWARD,e.screen)
            e.selectUpgrade(stage%4); e.finishReward()
            assertEquals(Screen.STORY,e.screen); assertEquals(StoryMoment.AFTER,e.run!!.storyMoment)
            assertEquals(stage+1,e.run!!.kills)
            val score=e.run!!.score; val gold=e.run!!.gold; val levels=e.levels.toList()
            e.advanceStory(); e=reload(e)
            assertEquals(score,e.run!!.score); assertEquals(gold,e.run!!.gold); assertEquals(levels,e.levels.toList())
            e.finishReward(); assertEquals(score,e.run!!.score); assertEquals(levels,e.levels.toList())
            e.skipStory()
            if(stage<31) {
                assertEquals(Screen.SHOP,e.screen); assertEquals(stage+1,e.run!!.stage)
                e=reload(e); assertEquals(Screen.SHOP,e.screen)
                e.leaveShop(); assertEquals(StoryMoment.BEFORE,e.run!!.storyMoment)
                e.skipStory(); assertEquals(Screen.INTRO,e.screen)
            } else assertEquals(StoryMoment.EPILOGUE,e.run!!.storyMoment)
        }
        var results=0; e.onResult={ _,clear -> assertTrue(clear); results++ }
        e.skipStory(); assertEquals(Screen.ENDING,e.screen); assertEquals(1,results)
        assertFalse(e.skipStory()); assertFalse(e.advanceStory()); assertEquals(1,results)
    }
    @Test fun anEpilogueCheckpointCanFinishAfterColdResume() {
        val e=GameEngine().apply {
            run=Run(Job.THIEF,stage=31,kills=32,checkpoint="STORY",storyEnabled=true,storyMoment=StoryMoment.EPILOGUE,storyPage=3,storyBeforeStage=31)
            resumeRun()
        }
        var clears=0; e.onResult={ _,clear -> if(clear) clears++ }
        assertEquals(3,e.run!!.storyPage); e.skipStory()
        assertEquals(Screen.ENDING,e.screen); assertEquals(1,clears)
    }
    @Test fun dialogueFitsSmallestLandscapeWindowAndKeepsPunctuationTogether() {
        for(job in Job.entries) for(stage in 0..31) for(moment in StoryMoment.entries) {
            for(page in MainStory.pages(moment,stage,job)) {
                val lines=StoryLayout.lines(page.text,35)
                assertTrue("$stage $moment ${page.text}",lines.size<=4)
                assertTrue(lines.all { it.length<=35 })
                assertEquals(page.text,lines.joinToString(""))
                assertTrue(lines.all { it.isEmpty() || it.first() !in "、。）」』" })
            }
        }
    }
    @Test fun oldAdventureCanJoinAtItsCurrentBossWithoutResettingStats() {
        val e=GameEngine().apply {
            run=Run(Job.MAGE,stage=12,gold=888,score=30000,kills=12,storyEnabled=true)
            resumeRun()
        }
        assertEquals(StoryMoment.BEFORE,e.run!!.storyMoment); assertEquals(12,e.run!!.stage)
        e.skipStory(); assertEquals(888,e.run!!.gold); assertEquals(30000,e.run!!.score)
        assertEquals(12,e.run!!.kills); assertEquals(Screen.INTRO,e.screen)
    }
}
