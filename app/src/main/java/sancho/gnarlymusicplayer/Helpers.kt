package sancho.gnarlymusicplayer

import java.io.File

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
		(onlyAudio && !file.isDirectory && isFileExtensionInArray(file.name, App.SUPPORTED_AUDIO_EXTENSIONS)) ||
		(!onlyAudio && (file.isDirectory || isFileExtensionInArray(file.name, App.SUPPORTED_EXTENSIONS)))
	}?.toMutableList()
}

fun listPlaylist(path: File): MutableList<File>?
{
	val list = mutableListOf<File>()

	path.readLines().forEach {
		val track = File(path.parent, it) // relative to playlist's directory
		if (track.exists() && !track.isDirectory && isFileExtensionInArray(it, App.SUPPORTED_AUDIO_EXTENSIONS))
			list.add(track)
	}

	return list
}
