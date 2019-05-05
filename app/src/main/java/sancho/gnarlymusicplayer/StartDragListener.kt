package sancho.gnarlymusicplayer

import androidx.recyclerview.widget.RecyclerView

interface StartDragListener
{
	fun requestDrag(viewHolder: RecyclerView.ViewHolder)
}
