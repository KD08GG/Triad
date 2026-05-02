package com.example.triad

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CreateTaskActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_task)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val etTitle = findViewById<EditText>(R.id.etTaskTitle)
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupCategory)
        val sliderPoints = findViewById<Slider>(R.id.sliderPoints)
        val switchRecurrent = findViewById<SwitchMaterial>(R.id.switchRecurrent)
        val btnSave = findViewById<Button>(R.id.btnSaveTask)

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val points = sliderPoints.value.toInt()
            val isRecurrent = switchRecurrent.isChecked
            
            val categoryId = toggleGroup.checkedButtonId
            val category = when(categoryId) {
                R.id.btnCatCuerpo -> "Cuerpo"
                R.id.btnCatAlma -> "Alma"
                R.id.btnCatEspiritu -> "Espíritu"
                R.id.btnCatDeberes -> "Deberes"
                else -> "General"
            }

            if (title.isNotEmpty()) {
                saveTask(title, category, points, isRecurrent)
            } else {
                Toast.makeText(this, "Por favor asigna un título a la misión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveTask(title: String, category: String, points: Int, recurrent: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        
        val task = hashMapOf(
            "title" to title,
            "category" to category,
            "points" to points,
            "recurrent" to recurrent,
            "completed" to false,
            "userId" to userId,
            "createdAt" to System.currentTimeMillis(),
            "penalizacion" to (points / 2) // Calcular penalización

        )

        db.collection("tasks")
            .add(task)
            .addOnSuccessListener {
                Toast.makeText(this, "Misión aceptada", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}