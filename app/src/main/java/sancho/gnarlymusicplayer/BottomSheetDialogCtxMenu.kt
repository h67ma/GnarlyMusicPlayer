package sancho.gnarlymusicplayer

import android.content.Context
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

class BottomSheetDialogCtxMenu(context: Context, contentViewId: Int) : BottomSheetDialog(context)
{
	init{
		setContentView(contentViewId)
	}

	fun setBottomSheetItemOnClick(id: Int, callback: () -> Unit)
	{
		findViewById<TextView>(id)?.setOnClickListener {
			callback()
			dismiss()
		}
	}

	fun setHeaderText(id: Int, text: String)
	{
		findViewById<TextView>(id)?.text = text
	}
}
