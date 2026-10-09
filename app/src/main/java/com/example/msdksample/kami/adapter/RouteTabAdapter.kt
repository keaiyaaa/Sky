package com.example.msdksample.kami.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.msdksample.R
import com.example.msdksample.kami.model.RouteSummary

class RouteTabAdapter(
    private val onClick: (RouteSummary) -> Unit,
    private val onLongClick: (RouteSummary) -> Unit
) : RecyclerView.Adapter<RouteTabAdapter.ViewHolder>() {

    private val items = mutableListOf<RouteSummary>()
    private var activeRouteId: String? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRouteName: TextView = itemView.findViewById(R.id.tvRouteName)
        val tvWaypointCount: TextView = itemView.findViewById(R.id.tvWaypointCount)
        val tvTaskName: TextView = itemView.findViewById(R.id.tvTaskName)
        val ivRouteStatus: ImageView = itemView.findViewById(R.id.ivRouteStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_route_tab,
            parent,
            false
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val route = item.route
        val isActive = route.id == activeRouteId

        holder.tvRouteName.text = route.name
        holder.tvWaypointCount.text = holder.itemView.context.getString(
            R.string.waypoint_count_format,
            item.waypointCount
        )
        holder.tvTaskName.text = route.taskName.ifBlank { route.priority.displayName }
        holder.tvRouteName.setTextColor(
            if (isActive) Color.parseColor("#5B8FF9") else Color.parseColor("#333333")
        )
        holder.ivRouteStatus.visibility = if (isActive) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<RouteSummary>, activeId: String?) {
        items.clear()
        items.addAll(newItems)
        activeRouteId = activeId
        notifyDataSetChanged()
    }
}
