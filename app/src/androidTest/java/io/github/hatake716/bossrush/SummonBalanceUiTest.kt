package io.github.hatake716.bossrush

import android.content.Intent
import android.graphics.Bitmap
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
class SummonBalanceUiTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val device get()=UiDevice.getInstance(ins)
    @Before fun emulatorOnly() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
        Configurator.getInstance().waitForIdleTimeout=100
        rule.launchActivity(Intent())
        ins.runOnMainSync {
            val e=rule.activity.gameView.engine
            e.run=Run(Job.SUMMONER); e.beginBattle()
            e.boss=Actor(300.0,125.0,1e7,1e7); e.nextPattern=1e6
            e.hazards.add(Hazard("circle",-1000.0,-1000.0,1.0,delay=1e6))
        }
    }
    private fun tap(label: String) {
        val button=device.wait(Until.findObject(By.desc(label)),5000) ?: error("Missing $label")
        button.click(); SystemClock.sleep(100)
    }
    private fun awaitGame(label: String,timeout: Long=4000,condition: (GameEngine)->Boolean) {
        val deadline=SystemClock.uptimeMillis()+timeout
        var ready=false
        while(!ready && SystemClock.uptimeMillis()<deadline) {
            ins.runOnMainSync { ready=condition(rule.activity.gameView.engine) }
            if(!ready) SystemClock.sleep(50)
        }
        ins.runOnMainSync {
            val e=rule.activity.gameView.engine
            assertTrue("$label: ${e.screen}, elapsed=${e.elapsed}",ready)
        }
    }
    private fun screenshot(name: String) {
        val dir=File(ins.targetContext.getExternalFilesDir(null),"summon-balance").also { it.mkdirs() }
        val bitmap=ins.uiAutomation.takeScreenshot()
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun touchSummonBlocksTheFirstFloorHitThenAllowsTheNextAndShowsUpdatedGrowthText() {
        tap("技3 はにわを呼ぶ")
        ins.runOnMainSync {
            val e=rule.activity.gameView.engine
            assertEquals(1,e.summons.size)
            e.hazards.add(Hazard("circle",e.player.x,e.player.y,50.0,delay=.5))
        }
        awaitGame("First attack consumed the guardian") { it.summons.isEmpty() }
        ins.runOnMainSync {
            val e=rule.activity.gameView.engine
            assertTrue(e.summons.isEmpty()); assertEquals(e.player.maxHp,e.player.hp,0.0)
            assertEquals(0.0,e.damageTaken,0.0); assertEquals("はにわが身代わりになった！",e.message)
        }
        screenshot("haniwa-blocked")
        ins.runOnMainSync {
            val e=rule.activity.gameView.engine
            e.hazards.add(Hazard("circle",e.player.x,e.player.y,50.0,delay=.1))
        }
        awaitGame("Next attack damaged the summoner") { it.damageTaken>0 }
        ins.runOnMainSync {
            val e=rule.activity.gameView.engine
            assertEquals(e.bossDamage(),e.damageTaken,1e-8)
            e.gauge=100.0; e.player.hp=20.0
        }
        tap("技2 白ウサギを呼ぶ")
        awaitGame("Rabbit's first healing tick") { it.player.hp>20 }
        ins.runOnMainSync { assertEquals(28.0,rule.activity.gameView.engine.player.hp,1e-8) }
        screenshot("rabbit-heal-eight")
        ins.runOnMainSync { rule.activity.gameView.engine.victory() }
        tap("はにわを呼ぶを選択"); screenshot("summoner-growth")
        tap("技の選択をキャンセル")
        ins.runOnMainSync { assertEquals(-1,rule.activity.gameView.engine.pendingUpgrade) }
    }
    @Test fun levelSixteenGuardExpiresAfterFiveActiveSecondsWithFullInitialLifeBar() {
        var expiresAt=0.0
        ins.runOnMainSync { rule.activity.gameView.engine.levels[2]=16 }
        tap("技3 はにわを呼ぶ")
        ins.runOnMainSync {
            val e=rule.activity.gameView.engine
            val summon=e.summons.single()
            assertTrue(summon.life/Skills.summonDuration(summon.kind,summon.level)>.85)
            expiresAt=e.elapsed+summon.life
            e.pause()
        }
        SystemClock.sleep(5200)
        ins.runOnMainSync {
            val e=rule.activity.gameView.engine
            assertEquals(1,e.summons.size); assertTrue(e.summons.single().life>4.0)
            e.unpause()
        }
        SystemClock.sleep(100); screenshot("haniwa-active")
        awaitGame("Five active combat seconds",12000) { it.elapsed>=expiresAt }
        ins.runOnMainSync {
            val e=rule.activity.gameView.engine
            assertTrue(e.summons.isEmpty()); assertEquals(e.player.maxHp,e.player.hp,0.0)
            e.hurt(10.0); assertEquals(10.0,e.damageTaken,1e-8)
        }
        screenshot("haniwa-expired")
    }
}
