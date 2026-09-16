package io.github.hatake716.bossrush

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlayerComboRenderTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val dir get()=File(ins.targetContext.getExternalFilesDir(null),"combos").also { it.mkdirs() }
    private fun guard() { check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" } }
    private fun save(b: Bitmap,name: String) { File(dir,"$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) } }
    private fun setup(e: GameEngine,job: Job,level: Int) {
        e.run=Run(job,stage=15,levels=IntArray(4) { level }); e.beginBattle(); e.messageTime=0.0
        e.boss=Actor(325.0,130.0,1e7,1e7); e.player.x=300.0; e.player.y=if(job==Job.MAGE) 286.0 else 192.0
        e.hazards.add(Hazard("circle",-1000.0,-1000.0,1.0,delay=1e6)); e.nextPattern=1e6
    }
    private fun cast(e: GameEngine,slot: Int,stage: Int) {
        repeat(stage) {
            e.projectiles.clear(); e.iceMarks.clear(); e.playerEffects.clear(); e.gauge=100.0; e.cooldowns[slot]=0.0
            assertTrue(e.useSkill(slot))
        }
    }

    @Test fun threeStagesHaveDifferentPixelsAndFireAndIceHaveDifferentColors() {
        guard()
        ins.runOnMainSync {
            val renderer=PlayerEffects(); val p=Paint().apply { isFilterBitmap=false }
            val sheet=Bitmap.createBitmap(1440,1200,Bitmap.Config.ARGB_8888); val canvas=Canvas(sheet); canvas.drawColor(Ink.dark)
            val kinds=listOf(PlayerEffectKind.SWORD,PlayerEffectKind.KNIFE,PlayerEffectKind.FIRE,PlayerEffectKind.ICE)
            for((row,kind) in kinds.withIndex()) {
                val hashes=mutableSetOf<Int>()
                for(stage in 1..3) {
                    val frame=Bitmap.createBitmap(480,300,Bitmap.Config.ARGB_8888); val c=Canvas(frame); c.drawColor(Ink.dark)
                    val fx=PlayerEffect(kind,240.0,195.0,100.0,16,-Math.PI/2,combo=stage,targetX=240.0,targetY=127.0)
                    fx.age=fx.lifetime*.28; renderer.impact(c,fx)
                    val pixels=IntArray(480*300); frame.getPixels(pixels,0,480,0,0,480,300); hashes.add(pixels.contentHashCode())
                    if(kind==PlayerEffectKind.FIRE) assertTrue(pixels.count { Color.red(it)>200 && Color.green(it)<180 && Color.blue(it)<120 }>300)
                    if(kind==PlayerEffectKind.ICE) assertTrue(pixels.count { Color.blue(it)>190 && Color.red(it)<180 }>300)
                    PixelFont.draw(c,"${kind.name} / COMBO $stage",20f,16f,1.7f,Ink.light)
                    canvas.drawBitmap(frame,(stage-1)*480f,row*300f,p); frame.recycle()
                }
                assertEquals("$kind stages need visibly different compositions",3,hashes.size)
            }
            save(sheet,"combo-stages"); sheet.recycle()
        }
    }

    @Test fun realVolleysAndSummonPunchesAnimateWhileMarkersStayOnTop() {
        guard()
        ins.runOnMainSync {
            val e=GameEngine(); val view=GameView(ins.targetContext,e); view.layout(0,0,960,540)
            val frame=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            val sheet=Bitmap.createBitmap(1280,1080,Bitmap.Config.ARGB_8888); val sheetCanvas=Canvas(sheet)
            val cases=listOf(Job.WARRIOR to 0,Job.THIEF to 0,Job.MAGE to 0,Job.MAGE to 1,Job.SUMMONER to 0,Job.SUMMONER to 2)
            for((index,pair) in cases.withIndex()) {
                val(job,slot)=pair; setup(e,job,16)
                val name=when { job==Job.MAGE -> if(slot==0) "fire" else "ice"; job==Job.SUMMONER -> if(slot==0) "giant" else "haniwa"; else -> job.name.lowercase() }
                if(job==Job.SUMMONER) {
                    e.useSkill(slot)
                    repeat(40) { e.update(1.0/120) }
                    if(slot==2) { e.gauge=100.0; e.cooldowns[2]=0.0; e.useSkill(2) }
                    else e.summons.single().timer=0.0
                } else cast(e,slot,3)
                var minDraw=Long.MAX_VALUE; var totalDraw=0L; var maxDraw=0L
                repeat(72) { n ->
                    e.update(1.0/60)
                    val start=System.nanoTime(); view.draw(Canvas(frame)); val draw=System.nanoTime()-start
                    totalDraw+=draw; minDraw=minOf(minDraw,draw); maxDraw=maxOf(maxDraw,draw)
                    if(n%2==0) save(frame,"motion-$name-${(n/2).toString().padStart(2,'0')}")
                    val best=when(name) { "fire" -> 13; "ice" -> 54; "giant" -> 7; "haniwa" -> 7; else -> 8 }
                    if(n==best) {
                        save(frame,"battle-$name")
                        val xx=(index%2)*640f; val yy=(index/2)*360f
                        sheetCanvas.drawBitmap(frame,null,RectF(xx,yy,xx+640,yy+360),null)
                    }
                }
                // Fixture draws include overlapping magic and a real warning, with the hit marker last.
                e.playerEffects.add(PlayerEffect(PlayerEffectKind.FIRE,e.player.x,e.player.y,90.0,16,age=.12,combo=3))
                e.playerEffects.add(PlayerEffect(PlayerEffectKind.ICE,e.player.x,e.player.y,90.0,16,age=.12,combo=3))
                e.hazards.add(Hazard("line",e.player.x,e.player.y,240.0,35.0,delay=2.0))
                val before=e.damageDone; view.draw(Canvas(frame)); assertEquals(before,e.damageDone,0.0)
                val x=(28+e.player.x).toInt(); val y=(96+e.player.y).toInt()
                assertEquals(Ink.light,frame.getPixel(x,y)); save(frame,"overlap-$name")
                File(dir,"render-time-$name.txt").writeText("Software Canvas / 960x540 / 72 frames: mean ${totalDraw/72/1e6} ms; min ${minDraw/1e6} ms; max ${maxDraw/1e6} ms\n")
            }
            save(sheet,"battle-comparison"); sheet.recycle(); frame.recycle(); view.suspend()
        }
    }
}
