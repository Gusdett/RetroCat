package com.retrocat.tamagotchi

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {

    private val pet = Pet()
    private lateinit var view: GameView
    private val prefs by lazy { getSharedPreferences("retrocat", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = GameView(this, pet)
        view.onSave = { pet.save(prefs) }
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        // restores the saved pet and fast-forwards the time the app was closed
        pet.load(prefs, System.currentTimeMillis())
        Chip.enabled = pet.soundOn
        view.resume()
    }

    override fun onPause() {
        super.onPause()
        view.pause()
        pet.save(prefs)
    }
}
