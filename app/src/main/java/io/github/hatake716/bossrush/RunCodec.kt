package io.github.hatake716.bossrush

import org.json.JSONArray
import org.json.JSONObject

/** Version 1 adventures migrate at their current boss, without replaying earned rewards. */
object RunCodec {
    fun encode(run: Run): String = JSONObject().put("version",2).put("job",run.job.name).put("stage",run.stage)
        .put("levels",JSONArray(run.levels.toList())).put("inventory",JSONArray(run.inventory.map { it.name }))
        .put("gold",run.gold).put("score",run.score).put("time",run.totalTime).put("damage",run.totalDamage)
        .put("kills",run.kills).put("previousHp",run.previousHp).put("checkpoint",run.checkpoint)
        .put("story",run.storyEnabled).put("storyMoment",run.storyMoment.name)
        .put("storyPage",run.storyPage).put("storyBeforeStage",run.storyBeforeStage).toString()

    fun decode(str: String?): Run? = try {
        if(str==null) null else {
            val j=JSONObject(str); val version=j.getInt("version")
            require(version in 1..2)
            val levels=j.getJSONArray("levels"); val inventory=j.getJSONArray("inventory")
            require(levels.length()==4 && inventory.length()<=5)
            val r=Run(Job.valueOf(j.getString("job")),j.getInt("stage"),
                IntArray(4) { levels.getInt(it).also { v -> require(v in 1..16) } },
                j.getInt("gold"),MutableList(inventory.length()) { Item.valueOf(inventory.getString(it)) },
                j.getInt("score"),j.getDouble("time"),j.getDouble("damage"),j.getInt("kills"),
                j.getDouble("previousHp"),j.getString("checkpoint"),
                storyEnabled=if(version==1) true else j.getBoolean("story"),
                storyMoment=if(version==1) StoryMoment.PROLOGUE else StoryMoment.valueOf(j.getString("storyMoment")),
                storyPage=if(version==1) 0 else j.getInt("storyPage"),
                storyBeforeStage=if(version==1) -1 else j.getInt("storyBeforeStage"))
            require(r.stage in 0..31 && r.gold>=0 && r.kills in 0..32 && r.score>=0)
            require(r.checkpoint in (if(version==1) listOf("INTRO","SHOP") else listOf("INTRO","SHOP","STORY")))
            require(r.totalTime.isFinite() && r.totalDamage.isFinite() && r.previousHp.isFinite())
            require(r.totalTime>=0 && r.totalDamage>=0 && r.previousHp>=0)
            require(r.storyBeforeStage in -1..r.stage)
            require(r.storyPage in MainStory.pages(r.storyMoment,r.stage,r.job).indices)
            if(r.checkpoint=="STORY") {
                require(r.storyEnabled)
                when(r.storyMoment) {
                    StoryMoment.PROLOGUE -> require(r.stage==0 && r.kills==0)
                    StoryMoment.BEFORE -> require(r.kills==r.stage)
                    StoryMoment.AFTER -> require(r.kills==r.stage+1)
                    StoryMoment.EPILOGUE -> require(r.stage==31 && r.kills==32)
                }
            }
            r
        }
    } catch(_: Exception) { null }
}
