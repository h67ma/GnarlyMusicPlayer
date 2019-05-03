package sancho.gnarlymusicplayer

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.explorer_item.view.*
import java.io.File

class ExplorerAdapter(
	private val context: Context,
	private val files: MutableList<File>,
	private val cliccListener: (File, Int) -> Unit) : RecyclerView.Adapter<ExplorerAdapter.FileHolder>()
{
	override fun onBindViewHolder(holder: FileHolder, position: Int)
	{
		holder.bind(files[position], cliccListener, position)
	}

	override fun getItemCount() = files.size

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileHolder
	{
		return FileHolder(LayoutInflater.from(context).inflate(R.layout.explorer_item, parent, false))
	}

	class FileHolder(view: View) : RecyclerView.ViewHolder(view)
	{
		fun bind(file: File, clickListener: (File, Int) -> Unit, position: Int)
		{
			itemView.explorer_text.text = file.name

			val drawable : Int = when
			{
				file.isDirectory -> R.drawable.folder
				isFileExtensionInArray(file, SUPPORTED_PLAYLIST_EXTENSIONS) -> R.drawable.playlist
				else -> R.drawable.note
			}
			itemView.explorer_text.setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0)

			itemView.setOnClickListener { clickListener(file, position)}
		}
	}
}
