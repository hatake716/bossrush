package io.github.hatake716.bossrush

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import kotlin.math.*

/** Hand-drawn pixel murals: stone relief, interlaced roots, nine worlds and each god's insignia.
 * Everything is painted on a 480 x 132 integer grid without filtering or antialiasing.
 * The limited palette is derived from the very same colors as the boss's ultimate.
 */
class MythicCutin {
    private val cache=object: LruCache<String,Bitmap>(4*480*132*4) {
        override fun sizeOf(key: String,value: Bitmap)=value.byteCount
    }
    private val paint=Paint().apply { isAntiAlias=false; isFilterBitmap=false }
    internal fun bitmap(id: String): Bitmap=cache.get(id) ?: Mural(id).draw().also { cache.put(id,it) }
    fun draw(c: Canvas,id: String,left: Float,top: Float,width: Float,height: Float,age: Double) {
        c.drawBitmap(bitmap(id),null,RectF(left,top,left+width,top+height),paint)
        // A few discrete runic embers; the architecture itself stays still and sharp.
        val colors=UltimateColors.forBoss(id)
        val scale=height/132f
        val tick=(age*7).toInt()
        paint.color=colors.core
        for(i in 0..13) {
            if((i+tick)%5<2) continue
            val x=left+(19+i*67).mod(480)/480f*width
            val y=top+((i*31-tick).mod(104)+14)*scale
            c.drawRect(x,y,x+scale,y+scale,paint)
        }
    }

