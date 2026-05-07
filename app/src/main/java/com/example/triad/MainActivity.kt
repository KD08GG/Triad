package com.example.triad

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        if (intent.getBooleanExtra("desbloqueado", false)) {
            Toast.makeText(
                this,
                "2km completados! Bienvenido de vuelta. +${RunningTrackerService.REWARD_PTS} pts",
                Toast.LENGTH_LONG
            ).show()
        }

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
            bottomNav.selectedItemId = R.id.nav_home
        }

        bottomNav.setOnItemSelectedListener { item ->
            // FIX: Evitar recargar el mismo fragment si ya esta seleccionado.
            // El comportamiento anterior instanciaba un Fragment nuevo en cada tap,
            // incluyendo al tab ya activo. Esto causaba que los listeners de Firestore
            // se acumularan (el fragment anterior no se destruia antes de crear el nuevo
            // con el replace sin addToBackStack) dejando snapshots huerfanos activos.
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home   -> {
                    if (currentFragment is HomeFragment) return@setOnItemSelectedListener true
                    HomeFragment()
                }
                R.id.nav_tasks  -> {
                    if (currentFragment is TasksFragment) return@setOnItemSelectedListener true
                    TasksFragment()
                }
                R.id.nav_shop   -> {
                    if (currentFragment is ShopFragment) return@setOnItemSelectedListener true
                    ShopFragment()
                }
                R.id.nav_avatar -> {
                    if (currentFragment is AvatarFragment) return@setOnItemSelectedListener true
                    AvatarFragment()
                }
                else -> return@setOnItemSelectedListener false
            }
            loadFragment(fragment)
            true
        }
    }

    fun loadFragment(fragment: Fragment) {
        // FIX: No agregar al back stack.
        // Agregar al back stack en bottom navigation es un anti-patron que causa
        // que el boton "atras" navegue a tabs anteriores en vez de salir de la app,
        // y que los fragments del historial mantengan sus listeners activos.
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
