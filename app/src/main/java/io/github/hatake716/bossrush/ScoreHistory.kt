package io.github.hatake716.bossrush

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal object ScoreHistoryCodec {
    fun encode(records: List<ScoreRecord>): String = JSONObject().put("version",2)
        .put("records",JSONArray().apply {
            HighScores.ranked(records).forEach { r ->
                put(JSONObject().put("score",r.score).put("outcome",r.outcome.name).put("mode",r.mode.name)
                    .put("job",r.job?.name).put("kills",r.kills).put("finishedAt",r.finishedAt))
            }
        }).toString()

    fun decode(value: String?): List<ScoreRecord> = try {
        if(value==null) emptyList() else {
            val root=JSONObject(value); val version=root.getInt("version"); require(version in 1..2)
            val rows=root.getJSONArray("records")
            HighScores.ranked((0 until rows.length()).mapNotNull { i ->
                // A damaged row must not discard the other saved runs.
                try {
                    val row=rows.getJSONObject(i)
                    val score=row.getLong("score"); require(score in 0..Int.MAX_VALUE.toLong())
                    val outcome=ScoreOutcome.valueOf(row.getString("outcome"))
                    ScoreRecord(score.toInt(),outcome,
                        if(outcome==ScoreOutcome.LEGACY) null else Job.valueOf(row.getString("job")),
                        if(outcome==ScoreOutcome.LEGACY) null else row.getInt("kills"),
                        if(outcome==ScoreOutcome.LEGACY) null else row.getLong("finishedAt"),
                        if(version==1) GameMode.NORMAL else GameMode.valueOf(row.getString("mode"))).takeIf { it.valid }
                } catch(_: Exception) { null }
            })
        }
    } catch(_: Exception) { emptyList() }
}

/** History, the finished checkpoint and the existing best score share one preference transaction. */
internal class ScoreHistory(private val prefs: SharedPreferences) {
    // All clears before modes were introduced were normal-mode clears. Once written,
    // the explicit flag prevents a hard-mode result from granting this unlock.
    val normalCleared get()=prefs.getBoolean("normalCleared",prefs.getInt("clears",0)>0)
    fun load(): List<ScoreRecord> {
        val old=prefs.getString("scores",null)
        val records=HighScores.inheritBest(ScoreHistoryCodec.decode(old),prefs.getInt("best",0))
        val encoded=ScoreHistoryCodec.encode(records)
        if(old!=encoded) prefs.edit().putString("scores",encoded).apply()
        return records
    }

    fun finish(run: Run,clear: Boolean,at: Long=System.currentTimeMillis()): List<ScoreRecord> {
        val record=ScoreRecord(run.score,if(clear) ScoreOutcome.CLEAR else ScoreOutcome.GAMEOVER,run.job,run.kills,at,run.mode)
        require(record.valid)
        val records=HighScores.add(load(),record)
        prefs.edit().putString("scores",ScoreHistoryCodec.encode(records)).remove("run")
            .putBoolean("normalCleared",normalCleared || (clear && run.mode==GameMode.NORMAL))
            .putInt("best",records.first().score)
            .putInt("clears",prefs.getInt("clears",0)+if(clear) 1 else 0).apply()
        return records
    }
}
