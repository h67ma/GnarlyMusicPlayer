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
	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View
	{
		val item : File? = getItem(position)
		val retView : View
		val holder : TextView
		if(convertView == null)
		{
			val inflater : LayoutInflater = LayoutInflater.from(context)
			retView = inflater.inflate(R.layout.explorer_item, parent, false)
			holder = retView.findViewById(R.id.explorer_text)
			retView.tag = holder
		}
		else
		{
			retView = convertView
			holder = retView.tag as TextView
		}

		if (item != null)
		{
			holder.text = item.name
			val drawable : Int = when
			{
				item.isDirectory -> R.drawable.folder
				isFileExtensionInArray(item, SUPPORTED_PLAYLIST_EXTENSIONS) -> R.drawable.playlist
				else -> R.drawable.note
			}
			holder.setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0)
		}

		return retView
	}
}
