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
    fun sample(time: Double, scene: String, bossIndex: Int): Double {
        val b=Bosses.all[bossIndex.coerceIn(0,31)]
        val end=scene=="ending"; val battle=scene=="battle"
        val bpm=if(end) 78 else if(battle) b.bpm else if(scene=="shop") 112 else 92
        val stepDuration=60.0/bpm/2
        val step=floor(time/stepDuration).toInt(); val local=time%stepDuration
        val phrase=step/16%4
        val motif=if(end) ending else if(battle) b.motif else home
        val root=if(end) 60 else if(battle) b.root else 57
        val degree=motif[step%motif.size]+if(battle&&phrase==2) 12 else 0
        val note=root+degree
        val gate=if(end) .95 else if(battle) .70 else .82
        val env=envelope(local,stepDuration*gate)
        val melodic=if(battle && step%16==15) .0 else pulse(time,freq(note),if(bossIndex%3==0) .125 else .25)*env*.20
        val chordRoots=if(end||b.mode=="major") intArrayOf(0,5,9,7) else intArrayOf(0,8,5,7)
        val chord=chordRoots[step/8%4]
        val arpeggio=intArrayOf(0,if(end||b.mode=="major") 4 else 3,7,12)[step%4]
        val harmony=pulse(time,freq(root-12+chord+arpeggio),.5)*env*(if(end) .09 else .065)
        val bass=triangle(time,freq(root-24+chord))*envelope(time%(stepDuration*2),stepDuration*1.9)*.17
        val tick=time%stepDuration
        val seed=((time*SAMPLE_RATE).toLong()*1103515245L+12345L)
        val noise=((seed xor(seed shr 11) xor(seed shl 7)) and 65535)/32768.0-1
        val drums=if(!battle) noise*exp(-tick*100)*.014 else {
            val kick=if(step%4==0) sin(2*PI*(90*tick-18*tick*tick))*exp(-tick*28)*.20 else .0
            val snare=if(step%4==2) noise*exp(-tick*43)*.11 else .0
            kick+snare+noise*exp(-tick*140)*.035
        }
        val swell=if(end) .68+.20*sin(time*.3) else 1.0
        return ((melodic+harmony+bass+drums)*swell*min(1.0,time*3)).coerceIn(-.8,.8)
    }
    fun effect(time: Double, kind: String): Double {
        if(time<0 || time>.30) return .0
        val base=when(kind) { "hurt" -> 90.0; "hit" -> 220.0; "coin" -> 1050.0; "heal" -> 660.0; "ultimate" -> 120.0; "cast" -> 330.0; "ice" -> 1100.0; "summon" -> 440.0; else -> 550.0 }
        val slope=if(kind in listOf("hurt","hit","fire","ultimate")) -.65 else .65
        return pulse(time,base*(1+slope*time*3),.25)*exp(-time*18)*.16
    }
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
            var frame=0L; var old=""; var effectName=""; var effectFrame=100000
            val buffer=ShortArray(512)
            while(active) {
                val current="$scene:$bossIndex"
                if(current!=old) { old=current; frame=0 }
                effects.poll()?.let { effectName=it; effectFrame=0 }
                for(i in buffer.indices) {
                    val music=ScoreSynth.sample(frame++.toDouble()/ScoreSynth.SAMPLE_RATE,scene,bossIndex)
                    val effect=ScoreSynth.effect(effectFrame++.toDouble()/ScoreSynth.SAMPLE_RATE,effectName)
                    buffer[i]=if(enabled) ((music+effect)*volume*24000).coerceIn(-32767.0,32767.0).toInt().toShort() else 0
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
