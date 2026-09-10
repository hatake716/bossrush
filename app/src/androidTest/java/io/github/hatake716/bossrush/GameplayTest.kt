package io.github.hatake716.bossrush

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.*

/** Tests interact through Android input. Fixture tests are explicitly named as such. */
@RunWith(AndroidJUnit4::class)
class GameplayTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)
    @Before fun launch() {
        Configurator.getInstance().waitForIdleTimeout=100
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "These tests are emulator-only" }
        instrumentation.targetContext.getSharedPreferences("bossrush",0).edit().clear().commit()
        rule.launchActivity(Intent())
        device.wait(Until.hasObject(By.desc("はじめから  →")),5000)
        device.findObject(By.text("Got it"))?.click()
        SystemClock.sleep(150); screenshot("title")
    }
    private fun tap(label: String) {
        val node=device.wait(Until.findObject(By.desc(label)),5000) ?: error("Missing button $label; ${read { it.screen }}")
        node.click(); SystemClock.sleep(100)
    }
    private fun <T> read(block: (GameEngine)->T): T {
        var value: T?=null
        instrumentation.runOnMainSync { value=block(rule.activity.gameView.engine) }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun screenshot(name: String) {
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots"); dir.mkdirs()
        instrumentation.uiAutomation.takeScreenshot().let { bmp -> File(dir,"$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }; bmp.recycle() }
    }
    private fun start(job: Job) {
        tap("はじめから  →"); tap("${job.label}を選択"); screenshot("job-${job.name}")
        tap("この職業で出発  →")
        tap("この場面をスキップ"); tap("この場面をスキップ")
        // These fixtures isolate combat/reward behavior. MainStoryUiTest covers the campaign.
        instrumentation.runOnMainSync { rule.activity.gameView.engine.run!!.storyEnabled=false }
        // First decoding of detailed scenery can outlast the menu's frame delay.
        // Capture the presented encounter, rather than the preceding job selection.
        assertNotNull(device.wait(Until.findObject(By.desc("戦闘開始  →")),5000))
        SystemClock.sleep(100); screenshot("intro-${job.name}"); tap("戦闘開始  →")
        assertEquals(Screen.BATTLE,read { it.screen })
    }
    @Test fun fourJobsHaveUsableControlsAndBackgroundPause() {
        for(job in Job.entries) {
            if(job!=Job.WARRIOR) { rule.finishActivity(); launch() }
            start(job)
            if(job==Job.WARRIOR) {
                tap("アイテム1 薬草のしずく"); tap("使う")
                assertEquals(2,read { it.run!!.inventory.size })
                tap("II"); tap("戦闘に戻る")
                assertEquals(-1,read { it.selectedItem })
            }
            val skills=Skills.all.getValue(job)
            for(i in 0..3) {
                val label="技${i+1} ${skills[i].name}"
                val node=device.wait(Until.findObject(By.desc(label)),3000)
                if(node==null) screenshot("missing-${job.name}-skill-$i")
                assertNotNull("Missing $label for $job in ${read { it.screen }}",node)
            }
            val x=read { it.player.x }
            gesture(1.0,.0,if(job==Job.SUMMONER) 0 else 2,700)
            assertTrue(read { it.player.x }>x+25)
            if(job==Job.SUMMONER) assertTrue(read { it.summons.isNotEmpty() })
            screenshot("battle-${job.name}")
            device.pressHome(); SystemClock.sleep(300)
            val before=read { it.elapsed }; SystemClock.sleep(200)
            assertEquals(before,read { it.elapsed },.0)
            instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // Wait for the Android task transition before injecting the first resumed touch.
            SystemClock.sleep(700)
            tap("戦闘に戻る"); assertEquals(Screen.BATTLE,read { it.screen })
        }
    }
    @Test fun warriorWinsThroughTouchThenUpgradesShopsAndResumesCheckpoint() {
        start(Job.WARRIOR)
        val started=SystemClock.uptimeMillis(); var cutinSeen=false
        while(SystemClock.uptimeMillis()-started<100000) {
            val screen=read { it.screen }
            if(screen==Screen.REWARD) break
            assertNotEquals("The touch pilot must survive",Screen.GAMEOVER,screen)
            if(screen==Screen.CUTIN) { if(!cutinSeen) screenshot("cutin"); cutinSeen=true; tap("タップまたはボタンで戦闘へ"); continue }
            data class Target(val dx: Double,val dy: Double,val slot: Int,val hp: Double)
            val target=read { e ->
                val threats=e.hazards.filter { !it.resolved && it.shape!="knock" && it.delay-it.time<2.5 }
                var best=Double.POSITIVE_INFINITY; var tx=e.player.x; var ty=e.player.y
                for(x in 40..560 step 20) for(y in 40..300 step 20) {
                    if(threats.any { it.contains(x.toDouble(),y.toDouble(),18.0) }) continue
                    val dist=hypot(x-e.player.x,y-e.player.y)
                    val range=hypot(x-e.boss.x,y-e.boss.y)
                    val score=dist*.35+abs(range-58)*1.2
                    if(score<best) { best=score; tx=x.toDouble(); ty=y.toDouble() }
                }
                val dx=tx-e.player.x; val dy=ty-e.player.y; val d=hypot(dx,dy).coerceAtLeast(1.0)
                val slot=if(hypot(e.player.x-e.boss.x,e.player.y-e.boss.y)<86) 0 else 2
                Target(if(d<10) .0 else dx/d,if(d<10) .0 else dy/d,slot,e.player.hp)
            }
            gesture(target.dx,target.dy,target.slot,180)
            if(target.hp<75 && read { it.run!!.inventory.isNotEmpty() && it.screen==Screen.BATTLE }) {
                tap("アイテム1 薬草のしずく"); tap("使う")
            }
        }
        assertEquals(Screen.REWARD,read { it.screen }); assertTrue(cutinSeen)
        assertEquals(1,read { it.run!!.kills }); screenshot("victory")
        tap("剣を選択"); assertEquals(1,read { it.levels[0] }); tap("旅の商人へ  →")
        assertEquals(2,read { it.levels[0] })
        val beforeGold=read { it.run!!.gold }; val stock=read { it.run!!.inventory.size }
        tap("35 G  購入")
        assertEquals(beforeGold-35,read { it.run!!.gold }); assertEquals(stock+1,read { it.run!!.inventory.size })
        screenshot("shop")
        rule.finishActivity(); rule.launchActivity(Intent()); tap("つづきから")
        assertEquals(Screen.SHOP,read { it.screen }); assertEquals(2,read { it.levels[0] }); assertEquals(1,read { it.run!!.stage })
        assertEquals(beforeGold-35,read { it.run!!.gold }); tap("次のボスへ  →"); screenshot("second-boss")
    }
    @Test fun fixtureUpgradeSelectionCanBeCancelledChangedAndSavedForEveryJob() {
        for(job in Job.entries) {
            if(job!=Job.WARRIOR) { rule.finishActivity(); launch() }
            start(job)
            instrumentation.runOnMainSync { rule.activity.gameView.engine.victory() }
            assertNotNull(device.wait(Until.findObject(By.desc("${Skills.all.getValue(job)[0].name}を選択")),5000))
            val names=Skills.all.getValue(job).map { "${it.name}を選択" }
            tap(names[0]); assertEquals(0,read { it.pendingUpgrade })
            tap(names[1]); assertEquals(1,read { it.pendingUpgrade })
            assertEquals(listOf(1,1,1,1),read { it.levels.toList() })
            screenshot("growth-selected-${job.name}")
            tap("技の選択をキャンセル"); assertEquals(-1,read { it.pendingUpgrade })
            assertFalse(read { it.canFinishReward })
            tap(names[2]); device.pressBack(); SystemClock.sleep(150)
            assertEquals(Screen.REWARD,read { it.screen }); assertEquals(-1,read { it.pendingUpgrade })
            screenshot("growth-cancelled-${job.name}")
            if(job==Job.THIEF) tap("三影の鏡")
            tap(names[3]); tap("旅の商人へ  →")
            assertEquals(Screen.SHOP,read { it.screen }); assertEquals(listOf(1,1,1,2),read { it.levels.toList() })
            rule.finishActivity(); rule.launchActivity(Intent()); tap("つづきから")
            assertEquals(Screen.SHOP,read { it.screen }); assertEquals(1,read { it.run!!.stage })
            assertEquals(listOf(1,1,1,2),read { it.levels.toList() })
        }
    }
    @Test fun fixtureThiefRewardReplacementAndColorEndingRenderCorrectly() {
        start(Job.THIEF)
        // Prepare an end-of-battle state to inspect otherwise lengthy branches, not a claimed full clear.
        instrumentation.runOnMainSync {
            val e=rule.activity.gameView.engine
            e.run!!.inventory.clear(); repeat(5) { e.run!!.inventory.add(Item.POTION) }
            e.elapsed=30.0; e.victory()
        }
        SystemClock.sleep(150); tap("ナイフを選択"); tap("時戻しの砂時計")
        tap("薬草のしずく"); assertTrue(read { it.run!!.inventory.contains(Item.HOURGLASS) })
        screenshot("thief-reward"); tap("旅の商人へ  →")
        instrumentation.runOnMainSync {
            val e=rule.activity.gameView.engine
            e.run!!.stage=31; e.run!!.kills=31; e.run!!.inventory.clear(); e.beginBattle(); e.elapsed=30.0; e.victory()
        }
        SystemClock.sleep(150); tap("ナイフを選択"); tap("三影の鏡"); tap("夜明けへ  →")
        assertEquals(Screen.ENDING,read { it.screen }); screenshot("ending-fixture")
        val bitmap=instrumentation.uiAutomation.takeScreenshot(); var colorful=0
        for(x in 0 until bitmap.width step 10) for(y in 0 until bitmap.height step 10) {
            val color=bitmap.getPixel(x,y)
            if(android.graphics.Color.blue(color)>android.graphics.Color.red(color)+15) colorful++
        }
        bitmap.recycle(); assertTrue("Ending must restore more than green",colorful>50)
        tap("タイトルへ"); assertTrue(rule.activity.gameView.bestScore>0); assertFalse(rule.activity.gameView.hasSave)
    }
    @Test fun codexContainsAllThirtyTwoAndShowsFinalGod() {
        tap("神話図鑑"); repeat(3) { tap("→") }; tap("図鑑 オーディン")
        assertEquals(31,read { it.selectedBoss }); screenshot("codex-odin")
    }
    private fun gesture(dx: Double,dy: Double,skill: Int,duration: Long) {
        lateinit var viewport: GameViewport
        val location=IntArray(2)
        instrumentation.runOnMainSync {
            viewport=rule.activity.gameView.viewport; rule.activity.gameView.getLocationOnScreen(location)
        }
        val props=Array(2) { i -> MotionEvent.PointerProperties().apply { id=i; toolType=MotionEvent.TOOL_TYPE_FINGER } }
        fun point(x: Float,y: Float)=MotionEvent.PointerCoords().apply {
            this.x=location[0]+viewport.screenX(x); this.y=location[1]+viewport.screenY(y); pressure=1f; size=1f
        }
        val coords=arrayOf(point(103f,374f),point(720f+viewport.extra+(skill%2)*145,345f+(skill/2)*88))
        val down=SystemClock.uptimeMillis()
        fun send(action: Int,count: Int) {
            val ev=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
            instrumentation.uiAutomation.injectInputEvent(ev,true); ev.recycle()
        }
        send(MotionEvent.ACTION_DOWN,1)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),2)
        coords[0]=point((103+dx*36).toFloat(),(374+dy*36).toFloat())
        send(MotionEvent.ACTION_MOVE,2); SystemClock.sleep(duration)
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),2)
        send(MotionEvent.ACTION_UP,1)
    }
}
