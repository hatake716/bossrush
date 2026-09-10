package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test

class HighScoresTest {
    private fun entry(score: Int,time: Long=1000,job: Job=Job.WARRIOR)=ScoreRecord(score,ScoreOutcome.GAMEOVER,job,3,time)

    @Test fun retainsOnlyTenHighestAcrossJobsAndDoesNotEvictForALowerScore() {
        var records=emptyList<ScoreRecord>()
        for(i in listOf(2,18,5,1,12,7,9,8,4,6,3,11,17,10,15,14,16,13))
            records=HighScores.add(records,entry(i*100,i.toLong(),Job.entries[i%4]))
        assertEquals((18 downTo 9).map { it*100 },records.map { it.score })
        assertEquals(records,HighScores.add(records,entry(1)))
        val top=HighScores.add(records,ScoreRecord(9000,ScoreOutcome.CLEAR,Job.SUMMONER,32,2000))
        assertEquals(9000,top.first().score); assertEquals(10,top.size); assertEquals(1000,top.last().score)
    }
    @Test fun tiesPreferRecentRunsAndSeparateIdenticalPlaysAreRetained() {
        val old=entry(500,100); val recent=entry(500,200,Job.THIEF)
        val legacy=ScoreRecord(500,ScoreOutcome.LEGACY)
        assertEquals(listOf(recent,old,legacy),HighScores.ranked(listOf(old,legacy,recent)))
        assertEquals(listOf(recent,recent),HighScores.add(listOf(recent),recent))
    }
    @Test fun oldBestMigratesOnceWithoutInventingItsJobResultOrDate() {
        val records=HighScores.inheritBest(emptyList(),5000)
        assertEquals(listOf(ScoreRecord(5000,ScoreOutcome.LEGACY)),records)
        assertEquals(records,HighScores.inheritBest(records,5000))
        val beaten=HighScores.add(records,entry(6000))
        assertEquals(beaten,HighScores.inheritBest(beaten,6000))
        assertTrue(HighScores.inheritBest(emptyList(),0).isEmpty())
    }
    @Test fun zeroScoreGameOversCountButInvalidOrIncompleteRecordsDoNot() {
        val zero=ScoreRecord(0,ScoreOutcome.GAMEOVER,Job.MAGE,0,0)
        val invalid=listOf(entry(-1),entry(5,-1),zero.copy(job=null),zero.copy(kills=32),
            zero.copy(outcome=ScoreOutcome.CLEAR),zero.copy(outcome=ScoreOutcome.LEGACY))
        assertEquals(listOf(zero),HighScores.ranked(invalid+zero))
        assertTrue(ScoreRecord(800000,ScoreOutcome.CLEAR,Job.WARRIOR,32,1000).valid)
    }
}