    private class Mural(private val id: String) {
        private val colors=UltimateColors.forBoss(id)
        private val bitmap=Bitmap.createBitmap(480,132,Bitmap.Config.ARGB_8888)
        private val c=Canvas(bitmap)
        private val p=Paint().apply { isAntiAlias=false; isFilterBitmap=false }
        private val ink=colors.shadow
        private val night=mix(ink,colors.energy,.12)
        private val far=mix(ink,colors.energy,.24)
        private val stone=mix(ink,colors.energy,.35)
        private val edge=mix(ink,colors.energy,.56)
        private val light=colors.energy
        private val gold=colors.accent
        private val pale=colors.core
        private fun rect(x: Int,y: Int,w: Int,h: Int,color: Int) {
            p.color=color; c.drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),p)
        }
        private fun dot(x: Int,y: Int,color: Int)=rect(x,y,1,1,color)
        private fun line(x0: Int,y0: Int,x1: Int,y1: Int,color: Int,width: Int=1) {
            var x=x0; var y=y0; val dx=abs(x1-x0); val sx=if(x0<x1) 1 else -1
            val dy=-abs(y1-y0); val sy=if(y0<y1) 1 else -1; var error=dx+dy
            while(true) { rect(x,y,width,width,color); if(x==x1 && y==y1) break
                val e=error*2; if(e>=dy) { error+=dy; x+=sx }; if(e<=dx) { error+=dx; y+=sy }
            }
        }
        private fun ellipse(cx: Int,cy: Int,rx: Int,ry: Int,color: Int,fill: Boolean=false) {
            for(y in -ry..ry) for(x in -rx..rx) {
                val d=x*x.toDouble()/(rx*rx)+y*y.toDouble()/(ry*ry)
                if(d<=1 && (fill || d>=1-2.6/min(rx,ry))) dot(cx+x,cy+y,color)
            }
        }
        private fun rune(x: Int,y: Int,n: Int,color: Int,size: Int=1) {
            line(x,y,x,y+6*size,color,size)
            when(n.mod(8)) {
                0 -> { line(x,y,x+3*size,y+2*size,color,size); line(x+3*size,y+2*size,x,y+4*size,color,size) }
                1 -> { line(x-2*size,y+2*size,x,y,color,size); line(x,y,x+2*size,y+2*size,color,size) }
                2 -> { line(x,y+2*size,x+3*size,y,color,size); line(x,y+4*size,x+3*size,y+2*size,color,size) }
                3 -> { line(x-2*size,y,x+2*size,y+6*size,color,size); line(x+2*size,y,x-2*size,y+6*size,color,size) }
                4 -> { line(x-2*size,y,x,y+3*size,color,size); line(x+2*size,y,x,y+3*size,color,size) }
                5 -> { line(x,y,x+3*size,y+3*size,color,size); line(x+3*size,y+3*size,x,y+6*size,color,size) }
                6 -> { line(x-2*size,y+2*size,x+2*size,y+4*size,color,size); line(x+2*size,y+2*size,x-2*size,y+4*size,color,size) }
                else -> { line(x,y+2*size,x+3*size,y+4*size,color,size); line(x+3*size,y+4*size,x+3*size,y,color,size) }
            }
        }
        fun draw(): Bitmap {
            bitmap.eraseColor(night)
            // Discrete sky bands, sparse dithering and distant mountains, never a smooth gradient.
            for(y in 14..109) for(x in 10..469) {
                if((x*13+y*7)%47==0) dot(x,y,if(y<61) far else ink)
            }
            landscape()
            // Yggdrasil: fluted trunk, reaching branches and roots braided into the stone floor.
            tree(98,107,70)
            tree(428,107,44)
            for(i in 0..8) {
                val a=-PI+i*PI/8; val x=112+(cos(a)*77).toInt(); val y=100+(sin(a)*76).toInt()
                ellipse(x,y,4,4,edge,true); ellipse(x,y,2,2,light)
                if(i>0) line(x,y,x+7,y+2,stone)
            }
            // A vaulted hall with individually cut voussoirs and carved stone pillars.
            for(x in listOf(12,213,457)) pillar(x)
            for(i in 0..13) {
                val a=PI+i*PI/13; val x=115+(cos(a)*96).toInt(); val y=90+(sin(a)*76).toInt()
                rect(x-5,y-3,11,5,stone); rect(x-5,y-3,10,1,edge); dot(x+5,y+1,ink)
            }
            // Raised perimeter knotwork, set above the scene.
            for(y in listOf(2,121)) {
                rect(0,y,480,9,ink); rect(0,y,480,1,edge); rect(0,y+8,480,1,stone)
                for(x in 0 until 480 step 12) {
                    line(x,y+2,x+6,y+6,gold); line(x+6,y+6,x+12,y+2,gold)
                    line(x,y+6,x+6,y+2,stone); line(x+6,y+2,x+12,y+6,stone)
                    dot(x+6,y+2,pale)
                }
            }
            for(i in 0..30) rune(24+i*14,112,i+Bosses.all.indexOfFirst { it.id==id },edge)
            // Upper lintel records the particular deity. The emblem remains visible above the portrait.
            ellipse(115,39,26,25,ink,true); ellipse(115,39,25,24,gold); ellipse(115,39,22,21,stone)
            emblem(115,40)
            // Inscription slab: shadowed relief leaves the title lettering legible.
            rect(236,33,210,70,ink); rect(237,34,208,1,stone); rect(237,102,208,1,stone)
            for(x in listOf(239,437)) for(y in listOf(37,96)) {
                rect(x,y,6,3,edge); dot(x+1,y,gold); dot(x+5,y+2,night)
            }
            for(i in 0..12) rune(252+i*14,16,i+id.length,edge)
            // Small repeated reliefs at the far right make each background distinct even behind a large boss.
            for(i in 0..3) {
                val x=264+i*51
                ellipse(x,111,8,3,stone); dot(x,109,light)
            }
            return bitmap
        }
        private fun landscape() {
            val index=Bosses.all.indexOfFirst { it.id==id }
            // Jagged ridgelines with stepped strata, rather than repeated triangle silhouettes.
            val ridges=intArrayOf(77,70,59,63,44,49,62,53,67,71,51,37,45,60,65,53,64,79,74,81,65,75,68,81,89)
            for(layer in 0..2) for(x in 20..456) {
                val xx=(x+index*7+layer*19).mod(436)
                val k=(xx/19).coerceAtMost(ridges.size-2); val f=xx%19
                val peak=(ridges[k]*(19-f)+ridges[k+1]*f)/19+layer*14
                val tone=if(layer==0) far else if(layer==1) night else ink
                if(peak<109) rect(x,peak,1,109-peak,tone)
                if(x%3!=0 && peak<105) dot(x,peak,if(layer==0) stone else far)
                if(x%11<2 && peak<100) line(x,peak+2,x-4,peak+10,if(layer==0) stone else far)
            }
            when(index/4) {
                0,1 -> {
                    for(i in 0..6) {
                        val x=35+i*28; val y=67+(i*11)%25
                        tree(x,108,109-y)
                        for(j in 0..11) { val xx=x+(j*13)%29-14; val yy=y+(j*7)%17
                            rect(xx,yy,4,2,far); line(xx,yy,xx+2,yy,edge) }
                    }
                }
                2 -> {
                    for(i in 0..4) for(x in 23..204) {
                        val y=35+(sin(x*.035+i*.6)*12).toInt()+i*3
                        if((x+i)%3!=0) dot(x,y,if(i%2==0) edge else far)
                    }
                    for(i in 0..4) { val x=31+i*38; line(x,96,x+10,68+i%3*6,stone)
                        line(x+10,68+i%3*6,x+17,94,edge) }
                }
                3 -> {
                    for(row in 0..6) for(i in 0..11) {
                        val x=24+i*17+(row%2)*7; val y=71+row*6
                        line(x,y,x+8,y,if(row%2==0) stone else far); line(x+8,y,x+11,y-2,edge)
                    }
                    ship(62,68)
                    for(i in 0..7) rect(25+i*25,109,18,1,stone)
                }
                4 -> {
                    for(i in 0..4) {
                        val x=36+i*38
                        for(y in 28..85 step 7) ellipse(x+(sin(y*.06+i)*3).toInt(),y,3,4,stone)
                        rect(x-7,98,15,11,far); rect(x-6,96,13,2,edge)
                        line(x-5,96,x-2,89,stone)
                    }
                    for(i in 0..7) line(26+i*24,108,49+i*20,84-i%3*5,stone)
                }
                5 -> {
                    for(i in 0..6) {
                        val x=25+i*28
                        rect(x,67+i%3*9,15,38-i%3*9,far); line(x,66+i%3*9,x+13,66+i%3*9,edge)
                        for(y in 73+i%3*9..106 step 8) { line(x,y,x+14,y,night); dot(x+4,y+2,stone) }
                    }
                    if(id=="surtr") for(i in 0..4) {
                        val x=35+i*36
                        for(j in 0..7) line(x+j,109,x+j*2,98-j%3*6,if(j%3==0) gold else stone)
                    }
                }
                6 -> {
                    for(i in 0..5) { val x=31+i*31; tree(x,109,37+i%2*11)
                        for(j in 0..7) { val xx=x+(j*11)%25-12; val yy=64+(j*13+i*7)%25
                            rect(xx,yy,3,3,if(j%3==0) gold else edge); dot(xx,yy,pale) }
                    }
                    for(i in 0..6) { line(25,91+i*3,204,91+i*3,far); dot(39+i*23,96,gold) }
                }
                else -> {
                    for(i in 0..5) {
                        val x=35+i*29; val y=53+i%3*7
                        rect(x,y,13,52,far); line(x-2,y,x+6,y-12,edge); line(x+6,y-12,x+15,y,stone)
                        for(j in 0..3) { rect(x+3,y+7+j*10,3,5,ink); dot(x+3,y+7+j*10,edge) }
                    }
                    for(i in 0..5) line(25,107-i*2,203,84-i*2,if(i%3==0) edge else far)
                }
            }
        }
        private fun pillar(x: Int) {
            rect(x,27,14,82,ink); rect(x+1,28,11,80,stone)
            rect(x+2,30,2,75,edge); rect(x+10,30,2,77,far)
            for(y in 33..99 step 13) {
                line(x+1,y,x+12,y,ink); line(x+4,y+1,x+9,y+1,edge)
                rune(x+6,y+3,y/13,night)
            }
            for(k in 0..2) { rect(x-3+k,22+k*2,20-k*2,2,if(k%2==0) edge else stone)
                rect(x-k,108+k*2,14+k*2,2,if(k%2==0) edge else stone) }
        }
        private fun tree(x: Int,y: Int,h: Int) {
            for(i in 0..h) {
                val xx=x+(sin(i*.09)*3).toInt(); val width=3+(h-i)/12
                rect(xx-width/2,y-i,width,1,stone); dot(xx-width/2,y-i,edge)
            }
            for(i in 0..6) {
                val start=y-h/3-i*6; val dir=if(i%2==0) -1 else 1
                val end=x+dir*(21+i*3); val yy=start-13-i*2
                line(x,start,end,yy,stone,3); line(x,start-1,end,yy-1,edge)
                line(end,yy,end+dir*9,yy-12,edge); line(end,yy,end-dir*6,yy-13,edge)
                for(j in 0..7) { val lx=end+(j*7%23)-11; val ly=yy-8+(j*11%13)
                    rect(lx,ly,3,2,if(j%3==0) gold else far); dot(lx,ly,edge) }
            }
            for(dir in listOf(-1,1)) for(i in 0..2) {
                line(x,y-6,x+dir*(12+i*9),y+2+i,stone,2)
                line(x+dir*(12+i*9),y+2+i,x+dir*(29+i*9),y+1,edge)
            }
        }
        private fun feather(x: Int,y: Int,dir: Int) {
            line(x,y,x+dir*19,y-12,gold,2)
            for(i in 0..7) { line(x+dir*(i*2),y-i,x+dir*(i*2+3),y-i-9,light)
                line(x+dir*(i*2),y-i,x+dir*(i*2+7),y-i+1,edge) }
        }
        private fun antlers(x: Int,y: Int) {
            for(d in listOf(-1,1)) { line(x,y+6,x+d*8,y-3,gold,2); line(x+d*8,y-3,x+d*11,y-17,light)
                for(i in 0..2) line(x+d*(8+i),y-i*5,x+d*(14+i*2),y-i*5-5,gold) }
        }
        private fun moon(x: Int,y: Int) { ellipse(x,y,14,14,light,true); ellipse(x+7,y-4,13,13,ink,true); ellipse(x,y,16,16,edge) }
        private fun sun(x: Int,y: Int) { ellipse(x,y,10,10,gold,true); ellipse(x-2,y-2,7,7,light,true)
            for(i in 0..11) { val a=i*PI/6; line(x+(cos(a)*13).toInt(),y+(sin(a)*13).toInt(),x+(cos(a)*19).toInt(),y+(sin(a)*19).toInt(),gold) } }
        private fun sword(x: Int,y: Int) { line(x,y-20,x,y+18,gold,3); line(x-1,y-18,x-1,y+3,pale)
            line(x-9,y+5,x+10,y+5,light,2); rect(x-2,y+17,6,3,light) }
        private fun hammer(x: Int,y: Int) { rect(x-2,y-4,5,22,gold); rect(x-13,y-13,27,13,light)
            rect(x-11,y-11,22,2,pale); rect(x-11,y-2,22,2,edge); rune(x-1,y-10,1,ink) }
        private fun wave(x: Int,y: Int,color: Int) { for(i in 0..38) dot(x+i,y+(sin(i*.33)*3).toInt(),color) }
        private fun ship(x: Int,y: Int) { line(x-18,y+7,x-10,y+13,gold,2); line(x-10,y+13,x+12,y+13,gold,2)
            line(x+12,y+13,x+19,y+4,light,2); line(x,y-19,x,y+9,light)
            for(i in 0..20) line(x+2,y-17+i,x+14-i/2,y-17+i,if(i%4<2) gold else edge)
            wave(x-19,y+17,light) }
        private fun wolf(x: Int,y: Int) { line(x-13,y+10,x-10,y-16,gold,2); line(x-10,y-16,x-3,y-5,light,2)
            line(x+13,y+10,x+10,y-16,gold,2); line(x+10,y-16,x+3,y-5,light,2)
            line(x-12,y+8,x,y+18,gold,2); line(x+12,y+8,x,y+18,gold,2)
            line(x-9,y,x-3,y+3,pale,2); line(x+9,y,x+3,y+3,pale,2); rect(x-2,y+10,5,3,light) }
        private fun serpent(x: Int,y: Int) { ellipse(x,y,17,16,gold); ellipse(x,y,15,14,light)
            for(i in 0..10) { val a=i*.55; dot(x+(cos(a)*16).toInt(),y+(sin(a)*15).toInt(),ink) }
            rect(x+9,y-12,9,5,gold); dot(x+15,y-11,pale); line(x+16,y-7,x+20,y-3,light) }
        private fun flames(x: Int,y: Int) { for(i in -3..3) { val h=13+(i*i*3)%17
            line(x+i*4,y+14,x+i*3,y+14-h,gold,3); line(x+i*3,y+13,x+i*3+2,y+20-h,light) } }
        private fun gate(x: Int,y: Int) { for(d in listOf(-1,1)) { rect(x+d*13-3,y-16,6,35,edge); rect(x+d*13-2,y-16,1,35,light) }
            for(i in 0..4) line(x-14+i*3,y-17-i,x+14-i*3,y-17-i,gold)
            line(x-16,y+18,x+16,y+18,light) }
        private fun apple(x: Int,y: Int) { ellipse(x-5,y+2,9,12,light,true); ellipse(x+5,y+2,9,12,gold,true)
            line(x,y-8,x+2,y-17,gold,2); line(x+3,y-14,x+12,y-18,light,2); rect(x-10,y-2,3,5,pale) }
        private fun emblem(x: Int,y: Int) {
            when(id) {
                "ratatoskr" -> {
                    ellipse(x-9,y+1,10,15,gold,true); ellipse(x-11,y-1,6,9,ink,true)
                    ellipse(x+3,y+7,7,10,light,true); ellipse(x+8,y-4,6,6,gold,true)
                    line(x+5,y-7,x+6,y-14,light,2); dot(x+10,y-5,pale)
                    line(x-14,y+18,x+17,y+18,gold); rect(x+10,y+4,7,6,edge); rect(x+9,y+3,9,2,pale)
                }
                "dainn" -> { antlers(x,y+1); rune(x,y+4,4,light,2) }
                "gullinbursti" -> { sun(x,y); line(x-14,y+5,x-8,y+14,pale,2); line(x+14,y+5,x+8,y+14,pale,2) }
                "hugin" -> { feather(x,y+8,-1); feather(x,y+8,1); rect(x-1,y,3,13,pale) }
                "munin" -> { moon(x,y); feather(x+3,y+10,-1) }
                "tanngrisnir" -> { antlers(x,y); lightning(x,y+3) }
                "skoll" -> { sun(x,y); rect(x-4,y-2,3,3,ink); rect(x+3,y-2,3,3,ink) }
                "hati" -> { moon(x,y); line(x-16,y+14,x+12,y+14,gold) }
                "hraesvelgr" -> { feather(x,y,-1); feather(x,y,1); wave(x-19,y+12,light) }
                "thjazi" -> { feather(x,y-2,-1); feather(x,y-2,1); rune(x,y+5,3,light,2) }
                "skadi" -> { antlers(x,y); line(x-14,y+15,x+14,y-13,pale); rune(x-1,y+5,3,gold) }
                "ullr" -> { ellipse(x-5,y,16,20,light); rect(x-24,y-22,19,45,ink); line(x-4,y-20,x-4,y+20,gold); line(x-14,y,x+19,y,pale); line(x+14,y-4,x+19,y,pale) }
                "aegir" -> { for(i in 0..3) wave(x-19,y-14+i*9,if(i%2==0) light else gold) }
                "ran" -> { for(i in -2..2) { line(x-18,y+i*7,x+18,y+i*7-9,gold); line(x+i*7,y-19,x+i*7+7,y+17,light) } }
                "njord" -> ship(x,y)
                "jormungandr" -> serpent(x,y)
                "garmr" -> { gate(x,y); wolf(x,y) }
                "hel" -> { moon(x,y); rune(x+6,y-10,5,gold,2); line(x,y-19,x,y+19,light) }
                "nidhogg" -> { serpent(x,y); line(x-16,y+18,x+16,y+18,gold); feather(x,y,1) }
                "fenrir" -> { wolf(x,y); for(d in listOf(-1,1)) for(i in 0..2) ellipse(x+d*(13+i*3),y+6+i*3,3,2,light) }
                "hrungnir" -> { line(x,y-19,x-18,y+15,gold,2); line(x-18,y+15,x+18,y+15,light,2); line(x+18,y+15,x,y-19,gold,2); rune(x,y-5,5,light,2) }
                "thrym" -> { hammer(x,y); rune(x-18,y-4,3,gold) }
                "utgardloki" -> { gate(x,y); ellipse(x,y,8,12,light); rune(x,y-5,3,gold) }
                "surtr" -> { flames(x,y); sword(x,y) }
                "idunn" -> apple(x,y)
                "freyr" -> { antlers(x,y); sword(x,y+1) }
                "freyja" -> { ellipse(x,y,17,16,gold); for(i in 0..4) { val a=i*PI/2.5; ellipse(x+(cos(a)*15).toInt(),y+(sin(a)*14).toInt(),3,3,light,true) }; rune(x,y-7,6,pale,2) }
                "tyr" -> { sword(x,y); for(i in -2..2) ellipse(x+i*7,y+11,4,2,gold) }
                "heimdall" -> { for(i in 0..4) ellipse(x,y+17,19-i*3,32-i*3,if(i%2==0) gold else light)
                    rect(x-21,y+10,43,14,ink); line(x-14,y+4,x+14,y-5,pale,3); line(x+14,y-10,x+14,y+1,gold,2) }
                "loki" -> { flames(x,y); ellipse(x,y,11,15,ink,true); line(x-8,y-3,x-2,y,light,2); line(x+8,y-3,x+2,y,gold,2); line(x-5,y+8,x+5,y+5,light) }
                "thor" -> { lightning(x-14,y-10); lightning(x+13,y+1); hammer(x,y) }
                "odin" -> { feather(x,y+6,-1); feather(x,y+6,1); sword(x,y); ellipse(x,y-10,7,4,gold); dot(x,y-10,pale) }
            }
        }
        private fun lightning(x: Int,y: Int) { line(x+3,y-7,x-3,y+1,light,2); line(x-3,y+1,x+3,y+1,pale,2); line(x+3,y+1,x-3,y+11,light,2) }
        private fun mix(a: Int,b: Int,t: Double): Int {
            fun channel(shift: Int)=(((a shr shift and 255)*(1-t)+(b shr shift and 255)*t).toInt() shl shift)
            return 0xff000000.toInt() or channel(16) or channel(8) or channel(0)
        }
    }
}
