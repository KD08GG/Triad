package com.example.triad

import android.content.Context

// Solo guarda correo y contraseña en el dispositivo.
// El resto de los datos van a Firestore.
// para que el auto-login se active solo si el usuario pasa la verificación biométrica.

class Prefs(context: Context) {
    private val storage = context.getSharedPreferences("MY_APP_PREFS", Context.MODE_PRIVATE)

    fun saveCorreo(correo: String) {
        storage.edit().putString("KEY_CORREO", correo).apply()
    }

    fun savePassword(password: String) {
        storage.edit().putString("KEY_PASSWORD", password).apply()
    }

    fun getCorreo(): String = storage.getString("KEY_CORREO", "") ?: ""

    fun getPassword(): String = storage.getString("KEY_PASSWORD", "") ?: ""

    // Si hay credenciales guardadas para auto-login biométrico
    fun setSesionGuardada(valor: Boolean) {
        storage.edit().putBoolean("KEY_SESION_GUARDADA", valor).apply()
    }

    fun haySesionGuardada(): Boolean {
        return storage.getBoolean("KEY_SESION_GUARDADA", false)
    }

    // Limpia todo al cerrar sesion
    fun wipe() {
        storage.edit().clear().apply()
    }
}