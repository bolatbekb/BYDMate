package com.bydmate.app.ui.home.graphite

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A repeating texture tile: [argb] row by row, non-premultiplied, white or black at some alpha. */
class GrTile(val width: Int, val height: Int, val argb: IntArray)

/*
 * The card materials as small seamless tiles of light and dark specks at a few percent alpha,
 * repeated over the card. Pure and deterministic (fixed seeds): the same pixels on every start,
 * so screenshots compare. Built once each, lazily, by GraphiteCard.
 */
internal object GrTextures {
    const val SAND_FINE_MAX = 40
    const val METAL_MAX = 12 // ≈ 5 %
    const val CONCRETE_MAX = 20 // pores ≈ 8 %

    private const val WHITE = 0xFFFFFF

    private fun speck(alpha: Int, light: Boolean): Int = (alpha shl 24) or (if (light) WHITE else 0)

    /** Fine grey sand: every pixel a gaussian speck, light or dark, 160 × 160. */
    fun sandFine(): GrTile {
        val n = 160
        val rnd = Random(7)
        val px = IntArray(n * n) {
            val g = rnd.nextGaussian().toFloat()
            speck((abs(g) * 14f).toInt().coerceIn(0, SAND_FINE_MAX), g >= 0f)
        }
        return GrTile(n, n, px)
    }


    /**
     * Brushed metal: 1 px rows of random brightness (mostly very faint, a few brighter), each
     * swelling and fading along x so the streaks have length; 256 × 64, seamless both ways.
     */
    fun metal(): GrTile {
        val w = 256
        val h = 64
        val rnd = Random(21)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val strength = rnd.nextFloat().pow(3) * METAL_MAX
            val isLight = rnd.nextFloat() < 0.6f
            val k = 1 + rnd.nextInt(3)
            val phase = rnd.nextFloat() * 2f * PI.toFloat()
            for (x in 0 until w) {
                val m = 0.55f + 0.45f * sin(2f * PI.toFloat() * k * x / w + phase)
                val a = (strength * m + 0.5f).toInt().coerceIn(0, METAL_MAX)
                px[y * w + x] = speck(a, isLight)
            }
        }
        return GrTile(w, h, px)
    }

    /**
     * Concrete: soft low-frequency mottling (two octaves of value noise, 3–5 %) and sparse small
     * dark pores (≈ 8 %); 256 × 256, seamless.
     */
    fun concrete(): GrTile {
        val n = 256
        val rnd = Random(31)
        val coarse = lattice(8, rnd)
        val fine = lattice(16, rnd)
        val specks = Specks(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val v = (noise(coarse, 8, x, y, n) + 0.5f * noise(fine, 16, x, y, n)) / 1.5f
            val i = y * n + x
            specks.alpha[i] = (abs(v) * 13f).toInt().coerceIn(0, 13)
            specks.light[i] = v >= 0f
        }
        repeat(170) {
            val r = 0.6f + rnd.nextFloat() * 0.7f
            specks.pore(rnd.nextFloat() * n, rnd.nextFloat() * n, r, CONCRETE_MAX.toFloat())
        }
        return specks.tile()
    }

    /** A tile being painted: alpha and light-or-dark per pixel. */
    private class Specks(val w: Int, val h: Int) {
        val alpha = IntArray(w * h)
        val light = BooleanArray(w * h)

        /**
         * A dark round pore centred at ([cx], [cy]) with soft 1 px edges, wrapped around the tile;
         * it replaces whatever speck is under it.
         */
        fun pore(cx: Float, cy: Float, r: Float, a: Float) {
            val reach = (r + 1f).toInt() + 1
            for (dy in -reach..reach) for (dx in -reach..reach) {
                val x = floor(cx).toInt() + dx
                val y = floor(cy).toInt() + dy
                val d = sqrt((x + 0.5f - cx).pow(2) + (y + 0.5f - cy).pow(2))
                val v = (a * (r + 0.5f - d).coerceIn(0f, 1f)).toInt()
                if (v <= 0) continue
                val i = Math.floorMod(y, h) * w + Math.floorMod(x, w)
                alpha[i] = v
                light[i] = false
            }
        }

        fun tile() = GrTile(w, h, IntArray(w * h) { speck(alpha[it], light[it]) })
    }

    private fun lattice(cells: Int, rnd: Random) = FloatArray(cells * cells) { rnd.nextFloat() * 2f - 1f }

    /** Smooth value noise over a [cells]² lattice that wraps, sampled at pixel ([x], [y]) of an [n]² tile. */
    private fun noise(lat: FloatArray, cells: Int, x: Int, y: Int, n: Int): Float {
        val fx = (x + 0.5f) * cells / n
        val fy = (y + 0.5f) * cells / n
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = smooth(fx - x0)
        val ty = smooth(fy - y0)
        fun at(i: Int, j: Int) = lat[Math.floorMod(j, cells) * cells + Math.floorMod(i, cells)]
        val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * tx
        val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * tx
        return top + (bottom - top) * ty
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
}
