package sancho.gnarlymusicplayer.comparators

import sancho.gnarlymusicplayer.models.ExplorerViewItem

class ExplorerViewFilesComparator: Comparator<ExplorerViewItem>
{
	override fun compare(a: ExplorerViewItem, b: ExplorerViewItem): Int
	{
		return a.displayName.compareTo(b.displayName, true)
	}
}
