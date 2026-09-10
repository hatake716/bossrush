package io.github.hatake716.bossrush

import android.content.res.AssetManager
import android.graphics.*

/** Draw actual 8x8 glyph tiles with nearest-neighbour scaling, including Japanese. */
class StoryText(assets: AssetManager) {
    val face: Typeface = Typeface.createFromAsset(assets,"fonts/misaki_gothic.ttf")
    private val ink=Paint().apply { typeface=face; textSize=8f; color=Color.WHITE; isAntiAlias=false; isSubpixelText=false }
    private val paint=Paint().apply { isFilterBitmap=false; isAntiAlias=false }
    private val glyphs=mutableMapOf<Char,Bitmap>()
    fun hasGlyph(ch: Char)=ink.hasGlyph(ch.toString())
    private fun glyph(ch: Char)=glyphs.getOrPut(ch) {
        Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawText(ch.toString(),0f,7f,ink)
        }
    }
    fun draw(c: Canvas,text: String,x: Float,y: Float,cell: Float=24f,color: Int=Ink.light) {
        paint.colorFilter=PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN)
        text.forEachIndexed { i,ch -> c.drawBitmap(glyph(ch),null,RectF(x+i*cell,y,x+(i+1)*cell,y+cell),paint) }
    }
    fun paragraph(c: Canvas,text: String,x: Float,y: Float,width: Float,cell: Float=24f,lineHeight: Float=32f): Int {
        val lines=StoryLayout.lines(text,(width/cell).toInt())
        lines.forEachIndexed { i,line -> draw(c,line,x,y+i*lineHeight,cell) }
        return lines.size
    }
}

/** Fixed-cell Japanese wrapping. Closing marks stay off the start of a line. */
object StoryLayout {
    private const val closing="、。，．！？）」』】〕〉》ー…"
    private const val opening="（「『【〔〈《"
    fun lines(text: String,columns: Int): List<String> {
        require(columns>=4)
        val result=mutableListOf<String>()
        for(paragraph in text.split('\n')) {
            var rest=paragraph
            while(rest.length>columns) {
                var end=columns
                while(end>1 && (rest[end] in closing || rest[end-1] in opening)) end--
                result.add(rest.take(end)); rest=rest.drop(end)
            }
            result.add(rest)
        }
        return result
    }
}
