package sancho.gnarlymusicplayer.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.*
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.queue_item.view.*
import sancho.gnarlymusicplayer.BottomSheetDialogCtxMenu
import sancho.gnarlymusicplayer.PlaybackQueue
import sancho.gnarlymusicplayer.R

class QueueAdapter(
	private val _context: Context,
	private val _playTrack: (Int) -> Unit,
	private val _setupExplorerCtxMenu: (BottomSheetDialogCtxMenu, String, Int) -> Unit) : RecyclerView.Adapter<QueueAdapter.TrackHolder>()
{
	var touchHelper: ItemTouchHelper? = null

	override fun onBindViewHolder(holder: TrackHolder, position: Int)
	{
		holder.itemView.queue_text.text = PlaybackQueue.queue[holder.adapterPosition].name

		holder.itemView.isSelected = PlaybackQueue.currentIdx == holder.adapterPosition

		holder.itemView.setOnClickListener { _playTrack(holder.adapterPosition) }

		holder.itemView.setOnLongClickListener {
			val dialog = BottomSheetDialogCtxMenu(_context, R.layout.bottom_sheet_queue)
			// note: can't simply use position, as items might get removed/rearranged without refreshing whole dataset
			_setupExplorerCtxMenu(dialog, PlaybackQueue.queue[holder.adapterPosition].name, holder.adapterPosition)
			dialog.show()
			true // consumed long press
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
		return TrackHolder(LayoutInflater.from(_context).inflate(R.layout.queue_item, parent, false))
	}

	class TrackHolder(view: View) : RecyclerView.ViewHolder(view)
}
