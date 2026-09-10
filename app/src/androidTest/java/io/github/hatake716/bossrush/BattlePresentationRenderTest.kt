package io.github.hatake716.bossrush

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class BattlePresentationRenderTest {
    @Test fun everyBossShattersInItsOwnColorsAndShakeSettlesBeforeReward() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
        val ins=InstrumentationRegistry.getInstrumentation()
        val ctx=ins.targetContext
        val dir=File(ctx.getExternalFilesDir(null),"presentation").also { it.mkdirs() }
        ins.runOnMainSync {
            val e=GameEngine(); e.newRun(); e.beginBattle()
            val view=GameView(ctx,e); view.layout(0,0,960,540)
            val frame=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            val sheet=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
            val sheetCanvas=Canvas(sheet)
            val fx=BossDefeatEffects()
            assertTrue(fx.shake(.14).let { it.first!=0f || it.second!=0f })
            assertEquals(0f to 0f,fx.shake(1.8))
            val missingColors=mutableListOf<String>()
            for(stage in 0..31) {
                e.run!!.stage=stage; e.beginBattle(); e.boss.y=180.0; e.victory()
                e.screenAge=.38; view.draw(Canvas(frame))
                assertTrue(view.controllerButtons().isEmpty())
                val color=UltimateColors.forBoss(e.bossInfo.id).energy
                val expected=FloatArray(3); Color.colorToHSV(color,expected)
                val actual=FloatArray(3)
                var pixels=0
                // Sparks fade into the background, so compare visible hue instead of opaque RGB.
                for(y in 96..430) for(x in 28..628) {
                    Color.colorToHSV(frame.getPixel(x,y),actual)
                    val distance=abs(expected[0]-actual[0])
                    if(min(distance,360-distance)<12 && actual[1]>expected[1]*.65f && actual[2]>.6f) pixels++
                }
                File(dir,"defeat-${e.bossInfo.id}.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG,100,it) }
                if(pixels<=20) missingColors.add("${e.bossInfo.id}: $pixels")
                if(stage==30) {
                    for((i,age) in listOf(.0,.38,.75,1.35).withIndex()) {
                        e.screenAge=age; view.draw(Canvas(frame))
                        sheetCanvas.drawBitmap(frame,null,RectF((i%2)*960f,(i/2)*540f,(i%2+1)*960f,(i/2+1)*540f),null)
                    }
                }
            }
            File(dir,"boss-defeat-sequence.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG,100,it) }
            frame.recycle(); sheet.recycle(); view.suspend()
            assertTrue("Visible boss-colored particles: $missingColors",missingColors.isEmpty())
        }
    }
}
