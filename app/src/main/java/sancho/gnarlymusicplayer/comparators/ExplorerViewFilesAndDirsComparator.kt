package sancho.gnarlymusicplayer.comparators

import sancho.gnarlymusicplayer.models.ExplorerViewItem

class ExplorerViewFilesAndDirsComparator: Comparator<ExplorerViewItem>
{
	override fun compare(a: ExplorerViewItem, b: ExplorerViewItem): Int
	{
		return when
		{
			!a.isDirectory && b.isDirectory -> 1
			a.isDirectory && !b.isDirectory -> -1
			else -> a.displayName.compareTo(b.displayName, true)
		}
	}
}
