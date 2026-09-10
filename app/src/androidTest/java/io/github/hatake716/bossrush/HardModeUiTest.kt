package io.github.hatake716.bossrush

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HardModeUiTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(ins)
    private val prefs get()=ins.targetContext.getSharedPreferences("bossrush",0)
    @Before fun launch() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
        Configurator.getInstance().waitForIdleTimeout=100
        prefs.edit().clear().commit(); rule.launchActivity(Intent())
        assertTrue(device.wait(Until.hasObject(By.desc("はじめから  →")),5000))
        device.findObject(By.text("Got it"))?.click()
    }
    private fun <T> read(block: (GameView)->T): T {
        var value: T?=null
        ins.runOnMainSync { value=block(rule.activity.gameView) }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun tap(label: String) {
        (device.wait(Until.findObject(By.desc(label)),5000) ?: error("Missing $label")).click()
        SystemClock.sleep(150)
    }
    private fun reopen() { rule.finishActivity(); rule.launchActivity(Intent()); SystemClock.sleep(250) }
    private fun key(code: Int) {
        val t=SystemClock.uptimeMillis()
        for(action in listOf(KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP))
            assertTrue(ins.uiAutomation.injectInputEvent(KeyEvent(t,t,action,code,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true))
        SystemClock.sleep(180)
    }
    private fun screenshot(name: String): Int {
        val dir=File(ins.targetContext.getExternalFilesDir(null),"hard-mode").also { it.mkdirs() }
        val bmp=ins.uiAutomation.takeScreenshot(); var blue=0
        for(x in bmp.width*2/3 until bmp.width step 7) for(y in bmp.height/6 until bmp.height*2/3 step 7) {
            val c=bmp.getPixel(x,y); if(Color.blue(c)>Color.red(c)+15) blue++
        }
        File(dir,"$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }; bmp.recycle()
        return blue
    }
    @Test fun firstNormalClearUnlocksColoredTitleAndControllerHardStartThenPersists() {
        assertFalse(read { it.normalCleared }); assertFalse(device.hasObject(By.desc("ハードモード  →")))
        val gray=screenshot("title-locked")
        // Drive the final boss through its real reward and epilogue completion callbacks.
        read { v ->
            val e=v.engine; e.run=Run(Job.WARRIOR,stage=31,kills=31,storyEnabled=true,storyBeforeStage=31)
            e.beginBattle(); e.elapsed=30.0; e.victory()
            repeat(37) { e.update(.05) }
        }
        assertFalse(read { it.normalCleared })
        tap("剣を選択"); tap("物語のつづきへ →"); tap("この場面をスキップ")
        assertEquals(StoryMoment.EPILOGUE,read { it.engine.run!!.storyMoment }); assertFalse(read { it.normalCleared })
        reopen(); tap("つづきから"); tap("この場面をスキップ")
        assertEquals(Screen.ENDING,read { it.engine.screen }); assertTrue(prefs.getBoolean("normalCleared",false))
        assertEquals(1,prefs.getInt("clears",0)); screenshot("normal-clear-unlock")
        tap("タイトルへ"); assertTrue(device.hasObject(By.desc("ハードモード  →")))
        assertTrue(screenshot("title-unlocked")>gray+30); assertNull(read { it.audio.playingId })
        reopen(); assertTrue(read { it.normalCleared })
        repeat(3) { if(read { it.controller.focusLabel }!="ハードモード  →") key(KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertEquals("ハードモード  →",read { it.controller.focusLabel })
        key(KeyEvent.KEYCODE_BUTTON_A); assertEquals(GameMode.HARD,read { it.engine.selectedMode })
        tap("この職業で出発  →"); assertEquals(GameMode.HARD,read { it.engine.run!!.mode })
        val saved=prefs.getString("run",null); assertEquals(GameMode.HARD,RunCodec.decode(saved)!!.mode)
        reopen(); assertTrue(read { it.normalCleared }); tap("つづきから")
        assertEquals(GameMode.HARD,read { it.engine.mode }); assertEquals(GameMode.HARD,read { it.engine.selectedMode })
        assertEquals(saved,prefs.getString("run",null))
        tap("この場面をスキップ"); tap("この場面をスキップ"); screenshot("hard-intro")
        tap("戦闘開始  →"); tap("II"); screenshot("hard-battle")
    }
    @Test fun previousClearsUnlockWithoutChangingAdventureAndCancelledModeChoiceKeepsSave() {
        val save=RunCodec.encode(Run(Job.MAGE,stage=4,kills=4,score=22222))
        val history=ScoreHistoryCodec.encode(listOf(ScoreRecord(54321,ScoreOutcome.CLEAR,Job.MAGE,32,1000)))
        prefs.edit().putInt("clears",2).putInt("best",54321).putString("scores",history)
            .putBoolean("sound",false).putString("run",save).commit()
        val before=prefs.all.toMap()
        reopen(); assertTrue(read { it.normalCleared }); assertEquals(before,prefs.all)
        tap("ハードモード  →")
        (device.wait(Until.findObject(By.text("戻る")),3000) ?: error("Missing dialog cancel")).click()
        SystemClock.sleep(150); assertEquals(Screen.TITLE,read { it.engine.screen }); assertEquals(before,prefs.all)
        tap("ハードモード  →")
        (device.wait(Until.findObject(By.text("職業を選ぶ")),3000) ?: error("Missing dialog confirm")).click()
        SystemClock.sleep(150); assertEquals(GameMode.HARD,read { it.engine.selectedMode }); assertEquals(save,prefs.getString("run",null))
        device.pressBack(); SystemClock.sleep(150); tap("つづきから")
        assertEquals(GameMode.NORMAL,read { it.engine.mode }); assertEquals(22222,read { it.engine.run!!.score })
    }
    @Test fun scoresKeepBothModesAndHardResultAloneCannotUnlockNormalClearReward() {
        read { v ->
            val e=v.engine; e.selectedMode=GameMode.HARD; e.newRun(); e.beginBattle()
            e.boss.hp=e.boss.maxHp*.6; e.hurt(1e6)
            assertEquals(3600,v.scoreRecords.single().score); assertEquals(GameMode.HARD,v.scoreRecords.single().mode)
            assertFalse(v.normalCleared)
            e.newRun(); e.run=Run(Job.WARRIOR,stage=31,kills=31,mode=GameMode.HARD)
            e.beginBattle(); e.elapsed=30.0; e.victory(); repeat(37) { e.update(.05) }
            e.selectUpgrade(0); e.finishReward(); e.finishReward()
            assertEquals(61500,v.scoreRecords.first().score); assertFalse(v.normalCleared)
        }
        reopen(); assertFalse(read { it.normalCleared }); assertFalse(device.hasObject(By.desc("ハードモード  →")))
        tap("ハイスコア"); assertTrue(read { it.contentDescription.toString().contains("ハードモード") })
        val saved=read { it.scoreRecords }; assertEquals(2,saved.size); assertTrue(saved.all { it.mode==GameMode.HARD })
        tap("タイトルへ"); tap("はじめから  →"); tap("この職業で出発  →")
        assertEquals(GameMode.NORMAL,read { it.engine.mode })
        read { v -> v.engine.beginBattle(); v.engine.hurt(1e6) }
        reopen(); assertEquals(3,read { it.scoreRecords.size }); assertEquals(1,read { it.scoreRecords.count { r -> r.mode==GameMode.NORMAL } })
        tap("ハイスコア"); screenshot("scores-both-modes")
    }
    @Test fun saveAndHistoryCodecsMigrateOldVersionsAndRejectUnknownModes() {
        for(mode in GameMode.entries) {
            val r=Run(Job.SUMMONER,stage=8,kills=8,score=40000,mode=mode)
            val restored=RunCodec.decode(RunCodec.encode(r))!!
            assertEquals(mode,restored.mode); assertEquals(r.score,restored.score); assertEquals(r.stage,restored.stage)
        }
        for(version in 1..2) {
            val old=JSONObject(RunCodec.encode(Run(Job.THIEF,stage=2,kills=2,score=789))).put("version",version)
            old.remove("mode")
            assertEquals(GameMode.NORMAL,RunCodec.decode(old.toString())!!.mode)
        }
        val bad=JSONObject(RunCodec.encode(Run(Job.WARRIOR))).put("mode","EXTREME")
        assertNull(RunCodec.decode(bad.toString()))
        val records=listOf(ScoreRecord(9000,ScoreOutcome.GAMEOVER,Job.THIEF,2,1000))
        val oldScores=JSONObject(ScoreHistoryCodec.encode(records)).put("version",1)
        oldScores.getJSONArray("records").getJSONObject(0).remove("mode")
        assertEquals(records,ScoreHistoryCodec.decode(oldScores.toString()))
        val hard=records.single().copy(mode=GameMode.HARD)
        assertEquals(listOf(hard),ScoreHistoryCodec.decode(ScoreHistoryCodec.encode(listOf(hard))))
    }
}
