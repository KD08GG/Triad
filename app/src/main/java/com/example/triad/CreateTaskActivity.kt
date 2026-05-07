package com.example.triad

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CreateTaskActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var selectedCategory = "Cuerpo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_task)

        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val etTitle      = findViewById<EditText>(R.id.etTaskTitle)
        val etPoints     = findViewById<TextInputEditText>(R.id.etPointsValue)
        val switchRec    = findViewById<SwitchMaterial>(R.id.switchRecurrent)
        val btnSave      = findViewById<android.widget.Button>(R.id.btnSaveTask)

        val btnCuerpo   = findViewById<LinearLayout>(R.id.btnCatCuerpo)
        val btnAlma     = findViewById<LinearLayout>(R.id.btnCatAlma)
        val btnEspiritu = findViewById<LinearLayout>(R.id.btnCatEspiritu)
        val btnDeberes  = findViewById<LinearLayout>(R.id.btnCatDeberes)
        val allButtons  = listOf(btnCuerpo, btnAlma, btnEspiritu, btnDeberes)

        fun selectCategory(cat: String, selected: LinearLayout) {
            selectedCategory = cat
            allButtons.forEach { it.alpha = if (it == selected) 1f else 0.4f }
        }

        btnCuerpo.setOnClickListener   { selectCategory("Cuerpo",   btnCuerpo) }
        btnAlma.setOnClickListener     { selectCategory("Alma",     btnAlma) }
        btnEspiritu.setOnClickListener { selectCategory("Espíritu", btnEspiritu) }
        btnDeberes.setOnClickListener  { selectCategory("Deberes",  btnDeberes) }

        allButtons.forEach { it.alpha = if (it == btnCuerpo) 1f else 0.4f }

        btnSave.setOnClickListener {
            val title  = etTitle.text.toString().trim()
            val points = etPoints.text.toString().toIntOrNull() ?: 50
            val isRec  = switchRec.isChecked

            if (title.isEmpty()) {
                Toast.makeText(this, "Asigna un título a la misión", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Buscar la fecha de la App (ultimoReinicio) antes de guardar
            obtenerFechaAppYGuardar(title, selectedCategory, points, isRec)
        }
    }

    private fun obtenerFechaAppYGuardar(title: String, category: String, points: Int, recurrent: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        
        db.collection("users").document(uid).get().addOnSuccessListener { snap ->
            // Si la App está en "mañana" (simulado), guardamos la tarea con esa fecha
            val hoyApp = snap.getString("ultimoReinicio") ?: GameUtils.fechaDeHoy()
            saveTask(title, category, points, recurrent, hoyApp)
        }.addOnFailureListener {
            saveTask(title, category, points, recurrent, GameUtils.fechaDeHoy())
        }
    }

    private fun saveTask(title: String, category: String, points: Int, recurrent: Boolean, date: String) {
        val userId = auth.currentUser?.uid ?: return

        val task = hashMapOf(
            "title"        to title,
            "category"     to category,
            "points"       to points,
            "recurrent"    to recurrent,
            "completed"    to false,
            "userId"       to userId,
            "createdAt"    to System.currentTimeMillis(),
            "createdDate"  to date,
            "penalizacion" to (points / 2)
        )

        db.collection("tasks").add(task)
            .addOnSuccessListener {
                Toast.makeText(this, "Misión aceptada!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
