package sancho.gnarlymusicplayer

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.bookmark_item.view.*

class BookmarksAdapter(
	private val context: Context,
	private val bookmarks: MutableList<Bookmark>,
	private val cliccListener: (Bookmark) -> Unit) : RecyclerView.Adapter<BookmarksAdapter.BookmarkHolder>()
{
	override fun onBindViewHolder(holder: BookmarkHolder, position: Int)
	{
		holder.bind(bookmarks[position], cliccListener)
	}

	override fun getItemCount() = bookmarks.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkHolder
	{
		return BookmarkHolder(LayoutInflater.from(context).inflate(R.layout.bookmark_item, parent, false))
	}

	class BookmarkHolder(view: View) : RecyclerView.ViewHolder(view)
	{
		fun bind(bookmark: Bookmark, clickListener: (Bookmark) -> Unit)
		{
			itemView.bookmark_text.text = bookmark.label
			itemView.setOnClickListener { clickListener(bookmark)}
		}
	}
}
