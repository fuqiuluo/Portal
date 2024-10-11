package moe.fuqiuluo.portal.ui.mock

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import moe.fuqiuluo.portal.R
import java.math.BigDecimal

class HistoricalLocationAdapter(
    private val dataSet: MutableList<HistoricalLocation>,
    private val onItemClicked: (HistoricalLocation, isLongClicked: Boolean) -> Unit
): RecyclerView.Adapter<HistoricalLocationAdapter.ViewHolder>() {
    class ViewHolder(
        val root: View
    ) : RecyclerView.ViewHolder(root) {
        val locationName: MaterialTextView = root.findViewById(R.id.history_location_name)
        val locationLatLon: MaterialTextView = root.findViewById(R.id.history_location_latlon)
        val locationAddress: MaterialTextView = root.findViewById(R.id.history_location_address)
    }

    operator fun get(position: Int): HistoricalLocation {
        return dataSet[position]
    }

    fun removeItem(position: Int): HistoricalLocation {
        val removed = dataSet.removeAt(position)
        notifyItemRemoved(position)
        return removed
    }

    /**
     * Called when RecyclerView needs a new [ViewHolder] of the given type to represent
     * an item.
     *
     *
     * This new ViewHolder should be constructed with a new View that can represent the items
     * of the given type. You can either create a new View manually or inflate it from an XML
     * layout file.
     *
     *
     * The new ViewHolder will be used to display items of the adapter using
     * [.onBindViewHolder]. Since it will be re-used to display
     * different items in the data set, it is a good idea to cache references to sub views of
     * the View to avoid unnecessary [View.findViewById] calls.
     *
     * @param parent The ViewGroup into which the new View will be added after it is bound to
     * an adapter position.
     * @param viewType The view type of the new View.
     *
     * @return A new ViewHolder that holds a View of the given view type.
     * @see .getItemViewType
     * @see .onBindViewHolder
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_history_location_item, parent, false)

        return ViewHolder(view)
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     *
     * @return The total number of items in this adapter.
     */
    override fun getItemCount(): Int {
        return dataSet.size
    }

    /**
     * Called by RecyclerView to display the data at the specified position. This method should
     * update the contents of the [ViewHolder.itemView] to reflect the item at the given
     * position.
     *
     *
     * Note that unlike [android.widget.ListView], RecyclerView will not call this method
     * again if the position of the item changes in the data set unless the item itself is
     * invalidated or the new position cannot be determined. For this reason, you should only
     * use the `position` parameter while acquiring the related data item inside
     * this method and should not keep a copy of it. If you need the position of an item later
     * on (e.g. in a click listener), use [ViewHolder.getAdapterPosition] which will
     * have the updated adapter position.
     *
     * Override [.onBindViewHolder] instead if Adapter can
     * handle efficient partial bind.
     *
     * @param holder The ViewHolder which should be updated to represent the contents of the
     * item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.locationName.text = dataSet[position].name
        val lat = BigDecimal(dataSet[position].lat).toPlainString().take(8)
        val lon = BigDecimal(dataSet[position].lon).toPlainString().take(8)
        holder.locationLatLon.text = "$lat,$lon"
        holder.locationAddress.text = dataSet[position].address
        holder.root.setOnClickListener {
            onItemClicked(dataSet[position], false)
        }
        holder.root.setOnLongClickListener {
            onItemClicked(dataSet[position], true)
            true
        }
    }
}