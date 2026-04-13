package com.example.triad

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

data class Task(
    val id: String = "",
    val title: String = "",
    val category: String = "",
    val points: Int = 0,
    val icono: String = "",
    val completed: Boolean = false
)

class TaskAdapter(
    private var tasks: List<Task>,
    private val onComplete: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle    : TextView      = view.findViewById(R.id.tvTaskTitle)
        val tvCategory : TextView      = view.findViewById(R.id.tvTaskCategory)
        val tvPoints   : TextView      = view.findViewById(R.id.tvTaskPoints)
        val btnComplete: MaterialButton = view.findViewById(R.id.btnCompleteTask)
        val ivIcon     : ImageView     = view.findViewById(R.id.ivCategoryIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        holder.tvTitle.text    = task.title
        holder.tvCategory.text = task.category.uppercase()
        holder.tvPoints.text   = "+${task.points} pts"

        // Color dinámico según categoría
        val colorRes = when (task.category.uppercase()) {
            "CUERPO"   -> R.color.cat_cuerpo
            "ALMA"     -> R.color.cat_alma
            "ESPIRITU" -> R.color.cat_espiritu
            "DEBERES"  -> R.color.cat_deberes
            else       -> R.color.cat_personal
        }
        holder.ivIcon.backgroundTintList =
            ContextCompat.getColorStateList(holder.itemView.context, colorRes)

        holder.btnComplete.setOnClickListener { onComplete(task) }
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}