package sancho.gnarlymusicplayer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.widget.TextView

class BookmarksAdapter(context: Context, items: MutableList<Bookmark>) : ArrayAdapter<Bookmark>(context, 0, items)
{
	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View
	{
		val item : Bookmark? = getItem(position)
		val retView : View
		val holder : TextView
		if(convertView == null)
		{
			val inflater : LayoutInflater = LayoutInflater.from(context)
			retView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false)
			holder = retView.findViewById(android.R.id.text1)
			retView.tag = holder
		}
		else
		{
			retView = convertView
			holder = retView.tag as TextView
		}

		if (item != null)
		{
			holder.text = item.label
		}

		return retView
	}
}
