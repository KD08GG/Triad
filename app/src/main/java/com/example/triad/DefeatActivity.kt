package com.example.triad

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DefeatActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_defeat)

        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val puntosPerdidos = intent.getIntExtra("puntos_perdidos", 0)
        val tvPerdidos     = findViewById<TextView>(R.id.tvPuntosPercidos)
        val tvMensaje      = findViewById<TextView>(R.id.tvDefeatMessage)
        val btnContinuar   = findViewById<MaterialButton>(R.id.btnContinuarDerrota)

        tvPerdidos.text = "-$puntosPerdidos pts"
        tvMensaje.text  = mensajeMotivacional()

        // Restaurar HP a 30 para que el usuario pueda seguir jugando
        restaurarHpMinimo()

        btnContinuar.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun restaurarHpMinimo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update("hp", 30)
    }

    private fun mensajeMotivacional(): String = listOf(
        "Todos caemos. Los héroes se levantan.",
        "La derrota de hoy es la lección de mañana.",
        "No es el fin. Es el inicio del regreso.",
        "Cada héroe tiene su día oscuro. Este es el tuyo.",
        "Descansa, reagrúpate y vuelve más fuerte."
    ).random()
}