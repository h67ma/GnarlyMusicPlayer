package sancho.gnarlymusicplayer.adapters

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import sancho.gnarlymusicplayer.BottomSheetDialogCtxMenu
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.models.QueueItem
import java.util.Collections.swap

class BookmarksAdapter(
	private val _context: Context,
	private val bookmarks: MutableList<QueueItem>,
	private val _setupCtxMenu: (BottomSheetDialogCtxMenu, String, Int) -> Unit,
	private val cliccListener: (QueueItem) -> Unit) : RecyclerView.Adapter<BookmarksAdapter.BookmarkHolder>()
{
	var touchHelper: ItemTouchHelper? = null

	override fun onBindViewHolder(holder: BookmarkHolder, position: Int)
	{
		holder.itemView.findViewById<TextView>(R.id.bookmark_text).text = bookmarks[holder.adapterPosition].name
		holder.itemView.setOnClickListener { cliccListener(bookmarks[holder.adapterPosition])}
		if(touchHelper != null)
		{
			holder.itemView.findViewById<AppCompatImageView>(R.id.bookmark_reorder).setOnTouchListener { _, event ->
				if (event.action == MotionEvent.ACTION_DOWN)
				{
					touchHelper?.startDrag(holder)
				}
				false
			}
		}

		holder.itemView.setOnLongClickListener {
			val dialog = BottomSheetDialogCtxMenu(_context, R.layout.bottom_sheet_bookmarks)
			// note: can't simply use position, as items might get removed/rearranged without refreshing whole dataset
			_setupCtxMenu(dialog, bookmarks[holder.adapterPosition].name, holder.adapterPosition)
			dialog.show()
			true // consumed long press
		}
	}

	override fun getItemCount() = bookmarks.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkHolder
	{
		return BookmarkHolder(LayoutInflater.from(_context).inflate(R.layout.bookmark_item, parent, false))
	}

	class BookmarkHolder(view: View) : RecyclerView.ViewHolder(view)

	fun onItemRemoved(position: Int)
	{
		bookmarks.removeAt(position)
	}

	fun onItemAdded(bookmark: QueueItem)
	{
		bookmarks.add(bookmark)
	}

	fun onItemMoved(fromPosition: Int, toPosition: Int)
	{
		if (fromPosition < toPosition)
		{
			for (i in fromPosition until toPosition)
			{
				swap(bookmarks, i, i + 1)
			}
		}
		else
		{
			for (i in fromPosition downTo toPosition + 1)
			{
				swap(bookmarks, i, i - 1)
			}
		}
		notifyItemMoved(fromPosition, toPosition)
	}
}
