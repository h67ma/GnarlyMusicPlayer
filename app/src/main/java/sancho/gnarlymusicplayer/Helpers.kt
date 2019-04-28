package sancho.gnarlymusicplayer

import java.io.File

fun isFileExtensionInArray(file : File, extensions : Array<String>): Boolean
{
	return file.name.lastIndexOf('.') > 0 && file.name.substring(file.name.lastIndexOf('.') + 1) in extensions
}
