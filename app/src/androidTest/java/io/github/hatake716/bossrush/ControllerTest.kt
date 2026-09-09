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
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Software gamepad events on the emulator; no physical controller certification. */
@RunWith(AndroidJUnit4::class)
class ControllerTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)
    private val view get()=rule.activity.gameView
    @Before fun launch() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
        instrumentation.targetContext.getSharedPreferences("bossrush",0).edit().clear().commit()
        rule.launchActivity(Intent())
        assertNotNull(device.wait(Until.findObject(By.desc("はじめから  →")),5000))
    }
    private fun <T> read(block: (GameView)->T): T {
        var result: T?=null
        instrumentation.runOnMainSync { result=block(view) }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun event(key: Int,action: Int,repeat: Int=0) {
        val time=SystemClock.uptimeMillis()
        assertTrue(instrumentation.uiAutomation.injectInputEvent(KeyEvent(time,time,action,key,repeat,0,-1,0,0,InputDevice.SOURCE_GAMEPAD),true))
        SystemClock.sleep(70)
    }
    private fun press(key: Int) { event(key,KeyEvent.ACTION_DOWN); event(key,KeyEvent.ACTION_UP); SystemClock.sleep(90) }
    private fun motion(x: Float=0f,y: Float=0f,hx: Float=0f,hy: Float=0f,trigger: Float=0f) {
        val properties=arrayOf(MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_UNKNOWN })
        val coordinates=arrayOf(MotionEvent.PointerCoords().apply {
            setAxisValue(MotionEvent.AXIS_X,x); setAxisValue(MotionEvent.AXIS_Y,y)
            setAxisValue(MotionEvent.AXIS_HAT_X,hx); setAxisValue(MotionEvent.AXIS_HAT_Y,hy)
            setAxisValue(MotionEvent.AXIS_LTRIGGER,trigger)
        })
        val time=SystemClock.uptimeMillis()
        val event=MotionEvent.obtain(time,time,MotionEvent.ACTION_MOVE,1,properties,coordinates,0,0,1f,1f,-1,0,InputDevice.SOURCE_JOYSTICK,0)
        assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)); event.recycle(); SystemClock.sleep(100)
    }
    private fun screen(expected: Screen) {
        val deadline=SystemClock.uptimeMillis()+5000
        while(read { it.engine.screen }!=expected && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(50)
        assertEquals(expected,read { it.engine.screen }); SystemClock.sleep(150)
    }
    private fun screenshot(name: String) {
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").also { it.mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
    }
    private fun fixture(job: Job=Job.WARRIOR) {
        read { v ->
            v.engine.run=Run(job); v.engine.beginBattle()
            v.engine.player.x=300.0; v.engine.player.y=160.0; v.engine.player.hp=v.engine.player.maxHp/2
            v.engine.boss.hp=100000.0; v.engine.boss.maxHp=100000.0
            v.engine.nextPattern=1e6; v.engine.invulnerability=1e6; v.controller.reset()
        }
        SystemClock.sleep(180)
    }
    @Test fun gamepadStartsAdventureMovesAndPausesWithoutTouch() {
        press(KeyEvent.KEYCODE_BUTTON_A); screen(Screen.JOBS)
        repeat(3) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertEquals("盗賊を選択",read { it.controller.focusLabel })
        screenshot("controller-jobs")
        press(KeyEvent.KEYCODE_BUTTON_A); assertEquals(Job.THIEF,read { it.engine.selectedJob })
        press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_BUTTON_A); screen(Screen.INTRO)
        press(KeyEvent.KEYCODE_BUTTON_A); screen(Screen.BATTLE)
        val x=read { it.engine.player.x }
        motion(x=.8f); SystemClock.sleep(250); motion()
        assertTrue(read { it.engine.player.x }>x+15)
        assertEquals(.0,read { it.engine.moveX },.0)
        val y=read { it.engine.player.y }; motion(hy=-1f); SystemClock.sleep(180); motion()
        assertTrue(read { it.engine.player.y }<y-10)
        motion(hx=1f,hy=-1f)
        assertEquals(1.0,read { it.engine.moveX },.0); assertEquals(-1.0,read { it.engine.moveY },.0)
        motion()
        screenshot("controller-battle")
        press(KeyEvent.KEYCODE_BUTTON_START); screen(Screen.PAUSED)
        val time=read { it.engine.elapsed }; SystemClock.sleep(150)
        assertEquals(time,read { it.engine.elapsed },.0)
        press(KeyEvent.KEYCODE_BUTTON_B); screen(Screen.BATTLE)
        motion(x=-1f)
        read { it.controller.onInputDeviceRemoved(-1) }; screen(Screen.PAUSED)
        assertEquals(.0,read { it.engine.moveX },.0); assertEquals(-1,read { it.engine.heldSkill })
        press(KeyEvent.KEYCODE_BUTTON_START); screen(Screen.BATTLE)
    }
    @Test fun fixtureAllFourJobsUseAllFourButtonsAndReleaseHeldAttacks() {
        val keys=listOf(KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_BUTTON_Y)
        for(job in Job.entries) for(slot in 0..3) {
            fixture(job)
            event(keys[slot],KeyEvent.ACTION_DOWN)
            assertEquals("$job/$slot",slot,read { it.engine.heldSkill })
            assertTrue("$job/$slot skill used",read { it.engine.cooldowns[slot] }>0)
            event(keys[slot],KeyEvent.ACTION_UP)
            assertEquals(-1,read { it.engine.heldSkill })
        }
        fixture()
        event(KeyEvent.KEYCODE_BUTTON_A,KeyEvent.ACTION_DOWN)
        event(KeyEvent.KEYCODE_BUTTON_X,KeyEvent.ACTION_DOWN)
        event(KeyEvent.KEYCODE_BUTTON_A,KeyEvent.ACTION_UP)
        assertEquals(2,read { it.engine.heldSkill })
        motion(x=.7f); assertEquals(2,read { it.engine.heldSkill })
        event(KeyEvent.KEYCODE_BUTTON_X,KeyEvent.ACTION_UP); motion()
        assertEquals(-1,read { it.engine.heldSkill }); assertEquals(.0,read { it.engine.moveX },.0)
        event(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.ACTION_DOWN)
        read { it.onWindowFocusChanged(false) }
        assertEquals(.0,read { it.engine.moveX },.0)
        event(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.ACTION_UP)
    }
    @Test fun fixtureInventoryTriggersUseOneItemAndNativeConfirmationSupportsGamepad() {
        fixture()
        read { it.engine.run!!.inventory.apply { clear(); add(Item.POTION); add(Item.POWER); add(Item.HOURGLASS) } }
        press(KeyEvent.KEYCODE_BUTTON_R1); assertEquals(1,read { it.controller.item })
        press(KeyEvent.KEYCODE_BUTTON_L1); assertEquals(0,read { it.controller.item })
        press(KeyEvent.KEYCODE_BUTTON_L1); assertEquals(2,read { it.controller.item })
        motion(trigger=1f); screen(Screen.PAUSED); assertEquals(2,read { it.engine.selectedItem })
        press(KeyEvent.KEYCODE_BUTTON_B); screen(Screen.BATTLE)
        motion(trigger=1f); assertEquals(Screen.BATTLE,read { it.engine.screen }) // held trigger cannot reopen
        motion(); press(KeyEvent.KEYCODE_BUTTON_L2); screen(Screen.PAUSED)
        event(KeyEvent.KEYCODE_BUTTON_A,KeyEvent.ACTION_DOWN)
        event(KeyEvent.KEYCODE_BUTTON_A,KeyEvent.ACTION_DOWN,1)
        event(KeyEvent.KEYCODE_BUTTON_A,KeyEvent.ACTION_UP); screen(Screen.BATTLE)
        assertEquals(listOf(Item.POTION,Item.POWER),read { it.engine.run!!.inventory.toList() })
        read { it.engine.run!!.inventory.clear() }
        press(KeyEvent.KEYCODE_BUTTON_R1); press(KeyEvent.KEYCODE_BUTTON_L2)
        assertEquals(Screen.BATTLE,read { it.engine.screen })
        // Prepare a shop to check native discard dialogs and duplicate inventory labels.
        read { it.engine.run!!.gold=200; it.engine.run!!.inventory.apply { add(Item.POTION); add(Item.POTION) }; it.engine.changeScreen(Screen.SHOP) }
        SystemClock.sleep(180)
        press(KeyEvent.KEYCODE_DPAD_DOWN) // first product -> first inventory slot
        press(KeyEvent.KEYCODE_DPAD_RIGHT) // duplicate title in the second slot
        assertEquals("薬草のしずく",read { it.controller.focusLabel })
        val left=read { it.controller.focused()!!.rect.left }
        assertTrue(left>200)
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertNotNull(device.wait(Until.findObject(By.text("手放す")),2000))
        press(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(2,read { it.engine.run!!.inventory.size })
        press(KeyEvent.KEYCODE_BUTTON_A)
        press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(1,read { it.engine.run!!.inventory.size })
    }
    @Test fun stickNavigatesMenusAndDisabledRewardsCannotBeConfirmed() {
        press(KeyEvent.KEYCODE_BUTTON_A); screen(Screen.JOBS)
        motion(x=1f); motion(); assertEquals("魔法使いを選択",read { it.controller.focusLabel })
        press(KeyEvent.KEYCODE_BUTTON_B); screen(Screen.TITLE)
        press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_BUTTON_A); screen(Screen.HELP)
        press(KeyEvent.KEYCODE_BUTTON_A); screenshot("controller-help")
        // Prepare a victory; choosing growth remains required before advancing.
        fixture(); read { it.engine.victory() }; screen(Screen.REWARD)
        assertFalse(read { v -> v.controllerButtons().first { it.label=="旅の商人へ  →" }.enabled })
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(0,read { it.engine.pendingUpgrade }); assertEquals(1,read { it.engine.levels[0] })
        assertTrue(read { v -> v.controllerButtons().first { it.label=="旅の商人へ  →" }.enabled })
        press(KeyEvent.KEYCODE_BUTTON_A) // Selecting the same card again must never confirm growth.
        assertEquals(Screen.REWARD,read { it.engine.screen }); assertEquals(1,read { it.engine.levels[0] })
        press(KeyEvent.KEYCODE_BUTTON_B); assertEquals(-1,read { it.engine.pendingUpgrade })
        assertFalse(read { it.engine.canFinishReward })
        press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(1,read { it.engine.pendingUpgrade })
        repeat(2) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }; press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("旅の商人へ  →",read { it.controller.focusLabel })
        press(KeyEvent.KEYCODE_BUTTON_A); screen(Screen.SHOP)
        assertEquals(listOf(1,2,1,1),read { it.engine.levels.toList() })
        val gold=read { it.engine.run!!.gold }
        press(KeyEvent.KEYCODE_BUTTON_A)
        assertTrue(read { it.engine.run!!.gold }<gold)
    }
}
