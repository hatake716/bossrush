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
class EnemyBarrageUiTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(ins)
    private val dir get()=File(ins.targetContext.getExternalFilesDir(null),"barrage").also { it.mkdirs() }
    @Before fun emulatorOnly() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
    }
    private fun save(b: Bitmap,name: String) { File(dir,"$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) } }
    private fun <T> read(block: (GameView)->T): T {
        var value: T?=null; ins.runOnMainSync { value=block(rule.activity.gameView) }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun tap(label: String) {
        (device.wait(Until.findObject(By.desc(label)),5000) ?: error("Missing $label")).click(); SystemClock.sleep(130)
    }
    @Test fun allThirtyTwoBossesRenderWarningsAndDifferentMovingBulletSprites() {
        ins.runOnMainSync {
            val e=GameEngine(); val view=GameView(ins.targetContext,e); view.layout(0,0,960,540)
            val frame=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            val sheet=Bitmap.createBitmap(1920,2160,Bitmap.Config.ARGB_8888)
            val p=Paint().apply { isFilterBitmap=false }
            var total=0
            for(stage in 0..31) {
                e.run=Run(Job.WARRIOR,stage); e.beginBattle(); e.player.hp=100000.0; e.player.maxHp=100000.0
                e.castNormal(BossCombat.forBoss(e.bossInfo.id).lastIndex,0)
                var ticks=0
                while(e.enemyVolley?.started!=true && ticks++<1600) { e.nextPattern=1e6; e.update(.01) }
                assertTrue(e.bossInfo.id,e.enemyVolley?.started==true)
                assertTrue(e.enemyBullets.isEmpty()); view.draw(Canvas(frame))
                if(stage in listOf(0,15,31)) save(frame,"warning-${e.bossInfo.id}")
                // Step away after the aim locks. A stationary player beside a
                // charging boss can legitimately absorb every shot in a volley.
                e.player.x=540.0; e.player.y=300.0
                val volley=e.enemyVolley!!; val target=volley.firstShot+1.05
                while(e.elapsed<target) { e.nextPattern=1e6; e.update(.01) }
                assertTrue(e.bossInfo.id,e.enemyBullets.isNotEmpty()); total+=e.enemyBullets.size
                view.draw(Canvas(frame))
                val x=view.viewport.screenX(28f+e.player.x.toFloat()).toInt()
                val y=view.viewport.screenY(96f+e.player.y.toFloat()).toInt()
                assertEquals("${e.bossInfo.id}: foot marker remains above bullets",Ink.light,frame.getPixel(x,y))
                val a=e.enemyBullets.map { it.x to it.y }; repeat(8) { e.update(.01) }
                assertNotEquals(a,e.enemyBullets.map { it.x to it.y })
                save(frame,"bullets-${e.bossInfo.id}")
                val xx=(stage%4)*480f; val yy=(stage/4)*270f
                Canvas(sheet).drawBitmap(frame,null,RectF(xx,yy,xx+480,yy+270),p)
            }
            assertTrue(total>500); save(sheet,"all-32-barrages"); frame.recycle(); sheet.recycle(); view.suspend()
        }
    }
    @Test fun touchPauseFreezesBulletsAndCutinAndVictoryRemoveThem() {
        Configurator.getInstance().waitForIdleTimeout=100
        ins.targetContext.getSharedPreferences("bossrush",0).edit().clear().commit()
        rule.launchActivity(Intent()); device.wait(Until.findObject(By.text("Got it")),2000)?.click()
        assertTrue(device.wait(Until.hasObject(By.desc("はじめから  →")),5000))
        read { v ->
            val e=v.engine; e.run=Run(Job.WARRIOR,stage=31,kills=31); e.beginBattle(); e.player.hp=100000.0; e.player.maxHp=100000.0
            e.castNormal(0,0)
            var ticks=0
            while(e.enemyBullets.isEmpty() && ticks++<1600) { e.nextPattern=1e6; e.update(.01) }
            assertTrue(e.enemyBullets.isNotEmpty())
            repeat(35) { e.update(.01) }
        }
        tap("II"); assertEquals(Screen.PAUSED,read { it.engine.screen })
        val before=read { it.engine.enemyBullets.map { b -> b.copy() } }
        assertTrue(before.isNotEmpty()); SystemClock.sleep(400)
        assertEquals(before,read { it.engine.enemyBullets })
        tap("戦闘に戻る"); assertEquals(Screen.BATTLE,read { it.engine.screen })
        assertNotEquals(before,read { it.engine.enemyBullets })
        ins.uiAutomation.takeScreenshot().let { save(it,"bullets-device");it.recycle() }
        read { it.engine.damageBoss(it.engine.boss.maxHp) }
        assertEquals(Screen.CUTIN,read { it.engine.screen }); assertTrue(read { it.engine.enemyBullets.isEmpty() }); assertNull(read { it.engine.enemyVolley })
        device.click(device.displayWidth/2,device.displayHeight/2); SystemClock.sleep(150)
        read { it.engine.damageBoss(it.engine.boss.maxHp) }
        assertEquals(Screen.DEFEAT,read { it.engine.screen }); assertTrue(read { it.engine.enemyBullets.isEmpty() })
    }
}
