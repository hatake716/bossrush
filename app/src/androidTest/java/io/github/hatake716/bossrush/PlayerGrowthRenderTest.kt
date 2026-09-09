package io.github.hatake716.bossrush

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class PlayerGrowthRenderTest {
    @Test fun everyEffectGrowsVisiblyAndCombatKeepsFootMarkersReadable() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
        val ins=InstrumentationRegistry.getInstrumentation(); val ctx=ins.targetContext
        val dir=File(ctx.getExternalFilesDir(null),"screenshots").also { it.mkdirs() }
        fun save(bitmap: Bitmap,name: String) { File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        ins.runOnMainSync {
            val renderer=PlayerEffects(); val paint=Paint().apply { isAntiAlias=false; isFilterBitmap=false }
            val primary=listOf(PlayerEffectKind.SWORD,PlayerEffectKind.FIRE,PlayerEffectKind.GIANT,PlayerEffectKind.KNIFE)
            val sheet=Bitmap.createBitmap(1440,1200,Bitmap.Config.ARGB_8888); val sc=Canvas(sheet); sc.drawColor(Ink.dark)
            val full=Bitmap.createBitmap(1440,PlayerEffectKind.entries.size*300,Bitmap.Config.ARGB_8888); val fc=Canvas(full); fc.drawColor(Ink.dark)
            for((row,kind) in PlayerEffectKind.entries.withIndex()) {
                val counts=mutableListOf<Int>()
                for((column,level) in listOf(1,8,16).withIndex()) {
                    val radius=when(kind) {
                        PlayerEffectKind.SWORD -> Skills.range(Job.WARRIOR,0,level)
                        PlayerEffectKind.KNIFE -> Skills.range(Job.THIEF,0,level)
                        PlayerEffectKind.GIANT -> Skills.range(Job.SUMMONER,0,level)
                        PlayerEffectKind.HANIWA -> Skills.range(Job.SUMMONER,2,level)
                        PlayerEffectKind.FIRE,PlayerEffectKind.ICE -> Skills.range(Job.MAGE,0,level)
                        PlayerEffectKind.ARROW -> Skills.arrowRadius(level)
                        PlayerEffectKind.STEAL -> Skills.range(Job.THIEF,1,level)
                        else -> Skills.auraRadius(level)
                    }
                    val mask=Bitmap.createBitmap(600,334,Bitmap.Config.ARGB_8888)
                    val fx=PlayerEffect(kind,280.0,205.0,radius,level,-PI/2)
                    fx.age=fx.lifetime*.25; renderer.impact(Canvas(mask),fx)
                    val pixels=IntArray(600*334); mask.getPixels(pixels,0,600,0,0,600,334)
                    counts.add(pixels.count { Color.alpha(it)>24 })
                    val tile=Bitmap.createBitmap(480,300,Bitmap.Config.ARGB_8888); val c=Canvas(tile); c.drawColor(Ink.dark)
                    // Preserve original pixels while enlarging the same center region at every level.
                    c.drawBitmap(mask,Rect(140,65,420,285),RectF(93f,56f,387f,287f),paint)
                    PixelFont.draw(c,"${kind.name.replace('_',' ')} / LV.$level",20f,16f,1.7f,Ink.light)
                    fc.drawBitmap(tile,column*480f,row*300f,paint)
                    if(kind in primary) sc.drawBitmap(tile,column*480f,primary.indexOf(kind)*300f,paint)
                    tile.recycle(); mask.recycle()
                }
                assertTrue("$kind Lv.8 should visibly exceed Lv.1: $counts",counts[1]>counts[0])
                assertTrue("$kind Lv.16 should visibly exceed Lv.8: $counts",counts[2]>counts[1])
            }
            save(sheet,"growth-primary-comparison"); save(full,"growth-all-effects"); sheet.recycle(); full.recycle()
            val e=GameEngine(); val view=GameView(ctx,e); view.layout(0,0,960,540)
            val frame=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            for(job in Job.entries) for(level in listOf(1,8,16)) {
                e.run=Run(job,levels=IntArray(4) { level }); e.beginBattle(); e.messageTime=0.0
                e.boss=Actor(300.0,145.0,1e6,1e6); e.player.x=335.0; e.player.y=205.0; e.player.hp=e.player.maxHp*.6
                e.hazards.add(Hazard("circle",-1000.0,-1000.0,1.0,delay=1e6)); e.nextPattern=1e6
                when(job) {
                    Job.WARRIOR -> { e.useSkill(0); e.useSkill(1); e.useSkill(2) }
                    Job.MAGE -> { e.useSkill(2); e.useSkill(0); e.useSkill(1) }
                    Job.SUMMONER -> { e.useSkill(0); e.gauge=100.0; e.useSkill(1) }
                    Job.THIEF -> { e.useSkill(0); e.useSkill(1); e.useSkill(2) }
                }
                repeat(18) { e.update(1.0/120) }
                view.draw(Canvas(frame)); save(frame,"growth-${job.name.lowercase()}-$level")
                if(level==16) {
                    // Keep a short real-engine sequence for visual review of animation and expiry.
                    repeat(60) { i ->
                        e.idealActions(); e.update(1.0/60); e.update(1.0/60)
                        view.draw(Canvas(frame)); save(frame,"growth-motion-${job.name.lowercase()}-${i.toString().padStart(2,'0')}")
                    }
                }
                e.hazards.clear(); e.hazards.add(Hazard("line",e.player.x,e.player.y,260.0,50.0,delay=2.0))
                repeat(2) { for(kind in PlayerEffectKind.entries) e.playerEffects.add(PlayerEffect(kind,e.player.x,e.player.y,65.0,16,age=.1)) }
                val before=e.damageTaken; view.draw(Canvas(frame))
                assertEquals(before,e.damageTaken,0.0)
                assertEquals("$job Lv.$level collision marker",Ink.light,frame.getPixel((28+e.player.x).toInt(),(96+e.player.y).toInt()))
                save(frame,"growth-overlap-${job.name.lowercase()}-$level")
                e.victory(); e.selectUpgrade(0); view.draw(Canvas(frame)); save(frame,"growth-reward-${job.name.lowercase()}-$level")
            }
            frame.recycle(); view.suspend()
        }
    }
}
