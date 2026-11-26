package com.retrocat.tamagotchi

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The title-screen tune: an original 16-bar loop (about 26 s) written as plain note text
 * and rendered by a tiny 4-channel software "sound chip":
 *   lead = 50% pulse, arpeggio = 25% pulse, bass = triangle, drums = noise + a pitch-dropping kick.
 */
object Song {
    private const val BPM = 150
    private const val STEPS_PER_BAR = 16          // 16th notes

    /** One chord per bar: C Am F G | C Am F G | F G Em Am | F G C G */
    private val chords = listOf("C", "Am", "F", "G", "C", "Am", "F", "G", "F", "G", "Em", "Am", "F", "G", "C", "G")

    /** Lead melody, one string per bar. "E5.2" = note E5 lasting 2 sixteenth-steps, "-.4" = rest. */
    private val lead = listOf(
        "E5.2 G5.2 C6.4 G5.2 E5.2 G5.4",
        "A5.2 E5.2 C5.2 E5.2 A5.4 G5.2 E5.2",
        "F5.2 A5.2 C6.4 A5.2 F5.2 A5.4",
        "G5.2 B5.2 D6.4 B5.2 G5.2 D5.2 B4.2",
        "E5.2 G5.2 C6.2 E6.2 D6.4 C6.2 G5.2",
        "C6.2 B5.2 A5.4 E5.2 A5.2 C6.4",
        "A5.2 C6.2 F6.4 E6.2 D6.2 C6.2 A5.2",
        "D6.4 B5.2 G5.2 D6.2 B5.2 G5.2 B5.2",
        "C6.4 A5.4 C6.4 F6.4",
        "B5.4 G5.4 B5.4 D6.4",
        "G5.2 B5.2 E6.4 D6.2 B5.2 G5.4",
        "A5.2 C6.2 E6.4 C6.2 A5.2 E5.4",
        "F5.2 A5.2 C6.2 F6.2 E6.2 C6.2 A5.2 C6.2",
        "D6.2 B5.2 G5.2 B5.2 D6.2 G6.2 F6.2 D6.2",
        "E6.4 C6.4 G5.4 E5.4",
        "G5.2 A5.2 B5.2 D6.2 G6.8",
    )

    private val semis = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    private fun midi(name: String): Int {
        var i = 1
        var s = semis.getValue(name[0])
        if (name[1] == '#') { s++; i++ }
        return 12 * (name.substring(i).toInt() + 1) + s
    }

    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun env(i: Int, len: Int, rate: Int): Float {
        val attack = minOf(1f, i / (0.004f * rate))
        val release = minOf(1f, (len - i) / (0.012f * rate))
        val decay = 1f - 0.3f * i / len
        return attack * release * decay
    }

    private fun tone(mix: FloatArray, rate: Int, start: Int, len: Int, freq: Double, duty: Double, vol: Float, triangle: Boolean = false) {
        var phase = 0.0
        val inc = freq / rate
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= mix.size) break
            val v = if (triangle) (4.0 * abs(phase - 0.5) - 1.0).toFloat() else if (phase < duty) 1f else -1f
            mix[idx] += v * vol * env(i, len, rate)
            phase += inc
            if (phase >= 1.0) phase -= 1.0
        }
    }

    private fun kick(mix: FloatArray, rate: Int, start: Int) {
        val len = (0.10 * rate).toInt()
        var phase = 0.0
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= mix.size) break
            val t = i.toDouble() / len
            phase += (150.0 - 105.0 * t) / rate
            mix[idx] += (sin(2 * PI * phase) * 0.38 * (1.0 - t).pow(1.5)).toFloat()
        }
    }

    private fun noise(mix: FloatArray, rate: Int, start: Int, seconds: Double, vol: Float, bright: Boolean, rnd: Random) {
        val len = (seconds * rate).toInt()
        var prev = 0f
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= mix.size) break
            val raw = rnd.nextFloat() * 2f - 1f
            val v = if (bright) (raw - prev) * 0.5f else raw
            prev = raw
            val f = 1f - i.toFloat() / len
            mix[idx] += v * vol * f * f
        }
    }

    fun render(rate: Int): ShortArray {
        val stepLen = rate * 60.0 / BPM / 4.0
        val total = (stepLen * chords.size * STEPS_PER_BAR).toInt()
        val mix = FloatArray(total)
        val rnd = Random(42)

        for (bar in chords.indices) {
            val barStart = bar * STEPS_PER_BAR

            // lead melody
            var s = barStart
            for (tok in lead[bar].split(" ")) {
                val parts = tok.split(".")
                val len = parts[1].toInt()
                if (parts[0] != "-") {
                    tone(mix, rate, (s * stepLen).toInt(), (len * stepLen * 0.92).toInt(), hz(midi(parts[0])), 0.5, 0.20f)
                }
                s += len
            }

            // chord tones: root, third, fifth
            val name = chords[bar]
            val root = semis.getValue(name[0])
            val third = if (name.endsWith("m")) 3 else 4
            val chordTones = intArrayOf(60 + root, 60 + root + third, 60 + root + 7)

            for (st in 0 until STEPS_PER_BAR) {
                val at = ((barStart + st) * stepLen).toInt()
                // arpeggio on every 16th note: root-third-fifth-third
                val n = chordTones[intArrayOf(0, 1, 2, 1)[st % 4]]
                tone(mix, rate, at, (stepLen * 0.85).toInt(), hz(n), 0.25, 0.07f)
                // bass on every 8th note: root, octave, root, octave...
                if (st % 2 == 0) {
                    val b = 36 + root + (if ((st / 2) % 2 == 0) 0 else 12)
                    tone(mix, rate, at, (stepLen * 1.7).toInt(), hz(b), 0.5, 0.22f, triangle = true)
                }
                // drums
                when {
                    st == 0 || st == 8 || (st == 10 && bar % 2 == 1) -> kick(mix, rate, at)
                    st == 4 || st == 12 -> noise(mix, rate, at, 0.09, 0.20f, false, rnd)
                    st % 2 == 0 -> noise(mix, rate, at, 0.025, 0.07f, true, rnd)
                }
            }
        }
        return ShortArray(total) { (mix[it].coerceIn(-1f, 1f) * 0.85f * 32767f).toInt().toShort() }
    }
}
