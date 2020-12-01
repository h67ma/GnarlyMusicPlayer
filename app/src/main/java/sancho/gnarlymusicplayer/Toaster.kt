package sancho.gnarlymusicplayer

import android.content.Context
import android.widget.Toast

/**
 * This basically changes the Toast behaviour so if multiple Toasts
 * are spawned in a short amount of time, new ones will be shown
 * instantly, instead of waiting for previous ones to disappear.
 */
object Toaster
{
	private var toast: Toast? = null

	fun show(context: Context, text: String)
	{
		toast?.cancel()
		toast = Toast.makeText(context, text, Toast.LENGTH_SHORT)
		toast?.show()
	}
}
