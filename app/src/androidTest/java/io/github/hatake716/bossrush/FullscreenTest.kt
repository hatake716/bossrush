package io.github.hatake716.bossrush

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.*
import android.os.SystemClock
import android.view.View
import android.view.Surface
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FullscreenTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val dir get()=File(context.getExternalFilesDir(null),"screenshots").also { it.mkdirs() }
    @Before fun emulatorOnly() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
    }
    private fun save(bitmap: Bitmap,name: String) {
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun allScreensFitNarrowWideTabletAndEitherCutoutWithoutSideBars() {
        instrumentation.runOnMainSync {
            val configurations=listOf(
                intArrayOf(1920,1080,0,0),intArrayOf(2400,1080,0,0),
                intArrayOf(2424,1080,96,0),intArrayOf(2424,1080,0,96),intArrayOf(1600,1200,0,0))
            for((width,height,left,right) in configurations) {
                val e=GameEngine(); e.newRun(); e.run!!.stage=30; e.beginBattle()
                val view=GameView(context,e); view.layout(0,0,width,height); view.safeArea(left,0,right,0)
                val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                for(screen in Screen.entries) {
                    e.changeScreen(screen); view.draw(Canvas(bitmap))
                    val provider=view.accessibilityNodeProvider
                    val root=provider.createAccessibilityNodeInfo(View.NO_ID)!!
                    for(index in 0 until root.childCount) {
                        val node=provider.createAccessibilityNodeInfo(index)!!; val bounds=Rect(); node.getBoundsInScreen(bounds)
                        assertTrue("$width/$left/$right $screen ${node.contentDescription}: $bounds",
                            bounds.left>=left && bounds.right<=width-right && bounds.top>=0 && bounds.bottom<=height && !bounds.isEmpty)
                        if(node.contentDescription.toString().startsWith("♪"))
                            assertTrue("Header stays at the top on tablets too",bounds.top<40*view.viewport.scale)
                    }
                    if(screen==Screen.TITLE || screen==Screen.BATTLE || screen==Screen.ENDING) {
                        for(x in listOf(1,width-2)) {
                            val colors=(height/4 until height-15 step 7).map { bitmap.getPixel(x,it) }.toSet()
                            assertTrue("$screen fills side $x on $width/$height",colors.size>8)
                        }
                        save(bitmap,"fullscreen-${width}x$height-$left-$right-${screen.name.lowercase()}")
                    }
                    if(screen==Screen.BATTLE) {
                        val v=view.viewport
                        val px=v.screenX(28+v.extra/2+e.player.x.toFloat()).toInt()
                        val py=v.screenY(96+e.player.y.toFloat()).toInt()
                        assertEquals("Uniform combat marker stays aligned",Ink.light,bitmap.getPixel(px,py))
                    }
                }
                bitmap.recycle(); view.suspend()
            }
        }
    }

    @Test fun bothLandscapeOrientationsKeepEdgeButtonsTouchableAndOutsideTheCameraHole() {
        Configurator.getInstance().waitForIdleTimeout=100
        context.getSharedPreferences("bossrush",0).edit().clear().commit()
        rule.launchActivity(Intent())
        val device=UiDevice.getInstance(instrumentation)
        device.findObject(By.text("Got it"))?.click()
        fun tap(label: String) {
            val node=device.wait(Until.findObject(By.desc(label)),5000) ?: error("Missing $label")
            node.click(); SystemClock.sleep(160)
        }
        try {
            for(direction in listOf("left","right")) {
                // UiDevice's user-rotation lock alone does not override sensorLandscape.
                // Ask the test Activity for each actual landscape orientation as well.
                instrumentation.runOnMainSync {
                    rule.activity.requestedOrientation=if(direction=="left") ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
                if(direction=="left") device.setOrientationLeft() else device.setOrientationRight()
                val rotation=if(direction=="left") Surface.ROTATION_90 else Surface.ROTATION_270
                val deadline=SystemClock.uptimeMillis()+5000
                while(device.displayRotation!=rotation && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(100)
                assertEquals("The actual display must rotate $direction",rotation,device.displayRotation)
                SystemClock.sleep(650)
                val mute=device.wait(Until.findObject(By.desc("♪ ON")),5000) ?: error("Missing mute")
                val bounds=mute.visibleBounds
                instrumentation.runOnMainSync {
                    val view=rule.activity.gameView
                    val safe=ViewCompat.getRootWindowInsets(view)!!.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
                    assertTrue(bounds.left>=safe.left)
                    assertTrue(bounds.right<=view.width-safe.right)
                    if(safe.left+safe.right>0) {
                        assertTrue("The camera hole follows $direction",if(direction=="left") safe.left>0 && safe.right==0
                            else safe.right>0 && safe.left==0)
                    }
                    assertEquals(view.width.toFloat(),view.viewport.screenX(view.viewport.fullLeft+view.viewport.fullWidth),.01f)
                }
                tap("♪ ON"); tap("♪ OFF")
                tap("遊び方"); tap("戻る")
                tap("はじめから  →"); tap("盗賊を選択"); tap("この職業で出発  →")
                tap("戦闘開始  →"); tap("II")
                val shot=instrumentation.uiAutomation.takeScreenshot(); save(shot,"fullscreen-device-$direction"); shot.recycle()
                tap("タイトルへ")
                // A second direction should start a fresh run without a confirmation dialog.
                instrumentation.runOnMainSync { rule.activity.gameView.hasSave=false }
            }
        } finally {
            instrumentation.runOnMainSync { rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
            device.unfreezeRotation()
        }
    }
}
