package io.github.hatake716.bossrush

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.*

/** Small authored dot sprites: dark silhouette, green body and a bright damage core. */
class EnemyBulletArt {
    private val p=Paint().apply { isAntiAlias=false }
    private val sprites=mapOf(
        BulletShape.SEED to listOf("..###..",".#####.","##+++##",".#+o+#.","..#+#..","...#..."),
        BulletShape.LEAF to listOf(".....#.","...###.","..#++#.",".#o+#..",".#+#...",".#....."),
        BulletShape.FANG to listOf(".#####.",".#+++#.","..#o+#.","..#+#..","...#...","...#..."),
        BulletShape.FEATHER to listOf("....#..","...###.","..#++#.",".#o+#..",".#+#...",".#....."),
        BulletShape.NEEDLE to listOf("...#...","..#+#..",".#+o+#.","..#+#..","..#+#..","...#...","...#..."),
        BulletShape.SUN to listOf("...#...",".#.+.#.","..###..","#+#o#+#","..###..",".#.+.#.","...#..."),
        BulletShape.MOON to listOf("..###..",".#++...","#+#....","#o#....","#+#....",".#++...","..###.."),
        BulletShape.SHARD to listOf("...#...","..#+#..",".#+o+#.",".#+++#.","..#+#..","...#..."),
        BulletShape.DROP to listOf("...#...","..#+#..",".#+++#.","#+o+++#",".#+++#.","..###.."),
        BulletShape.RUNE to listOf("...#...","..#+#..",".#+.+#.","#+.o.+#",".#+.+#.","..#+#..","...#..."),
        BulletShape.CHAIN to listOf(".###...","#+.+#..",".#o###.","..#+.+#","...###."),
        BulletShape.FLAME to listOf("...#...","..#+#..","#.#++#.","#+#+++#","#+o+++#",".#+++#.","..###.."),
        BulletShape.APPLE to listOf("...##..","...#...",".##.##.","#+++++#","#+o+++#",".#+++#.","..#.#.."),
        BulletShape.BLADE to listOf("...#...","..#+#..","..#o#..","..#+#..",".####..","...#...","..###.."),
        BulletShape.HAMMER to listOf(".#####.","#+o+++#",".#####.","...#...","..#+#..","...#..."),
        BulletShape.SPEAR to listOf("...#...","..#+#..",".#+o+#.","..###..","...#...","...#...","..###..")
    )
    fun draw(c: Canvas,e: GameEngine) {
        e.enemyVolley?.takeIf { it.started && e.elapsed<it.firstShot }?.let { v ->
            val u=((e.elapsed-v.readyAt)/v.warning).coerceIn(0.0,1.0)
            p.style=Paint.Style.STROKE; p.strokeWidth=2f; p.color=Ink.light
            val radius=(30-12*u).toFloat()
            c.drawRect(v.x.toFloat()-radius,v.y.toFloat()-radius,v.x.toFloat()+radius,v.y.toFloat()+radius,p)
            p.style=Paint.Style.FILL
            // A dotted preview shows the first directions without implying a floor hitbox.
            for(b in v.bullets(0)) for(distance in listOf(34.0,44.0,54.0)) {
                val x=(v.x+cos(b.heading)*distance).roundToInt().toFloat()
                val y=(v.y+sin(b.heading)*distance).roundToInt().toFloat()
                p.color=Ink.dark; c.drawRect(x-1,y-1,x+4,y+4,p)
                p.color=Ink.light; c.drawRect(x,y,x+2,y+2,p)
            }
        }
        for(b in e.enemyBullets) {
            val a=b.angle; val x=b.x.roundToInt().toFloat(); val y=b.y.roundToInt().toFloat()
            p.color=Ink.mid
            for(i in 1..3) {
                val tx=(x-cos(a)*i*4).roundToInt().toFloat(); val ty=(y-sin(a)*i*4).roundToInt().toFloat()
                c.drawRect(tx-1,ty-1,tx+1,ty+1,p)
            }
            c.save(); c.translate(x,y); c.rotate((a*180/PI+90).toFloat())
            val sprite=sprites.getValue(b.shape); val unit=1.6f
            for(row in sprite.indices) for(col in sprite[row].indices) {
                val pixel=sprite[row][col]; if(pixel=='.') continue
                p.color=when(pixel) { '#' -> Ink.dark; 'o' -> Ink.paper; else -> Ink.light }
                p.alpha=if(b.armed) 255 else 125
                val xx=(col-3)*unit; val yy=(row-sprite.size/2)*unit
                c.drawRect(xx-unit/2,yy-unit/2,xx+unit/2,yy+unit/2,p)
            }
            c.restore(); p.alpha=255
        }
    }
}
