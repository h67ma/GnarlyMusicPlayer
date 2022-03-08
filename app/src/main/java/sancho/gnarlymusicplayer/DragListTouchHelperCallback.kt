package sancho.gnarlymusicplayer

import android.content.Context
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

abstract class DragListTouchHelperCallback(private val context: Context) : ItemTouchHelper.Callback()
{
	override fun isLongPressDragEnabled(): Boolean
	{
		return false
	}

	override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int)
	{
		if (viewHolder?.itemView?.isSelected == true)
			return // don't change color of selected item

		// highlight item on dragging start
		viewHolder?.itemView?.setBackgroundColor(context.getColor(R.color.itemHighlight))
	}

	override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder)
	{
		// de-highlight item on dragging end
		viewHolder.itemView.setBackgroundResource(R.drawable.highlightable_item)

		// clears some weird residue shadows
		super.clearView(recyclerView, viewHolder)
	}
}
