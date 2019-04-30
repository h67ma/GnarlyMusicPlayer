package sancho.gnarlymusicplayer

import java.io.File

const val REQUEST_READ_STORAGE = 42
const val PREFERENCE_BOOKMARKS = "sancho.gnarlymusicplayer.preference.bookmarks"

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
	"ogg",
	"m3u",
	"m3u8"
)

val SUPPORTED_PLAYLIST_EXTENSIONS = arrayOf(
	"m3u", "m3u8"
)

fun isFileExtensionInArray(file : File, extensions : Array<String>): Boolean
{
	return file.name.lastIndexOf('.') > 0 && file.name.substring(file.name.lastIndexOf('.') + 1) in extensions
}
