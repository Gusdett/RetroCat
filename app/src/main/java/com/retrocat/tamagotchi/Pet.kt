package com.retrocat.tamagotchi

import android.content.SharedPreferences
import kotlin.math.max
import kotlin.math.min

enum class Stage { EGG, KITTEN, CAT, DEAD }

enum class PetEvent { HATCHED, GREW, POOPED, SICK, DIED, WOKE }

class Alert(val code: String, val delayMs: Long)

enum class Res { OK, FULL, ASLEEP, NOT_SICK, NOT_TIRED, TOO_TIRED, SICK, NO_POOP, UNAVAILABLE }

/**
 * Pure game state of the pet. All time is counted in real seconds.
 * Stats run 0..100 (hunger = fullness: 100 is full, 0 is starving).
 */
class Pet {

    companion object {
        const val EGG_SEC = 60f              // egg hatches after 1 minute
        const val KITTEN_SEC = 20 * 60f      // kitten grows up after 20 minutes
        const val POOP_EVERY = 240f          // one poop every 4 awake minutes
        const val STARVE_DEATH = 20 * 60f    // 20 minutes at zero food
        const val SICK_DEATH = 30 * 60f      // 30 minutes sick without medicine
        const val OFFLINE_RATE = 0.5f        // time passes at half speed while the app is closed
        const val OFFLINE_MAX = 3 * 3600f    // and at most 3 hours are simulated
        const val COATS = 5                  // number of fur colours
    }

    var stage = Stage.EGG
    var stageSec = 0f
    var totalSec = 0f
    var hunger = 80f
    var happy = 80f
    var energy = 100f
    var poops = 0
    var sick = false
    var sleeping = false
    var soundOn = true
    var name = "MIMI"
    var coat = 0
    var adopted = false          // false = the adoption screen is shown, the egg's clock is paused

    private var poopTimer = 0f
    private var dirtySec = 0f
    private var starveSec = 0f
    private var sickSec = 0f

    var onEvent: ((PetEvent) -> Unit)? = null

    val alive get() = stage == Stage.KITTEN || stage == Stage.CAT

    private fun emit(e: PetEvent) {
        onEvent?.invoke(e)
    }

    fun tick(dt: Float, canDie: Boolean = true) {
        if (!adopted || stage == Stage.DEAD) return
        totalSec += dt
        stageSec += dt

        if (stage == Stage.EGG) {
            if (stageSec >= EGG_SEC) {
                stage = Stage.KITTEN
                stageSec = 0f
                hunger = 70f
                happy = 80f
                energy = 100f
                emit(PetEvent.HATCHED)
            }
            return
        }

        if (stage == Stage.KITTEN && stageSec >= KITTEN_SEC) {
            stage = Stage.CAT
            stageSec = 0f
            emit(PetEvent.GREW)
        }

        if (sleeping) {
            energy += dt / 6f
            hunger -= dt / 90f
            if (energy >= 100f) {
                energy = 100f
                sleeping = false
                emit(PetEvent.WOKE)
            }
        } else {
            hunger -= dt / 40f
            happy -= dt / 55f
            energy -= dt / 75f
            if (poops > 0) happy -= poops * dt / 200f
            if (sick) happy -= dt / 60f
            poopTimer += dt
            if (poopTimer >= POOP_EVERY) {
                poopTimer = 0f
                if (poops < 4) {
                    poops++
                    emit(PetEvent.POOPED)
                }
            }
            if (energy <= 0f) {
                energy = 0f
                sleeping = true
            }
        }
        hunger = hunger.coerceIn(0f, 100f)
        happy = happy.coerceIn(0f, 100f)
        energy = energy.coerceIn(0f, 100f)

        // a dirty room makes the cat sick
        if (poops >= 2) dirtySec += dt else dirtySec = max(0f, dirtySec - dt)
        if (!sick && dirtySec >= 180f) {
            sick = true
            dirtySec = 0f
            emit(PetEvent.SICK)
        }

        if (hunger <= 0f) starveSec += dt else starveSec = max(0f, starveSec - dt * 2f)
        if (sick) sickSec += dt else sickSec = 0f

        if (!canDie) {
            hunger = max(hunger, 8f)
            happy = max(happy, 10f)
            starveSec = 0f
            sickSec = min(sickSec, 600f)
        } else if (starveSec >= STARVE_DEATH || sickSec >= SICK_DEATH) {
            stage = Stage.DEAD
            sleeping = false
            emit(PetEvent.DIED)
        }
    }

    // ---------------------------------------------------------------- actions

    fun feed(): Res {
        if (!alive) return Res.UNAVAILABLE
        if (sleeping) return Res.ASLEEP
        if (hunger >= 95f) return Res.FULL
        hunger = min(100f, hunger + 30f)
        happy = min(100f, happy + 3f)
        return Res.OK
    }

    fun canPlay(): Res {
        if (!alive) return Res.UNAVAILABLE
        if (sleeping) return Res.ASLEEP
        if (sick) return Res.SICK
        if (energy < 15f) return Res.TOO_TIRED
        return Res.OK
    }

    fun rewardPlay(score: Int) {
        happy = min(100f, happy + min(40, score * 5))
        energy = max(0f, energy - 12f)
        hunger = max(0f, hunger - 6f)
    }

