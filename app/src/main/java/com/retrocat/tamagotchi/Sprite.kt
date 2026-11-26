package com.retrocat.tamagotchi

import android.graphics.Canvas
import android.graphics.Paint

/** Face indices inside [CatSet.faces]. */
object Face {
    const val NEUTRAL = 0
    const val BLINK = 1
    const val HAPPY = 2
    const val SAD = 3
    const val SLEEP = 4
    const val EAT = 5
    const val SICK = 6
}

/**
 * A tiny pixel image. Each pixel is a palette index 0..3 or -1 (transparent).
 * Characters in the source art: '*' = 0, '+' = 1, 'o' = 2, '#' = 3, '.' = transparent.
 */
class Sprite(val w: Int, val h: Int, private val px: ByteArray) {

    /** Draws at logical (x, y). [s] enlarges every pixel to s x s logical pixels. */
    fun draw(c: Canvas, x: Int, y: Int, p: Paint, pal: IntArray, flip: Boolean = false, s: Int = 1) {
        for (row in 0 until h) {
            var col = 0
            while (col < w) {
                val v = px[row * w + col].toInt()
                if (v < 0) {
                    col++
                    continue
                }
                var end = col + 1
                while (end < w && px[row * w + end].toInt() == v) end++
                p.color = pal[v]
                val x0 = if (flip) w - end else col
                val left = (x + x0 * s).toFloat()
                val top = (y + row * s).toFloat()
                c.drawRect(left, top, left + (end - col) * s, top + s, p)
                col = end
            }
        }
    }

    companion object {
        fun of(vararg rows: String): Sprite {
            val h = rows.size
            val w = rows[0].length
            val data = ByteArray(w * h)
            for (y in 0 until h) {
                require(rows[y].length == w) { "Sprite row $y has width ${rows[y].length}, expected $w" }
                for (x in 0 until w) {
                    data[y * w + x] = when (rows[y][x]) {
                        '*' -> 0
                        '+' -> 1
                        'o' -> 2
                        '#' -> 3
                        else -> -1
                    }
                }
            }
            return Sprite(w, h, data)
        }
    }
}

/** A cat built from layers: base body, two tail frames and one sprite per facial expression. */
class CatSet(val base: Sprite, val tailA: Sprite, val tailB: Sprite, val faces: Array<Sprite>)
