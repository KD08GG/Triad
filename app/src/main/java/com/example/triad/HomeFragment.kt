package com.example.triad

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val tvGreeting      = view.findViewById<TextView>(R.id.tvGreeting)
        val tvUserName      = view.findViewById<TextView>(R.id.tvHomeUserName)
        val tvPoints        = view.findViewById<TextView>(R.id.tvHomePoints)
        val tvLevel         = view.findViewById<TextView>(R.id.tvHomeLevel)
        val tvPendientes    = view.findViewById<TextView>(R.id.tvPendingCount)
        val tvCompletadas   = view.findViewById<TextView>(R.id.tvCompletedCount)
        val pbDiario        = view.findViewById<ProgressBar>(R.id.pbDailyProgress)
        val tvProgressLabel = view.findViewById<TextView>(R.id.tvProgressLabel)

        // ── BOTÓN DEBUG (solo para pruebas — elimínalo antes de publicar) ──
        // Si no tienes este botón en tu layout, agrégalo temporalmente
        // o comenta este bloque. Ver instrucciones abajo.
        view.findViewById<Button>(R.id.btnDebugReinicio)?.setOnClickListener {
            forzarReinicioDeDia(tvUserName, tvPoints, tvLevel, tvPendientes, tvCompletadas, pbDiario, tvProgressLabel)
        }
        // ────────────────────────────────────────────────────────────────────

        tvGreeting.text = saludoDelDia()

        verificarReinicioDiario {
            cargarDatos(tvUserName, tvPoints, tvLevel, tvPendientes, tvCompletadas, pbDiario, tvProgressLabel)
        }
    }

    // ─── FORZAR REINICIO PARA DEBUG ──────────────────────────────────────────
    // Resetea ultimoReinicio a ayer para que verificarReinicioDiario lo detecte
    // como día nuevo y aplique penalizaciones inmediatamente.
    private fun forzarReinicioDeDia(
        tvName: TextView, tvPoints: TextView, tvLevel: TextView,
        tvPendientes: TextView, tvCompletadas: TextView,
        pbDiario: ProgressBar, tvProgressLabel: TextView
    ) {
        val uid = auth.currentUser?.uid ?: return
        // Poner ultimoReinicio en "ayer" para forzar el reinicio
        val ayer = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
        val fechaAyer = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(ayer.time)

        db.collection("users").document(uid)
            .update("ultimoReinicio", fechaAyer)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "🔧 Debug: simulando día nuevo...", Toast.LENGTH_SHORT).show()
                // Ahora ejecutar el reinicio normalmente
                reiniciarTareas(uid, fechaDeHoy()) {
                    cargarDatos(tvName, tvPoints, tvLevel, tvPendientes, tvCompletadas, pbDiario, tvProgressLabel)
                }
            }
    }

    // ─── REINICIO DIARIO ─────────────────────────────────────────────────────
    private fun verificarReinicioDiario(onDone: () -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onDone(); return }
        val hoy = fechaDeHoy()

        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val ultimoReinicio = snap.getString("ultimoReinicio") ?: ""
                if (ultimoReinicio != hoy) {
                    reiniciarTareas(uid, hoy, onDone)
                } else {
                    onDone()
                }
            }
            .addOnFailureListener { onDone() }
    }

    private fun reiniciarTareas(uid: String, hoy: String, onDone: () -> Unit) {
        db.collection("tasks")
            .whereEqualTo("userId", uid)
            .whereEqualTo("recurrent", true)
            .get()
            .addOnSuccessListener { tareas ->
                val batch = db.batch()
                var puntosAPenalizar = 0
                var tareasNoCumplidas = 0

                for (doc in tareas) {
                    val completada   = doc.getBoolean("completed") ?: false
                    val penalizacion = doc.getLong("penalizacion")?.toInt() ?: 0

                    if (!completada && penalizacion > 0) {
                        puntosAPenalizar += penalizacion
                        tareasNoCumplidas++
                    }
                    // Resetear para el nuevo día
                    batch.update(doc.reference, "completed", false)
                }

                val userRef = db.collection("users").document(uid)
                batch.update(userRef, "ultimoReinicio", hoy)

                if (puntosAPenalizar > 0) {
                    // Mostrar cuántas tareas no cumplió
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "😬 No completaste $tareasNoCumplidas tarea(s). -$puntosAPenalizar pts",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    aplicarPenalizacion(uid, puntosAPenalizar, batch, onDone)
                } else {
                    batch.commit()
                        .addOnSuccessListener { onDone() }
                        .addOnFailureListener { onDone() }
                }
            }
            .addOnFailureListener { onDone() }
    }

    private fun aplicarPenalizacion(
        uid: String,
        puntos: Int,
        batch: WriteBatch,
        onDone: () -> Unit
    ) {
        val userRef = db.collection("users").document(uid)

        db.runTransaction { transaction ->
            val snap           = transaction.get(userRef)
            val puntosActuales = snap.getLong("points") ?: 0L
            // IMPORTANTE: coerceAtLeast(0) para no ir a negativos
            // Los puntos nunca bajan de 0 — llegar a 0 ya activa el bloqueo
            val nuevoPuntaje   = (puntosActuales - puntos).coerceAtLeast(0L)
            val nuevoNivel     = calcularNivel(nuevoPuntaje.toInt())
            transaction.update(userRef, "points", nuevoPuntaje)
            transaction.update(userRef, "level", nuevoNivel.toLong())
            nuevoPuntaje
        }.addOnSuccessListener { nuevoPuntaje ->
            batch.commit().addOnSuccessListener {
                if (nuevoPuntaje <= 0L) {
                    bloquearUsuario(uid)
                } else {
                    onDone()
                }
            }.addOnFailureListener { onDone() }
        }.addOnFailureListener {
            batch.commit()
                .addOnSuccessListener { onDone() }
                .addOnFailureListener { onDone() }
        }
    }

    private fun bloquearUsuario(uid: String) {
        if (!isAdded) return
        db.collection("users").document(uid)
            .update(mapOf(
                "bloqueada"        to true,
                "metrosAcumulados" to 0.0,
                "points"           to 0L
            ))
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "💀 Sin puntos. ¡A correr!", Toast.LENGTH_LONG).show()
                    startActivity(
                        Intent(requireContext(), LockActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }
            }
    }

    // ─── CARGA DE DATOS ───────────────────────────────────────────────────────
    private fun cargarDatos(
        tvName: TextView, tvPoints: TextView, tvLevel: TextView,
        tvPendientes: TextView, tvCompletadas: TextView,
        pbDiario: ProgressBar, tvProgressLabel: TextView
    ) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (!isAdded || snap == null || !snap.exists()) return@addSnapshotListener
                val name   = snap.getString("name") ?: "Héroe"
                val points = snap.getLong("points") ?: 0L
                val level  = snap.getLong("level") ?: 1L
                tvName.text   = name
                tvPoints.text = "$points pts"
                tvLevel.text  = "Nivel $level · ${nombreNivel(level.toInt())}"
            }

        db.collection("tasks")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snaps, _ ->
                if (!isAdded || snaps == null) return@addSnapshotListener
                val total       = snaps.size()
                val completadas = snaps.count { it.getBoolean("completed") == true }
                val pendientes  = total - completadas
                tvPendientes.text    = "$pendientes"
                tvCompletadas.text   = "$completadas"
                val progreso         = if (total > 0) (completadas * 100 / total) else 0
                pbDiario.progress    = progreso
                tvProgressLabel.text = "$progreso% del día completado"
            }
    }

    // ─── UTILIDADES ──────────────────────────────────────────────────────────
    private fun fechaDeHoy(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun saludoDelDia(): String =
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11  -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else      -> "Buenas noches"
        }

    private fun nombreNivel(level: Int): String = when (level) {
        1    -> "Aprendiz"
        2    -> "Guerrero"
        3    -> "Explorador"
        4    -> "Guardián"
        5    -> "Campeón"
        6    -> "Maestro"
        7    -> "Legendario"
        else -> "Héroe"
    }

    private fun calcularNivel(puntos: Int): Int = when {
        puntos < 500   -> 1
        puntos < 1200  -> 2
        puntos < 2500  -> 3
        puntos < 4500  -> 4
        puntos < 7000  -> 5
        puntos < 10000 -> 6
        else           -> 7
    }
}