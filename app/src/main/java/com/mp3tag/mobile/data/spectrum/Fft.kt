package com.mp3tag.mobile.data.spectrum

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Iterative radix-2 FFT for real input. [size] must be a power of two. Not thread-safe. */
internal class Fft(private val size: Int) {

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    }

    private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { -sin(2.0 * PI * it / size) }
    private val bitReverse = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) table[i] = Integer.reverse(i) ushr (32 - bits)
    }
    private val re = DoubleArray(size)
    private val im = DoubleArray(size)

    /** Writes |X[k]|^2 for k in 0..size/2 of [input] (length [size]) into [power]. */
    fun power(input: DoubleArray, power: DoubleArray) {
        for (i in 0 until size) {
            re[bitReverse[i]] = input[i]
            im[bitReverse[i]] = 0.0
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val xr = re[j + half]
                    val xi = im[j + half]
                    val tr = xr * wr - xi * wi
                    val ti = xr * wi + xi * wr
                    re[j + half] = re[j] - tr
                    im[j + half] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += len
            }
            len = len shl 1
        }
        for (k in 0..size / 2) {
            power[k] = re[k] * re[k] + im[k] * im[k]
        }
    }
}
