package io.github.hatake716.bossrush

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
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
class MainStoryUiTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(ins)
    @Before fun launch() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
        Configurator.getInstance().waitForIdleTimeout=100
        ins.targetContext.getSharedPreferences("bossrush",0).edit().clear().commit()
        rule.launchActivity(Intent())
        assertTrue(device.wait(Until.hasObject(By.desc("はじめから  →")),5000))
        device.findObject(By.text("Got it"))?.click()
    }
    private fun <T> read(block: (GameEngine)->T): T {
        var value: T?=null
        ins.runOnMainSync { value=block(rule.activity.gameView.engine) }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun tap(label: String) {
        val node=device.wait(Until.findObject(By.desc(label)),5000) ?: error("Missing $label")
        node.click(); SystemClock.sleep(130)
    }
    private fun start() { tap("はじめから  →"); tap("この職業で出発  →") }
    private fun reopen() { rule.finishActivity(); rule.launchActivity(Intent()); tap("つづきから") }
    private fun screenshot(name: String) {
        val dir=File(ins.targetContext.getExternalFilesDir(null),"story-screenshots"); dir.mkdirs()
        ins.uiAutomation.takeScreenshot().let { bmp ->
            File(dir,"$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }; bmp.recycle()
        }
    }
    @Test fun newAdventureHasReadablePagesAndPersistsCurrentPageAcrossActivityRestart() {
        start(); assertEquals(Screen.STORY,read { it.screen })
        assertEquals(StoryMoment.PROLOGUE,read { it.run!!.storyMoment }); screenshot("prologue-device")
        tap("次へ →"); tap("次へ →")
        assertEquals(2,read { it.run!!.storyPage }); val line=read { it.storyLine }
        reopen(); assertEquals(2,read { it.run!!.storyPage }); assertEquals(line,read { it.storyLine })
        assertTrue(rule.activity.gameView.contentDescription.toString().contains(line.text))
        tap("前のページ"); assertEquals(1,read { it.run!!.storyPage })
        device.pressBack(); SystemClock.sleep(180); tap("つづきから")
        assertEquals(1,read { it.run!!.storyPage })
        tap("この場面をスキップ"); assertEquals(StoryMoment.BEFORE,read { it.run!!.storyMoment })
        tap("次へ →"); screenshot("ratatoskr-device")
        tap("この場面をスキップ"); tap("戦闘開始  →")
        assertEquals(Screen.BATTLE,read { it.screen })
    }
    @Test fun victoryGrowthAfterStoryAndNextBossResumeWithoutDuplicateLoot() {
        start(); tap("この場面をスキップ"); tap("この場面をスキップ"); tap("戦闘開始  →")
        // A completed battle fixture isolates the new reward -> story checkpoint boundary.
        read { it.elapsed=30.0; it.victory() }
        tap("剣を選択"); tap("物語のつづきへ →")
        assertEquals(StoryMoment.AFTER,read { it.run!!.storyMoment })
        val gold=read { it.run!!.gold }; val score=read { it.run!!.score }
        screenshot("first-color-device")
        tap("次へ →"); reopen()
        assertEquals(1,read { it.run!!.storyPage }); assertEquals(1,read { it.run!!.kills })
        assertEquals(2,read { it.levels[0] }); assertEquals(gold,read { it.run!!.gold }); assertEquals(score,read { it.run!!.score })
        tap("旅の商人へ →"); assertEquals(Screen.SHOP,read { it.screen })
        tap("次のボスへ  →"); assertEquals(StoryMoment.BEFORE,read { it.run!!.storyMoment })
        assertEquals(1,read { it.run!!.stage }); screenshot("dainn-device")
        reopen(); assertEquals(1,read { it.run!!.stage }); assertEquals(0,read { it.run!!.storyPage })
    }
    @Test fun finalGodLeadsToSavedColorEpilogueAndRecordsClearOnce() {
        read { e ->
            e.run=Run(Job.WARRIOR,stage=31,kills=31,storyEnabled=true,storyBeforeStage=31)
            e.beginBattle(); e.elapsed=30.0; e.victory()
        }
        tap("剣を選択"); tap("物語のつづきへ →")
        assertEquals(StoryMoment.AFTER,read { it.run!!.storyMoment })
        screenshot("odin-after-device")
        tap("この場面をスキップ")
        assertEquals(StoryMoment.EPILOGUE,read { it.run!!.storyMoment })
        tap("次へ →"); reopen(); assertEquals(1,read { it.run!!.storyPage })
        screenshot("epilogue-device")
        val bitmap=ins.uiAutomation.takeScreenshot(); var blue=0
        for(x in 0 until bitmap.width step 8) for(y in 0 until bitmap.height step 8) {
            val c=bitmap.getPixel(x,y)
            if(android.graphics.Color.blue(c)>android.graphics.Color.red(c)+15) blue++
        }
        bitmap.recycle(); assertTrue("The epilogue restores a full-color world",blue>30)
        tap("この場面をスキップ"); assertEquals(Screen.ENDING,read { it.screen })
        val prefs=ins.targetContext.getSharedPreferences("bossrush",0)
        assertEquals(1,prefs.getInt("clears",0)); assertFalse(prefs.contains("run"))
        tap("タイトルへ"); assertEquals(1,prefs.getInt("clears",0)); assertFalse(rule.activity.gameView.hasSave)
    }
    @Test fun saveCodecMigratesLegacyAdventureAndRejectsInvalidStoryPage() {
        val r=Run(Job.MAGE,stage=13,gold=999,score=34000,kills=13,checkpoint="SHOP")
        val legacy=JSONObject(RunCodec.encode(r)).put("version",1)
        listOf("story","storyMoment","storyPage","storyBeforeStage").forEach { legacy.remove(it) }
        val restored=RunCodec.decode(legacy.toString())!!
        assertTrue(restored.storyEnabled); assertEquals(999,restored.gold); assertEquals(13,restored.stage)
        val e=GameEngine(); e.run=restored; e.resumeRun(); assertEquals(Screen.SHOP,e.screen)
        e.leaveShop(); assertEquals(StoryMoment.BEFORE,e.run!!.storyMoment)
        assertEquals(13,e.run!!.stage); assertEquals(34000,e.run!!.score)
        for(job in Job.entries) for(stage in 0..31) for(moment in listOf(StoryMoment.BEFORE,StoryMoment.AFTER)) {
            val value=Run(job,stage=stage,kills=stage+if(moment==StoryMoment.AFTER) 1 else 0,
                checkpoint="STORY",storyEnabled=true,storyMoment=moment,storyPage=MainStory.pages(moment,stage,job).lastIndex)
            val decoded=RunCodec.decode(RunCodec.encode(value))!!
            assertEquals(value.storyPage,decoded.storyPage); assertEquals(moment,decoded.storyMoment); assertEquals(stage,decoded.stage)
        }
        val invalid=JSONObject(RunCodec.encode(e.run!!)).put("storyPage",999)
        assertNull(RunCodec.decode(invalid.toString()))
        assertNull(RunCodec.decode("{}"))
    }
    @Test fun storyRoutesToSilentPrologueBossThemeAndColorEndingMusic() {
        fun awaitMusic(id: String?) {
            val deadline=SystemClock.uptimeMillis()+6000
            while(SystemClock.uptimeMillis()<deadline) {
                if(rule.activity.gameView.audio.playingId==id) return
                SystemClock.sleep(50)
            }
            assertEquals(id,rule.activity.gameView.audio.playingId)
        }
        start(); awaitMusic(null)
        tap("この場面をスキップ"); awaitMusic("ratatoskr")
        tap("この場面をスキップ"); awaitMusic("ratatoskr")
        tap("戦闘開始  →"); read { it.victory() }
        tap("剣を選択"); tap("物語のつづきへ →"); awaitMusic("shop")
        read { e ->
            e.run=Run(Job.WARRIOR,stage=31,kills=32,checkpoint="STORY",storyEnabled=true,storyMoment=StoryMoment.EPILOGUE)
            e.resumeRun()
        }
        awaitMusic("ending"); device.pressBack(); awaitMusic(null)
    }
    @Test fun allJapaneseGlyphsAndEveryBossDialogueRenderInsideTheWindow() {
        val dir=File(ins.targetContext.getExternalFilesDir(null),"story-screenshots"); dir.mkdirs()
        val pages=Job.entries.flatMap { job -> StoryMoment.entries.flatMap { moment ->
            (0..31).flatMap { stage -> MainStory.pages(moment,stage,job) }
        } }
        ins.runOnMainSync {
            val font=rule.activity.gameView.storyText
            for(ch in pages.flatMap { (it.speaker+it.text).toList() }.toSet()) {
                assertTrue("Missing Japanese pixel glyph: $ch",font.hasGlyph(ch))
            }
            val canvasBitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            val e=GameEngine(); val view=GameView(ins.targetContext,e); view.layout(0,0,960,540)
            for(stage in 0..31) for(moment in listOf(StoryMoment.BEFORE,StoryMoment.AFTER)) {
                e.run=Run(Job.entries[stage%4],stage=stage,kills=stage+if(moment==StoryMoment.AFTER) 1 else 0,
                    checkpoint="STORY",storyEnabled=true,storyMoment=moment,storyPage=1)
                e.resumeRun(); view.draw(Canvas(canvasBitmap))
                val next=view.controllerButtons().first { it.label==e.storyAdvanceLabel }
                assertTrue(next.rect.left>=0 && next.rect.right<=960 && next.rect.bottom<=540)
                if(stage in listOf(0,17,19,31) && moment==StoryMoment.BEFORE)
                    File(dir,"dialogue-${MainStory.chapters[stage].id}.png").outputStream().use { canvasBitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            canvasBitmap.recycle(); view.audio.stop()
        }
    }
}
