package com.example.msdksample.kami.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.msdksample.R
import com.example.msdksample.kami.model.Waypoint
import java.util.Locale

/**
 * 侧边栏航点列表适配器
 * 显示航点编号、坐标，点击可定位到该航点
 */
class WaypointAdapter(
    private val waypoints: List<Waypoint>,
    private val onClick: (Waypoint) -> Unit
) : RecyclerView.Adapter<WaypointAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvWaypointLabel: TextView = itemView.findViewById(R.id.tvWaypointLabel)
        val tvWaypointCoord: TextView = itemView.findViewById(R.id.tvWaypointCoord)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_waypoint,
            parent,
            false
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val waypoint = waypoints[position]
        val sequence = if (waypoint.sequence > 0) waypoint.sequence else position + 1
        holder.tvWaypointLabel.text = String.format(Locale.US, "P%02d", sequence)
        holder.tvWaypointCoord.text =
            String.format(Locale.US, "(%.6f, %.6f)", waypoint.latitude, waypoint.longitude)
        holder.itemView.setOnClickListener { onClick(waypoint) }
    }

    override fun getItemCount() = waypoints.size
}
