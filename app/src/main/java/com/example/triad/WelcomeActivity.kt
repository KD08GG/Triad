package com.example.triad

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class WelcomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val tareasSeleccionadas = mutableSetOf<TareaPredefinida>()

    private val tareasPredefinidas = listOf(
        TareaPredefinida("cuerpo_01", "Salud Física (Ejercicio)",  "CUERPO",   "🏋️", 50),
        TareaPredefinida("cuerpo_02", "Alimentación Saludable",    "CUERPO",   "🥗", 20),
        TareaPredefinida("cuerpo_03", "Descanso (7-8 hrs)",        "CUERPO",   "😴", 30),
        TareaPredefinida("alma_01",   "Relaciones y Social",       "ALMA",     "🤝", 40),
        TareaPredefinida("alma_02",   "Juegos / Recreación",       "ALMA",     "🎲", 30),
        TareaPredefinida("alma_03",   "Conocimiento Nuevo",        "ALMA",     "📚", 50),
        TareaPredefinida("espiritu_01","Meditación",               "ESPIRITU", "🧘", 60),
        TareaPredefinida("espiritu_02","Escritura Introspectiva",  "ESPIRITU", "✍️", 45),
        TareaPredefinida("espiritu_03","Lectura Espiritual",       "ESPIRITU", "📖", 50),
        TareaPredefinida("deberes_01", "Tender la cama",           "DEBERES",  "🛏️", 10),
        TareaPredefinida("deberes_02", "Lavar trastes",            "DEBERES",  "🫧", 15),
        TareaPredefinida("deberes_03", "Preparar comida",          "DEBERES",  "🍳", 15),
        TareaPredefinida("deberes_04", "Tareas / Estudio",         "DEBERES",  "📝", 30),
        TareaPredefinida("deberes_05", "Realizar trabajo",         "DEBERES",  "💼", 40),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

        val rvTareas     = findViewById<RecyclerView>(R.id.rvTareasPredefinidas)
        val etCustom     = findViewById<TextInputEditText>(R.id.etCustomTask)
        val btnAddCustom = findViewById<MaterialButton>(R.id.btnAddCustom)
        val btnContinuar = findViewById<MaterialButton>(R.id.btnContinuar)
        val tvCounter    = findViewById<TextView>(R.id.tvSelectionCounter)

        val listaCompleta = tareasPredefinidas.toMutableList()

        val adapter = WelcomeTaskAdapter(listaCompleta) { tarea, seleccionada ->
            if (seleccionada) tareasSeleccionadas.add(tarea)
            else tareasSeleccionadas.remove(tarea)

            val count = tareasSeleccionadas.size
            tvCounter.text = if (count == 0) "Selecciona al menos una misión"
            else "$count misión${if (count > 1) "es" else ""} seleccionada${if (count > 1) "s" else ""}"

            btnContinuar.isEnabled = count > 0
            btnContinuar.alpha = if (count > 0) 1f else 0.5f
        }

        rvTareas.layoutManager = LinearLayoutManager(this)
        rvTareas.adapter = adapter

        btnAddCustom.setOnClickListener {
            val texto = etCustom.text.toString().trim()
            if (texto.isNotEmpty()) {
                val nueva = TareaPredefinida(
                    id        = "custom_${System.currentTimeMillis()}",
                    titulo    = texto,
                    categoria = "PERSONAL",
                    icono     = "⭐",
                    puntos    = 25
                )
                listaCompleta.add(nueva)
                adapter.notifyListChanged()
                rvTareas.smoothScrollToPosition(listaCompleta.size - 1)
                etCustom.text?.clear()
            } else {
                Toast.makeText(this, "Escribe el nombre de tu misión", Toast.LENGTH_SHORT).show()
            }
        }

        btnContinuar.setOnClickListener {
            btnContinuar.isEnabled = false
            btnContinuar.text = "Guardando..."
            guardarTareasYContinuar()
        }
    }

    private fun guardarTareasYContinuar() {
        val userId = auth.currentUser?.uid ?: return
        val batch  = db.batch()
        val hoy    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        tareasSeleccionadas.forEach { tarea ->
            val ref  = db.collection("tasks").document()
            val data = hashMapOf(
                "title"        to tarea.titulo,
                "category"     to tarea.categoria,
                "points"       to tarea.puntos,
                "icono"        to tarea.icono,
                "recurrent"    to true,
                "completed"    to false,
                "userId"       to userId,
                "createdAt"    to System.currentTimeMillis(),
                "createdDate"  to hoy,
                "penalizacion" to (tarea.puntos / 2)
            )
            batch.set(ref, data)
        }

        val userRef = db.collection("users").document(userId)
        batch.update(userRef, mapOf(
            "onboardingCompleto" to true,
            "ultimoReinicio"     to hoy
        ))

        batch.commit()
            .addOnSuccessListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar. Intenta de nuevo.", Toast.LENGTH_SHORT).show()
                findViewById<MaterialButton>(R.id.btnContinuar).isEnabled = true
                findViewById<MaterialButton>(R.id.btnContinuar).text = "¡Comenzar mi aventura!"
            }
    }
}

