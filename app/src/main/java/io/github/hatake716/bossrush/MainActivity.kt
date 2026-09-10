package io.github.hatake716.bossrush

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class MainActivity: ComponentActivity() {
    lateinit var gameView: GameView
        private set
    private val prefs by lazy { getSharedPreferences("bossrush",MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        if(Build.VERSION.SDK_INT>=28) window.attributes=window.attributes.apply {
            layoutInDisplayCutoutMode=if(Build.VERSION.SDK_INT>=30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val engine=GameEngine()
        gameView=GameView(this,engine)
        val history=ScoreHistory(prefs)
        gameView.scoreRecords=history.load()
        gameView.normalCleared=history.normalCleared
        engine.onCheckpoint={ saveRun(engine.run) }
        engine.onResult={ _,clear ->
            gameView.scoreRecords=history.finish(checkNotNull(engine.run),clear)
            gameView.normalCleared=history.normalCleared
            gameView.hasSave=false; gameView.bestScore=gameView.scoreRecords.firstOrNull()?.score ?: 0
        }
        gameView.hasSave=loadRun()!=null
        gameView.bestScore=gameView.scoreRecords.firstOrNull()?.score ?: 0
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
        prefs.edit().putString("run",RunCodec.encode(run)).apply(); gameView.hasSave=true
    }
    private fun loadRun(): Run? = RunCodec.decode(prefs.getString("run",null))
    override fun onResume() { super.onResume(); if(::gameView.isInitialized) gameView.resume() }
    override fun onPause() { if(::gameView.isInitialized) gameView.suspend(); super.onPause() }
    override fun onDestroy() { if(::gameView.isInitialized) gameView.audio.stop(); super.onDestroy() }
}
