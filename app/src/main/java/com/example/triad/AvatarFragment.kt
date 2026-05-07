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
import com.google.firebase.firestore.ListenerRegistration

class AvatarFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var userListener:   ListenerRegistration? = null
    private var pilarsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_avatar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val tvName      = view.findViewById<TextView>(R.id.tvAvatarName)
        val tvLevel     = view.findViewById<TextView>(R.id.tvAvatarLevel)
        val tvPoints    = view.findViewById<TextView>(R.id.tvAvatarPoints)
        val tvNextLevel = view.findViewById<TextView>(R.id.tvNextLevel)
        val pbXp        = view.findViewById<ProgressBar>(R.id.pbXp)
        val pbCuerpo    = view.findViewById<ProgressBar>(R.id.pbCuerpo)
        val pbAlma      = view.findViewById<ProgressBar>(R.id.pbAlma)
        val pbEspiritu  = view.findViewById<ProgressBar>(R.id.pbEspiritu)
        val tvCuerpo    = view.findViewById<TextView>(R.id.tvCuerpoValue)
        val tvAlma      = view.findViewById<TextView>(R.id.tvAlmaValue)
        val tvEspiritu  = view.findViewById<TextView>(R.id.tvEspirituValue)
        val btnLogout   = view.findViewById<MaterialButton>(R.id.btnLogout)

        cargarDatosUsuario(tvName, tvLevel, tvPoints, tvNextLevel, pbXp)
        cargarStatsPilares(pbCuerpo, pbAlma, pbEspiritu, tvCuerpo, tvAlma, tvEspiritu)

        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Cerrar sesion?")
                .setMessage("Tu progreso esta guardado en la nube.")
                .setPositiveButton("Cerrar sesion") { _, _ ->
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

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        pilarsListener?.remove()
        userListener   = null
        pilarsListener = null
    }

    private fun cargarDatosUsuario(
        tvName: TextView, tvLevel: TextView, tvPoints: TextView,
        tvNextLevel: TextView, pbXp: ProgressBar
    ) {
        val uid = auth.currentUser?.uid ?: return
        userListener?.remove()
        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (!isAdded || snap == null || !snap.exists()) return@addSnapshotListener

                val name   = snap.getString("name") ?: "Heroe"
                val points = snap.getLong("points") ?: 0L
                val level  = snap.getLong("level")?.toInt() ?: 1

                tvName.text   = name
                tvPoints.text = "$points pts"
                tvLevel.text  = "NV.$level ${GameUtils.nombreNivel(level).uppercase()}"

                val (ptActual, ptSig) = GameUtils.rangoPuntos(level)
                val progreso = if (ptSig > ptActual) {
                    ((points - ptActual) * 100 / (ptSig - ptActual)).toInt().coerceIn(0, 100)
                } else 100
                pbXp.progress = progreso

                tvNextLevel.text = if (level >= 7) "Nivel maximo alcanzado!"
                else "Faltan ${ptSig - points} pts para Nivel ${level + 1}"
            }
    }

    private fun cargarStatsPilares(
        pbCuerpo: ProgressBar, pbAlma: ProgressBar, pbEspiritu: ProgressBar,
        tvCuerpo: TextView, tvAlma: TextView, tvEspiritu: TextView
    ) {
        val uid = auth.currentUser?.uid ?: return
        pilarsListener?.remove()
        pilarsListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (!isAdded || snap == null || !snap.exists()) return@addSnapshotListener

                val statCuerpo   = snap.getLong("statCuerpo")   ?: 0L
                val statAlma     = snap.getLong("statAlma")     ?: 0L
                val statEspiritu = snap.getLong("statEspiritu") ?: 0L
                val total        = statCuerpo + statAlma + statEspiritu

                if (total == 0L) {
                    pbCuerpo.progress   = 33
                    pbAlma.progress     = 33
                    pbEspiritu.progress = 33
                } else {
                    pbCuerpo.progress   = ((statCuerpo.toFloat()   / total) * 100).toInt()
                    pbAlma.progress     = ((statAlma.toFloat()     / total) * 100).toInt()
                    pbEspiritu.progress = ((statEspiritu.toFloat() / total) * 100).toInt()
                }

                tvCuerpo.text   = "$statCuerpo tasks"
                tvAlma.text     = "$statAlma tasks"
                tvEspiritu.text = "$statEspiritu tasks"
            }
    }
}
