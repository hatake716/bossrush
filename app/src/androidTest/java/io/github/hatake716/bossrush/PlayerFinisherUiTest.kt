package io.github.hatake716.bossrush

import android.content.Intent
import android.graphics.*
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlayerFinisherUiTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val ins=InstrumentationRegistry.getInstrumentation()
    @Before fun emulatorOnly() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
    }
    @Test fun touchFourthButtonRespectsHpAndOneUseThenRewardCanUpgradeOrCancel() {
        Configurator.getInstance().waitForIdleTimeout=100
        rule.launchActivity(Intent())
        val device=UiDevice.getInstance(ins)
        for(job in Job.entries) {
            ins.runOnMainSync {
                val e=rule.activity.gameView.engine
                e.run=Run(job); e.beginBattle(); e.player.hp=e.player.maxHp/2
                e.boss=Actor(300.0,145.0,1e6,1e6); e.nextPattern=1e6; e.invulnerability=1e6
            }
            val label="技4 ${Skills.all.getValue(job)[3].name}"
            val button=device.wait(Until.findObject(By.desc(label)),5000) ?: error("Missing $label")
            button.click(); SystemClock.sleep(100)
            ins.runOnMainSync { assertFalse(rule.activity.gameView.engine.playerFinisherUsed) }
            ins.runOnMainSync { rule.activity.gameView.engine.player.hp=rule.activity.gameView.engine.player.maxHp/3 }
            SystemClock.sleep(100); button.click(); SystemClock.sleep(200)
            ins.runOnMainSync { assertTrue(rule.activity.gameView.engine.playerFinisherUsed) }
            button.click(); SystemClock.sleep(150)
            ins.runOnMainSync {
                val e=rule.activity.gameView.engine
                if(job==Job.WARRIOR) assertEquals(10,e.playerFinisherHits)
                if(job==Job.SUMMONER) assertEquals(5,e.summons.count { it.finisher })
                if(job==Job.THIEF) assertTrue(e.buffs.getValue("vanish")<10.0)
                e.victory()
            }
            val choice=device.wait(Until.findObject(By.desc("${Skills.all.getValue(job)[3].name}を選択")),5000) ?: error("Missing finisher upgrade")
            choice.click(); SystemClock.sleep(100)
            val cancel=device.wait(Until.findObject(By.desc("技の選択をキャンセル")),3000) ?: error("Missing cancel")
            cancel.click(); SystemClock.sleep(100)
            ins.runOnMainSync {
                val e=rule.activity.gameView.engine
                assertEquals(-1,e.pendingUpgrade); assertEquals(1,e.levels[3])
                assertTrue(e.selectUpgrade(3))
                if(job==Job.THIEF) e.chooseLoot(Item.HOURGLASS)
                e.finishReward(); assertEquals(2,e.levels[3])
            }
        }
    }
    @Test fun fixtureAllFinishersRenderWithReadableFootMarkersAndNoExtraDamage() {
        val ctx=ins.targetContext
        val dir=File(ctx.getExternalFilesDir(null),"finishers").also { it.mkdirs() }
        ins.runOnMainSync {
            val sheet=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888); val canvas=Canvas(sheet)
            val e=GameEngine(); val view=GameView(ctx,e); view.layout(0,0,960,540)
            val frame=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            for((index,job) in Job.entries.withIndex()) {
                e.run=Run(job,stage=30,levels=intArrayOf(1,1,1,16)); e.beginBattle()
                e.player.hp=e.player.maxHp/3; e.boss=Actor(300.0,140.0,1e7,1e7)
                e.player.x=365.0; e.player.y=246.0; e.nextPattern=1e6
                e.hazards.add(Hazard("line",470.0,160.0,280.0,45.0,delay=10.0))
                e.useSkill(3)
                repeat(if(job==Job.WARRIOR) 14 else if(job==Job.MAGE) 29 else 35) { e.update(.01) }
                view.draw(Canvas(frame)); val damage=e.damageDone
                view.draw(Canvas(frame)); assertEquals(damage,e.damageDone,0.0)
                assertEquals(Ink.light,frame.getPixel((28+e.player.x).toInt(),(96+e.player.y).toInt()))
                if(job==Job.SUMMONER) assertEquals(5,e.summons.size)
                File(dir,"finisher-${job.name.lowercase()}.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG,100,it) }
                canvas.drawBitmap(frame,(index%2)*960f,(index/2)*540f,null)
            }
            File(dir,"finishers.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG,100,it) }
            frame.recycle(); sheet.recycle(); view.suspend()
        }
    }
}
