package sancho.gnarlymusicplayer

import android.app.Application
import androidx.recyclerview.widget.RecyclerView
import sancho.gnarlymusicplayer.models.ExplorerViewItem
import sancho.gnarlymusicplayer.models.Track
import java.io.File

class App: Application()
{
	companion object
	{
		var currentTrack: Int = RecyclerView.NO_POSITION
		lateinit var queue: MutableList<Track>
		var mediaPlaybackServiceStarted: Boolean = false
		var savedTrackPath: String = ""
		var savedTrackTime: Int = 0

		const val REQUEST_READ_STORAGE = 42

		const val INTENT_LAUNCH_FOR_RESULT_SETTINGS = 1613

		const val EXPLORER_NORMAL_ITEM = 0
		const val EXPLORER_GROUP_ITEM = 1

		const val PREFERENCE_BOOKMARKS = "sancho.gnarlymusicplayer.preference.bookmarks"
		const val PREFERENCE_QUEUE = "sancho.gnarlymusicplayer.preference.queue"
		const val PREFERENCE_LASTDIR = "sancho.gnarlymusicplayer.preference.lastdir"
		const val PREFERENCE_CURRENTTRACK = "sancho.gnarlymusicplayer.preference.currenttrack"
		const val PREFERENCE_SAVEDTRACK_PATH = "sancho.gnarlymusicplayer.preference.savedtrack.path"
		const val PREFERENCE_SAVEDTRACK_TIME = "sancho.gnarlymusicplayer.preference.savedtrack.time"

		const val BUNDLE_LASTSELECTEDTRACK = "sancho.gnarlymusicplayer.bundle.lastselectedtrack"

		const val ACTION_START_PLAYBACK_SERVICE = "sancho.gnarlymusicplayer.action.startplayback"
		const val ACTION_STOP_PLAYBACK_SERVICE = "sancho.gnarlymusicplayer.action.stopplayback"
		const val ACTION_REPLAY_TRACK = "sancho.gnarlymusicplayer.action.replaytrack"
		const val ACTION_PREV_TRACK = "sancho.gnarlymusicplayer.action.prevtrack"
		const val ACTION_PLAYPAUSE = "sancho.gnarlymusicplayer.action.playpause"
		const val ACTION_NEXT_TRACK = "sancho.gnarlymusicplayer.action.nexttrack"

		const val NOTIFICATION_CHANNEL_ID = "sancho.gnarlymusicplayer.notificationthing"
		const val NOTIFICATION_ID = 420

		const val MIN_TRACK_TIME_S_TO_SAVE = 30

		// from https://developer.android.com/guide/topics/media/media-formats
		val SUPPORTED_FILE_EXTENSIONS = arrayOf(
			"3gp",
			"mp4",
			"m4a",
			"aac",
			"ts",
			"flac",
			"gsm",
			"mid",
			"xmf",
			"mxmf",
			"rtttl",
			"rtx",
			"ota",
			"imy",
			"mp3",
			"mkv",
			"wav",
			"ogg"
		)

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
