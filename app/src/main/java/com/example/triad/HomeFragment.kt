package com.example.triad

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

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

        val tvGreeting       = view.findViewById<TextView>(R.id.tvGreeting)
        val tvUserName       = view.findViewById<TextView>(R.id.tvHomeUserName)
        val tvPoints         = view.findViewById<TextView>(R.id.tvHomePoints)
        val tvLevel          = view.findViewById<TextView>(R.id.tvHomeLevel)
        val tvPendientes     = view.findViewById<TextView>(R.id.tvPendingCount)
        val tvCompletadas    = view.findViewById<TextView>(R.id.tvCompletedCount)
        val pbDiario         = view.findViewById<ProgressBar>(R.id.pbDailyProgress)
        val tvProgressLabel  = view.findViewById<TextView>(R.id.tvProgressLabel)

        // Saludo dinámico según hora
        tvGreeting.text = saludoDelDia()

        cargarDatos(tvUserName, tvPoints, tvLevel, tvPendientes, tvCompletadas, pbDiario, tvProgressLabel)
    }

    private fun cargarDatos(
        tvName: TextView, tvPoints: TextView, tvLevel: TextView,
        tvPendientes: TextView, tvCompletadas: TextView,
        pbDiario: ProgressBar, tvProgressLabel: TextView
    ) {
        val uid = auth.currentUser?.uid ?: return

        // Datos del usuario
        db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val name   = snap.getString("name") ?: "Héroe"
                val points = snap.getLong("points") ?: 0L
                val level  = snap.getLong("level") ?: 1L

                tvName.text   = name
                tvPoints.text = "$points pts"
                tvLevel.text  = "Nivel $level · ${nombreNivel(level.toInt())}"
            }

        // Tareas del usuario
        db.collection("tasks")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snaps, _ ->
                if (snaps == null) return@addSnapshotListener
                val total      = snaps.size()
                val completadas = snaps.count { it.getBoolean("completed") == true }
                val pendientes  = total - completadas

                tvPendientes.text  = "$pendientes"
                tvCompletadas.text = "$completadas"

                val progreso = if (total > 0) (completadas * 100 / total) else 0
                pbDiario.progress   = progreso
                tvProgressLabel.text = "$progreso% del día completado"
            }
    }

    private fun saludoDelDia(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11  -> "☀️ Buenos días"
            in 12..18 -> "🌤️ Buenas tardes"
            else      -> "🌙 Buenas noches"
        }
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
}