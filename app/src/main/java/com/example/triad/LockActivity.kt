package com.example.triad

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class LockActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var googleMap: GoogleMap? = null
    private var rastreoActivo = false

    // FIX: Guardar referencia del listener para removerlo en onDestroy.
    // Sin esto, si el usuario completa los 2km y la app navega a MainActivity,
    // LockActivity queda en el back stack con su listener activo. Cuando Firestore
    // notifica el cambio de "bloqueada = false", desbloquearAutomaticamente() se llama
    // de nuevo, lanzando MainActivity por segunda vez y causando el bug de "doble navegacion"
    // que se manifestaba como glitches visuales al volver de correr.
    private var userProgressListener: ListenerRegistration? = null

    // FIX: Flag para evitar que irAlJuego() se llame mas de una vez.
    // Tanto RunningTrackerService como el listener de escucharProgreso() pueden
    // disparar la navegacion. Sin este flag, ambos pueden ejecutarse en rapida
    // sucesion y lanzar MainActivity dos veces.
    private var navegacionIniciada = false

    private val META_METROS  = 2000.0
    private val PERM_LOCATION = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        if (intent.getBooleanExtra("desbloqueado", false)) {
            irAlJuego()
            return
        }

        inicializarMapa()
        escucharProgreso()
        configurarBoton()
    }

    override fun onDestroy() {
        super.onDestroy()
        // FIX: Remover el listener al destruir la actividad
        userProgressListener?.remove()
        userProgressListener = null
    }

    private fun escucharProgreso() {
        val uid = auth.currentUser?.uid ?: return
        val pbProgreso   = findViewById<ProgressBar>(R.id.pbCarrera)
        val tvDistancia  = findViewById<TextView>(R.id.tvDistanciaRecorrida)
        val tvRestante   = findViewById<TextView>(R.id.tvDistanciaRestante)
        val tvPorcentaje = findViewById<TextView>(R.id.tvPorcentajeCarrera)

        // FIX: guardar la referencia del listener
        userProgressListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener

                val metros    = snap.getDouble("metrosAcumulados") ?: 0.0
                val bloqueada = snap.getBoolean("bloqueada") ?: true

                val progreso = ((metros / META_METROS) * 100).toInt().coerceIn(0, 100)
                tvDistancia.text  = "${"%.0f".format(metros)} m recorridos"
                tvRestante.text   = "${"%.0f".format((META_METROS - metros).coerceAtLeast(0.0))} m restantes"
                tvPorcentaje.text = "$progreso%"
                pbProgreso.progress = progreso

                // FIX: verificar navegacionIniciada antes de actuar.
                // Evita que multiples snapshots desencadenen navegaciones duplicadas.
                if (!navegacionIniciada) {
                    if (metros >= META_METROS && bloqueada) {
                        desbloquearAutomaticamente(uid)
                    } else if (!bloqueada) {
                        irAlJuego()
                    }
                }
            }
    }

    private fun desbloquearAutomaticamente(uid: String) {
        // FIX: marcar la navegacion como iniciada inmediatamente para
        // que snapshots posteriores no vuelvan a llamar este metodo.
        navegacionIniciada = true
        stopService(Intent(this, RunningTrackerService::class.java))

        db.collection("users").document(uid)
            .update(mapOf(
                "bloqueada"        to false,
                "metrosAcumulados" to 0.0,
                "points"           to 300L,
                "level"            to GameUtils.calcularNivel(300).toLong()
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "Objetivo cumplido! Estas libre.", Toast.LENGTH_LONG).show()
                irAlJuego()
            }
            .addOnFailureListener {
                // Si falla la escritura, resetear el flag para permitir reintento
                navegacionIniciada = false
            }
    }

    private fun irAlJuego() {
        if (navegacionIniciada && !isTaskRoot) return  // guardia adicional
        navegacionIniciada = true
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun configurarBoton() {
        val btn = findViewById<MaterialButton>(R.id.btnIniciarCarrera)
        btn.setOnClickListener {
            if (!rastreoActivo) {
                verificarPermisosYCorrer()
            } else {
                stopService(Intent(this, RunningTrackerService::class.java))
                rastreoActivo = false
                btn.text = "Iniciar rastreo GPS"
            }
        }
    }

    private fun inicializarMapa() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapView) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
        }
    }

    private fun verificarPermisosYCorrer() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERM_LOCATION)
        } else {
            iniciarServicio()
        }
    }

    private fun iniciarServicio() {
        ContextCompat.startForegroundService(this, Intent(this, RunningTrackerService::class.java))
        rastreoActivo = true
        findViewById<MaterialButton>(R.id.btnIniciarCarrera).text = "Pausar rastreo"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_LOCATION && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarServicio()
        }
    }
}