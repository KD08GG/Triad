package com.example.triad

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class DefeatActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_defeat)

        val puntosPerdidos = intent.getIntExtra("puntos_perdidos", 0)
        val tvPerdidos     = findViewById<TextView>(R.id.tvPuntosPercidos)
        val tvMensaje      = findViewById<TextView>(R.id.tvDefeatMessage)
        val btnContinuar   = findViewById<MaterialButton>(R.id.btnContinuarDerrota)

        tvPerdidos.text = "-$puntosPerdidos pts"
        tvMensaje.text  = mensajeMotivacional()

        // La restauración de puntos (300 pts) ocurre en LockActivity cuando el usuario
        // completa el reto de correr. DefeatActivity solo muestra la pantalla informativa.
        btnContinuar.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }
    }

    private fun mensajeMotivacional(): String = listOf(
        "Todos caemos. Los heroes se levantan.",
        "La derrota de hoy es la leccion de manana.",
        "No es el fin. Es el inicio del regreso.",
        "Cada heroe tiene su dia oscuro. Este es el tuyo.",
        "Descansa, reagrupate y vuelve mas fuerte."
    ).random()
}