package com.example.triad

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ShopFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: ShopAdapter
    private val shopItems = mutableListOf<ShopItem>()
    private var userPoints: Long = 0

    private val recompensasFallback = listOf(
        ShopItem("r1",  "📱 15 min Redes Sociales",    60),   // ~1/3 de un día bueno
        ShopItem("r2",  "🍕 Un snack a tu gusto",       80),   // alcanzable en 1 día
        ShopItem("r3",  "🎬 Un capítulo de serie",     120),   // 1-2 días
        ShopItem("r4",  "🎮 1 hora de videojuegos",    150),   // 1-2 días
        ShopItem("r5",  "📱 30 min Redes Sociales",    100),   // 1 día completo
        ShopItem("r6",  "🍔 Comida chatarra",          150),   // 2 días
        ShopItem("r7",  "🎥 Una película completa",    200),   // 2 días
        ShopItem("r8",  "🌴 Día de misiones reducidas",250),   // 2-3 días
        ShopItem("r9",  "🎮 Noche de videojuegos",     300),   // 3 días
        ShopItem("r10", "🛍️ Gusto personal pequeño",   400),   // 4-5 días
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_shop, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val tvWallet = view.findViewById<TextView>(R.id.tvWalletPoints)
        val rvShop   = view.findViewById<RecyclerView>(R.id.rvShopItems)
        val fab      = view.findViewById<FloatingActionButton>(R.id.fabAddReward)

        rvShop.layoutManager = LinearLayoutManager(requireContext())
        adapter = ShopAdapter(shopItems) { item -> intentarCompra(item) }
        rvShop.adapter = adapter

        // FAB: agregar recompensa personalizada
        fab.setOnClickListener { mostrarDialogoAgregarRecompensa() }

        cargarPuntos(tvWallet)
        cargarRecompensas()
    }

    private fun cargarPuntos(tvWallet: TextView) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (!isAdded || snap == null || !snap.exists()) return@addSnapshotListener
                userPoints = snap.getLong("points") ?: 0L
                tvWallet.text = "💰 $userPoints pts disponibles"
            }
    }

    private fun cargarRecompensas() {
        db.collection("recompensas").get()
            .addOnSuccessListener { result ->
                shopItems.clear()
                if (result.isEmpty) {
                    shopItems.addAll(recompensasFallback)
                } else {
                    for (doc in result) {
                        shopItems.add(ShopItem(
                            id     = doc.id,
                            title  = "${doc.getString("icono") ?: ""} ${doc.getString("titulo") ?: ""}".trim(),
                            points = (doc.getLong("costo_puntos") ?: 0L).toInt()
                        ))
                    }
                }
                // También cargar recompensas personalizadas del usuario
                cargarRecompensasPersonalizadas()
            }
            .addOnFailureListener {
                shopItems.addAll(recompensasFallback)
                adapter.updateItems(shopItems)
            }
    }

    private fun cargarRecompensasPersonalizadas() {
        val uid = auth.currentUser?.uid ?: run { adapter.updateItems(shopItems); return }
        db.collection("recompensas_usuario").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    shopItems.add(ShopItem(
                        id     = doc.id,
                        title  = "${doc.getString("icono") ?: "⭐"} ${doc.getString("titulo") ?: ""}".trim(),
                        points = (doc.getLong("costo_puntos") ?: 0L).toInt()
                    ))
                }
                adapter.updateItems(shopItems)
            }
            .addOnFailureListener { adapter.updateItems(shopItems) }
    }

    // ─── AGREGAR RECOMPENSA PERSONALIZADA ─────────────────────────────────────
    private fun mostrarDialogoAgregarRecompensa() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_reward, null)

        val etNombre = dialogView.findViewById<EditText>(R.id.etRewardName)
        val etPuntos = dialogView.findViewById<EditText>(R.id.etRewardPoints)
        val etIcono  = dialogView.findViewById<EditText>(R.id.etRewardIcon)

        AlertDialog.Builder(requireContext())
            .setTitle("🎁 Nueva recompensa")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val nombre = etNombre.text.toString().trim()
                val puntos = etPuntos.text.toString().toIntOrNull() ?: 0
                val icono  = etIcono.text.toString().trim().ifEmpty { "⭐" }

                if (nombre.isEmpty() || puntos <= 0) {
                    Toast.makeText(requireContext(), "Nombre y puntos son obligatorios", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                guardarRecompensaPersonalizada(nombre, puntos, icono)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarRecompensaPersonalizada(nombre: String, puntos: Int, icono: String) {
        val uid = auth.currentUser?.uid ?: return
        val data = hashMapOf(
            "titulo"       to nombre,
            "icono"        to icono,
            "costo_puntos" to puntos.toLong(),
            "userId"       to uid,
            "createdAt"    to System.currentTimeMillis()
        )
        db.collection("recompensas_usuario").add(data)
            .addOnSuccessListener { ref ->
                val nueva = ShopItem(ref.id, "$icono $nombre", puntos)
                shopItems.add(nueva)
                adapter.updateItems(shopItems)
                Toast.makeText(requireContext(), "¡Recompensa añadida! 🎉", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al guardar", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── COMPRA ───────────────────────────────────────────────────────────────
    private fun intentarCompra(item: ShopItem) {
        if (userPoints < item.points) {
            val faltan = item.points - userPoints
            Toast.makeText(requireContext(), "Te faltan $faltan pts 💸", Toast.LENGTH_SHORT).show()
            return
        }
        // Confirmación antes de gastar
        AlertDialog.Builder(requireContext())
            .setTitle("¿Canjear recompensa?")
            .setMessage("${item.title}\nCosto: ${item.points} pts\nSaldo actual: $userPoints pts")
            .setPositiveButton("¡Canjear!") { _, _ ->
                ejecutarCompra(item)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarCompra(item: ShopItem) {
        val uid     = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        db.runTransaction { transaction ->
            val snap    = transaction.get(userRef)
            val current = snap.getLong("points") ?: 0L
            if (current < item.points) throw Exception("Puntos insuficientes")
            transaction.update(userRef, "points", current - item.points)
        }.addOnSuccessListener {
            Toast.makeText(requireContext(), "¡Disfrutalo! 🎉\n${item.title}", Toast.LENGTH_LONG).show()
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}