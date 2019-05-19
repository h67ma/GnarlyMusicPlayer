package sancho.gnarlymusicplayer.adapters

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.bookmark_item.view.*
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.Track
import java.util.Collections.swap

class BookmarksAdapter(
	private val context: Context,
	private val bookmarks: MutableList<Track>,
	private val cliccListener: (Track) -> Unit) : RecyclerView.Adapter<BookmarksAdapter.BookmarkHolder>()
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
		fun bind(bookmark: Track, clickListener: (Track) -> Unit, touchHelper: ItemTouchHelper?)
		{
			itemView.bookmark_text.text = bookmark.name
			itemView.setOnClickListener { clickListener(bookmark)}
			if(touchHelper != null)
			{
				itemView.bookmark_reorder.setOnTouchListener { _, event ->
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

	fun onItemAdded(bookmark: Track)
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
