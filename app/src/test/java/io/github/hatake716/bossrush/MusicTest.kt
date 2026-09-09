package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class MusicTest {
    @Test fun catalogCoversEveryBossAndBothOtherScoresWithWholeThirtySecondPhrases() {
        assertEquals(Bosses.all.map { it.id }, MusicCatalog.battles.map { it.id })
        assertEquals(34, MusicCatalog.all.map { it.id }.toSet().size)
        for (track in MusicCatalog.all) {
            assertTrue("${track.id}: ${track.seconds}", track.seconds in 27.0..33.0)
            assertEquals(0, track.bars % 4)
            assertEquals(track.bars * track.beats * 60.0 / track.bpm, track.seconds, 1.0 / MusicCatalog.SAMPLE_RATE)
        }
        assertEquals(30.0, MusicCatalog.shop.seconds, .00001)
        assertEquals(30.0, MusicCatalog.ending.seconds, .00001)
    }
    @Test fun zunHarmonyAndIndividualMotifsArePreserved() {
        for (bar in 0 until 40) {
            val chord = BattleScore.chordAt(bar)
            assertEquals(if (bar % 4 < 2) 4 else 3, chord[1] - chord[0])
            assertEquals(7, chord[2] - chord[0])
            assertEquals(listOf(8, 10, 0, 0)[bar % 4], chord[0])
        }
        assertEquals(32, BattleScore.themes.map { it.phraseA to it.phraseB }.toSet().size)
        assertEquals(3, MusicCatalog.battles.first { it.id == "loki" }.beats)
        assertTrue(MusicCatalog.battles.all { it.bpm in 192..232 })
    }
    @Test fun titleAndOtherSilentScreensCannotResolveAMusicTrack() {
        for (screen in listOf("title", "jobs", "codex", "help", "gameover", "unknown")) {
            for (boss in 0..31) assertNull(MusicCatalog.forScene(screen, boss))
        }
        assertEquals("thor", MusicCatalog.forScene("battle", 30)?.id)
        assertEquals("shop", MusicCatalog.forScene("shop", 31)?.id)
        assertEquals("ending", MusicCatalog.forScene("ending", 31)?.id)
    }
    @Test fun stereoLoopsHaveNoInsertedSilenceOrPositionResetOnTheSameTrack() {
        val pcm = MusicPcm("test", shortArrayOf(10000, -5000, 20000, -10000, 15000, -7500))
        val mixer = AudioMix(); mixer.music = pcm
        val output = ShortArray(1024)
        repeat(20) { mixer.render(output, true, .7) }
        assertEquals(20 * 512 % 3, mixer.position)
        val position = mixer.position; mixer.music = pcm; assertEquals(position, mixer.position)
        for (i in output.indices step 2) { assertTrue(output[i] > 0); assertTrue(output[i + 1] < 0) }
        // At full entrance gain each cycle is bit-identical, including across chunk boundaries.
        for (i in 6 until output.size) assertEquals(output[i - 6], output[i])
    }
    @Test fun muteSceneSwitchSeDuckingAndSaturationOperateOnTheActualMixer() {
        val mixer = AudioMix(); val out = ShortArray(4096)
        mixer.music = MusicPcm("test", ShortArray(8192) { 12000 })
        mixer.effects.add("sword"); mixer.render(out, true, .7)
        assertTrue(mixer.peak > 0); assertTrue(mixer.duck in .58.. .60)
        mixer.render(out, false, .7); assertTrue(out.all { it.toInt() == 0 }); assertEquals(0, mixer.effects.voiceCount)
        mixer.music = null; mixer.render(out, true, .7); assertTrue(out.all { it.toInt() == 0 })
        mixer.effects.add("click"); mixer.render(out, true, .7); assertTrue(mixer.peak > 0)
        repeat(8) { mixer.effects.add("ultimate") }
        repeat(10) { mixer.render(out, true, 1.0); assertTrue(out.all { abs(it.toInt()) <= 30000 }) }
        repeat(100) { mixer.render(out, true, .7) }; assertEquals(1.0, mixer.duck, .0001)
    }
    @Test fun combatEffectsRetainTheirDurationAndPitchAtStereoOutputRate() {
        val base = EffectMixer(); val stereo = EffectMixer(MusicCatalog.SAMPLE_RATE)
        base.add("sword"); stereo.add("sword")
        val clip = SoundEffects.clip("sword")!!
        repeat(clip.size) {
            assertEquals(base.next(), stereo.next(), 1e-7)
            stereo.next()
        }
        assertEquals(0, base.voiceCount); assertEquals(0, stereo.voiceCount)
    }
}
