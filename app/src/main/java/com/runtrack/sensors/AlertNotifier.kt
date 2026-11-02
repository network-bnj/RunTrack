package com.runtrack.sensors

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Crea los canales y dispara las notificaciones (visibles aunque la app esté cerrada). */
class AlertNotifier(private val ctx: Context) {
    companion object {
        const val CH_SERVICE = "runtrack_service"
        const val CH_ALERTS = "runtrack_alerts"
        const val ID_SERVICE = 1
        const val ID_ALERT = 2
    }

    init {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Monitoreo activo", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Alertas de carrera", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun openApp() = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun serviceNotification() = NotificationCompat.Builder(ctx, CH_SERVICE)
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setContentTitle("RunTrack monitoreando")
        .setContentText("Acelerómetro y giroscopio activos")
        .setOngoing(true).setContentIntent(openApp()).build()

    fun directionChange(count: Int) {
        if (Build_postNotificationsDenied()) return
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Cambio de dirección detectado")
            .setContentText("Giros acumulados en esta sesión: $count")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(openApp()).build()
        NotificationManagerCompat.from(ctx).notify(ID_ALERT, n)
    }

    private fun Build_postNotificationsDenied() =
        android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}
