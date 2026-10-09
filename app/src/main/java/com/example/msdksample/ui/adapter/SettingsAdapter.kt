package com.example.msdksample.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.msdksample.databinding.ItemSettingsBinding

data class SettingsItem(
    val icon: Int,
    val title: String,
    val subtitle: String,
    val type: SettingsItemType
)

enum class SettingsItemType {
    ACCOUNT, DEVICE, FLIGHT, VIDEO, ABOUT, FEEDBACK, CACHE, LOGOUT
}

class SettingsAdapter(
    private val items: List<SettingsItem>,
    private val onItemClick: (SettingsItemType) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSettingsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
        holder.itemView.setOnClickListener {
            onItemClick(items[position].type)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemSettingsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SettingsItem) {
            binding.ivSettingsIcon.setImageResource(item.icon)
            binding.tvSettingsTitle.text = item.title
            binding.tvSettingsSubtitle.text = item.subtitle
        }
    }
}