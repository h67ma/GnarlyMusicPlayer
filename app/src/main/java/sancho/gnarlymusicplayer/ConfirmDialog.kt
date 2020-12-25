package sancho.gnarlymusicplayer

import android.app.AlertDialog
import android.content.Context

object ConfirmDialog
{
	fun show(context: Context, message: String, callback: () -> Unit)
	{
		AlertDialog.Builder(context)
			.setMessage(message)
			.setPositiveButton(android.R.string.ok) { _, _ -> callback() }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}
}
