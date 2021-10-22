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
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.models.QueueItem
import java.util.Collections.swap

class BookmarksAdapter(
	private val context: Context,
	private val bookmarks: MutableList<QueueItem>,
	private val cliccListener: (QueueItem) -> Unit) : RecyclerView.Adapter<BookmarksAdapter.BookmarkHolder>()
{
	var touchHelper: ItemTouchHelper? = null

	override fun onBindViewHolder(holder: BookmarkHolder, position: Int)
	{
		holder.bind(bookmarks[position], cliccListener, touchHelper)
	}

	override fun getItemCount() = bookmarks.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkHolder
	{
		return BookmarkHolder(LayoutInflater.from(context).inflate(R.layout.bookmark_item, parent, false))
	}

	class BookmarkHolder(view: View) : RecyclerView.ViewHolder(view)
	{
		fun bind(bookmark: QueueItem, clickListener: (QueueItem) -> Unit, touchHelper: ItemTouchHelper?)
		{
			itemView.findViewById<TextView>(R.id.bookmark_text).text = bookmark.name
			itemView.setOnClickListener { clickListener(bookmark)}
			if(touchHelper != null)
			{
				itemView.findViewById<AppCompatImageView>(R.id.bookmark_reorder).setOnTouchListener { _, event ->
					if (event.action == MotionEvent.ACTION_DOWN)
					{
						touchHelper.startDrag(this)
					}
					false
				}
			}
		}
	}

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
