package com.example.triad

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.android.material.button.MaterialButton

// ── Data model ────────────────────────────────────────────────────────────────
data class PurchasedReward(
    val id: String = "",
    val title: String = "",
    val cost: Int = 0,
    val purchasedAt: Long = 0L,
    val activated: Boolean = false
)

// ── Adapter ───────────────────────────────────────────────────────────────────
class PurchasedRewardAdapter(
    private var items: List<PurchasedReward>,
    private val showActivate: Boolean,
    private val onActivate: (PurchasedReward) -> Unit
) : RecyclerView.Adapter<PurchasedRewardAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView     = view.findViewById(R.id.tvRewardName)
        val tvDate: TextView     = view.findViewById(R.id.tvRewardDate)
        val btnActivate: MaterialButton = view.findViewById(R.id.btnActivate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_purchased_reward, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.title
        holder.tvDate.text = formatDate(item.purchasedAt)

        if (showActivate) {
            holder.btnActivate.visibility = View.VISIBLE
            holder.btnActivate.setOnClickListener { onActivate(item) }
            // Darken used items
            holder.itemView.alpha = 1.0f
        } else {
            holder.btnActivate.visibility = View.GONE
            holder.itemView.alpha = 0.5f   // historial: desaturado
        }
    }

    fun update(newItems: List<PurchasedReward>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatDate(ts: Long): String {
        if (ts == 0L) return "Fecha desconocida"
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy · HH:mm", java.util.Locale.getDefault())
        return "Canjeado el ${sdf.format(java.util.Date(ts))}"
    }
}

// ── Fragment ──────────────────────────────────────────────────────────────────
class MyRewardsFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: PurchasedRewardAdapter

    private val pendingList  = mutableListOf<PurchasedReward>()
    private val historyList  = mutableListOf<PurchasedReward>()
    private var showPending  = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_my_rewards, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db   = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val rv          = view.findViewById<RecyclerView>(R.id.rvMyRewards)
        val emptyState  = view.findViewById<LinearLayout>(R.id.emptyState)
        val tabPending  = view.findViewById<TextView>(R.id.tabPendientes)
        val tabHistory  = view.findViewById<TextView>(R.id.tabUsados)
        val btnBack     = view.findViewById<MaterialButton>(R.id.btnBack)

        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = PurchasedRewardAdapter(pendingList, showPending) { reward -> activarPremio(reward) }
        rv.adapter = adapter

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        tabPending.setOnClickListener {
            showPending = true
            setTabActive(tabPending, tabHistory)
            adapter = PurchasedRewardAdapter(pendingList, true) { reward -> activarPremio(reward) }
            rv.adapter = adapter
            toggleEmpty(emptyState, rv, pendingList.isEmpty())
        }
        tabHistory.setOnClickListener {
            showPending = false
            setTabActive(tabHistory, tabPending)
            adapter = PurchasedRewardAdapter(historyList, false) { }
            rv.adapter = adapter
            toggleEmpty(emptyState, rv, historyList.isEmpty())
        }

        cargarPremios(emptyState, rv)
    }

    private fun cargarPremios(emptyState: LinearLayout, rv: RecyclerView) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("premios_usuario")
            .whereEqualTo("userId", uid)
            .orderBy("purchasedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snaps, _ ->
                if (!isAdded || snaps == null) return@addSnapshotListener
                pendingList.clear()
                historyList.clear()

                for (doc in snaps) {
                    val reward = PurchasedReward(
                        id          = doc.id,
                        title       = doc.getString("title") ?: "",
                        cost        = doc.getLong("cost")?.toInt() ?: 0,
                        purchasedAt = doc.getLong("purchasedAt") ?: 0L,
                        activated   = doc.getBoolean("activated") ?: false
                    )
                    if (reward.activated) historyList.add(reward)
                    else pendingList.add(reward)
                }

                // Historial: más reciente arriba (por fecha de canje descendente)
                historyList.sortByDescending { it.purchasedAt }
                // Pendientes: también más recientes arriba
                pendingList.sortByDescending { it.purchasedAt }

                val currentList = if (showPending) pendingList else historyList
                adapter.update(currentList)
                toggleEmpty(emptyState, rv, currentList.isEmpty())
            }
    }

    private fun activarPremio(reward: PurchasedReward) {
        db.collection("premios_usuario").document(reward.id)
            .update("activated", true)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Premio activado. Disfrútalo!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun toggleEmpty(emptyState: LinearLayout, rv: RecyclerView, isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rv.visibility         = if (isEmpty) View.GONE    else View.VISIBLE
    }

    private fun setTabActive(active: TextView, inactive: TextView) {
        active.background   = ContextCompat.getDrawable(requireContext(), R.drawable.bg_teal_button)
        active.setTextColor(ContextCompat.getColor(requireContext(), R.color.background))
        inactive.background = null
        inactive.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
    }
}