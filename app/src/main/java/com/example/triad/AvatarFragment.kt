package com.example.triad

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AvatarFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_avatar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val tvName       = view.findViewById<TextView>(R.id.tvAvatarName)
        val tvLevel      = view.findViewById<TextView>(R.id.tvAvatarLevel)
        val tvPoints     = view.findViewById<TextView>(R.id.tvAvatarPoints)
        val tvNextLevel  = view.findViewById<TextView>(R.id.tvNextLevel)
        val pbXp         = view.findViewById<ProgressBar>(R.id.pbXp)
        val pbHp         = view.findViewById<ProgressBar>(R.id.pbHp)
        val pbHappiness  = view.findViewById<ProgressBar>(R.id.pbHappiness)
        val pbMana       = view.findViewById<ProgressBar>(R.id.pbMana)
        val tvHp         = view.findViewById<TextView>(R.id.tvHpValue)
        val tvHappiness  = view.findViewById<TextView>(R.id.tvHappinessValue)
        val tvMana       = view.findViewById<TextView>(R.id.tvManaValue)
        val btnLogout    = view.findViewById<MaterialButton>(R.id.btnLogout)

        cargarDatos(tvName, tvLevel, tvPoints, tvNextLevel, pbXp, pbHp, pbHappiness, pbMana, tvHp, tvHappiness, tvMana)

        // Cerrar sesión con confirmación
        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("¿Cerrar sesión?")
                .setMessage("Tu progreso está guardado en la nube. Puedes volver cuando quieras.")
                .setPositiveButton("Cerrar sesión") { _, _ ->
                    Prefs(requireContext()).wipe()
                    NotificationScheduler.cancelar(requireContext())
                    auth.signOut()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun cargarDatos(
        tvName: TextView, tvLevel: TextView, tvPoints: TextView,
        tvNextLevel: TextView, pbXp: ProgressBar,
        pbHp: ProgressBar, pbHappiness: ProgressBar, pbMana: ProgressBar,
        tvHp: TextView, tvHappiness: TextView, tvMana: TextView
    ) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener

                val name      = snap.getString("name") ?: "Héroe"
                val points    = snap.getLong("points") ?: 0L
                val level     = snap.getLong("level")?.toInt() ?: 1
                val hp        = snap.getLong("hp")?.toInt() ?: 100
                val happiness = snap.getLong("happiness")?.toInt() ?: 100
                val mana      = snap.getLong("mana")?.toInt() ?: 100

                tvName.text      = name
                tvPoints.text    = "$points pts"
                tvLevel.text     = "Nivel $level · ${nombreNivel(level)}"
                tvHp.text        = "$hp / 100"
                tvHappiness.text = "$happiness / 100"
                tvMana.text      = "$mana / 100"

                pbHp.progress        = hp
                pbHappiness.progress = happiness
                pbMana.progress      = mana

                // Progreso hacia siguiente nivel
                val (puntosActual, puntosSiguiente) = rangoPuntos(level)
                val progreso = if (puntosSiguiente > puntosActual) {
                    ((points - puntosActual) * 100 / (puntosSiguiente - puntosActual)).toInt().coerceIn(0, 100)
                } else 100
                pbXp.progress = progreso

                val puntosRestantes = puntosSiguiente - points
                tvNextLevel.text = if (level >= 7) "¡Nivel máximo alcanzado! 🔥"
                else "Faltan $puntosRestantes pts para Nivel ${level + 1}"
            }
    }

    private fun nombreNivel(level: Int): String = when (level) {
        1    -> "Aprendiz 🌱"
        2    -> "Guerrero ⚔️"
        3    -> "Explorador 🗺️"
        4    -> "Guardián 🛡️"
        5    -> "Campeón 🏆"
        6    -> "Maestro 🌟"
        7    -> "Legendario 🔥"
        else -> "Héroe"
    }

    private fun rangoPuntos(level: Int): Pair<Long, Long> = when (level) {
        1    -> Pair(0L,     500L)
        2    -> Pair(500L,   1200L)
        3    -> Pair(1200L,  2500L)
        4    -> Pair(2500L,  4500L)
        5    -> Pair(4500L,  7000L)
        6    -> Pair(7000L,  10000L)
        else -> Pair(10000L, 10000L)
    }
}