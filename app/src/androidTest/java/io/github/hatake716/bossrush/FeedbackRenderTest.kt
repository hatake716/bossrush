package io.github.hatake716.bossrush

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.*

/** Deterministic visual fixtures; this is effect coverage, not a claim of playing through 32 bosses. */
@RunWith(AndroidJUnit4::class)
class FeedbackRenderTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    @Test fun allPatternsAndElementsRenderWhileSafeAreasAndThePlayerRemainReadable() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
        Configurator.getInstance().waitForIdleTimeout=100
        val device=UiDevice.getInstance(instrumentation)
        rule.launchActivity(Intent()); SystemClock.sleep(400)
        device.findObject(By.text("Got it"))?.click(); SystemClock.sleep(200)
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots"); dir.mkdirs()
        instrumentation.runOnMainSync {
            val view=rule.activity.gameView; view.suspend()
            val e=view.engine; e.selectedJob=Job.WARRIOR; e.newRun(); e.beginBattle()
            val scale=view.viewport.scale
            val ox=view.viewport.x+view.viewport.extra/2*scale; val oy=view.viewport.y
            fun render(): Bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            fun countDiff(a: Bitmap,b: Bitmap): Int {
                var count=0
                for(x in 40..610 step 3) for(y in 104..422 step 3) {
                    val px=(ox+x*scale).toInt(); val py=(oy+y*scale).toInt()
                    if(a.getPixel(px,py)!=b.getPixel(px,py)) count++
                }
                return count
            }
            fun save(bmp: Bitmap,name: String) { File(dir,"$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) } }
            for(pattern in Pattern.entries) {
                e.hazards.clear(); e.impacts.clear(); e.run!!.stage=30; e.cast(pattern,true,0)
                e.hazards.forEach { it.time=it.delay*.72 }
                val warning=render()
                val damageBefore=e.damageTaken
                e.hazards.forEach { it.resolved=true; e.impacts.add(BattleImpact(it.copy(),.10)) }
                val impact=render()
                assertTrue("$pattern must visibly erupt",countDiff(warning,impact)>80)
                assertEquals(damageBefore,e.damageTaken,.0) // Drawing is never damage.
                val px=(ox+(28+e.player.x)*scale).toInt(); val py=(oy+(96+e.player.y)*scale).toInt()
                assertEquals("Player foot marker is drawn over every effect",Ink.light,impact.getPixel(px,py))
                if(pattern==Pattern.ECLIPSE || pattern==Pattern.TOWERS) {
                    val h=e.hazards.single()
                    val safeX=(ox+(28+h.x)*scale).toInt(); val safeY=(oy+(96+h.y)*scale).toInt()
                    e.impacts.clear(); val baseline=render()
                    assertEquals("The safe center is not painted by the explosion",baseline.getPixel(safeX,safeY),impact.getPixel(safeX,safeY)); baseline.recycle()
                }
                save(warning,"vfx-${pattern.name.lowercase()}-warning"); save(impact,"vfx-${pattern.name.lowercase()}-impact")
                warning.recycle(); impact.recycle()
            }
            for(stage in listOf(0,3,10,12,17,23,29,30,31)) {
                e.run!!.stage=stage; e.hazards.clear(); e.impacts.clear()
                e.hazards.add(Hazard("circle",300.0,166.0,125.0,resolved=true,multiplier=1.65))
                e.impacts.add(BattleImpact(e.hazards.single(),.12))
                val bitmap=render(); save(bitmap,"vfx-theme-${e.bossInfo.id}"); bitmap.recycle()
            }
        }
    }
}
