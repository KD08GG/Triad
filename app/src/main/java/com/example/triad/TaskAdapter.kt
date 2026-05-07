package com.example.triad

import android.app.AlertDialog
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class TaskAdapter(
    private var allTasks: List<Task>,
    private val onComplete: (Task) -> Unit,
    private val onEdit: (Task, String, Int) -> Unit,
    private val onDelete: (Task) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_TASK = 1
        private const val TYPE_DIVIDER = 2
    }

    private var visibleItems: List<Any> = emptyList()
    private var activeFilter: String? = null

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTaskTitle)
        val tvCategory: TextView = view.findViewById(R.id.tvTaskCategory)
        val tvPoints: TextView = view.findViewById(R.id.tvTaskPoints)
        val btnComplete: MaterialButton = view.findViewById(R.id.btnCompleteTask)
        val btnEdit: MaterialButton = view.findViewById(R.id.btnEditTask)
        val ivIcon: ImageView = view.findViewById(R.id.ivCategoryIcon)
    }

    class DividerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.tvDividerLabel)
    }

    override fun getItemViewType(position: Int): Int =
        if (visibleItems[position] is Task) TYPE_TASK else TYPE_DIVIDER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_TASK) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            TaskViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task_divider, parent, false)
            DividerViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TaskViewHolder) {
            val task = visibleItems[position] as Task
            val ctx = holder.itemView.context

            holder.tvTitle.text = task.title
            holder.tvCategory.text = task.category.uppercase()
            holder.tvPoints.text = "+${task.points} pts"

            val (colorRes, bgRes) = when (GameUtils.normalizar(task.category)) {
                "cuerpo" -> Pair(R.color.cat_cuerpo, R.color.cat_cuerpo_bg)
                "alma" -> Pair(R.color.cat_alma, R.color.cat_alma_bg)
                "espiritu" -> Pair(R.color.cat_espiritu, R.color.cat_espiritu_bg)
                "deberes" -> Pair(R.color.cat_deberes, R.color.cat_deberes_bg)
                else -> Pair(R.color.primary, R.color.primary_surface)
            }

            holder.ivIcon.backgroundTintList = ContextCompat.getColorStateList(ctx, bgRes)
            holder.ivIcon.imageTintList = ContextCompat.getColorStateList(ctx, colorRes)
            holder.tvCategory.setTextColor(ContextCompat.getColor(ctx, colorRes))

            if (task.completed) {
                // Tarea completada: Tachado, opacidad baja y deshabilitada
                holder.tvTitle.paintFlags = holder.tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                holder.itemView.alpha = 0.5f
                holder.btnComplete.isEnabled = false
                holder.btnComplete.alpha = 0.3f
                holder.btnEdit.visibility = View.GONE
            } else {
                // Tarea pendiente
                holder.tvTitle.paintFlags = holder.tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.itemView.alpha = 1.0f
                holder.btnComplete.isEnabled = true
                holder.btnComplete.alpha = 1.0f
                holder.btnEdit.visibility = View.VISIBLE
            }

            holder.btnComplete.setOnClickListener { onComplete(task) }
            
            holder.btnEdit.setOnClickListener {
                val opciones = arrayOf("✏️ Editar misión", "🗑️ Eliminar misión")
                AlertDialog.Builder(ctx)
                    .setTitle(task.title)
                    .setItems(opciones) { _, which ->
                        when (which) {
                            0 -> mostrarDialogoEditar(ctx, task)
                            1 -> mostrarDialogoEliminar(ctx, task)
                        }
                    }
                    .show()
            }
        } else if (holder is DividerViewHolder) {
            holder.tvLabel.text = "Completadas hoy"
        }
    }

    private fun mostrarDialogoEditar(ctx: android.content.Context, task: Task) {
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val etTitulo = EditText(ctx).apply { setText(task.title); hint = "Título" }
        val etPuntos = EditText(ctx).apply { 
            setText(task.points.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Puntos"
        }
        layout.addView(etTitulo)
        layout.addView(etPuntos)

        AlertDialog.Builder(ctx)
            .setTitle("Editar misión")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val tit = etTitulo.text.toString().trim()
                val pts = etPuntos.text.toString().toIntOrNull() ?: 0
                if (tit.isNotEmpty() && pts > 0) onEdit(task, tit, pts)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoEliminar(ctx: android.content.Context, task: Task) {
        AlertDialog.Builder(ctx)
            .setTitle("Eliminar")
            .setMessage("¿Seguro que quieres borrar esta misión?")
            .setPositiveButton("Eliminar") { _, _ -> onDelete(task) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun getItemCount() = visibleItems.size

    fun updateTasks(newTasks: List<Task>) {
        allTasks = newTasks
        applyFilter(activeFilter)
    }

    fun applyFilter(category: String?) {
        activeFilter = category
        val filtered = if (category == null) {
            allTasks
        } else {
            val normalizedFilter = GameUtils.normalizar(category)
            allTasks.filter { GameUtils.normalizar(it.category) == normalizedFilter }
        }

        // Organización de la lista: Pendientes arriba, completadas abajo
        val pendientes = filtered.filter { !it.completed }.sortedByDescending { it.id }
        val completadas = filtered.filter { it.completed }.sortedByDescending { it.id }

        val finalItems = mutableListOf<Any>()
        finalItems.addAll(pendientes)
        if (completadas.isNotEmpty()) {
            finalItems.add("DIVIDER")
            finalItems.addAll(completadas)
        }
        visibleItems = finalItems
        notifyDataSetChanged()
    }
}
