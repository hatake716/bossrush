package io.github.hatake716.bossrush

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

/** Original two-pulse / triangle / noise compositions, synthesized entirely on device. */
object ScoreSynth {
    const val SAMPLE_RATE=22050
    private val home=listOf(0,7,12,10,7,3,5,2,0,7,10,12,14,10,7,5)
    private val ending=listOf(0,4,7,12,11,7,9,7,5,4,2,4,7,4,2,0,0,4,7,12,14,12,11,9,7,9,7,4,5,4,2,0)
    private val majorRoots=intArrayOf(0,5,9,7)
    private val minorRoots=intArrayOf(0,8,5,7)
    private val majorArp=intArrayOf(0,4,7,12)
    private val minorArp=intArrayOf(0,3,7,12)
    fun sample(time: Double, scene: String, bossIndex: Int): Double {
        if(scene=="battle") return BattleScore.sample(time,bossIndex)
        val end=scene=="ending"
        val bpm=if(end) 78 else if(scene=="shop") 112 else 92
        val stepDuration=60.0/bpm/2
        val step=floor(time/stepDuration).toInt(); val local=time%stepDuration
        val motif=if(end) ending else home
        val root=if(end) 60 else 57
        val note=root+motif[step%motif.size]
        val env=envelope(local,stepDuration*(if(end) .95 else .82))
        val melodic=pulse(time,freq(note),if(bossIndex%3==0) .125 else .25)*env*.20
        val chordRoots=if(end) majorRoots else minorRoots
        val chord=chordRoots[step/8%4]
        val arpeggio=(if(end) majorArp else minorArp)[step%4]
        val harmony=pulse(time,freq(root-12+chord+arpeggio),.5)*env*(if(end) .09 else .065)
        val bass=triangle(time,freq(root-24+chord))*envelope(time%(stepDuration*2),stepDuration*1.9)*.17
        val seed=((time*SAMPLE_RATE).toLong()*1103515245L+12345L)
        val noise=((seed xor(seed shr 11) xor(seed shl 7)) and 65535)/32768.0-1
        val drums=noise*exp(-local*100)*.014
        val swell=if(end) .68+.20*sin(time*.3) else 1.0
        return ((melodic+harmony+bass+drums)*swell*min(1.0,time*3)).coerceIn(-.8,.8)
    }
    fun effect(time: Double, kind: String): Double = SoundEffects.sample(time,kind)
    private fun freq(note: Int)=440*2.0.pow((note-69)/12.0)
    private fun pulse(t: Double,f: Double,duty: Double)=if((t*f)%1<duty) 1.0-duty else -duty
    private fun triangle(t: Double,f: Double)=1-4*abs((t*f)%1-.5)
    private fun envelope(t: Double,length: Double)=if(t>length) .0 else min(1.0,t/.008)*min(1.0,(length-t)/.025)
}

class Chiptune {
    @Volatile var enabled=true
    @Volatile var volume=.7
    @Volatile private var scene="title"
    @Volatile private var bossIndex=0
    @Volatile private var active=false
    private var worker: Thread?=null
    private val effects=ConcurrentLinkedQueue<String>()
    fun music(name: String,index: Int=0) { if(scene!=name||bossIndex!=index) { scene=name; bossIndex=index } }
    fun effect(name: String) { if(effects.size<12) effects.add(name) }
    @Synchronized fun start() {
        if(active) return
        active=true
        worker=Thread({ renderLoop() },"bossrush-audio").apply { isDaemon=true; start() }
    }
    @Synchronized fun stop() {
        active=false
        worker?.join(800); worker=null; effects.clear()
    }
    private fun renderLoop() {
        var track: AudioTrack?=null
        try {
            val minBuffer=AudioTrack.getMinBufferSize(ScoreSynth.SAMPLE_RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
            track=AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(ScoreSynth.SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(max(4096,minBuffer)).setTransferMode(AudioTrack.MODE_STREAM).build()
            track.play()
            var frame=0L; var old=""
            val mixer=EffectMixer(); var duck=1.0
            val buffer=ShortArray(512)
            while(active) {
                val current="$scene:$bossIndex"
                if(current!=old) { old=current; frame=0; mixer.clear() }
                while(true) { val name=effects.poll() ?: break; if(enabled) mixer.add(name) }
                if(!enabled) mixer.clear()
                for(i in buffer.indices) {
                    val music=ScoreSynth.sample(frame++.toDouble()/ScoreSynth.SAMPLE_RATE,scene,bossIndex)
                    val target=if(mixer.voiceCount>0) .58 else 1.0
                    duck+=(target-duck)*(if(target<duck) .007 else .0007)
                    val effect=mixer.next()
                    val mixed=music*duck+effect*.85
                    // Smooth saturation keeps overlapping impacts within PCM headroom.
                    buffer[i]=if(enabled) (tanh(mixed)*volume*30000).toInt().toShort() else 0
                }
                if(track.write(buffer,0,buffer.size)<0) break
            }
        } catch(e: IllegalArgumentException) {
            android.util.Log.w("BOSSRUSH","Audio format unavailable",e)
        } catch(e: IllegalStateException) {
            android.util.Log.w("BOSSRUSH","Audio output unavailable",e)
        } finally {
            try { track?.pause(); track?.flush() } catch(_: IllegalStateException) { }
            track?.release()
        }
    }
}
