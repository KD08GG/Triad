package com.example.triad

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

data class ShopItem(
    val id: String = "",
    val title: String = "",
    val points: Int = 0
)

class ShopAdapter(
    private var items: List<ShopItem>,
    private val onBuy: (ShopItem) -> Unit
) : RecyclerView.Adapter<ShopAdapter.ShopViewHolder>() {

    class ShopViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvShopItemTitle)
        val price: TextView = view.findViewById(R.id.tvShopItemPrice)
        val btnBuy: MaterialButton = view.findViewById(R.id.btnBuyItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shop, parent, false)
        return ShopViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.price.text = "${item.points} Puntos"
        holder.btnBuy.setOnClickListener { onBuy(item) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ShopItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}