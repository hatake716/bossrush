package io.github.hatake716.bossrush

import android.os.SystemClock
import android.content.Intent
import androidx.test.rule.ActivityTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class MusicPlaybackTest {
    @get:Rule val rule = ActivityTestRule(MainActivity::class.java, true, false)
    private fun emulator() {
        check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
        assertTrue(label, condition())
    }
    @Test fun allPackagedTracksDecodeWithStereoEnergyAndContinuousEdges() {
        emulator()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val manifest = JSONObject(ctx.assets.open("music/manifest.json").bufferedReader().use { it.readText() }).getJSONArray("tracks")
        assertEquals(MusicCatalog.all.size, manifest.length())
        val report = JSONArray(); val signatures = mutableSetOf<Int>()
        for ((index, track) in MusicCatalog.all.withIndex()) {
            val metadata = manifest.getJSONObject(index)
            assertEquals(track.id, metadata.getString("id")); assertEquals(track.frames, metadata.getInt("frames"))
            val digest = ctx.assets.open(track.asset).use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()) }.joinToString("") { "%02x".format(it) }
            assertEquals(metadata.getString("sha256"), digest)
            val started = SystemClock.elapsedRealtime()
            val pcm = MusicDecoder.decode(ctx.assets, track)
            val elapsed = SystemClock.elapsedRealtime() - started
            assertTrue(track.id, pcm.frames in track.frames - 2048..track.frames)
            val rms = sqrt(pcm.samples.sumOf { it.toDouble() * it } / pcm.samples.size) / 32768
            assertTrue("${track.id} rms $rms", rms in .03.. .5)
            assertTrue(track.id, pcm.samples.indices.step(2).any { pcm.samples[it] != pcm.samples[it+1] })
            for (ch in 0..1) assertEquals(pcm.samples[ch], pcm.samples[(pcm.frames-1)*2+ch])
            val mixer = AudioMix(); mixer.music = pcm
            val buffer = ShortArray(1024)
            // Cross the actual loop twice through the runtime mixer, with SE present once.
            mixer.effects.add("sword")
            repeat((pcm.frames*2+2048)/512) { mixer.render(buffer,true,.7) }
            assertTrue(mixer.peak > 0)
            signatures.add(pcm.samples.contentHashCode())
            report.put(JSONObject().put("id",track.id).put("frames",pcm.frames).put("expected_frames",track.frames)
                .put("seconds",pcm.frames/44100.0).put("rms",rms).put("decode_ms",elapsed).put("peak",pcm.samples.maxOf { abs(it.toInt()) }))
        }
        assertEquals(34, signatures.size)
        File(ctx.getExternalFilesDir(null), "music-verification.json").writeText(report.toString(2))
    }
    @Test fun audioTrackSurvivesMuteFastSceneChangesAndBackgroundRestart() {
        emulator()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = Chiptune(ctx)
        try {
            audio.start(); SystemClock.sleep(150)
            assertNull(audio.playingId); assertEquals(0, audio.renderedPeak)
            audio.music("battle",30)
            await("Thor loads and plays") { audio.playingId == "thor" && audio.renderedPeak > 0 }
            audio.effect("sword"); audio.enabled = false
            await("Mute is silent") { audio.renderedPeak == 0 }
            audio.music("battle",10); audio.music("ending"); audio.music("title")
            audio.enabled = true
            await("A stale decode cannot restore title BGM") { audio.playingId == null && audio.renderedPeak == 0 }
            SystemClock.sleep(500); assertNull(audio.playingId); assertEquals(0, audio.renderedPeak)
            audio.effect("click"); await("Silent title still has UI SE") { audio.renderedPeak > 0 }
            audio.music("shop"); await("Shop plays") { audio.playingId == "shop" && audio.renderedPeak > 0 }
            audio.music("ending"); await("Ending plays") { audio.playingId == "ending" && audio.renderedPeak > 0 }
            repeat(3) {
                audio.stop(); assertFalse(audio.running); assertEquals(0,audio.renderedPeak)
                audio.start(); await("Restart restores current scene") { audio.playingId == "ending" && audio.renderedPeak > 0 }
            }
            audio.stop(); audio.music("title"); audio.start()
            SystemClock.sleep(300); assertNull(audio.playingId); assertEquals(0,audio.renderedPeak)
        } finally { audio.stop() }
    }
    @Test fun fixtureGameScreensRouteBattleRewardEndingAndSilentMenus() {
        emulator()
        val ins = InstrumentationRegistry.getInstrumentation()
        rule.launchActivity(Intent())
        val view = rule.activity.gameView
        try {
            ins.runOnMainSync { view.audio.enabled = true }
            await("Title stays silent") { view.audio.running && view.audio.playingId == null }
            ins.runOnMainSync {
                view.engine.run = Run(Job.WARRIOR, stage = 30)
                view.engine.beginBattle()
                view.engine.screen = Screen.PAUSED
            }
            await("Battle scene routes to Thor") { view.audio.playingId == "thor" && view.audio.renderedPeak > 0 }
            ins.runOnMainSync { view.engine.victory() }
            await("Reward routes to shop") { view.audio.playingId == "shop" && view.audio.renderedPeak > 0 }
            ins.runOnMainSync { view.engine.screen = Screen.ENDING }
            await("Color ending routes to the ending score") { view.audio.playingId == "ending" && view.audio.renderedPeak > 0 }
            for (screen in listOf(Screen.TITLE, Screen.JOBS, Screen.CODEX, Screen.HELP)) {
                ins.runOnMainSync { view.engine.screen = screen }
                await("$screen has no BGM") { view.audio.playingId == null && view.audio.renderedPeak == 0 }
            }
        } finally { rule.finishActivity() }
    }

}
