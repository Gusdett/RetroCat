package com.retrocat.tamagotchi

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Sound effects as lists of (frequency Hz, duration ms). Frequency 0 is a rest. */
enum class Snd(val notes: List<Pair<Int, Int>>) {
    BLIP(listOf(988 to 40)),
    EAT(listOf(523 to 50, 659 to 50, 523 to 50, 659 to 50)),
    HAPPY(listOf(523 to 60, 659 to 60, 784 to 60, 1047 to 120)),
    SAD(listOf(392 to 120, 330 to 120, 262 to 220)),
    HATCH(listOf(330 to 80, 392 to 80, 494 to 80, 659 to 80, 0 to 40, 659 to 80, 784 to 160)),
    GROW(listOf(262 to 80, 330 to 80, 392 to 80, 523 to 80, 659 to 80, 784 to 200)),
    POOP(listOf(196 to 80, 147 to 120)),
    SICK(listOf(220 to 100, 208 to 100, 196 to 200)),
    DEATH(listOf(392 to 150, 370 to 150, 349 to 150, 330 to 150, 294 to 300)),
    MEOW(listOf(700 to 40, 850 to 40, 1000 to 50, 800 to 50, 600 to 60)),
    CATCH(listOf(1047 to 30, 1319 to 50)),
    MISS(listOf(196 to 100)),
    WIN(listOf(523 to 80, 659 to 80, 784 to 80, 1047 to 80, 784 to 80, 1047 to 200)),
}

/** A tiny square-wave "chiptune" synthesizer: no audio files needed. */
object Chip {
    private const val RATE = 22050

    @Volatile
    var enabled = true

    private val pool = Executors.newCachedThreadPool()
    private val cache = ConcurrentHashMap<Snd, ShortArray>()

    fun play(s: Snd) {
        if (!enabled) return
        pool.execute {
            try {
                val pcm = cache.getOrPut(s) { render(s) }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 80)
                track.release()
            } catch (_: Throwable) {
                // audio is a nice-to-have; never crash the game because of it
            }
        }
    }

    private fun render(s: Snd): ShortArray {
        val total = s.notes.sumOf { it.second * RATE / 1000 }
        val out = ShortArray(total)
        var pos = 0
        for ((freq, ms) in s.notes) {
            val n = ms * RATE / 1000
            for (i in 0 until n) {
                var v = 0.0
                if (freq > 0) {
                    val period = RATE.toDouble() / freq
                    v = if ((i % period) < period / 2) 1.0 else -1.0
                    if (i > n * 0.8) v *= (n - i) / (n * 0.2)   // short fade-out avoids clicks
                }
                out[pos++] = (v * 0.22 * 32767).toInt().toShort()
            }
        }
        return out
    }
}