data class TareaPredefinida(
    val id       : String,
    val titulo   : String,
    val categoria: String,
    val icono    : String,
    val puntos   : Int
)

class WelcomeTaskAdapter(
    private val tareas: MutableList<TareaPredefinida>,
    private val onToggle: (TareaPredefinida, Boolean) -> Unit
) : RecyclerView.Adapter<WelcomeTaskAdapter.VH>() {

    private val seleccionadas = mutableSetOf<String>()
    private val ordenCategorias = listOf("CUERPO", "ALMA", "ESPIRITU", "DEBERES", "PERSONAL")

    private fun calcularGrupos(): List<Pair<String?, TareaPredefinida?>> = buildList {
        ordenCategorias.forEach { cat ->
            val items = tareas.filter { it.categoria == cat }
            if (items.isNotEmpty()) {
                add(Pair(cat, null))
                items.forEach { add(Pair(null, it)) }
            }
        }
    }

    private var grupos = calcularGrupos()

    fun notifyListChanged() {
        grupos = calcularGrupos()
        notifyDataSetChanged()
    }

    companion object {
        const val VIEW_HEADER = 0
        const val VIEW_ITEM   = 1
    }

    override fun getItemViewType(position: Int) =
        if (grupos[position].first != null) VIEW_HEADER else VIEW_ITEM

    override fun getItemCount() = grupos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_HEADER)
            VH(inflater.inflate(R.layout.item_welcome_header, parent, false))
        else
            VH(inflater.inflate(R.layout.item_welcome_task, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (cat, tarea) = grupos[position]
        if (cat != null) holder.bindHeader(cat)
        else if (tarea != null) {
            val isSelected = tarea.id in seleccionadas
            holder.bindTask(tarea, isSelected) { selected ->
                if (selected) seleccionadas.add(tarea.id)
                else seleccionadas.remove(tarea.id)
                onToggle(tarea, selected)
            }
        }
    }

    inner class VH(val view: View) : RecyclerView.ViewHolder(view) {

        fun bindHeader(categoria: String) {
            val tv = view.findViewById<TextView>(R.id.tvCategoryHeader)
            val (label, colorRes) = when (categoria) {
                "CUERPO"   -> Pair("💪 Cuerpo",  R.color.cat_cuerpo)
                "ALMA"     -> Pair("💛 Alma",     R.color.cat_alma)
                "ESPIRITU" -> Pair("🔮 Espíritu", R.color.cat_espiritu)
                "DEBERES"  -> Pair("📋 Deberes",  R.color.cat_deberes)
                else       -> Pair("⭐ Personal", R.color.cat_personal)
            }
            tv.text = label
            tv.setTextColor(ContextCompat.getColor(view.context, colorRes))
        }

        fun bindTask(tarea: TareaPredefinida, isSelected: Boolean, onToggle: (Boolean) -> Unit) {
            val tvIcono  = view.findViewById<TextView>(R.id.tvTaskIcon)
            val tvTitulo = view.findViewById<TextView>(R.id.tvTaskTitle)
            val tvPuntos = view.findViewById<TextView>(R.id.tvTaskPoints)
            val btnCheck = view.findViewById<MaterialButton>(R.id.btnCompleteTask)
            val cardView = view.findViewById<View>(R.id.cardTask)

            tvIcono.text  = tarea.icono
            tvTitulo.text = tarea.titulo
            tvPuntos.text = "+${tarea.puntos} pts"

            actualizarEstiloCard(cardView, tvTitulo, btnCheck, tarea.categoria, isSelected)

            val toggle = {
                val nuevoEstado = !isSelected
                onToggle(nuevoEstado)
                notifyItemChanged(bindingAdapterPosition)
            }

            cardView.setOnClickListener { toggle() }
            btnCheck.setOnClickListener { toggle() }
        }

        private fun actualizarEstiloCard(card: View, tvTitulo: TextView, btnCheck: MaterialButton, cat: String, selected: Boolean) {
            val colorRes = when (cat) {
                "CUERPO"   -> R.color.cat_cuerpo
                "ALMA"     -> R.color.cat_alma
                "ESPIRITU" -> R.color.cat_espiritu
                "DEBERES"  -> R.color.cat_deberes
                else       -> R.color.cat_personal
            }
            card.setBackgroundResource(if (selected) R.drawable.bg_task_selected else R.drawable.bg_task_unselected)
            
            val ctx = card.context
            if (selected) {
                btnCheck.iconTint = ContextCompat.getColorStateList(ctx, colorRes)
                btnCheck.alpha = 1f
            } else {
                btnCheck.iconTint = ContextCompat.getColorStateList(ctx, R.color.text_hint)
                btnCheck.alpha = 0.3f
            }
            
            tvTitulo.setTextColor(
                ContextCompat.getColor(ctx, if (selected) colorRes else R.color.text_primary)
            )
        }
    }
}
