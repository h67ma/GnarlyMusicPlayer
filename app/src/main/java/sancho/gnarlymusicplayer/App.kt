package sancho.gnarlymusicplayer

import android.app.Application
import android.media.AudioManager
import sancho.gnarlymusicplayer.models.ExplorerViewItem
import java.io.File

class App: Application()
{
	companion object
	{
		var mediaPlaybackServiceStarted: Boolean = false
		var serviceBound: Boolean = false

		// needs to be global because is used in service and in settings activity
		// when session doesn't exist set it to error value
		var audioSessionId: Int = AudioManager.ERROR

		const val REQUEST_READ_STORAGE = 42

		const val INTENT_LAUNCH_FOR_RESULT_SETTINGS = 1613
		const val INTENT_LAUNCH_EQ = 1337

		const val EXPLORER_NORMAL_ITEM = 0
		const val EXPLORER_GROUP_ITEM = 1

		const val BUNDLE_LASTSELECTEDTRACK = "sancho.gnarlymusicplayer.bundle.lastselectedtrack"

		const val ACTION_START_PLAYBACK_SERVICE = "sancho.gnarlymusicplayer.action.startplayback"
		const val ACTION_STOP_PLAYBACK_SERVICE = "sancho.gnarlymusicplayer.action.stopplayback"
		const val ACTION_REPLAY_TRACK = "sancho.gnarlymusicplayer.action.replaytrack"
		const val ACTION_PREV_TRACK = "sancho.gnarlymusicplayer.action.prevtrack"
		const val ACTION_PLAYPAUSE = "sancho.gnarlymusicplayer.action.playpause"
		const val ACTION_NEXT_TRACK = "sancho.gnarlymusicplayer.action.nexttrack"
		const val ACTION_UPDATE_MAX_VOLUME = "sancho.gnarlymusicplayer.action.updatemaxvolume"

		const val NOTIFICATION_CHANNEL_ID = "sancho.gnarlymusicplayer.notificationthing"
		const val NOTIFICATION_ID = 420

		const val MIN_TRACK_TIME_S_TO_SAVE = 30

		val explorerViewFilesAndDirsComparator = Comparator<ExplorerViewItem>{ a, b ->
			when
			{
				!a.isDirectory && b.isDirectory -> 1
				a.isDirectory && !b.isDirectory -> -1
				else -> a.displayName.compareTo(b.displayName, true)
			}
		}

		val explorerViewFilesComparator = Comparator<ExplorerViewItem>{ a, b ->
			a.displayName.compareTo(b.displayName, true)
		}

		val filesComparator = Comparator<File>{ a, b ->
			a.name.compareTo(b.name, true)
		}
	}
}
