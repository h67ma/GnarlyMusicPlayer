package sancho.gnarlymusicplayer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import java.io.File
import android.view.LayoutInflater
import android.widget.TextView

class ExplorerAdapter(context: Context, items: MutableList<File>) : ArrayAdapter<File>(context, 0, items)
{
	private val SUPPORTED_PLAYLIST_EXTENSIONS = arrayOf(
		"m3u",
		"m3u8"
	)

	private class ViewHolder
	{
		var itemName: TextView? = null
	}

	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View
	{
		val item : File? = getItem(position)
		val retView : View
		val holder : ViewHolder
		if(convertView == null)
		{
			holder = ViewHolder()
			val inflater : LayoutInflater = LayoutInflater.from(context)
			retView = inflater.inflate(R.layout.explorer_item, parent, false)
			holder.itemName = retView.findViewById(R.id.explorerText)
			retView.tag = holder
		}
		else
		{
			retView = convertView
			holder = retView.tag as ViewHolder
		}

		if (item != null)
		{
			holder.itemName?.text = item.name
			val drawable : Int = when
			{
				item.isDirectory -> R.drawable.folder
				isFileExtensionInArray(item, SUPPORTED_PLAYLIST_EXTENSIONS) -> R.drawable.playlist
				else -> R.drawable.note
			}
			holder.itemName?.setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0)
		}

		return retView
	}
}
