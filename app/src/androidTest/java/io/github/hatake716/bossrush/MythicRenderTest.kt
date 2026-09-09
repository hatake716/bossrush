package io.github.hatake716.bossrush

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MythicRenderTest {
    @Test fun allGodsHaveLimitedPalettePixelMuralsAndVisibleDoubleCastWarnings() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val dir=File(context.getExternalFilesDir(null),"screenshots").also { it.mkdirs() }
        fun save(b: Bitmap,name: String) { File(dir,"$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) } }
        instrumentation.runOnMainSync {
            val murals=MythicCutin(); val silhouettes=mutableSetOf<Int>()
            val e=GameEngine().apply { run=Run(Job.WARRIOR) }
            val view=GameView(context,e); view.layout(0,0,960,540)
            val frame=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            val sheets=List(2) { Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888) }
            val paint=Paint().apply { isFilterBitmap=false; isAntiAlias=false }
            for((i,boss) in Bosses.all.withIndex()) {
                val mural=murals.bitmap(boss.id)
                assertEquals(480,mural.width); assertEquals(132,mural.height)
                val pixels=IntArray(480*132); mural.getPixels(pixels,0,480,0,0,480,132)
                val colors=pixels.toSet()
                assertTrue("${boss.id} must use a limited pixel palette",colors.size in 7..12)
                assertTrue(colors.all { Color.alpha(it)==255 })
                val energy=UltimateColors.forBoss(boss.id).energy; val accent=UltimateColors.forBoss(boss.id).accent
                assertTrue("${boss.id} uses its attack colors",pixels.count { it==energy }>60 && pixels.count { it==accent }>60)
                // Index colors by first occurrence: geometry must differ beyond palette swaps.
                val indices=colors.withIndex().associate { it.value to it.index }
                silhouettes.add(pixels.map { indices.getValue(it) }.hashCode())
                e.run!!.stage=i; e.boss=Actor(300.0,125.0,450.0,900.0)
                e.ultimateUsed=false; e.changeScreen(Screen.CUTIN); e.screenAge=.8
                view.draw(Canvas(frame))
                val x=i%4*480f; val y=(i%16)/4*270f
                Canvas(sheets[i/16]).drawBitmap(frame,null,RectF(x,y,x+480,y+270),paint)
                if(i in listOf(0,12,17,23,30,31)) save(frame,"mythic-cutin-${boss.id}")
                e.changeScreen(Screen.BATTLE); e.boss.hp=225.0; e.ultimateUsed=true
                e.player=Actor(300.0,265.0,150.0,150.0)
                e.hazards.clear(); e.normalCues.clear(); e.cues.clear(); e.impacts.clear()
                e.cast(boss.sequence.first(),true,0)
                e.hazards.forEach { it.time=it.delay*.55 }
                assertTrue(e.hazards.any { it.ultimate } && e.hazards.any { !it.ultimate })
                assertTrue(e.castHint.startsWith("二重詠唱"))
                view.draw(Canvas(frame))
                assertEquals("${boss.id} double cast keeps player readable",Ink.light,frame.getPixel(328,361))
                if(i in listOf(0,12,23,30,31)) save(frame,"double-cast-${boss.id}")
            }
            assertEquals("Every deity has its own relief",32,silhouettes.size)
            sheets.forEachIndexed { i,b -> save(b,"mythic-cutins-${i+1}"); b.recycle() }
            frame.recycle(); view.suspend()
        }
    }
}
