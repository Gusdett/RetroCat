package com.retrocat.tamagotchi

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules ONE reminder when the app goes to the background: at the moment the pet is
 * predicted to need attention (see [Pet.nextAlert]). It is cancelled whenever the app is opened.
 */
object Notifier {
    const val CHANNEL = "retrocat_needs"

    private fun pending(ctx: Context, code: String, name: String): PendingIntent {
        val i = Intent(ctx, AlertReceiver::class.java).putExtra("code", code).putExtra("name", name)
        return PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, "", ""))
    }

    fun schedule(ctx: Context, pet: Pet) {
        cancel(ctx)
        val alert = pet.nextAlert() ?: return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // inexact on purpose: needs no special "exact alarm" permission
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + alert.delayMs,
            pending(ctx, alert.code, pet.name)
        )
    }
}

class AlertReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    private fun builder(ctx: Context): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, Notifier.CHANNEL) else Notification.Builder(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        val code = intent.getStringExtra("code") ?: return
        val name = (intent.getStringExtra("name") ?: "").ifEmpty { "Kucingmu" }
        val (title, text) = when (code) {
            "HATCH" -> "Telurmu sudah menetas!" to "Ada anak kucing baru. Ayo kenalan."
            "HUNGRY" -> "$name lapar!" to "Beri dia makan sebelum perutnya kosong."
            "POOP" -> "$name butuh dibersihkan" to "Kotorannya menumpuk. Bersihkan sebelum dia sakit."
            "SICK" -> "$name sakit!" to "Buka aplikasi dan beri obat (HEAL)."
            "LONELY" -> "$name kesepian" to "Ayo main atau elus dia."
            else -> return
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(Notifier.CHANNEL, "Kebutuhan kucing", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = builder(ctx)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }
}
