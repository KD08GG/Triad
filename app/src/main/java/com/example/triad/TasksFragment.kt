package com.example.triad

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TasksFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: TaskAdapter
    private val taskList = mutableListOf<Task>()

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
        adapter = TaskAdapter(taskList) { task -> completarTarea(task) }
        rvTasks.adapter = adapter

        fab.setOnClickListener {
            // Abre CreateTaskActivity (ya existente)
            startActivity(android.content.Intent(requireContext(), CreateTaskActivity::class.java))
        }

        cargarTareas()
    }

    private fun cargarTareas() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("tasks")
            .whereEqualTo("userId", uid)
            .whereEqualTo("completed", false)
            .addSnapshotListener { value, error ->
                if (error != null || value == null) return@addSnapshotListener
                taskList.clear()
                for (doc in value) {
                    val task = doc.toObject(Task::class.java).copy(id = doc.id)
                    taskList.add(task)
                }
                adapter.updateTasks(taskList)
            }
    }

    private fun completarTarea(task: Task) {
        db.collection("tasks").document(task.id)
            .update("completed", true)
            .addOnSuccessListener {
                actualizarPuntos(task.points)
                Toast.makeText(requireContext(), "¡Misión cumplida! +${task.points} pts", Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarPuntos(puntos: Int) {
        val uid     = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        db.runTransaction { transaction ->
            val snap   = transaction.get(userRef)
            val actual = snap.getLong("points") ?: 0L
            val nivel  = calcularNivel((actual + puntos).toInt())
            transaction.update(userRef, "points", actual + puntos)
            transaction.update(userRef, "level", nivel.toLong())
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Error al actualizar puntos", Toast.LENGTH_SHORT).show()
        }
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