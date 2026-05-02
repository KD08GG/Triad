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
// Se ejecuta cuando el teléfono se reinicia. Si el usuario estaba bloqueado
// y había iniciado el rastreo, lo reanuda automáticamente.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val auth = FirebaseAuth.getInstance()
        val uid  = auth.currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val bloqueada      = snap.getBoolean("bloqueada") ?: false
                val rastreoActivo  = snap.getBoolean("rastreoActivo") ?: false
                if (bloqueada && rastreoActivo) {
                    // Reiniciar el servicio después del reboot
                    val serviceIntent = Intent(context, RunningTrackerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
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

    // Solo la distancia de ESTA sesión continua en memoria
    // Si el servicio muere → onDestroy resetea → próxima sesión empieza desde 0
    private var distanciaEnMemoria = 0.0
    private var ultimaUbicacion: Location? = null
    private var sesionIniciada = false

    companion object {
        const val CHANNEL_ID   = "triad_tracker"
        const val NOTIF_ID     = 2001
        const val ACTION_STOP  = "com.example.triad.STOP_TRACKER"
        const val META_METROS  = 2000.0
        private const val MIN_DISTANCIA = 8f    // metros mínimos para contar
        private const val INTERVALO     = 3000L // ms entre lecturas GPS
        private const val MAX_VELOCIDAD = 12f   // m/s máx realista (~43 km/h)
    }

    override fun onCreate() {
        super.onCreate()
        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            detener()
            return START_NOT_STICKY
        }

        crearCanalNotificacion()
        startForeground(NOTIF_ID, construirNotificacion(0.0))

        // Marcar en Firestore que el rastreo está activo (para BootReceiver)
        marcarRastreoActivo(true)
        iniciarRastreo()

        // START_STICKY: el sistema reinicia el servicio si lo mata por RAM
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

                // Ignorar primeros 2 puntos para que GPS se estabilice
                if (!sesionIniciada) {
                    ultimaUbicacion = nueva
                    sesionIniciada = true
                    return
                }

                ultimaUbicacion?.let { anterior ->
                    val distancia = anterior.distanceTo(nueva)
                    val tiempoMs  = nueva.time - anterior.time

                    // Filtro anti-ruido GPS:
                    // 1. Distancia mínima real
                    // 2. Velocidad máxima realista (anti-teletransporte GPS)
                    val velocidad = if (tiempoMs > 0) (distancia / (tiempoMs / 1000f)) else 0f
                    if (distancia >= MIN_DISTANCIA && velocidad <= MAX_VELOCIDAD) {
                        distanciaEnMemoria += distancia
                        guardarEnFirestoreYVerificar()
                        actualizarNotificacion(distanciaEnMemoria)
                    }
                }
                ultimaUbicacion = nueva
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            detener()
        }
    }

    // Sobreescribe metros en Firestore — NO acumula entre sesiones
    // Eso garantiza que los 2km sean CONTINUOS (sin cerrar la app)
    private fun guardarEnFirestoreYVerificar() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update("metrosAcumulados", distanciaEnMemoria)
            .addOnSuccessListener {
                if (distanciaEnMemoria >= META_METROS) desbloquear(uid)
            }
    }

    private fun desbloquear(uid: String) {
        marcarRastreoActivo(false)
        db.runTransaction { transaction ->
            val ref    = db.collection("users").document(uid)
            val snap   = transaction.get(ref)
            val puntos = snap.getLong("points") ?: 0L
            transaction.update(ref, "bloqueada",       false)
            transaction.update(ref, "metrosAcumulados", 0.0)
            transaction.update(ref, "rastreoActivo",   false)
            transaction.update(ref, "points",          puntos + 300L)
        }.addOnSuccessListener {
            startActivity(Intent(this, LockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("desbloqueado", true)
            })
            detener()
        }
    }

    private fun marcarRastreoActivo(activo: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update("rastreoActivo", activo)
    }

    // ─── NOTIFICACIÓN ────────────────────────────────────────────────────────
    private fun actualizarNotificacion(metros: Double) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, construirNotificacion(metros))
    }

    private fun construirNotificacion(metros: Double): Notification {
        val restante  = (META_METROS - metros).coerceAtLeast(0.0)
        val progreso  = ((metros / META_METROS) * 100).toInt().coerceIn(0, 100)

        val stopIntent = Intent(this, RunningTrackerService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            this, 1,
            Intent(this, LockActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("🏃 Triad — ¡Corriendo para desbloquearte!")
            .setContentText("${"%.0f".format(metros)}m / 2000m — Faltan ${"%.0f".format(restante)}m")
            .setProgress(100, progreso, false)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "Cancelar sesión", stopPending)
            .build()
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID, "Rastreo de carrera", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Muestra tu progreso mientras corres para desbloquear Triad" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(canal)
        }
    }

    private fun detener() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        stopForeground(true)
        stopSelf()
    }

    // ─── DESTRUCCIÓN — resetear metros para garantizar continuidad ─────────
    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        // Si el servicio fue destruido antes de los 2km, resetear distancia
        // Esto fuerza que la próxima sesión empiece desde 0 (continuidad)
        val uid = auth.currentUser?.uid
        if (uid != null && distanciaEnMemoria < META_METROS) {
            db.collection("users").document(uid)
                .update(mapOf(
                    "metrosAcumulados" to 0.0,
                    "rastreoActivo"    to false
                ))
        }
        distanciaEnMemoria = 0.0
        ultimaUbicacion    = null
        sesionIniciada     = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
