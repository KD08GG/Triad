package com.example.triad

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class TasksFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: TaskAdapter

    private val allTasks = mutableListOf<Task>()
    private var activeChip: TextView? = null
    private var tasksListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_tasks, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val rvTasks = view.findViewById<RecyclerView>(R.id.rvTasks)
        val fab     = view.findViewById<FloatingActionButton>(R.id.fabAddTask)

        rvTasks.layoutManager = LinearLayoutManager(requireContext())
        adapter = TaskAdapter(
            allTasks,
            onComplete = { task -> completarTarea(task) },
            onEdit     = { task, nuevoTitulo, nuevosPuntos -> editarTarea(task, nuevoTitulo, nuevosPuntos) },
            onDelete   = { task -> eliminarTarea(task) }
        )
        rvTasks.adapter = adapter

        fab.setOnClickListener {
            startActivity(Intent(requireContext(), CreateTaskActivity::class.java))
        }

        val chipAll      = view.findViewById<TextView>(R.id.chipAll)
        val chipCuerpo   = view.findViewById<TextView>(R.id.chipCuerpo)
        val chipAlma     = view.findViewById<TextView>(R.id.chipAlma)
        val chipEspiritu = view.findViewById<TextView>(R.id.chipEspiritu)

        fun selectChip(chip: TextView, filter: String?) {
            activeChip?.let { setChipInactive(it) }
            setChipActive(chip, filter)
            activeChip = chip
            adapter.applyFilter(filter)
        }

        chipAll.setOnClickListener      { selectChip(chipAll,      null) }
        chipCuerpo.setOnClickListener   { selectChip(chipCuerpo,   "Cuerpo") }
        chipAlma.setOnClickListener     { selectChip(chipAlma,     "Alma") }
        chipEspiritu.setOnClickListener { selectChip(chipEspiritu, "Espiritu") }

        setChipActive(chipAll, null)
        activeChip = chipAll

        escucharCambios()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tasksListener?.remove()
        userListener?.remove()
    }

    private fun setChipActive(chip: TextView, filter: String?) {
        val ctx = requireContext()
        val bgDrawable = when (GameUtils.normalizar(filter ?: "")) {
            "cuerpo"   -> R.drawable.bg_pill_cuerpo
            "alma"     -> R.drawable.bg_pill_alma
            "espiritu" -> R.drawable.bg_pill_espiritu
            else       -> R.drawable.bg_pill_teal
        }
        val textColor = when (GameUtils.normalizar(filter ?: "")) {
            "cuerpo"   -> R.color.cat_cuerpo
            "alma"     -> R.color.cat_alma
            "espiritu" -> R.color.cat_espiritu
            else       -> R.color.primary
        }
        chip.background = ContextCompat.getDrawable(ctx, bgDrawable)
        chip.setTextColor(ContextCompat.getColor(ctx, textColor))
        chip.alpha = 1.0f
    }

    private fun setChipInactive(chip: TextView) {
        val ctx = requireContext()
        chip.background = ContextCompat.getDrawable(ctx, R.drawable.bg_surface_card)
        chip.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        chip.alpha = 0.7f
    }

    /**
     * Sincronización Total: Escucha primero la "fecha de hoy" del usuario (ultimoReinicio)
     * y luego carga las tareas filtrando por esa fecha exacta.
     */
    private fun escucharCambios() {
        val uid = auth.currentUser?.uid ?: return

        userListener?.remove()
        userListener = db.collection("users").document(uid)
            .addSnapshotListener { userSnap, _ ->
                if (!isAdded || userSnap == null || !userSnap.exists()) return@addSnapshotListener
                
                // La fecha de "hoy" para la app es la del último reinicio
                val fechaHoyApp = userSnap.getString("ultimoReinicio") ?: GameUtils.fechaDeHoy()
                cargarTareas(uid, fechaHoyApp)
            }
    }

    private fun cargarTareas(uid: String, hoy: String) {
        tasksListener?.remove()
        tasksListener = db.collection("tasks")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { value, error ->
                if (!isAdded || error != null || value == null) return@addSnapshotListener

                allTasks.clear()
                for (doc in value) {
                    val task        = doc.toObject(Task::class.java).copy(id = doc.id)
                    val recurrent   = doc.getBoolean("recurrent") ?: false
                    val createdDate = doc.getString("createdDate") ?: ""

                    // Solo mostramos tareas si son recurrentes O si se crearon en la "fecha de hoy" de la app
                    if (recurrent || createdDate == hoy) {
                        allTasks.add(task)
                    }
                }
                adapter.updateTasks(allTasks)
            }
    }

    private fun completarTarea(task: Task) {
        if (!isAdded || task.completed) return
        db.collection("tasks").document(task.id)
            .update("completed", true)
            .addOnSuccessListener {
                actualizarPuntosYStats(task)
            }
    }

    private fun editarTarea(task: Task, nuevoTitulo: String, nuevosPuntos: Int) {
        if (!isAdded) return
        val nuevaPenalizacion = nuevosPuntos / 2
        db.collection("tasks").document(task.id)
            .update(mapOf(
                "title"        to nuevoTitulo,
                "points"       to nuevosPuntos,
                "penalizacion" to nuevaPenalizacion
            ))
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "Misión actualizada", Toast.LENGTH_SHORT).show()
            }
    }

    private fun eliminarTarea(task: Task) {
        if (!isAdded) return
        db.collection("tasks").document(task.id)
            .delete()
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "Misión eliminada", Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarPuntosYStats(task: Task) {
        val uid     = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)

        val statField = when (GameUtils.normalizar(task.category)) {
            "cuerpo"   -> "statCuerpo"
            "alma"     -> "statAlma"
            "espiritu" -> "statEspiritu"
            else       -> null
        }

        db.runTransaction { transaction ->
            val snap     = transaction.get(userRef)
            val actual   = snap.getLong("points") ?: 0L
            val nuevoPts = actual + task.points
            val nivel    = GameUtils.calcularNivel(nuevoPts.toInt())

            transaction.update(userRef, "points", nuevoPts)
            transaction.update(userRef, "level",  nivel.toLong())

            if (statField != null) {
                val statActual = snap.getLong(statField) ?: 0L
                transaction.update(userRef, statField, statActual + 1L)
            }
        }
    }
}
