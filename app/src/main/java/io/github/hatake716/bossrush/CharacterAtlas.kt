package io.github.hatake716.bossrush

import android.content.res.AssetManager
import android.graphics.*
import android.util.LruCache
import kotlin.math.*

/** Bounded, sampled image decoding keeps the 39 original PNGs off the active heap. */
class CharacterAtlas(private val assets: AssetManager) {
    private val cache=object: LruCache<String,Bitmap>(6*1024*1024) {
        override fun sizeOf(key: String,value: Bitmap)=value.byteCount
    }
    private val paint=Paint().apply { isAntiAlias=false; isFilterBitmap=false }
    // The ending warms the monochrome character along with the restored world.
    private val dawn=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        0f,1.10f,0f,0f,12f,
        0f,.92f,0f,0f,13f,
        0f,.43f,0f,0f,43f,
        0f,0f,0f,1f,0f
    )))

    internal fun bitmap(key: String): Bitmap = cache.get(key) ?: decode(key).also { cache.put(key,it) }

    private fun decode(key: String): Bitmap {
        val path="sprites/$key.png"
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        assets.open(path).use { BitmapFactory.decodeStream(it,null,options) }
        require(options.outWidth>0 && options.outHeight>0) { "Invalid character image: $key" }
        options.inJustDecodeBounds=false
        options.inScaled=false
        options.inSampleSize=1
        options.inPreferredConfig=Bitmap.Config.ARGB_8888
        while(max(options.outWidth,options.outHeight)/options.inSampleSize>512) options.inSampleSize*=2
        val source=assets.open(path).use { requireNotNull(BitmapFactory.decodeStream(it,null,options)) }
        // Ignore nearly invisible outer pixels when fitting the sprite, retaining the
        // generated alpha in the cropped bitmap itself. No opaque backdrop is painted.
        val pixels=IntArray(source.width*source.height)
        source.getPixels(pixels,0,source.width,0,0,source.width,source.height)
        var left=source.width; var top=source.height; var right=-1; var bottom=-1
        for(y in 0 until source.height) for(x in 0 until source.width) {
            if(Color.alpha(pixels[y*source.width+x])>=48) {
                left=min(left,x); top=min(top,y); right=max(right,x); bottom=max(bottom,y)
            }
        }
        require(right>=left && bottom>=top) { "Empty character image: $key" }
        val cropped=Bitmap.createBitmap(source,left,top,right-left+1,bottom-top+1)
        if(cropped!==source) source.recycle()
        return cropped
    }

    fun draw(c: Canvas,key: String,x: Float,feetY: Float,width: Float,height: Float,
             color: Boolean=false,alpha: Int=255,flip: Boolean=false) {
        val bitmap=bitmap(key)
        val scale=min(width/bitmap.width,height/bitmap.height)
        val w=bitmap.width*scale; val h=bitmap.height*scale
        paint.alpha=alpha; paint.colorFilter=if(color) dawn else null
        c.save(); c.translate(x,feetY); if(flip) c.scale(-1f,1f)
        c.drawBitmap(bitmap,null,RectF(-w/2,-h,w/2,0f),paint)
        c.restore(); paint.alpha=255; paint.colorFilter=null
    }
}
