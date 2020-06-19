package sancho.gnarlymusicplayer

fun isFileExtensionInArray(extension: String, extensions : Array<String>): Boolean
{
	return extension.lastIndexOf('.') > 0 && extension.substring(extension.lastIndexOf('.') + 1) in extensions
}

fun getStyleFromPreference(colorKey: String): Int
{
	val resources = mapOf(
		"green" to R.style.AppThemeGreen,
		"lime" to R.style.AppThemeLime,
		"blu" to R.style.AppThemeBlu,
		"cyan" to R.style.AppThemeCyan,
		"red" to R.style.AppThemeRed,
		"orang" to R.style.AppThemeOrang,
		"cherri" to R.style.AppThemeCherri,
		"purpl" to R.style.AppThemePurpl,
		"pink" to R.style.AppThemePink,
		"macintosh" to R.style.AppThemeMacintoshPlus)

	return resources[colorKey] ?: R.style.AppThemeGreen
}
