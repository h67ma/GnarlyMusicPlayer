package sancho.gnarlymusicplayer

import java.io.File

fun isFileSupported(filename: String): Boolean
{
	return isFileExtensionInArray(filename, App.SUPPORTED_EXTENSIONS)
}

fun isFileSupportedAndAudio(filename: String): Boolean
{
	return isFileExtensionInArray(filename, App.SUPPORTED_AUDIO_EXTENSIONS)
}

fun isFileSupportedAndPlaylist(filename: String): Boolean
{
	return isFileExtensionInArray(filename, App.SUPPORTED_PLAYLIST_EXTENSIONS)
}

fun isFileExtensionInArray(filename: String, extensions : Array<String>): Boolean
{
	return filename.lastIndexOf('.') > 0 && filename.substring(filename.lastIndexOf('.') + 1) in extensions
}

fun getStyleFromPreference(colorKey: String): Int
{
	val resources = mapOf(
		"lime" to R.style.AppThemeLime,
		"green" to R.style.AppThemeGreen,
		"blu" to R.style.AppThemeBlu,
		"cyan" to R.style.AppThemeCyan,
		"red" to R.style.AppThemeRed,
		"orang" to R.style.AppThemeOrang,
		"cherri" to R.style.AppThemeCherri,
		"purpl" to R.style.AppThemePurpl,
		"pink" to R.style.AppThemePink,
		"macintosh" to R.style.AppThemeMacintoshPlus)

	return resources[colorKey] ?: R.style.AppThemeLime
}

fun listDir(path: File, onlyAudio: Boolean): MutableList<File>?
{
	return path.listFiles{ file ->
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
