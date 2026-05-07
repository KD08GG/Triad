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
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*



class HomeFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var userListener: ListenerRegistration? = null
    private var tasksListener: ListenerRegistration? = null
    private var isResetting = false

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
        val tvPendientes    = view.findViewById<TextView>(R.id.tvPendingCount)
        val tvCompletadas   = view.findViewById<TextView>(R.id.tvCompletedCount)
        val pbDiario        = view.findViewById<ProgressBar>(R.id.pbDailyProgress)
        val tvProgressLabel = view.findViewById<TextView>(R.id.tvProgressLabel)

        view.findViewById<Button>(R.id.btnDebugReinicio)?.setOnClickListener {
            if (!isResetting) forzarReinicioDeDia(tvUserName, tvPoints, tvPendientes, tvCompletadas, pbDiario, tvProgressLabel)
        }

        tvGreeting.text = GameUtils.saludoDelDia()

        verificarReinicioDiario {
            cargarDatos(tvUserName, tvPoints, tvPendientes, tvCompletadas, pbDiario, tvProgressLabel)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        tasksListener?.remove()
    }

    private fun forzarReinicioDeDia(
        tvName: TextView, tvPoints: TextView,
        tvPendientes: TextView, tvCompletadas: TextView,
        pbDiario: ProgressBar, tvProgressLabel: TextView
    ) {
        val uid = auth.currentUser?.uid ?: return
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val fechaSimulada = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        Toast.makeText(requireContext(), "Debug: Saltando a mañana ($fechaSimulada)...", Toast.LENGTH_SHORT).show()

        reiniciarTareasAtomicamente(uid, fechaSimulada) {
            if (isAdded) {
                cargarDatos(tvName, tvPoints, tvPendientes, tvCompletadas, pbDiario, tvProgressLabel)
            }
        }
    }

    private fun verificarReinicioDiario(onDone: () -> Unit) {
        if (isResetting) { onDone(); return }
        val uid = auth.currentUser?.uid ?: run { onDone(); return }
        val hoyReal = GameUtils.fechaDeHoy()

        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val ultimoReinicio = snap.getString("ultimoReinicio") ?: ""
                if (ultimoReinicio.isEmpty() || ultimoReinicio < hoyReal) {
                    reiniciarTareasAtomicamente(uid, hoyReal, onDone)
                } else {
                    onDone()
                }
            }
            .addOnFailureListener { onDone() }
    }

    private fun reiniciarTareasAtomicamente(uid: String, hoyApp: String, onDone: () -> Unit) {
        if (isResetting) return
        isResetting = true

        db.collection("tasks").whereEqualTo("userId", uid).get().addOnSuccessListener { snaps ->
            val userRef = db.collection("users").document(uid)

            db.runTransaction { transaction ->
                val userSnap = transaction.get(userRef)
                val lastReset = userSnap.getString("ultimoReinicio") ?: ""

                // Bloqueo de seguridad: No reiniciar si ya se hizo para esta fecha o una futura
                if (lastReset >= hoyApp) return@runTransaction null

                var totalPenalty = 0
                for (doc in snaps) {
                    val completed    = doc.getBoolean("completed") ?: false
                    val recurrent    = doc.getBoolean("recurrent") ?: false
                    val createdDate  = doc.getString("createdDate") ?: ""
                    val penaltyValue = doc.getLong("penalizacion")?.toInt() ?: 0

                    // 1. Calcular penalización (solo si no se completó y es de un día pasado)
                    if (!completed && (recurrent || (createdDate.isNotEmpty() && createdDate < hoyApp))) {
                        totalPenalty += penaltyValue
                    }

                    // 2. Limpiar tareas (Borrarlas o resetearlas)
                    if (!recurrent && createdDate.isNotEmpty() && createdDate < hoyApp) {
                        transaction.delete(doc.reference)
                    } else if (recurrent) {
                        transaction.update(doc.reference, "completed", false)
                    }
                }

                val currentPoints = userSnap.getLong("points") ?: 0L
                val finalPoints   = (currentPoints - totalPenalty).coerceAtLeast(0L)

                transaction.update(userRef, "points", finalPoints)
                transaction.update(userRef, "ultimoReinicio", hoyApp)
                
                if (finalPoints <= 0) transaction.update(userRef, "bloqueada", true)

                totalPenalty
            }.addOnSuccessListener { penalty ->
                isResetting = false
                if (isAdded && penalty != null && (penalty as Int) > 0) {
                    Toast.makeText(requireContext(), "Resumen del día: -$penalty pts", Toast.LENGTH_LONG).show()
                }
                onDone()
            }.addOnFailureListener {
                isResetting = false
                onDone()
            }
        }.addOnFailureListener {
            isResetting = false
            onDone()
        }
    }

    private fun cargarDatos(
        tvName: TextView, tvPoints: TextView,
        tvPendientes: TextView, tvCompletadas: TextView,
        pbDiario: ProgressBar, tvProgressLabel: TextView
    ) {
        val uid = auth.currentUser?.uid ?: return

        userListener?.remove()
        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (!isAdded || snap == null || !snap.exists()) return@addSnapshotListener
                tvName.text   = snap.getString("name") ?: "Heroe"
                val pts = snap.getLong("points") ?: 0L
                tvPoints.text = "$pts pts"
                val hoyApp = snap.getString("ultimoReinicio") ?: GameUtils.fechaDeHoy()
                
                val level = snap.getLong("level") ?: 1L
                view?.findViewById<TextView>(R.id.tvHomeLevel)?.text =
                    "NV.$level ${GameUtils.nombreNivel(level.toInt()).uppercase()}"

                if (pts <= 0 && snap.getBoolean("bloqueada") == true) {
                    startActivity(Intent(requireContext(), LockActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
                
                actualizarConteoTareas(uid, hoyApp, tvPendientes, tvCompletadas, pbDiario, tvProgressLabel)
            }
    }

    private fun actualizarConteoTareas(uid: String, hoy: String, tvP: TextView, tvC: TextView, pb: ProgressBar, tvL: TextView) {
        tasksListener?.remove()
        tasksListener = db.collection("tasks").whereEqualTo("userId", uid)
            .addSnapshotListener { snaps, _ ->
                if (!isAdded || snaps == null) return@addSnapshotListener
                val hoyTasks = snaps.documents.filter { doc ->
                    doc.getBoolean("recurrent") == true || doc.getString("createdDate") == hoy
                }
                val total = hoyTasks.size
                val comp  = hoyTasks.count { it.getBoolean("completed") == true }
                tvP.text = "${total - comp}"
                tvC.text = "$comp"
                val prog = if (total > 0) (comp * 100 / total) else 0
                pb.progress = prog
                tvL.text = "$prog%"
            }
    }
}
