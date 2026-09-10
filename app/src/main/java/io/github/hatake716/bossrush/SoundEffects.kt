package io.github.hatake716.bossrush

import kotlin.math.*

/** Short original, material-specific chip sounds. No external audio assets. */
object SoundEffects {
    const val SAMPLE_RATE = 22050
    val durations=mapOf(
        "sword" to .25, "knife" to .15, "haniwa" to .29, "arrow" to .18,
        "fire" to .32, "ice" to .35, "arrow-hit" to .17, "fire-hit" to .36,
        "ice-hit" to .30, "giant-hit" to .33, "summon-giant" to .55,
        "summon-rabbit" to .48, "summon-haniwa" to .43, "shield" to .32,
        "focus" to .42, "speed" to .27, "hurt" to .25,
        "limit-slash" to .42, "limit-flare" to .52, "limit-summon" to .8, "limit-vanish" to .65,
        "coin" to .28, "heal" to .48, "ultimate" to .75, "cast" to .25,
        "boss-defeat" to 1.1, "buff" to .32, "victory" to .70, "click" to .07
    )
    fun sample(time: Double,kind: String): Double {
        val duration=durations[kind] ?: return .0
        if(time<0 || time>=duration) return .0
        val t=time; val u=t/duration
        val attack=min(1.0,t/.002); val release=min(1.0,(duration-t)/.02)
        val noise=AudioMath.noise(t)
        fun tone(base: Double,sweep: Double=0.0,duty: Double=.25): Double {
            // Integrated linear frequency sweep, so pitch does not jump when the frequency changes.
            val phase=base*t+sweep*t*t/2
            return if(phase%1<duty) 1.0-duty else -duty
        }
        fun bell(base: Double)=sin(2*PI*base*t)*.65+sin(2*PI*base*2.01*t)*.22
        fun flourish(base: Double): Double {
            val semitone=intArrayOf(0,4,7,12)[min(3,(u*4).toInt())]
            return tone(base*2.0.pow(semitone/12.0))*exp(-t*4)
        }
        val sound=when(kind) {
            "sword" -> noise*exp(-t*23)*.54+tone(980.0,-2800.0)*exp(-t*12)*.40
            "knife" -> noise*exp(-t*40)*.35+tone(1650.0,-6000.0,.125)*exp(-t*24)*.65
            "haniwa" -> sin(2*PI*(160*t-180*t*t))*exp(-t*21)*.65+noise*exp(-t*65)*.28
            "arrow" -> noise*exp(-t*30)*.45+tone(1450.0,-5200.0)*exp(-t*32)*.38
            "fire" -> noise*(1-u)*.42+tone(150.0,2100.0)*exp(-t*10)*.40
            "ice" -> (bell(1380.0)+bell(2070.0)*.45)*exp(-t*10)*.50
            "arrow-hit" -> noise*exp(-t*58)*.50+tone(420.0,-1300.0)*exp(-t*27)*.48
            "fire-hit" -> noise*exp(-t*14)*.52+sin(2*PI*(100*t+1.8*(1-exp(-t*60))))*exp(-t*15)*.46
            "ice-hit" -> (bell(1860.0)+noise*.3)*exp(-t*20)*.58
            "giant-hit" -> sin(2*PI*(68*t+2.0*(1-exp(-t*55))))*exp(-t*16)*.68+noise*exp(-t*40)*.28
            "summon-giant" -> tone(85.0,450.0,.5)*exp(-t*4)*.55+noise*exp(-t*18)*.20
            "summon-rabbit" -> flourish(720.0)*.65
            "summon-haniwa" -> tone(245.0,350.0)*exp(-t*8)*.55+bell(490.0)*exp(-t*16)*.24
            "shield" -> bell(440.0)*exp(-t*14)*.5+noise*exp(-t*60)*.24
            "focus" -> flourish(440.0)*.55+bell(880.0)*exp(-t*8)*.18
            "speed" -> tone(550.0,3300.0,.125)*exp(-t*9)*.65
            "hurt" -> tone(115.0,-260.0,.5)*exp(-t*17)*.6+noise*exp(-t*28)*.25
            "coin" -> tone(if(t<.08) 1100.0 else 1650.0)*exp(-t*10)*.60
            "limit-slash" -> {
                val step=min(9,(t/.016).toInt()); val local=t-step*.016
                (noise*.6+bell(900.0+step*85)*.35)*exp(-local*65)*exp(-max(0.0,t-.16)*14)
            }
            "limit-flare" -> noise*exp(-t*9)*.58+sin(2*PI*(62*t+3.5*(1-exp(-t*35))))*exp(-t*11)*.35
            "limit-summon" -> (tone(65.0,140.0,.5)*.55+bell(195.0)*.25+noise*.15)*exp(-t*3)
            "limit-vanish" -> (noise*.28+bell(1046.5)*.38+tone(1568.0,-1400.0)*.22)*exp(-t*6)
            "heal" -> flourish(660.0)*.5
            "ultimate" -> (tone(68.0,80.0,.5)*.45+noise*.3)*(1-u)*(.75+.25*sin(t*36))
            "cast" -> tone(240.0,1000.0,.125)*exp(-t*11)*.40
            "buff" -> flourish(550.0)*.45
            "boss-defeat" -> (noise*.64+sin(2*PI*(48*t+2.4*(1-exp(-t*28))))*.32)*exp(-t*4)
            "victory" -> flourish(523.25)*.60
            else -> tone(950.0)*exp(-t*60)*.40
        }
        return (sound*attack*release).coerceIn(-.95,.95)
    }
    private val clips by lazy { durations.mapValues { (kind,duration) -> FloatArray((duration*SoundEffects.SAMPLE_RATE).toInt()) { sample(it.toDouble()/SoundEffects.SAMPLE_RATE,kind).toFloat() } } }
    fun clip(kind: String): FloatArray? = clips[kind]
}

/** Independent voices prevent simultaneous attacks, impact sounds and UI sounds cutting one another off. */
class EffectMixer(private val outputRate: Int = SoundEffects.SAMPLE_RATE) {
    init { require(outputRate > 0) }
    private data class Voice(val clip: FloatArray,var frame: Double=0.0)
    private val voices=mutableListOf<Voice>()
    val voiceCount get()=voices.size
    fun add(kind: String) {
        val clip=SoundEffects.clip(kind) ?: return
        if(voices.size==8) voices.removeAt(0)
        voices.add(Voice(clip))
    }
    fun next(): Double {
        var result=.0
        var i=voices.lastIndex
        while(i>=0) {
            val v=voices[i]
            val frame=v.frame.toInt(); val fraction=v.frame-frame
            result+=v.clip[frame]*(1-fraction)+v.clip[min(frame+1,v.clip.lastIndex)]*fraction
            v.frame+=SoundEffects.SAMPLE_RATE.toDouble()/outputRate
            if(v.frame>=v.clip.size) voices.removeAt(i)
            i--
        }
        return result
    }
    fun clear()=voices.clear()
}
