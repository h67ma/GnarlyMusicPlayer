package sancho.gnarlymusicplayer

import java.io.File

object Helpers
{
	// from https://developer.android.com/guide/topics/media/media-formats
	private val SUPPORTED_AUDIO_EXTENSIONS = arrayOf(
		"3gp",
		"mp4",
		"m4a",
		"aac",
		"ts",
		"amr",
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

	private val SUPPORTED_PLAYLIST_EXTENSIONS = arrayOf(
		"m3u",
		"m3u8"
	)

	private val SUPPORTED_EXTENSIONS = SUPPORTED_PLAYLIST_EXTENSIONS + SUPPORTED_AUDIO_EXTENSIONS

	fun isFileSupported(filename: String): Boolean
	{
		return isFileExtensionInArray(filename, SUPPORTED_EXTENSIONS)
	}

	private fun isFileSupportedAndAudio(filename: String): Boolean
	{
		return isFileExtensionInArray(filename, SUPPORTED_AUDIO_EXTENSIONS)
	}

	fun isFileSupportedAndPlaylist(filename: String): Boolean
	{
		return isFileExtensionInArray(filename, SUPPORTED_PLAYLIST_EXTENSIONS)
	}

	private fun isFileExtensionInArray(filename: String, extensions: Array<String>): Boolean
	{
		return filename.lastIndexOf('.') > 0 && filename.substring(filename.lastIndexOf('.') + 1) in extensions
	}

	fun listDir(path: File, onlyAudio: Boolean): MutableList<File>?
	{
		return path.listFiles { file ->
			(onlyAudio && !file.isDirectory && isFileSupportedAndAudio(file.name)) ||
					(!onlyAudio && (file.isDirectory || isFileSupported(file.name)))
		}?.toMutableList()
	}

	fun listPlaylist(path: File): MutableList<File>?
	{
		val list = mutableListOf<File>()

		path.readLines().forEach {
			val track = File(path.parent, it) // relative to playlist's directory
			if (track.exists() && !track.isDirectory && isFileSupportedAndAudio(it))
				list.add(track)
		}

		return list
	}
}
