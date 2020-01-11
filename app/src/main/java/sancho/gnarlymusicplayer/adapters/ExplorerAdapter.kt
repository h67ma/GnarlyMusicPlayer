package sancho.gnarlymusicplayer.adapters

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.explorer_group_item.view.*
import kotlinx.android.synthetic.main.explorer_item.view.*
import sancho.gnarlymusicplayer.App
import sancho.gnarlymusicplayer.R
import sancho.gnarlymusicplayer.models.ExplorerViewItem

class ExplorerAdapter(
	private val context: Context,
	private val files: MutableList<ExplorerViewItem>,
	private val cliccListener: (ExplorerViewItem) -> Unit,
	private val longCliccListener: (ExplorerViewItem) -> Boolean) : RecyclerView.Adapter<ExplorerAdapter.FileHolder>()
{
	override fun onBindViewHolder(holder: FileHolder, position: Int)
	{
		holder.bind(files[position], cliccListener, longCliccListener, position)
	}

	override fun getItemCount() = files.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileHolder
	{
		return when (viewType)
		{
			App.EXPLORER_NORMAL_ITEM -> FileHolder(LayoutInflater.from(context).inflate(R.layout.explorer_item, parent, false))
			else -> FileHolder(LayoutInflater.from(context).inflate(R.layout.explorer_group_item, parent, false))
		}

	}

	class FileHolder(view: View) : RecyclerView.ViewHolder(view)
	{
		fun bind(file: ExplorerViewItem, clickListener: (ExplorerViewItem) -> Unit, longClickListener: (ExplorerViewItem) -> Boolean, position: Int)
		{
			if (file.isHeader)
			{
				itemView.explorer_header_text.text = file.displayName
			}
			else
			{
				itemView.explorer_text.text = file.displayName

				itemView.explorer_text.setCompoundDrawablesWithIntrinsicBounds(
					if (file.isDirectory) R.drawable.folder else R.drawable.note, 0, 0, 0
				)

				itemView.setOnClickListener { clickListener(file) }
				itemView.setOnLongClickListener { longClickListener(file) }
			}
		}
	}

	override fun getItemViewType(pos: Int): Int
	{
		if (files[pos].isHeader)
			return App.EXPLORER_GROUP_ITEM

		return App.EXPLORER_NORMAL_ITEM
	}
}
