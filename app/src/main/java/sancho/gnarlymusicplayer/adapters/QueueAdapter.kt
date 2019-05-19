package sancho.gnarlymusicplayer.adapters

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.queue_item.view.*
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.Track
import java.util.Collections.swap

class QueueAdapter(
	private val context: Context,
	private val tracks: MutableList<Track>,
	private val cliccListener: (Track, Int) -> Unit) : RecyclerView.Adapter<QueueAdapter.TrackHolder>()
{
	var touchHelper: ItemTouchHelper? = null
	var selectedPos: Int = RecyclerView.NO_POSITION

	override fun onBindViewHolder(holder: TrackHolder, position: Int)
	{
		holder.bind(tracks[position], cliccListener, touchHelper, position, selectedPos, this)
	}

	override fun getItemCount() = tracks.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHolder
	{
		return TrackHolder(LayoutInflater.from(context).inflate(R.layout.queue_item, parent, false))
	}

	class TrackHolder(view: View) : RecyclerView.ViewHolder(view)
	{
		fun bind(bookmark: Track, clickListener: (Track, Int) -> Unit, touchHelper: ItemTouchHelper?, position: Int, selectedPos: Int, adapter: QueueAdapter)
		{
			itemView.isSelected = selectedPos == position

			itemView.queue_text.text = bookmark.name
			itemView.setOnClickListener {
				clickListener(bookmark, position)
				adapter.notifyItemChanged(adapter.selectedPos)
				adapter.selectedPos = position
				adapter.notifyItemChanged(adapter.selectedPos)
			}
			if(touchHelper != null)
			{
				itemView.queue_reorder.setOnTouchListener { _, event ->
					if (event.action == MotionEvent.ACTION_DOWN)
					{
						touchHelper.startDrag(this)
					}
					false
				}
			}
		}
	}

	fun onItemMoved(fromPosition: Int, toPosition: Int)
	{
		if (fromPosition < toPosition)
		{
			for (i in fromPosition until toPosition)
			{
				swap(tracks, i, i + 1)
			}
		}
		else
		{
			for (i in fromPosition downTo toPosition + 1)
			{
				swap(tracks, i, i - 1)
			}
		}
		notifyItemMoved(fromPosition, toPosition)
	}
}
