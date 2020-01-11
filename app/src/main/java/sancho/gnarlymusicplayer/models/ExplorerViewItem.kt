package sancho.gnarlymusicplayer.models

abstract class ExplorerViewItem(var path: String, var displayName: String, var isDirectory: Boolean, var isHeader: Boolean)
{
	override fun equals(other: Any?): Boolean
	{
		return (other as ExplorerViewItem).path == this.path
	}
}
