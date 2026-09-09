package io.github.hatake716.bossrush

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.*

/** Fixed encounters advanced through the real engine, for motion and pixel-art visual QA. */
@RunWith(AndroidJUnit4::class)
class BossMovementRenderTest {
    @Test fun chargesLeapsFlanksAndBlinksRenderThroughoutTheirActualMovement() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val dir=File(context.getExternalFilesDir(null),"screenshots").also { it.mkdirs() }
        fun save(b: Bitmap,name: String) { File(dir,"$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) } }
        instrumentation.runOnMainSync {
            val e=GameEngine(); val view=GameView(context,e); view.layout(0,0,960,540)
            val frame=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            val sheet=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
            val paint=Paint().apply { isFilterBitmap=false; isAntiAlias=false }
            for((row,stage) in listOf(2,30,10,29).withIndex()) {
                e.run=Run(Job.WARRIOR,stage); e.beginBattle()
                e.boss=Actor(150.0,135.0,900.0,900.0)
                e.player=Actor(400.0,255.0,150.0,150.0); e.invulnerability=100.0
                val id=e.bossInfo.id; val profile=BossMobility.forBoss(id)
                e.castNormal(profile.slot,0); e.normalCues.clear(); e.nextPattern=1e6
                val move=e.bossMove!!; val duration=move.start+move.travel+.10
                val hashes=mutableSetOf<Int>(); var moved=false
                for(i in 0..59) {
                    val target=duration*i/59
                    while(e.elapsed+1e-7<target) e.update(min(1.0/120,target-e.elapsed))
                    view.draw(Canvas(frame))
                    // The collision marker must remain on top of the moving boss and trails.
                    assertEquals(id,Ink.light,frame.getPixel(view.viewport.screenX(428f).toInt(),view.viewport.screenY(351f).toInt()))
                    val pixels=IntArray(960*540); frame.getPixels(pixels,0,960,0,0,960,540)
                    hashes.add(pixels.contentHashCode())
                    if(hypot(e.boss.x-move.fromX,e.boss.y-move.fromY)>50) moved=true
                    save(frame,"motion-$id-${i.toString().padStart(2,'0')}")
                    val targets=listOf(0,(move.start/duration*59).roundToInt(),((move.start+move.travel*.5)/duration*59).roundToInt(),59)
                    for(column in targets.indices) if(i==targets[column]) {
                        val x=column*480f; val y=row*270f
                        Canvas(sheet).drawBitmap(frame,null,RectF(x,y,x+480,y+270),paint)
                    }
                }
                assertTrue(id,moved); assertTrue(id,hashes.size>20)
                // The final frame includes 0.10 s of recovery, when idle repositioning may resume.
                assertTrue(id,hypot(move.toX-e.boss.x,move.toY-e.boss.y)<10)
            }
            save(sheet,"boss-movement-storyboard"); sheet.recycle(); frame.recycle(); view.suspend()
        }
    }
}
