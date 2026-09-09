package io.github.hatake716.bossrush

import android.content.res.AssetManager
import android.graphics.*
import android.util.LruCache
import kotlin.math.max

/** Original full-size art stays in assets; only a few sampled scenes are kept in RAM. */
class BackgroundArt(private val assets: AssetManager) {
    private val cache=object: LruCache<String,Bitmap>(16*1024*1024) {
        override fun sizeOf(key: String,value: Bitmap)=value.byteCount
    }
    private val paint=Paint().apply { isAntiAlias=false; isFilterBitmap=false }
    private val shade=Paint()
    private val source=Rect()
    private val destination=RectF()
    // A fixed luminance range keeps pale telegraphs brighter than all scenery,
    // including snow, lightning and the sun. Generated texture remains visible.
    private val battlefield=palette(16f,29f,26f,111f,138f,91f)
    private val title=palette(10f,18f,16f,204f,224f,157f)
    private val titleShade=LinearGradient(0f,0f,580f,0f,
        intArrayOf(Color.argb(125,16,29,26),Color.argb(95,16,29,26),Color.TRANSPARENT),
        floatArrayOf(0f,.62f,1f),Shader.TileMode.CLAMP)

    private fun palette(r0: Float,g0: Float,b0: Float,r1: Float,g1: Float,b1: Float): ColorMatrixColorFilter {
        fun component(start: Float,end: Float)=floatArrayOf(
            .2126f*(end-start)/255f,.7152f*(end-start)/255f,.0722f*(end-start)/255f,0f,start)
        return ColorMatrixColorFilter(ColorMatrix(component(r0,r1)+component(g0,g1)+component(b0,b1)+floatArrayOf(0f,0f,0f,1f,0f)))
    }

    internal fun bitmap(key: String): Bitmap = cache.get(key) ?: run {
        val path="backgrounds/$key.png"
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        assets.open(path).use { BitmapFactory.decodeStream(it,null,options) }
        require(options.outWidth>0 && options.outHeight>0) { "Invalid background: $key" }
        options.inJustDecodeBounds=false; options.inScaled=false
        options.inPreferredConfig=Bitmap.Config.ARGB_8888; options.inSampleSize=1
        while(max(options.outWidth,options.outHeight)/options.inSampleSize>2048) options.inSampleSize*=2
        assets.open(path).use { requireNotNull(BitmapFactory.decodeStream(it,null,options)) }
            .also { cache.put(key,it) }
    }

    private fun cover(c: Canvas,key: String,left: Float,top: Float,width: Float,height: Float,filter: ColorFilter?) {
        val bitmap=bitmap(key)
        val ratio=max(width/bitmap.width,height/bitmap.height)
        val cropWidth=(width/ratio).toInt().coerceIn(1,bitmap.width)
        val cropHeight=(height/ratio).toInt().coerceIn(1,bitmap.height)
        val x=(bitmap.width-cropWidth)/2; val y=(bitmap.height-cropHeight)/2
        source.set(x,y,x+cropWidth,y+cropHeight)
        destination.set(left,top,left+width,top+height)
        paint.colorFilter=filter
        c.drawBitmap(bitmap,source,destination,paint)
        paint.colorFilter=null
    }

    fun battle(c: Canvas,bossId: String,left: Float=0f,top: Float=0f,width: Float=600f,height: Float=334f) {
        fullImage(c,"battle_$bossId",left,top,width,height,battlefield)
    }

    fun portrait(c: Canvas,bossId: String,left: Float,top: Float,width: Float,height: Float) {
        cover(c,"battle_$bossId",left,top,width,height,battlefield)
    }

    private fun fullImage(c: Canvas,key: String,left: Float,top: Float,width: Float,height: Float,filter: ColorFilter?) {
        destination.set(left,top,left+width,top+height)
        paint.colorFilter=filter
        c.drawBitmap(bitmap(key),null,destination,paint)
        paint.colorFilter=null
    }

    fun landscape(c: Canvas,color: Boolean=false,left: Float=0f,top: Float=57f,width: Float=960f,height: Float=483f) {
        // One composition: the title withholds its color; the ending reveals it.
        // Flexible scenery fills the display; characters and UI use a uniform scale.
        fullImage(c,"worldtree",left,top,width,height,if(color) null else title)
        shade.shader=titleShade
        c.drawRect(left,top,580f,top+height,shade)
        shade.shader=null
    }
}
