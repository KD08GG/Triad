package com.example.triad

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// ─── BOOT RECEIVER ───────────────────────────────────────────────────────────
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                if ((snap.getBoolean("bloqueada") == true) &&
                    (snap.getBoolean("rastreoActivo") == true)) {
                    val si = Intent(context, RunningTrackerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        context.startForegroundService(si)
                    else context.startService(si)
                }
            }
    }
}

// ─── RUNNING TRACKER SERVICE ─────────────────────────────────────────────────
class RunningTrackerService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var distanciaEnMemoria = 0.0
    private var ultimaUbicacion: Location? = null
    private var sesionIniciada = false

    companion object {
        const val CHANNEL_ID   = "triad_tracker"
        const val NOTIF_ID     = 2001
        const val ACTION_STOP  = "com.example.triad.STOP_TRACKER"
        const val META_METROS  = 2000.0
        const val REWARD_PTS   = 100L   // puntos restaurados al completar los 2km
        private const val MIN_DISTANCIA = 8f
        private const val INTERVALO     = 3000L
        private const val MAX_VELOCIDAD = 12f   // m/s (~43 km/h, filtro anti-trampa GPS)
    }

    override fun onCreate() {
        super.onCreate()
        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { detener(); return START_NOT_STICKY }
        crearCanalNotificacion()
        startForeground(NOTIF_ID, construirNotificacion(0.0))
        marcarRastreoActivo(true)
        iniciarRastreo()
        return START_STICKY
    }

    private fun iniciarRastreo() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVALO)
            .setMinUpdateDistanceMeters(MIN_DISTANCIA)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateDelayMillis(INTERVALO * 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val nueva = result.lastLocation ?: return

                // Ignorar primer punto para estabilizar GPS
                if (!sesionIniciada) {
                    ultimaUbicacion = nueva
                    sesionIniciada  = true
                    return
                }

                ultimaUbicacion?.let { anterior ->
                    val distancia = anterior.distanceTo(nueva)
                    val tiempoMs  = nueva.time - anterior.time
                    val velocidad = if (tiempoMs > 0) (distancia / (tiempoMs / 1000f)) else 0f

                    if (distancia >= MIN_DISTANCIA && velocidad <= MAX_VELOCIDAD) {
                        distanciaEnMemoria += distancia
                        guardarYVerificar()
                        actualizarNotificacion(distanciaEnMemoria)
                    }
                }
                ultimaUbicacion = nueva
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback, Looper.getMainLooper()
            )
        } catch (e: SecurityException) { detener() }
    }

    private fun guardarYVerificar() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update("metrosAcumulados", distanciaEnMemoria)
            .addOnSuccessListener {
                if (distanciaEnMemoria >= META_METROS) desbloquear(uid)
            }
    }

    // ── Desbloqueo: restaurar estado + +100 pts + navegar a MainActivity ─────
    private fun desbloquear(uid: String) {
        marcarRastreoActivo(false)

        db.runTransaction { transaction ->
            val ref    = db.collection("users").document(uid)
            val snap   = transaction.get(ref)
            val puntos = snap.getLong("points") ?: 0L

            transaction.update(ref, mapOf(
                "bloqueada"        to false,
                "metrosAcumulados" to 0.0,
                "rastreoActivo"    to false,
                "points"           to puntos + REWARD_PTS,  // +100 pts de regreso
                "hp"               to 30L                   // HP arranca en 30, no en 0
            ))
        }.addOnSuccessListener {
            // Ir directo a MainActivity (no pasar por LockActivity de nuevo)
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("desbloqueado", true)  // MainActivity mostrará toast de bienvenida
            }
            startActivity(intent)
            detener()
        }
    }

    private fun marcarRastreoActivo(activo: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("rastreoActivo", activo)
    }

    // ── Notificación ─────────────────────────────────────────────────────────
    private fun actualizarNotificacion(metros: Double) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, construirNotificacion(metros))
    }

    private fun construirNotificacion(metros: Double): Notification {
        val restante = (META_METROS - metros).coerceAtLeast(0.0)
        val progreso = ((metros / META_METROS) * 100).toInt().coerceIn(0, 100)

        val stopPending = PendingIntent.getService(
            this, 0,
            Intent(this, RunningTrackerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            this, 1,
            Intent(this, LockActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Triad — Corriendo para desbloquearte!")
            .setContentText("${"%.0f".format(metros)}m / 2000m — Faltan ${"%.0f".format(restante)}m")
            .setProgress(100, progreso, false)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "Cancelar sesion", stopPending)
            .build()
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID, "Rastreo de carrera", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Progreso mientras corres para desbloquear Triad" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(canal)
        }
    }

    private fun detener() {
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(true)
        stopSelf()
    }

    // Al destruir sin completar 2km → resetear metros (garantiza continuidad)
    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)

        val uid = auth.currentUser?.uid
        if (uid != null && distanciaEnMemoria < META_METROS) {
            db.collection("users").document(uid)
                .update(mapOf("metrosAcumulados" to 0.0, "rastreoActivo" to false))
        }
        distanciaEnMemoria = 0.0; ultimaUbicacion = null; sesionIniciada = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
