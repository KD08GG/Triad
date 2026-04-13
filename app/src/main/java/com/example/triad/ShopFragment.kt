package com.example.triad

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ShopFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: ShopAdapter
    private val shopItems = mutableListOf<ShopItem>()
    private var userPoints: Long = 0

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

        rvShop.layoutManager = LinearLayoutManager(requireContext())
        adapter = ShopAdapter(shopItems) { item -> intentarCompra(item) }
        rvShop.adapter = adapter

        cargarPuntos(tvWallet)
        cargarRecompensas()
    }

    private fun cargarPuntos(tvWallet: TextView) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    userPoints = snap.getLong("points") ?: 0L
                    tvWallet.text = "$userPoints pts disponibles"
                }
            }
    }

    private fun cargarRecompensas() {
        db.collection("recompensas").get()
            .addOnSuccessListener { result ->
                shopItems.clear()
                if (result.isEmpty) {
                    // Fallback hardcodeado si Firestore está vacío
                    shopItems.addAll(listOf(
                        ShopItem("r1", "📱 30 min Redes Sociales",   200),
                        ShopItem("r2", "🍕 Un snack delicioso",       300),
                        ShopItem("r3", "🎬 Ver un capítulo de serie", 400),
                        ShopItem("r4", "🎮 Noche de videojuegos",     600),
                        ShopItem("r5", "🍔 Comida chatarra",          500),
                        ShopItem("r6", "🌴 Día libre de opcionales",  800),
                        ShopItem("r7", "🎥 Ver una película",         700),
                    ))
                } else {
                    for (doc in result) {
                        val item = ShopItem(
                            id     = doc.id,
                            title  = "${doc.getString("icono") ?: ""} ${doc.getString("titulo") ?: ""}".trim(),
                            points = (doc.getLong("costo_puntos") ?: 0L).toInt()
                        )
                        shopItems.add(item)
                    }
                }
                adapter.updateItems(shopItems)
            }
    }

    private fun intentarCompra(item: ShopItem) {
        if (userPoints < item.points) {
            Toast.makeText(requireContext(), "Necesitas ${item.points - userPoints} pts más", Toast.LENGTH_SHORT).show()
            return
        }
        val uid     = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        db.runTransaction { transaction ->
            val snap    = transaction.get(userRef)
            val current = snap.getLong("points") ?: 0L
            if (current < item.points) throw Exception("Puntos insuficientes")
            transaction.update(userRef, "points", current - item.points)
        }.addOnSuccessListener {
            Toast.makeText(requireContext(), "¡Recompensa canjeada! 🎉\n${item.title}", Toast.LENGTH_LONG).show()
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}