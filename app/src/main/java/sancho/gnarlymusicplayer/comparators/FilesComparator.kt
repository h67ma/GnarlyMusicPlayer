package sancho.gnarlymusicplayer.comparators

import java.io.File

class FilesComparator: Comparator<File>
{
	override fun compare(a: File, b: File): Int
	{
		return a.name.compareTo(b.name, true)
	}
}
