package sancho.gnarlymusicplayer

fun String.isFileExtensionInArray(extensions : Array<String>): Boolean
{
	return this.lastIndexOf('.') > 0 && this.substring(this.lastIndexOf('.') + 1) in extensions
}
