package io.github.hatake716.bossrush

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.math.pow

/** Native rendering fixtures cover every scene; they do not simulate 32 victories. */
@RunWith(AndroidJUnit4::class)
class BackgroundArtTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val dir get()=File(context.getExternalFilesDir(null),"screenshots").also { it.mkdirs() }
    @Before fun emulatorOnly() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
    }
    private fun save(bitmap: Bitmap,name: String) {
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val value=channel/255.0
            return if(value<=.04045) value/12.92 else ((value+.055)/1.055).pow(2.4)
        }
        return .2126*linear(Color.red(color))+.7152*linear(Color.green(color))+.0722*linear(Color.blue(color))
    }

    @Test fun allScenesAreOpaqueDistinctAndLeaveContrastForTelegraphs() {
        val art=BackgroundArt(context.assets)
        val keys=Bosses.all.map { "battle_${it.id}" }+"worldtree"
        assertEquals(keys.toSet(),context.assets.list("backgrounds")!!.map { it.removeSuffix(".png") }.toSet())
        val hashes=mutableSetOf<String>()
        for(key in keys) {
            val digest=context.assets.open("backgrounds/$key.png").use {
                val hash=MessageDigest.getInstance("SHA-256"); val buffer=ByteArray(8192)
                while(true) { val size=it.read(buffer); if(size<0) break; hash.update(buffer,0,size) }
                hash.digest().joinToString("") { byte -> "%02x".format(byte) }
            }
            assertTrue("$key has its own image",hashes.add(digest))
            val bitmap=art.bitmap(key)
            assertTrue("$key landscape resolution",bitmap.width in 960..2048 && bitmap.height in 540..2048)
            for(y in 0 until bitmap.height step 13) for(x in 0 until bitmap.width step 13)
                assertEquals("$key has no transparent holes",255,Color.alpha(bitmap.getPixel(x,y)))
            if(key=="worldtree") continue
            val target=Bitmap.createBitmap(600,334,Bitmap.Config.ARGB_8888)
            art.battle(Canvas(target),key.removePrefix("battle_"))
            val shades=mutableSetOf<Int>()
            for(y in 0 until 334 step 3) for(x in 0 until 600 step 3) {
                val color=target.getPixel(x,y); shades.add(color)
                val contrast=(luminance(Ink.light)+.05)/(luminance(color)+.05)
                assertTrue("$key scenery competes with the pale warning outline at $x,$y: $contrast",contrast>=2.8)
                assertEquals("$key fills the arena",255,Color.alpha(color))
            }
            assertTrue("$key retains detailed tonal texture",shades.size>80)
            target.recycle()
        }
    }

    @Test fun everyEncounterUsesItsSceneryAndEndingRestoresTheSameWorldInColor() {
        instrumentation.runOnMainSync {
            val e=GameEngine(); e.selectedJob=Job.WARRIOR; e.newRun()
            val view=GameView(context,e); view.layout(0,0,960,540)
            val backgrounds=BackgroundArt(context.assets)
            val effects=BattleEffects()
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Ink.light; textSize=19f }
            val thumbnailPaint=Paint().apply { isFilterBitmap=false }
            val selected=listOf(0,10,12,17,23,30)
            val overview=Bitmap.createBitmap(1248,1170,Bitmap.Config.ARGB_8888)
            val overviewCanvas=Canvas(overview); overviewCanvas.drawColor(Ink.dark)
            for(page in 0..3) {
                val contact=Bitmap.createBitmap(1248,1560,Bitmap.Config.ARGB_8888)
                val c=Canvas(contact); c.drawColor(Ink.dark)
                for(slot in 0..7) {
                    val stage=page*8+slot; e.run!!.stage=stage; e.beginBattle()
                    val b=e.bossInfo
                    val scene=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(scene))
                    val ground=Bitmap.createBitmap(600,334,Bitmap.Config.ARGB_8888)
                    backgrounds.battle(Canvas(ground),b.id)
                    // Points away from the UI, numbered ring, boss and hero prove routing
                    // by encounter ID, rather than only checking that assets exist.
                    for((x,y) in listOf(175 to 205,410 to 220,450 to 265))
                        assertEquals("${b.id} is used by GameView",ground.getPixel(x,y),scene.getPixel(28+x,96+y))
                    val x=12f+slot%2*624; val y=12f+slot/2*390
                    c.drawBitmap(scene,Rect(28,96,628,430),RectF(x,y,x+600,y+334),thumbnailPaint)
                    c.drawText("${stage+1}. ${b.name}",x+8,y+364,paint)
                    val index=selected.indexOf(stage)
                    if(index>=0) {
                        val xx=12f+index%2*624; val yy=12f+index/2*390
                        overviewCanvas.drawBitmap(scene,Rect(28,96,628,430),RectF(xx,yy,xx+600,yy+334),thumbnailPaint)
                        overviewCanvas.drawText("${stage+1}. ${b.name}",xx+8,yy+364,paint)
                    }
                    save(scene,"scene-${b.id}")
                    // Attack light may cover the outside; the safe interior must keep
                    // this boss's background, with no generic repaint of the floor.
                    val hazard=Hazard("safe",300.0,230.0,62.0,resolved=true,multiplier=1.65,ultimate=true)
                    val before=ground.getPixel(300,230)
                    effects.impact(Canvas(ground),BattleImpact(hazard,.12),b.id)
                    assertEquals("${b.id} safe center remains visible",before,ground.getPixel(300,230))
                    scene.recycle(); ground.recycle()
                }
                save(contact,"backgrounds-${page+1}"); contact.recycle()
            }
            save(overview,"backgrounds-overview"); overview.recycle()
            val title=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            val ending=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            backgrounds.landscape(Canvas(title)); backgrounds.landscape(Canvas(ending),true)
            var restored=0; var greenTitle=0
            for(y in 70..530 step 5) for(x in 590..950 step 5) {
                val color=ending.getPixel(x,y); val monochrome=title.getPixel(x,y)
                if(Color.red(color)>Color.green(color)+12 || Color.blue(color)>Color.green(color)+12) restored++
                if(Color.green(monochrome)>=Color.red(monochrome) && Color.green(monochrome)>=Color.blue(monochrome)) greenTitle++
            }
            assertTrue("Dawn reveals warm and blue colors",restored>100)
            assertEquals("The title keeps its green palette",93*73,greenTitle)
            title.recycle(); ending.recycle(); view.suspend()
        }
    }
}
