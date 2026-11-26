package com.retrocat.tamagotchi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout

class MainActivity : Activity() {

    private val pet = Pet()
    private lateinit var view: GameView
    private val prefs by lazy { getSharedPreferences("retrocat", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = GameView(this, pet)
        view.onSave = { pet.save(prefs) }
        view.onAskName = { current, done -> askName(current, done) }
        setContentView(view)

        // Android 13+ needs the user's OK before an app may show notifications
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun askName(current: String, done: (String) -> Unit) {
        val input = EditText(this)
        input.setText(current)
        input.setSelection(input.text.length)
        input.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(8))
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        val box = FrameLayout(this)
        box.setPadding(48, 16, 48, 0)
        box.addView(input)
        AlertDialog.Builder(this)
            .setTitle("Nama kucing (maks 8 huruf)")
            .setView(box)
            .setPositiveButton("OK") { _, _ -> done(input.text.toString()) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        Notifier.cancel(this)
        // restores the saved pet and fast-forwards the time the app was closed
        pet.load(prefs, System.currentTimeMillis())
        Chip.enabled = pet.soundOn
        view.resume()
    }

    override fun onPause() {
        super.onPause()
        view.pause()
        pet.save(prefs)
        Notifier.schedule(this, pet)
    }
}
