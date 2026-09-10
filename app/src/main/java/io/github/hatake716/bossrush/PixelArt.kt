package io.github.hatake716.bossrush

import android.graphics.*
import kotlin.math.*

object Ink {
    val dark=Color.rgb(16,29,26)
    val deep=Color.rgb(35,56,45)
    val mid=Color.rgb(111,138,91)
    val light=Color.rgb(216,231,168)
    val paper=Color.rgb(189,207,140)
    val palette=intArrayOf(dark,deep,mid,light)
    val dawn=intArrayOf(Color.rgb(31,36,61),Color.rgb(67,99,107),Color.rgb(92,184,144),Color.rgb(250,219,148))
}

object PixelFont {
    private val rows=mapOf(
        'A' to "01110/10001/10001/11111/10001/10001/10001", 'B' to "11110/10001/10001/11110/10001/10001/11110",
        'C' to "01111/10000/10000/10000/10000/10000/01111", 'D' to "11110/10001/10001/10001/10001/10001/11110",
        'E' to "11111/10000/10000/11110/10000/10000/11111", 'F' to "11111/10000/10000/11110/10000/10000/10000",
        'G' to "01111/10000/10000/10111/10001/10001/01111", 'H' to "10001/10001/10001/11111/10001/10001/10001",
        'I' to "111/010/010/010/010/010/111", 'J' to "00111/00010/00010/00010/10010/10010/01100",
        'K' to "10001/10010/10100/11000/10100/10010/10001", 'L' to "10000/10000/10000/10000/10000/10000/11111",
        'M' to "10001/11011/10101/10101/10001/10001/10001", 'N' to "10001/11001/10101/10011/10001/10001/10001",
        'O' to "01110/10001/10001/10001/10001/10001/01110", 'P' to "11110/10001/10001/11110/10000/10000/10000",
        'Q' to "01110/10001/10001/10001/10101/10010/01101", 'R' to "11110/10001/10001/11110/10100/10010/10001",
        'S' to "01111/10000/10000/01110/00001/00001/11110", 'T' to "11111/00100/00100/00100/00100/00100/00100",
        'U' to "10001/10001/10001/10001/10001/10001/01110", 'V' to "10001/10001/10001/10001/10001/01010/00100",
        'W' to "10001/10001/10001/10101/10101/10101/01010", 'X' to "10001/10001/01010/00100/01010/10001/10001",
        'Y' to "10001/10001/01010/00100/00100/00100/00100", 'Z' to "11111/00001/00010/00100/01000/10000/11111",
        '0' to "01110/10001/10011/10101/11001/10001/01110", '1' to "010/110/010/010/010/010/111",
        '2' to "01110/10001/00001/00010/00100/01000/11111", '3' to "11110/00001/00001/01110/00001/00001/11110",
        '4' to "00010/00110/01010/10010/11111/00010/00010", '5' to "11111/10000/10000/11110/00001/00001/11110",
        '6' to "01110/10000/10000/11110/10001/10001/01110", '7' to "11111/00001/00010/00100/01000/01000/01000",
        '8' to "01110/10001/10001/01110/10001/10001/01110", '9' to "01110/10001/10001/01111/00001/00001/01110",
        '-' to "000/000/000/111/000/000/000", '.' to "0/0/0/0/0/0/1", ':' to "0/1/1/0/1/1/0",
        '/' to "00001/00001/00010/00100/01000/10000/10000", '+' to "000/010/010/111/010/010/000",
        '!' to "1/1/1/1/1/0/1", '?' to "1110/0001/0001/0010/0100/0000/0100",
        '%' to "11001/11010/00010/00100/01000/01011/10011"
    ).mapValues { it.value.split('/') }
    private val p=Paint()
    fun width(text: String,size: Float): Float = text.uppercase().sumOf { if(it==' ') 4 else (rows[it]?.first()?.length ?: 5)+1 }.toFloat()*size
    fun draw(c: Canvas,text: String,x: Float,y: Float,size: Float,color: Int=Ink.light,center: Boolean=false) {
        p.color=color
        var px=if(center) x-width(text,size)/2 else x
        for(ch in text.uppercase()) {
            val glyph=rows[ch]
            if(glyph!=null) for(j in glyph.indices) for(i in glyph[j].indices) if(glyph[j][i]=='1') c.drawRect(px+i*size,y+j*size,px+(i+1)*size,y+(j+1)*size,p)
            px+=(if(ch==' ') 4 else (glyph?.first()?.length ?: 5)+1)*size
        }
    }
}

