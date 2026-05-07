package com.example.triad

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailyReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val CHANNEL_ID   = "triad_recordatorio"
        const val CHANNEL_NAME = "Recordatorio de misiones"
        const val NOTIF_ID     = 1001
        private const val PREFS_NAME     = "triad_notif_prefs"
        private const val KEY_LAST_NOTIF = "last_notif_date"
    }

    override fun doWork(): Result {
        if (yaSeNotifico()) return Result.success()
        crearCanalSiNecesario()
        mostrarNotificacion()
        registrarFechaNotificacion()
        return Result.success()
    }

    private fun yaSeNotifico(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_NOTIF, "") == GameUtils.fechaDeHoy()
    }

    private fun registrarFechaNotificacion() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_NOTIF, GameUtils.fechaDeHoy())
            .apply()
    }

    private fun crearCanalSiNecesario() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Recordatorio diario para completar tus misiones en Triad"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(canal)
        }
    }

    private fun mostrarNotificacion() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mensajes = listOf(
            "Tus misiones de hoy te esperan, heroe.",
            "Cada tarea completada te acerca a la mejor version de ti.",
            "No rompas tu racha. Completa tus misiones hoy!",
            "El equilibrio no se logra solo. Vamos!",
            "Un pequeño paso hoy, un gran avance manana."
        )

        val notificacion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Triad - Misiones del dia")
            .setContentText(mensajes.random())
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensajes.random()))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, notificacion)
    }
}
