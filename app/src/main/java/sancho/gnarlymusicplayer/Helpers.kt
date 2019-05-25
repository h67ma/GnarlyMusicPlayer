package sancho.gnarlymusicplayer

import android.app.AlertDialog
import android.content.Context
import java.io.File

const val REQUEST_READ_STORAGE = 42

const val PREFERENCE_BOOKMARKS = "sancho.gnarlymusicplayer.preference.bookmarks"
const val PREFERENCE_QUEUE = "sancho.gnarlymusicplayer.preference.queue"
const val PREFERENCE_LASTDIR = "sancho.gnarlymusicplayer.preference.lastdir"
const val PREFERENCE_ACCENTCOLOR = "sancho.gnarlymusicplayer.preference.accentcolor"
const val PREFERENCE_CURRENTTRACK = "sancho.gnarlymusicplayer.preference.currenttrack"

const val BUNDLE_LASTSELECTEDTRACK = "sancho.gnarlymusicplayer.bundle.lastselectedtrack"

const val ACTION_START_PLAYBACK_SERVICE = "sancho.gnarlymusicplayer.action.startplayback"
const val ACTION_STOP_PLAYBACK_SERVICE = "sancho.gnarlymusicplayer.action.stopplayback"
const val ACTION_REPLAY_TRACK = "sancho.gnarlymusicplayer.action.replaytrack"
const val ACTION_PREV_TRACK = "sancho.gnarlymusicplayer.action.prevtrack"
const val ACTION_PLAYPAUSE = "sancho.gnarlymusicplayer.action.playpause"
const val ACTION_NEXT_TRACK = "sancho.gnarlymusicplayer.action.nexttrack"

const val NOTIFICATION_CHANNEL_ID = "sancho.gnarlymusicplayer.notificationthing"
const val NOTIFICATION_ID = 420

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

val COLOR_RESOURCES = arrayOf(
	R.style.AppThemeBlack,
	R.style.AppThemeGreen,
	R.style.AppThemeBlu,
	R.style.AppThemeCyan,
	R.style.AppThemeRed,
	R.style.AppThemeOrang,
	R.style.AppThemePurpl,
	R.style.AppThemePink)

val COLOR_NAMES = arrayOf(
	"Black",
	"Green",
	"Blu",
	"Cyan",
	"Red",
	"Orang",
	"Purpl",
	"Pink")

fun isFileExtensionInArray(file : File, extensions : Array<String>): Boolean
{
	return file.name.lastIndexOf('.') > 0 && file.name.substring(file.name.lastIndexOf('.') + 1) in extensions
}

fun showAboutDialog(context: Context)
{
	AlertDialog.Builder(context)
		.setTitle(context.getString(R.string.about))
		.setMessage(context.getString(R.string.about_message))
		.setPositiveButton(context.getString(R.string.ok), null)
		.create()
		.show()
}
