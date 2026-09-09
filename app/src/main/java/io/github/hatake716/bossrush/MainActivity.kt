package io.github.hatake716.bossrush

import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import org.json.JSONArray
import org.json.JSONObject

class MainActivity: ComponentActivity() {
    lateinit var gameView: GameView
        private set
    private val prefs by lazy { getSharedPreferences("bossrush",MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val engine=GameEngine()
        gameView=GameView(this,engine)
        engine.onCheckpoint={ saveRun(engine.run) }
        engine.onResult={ score,clear ->
            prefs.edit().remove("run").putInt("best",maxOf(score,prefs.getInt("best",0)))
                .putInt("clears",prefs.getInt("clears",0)+if(clear) 1 else 0).apply()
            gameView.hasSave=false; gameView.bestScore=prefs.getInt("best",0)
        }
        gameView.hasSave=loadRun()!=null
        gameView.bestScore=prefs.getInt("best",0)
        gameView.audio.enabled=prefs.getBoolean("sound",true)
        gameView.onSoundChanged={ prefs.edit().putBoolean("sound",it).apply() }
        gameView.onContinue={ loadRun()?.let { engine.run=it; engine.selectedJob=it.job; engine.resumeRun() } }
        setContentView(gameView)
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { gameView.goBack() }
        })
    }
    private fun saveRun(run: Run?) {
        run ?: return
        val json=JSONObject().put("version",1).put("job",run.job.name).put("stage",run.stage)
            .put("levels",JSONArray(run.levels.toList())).put("inventory",JSONArray(run.inventory.map { it.name }))
            .put("gold",run.gold).put("score",run.score).put("time",run.totalTime).put("damage",run.totalDamage)
            .put("kills",run.kills).put("previousHp",run.previousHp).put("checkpoint",run.checkpoint)
        prefs.edit().putString("run",json.toString()).apply(); gameView.hasSave=true
    }
    private fun loadRun(): Run? = try {
        val str=prefs.getString("run",null)
        if(str==null) null else {
            val j=JSONObject(str)
            require(j.getInt("version")==1)
            val levels=j.getJSONArray("levels"); val inventory=j.getJSONArray("inventory")
            require(levels.length()==4 && inventory.length()<=5)
            val r=Run(Job.valueOf(j.getString("job")),j.getInt("stage"),IntArray(4) { levels.getInt(it).also { v -> require(v in 1..16) } },
                j.getInt("gold"),MutableList(inventory.length()) { Item.valueOf(inventory.getString(it)) },j.getInt("score"),
                j.getDouble("time"),j.getDouble("damage"),j.getInt("kills"),j.getDouble("previousHp"),j.getString("checkpoint"))
            require(r.stage in 0..31 && r.gold>=0 && r.kills in 0..32 && r.checkpoint in listOf("INTRO","SHOP"))
            require(r.totalTime.isFinite() && r.totalDamage.isFinite() && r.previousHp.isFinite())
            r
        }
    } catch(_: Exception) { null }
    override fun onResume() { super.onResume(); if(::gameView.isInitialized) gameView.resume() }
    override fun onPause() { if(::gameView.isInitialized) gameView.suspend(); super.onPause() }
    override fun onDestroy() { if(::gameView.isInitialized) gameView.audio.stop(); super.onDestroy() }
}