    fun toggleSleep(): Res {
        if (!alive) return Res.UNAVAILABLE
        if (!sleeping && energy >= 95f) return Res.NOT_TIRED
        sleeping = !sleeping
        return Res.OK
    }

    fun clean(): Res {
        if (!alive) return Res.UNAVAILABLE
        if (poops == 0) return Res.NO_POOP
        poops = 0
        dirtySec = 0f
        happy = min(100f, happy + 2f)
        return Res.OK
    }

    fun heal(): Res {
        if (!alive) return Res.UNAVAILABLE
        if (!sick) return Res.NOT_SICK
        sick = false
        sickSec = 0f
        happy = max(0f, happy - 5f)
        return Res.OK
    }

    fun stroke() {
        happy = min(100f, happy + 2f)
    }

    fun restart() {
        adopted = false
        stage = Stage.EGG
        stageSec = 0f
        totalSec = 0f
        hunger = 80f
        happy = 80f
        energy = 100f
        poops = 0
        sick = false
        sleeping = false
        poopTimer = 0f
        dirtySec = 0f
        starveSec = 0f
        sickSec = 0f
    }

    // ---------------------------------------------------------------- persistence

    fun save(p: SharedPreferences) {
        p.edit()
            .putString("stage", stage.name)
            .putFloat("stageSec", stageSec)
            .putFloat("totalSec", totalSec)
            .putFloat("hunger", hunger)
            .putFloat("happy", happy)
            .putFloat("energy", energy)
            .putInt("poops", poops)
            .putBoolean("sick", sick)
            .putBoolean("sleeping", sleeping)
            .putBoolean("soundOn", soundOn)
            .putString("name", name)
            .putInt("coat", coat)
            .putBoolean("adopted", adopted)
            .putFloat("poopTimer", poopTimer)
            .putFloat("dirtySec", dirtySec)
            .putFloat("starveSec", starveSec)
            .putFloat("sickSec", sickSec)
            .putLong("saved", System.currentTimeMillis())
            .apply()
    }

    /** Restores the saved pet and fast-forwards the time the app was closed (gently: no dying offline). */
    fun load(p: SharedPreferences, nowMs: Long) {
        if (!p.contains("stage")) return
        stage = try {
            Stage.valueOf(p.getString("stage", "EGG") ?: "EGG")
        } catch (e: IllegalArgumentException) {
            Stage.EGG
        }
        stageSec = p.getFloat("stageSec", 0f)
        totalSec = p.getFloat("totalSec", 0f)
        hunger = p.getFloat("hunger", 80f)
        happy = p.getFloat("happy", 80f)
        energy = p.getFloat("energy", 100f)
        poops = p.getInt("poops", 0)
        sick = p.getBoolean("sick", false)
        sleeping = p.getBoolean("sleeping", false)
        soundOn = p.getBoolean("soundOn", true)
        name = p.getString("name", "MIMI") ?: "MIMI"
        coat = p.getInt("coat", 0).coerceIn(0, COATS - 1)
        adopted = p.getBoolean("adopted", true)   // pets saved by version 1.0 were already adopted
        poopTimer = p.getFloat("poopTimer", 0f)
        dirtySec = p.getFloat("dirtySec", 0f)
        starveSec = p.getFloat("starveSec", 0f)
        sickSec = p.getFloat("sickSec", 0f)

        val away = ((nowMs - p.getLong("saved", nowMs)) / 1000f).coerceIn(0f, OFFLINE_MAX) * OFFLINE_RATE
        val listener = onEvent
        onEvent = null
        var left = away
        while (left > 0f) {
            val d = min(30f, left)
            tick(d, false)
            left -= d
        }
        onEvent = listener
    }

    // ---------------------------------------------------------------- notifications

    private fun copy(): Pet {
        val c = Pet()
        c.stage = stage; c.stageSec = stageSec; c.totalSec = totalSec
        c.hunger = hunger; c.happy = happy; c.energy = energy
        c.poops = poops; c.sick = sick; c.sleeping = sleeping; c.adopted = adopted
        c.poopTimer = poopTimer; c.dirtySec = dirtySec; c.starveSec = starveSec; c.sickSec = sickSec
        return c
    }

    /**
     * Looks ahead (with the same slow offline clock the game uses while closed) and returns the first
     * moment the cat will need attention, or null if nothing happens within the offline window.
     * Needs-alerts are never sooner than 5 minutes so closing the app does not instantly buzz the phone.
     */
    fun nextAlert(): Alert? {
        if (!adopted || stage == Stage.DEAD) return null
        val sim = copy()
        var code: String? = null
        sim.onEvent = { e ->
            if (code == null) {
                code = when (e) {
                    PetEvent.HATCHED -> "HATCH"
                    PetEvent.SICK -> "SICK"
                    else -> null
                }
            }
        }
        var t = 0f
        val maxSim = OFFLINE_MAX * OFFLINE_RATE
        while (t < maxSim && code == null) {
            sim.tick(30f, false)
            t += 30f
            if (code == null && sim.alive) {
                code = when {
                    sim.hunger < 25f -> "HUNGRY"
                    sim.poops >= 2 -> "POOP"
                    sim.happy < 25f -> "LONELY"
                    else -> null
                }
            }
        }
        val c = code ?: return null
        val ms = (t / OFFLINE_RATE * 1000f).toLong()
        return Alert(c, max(ms, if (c == "HATCH") 30_000L else 5 * 60_000L))
    }
}
