package io.github.hatake716.bossrush

enum class ScoreOutcome(val label: String) { CLEAR("クリア"), GAMEOVER("ゲームオーバー"), LEGACY("以前の最高記録") }

data class ScoreRecord(
    val score: Int,
    val outcome: ScoreOutcome,
    val job: Job?=null,
    val kills: Int?=null,
    val finishedAt: Long?=null,
    val mode: GameMode=GameMode.NORMAL
) {
    val valid get()=score>=0 && when(outcome) {
        ScoreOutcome.LEGACY -> job==null && kills==null && finishedAt==null
        ScoreOutcome.CLEAR -> job!=null && kills==32 && finishedAt!=null && finishedAt>=0
        ScoreOutcome.GAMEOVER -> job!=null && kills in 0..31 && finishedAt!=null && finishedAt>=0
    }
}

object HighScores {
    const val LIMIT=10
    fun ranked(records: List<ScoreRecord>): List<ScoreRecord> = records.filter { it.valid }
        .sortedWith(compareByDescending<ScoreRecord> { it.score }.thenByDescending { it.finishedAt ?: -1L }).take(LIMIT)

    fun add(records: List<ScoreRecord>,record: ScoreRecord)=ranked(listOf(record)+records)

    /** Earlier versions kept only one number; never invent its missing play details. */
    fun inheritBest(records: List<ScoreRecord>,best: Int): List<ScoreRecord> {
        val sorted=ranked(records)
        return if(best>0 && sorted.none { it.score>=best }) add(sorted,ScoreRecord(best,ScoreOutcome.LEGACY)) else sorted
    }
}
