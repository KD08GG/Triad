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

class LockActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var googleMap: GoogleMap? = null
    private var rastreoActivo = false

    private val META_METROS  = 2000.0
    private val PERM_LOCATION = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Si el servicio ya desbloqueó al usuario, ir directo al juego
        if (intent.getBooleanExtra("desbloqueado", false)) {
            Toast.makeText(this, "¡2km completados! Bienvenido de vuelta 🔥", Toast.LENGTH_LONG).show()
            irAlJuego()
            return
        }

        inicializarMapa()
        escucharProgreso()
        configurarBoton()
    }

    private fun configurarBoton() {
        val btn = findViewById<MaterialButton>(R.id.btnIniciarCarrera)
        btn.setOnClickListener {
            if (!rastreoActivo) {
                verificarPermisosYCorrer()
            } else {
                // Pausar: detener el servicio (y onDestroy reseteará metros)
                stopService(Intent(this, RunningTrackerService::class.java))
                rastreoActivo = false
                btn.text = "🏃 Iniciar rastreo GPS"
                btn.isEnabled = true
                Toast.makeText(this, "Sesión pausada. Los metros se reinician.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun inicializarMapa() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapView) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
        }
    }

    private fun escucharProgreso() {
        val uid          = auth.currentUser?.uid ?: return
        val pbProgreso   = findViewById<ProgressBar>(R.id.pbCarrera)
        val tvDistancia  = findViewById<TextView>(R.id.tvDistanciaRecorrida)
        val tvRestante   = findViewById<TextView>(R.id.tvDistanciaRestante)
        val tvPorcentaje = findViewById<TextView>(R.id.tvPorcentajeCarrera)

        db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener

                val metros    = snap.getDouble("metrosAcumulados") ?: 0.0
                val bloqueada = snap.getBoolean("bloqueada") ?: true

                val km       = metros / 1000.0
                val restante = ((META_METROS - metros) / 1000.0).coerceAtLeast(0.0)
                val progreso = ((metros / META_METROS) * 100).toInt().coerceIn(0, 100)

                tvDistancia.text  = "${"%.0f".format(metros)} m recorridos"
                tvRestante.text   = "${"%.0f".format(restante * 1000)} m restantes"
                tvPorcentaje.text = "$progreso%"
                pbProgreso.progress = progreso

                if (!bloqueada) irAlJuego()
            }
    }

    private fun verificarPermisosYCorrer() {
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!ok) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                PERM_LOCATION
            )
        } else {
            iniciarServicio()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_LOCATION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarServicio()
            googleMap?.isMyLocationEnabled = true
        } else {
            Toast.makeText(this, "Necesitas dar permiso de ubicación para correr", Toast.LENGTH_LONG).show()
        }
    }

    private fun iniciarServicio() {
        ContextCompat.startForegroundService(this, Intent(this, RunningTrackerService::class.java))
        rastreoActivo = true
        val btn = findViewById<MaterialButton>(R.id.btnIniciarCarrera)
        btn.text = "⏸ Pausar rastreo"
        Toast.makeText(this, "¡Rastreo iniciado! Sal a correr 🏃", Toast.LENGTH_SHORT).show()
    }

    private fun irAlJuego() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(this, "Debes correr 2km continuos para desbloquear Triad 🔒", Toast.LENGTH_SHORT).show()
    }
}