package sancho.gnarlymusicplayer

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.queue_item.view.*
import java.util.Collections.swap

class QueueAdapter(
	private val context: Context,
	private val tracks: MutableList<Track>,
	private val cliccListener: (Track) -> Unit) : RecyclerView.Adapter<QueueAdapter.TrackHolder>()
{
	var touchHelper: ItemTouchHelper? = null

	override fun onBindViewHolder(holder: TrackHolder, position: Int)
	{
		holder.bind(tracks[position], cliccListener, touchHelper)
	}

	override fun getItemCount() = tracks.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHolder
	{
		return TrackHolder(LayoutInflater.from(context).inflate(R.layout.queue_item, parent, false))
	}

	class TrackHolder(view: View) : RecyclerView.ViewHolder(view)
	{
		fun bind(bookmark: Track, clickListener: (Track) -> Unit, touchHelper: ItemTouchHelper?)
		{
			itemView.queue_text.text = bookmark.name
			itemView.setOnClickListener { clickListener(bookmark)}
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
