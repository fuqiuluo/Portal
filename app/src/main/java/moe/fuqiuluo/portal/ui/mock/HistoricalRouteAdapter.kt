package moe.fuqiuluo.portal.ui.mock

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import moe.fuqiuluo.portal.R

class HistoricalRouteAdapter(
    private val dataSet: MutableList<HistoricalRoute>,
    private val onItemClicked: (HistoricalRoute, isLongClicked: Boolean) -> Unit
) : RecyclerView.Adapter<HistoricalRouteAdapter.ViewHolder>() {

    class ViewHolder(
        val root: View
    ) : RecyclerView.ViewHolder(root) {
        val name: MaterialTextView = root.findViewById(R.id.name)
        val route: MaterialTextView = root.findViewById(R.id.desc)
    }

    operator fun get(position: Int): HistoricalRoute {
        return dataSet[position]
    }

    fun removeItem(position: Int): HistoricalRoute {
        val removed = dataSet.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, itemCount - position)
        return removed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_route_item, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.name.text = dataSet[position].name
        holder.route.text = dataSet[position].route.toString()
        holder.root.setOnClickListener {
            onItemClicked(dataSet[position], false)
        }
        holder.root.setOnLongClickListener {
            onItemClicked(dataSet[position], true)
            true
        }
    }
}