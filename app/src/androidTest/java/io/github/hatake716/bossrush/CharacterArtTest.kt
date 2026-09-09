package io.github.hatake716.bossrush

import android.content.Intent
import android.graphics.*
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class CharacterArtTest {
    @get:Rule val rule=ActivityTestRule(MainActivity::class.java,true,false)
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val dir get()=File(context.getExternalFilesDir(null),"screenshots").also { it.mkdirs() }
    @Before fun emulatorOnly() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
    }
    private fun save(bitmap: Bitmap,name: String) {
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }

    @Test fun everyCharacterHasDistinctTransparentArtAndFitsItsDrawingBounds() {
        val atlas=CharacterAtlas(context.assets)
        val companions=listOf("hero_warrior","hero_mage","hero_summoner","hero_thief","summon_giant","summon_rabbit","summon_haniwa")
        val keys=Bosses.all.map { "boss_${it.id}" }+companions
        assertEquals(keys.toSet(),context.assets.list("sprites")!!.map { it.removeSuffix(".png") }.toSet())
        val hashes=mutableSetOf<String>()
        for(key in keys) {
            val bytes=context.assets.open("sprites/$key.png").use { it.readBytes() }
            assertTrue("$key must have its own artwork",hashes.add(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }))
            val b=atlas.bitmap(key)
            assertTrue("$key sampled size",b.width in 32..512 && b.height in 32..512)
            assertTrue("$key alpha channel",b.hasAlpha())
            var clear=0; var solid=0
            for(y in 0 until b.height step 2) for(x in 0 until b.width step 2) {
                val alpha=Color.alpha(b.getPixel(x,y))
                if(alpha<16) clear++
                if(alpha>190) solid++
            }
            assertTrue("$key transparent exterior",clear>b.width*b.height/80)
            assertTrue("$key visible body",solid>b.width*b.height/80)
            val target=Bitmap.createBitmap(240,220,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(target); canvas.drawColor(Ink.deep)
            atlas.draw(canvas,key,120f,180f,180f,140f)
            var changed=0
            for(y in 0 until 220) for(x in 0 until 240) {
                if(target.getPixel(x,y)!=Ink.deep) {
                    changed++
                    assertTrue("$key stays inside its combat/portrait box",x in 30..209 && y in 40..179)
                }
            }
            assertTrue("$key rendered visibly",changed>150)
            target.recycle()
        }
        // Native Canvas contact sheets are visual fixtures, not gameplay screenshots.
        for(page in 0..3) {
            val bitmap=Bitmap.createBitmap(1600,1000,Bitmap.Config.ARGB_8888)
            val c=Canvas(bitmap); c.drawColor(Ink.dark)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Ink.light; textSize=25f; textAlign=Paint.Align.CENTER }
            for(slot in 0..7) {
                val b=Bosses.all[page*8+slot]; val x=slot%4*400f; val y=slot/4*500f
                paint.color=Ink.deep; c.drawRect(x+12,y+12,x+388,y+488,paint)
                atlas.draw(c,"boss_${b.id}",x+200,y+426,350f,365f)
                paint.color=Ink.light; c.drawText("${page*8+slot+1}. ${b.name}",x+200,y+465,paint)
            }
            save(bitmap,"art-bestiary-${page+1}")
        }
        val bitmap=Bitmap.createBitmap(1600,880,Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap); c.drawColor(Ink.dark)
        val names=listOf("戦士","魔法使い","召喚士","盗賊","巨人","白ウサギ","はにわ")
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Ink.light; textSize=27f; textAlign=Paint.Align.CENTER }
        for((i,key) in companions.withIndex()) {
            val x=i%4*400f+200; val y=i/4*440f
            atlas.draw(c,key,x,y+373,325f,340f)
            c.drawText(names[i],x,y+411,paint)
        }
        save(bitmap,"art-party")
    }

    @Test fun enlargedBestiaryNavigatesAllGodsAndReturnsToTheSelectedPage() {
        Configurator.getInstance().waitForIdleTimeout=100
        rule.launchActivity(Intent())
        val device=UiDevice.getInstance(instrumentation)
        device.wait(Until.hasObject(By.desc("神話図鑑")),5000)
        device.findObject(By.text("Got it"))?.click()
        fun tap(label: String) {
            val node=device.wait(Until.findObject(By.desc(label)),5000) ?: error("Missing $label")
            node.click(); SystemClock.sleep(160)
        }
        tap("神話図鑑")
        tap("図鑑 ${Bosses.all[0].name}")
        tap("${Bosses.all[0].name}の姿を拡大")
        assertFalse(device.findObject(By.desc("前の神")).isEnabled)
        for(stage in 0..31) {
            instrumentation.runOnMainSync { assertEquals(stage,rule.activity.gameView.engine.selectedBoss) }
            if(stage in listOf(0,15,17,23,30,31)) save(instrumentation.uiAutomation.takeScreenshot(),"portrait-${Bosses.all[stage].id}")
            if(stage<31) tap("次の神")
        }
        assertFalse(device.findObject(By.desc("次の神")).isEnabled)
        tap("前の神"); tap("図鑑へ戻る")
        assertNotNull(device.wait(Until.findObject(By.desc("${Bosses.all[30].name}の姿を拡大")),3000))
        tap("${Bosses.all[30].name}の姿を拡大")
        device.pressBack()
        assertNotNull(device.wait(Until.findObject(By.desc("${Bosses.all[30].name}の姿を拡大")),3000))
    }
}
