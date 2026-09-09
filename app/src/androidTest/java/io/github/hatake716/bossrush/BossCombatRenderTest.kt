package io.github.hatake716.bossrush

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Authored battle states for visual QA, not a recording of a 32-boss human playthrough. */
@RunWith(AndroidJUnit4::class)
class BossCombatRenderTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun emulatorOnly() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
    }
    private fun save(bitmap: Bitmap,name: String) {
        val dir=File(context.getExternalFilesDir(null),"screenshots").also { it.mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun colored(bitmap: Bitmap): Int {
        var count=0
        for(x in 0 until bitmap.width step 2) for(y in 0 until bitmap.height step 2) {
            val p=bitmap.getPixel(x,y); val r=Color.red(p); val g=Color.green(p); val b=Color.blue(p)
            val high=maxOf(r,g,b); val low=minOf(r,g,b)
            if(high>125 && high-low>65 && (r>g+16 || b>r+16)) count++
        }
        return count
    }
    @Test fun allThirtyTwoUltimatesHaveSaturatedDistinctEnergyAndProtectSafeCenters() {
        emulatorOnly()
        instrumentation.runOnMainSync {
            val effects=BattleEffects(); val hashes=mutableSetOf<Int>()
            for(boss in Bosses.all) {
                val bitmap=Bitmap.createBitmap(600,334,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(bitmap); canvas.drawColor(Ink.dark)
                val normal=Hazard("circle",300.0,170.0,145.0,resolved=true)
                effects.impact(canvas,BattleImpact(normal,.12),boss.id)
                assertEquals("${boss.id} normal remains Game Boy color",0,colored(bitmap))
                canvas.drawColor(Ink.dark)
                effects.impact(canvas,BattleImpact(normal.copy(ultimate=true,multiplier=1.65),.12),boss.id)
                assertTrue("${boss.id} needs vivid energy",colored(bitmap)>90)
                val pixels=IntArray(600*334); bitmap.getPixels(pixels,0,600,0,0,600,334); hashes.add(pixels.contentHashCode())
                canvas.drawColor(Ink.dark)
                val safe=normal.copy(shape="safe",a=67.0,ultimate=true)
                effects.impact(canvas,BattleImpact(safe,.12),boss.id)
                assertEquals("${boss.id} safe interior",Ink.dark,bitmap.getPixel(300,170))
                bitmap.recycle()
            }
            assertEquals(32,hashes.size)
        }
    }
    @Test fun everyNormalAttackAndColorCutinRendersWithReadablePlayerAndUpdatedHints() {
        emulatorOnly()
        instrumentation.runOnMainSync {
            val e=GameEngine().apply { run=Run(Job.WARRIOR); screen=Screen.BATTLE }
            val view=GameView(context,e); view.layout(0,0,960,540)
            val frame=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            val gallery=List(4) { Bitmap.createBitmap(1680,1056,Bitmap.Config.ARGB_8888).apply { eraseColor(Ink.dark) } }
            val paint=Paint().apply { isAntiAlias=false; color=Ink.light; textSize=18f }
            for((stage,boss) in Bosses.all.withIndex()) {
                e.run!!.stage=stage; e.changeScreen(Screen.BATTLE)
                e.player=Actor(300.0,265.0,150.0,150.0); e.boss=Actor(300.0,125.0,900.0,900.0)
                val attacks=BossCombat.forBoss(boss.id)
                for(slot in attacks.indices) {
                    e.hazards.clear(); e.impacts.clear(); e.normalCues.clear(); e.cues.clear(); e.cutinTime=0.0
                    e.castNormal(slot,0); e.hazards.forEach { it.time=it.delay*.60 }
                    view.draw(Canvas(frame))
                    assertEquals(Ink.light,frame.getPixel(328,361))
                    assertTrue(e.castHint==attacks[slot].hint && e.castName.startsWith(boss.attackNames[slot]))
                    if(slot==0) {
                        save(frame,"normal-${boss.id}")
                        val g=Canvas(gallery[stage/16]); val x=(stage%4)*420f; val y=((stage%16)/4)*264f
                        g.drawText("${stage+1}. ${boss.name}",x+8,y+22,paint)
                        g.drawBitmap(frame,Rect(25,93,631,433),RectF(x,y+28,x+420,y+264),paint)
                    }
                }
                e.hazards.clear(); e.impacts.clear(); e.normalCues.clear()
                e.cast(Pattern.CROSS,true,0)
                e.hazards.forEach { it.resolved=true; e.impacts.add(BattleImpact(it.copy(),.12)) }
                view.draw(Canvas(frame))
                assertEquals("${boss.id} foot marker stays on top",Ink.light,frame.getPixel(328,361))
                val g=Canvas(gallery[2+stage/16]); val x=(stage%4)*420f; val y=((stage%16)/4)*264f
                g.drawText("${stage+1}. ${boss.name}",x+8,y+22,paint)
                g.drawBitmap(frame,Rect(25,93,631,433),RectF(x,y+28,x+420,y+264),paint)
                if(stage in listOf(0,12,17,23,30,31)) save(frame,"ultimate-${boss.id}")
                e.changeScreen(Screen.CUTIN); e.screenAge=.8
                view.draw(Canvas(frame)); assertTrue("${boss.id} cutin is in color",colored(frame)>1000)
                if(stage in listOf(0,12,17,23,30,31)) save(frame,"color-cutin-${boss.id}")
                // Broad backdrop stays in the attack's primary hue; secondary sparks
                // cannot turn every boss's background into the same warm color.
                val cutin=Bitmap.createBitmap(960,190,Bitmap.Config.ARGB_8888)
                BattleEffects().cutin(Canvas(cutin),boss.id,0f,0f,960f,190f,.8)
                val energy=UltimateColors.forBoss(boss.id).energy
                var primaryPixels=0
                for(px in 0 until 960) for(py in 0 until 190) if(cutin.getPixel(px,py)==energy) primaryPixels++
                assertTrue("${boss.id} backdrop follows attack energy",primaryPixels>15000)
                cutin.recycle()
            }
            gallery.forEachIndexed { i,b -> save(b,if(i<2) "normal-attacks-${i+1}" else "ultimate-colors-${i-1}"); b.recycle() }
            frame.recycle(); view.suspend()
        }
    }
}
