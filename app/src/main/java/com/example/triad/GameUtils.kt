package com.example.triad

import java.text.SimpleDateFormat
import java.util.*

object GameUtils {

    fun calcularNivel(puntos: Int): Int = when {
        puntos < 500   -> 1
        puntos < 1200  -> 2
        puntos < 2500  -> 3
        puntos < 4500  -> 4
        puntos < 7000  -> 5
        puntos < 10000 -> 6
        else           -> 7
    }

    fun nombreNivel(level: Int): String = when (level) {
        1 -> "Aprendiz"; 2 -> "Guerrero"; 3 -> "Explorador"
        4 -> "Guardián"; 5 -> "Campeón";  6 -> "Maestro"
        7 -> "Legendario"; else -> "Héroe"
    }

    fun rangoPuntos(level: Int): Pair<Long, Long> = when (level) {
        1    -> Pair(0L,     500L)
        2    -> Pair(500L,   1200L)
        3    -> Pair(1200L,  2500L)
        4    -> Pair(2500L,  4500L)
        5    -> Pair(4500L,  7000L)
        6    -> Pair(7000L,  10000L)
        else -> Pair(10000L, 10000L)
    }

    fun fechaDeHoy(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun obtenerTiempoApp(ultimoReinicio: String): Long {
        val hoyReal = fechaDeHoy()
        if (ultimoReinicio.isEmpty() || ultimoReinicio <= hoyReal) return System.currentTimeMillis()

        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateSimulada = sdf.parse(ultimoReinicio) ?: return System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { 
                time = dateSimulada 
                val ahora = Calendar.getInstance()
                set(Calendar.HOUR_OF_DAY, ahora.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, ahora.get(Calendar.MINUTE))
            }
            cal.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun saludoDelDia(): String {
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hora) {
            in 5..11  -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else      -> "Buenas noches"
        }
    }

    fun normalizar(texto: String): String =
        texto.lowercase()
            .replace("á", "a").replace("é", "e")
            .replace("í", "i").replace("ó", "o")
            .replace("ú", "u")
}
