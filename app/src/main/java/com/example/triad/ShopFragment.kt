package com.example.triad

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ShopFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: ShopAdapter

    private val catalogItems    = mutableListOf<ShopItem>()
    private val purchasedItems  = mutableListOf<ShopItem>()
    private var userPoints: Long = 0
    private var ultimoReinicio: String = ""
    private var isCatalogView   = true

    private var pointsListener:    ListenerRegistration? = null
    private var purchasedListener: ListenerRegistration? = null

    private val recompensasFallback = listOf(
        ShopItem("r1",  "15 min Redes Sociales",    60),
        ShopItem("r2",  "Un snack a tu gusto",       80),
        ShopItem("r3",  "Un capitulo de serie",     120),
        ShopItem("r4",  "1 hora de videojuegos",    150),
        ShopItem("r5",  "30 min Redes Sociales",    100),
        ShopItem("r6",  "Comida chatarra",          150),
        ShopItem("r7",  "Una pelicula completa",    200),
        ShopItem("r8",  "Dia de misiones reducidas",250),
        ShopItem("r9",  "Noche de videojuegos",     300),
        ShopItem("r10", "Gusto personal pequeño",   400)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_shop, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val tvWallet      = view.findViewById<TextView>(R.id.tvWalletPoints)
        val tvCoinCount   = view.findViewById<TextView>(R.id.tvCoinCount)
        val rvShop        = view.findViewById<RecyclerView>(R.id.rvShopItems)
        val fab           = view.findViewById<FloatingActionButton>(R.id.fabAddReward)
        val tabExplorar   = view.findViewById<TextView>(R.id.tabExplorar)
        val tabMisPremios = view.findViewById<TextView>(R.id.tabMisPremios)

        rvShop.layoutManager = LinearLayoutManager(requireContext())
        adapter = ShopAdapter(mutableListOf()) { item ->
            if (!item.isPurchased) intentarCompra(item)
        }
        rvShop.adapter = adapter

        fab.setOnClickListener { mostrarDialogoAgregarRecompensa() }

        tabExplorar.setOnClickListener {
            isCatalogView = true
            setTabActive(tabExplorar, tabMisPremios)
            adapter.updateItems(catalogItems)
            fab.show()
        }

        tabMisPremios.setOnClickListener {
            isCatalogView = false
            setTabActive(tabMisPremios, tabExplorar)
            adapter.updateItems(purchasedItems)
            fab.hide()
        }

        cargarPuntos(tvWallet, tvCoinCount)
        cargarCatalogo()
        cargarMisPremios()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pointsListener?.remove()
        purchasedListener?.remove()
    }

    private fun cargarPuntos(tvWallet: TextView, tvCoin: TextView) {
        val uid = auth.currentUser?.uid ?: return
        pointsListener?.remove()
        pointsListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (!isAdded || snap == null || !snap.exists()) return@addSnapshotListener
                userPoints     = snap.getLong("points") ?: 0L
                ultimoReinicio = snap.getString("ultimoReinicio") ?: GameUtils.fechaDeHoy()
                
                tvWallet.text = "$userPoints pts disponibles"
                tvCoin.text   = "$userPoints SC"
            }
    }

    private fun cargarCatalogo() {
        db.collection("recompensas").get()
            .addOnSuccessListener { result ->
                catalogItems.clear()
                if (result.isEmpty) {
                    catalogItems.addAll(recompensasFallback)
                } else {
                    for (doc in result) {
                        catalogItems.add(ShopItem(
                            id     = doc.id,
                            title  = doc.getString("titulo") ?: "",
                            points = (doc.getLong("costo_puntos") ?: 0L).toInt()
                        ))
                    }
                }
                cargarRecompensasPersonalizadas()
            }
    }

    private fun cargarRecompensasPersonalizadas() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("recompensas_usuario").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { result ->
                if (!isAdded) return@addOnSuccessListener
                for (doc in result) {
                    catalogItems.add(ShopItem(
                        id     = doc.id,
                        title  = doc.getString("titulo") ?: "",
                        points = (doc.getLong("costo_puntos") ?: 0L).toInt()
                    ))
                }
                if (isCatalogView) adapter.updateItems(catalogItems)
            }
    }

    private fun cargarMisPremios() {
        val uid = auth.currentUser?.uid ?: return
        purchasedListener?.remove()
        purchasedListener = db.collection("premios_usuario")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { result, _ ->
                if (!isAdded || result == null) return@addSnapshotListener
                purchasedItems.clear()
                for (doc in result) {
                    purchasedItems.add(ShopItem(
                        id          = doc.id,
                        title       = doc.getString("title") ?: "",
                        points      = (doc.getLong("cost") ?: 0L).toInt(),
                        isPurchased = true,
                        purchasedAt = doc.getLong("purchasedAt") ?: 0L
                    ))
                }
                if (!isCatalogView) adapter.updateItems(purchasedItems)
            }
    }

    private fun mostrarDialogoAgregarRecompensa() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_reward, null)
        val etNombre   = dialogView.findViewById<TextInputEditText>(R.id.etRewardName)
        val etPuntos   = dialogView.findViewById<TextInputEditText>(R.id.etRewardPoints)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val nombre = etNombre?.text.toString().trim()
                val puntos = etPuntos?.text.toString().toIntOrNull() ?: 0
                if (nombre.isNotEmpty() && puntos > 0) guardarRecompensaPersonalizada(nombre, puntos)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarRecompensaPersonalizada(nombre: String, puntos: Int) {
        val uid = auth.currentUser?.uid ?: return
        val data = hashMapOf(
            "titulo"       to nombre,
            "costo_puntos" to puntos.toLong(),
            "userId"       to uid,
            "createdAt"    to System.currentTimeMillis()
        )
        db.collection("recompensas_usuario").add(data).addOnSuccessListener { ref ->
            if (!isAdded) return@addOnSuccessListener
            catalogItems.add(ShopItem(ref.id, nombre, puntos))
            if (isCatalogView) adapter.updateItems(catalogItems)
        }
    }

    private fun intentarCompra(item: ShopItem) {
        if (userPoints < item.points) {
            Toast.makeText(requireContext(), "Puntos insuficientes", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Canjear recompensa")
            .setMessage("Canjear ${item.title} por ${item.points} pts?")
            .setPositiveButton("Canjear") { _, _ -> ejecutarCompra(item) }
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
            if (!isAdded) return@addOnSuccessListener
            
            // USAR TIEMPO SIMULADO SI EXISTE
            val timestampSimulado = GameUtils.obtenerTiempoApp(ultimoReinicio)

            db.collection("premios_usuario").add(hashMapOf(
                "userId"      to uid,
                "title"       to item.title,
                "cost"        to item.points,
                "purchasedAt" to timestampSimulado
            )).addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                Toast.makeText(requireContext(), "Canjeado con exito!", Toast.LENGTH_SHORT).show()
                view?.findViewById<TextView>(R.id.tabMisPremios)?.performClick()
            }
        }.addOnFailureListener {
            if (!isAdded) return@addOnFailureListener
            Toast.makeText(requireContext(), "Error al canjear: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setTabActive(active: TextView, inactive: TextView) {
        if (!isAdded) return
        active.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_teal_button)
        active.setTextColor(ContextCompat.getColor(requireContext(), R.color.background))
        active.alpha = 1.0f
        inactive.background = null
        inactive.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        inactive.alpha = 0.7f
    }
}
