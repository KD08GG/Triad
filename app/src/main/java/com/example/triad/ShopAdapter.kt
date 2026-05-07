package com.example.triad

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

data class ShopItem(
    val id: String = "",
    val title: String = "",
    val points: Int = 0,
    val isPurchased: Boolean = false,
    val purchasedAt: Long = 0
)

class ShopAdapter(
    private var items: List<ShopItem>,
    private val onItemClick: (ShopItem) -> Unit
) : RecyclerView.Adapter<ShopAdapter.ShopViewHolder>() {

    class ShopViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvShopItemName)
        val price: TextView = view.findViewById(R.id.tvShopItemCost)
        val btnAction: MaterialButton = view.findViewById(R.id.btnBuyItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shop, parent, false)
        return ShopViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        
        if (item.isPurchased) {
            // Formato de fecha para el historial
            val dateStr = if (item.purchasedAt > 0) {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                sdf.format(Date(item.purchasedAt))
            } else "Recientemente"
            
            holder.price.text = "Canjeado el $dateStr"
            holder.btnAction.text = "Canjeado"
            holder.btnAction.isEnabled = false // Bloqueado para que sea solo historial
            holder.btnAction.alpha = 0.5f
        } else {
            holder.price.text = "${item.points} pts"
            holder.btnAction.text = "Canjear"
            holder.btnAction.isEnabled = true
            holder.btnAction.alpha = 1.0f
        }

        holder.btnAction.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ShopItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
