package io.github.hatake716.bossrush

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScoreHistoryUiTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(ins)
    private val prefs get()=ins.targetContext.getSharedPreferences("bossrush",0)
    @Before fun launch() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
        Configurator.getInstance().waitForIdleTimeout=100
        prefs.edit().clear().commit(); rule.launchActivity(Intent())
        assertTrue(device.wait(Until.hasObject(By.desc("ハイスコア")),5000))
        device.findObject(By.text("Got it"))?.click()
    }
    private fun <T> read(block: (GameView)->T): T {
        var value: T?=null
        ins.runOnMainSync { value=block(rule.activity.gameView) }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun tap(label: String) {
        (device.wait(Until.findObject(By.desc(label)),5000) ?: error("Missing $label")).click()
        SystemClock.sleep(130)
    }
    private fun reopen() { rule.finishActivity(); rule.launchActivity(Intent()); SystemClock.sleep(200) }
    private fun key(code: Int) {
        val t=SystemClock.uptimeMillis()
        for(action in listOf(KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP))
            assertTrue(ins.uiAutomation.injectInputEvent(KeyEvent(t,t,action,code,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true))
        SystemClock.sleep(170)
    }
    private fun screenshot(name: String) {
        val dir=File(ins.targetContext.getExternalFilesDir(null),"high-scores").also { it.mkdirs() }
        ins.uiAutomation.takeScreenshot().let { bmp ->
            File(dir,"$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }; bmp.recycle()
        }
    }
    @Test fun emptyHistoryOpensFromTitleWithTouchAndControllerAndStaysSilent() {
        screenshot("title-high-scores")
        tap("ハイスコア")
        assertEquals(Screen.SCORES,read { it.engine.screen })
        assertTrue(read { it.contentDescription.toString().contains("記録はまだありません") })
        assertNull(read { it.audio.playingId }); screenshot("scores-empty")
        tap("タイトルへ")
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("ハイスコア",read { it.controller.focusLabel })
        key(KeyEvent.KEYCODE_BUTTON_A); assertEquals(Screen.SCORES,read { it.engine.screen })
        key(KeyEvent.KEYCODE_BUTTON_B); assertEquals(Screen.TITLE,read { it.engine.screen })
        read { it.engine.newRun() }
        assertTrue(read { it.scoreRecords.isEmpty() }) // Starting or pausing is not a finished play.
    }
    @Test fun oldBestMigratesOnceAndReadingHistoryPreservesTheCurrentAdventure() {
        val save=RunCodec.encode(Run(Job.MAGE,stage=4,kills=4,score=7000))
        prefs.edit().remove("scores").putInt("best",234567).putInt("clears",2).putBoolean("sound",false).putString("run",save).commit()
        reopen(); tap("ハイスコア")
        assertEquals(listOf(ScoreRecord(234567,ScoreOutcome.LEGACY)),read { it.scoreRecords })
        assertTrue(read { it.contentDescription.toString().contains("以前の最高記録") })
        screenshot("scores-inherited")
        device.pressBack(); SystemClock.sleep(150); reopen(); tap("ハイスコア")
        assertEquals(1,read { it.scoreRecords.size }); assertEquals(234567,read { it.bestScore })
        assertEquals(save,prefs.getString("run",null)); assertEquals(2,prefs.getInt("clears",0)); assertFalse(prefs.getBoolean("sound",true))
        tap("タイトルへ"); tap("つづきから")
        assertEquals(4,read { it.engine.run!!.stage }); assertEquals(7000,read { it.engine.run!!.score })
        assertEquals(1,read { it.scoreRecords.size })
    }
    @Test fun gameOversAndClearAreSavedOnceAndOnlyTopTenSurviveRestart() {
        val scores=listOf(1000,4000,500,12000,3000,8000,100,15000,6000,9000,11000,13000,7000)
        for((i,score) in scores.withIndex()) read { v ->
            val e=v.engine; e.selectedJob=Job.entries[i%4]; e.newRun()
            e.run!!.score=score; e.run!!.kills=i; e.run!!.stage=i; e.beginBattle()
            e.hurt(100000.0); e.hurt(100000.0)
            assertEquals(Screen.GAMEOVER,e.screen)
            assertEquals(minOf(i+1,10),v.scoreRecords.size)
            assertFalse(prefs.contains("run"))
        }
        assertEquals(scores.sortedDescending().take(10),read { it.scoreRecords.map { r -> r.score } })
        val before=read { it.scoreRecords }
        read { v ->
            val e=v.engine; e.selectedJob=Job.SUMMONER; e.newRun()
            e.run!!.stage=31; e.run!!.kills=31; e.run!!.score=16000; e.beginBattle(); e.ultimateUsed=true
            e.damageBoss(e.boss.maxHp)
            assertEquals(before,v.scoreRecords) // A boss victory is not the run's final result yet.
            repeat(37) { e.update(.05) }; e.selectUpgrade(0); e.finishReward(); e.finishReward()
            assertEquals(Screen.ENDING,e.screen)
            assertEquals(1,prefs.getInt("clears",0))
        }
        val final=read { it.scoreRecords }
        assertEquals(10,final.size); assertEquals(41000,final.first().score)
        assertEquals(ScoreOutcome.CLEAR,final.first().outcome); assertEquals(32,final.first().kills)
        assertEquals(Job.SUMMONER,final.first().job); assertNotNull(final.first().finishedAt)
        assertEquals(ScoreOutcome.GAMEOVER,final[1].outcome)
        reopen(); tap("ハイスコア")
        assertEquals(final,read { it.scoreRecords }); assertEquals(41000,read { it.bestScore })
        assertTrue(read { it.contentDescription.toString().contains("10位") })
        screenshot("scores-top-ten")
        tap("タイトルへ"); tap("はじめから  →"); tap("この職業で出発  →")
        reopen(); tap("ハイスコア"); assertEquals(final,read { it.scoreRecords })
    }
    @Test fun codecRoundTripsAndRecoversOtherRowsAndLegacyBestFromBadData() {
        val records=listOf(ScoreRecord(500,ScoreOutcome.CLEAR,Job.THIEF,32,1000),ScoreRecord(100,ScoreOutcome.LEGACY))
        assertEquals(records,ScoreHistoryCodec.decode(ScoreHistoryCodec.encode(records)))
        val value=JSONObject(ScoreHistoryCodec.encode(records))
        value.getJSONArray("records").put(JSONObject().put("score",-5)).put("not a row")
        assertEquals(records,ScoreHistoryCodec.decode(value.toString()))
        assertTrue(ScoreHistoryCodec.decode("broken").isEmpty())
        assertTrue(ScoreHistoryCodec.decode(JSONObject().put("version",9).put("records",JSONArray()).toString()).isEmpty())
        prefs.edit().putInt("best",500).putString("scores","broken").commit()
        assertEquals(listOf(ScoreRecord(500,ScoreOutcome.LEGACY)),ScoreHistory(prefs).load())
    }
}
