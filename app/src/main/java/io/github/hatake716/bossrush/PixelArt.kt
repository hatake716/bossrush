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

class PixelArt {
    private val cache=mutableMapOf<String,Bitmap>()
    private val paint=Paint().apply { isFilterBitmap=false; isAntiAlias=false }
    fun sprite(c: Canvas,key: String,x: Float,y: Float,scale: Float=2f,variant: Int=0,color: Boolean=false,alpha: Int=255,flip: Boolean=false) {
        val b=cache.getOrPut("$key:$variant:$color") { make(key,variant,color) }
        paint.alpha=alpha
        c.save(); c.translate(x,y); if(flip) c.scale(-1f,1f)
        c.drawBitmap(b,null,RectF(-24*scale,-44*scale,24*scale,4*scale),paint); c.restore(); paint.alpha=255
    }
    private fun make(key: String,variant: Int,color: Boolean): Bitmap {
        val b=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888); val c=Canvas(b)
        val p=Paint(); val pal=if(color) Ink.dawn else Ink.palette
        fun r(x: Int,y: Int,w: Int,h: Int,k: Int) { p.color=pal[k]; c.drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),p) }
        fun poly(k: Int,vararg points: Int) { p.color=pal[k]; val path=Path(); path.moveTo(points[0].toFloat(),points[1].toFloat()); for(i in 2 until points.size step 2) path.lineTo(points[i].toFloat(),points[i+1].toFloat()); path.close(); c.drawPath(path,p) }
        fun eye(x: Int,y: Int) { r(x,y,4,3,3); r(x+1,y,2,2,0) }
        if(key in listOf("warrior","magehero","summoner","thief")) {
            // Hand-authored 16x24 sprites are drawn into the common 48x48 canvas.
            r(16,39,7,5,0); r(26,39,7,5,0); r(18,36,4,6,2); r(26,36,4,6,2)
            poly(0,14,23,31,23,35,38,12,38); poly(2,17,23,29,23,32,35,15,35)
            r(20,25,7,9,3); r(15,32,17,3,0); r(23,32,3,3,3)
            r(15,11,18,14,0); r(17,12,14,11,3); r(20,17,2,3,0); r(27,17,2,3,0); r(23,22,4,2,2)
            when(key) {
                "warrior" -> { r(15,10,17,5,2); r(19,7,9,4,2); r(19,9,3,5,3); r(13,13,4,7,2); r(31,13,4,7,2); r(35,19,3,17,3); r(32,30,9,3,0); r(9,26,9,12,0); r(10,27,7,8,2); r(12,28,3,8,3) }
                "magehero" -> { poly(0,9,15,21,0,28,10,38,15); poly(2,13,13,21,3,27,12,34,13); r(10,13,27,3,3); r(35,15,3,26,2); r(32,14,9,6,0); r(34,12,5,6,3); r(17,25,3,10,0) }
                "summoner" -> { r(15,9,18,8,2); r(18,8,11,4,3); r(20,8,2,7,0); r(27,8,2,7,0); r(11,9,4,6,3); r(33,9,4,6,3); r(34,24,7,12,0); r(35,25,5,9,3); r(36,27,3,1,2); r(36,30,3,1,2) }
                "thief" -> { poly(2,14,14,16,7,27,5,35,14); r(14,13,21,4,0); r(16,21,17,4,2); r(11,25,4,14,2); r(35,26,3,10,3); r(33,33,7,2,0); r(8,10,7,4,2) }
            }
            return b
        }
        when(key) {
            "squirrel" -> {
                poly(0,30,35,37,34,45,23,46,6,40,1,32,3,27,12,30,23)
                poly(2,32,31,38,29,42,19,42,7,36,6,31,13,34,23)
                r(35,9,4,13,3); r(31,24,8,6,1)
                poly(0,9,29,12,15,14,7,19,12,26,11,30,5,31,26,29,39,11,39)
                poly(2,13,28,14,17,20,15,27,16,28,28,25,36,13,36)
                r(16,10,3,7,3); r(27,10,2,6,3); r(16,28,10,10,3); eye(14,20); eye(24,20)
                r(20,24,3,3,0); r(9,36,9,5,0); r(23,36,8,5,0); r(11,34,6,4,2)
            }
            "wolf", "boar", "stag", "goat" -> {
                poly(0,4,30,6,22,19,18,34,20,40,14,43,5,39,3,33,12,29,9,23,13,22,20,10,23,2,20)
                poly(2,8,26,19,22,30,24,34,32,30,38,11,38,6,33)
                r(10,34,6,10,0); r(28,34,6,10,0); r(11,35,3,7,3); r(29,35,3,7,2)
                poly(0,23,10,23,3,30,9,36,7,43,15,43,26,36,32,25,27)
                poly(2,26,12,31,11,37,11,40,17,39,25,34,29,27,25)
                r(32,20,12,7,3); r(39,20,5,3,0); eye(28,16); r(33,28,4,3,3)
                for(i in 0..3) r(9+i*5,24+i%2*2,2,4,1)
                if(key=="boar") { r(21,13,4,12,3); r(17,17,4,10,2); r(38,25,3,8,3); r(35,30,5,3,3) }
                if(key=="stag") { r(25,1,2,14,3); r(35,1,2,11,3); r(20,3,5,2,3); r(21,0,2,4,3); r(37,4,6,2,3); r(41,1,2,5,3); r(28,4,5,2,2) }
                if(key=="goat") { r(24,2,4,12,3); r(34,2,4,10,3); r(30,29,5,9,3) }
                if(variant==19) { r(18,30,15,2,0); r(21,27,3,8,3) }
            }
            "raven", "eagle" -> {
                poly(0,21,19,13,13,2,5,1,19,6,30,17,35,23,42,30,33,43,28,47,11,35,18,28,17)
                poly(2,21,23,12,17,4,9,5,21,11,28,20,31,24,37,28,30,38,26,43,16,32,23)
                for(i in 0..3) { r(5+i*3,15+i*3,2,8,1); r(40-i*3,18+i*2,2,7,1) }
                r(19,13,12,17,0); r(21,14,9,14,2); r(23,13,9,5,3); eye(25,17)
                poly(3,30,19,39,23,29,24); r(20,37,3,7,3); r(28,36,3,8,3); r(17,42,8,2,2); r(26,42,8,2,2)
                if(key=="eagle") { r(20,13,10,5,3); r(19,17,3,8,3) }
            }
            "serpent", "dragon" -> {
                poly(0,4,39,2,28,8,17,18,14,25,18,30,12,30,4,36,7,42,4,47,13,44,26,34,30,25,23,15,22,9,29,11,35,28,35,34,30,38,33,37,42,10,45)
                poly(2,7,39,6,29,11,22,19,19,27,24,35,26,40,22,43,15,39,10,34,10,33,18,28,21,21,17,12,18,5,28,5,37)
                r(12,38,20,4,3); r(8,28,3,9,3); r(34,12,7,4,3); eye(35,16); r(40,21,6,3,3)
                for(i in 0..5) r(12+i*4,35+(i%2)*2,2,2,1)
                if(key=="dragon") { poly(1,21,24,10,3,1,6,3,21,15,31); poly(2,16,24,9,8,5,10,6,20); r(10,38,4,7,0); r(28,39,5,6,0) }
            }
            "rabbit" -> {
                r(14,5,6,18,0); r(27,3,6,19,0); r(15,6,4,15,3); r(28,4,4,17,3); r(16,8,2,10,2); r(29,6,2,12,2)
                poly(0,12,22,17,17,31,17,36,25,35,37,31,43,13,43,9,35)
                poly(3,14,22,18,20,29,20,33,26,32,36,28,40,14,40,12,34)
                r(17,25,2,4,0); r(28,25,2,4,0); r(23,31,3,2,2); r(6,33,7,7,3); r(12,40,9,4,2); r(28,40,8,4,2)
            }
            "haniwa" -> {
                poly(0,14,13,18,7,29,7,34,13,34,25,42,20,45,24,36,32,33,31,34,44,13,44,14,29,8,32,3,23,6,20,14,25)
                r(17,13,14,28,2); r(20,9,8,7,2); r(14,25,6,4,2); r(7,23,3,6,2); r(31,25,7,4,2); r(38,23,3,4,2)
                r(19,17,3,5,0); r(26,17,3,5,0); r(22,26,5,6,0); r(18,36,12,2,3); r(18,41,4,3,0); r(27,41,4,3,0)
            }
            else -> {
                // Distinct silhouettes and regalia for gods, giants, archers, sea rulers and mages.
                val large=key=="giant"||key=="firegiant"||key=="hammer"
                val left=if(large) 10 else 15; val right=if(large) 37 else 32
                poly(0,left,19,right,19,right+5,42,left-4,42)
                poly(2,left+3,22,right-3,22,right,39,left,39)
                r(17,39,6,7,0); r(27,39,7,7,0); r(17,39,4,5,3); r(28,39,4,5,2)
                r(16,8,18,16,0); r(18,10,14,12,3); r(18,9,14,4,2); eye(19,15); eye(27,15); r(24,20,4,2,2)
                r(left-3,22,7,12,0); r(left-2,24,5,8,2); r(right-3,22,7,11,0); r(right-2,24,5,8,2)
                r(left,33,right-left,3,0); r(23,32,4,5,3)
                r(20,25,2,7,3); r(29,25,2,7,3)
                when(key) {
                    "giant" -> { r(15,7,20,7,2); r(12,20,8,8,3); r(28,20,10,8,3); r(6,22,8,17,2); r(36,22,8,17,2); poly(0,23,26,28,26,26,31) }
                    "firegiant" -> { poly(3,17,11,13,2,21,6,25,0,29,7,36,1,32,13); r(40,6,3,34,3); poly(2,38,23,36,13,41,3,45,17,44,25); r(37,32,10,3,0); r(19,15,4,3,0) }
                    "archer" -> { poly(2,14,13,18,3,28,4,35,14); r(14,12,22,3,3); r(39,11,2,29,3); r(42,17,2,17,2); r(40,16,3,2,2); r(40,33,3,2,2); r(34,25,12,2,3) }
                    "queen" -> { r(17,5,15,7,2); r(17,2,3,8,3); r(24,1,3,8,3); r(30,2,3,8,3); poly(3,22,24,28,24,34,39,15,39); r(35,24,5,7,3); if(variant==17) { r(25,12,7,9,1); r(26,24,7,15,1) } }
                    "king" -> { r(16,5,18,5,3); r(16,1,4,6,3); r(24,1,3,6,3); r(31,1,3,6,3); r(20,22,11,5,2); r(38,7,3,34,3); r(35,7,9,4,3) }
                    "sea" -> { poly(2,15,12,12,4,20,7,25,2,29,8,37,4,34,15); r(36,10,3,32,3); r(32,9,3,9,3); r(41,9,3,9,3); r(33,17,10,3,3); for(i in 0..3) r(12+i*7,39+i%2*3,6,3,2) }
                    "mage" -> { poly(2,10,13,18,3,21,8,29,1,38,14); r(13,13,22,3,3); r(23,21,6,9,1); r(39,17,2,26,3); r(36,13,8,7,2); r(38,14,4,4,3) }
                    "knight" -> { r(16,6,18,9,2); r(14,6,5,3,3); r(31,6,6,3,3); r(16,13,18,3,0); r(24,6,3,15,3); r(39,8,3,29,3); r(35,30,10,3,0); if(variant==27) { r(33,26,7,8,0); r(31,26,5,4,2) } }
                    "hammer" -> { r(15,6,20,9,2); r(13,7,4,8,3); r(33,7,4,8,3); poly(2,17,19,33,19,31,29,24,33,19,28); r(39,15,3,27,2); r(33,9,14,12,0); r(34,10,12,9,3); r(36,12,3,5,2) }
                    "odin" -> { poly(0,12,15,19,3,29,3,37,16); poly(2,16,13,21,5,27,5,33,14); r(11,13,28,3,3); r(27,15,4,4,0); poly(3,19,21,32,21,28,33,24,38,21,29); r(39,8,2,37,3); poly(3,37,10,40,0,44,10); r(8,18,5,8,0); r(4,20,6,3,2) }
                }
            }
        }
        // Tiny individual runes distinguish shared creature families.
        if(variant>0) { r(22,35,2,2,3); if(variant%2==0) r(24,37,2,2,1) }
        return b
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
            "rest" -> { r(1f,5f,2f,10f); r(3f,9f,12f,4f); r(13f,8f,2f,7f); r(4f,6f,4f,3f); r(9f,7f,4f,2f) }
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