/** Scene rendering uses the same authored asset at portrait and combat sizes. */
class PixelArt(assets: android.content.res.AssetManager) {
    private val atlas=CharacterAtlas(assets)
    private val paint=Paint().apply { isFilterBitmap=false; isAntiAlias=false }
    fun sprite(c: Canvas,key: String,x: Float,y: Float,scale: Float=2f,color: Boolean=false,alpha: Int=255,flip: Boolean=false) {
        val asset=when(key) {
            "warrior" -> "hero_warrior"; "magehero" -> "hero_mage"
            "summoner" -> "hero_summoner"; "thief" -> "hero_thief"
            "giant", "rabbit", "haniwa" -> "summon_$key"
            else -> error("Unknown companion: $key")
        }
        atlas.draw(c,asset,x,y+2*scale,42*scale,(if(key=="giant") 48 else 40)*scale,color,alpha,flip)
    }
    fun boss(c: Canvas,id: String,x: Float,feetY: Float,width: Float,height: Float,alpha: Int=255) {
        atlas.draw(c,"boss_$id",x,feetY,width,height,alpha=alpha)
    }
    fun heroKey(job: Job) = when(job) { Job.WARRIOR -> "warrior"; Job.MAGE -> "magehero"; Job.SUMMONER -> "summoner"; Job.THIEF -> "thief" }
    fun icon(c: Canvas,key: String,x: Float,y: Float,scale: Float=2f,color: Int=Ink.light) {
        c.save(); c.translate(x,y); c.scale(scale,scale); paint.color=color
        fun r(a: Float,b: Float,w: Float,h: Float) = c.drawRect(a,b,a+w,b+h,paint)
        when(key) {
            "sword", "knife" -> { r(7f,1f,2f,10f); r(5f,3f,2f,7f); r(3f,10f,10f,2f); r(7f,12f,2f,4f) }
            "shield" -> { r(2f,2f,12f,8f); r(4f,10f,8f,3f); r(6f,13f,4f,2f); paint.color=Ink.deep; r(7f,4f,2f,7f) }
            "bow" -> { r(3f,2f,2f,12f); r(5f,1f,4f,2f); r(5f,13f,4f,2f); r(9f,3f,2f,10f); r(1f,7f,14f,2f); r(12f,5f,2f,6f) }
            "fire" -> { r(6f,1f,3f,12f); r(3f,6f,9f,7f); r(1f,9f,13f,4f); r(4f,13f,7f,2f); paint.color=Ink.deep; r(6f,9f,3f,5f) }
            "ice" -> { r(7f,1f,2f,14f); r(1f,7f,14f,2f); r(3f,3f,3f,3f); r(10f,3f,3f,3f); r(3f,10f,3f,3f); r(10f,10f,3f,3f) }
            "limit-sword" -> { for(i in 0..2) { r(2f+i*5,2f+i,2f,10f); r(1f+i*5,11f+i,4f,2f) } }
            "limit-flare" -> { r(7f,0f,2f,16f); r(0f,7f,16f,2f); r(3f,3f,10f,10f); paint.color=Ink.deep; r(5f,5f,6f,6f); paint.color=color; r(7f,6f,2f,4f) }
            "limit-giants" -> { for(i in 0..4) { val xx=1f+i*3; val yy=if(i%2==0) 3f else 7f; r(xx,yy,2f,3f); r(xx-1,yy+3,4f,4f); r(xx,yy+7,2f,3f) } }
            "limit-vanish" -> { r(6f,1f,5f,3f); r(4f,4f,9f,6f); r(2f,10f,13f,3f); r(1f,14f,3f,1f); r(6f,13f,3f,2f); r(12f,14f,3f,1f); paint.color=Ink.deep; r(7f,5f,2f,3f) }
            "potion" -> { r(6f,1f,5f,3f); r(7f,4f,3f,2f); r(4f,6f,9f,8f); r(6f,14f,5f,1f); paint.color=Ink.deep; r(6f,7f,2f,4f) }
            "coin", "steal" -> { r(4f,1f,8f,2f); r(2f,3f,12f,10f); r(4f,13f,8f,2f); paint.color=Ink.deep; r(7f,4f,2f,8f) }
            "hourglass" -> { r(3f,1f,10f,2f); r(3f,13f,10f,2f); r(4f,3f,8f,2f); r(6f,5f,4f,5f); r(4f,10f,8f,3f) }
            "speed" -> { r(8f,1f,5f,3f); r(5f,4f,7f,3f); r(3f,7f,7f,3f); r(7f,10f,2f,4f); r(2f,13f,5f,2f) }
            "giant", "rabbit", "haniwa" -> { c.restore(); sprite(c,key,x+8*scale,y+14*scale,scale*.38f); return }
            "ghost", "clone" -> { r(4f,2f,8f,2f); r(2f,4f,12f,9f); r(2f,13f,3f,2f); r(7f,13f,3f,2f); r(12f,13f,2f,2f); paint.color=Ink.deep; r(5f,6f,2f,3f); r(10f,6f,2f,3f) }
            else -> { r(7f,0f,2f,16f); r(0f,7f,16f,2f); r(4f,4f,8f,8f); paint.color=Ink.deep; r(6f,6f,4f,4f) }
        }
        c.restore()
    }
    fun tree(c: Canvas,x: Float,y: Float,scale: Float=1f,color: Boolean=false) {
        c.save(); c.translate(x,y); c.scale(scale,scale)
        val pal=if(color) Ink.dawn else Ink.palette
        fun r(x: Float,y: Float,w: Float,h: Float,k: Int) { paint.color=pal[k]; c.drawRect(x,y,x+w,y+h,paint) }
        r(-12f,-90f,24f,100f,1); r(-6f,-85f,5f,98f,2); r(-18f,-20f,8f,35f,1); r(11f,-22f,9f,37f,1)
        for(i in 0..4) { r(-36f-i*13,-75f-i*12,32f,12f,1); r(10f+i*13,-80f-i*12,30f,12f,1) }
        for(i in 0..30) {
            val a=i*2.4; val xx=(cos(a)*sqrt(i.toDouble())*15).toFloat(); val yy=(-118+sin(a)*sqrt(i.toDouble())*9).toFloat()
            r(xx-19,yy-10,38f,20f,if(i%3==0) 2 else 1)
            if(i%3==0) { r(xx-14,yy-11,16f,3f,2); r(xx+9,yy+2,3f,2f,3) }
        }
        for(i in 0..6) { r(-44f+i*14,8f-(i%2)*4,18f,6f,1) }
        c.restore()
    }
}
