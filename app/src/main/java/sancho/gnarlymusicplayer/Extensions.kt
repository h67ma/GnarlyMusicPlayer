package sancho.gnarlymusicplayer

fun String.isFileExtensionInArray(extensions : Array<String>): Boolean
{
	return this.lastIndexOf('.') > 0 && this.substring(this.lastIndexOf('.') + 1) in extensions
}

fun Int.toMinuteSecondString(): String
{
	return "%2d:%02d".format(this / 60, this % 60) // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/format.html - thanks kotlin documentation for NOT EXISTING
}
