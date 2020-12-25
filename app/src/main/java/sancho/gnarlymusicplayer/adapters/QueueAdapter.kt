package sancho.gnarlymusicplayer.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.*
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.queue_item.view.*
import sancho.gnarlymusicplayer.PlaybackQueue
import sancho.gnarlymusicplayer.R
import java.util.Collections.swap

class QueueAdapter(
	private val context: Context,
	private val cliccListener: (Int) -> Unit) : RecyclerView.Adapter<QueueAdapter.TrackHolder>()
{
	var touchHelper: ItemTouchHelper? = null
	var selectedPosition: Int = -1

	override fun onBindViewHolder(holder: TrackHolder, position: Int)
	{
		holder.itemView.queue_text.text = PlaybackQueue.queue[position].name

		holder.itemView.isSelected = PlaybackQueue.currentIdx == position

		holder.itemView.setOnClickListener { cliccListener(position) }

		holder.itemView.setOnLongClickListener {
			selectedPosition = holder.adapterPosition
			false
		}

		@SuppressLint("ClickableViewAccessibility") // we don't want to click the track, only drag
		if(touchHelper != null)
		{
			holder.itemView.queue_reorder.setOnTouchListener { _, event ->
				if (event.action == MotionEvent.ACTION_DOWN)
				{
					touchHelper?.startDrag(holder)
				}
				false
			}
		}
	}

	override fun getItemCount() = PlaybackQueue.queue.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHolder
	{
		return TrackHolder(LayoutInflater.from(context).inflate(R.layout.queue_item, parent, false))
	}

	class TrackHolder(view: View) : RecyclerView.ViewHolder(view)

	fun onItemMoved(fromPosition: Int, toPosition: Int)
	{
		if (fromPosition < toPosition)
		{
			for (i in fromPosition until toPosition)
			{
				swap(PlaybackQueue.queue, i, i + 1)
			}
		}
		else
		{
			for (i in fromPosition downTo toPosition + 1)
			{
				swap(PlaybackQueue.queue, i, i - 1)
			}
		}
		notifyItemMoved(fromPosition, toPosition)
	}
}
