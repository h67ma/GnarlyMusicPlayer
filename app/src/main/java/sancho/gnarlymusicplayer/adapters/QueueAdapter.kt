package sancho.gnarlymusicplayer.adapters

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.queue_item.view.*
import sancho.gnarlymusicplayer.App
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.models.QueueItem
import java.util.Collections.swap

class QueueAdapter(
	private val context: Context,
	private val items: MutableList<QueueItem>,
	private val cliccListener: (Int) -> Unit) : RecyclerView.Adapter<QueueAdapter.TrackHolder>()
{
	var touchHelper: ItemTouchHelper? = null

	override fun onBindViewHolder(holder: TrackHolder, position: Int)
	{
		holder.bind(items[position], cliccListener, touchHelper)
	}

	override fun getItemCount() = items.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHolder
	{
		return TrackHolder(LayoutInflater.from(context).inflate(R.layout.queue_item, parent, false))
	}

	class TrackHolder(view: View) : RecyclerView.ViewHolder(view)
	{
		fun bind(bookmark: QueueItem, cliccListener: (Int) -> Unit, touchHelper: ItemTouchHelper?)
		{
			itemView.queue_text.text = bookmark.name

			itemView.isSelected = App.currentTrack == adapterPosition

			itemView.setOnClickListener {cliccListener(adapterPosition)}

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
				swap(items, i, i + 1)
			}
		}
		else
		{
			for (i in fromPosition downTo toPosition + 1)
			{
				swap(items, i, i - 1)
			}
		}
		notifyItemMoved(fromPosition, toPosition)
	}
}
