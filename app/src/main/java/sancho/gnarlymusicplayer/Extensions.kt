package sancho.gnarlymusicplayer

import java.io.File
import java.util.Arrays

fun String.isFileExtensionInArray(extensions : Array<String>): Boolean
{
	return this.lastIndexOf('.') > 0 && this.substring(this.lastIndexOf('.') + 1) in extensions
}

fun Array<File>.sortFiles()
{
	Arrays.sort(this) { a, b -> a.name.compareTo(b.name, true) }
}

fun Array<File>.sortFilesAndDirs()
{
	Arrays.sort(this) { a, b ->
		when
		{
			a.isFile && b.isDirectory -> 1
			a.isDirectory && b.isFile -> -1
			else -> a.name.compareTo(b.name, true)
		}
	}
}
